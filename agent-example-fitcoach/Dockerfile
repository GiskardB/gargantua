# ═══════════════════════════════════════════════════════════════════
# FitCoach AI — Multi-stage Dockerfile (JVM + GraalVM native)
#
# Build targets:
#   runtime-jvm    — Eclipse Temurin 21 JRE + ZGC (~250 MB, ~2s startup)
#   runtime-native — GraalVM native binary on distroless (~50 MB, <100ms)
#
# Usage (run from monorepo root):
#   docker build -f agent-example-fitcoach/Dockerfile --target runtime-jvm    -t fitcoach:jvm .
#   docker build -f agent-example-fitcoach/Dockerfile --target runtime-native -t fitcoach:native .
#
# NOTE: build context MUST be the monorepo root because fitcoach depends on
#       the local Gargantua modules (agent-engine, agent-mcp-server, etc.).
#       The docker-compose.yml in this directory handles this automatically.
# ═══════════════════════════════════════════════════════════════════

# ─────────────────────────────────────────────────────────────────
# Stage 1 — Builder (Maven 3.9 + JDK 21)
# ─────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /workspace

# Copy all module POMs first for dependency-cache layer
COPY pom.xml .
COPY agent-core/pom.xml                      agent-core/pom.xml
COPY agent-memory-sdk/pom.xml                agent-memory-sdk/pom.xml
COPY agent-engine/pom.xml                    agent-engine/pom.xml
COPY agent-mcp-server/pom.xml                agent-mcp-server/pom.xml
COPY agent-example-fitcoach/pom.xml          agent-example-fitcoach/pom.xml
COPY agent-skill-linter-maven-plugin/pom.xml agent-skill-linter-maven-plugin/pom.xml
COPY agent-archetype/pom.xml                 agent-archetype/pom.xml

# Download dependencies (cached unless POMs change)
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B 2>/dev/null || true

# Copy full source
COPY . .

# Build the example JAR (with all framework modules it transitively needs)
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -pl agent-example-fitcoach -am -DskipTests -B

# ─────────────────────────────────────────────────────────────────
# Stage 2A — JVM Runtime (Eclipse Temurin 21 JRE on Ubuntu Jammy)
# Glibc-based (NOT Alpine/musl) because Gargantua's semantic routing
# pulls in ONNX Runtime (via langchain4j-embeddings), whose native
# libonnxruntime.so depends on libstdc++.so.6 — which is not available
# on Alpine. Ubuntu Jammy ships libstdc++ by default.
# ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime-jvm

RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

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
    mvn -Pnative native:compile -pl agent-example-fitcoach -am -DskipTests -B

# ─────────────────────────────────────────────────────────────────
# Stage 2C — Distroless Native Runtime
# ─────────────────────────────────────────────────────────────────
FROM gcr.io/distroless/base-debian12 AS runtime-native

WORKDIR /app

COPY --from=native-builder /workspace/agent-example-fitcoach/target/agent-example app

EXPOSE 8080

ENTRYPOINT ["/app/app"]
