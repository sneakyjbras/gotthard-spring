package ch.gotthard.service;

import ch.gotthard.TestcontainersConfiguration;
import ch.gotthard.domain.query.QueryFixtures;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The use cases against the whole application: real wiring, real PostgreSQL, real SQL.
 *
 * <p>{@code @Transactional} rolls each test back, which is what lets a test empty {@code risk_rules}
 * and put its own weights in without disturbing the migration's. The services' own transactions join
 * the test's, so a repository write and a JDBC read see each other.
 *
 * <p>{@code @AutoConfigureMockMvc} is here even though these tests make no HTTP request: it makes the
 * context configuration identical to the API and authentication suites, so Spring's context cache
 * starts one application and one container for all of them rather than one per suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
abstract class AbstractServiceIntegrationTest {

    @Autowired
    private DataSource dataSource;

    protected QueryFixtures fixtures;

    protected NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void openJdbcAccess() {
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        fixtures = new QueryFixtures(jdbc);
    }
}
