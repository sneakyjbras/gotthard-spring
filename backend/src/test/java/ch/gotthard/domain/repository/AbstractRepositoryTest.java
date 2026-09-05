package ch.gotthard.domain.repository;

import ch.gotthard.TestcontainersConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * Common wiring for every repository test: a real PostgreSQL container (never H2 — the schema needs
 * pgvector, BRIN and a generated column), with Flyway applying {@code V1__baseline.sql} and
 * Hibernate then validating against it.
 *
 * <p>{@code @AutoConfigureTestDatabase(Replace.NONE)} stops Spring Boot from swapping in an embedded
 * database, which would otherwise silently override the Testcontainers connection {@link
 * TestcontainersConfiguration} provides via {@code @ServiceConnection}.
 *
 * <p>Every subclass shares this exact configuration, so Spring's test context cache starts the
 * container once for the whole module rather than once per test class.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
abstract class AbstractRepositoryTest {}
