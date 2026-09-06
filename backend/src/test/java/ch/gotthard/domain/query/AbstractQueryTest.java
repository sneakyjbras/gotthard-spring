package ch.gotthard.domain.query;

import ch.gotthard.TestcontainersConfiguration;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Window-function tests run against real PostgreSQL, because a window function is not a thing an
 * in-memory database imitates — {@code RANGE BETWEEN INTERVAL … PRECEDING} is exactly the feature
 * that would be missing, and the numbers under test are the ones it produces.
 *
 * <p>The annotations match {@code AbstractRepositoryTest} exactly so Spring's context cache treats
 * both suites as one context and starts a single container for the module. {@code @DataJpaTest} does
 * not auto-configure a {@link NamedParameterJdbcTemplate}, so it is built from the container's
 * {@link DataSource}; it still joins the test's transaction through {@code DataSourceUtils}, which is
 * what lets a test insert rows and query them back before the rollback.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
abstract class AbstractQueryTest {

    @Autowired
    private DataSource dataSource;

    protected NamedParameterJdbcTemplate jdbc;

    protected QueryFixtures fixtures;

    @BeforeEach
    void openJdbcAccess() {
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        fixtures = new QueryFixtures(jdbc);
    }
}
