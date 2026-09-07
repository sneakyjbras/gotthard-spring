plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "7.0.4"
}

group = "ch.gotthard"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Claude, behind ch.gotthard.ai.analysis.LlmClient. Not managed by the Boot BOM, so pinned
	// explicitly. The stub adapter is what runs without an ANTHROPIC_API_KEY, so nothing about the
	// build or the test suite depends on this jar being reachable at run time.
	implementation("com.anthropic:anthropic-java:2.61.0")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	// Hibernate's own pgvector support (PolicyChunk#embedding, mapped via SqlTypes.VECTOR_FLOAT32).
	// Not managed by the Boot BOM, so pinned explicitly — kept in step with the hibernate-core version
	// spring-boot-starter-data-jpa resolves (see `./gradlew dependencies`) since the two ship together
	// upstream.
	implementation("org.hibernate.orm:hibernate-vector:7.4.5.Final")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	runtimeOnly("org.postgresql:postgresql")
	// spring-security-crypto declares Bouncy Castle as optional; Argon2PasswordEncoder throws at
	// runtime without it. Not managed by the Boot BOM, so the version is pinned explicitly here.
	runtimeOnly("org.bouncycastle:bcprov-jdk18on:1.85.2")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

spotless {
	java {
		palantirJavaFormat("2.68.0")
		removeUnusedImports()
		trimTrailingWhitespace()
		endWithNewline()
	}
}
