package ch.gotthard;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Tests run against the same image production does. The schema needs pgvector, BRIN indexes and a
 * generated column, so stock {@code postgres} would fail and an in-memory database would be a
 * fiction. Keep this image in step with docker-compose.yml and the Helm chart.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    private static final DockerImageName POSTGRES =
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES);
    }
}
