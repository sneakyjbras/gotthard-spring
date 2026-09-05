plugins {
	// Java 25 is not installed on every machine; let Gradle fetch the toolchain
	// so `./gradlew` works from a clean checkout with only a JDK 17+ present.
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "gotthard-spring"
