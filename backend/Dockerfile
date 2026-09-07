# syntax=docker/dockerfile:1
#
# Multi-stage build for the gotthard-spring backend.
#
# Jar strategy: LAYERED, not a plain fat jar. The Spring Boot Gradle plugin
# (applied by build.gradle.kts) writes a Spring-Boot-Layers-Index into every
# bootJar by default — nothing extra to configure — splitting the jar into
# dependencies / spring-boot-loader / snapshot-dependencies / application.
# `java -Djarmode=tools ... extract --layers` (no `--launcher`) turns that
# into a THIN application jar (Main-Class is the app itself, with a
# Class-Path manifest entry pointing at ../lib/*.jar) plus a sibling
# dependencies/lib/ directory of the ~115 third-party jars (this app pulls in
# the anthropic-java SDK, which is most of the bulk). No Spring Boot loader
# classes are needed at all with this layout — `java -jar app.jar` resolves
# the Class-Path relative to the jar's own location. The runtime stage COPYs
# lib/ and the app jar as separate layers, so an application-code change only
# invalidates the small, last-COPY'd layer; the dependency jars stay cached
# across rebuilds. This was straightforward — the plugin/jarmode does the
# work — so there was no reason to fall back to a plain fat jar.
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace

# Wrapper + build scripts first so dependency resolution is its own Docker
# layer, cached until build.gradle.kts / settings.gradle.kts actually change.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew

COPY src ./src

# The Gradle toolchain plugin (settings.gradle.kts) wants JDK 25; this base
# image's own JAVA_HOME already satisfies it, so no extra toolchain download.
RUN ./gradlew bootJar -x test --no-daemon

# --layers with no explicit list extracts every layer into its own directory;
# the app jar keeps its version in the filename, so pin it to app.jar for a
# stable COPY/ENTRYPOINT path below.
RUN java -Djarmode=tools -jar build/libs/*.jar extract --layers --destination /workspace/extracted \
    && mv /workspace/extracted/application/*.jar /workspace/extracted/application/app.jar

# --- runtime --------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

# Fixed, non-root UID/GID (matches the chart's pod securityContext).
RUN addgroup -g 1001 spring && adduser -D -H -u 1001 -G spring spring

WORKDIR /app

COPY --from=build --chown=spring:spring /workspace/extracted/dependencies/lib/ ./lib/
COPY --from=build --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./lib/
COPY --from=build --chown=spring:spring /workspace/extracted/application/app.jar ./app.jar

USER spring:spring
EXPOSE 8080

# Virtual threads (spring.threads.virtual.enabled=true) make this an IO-bound
# workload; container-aware defaults (JDK 10+) size the heap from the pod's
# memory limit, so no -Xmx is hardcoded here — the chart's resources.limits
# is the one place that number lives.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
