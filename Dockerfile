# ─────────────────────────────────────────────────────────────
# Stage 1 – builder (Maven + JDK 21)
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy all POMs first for dependency-cache layer
COPY pom.xml .
COPY agent-core/pom.xml                      agent-core/pom.xml
COPY agent-memory-sdk/pom.xml                agent-memory-sdk/pom.xml
COPY agent-spring-boot-starter/pom.xml       agent-spring-boot-starter/pom.xml
COPY agent-adapters/pom.xml                  agent-adapters/pom.xml
COPY agent-mcp-server/pom.xml                agent-mcp-server/pom.xml
COPY agent-example/pom.xml                   agent-example/pom.xml
COPY agent-shell/pom.xml                     agent-shell/pom.xml
COPY agent-skill-linter-maven-plugin/pom.xml agent-skill-linter-maven-plugin/pom.xml

# Download dependencies (cached unless POMs change)
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw dependency:go-offline -B 2>/dev/null || true

# Copy source
COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw clean package -pl agent-example -am -DskipTests -B

# ─────────────────────────────────────────────────────────────
# Stage 2A – JVM runtime
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime-jvm

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /workspace/agent-example/target/*.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]

# ─────────────────────────────────────────────────────────────
# Stage 2B – GraalVM native-image builder
# ─────────────────────────────────────────────────────────────
FROM ghcr.io/graalvm/native-image-community:21 AS native-builder

WORKDIR /workspace
COPY --from=builder /workspace /workspace

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -Pnative native:compile -pl agent-example -am -DskipTests -B

# ─────────────────────────────────────────────────────────────
# Stage 2C – Distroless native runtime
# ─────────────────────────────────────────────────────────────
FROM gcr.io/distroless/base-debian12 AS runtime-native

WORKDIR /app

COPY --from=native-builder /workspace/agent-example/target/agent-example app

EXPOSE 8080

ENTRYPOINT ["/app/app"]
