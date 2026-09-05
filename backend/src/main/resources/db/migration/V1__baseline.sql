-- gotthard-spring — baseline schema.
--
-- Six tables come from the assignment brief verbatim (transactions,
-- card/payment/crypto_activity, risk_rules, risk_assessments). The rest are
-- ours: the brief references a `customers` table without defining it, says
-- nothing about operators, and requires AI analyses to be persisted.
--
-- Deviation from the brief, deliberate: activity_type / status / levels are
-- VARCHAR + CHECK rather than native PostgreSQL ENUMs. Native enums need a
-- custom Hibernate type and cannot be altered inside a transaction; CHECK
-- constraints map straight onto @Enumerated(EnumType.STRING) and stay simple.

CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------- operators

CREATE TABLE operators (
    operator_id  UUID         PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    role         VARCHAR(16)  NOT NULL CHECK (role IN ('OPERATOR','SUPERVISOR')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Credentials live apart from identity so operator rows can be read freely
-- without ever loading a hash. argon2id, never reversible encryption.
CREATE TABLE operator_credentials (
    operator_id   UUID         PRIMARY KEY REFERENCES operators(operator_id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- customers

CREATE TABLE customers (
    customer_id  UUID         PRIMARY KEY,
    reference    VARCHAR(32)  NOT NULL UNIQUE,   -- human-typeable, e.g. CH-4410-8821
    full_name    VARCHAR(128) NOT NULL,
    country      CHAR(2)      NOT NULL,
    segment      VARCHAR(32)  NOT NULL,
    onboarded_at TIMESTAMPTZ  NOT NULL
);

-- ------------------------------------------------------------- transactions

CREATE TABLE transactions (
    transaction_id UUID          PRIMARY KEY,
    customer_id    UUID          NOT NULL REFERENCES customers(customer_id),
    activity_type  VARCHAR(8)    NOT NULL CHECK (activity_type IN ('CARD','PAYMENT','CRYPTO')),
    amount         DECIMAL(18,2) NOT NULL,
    currency       VARCHAR(10)   NOT NULL,
    status         VARCHAR(16)   NOT NULL CHECK (status IN ('COMPLETED','PENDING','FAILED','REVERSED')),
    created_at     TIMESTAMPTZ   NOT NULL
);

-- Dashboard and window queries always slice by customer then time.
CREATE INDEX idx_tx_customer_time ON transactions (customer_id, created_at DESC);

-- Transactions are append-only and time-ordered, so BRIN indexes the whole
-- table in a few pages where a btree would cost megabytes.
CREATE INDEX idx_tx_created_brin ON transactions USING BRIN (created_at);

CREATE TABLE card_activity (
    transaction_id     UUID PRIMARY KEY REFERENCES transactions(transaction_id) ON DELETE CASCADE,
    card_pan           VARCHAR(24)  NOT NULL,
    card_type          VARCHAR(16)  NOT NULL,
    merchant_name      VARCHAR(128) NOT NULL,
    mcc_code           CHAR(4)      NOT NULL,
    card_present       BOOLEAN      NOT NULL,
    authorization_code VARCHAR(16),
    decline_reason     VARCHAR(64)
);

CREATE TABLE payment_activity (
    transaction_id        UUID PRIMARY KEY REFERENCES transactions(transaction_id) ON DELETE CASCADE,
    payment_method        VARCHAR(16) NOT NULL,
    sender_account        VARCHAR(34) NOT NULL,
    receiver_account      VARCHAR(34) NOT NULL,
    receiver_bank_country CHAR(2)     NOT NULL
);

CREATE INDEX idx_payment_beneficiary ON payment_activity (receiver_account);

CREATE TABLE crypto_activity (
    transaction_id      UUID PRIMARY KEY REFERENCES transactions(transaction_id) ON DELETE CASCADE,
    blockchain          VARCHAR(16)  NOT NULL,
    wallet_address_from VARCHAR(128) NOT NULL,
    wallet_address_to   VARCHAR(128) NOT NULL,
    tx_hash             VARCHAR(128) NOT NULL,
    exchange_name       VARCHAR(64)
);

-- The wallet graph is traversed from both ends during BFS.
CREATE INDEX idx_crypto_from ON crypto_activity (wallet_address_from);
CREATE INDEX idx_crypto_to   ON crypto_activity (wallet_address_to);

-- --------------------------------------------------------------- risk layer

-- Rule logic lives in Java, one class per rule. This table holds the tunable
-- part: the weight, and a human-readable statement of the condition. Weights
-- change with an UPDATE, not a redeploy.
CREATE TABLE risk_rules (
    rule_id         UUID          PRIMARY KEY,
    rule_code       VARCHAR(16)   NOT NULL UNIQUE,   -- binds to the Java class, e.g. R-04
    rule_name       VARCHAR(128)  NOT NULL,
    applies_to      VARCHAR(8)    NOT NULL CHECK (applies_to IN ('CARD','PAYMENT','CRYPTO','ALL')),
    threshold_logic TEXT          NOT NULL,
    weight          DECIMAL(5,2)  NOT NULL,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE TABLE risk_assessments (
    assessment_id      UUID         PRIMARY KEY,
    transaction_id     UUID         NOT NULL REFERENCES transactions(transaction_id) ON DELETE CASCADE,
    rule_id            UUID         NOT NULL REFERENCES risk_rules(rule_id),
    triggered_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    score_contribution DECIMAL(5,2) NOT NULL
);

CREATE INDEX idx_assessment_tx ON risk_assessments (transaction_id);

-- ------------------------------------------------------- knowledge for RAG

CREATE TABLE policy_chunks (
    chunk_id  UUID         PRIMARY KEY,
    document  VARCHAR(32)  NOT NULL,        -- AML-004
    title     VARCHAR(200) NOT NULL,
    section   VARCHAR(64),
    body      TEXT         NOT NULL,
    embedding vector(384),
    metadata  JSONB        NOT NULL DEFAULT '{}'
);

-- ------------------------------------------------------------- ai analyses

CREATE TABLE ai_analyses (
    analysis_id     UUID         PRIMARY KEY,
    customer_id     UUID         NOT NULL REFERENCES customers(customer_id),
    requested_by    UUID         NOT NULL REFERENCES operators(operator_id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- exactly what was analysed, so a later reader sees the same evidence
    window_from     TIMESTAMPTZ  NOT NULL,
    window_to       TIMESTAMPTZ  NOT NULL,

    -- our rules decide this
    computed_score  DECIMAL(5,2) NOT NULL,
    computed_level  VARCHAR(8)   NOT NULL CHECK (computed_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    -- the model's own call, kept separately on purpose
    assessed_level  VARCHAR(8)   NOT NULL CHECK (assessed_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    -- disagreement between the two is an audit signal worth surfacing
    levels_diverged BOOLEAN      GENERATED ALWAYS AS (computed_level <> assessed_level) STORED,

    summary         TEXT         NOT NULL,
    recommendations JSONB        NOT NULL,

    provider        VARCHAR(16)  NOT NULL,   -- anthropic | stub
    model           VARCHAR(64)  NOT NULL,
    prompt_version  VARCHAR(16)  NOT NULL,
    input_tokens    INTEGER,
    output_tokens   INTEGER,
    latency_ms      INTEGER,
    raw_response    JSONB
);

CREATE INDEX idx_analysis_customer_time ON ai_analyses (customer_id, created_at DESC);
CREATE INDEX idx_analysis_recommendations ON ai_analyses USING GIN (recommendations);

-- Which policy text the model was actually shown, and how well it matched.
CREATE TABLE ai_analysis_citations (
    analysis_id UUID     NOT NULL REFERENCES ai_analyses(analysis_id) ON DELETE CASCADE,
    chunk_id    UUID     NOT NULL REFERENCES policy_chunks(chunk_id),
    similarity  REAL     NOT NULL,
    rank        SMALLINT NOT NULL,
    PRIMARY KEY (analysis_id, chunk_id)
);
