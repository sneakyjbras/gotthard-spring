-- gotthard-spring — demo operator seed data.
--
-- Two operators, one of each role, so "login by different operators" is
-- demonstrable end to end. See the README for the plaintext demo passwords —
-- they exist only so this seed data is usable, and must never be reused
-- anywhere real.
--
-- Hashes are argon2id, produced by the exact encoder security/ runs at login
-- (Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()) and confirmed to
-- round-trip through PasswordEncoder.matches(...) before being written here —
-- not guessed, not copied from an unrelated tool.

INSERT INTO operators (operator_id, username, display_name, role, created_at) VALUES
    ('11111111-1111-1111-1111-111111111111', 'e.rossi',  'Elena Rossi', 'OPERATOR',   now()),
    ('22222222-2222-2222-2222-222222222222', 'm.keller', 'Marc Keller', 'SUPERVISOR', now());

INSERT INTO operator_credentials (operator_id, password_hash, updated_at) VALUES
    ('11111111-1111-1111-1111-111111111111',
     '$argon2id$v=19$m=16384,t=2,p=1$/4k0udHN8tc9Z18tkwMVKQ$+MEbqaeRLBnvW21K3aQ4jIJaqFxc4anXJpZ9AC5+kfk',
     now()),
    ('22222222-2222-2222-2222-222222222222',
     '$argon2id$v=19$m=16384,t=2,p=1$9WO5rlUxb71wKQfsC+lgRA$v+geAXRgh2hRK1kOZ56yXhz5mLCys6Eq8u9ikTjZGIw',
     now());
