# ═══════════════════════════════════════════════════════════════════
# FitCoach AI — Multi-stage Dockerfile (JVM + GraalVM native)
#
# Build targets:
#   runtime-jvm    — Eclipse Temurin 21 JRE + ZGC (~250 MB, ~2s startup)
#   runtime-native — GraalVM native binary on distroless (~50 MB, <100ms)
#
# Usage:
#   docker build --target runtime-jvm    -t fitcoach:jvm .
#   docker build --target runtime-native -t fitcoach:native .
#
# NOTE: must be built from the monorepo root (context = ../)
#       because agent-example depends on agent-engine, agent-mcp-server, etc.
#       The docker-compose.yml handles this automatically.
# ═══════════════════════════════════════════════════════════════════

# ─────────────────────────────────────────────────────────────────
# Stage 1 — Builder (Maven + JDK 21)
# ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy all POMs first for dependency-cache layer
COPY pom.xml .
COPY agent-core/pom.xml                      agent-core/pom.xml
COPY agent-memory-sdk/pom.xml                agent-memory-sdk/pom.xml
COPY agent-spring-boot-starter/pom.xml       agent-spring-boot-starter/pom.xml
COPY agent-adapters/pom.xml                  agent-adapters/pom.xml
COPY agent-engine/pom.xml                    agent-engine/pom.xml
COPY agent-mcp-server/pom.xml                agent-mcp-server/pom.xml
COPY agent-example-fitcoach/pom.xml          agent-example-fitcoach/pom.xml
COPY agent-shell/pom.xml                     agent-shell/pom.xml
COPY agent-skill-linter-maven-plugin/pom.xml agent-skill-linter-maven-plugin/pom.xml

# Maven wrapper
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw

# Download dependencies (cached unless POMs change)
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw dependency:go-offline -B 2>/dev/null || true

# Copy full source
COPY . .

# Build the example JAR (with all framework modules)
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw clean package -pl agent-example-fitcoach -am -DskipTests -B

# ─────────────────────────────────────────────────────────────────
# Stage 2A — JVM Runtime (Eclipse Temurin 21 JRE)
# ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime-jvm

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /workspace/agent-example-fitcoach/target/*.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]

# ─────────────────────────────────────────────────────────────────
# Stage 2B — GraalVM Native Image Builder
# ─────────────────────────────────────────────────────────────────
FROM ghcr.io/graalvm/native-image-community:21 AS native-builder

WORKDIR /workspace
COPY --from=builder /workspace /workspace

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -Pnative native:compile -pl agent-example-fitcoach -am -DskipTests -B

# ─────────────────────────────────────────────────────────────────
# Stage 2C — Distroless Native Runtime
# ─────────────────────────────────────────────────────────────────
FROM gcr.io/distroless/base-debian12 AS runtime-native

WORKDIR /app

COPY --from=native-builder /workspace/agent-example-fitcoach/target/agent-example app

EXPOSE 8080

ENTRYPOINT ["/app/app"]
