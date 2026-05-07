# ═══════════════════════════════════════════════════════════════════
# Weather Agent — Dockerfile (monorepo-aware)
#
# Build context MUST be the monorepo root, not this directory, because
# this example depends on the sibling Gargantua modules (agent-engine,
# agent-mcp-server, agent-skill-linter-maven-plugin) installed locally —
# they're not on Maven Central.
#
# The docker-compose.yml in this directory handles the context for you.
# To build manually from the monorepo root:
#   docker build -f agent-example-weather/Dockerfile -t weather-agent:jvm .
# ═══════════════════════════════════════════════════════════════════

FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Module POMs first — gives Docker a stable cache layer that only invalidates
# when a pom.xml changes (not on every code edit).
COPY pom.xml .
COPY agent-core/pom.xml                      agent-core/pom.xml
COPY agent-memory-sdk/pom.xml                agent-memory-sdk/pom.xml
COPY agent-engine/pom.xml                    agent-engine/pom.xml
COPY agent-mcp-server/pom.xml                agent-mcp-server/pom.xml
COPY agent-example-fitcoach/pom.xml          agent-example-fitcoach/pom.xml
COPY agent-example-cookbook/pom.xml          agent-example-cookbook/pom.xml
COPY agent-example-weather/pom.xml           agent-example-weather/pom.xml
COPY agent-skill-linter-maven-plugin/pom.xml agent-skill-linter-maven-plugin/pom.xml
COPY agent-archetype/pom.xml                 agent-archetype/pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl agent-example-weather -am dependency:go-offline -B 2>/dev/null || true

# Full source after the dep cache layer
COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl agent-example-weather -am clean package -DskipTests -B

# Glibc-based runtime (NOT Alpine/musl) because Gargantua's semantic routing
# pulls in ONNX Runtime via langchain4j-embeddings, whose native libonnxruntime.so
# depends on libstdc++.so.6 — not available on Alpine. Ubuntu Jammy ships it.
FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system app && useradd --system --gid app app
USER app
WORKDIR /app
COPY --from=builder /workspace/agent-example-weather/target/*.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
