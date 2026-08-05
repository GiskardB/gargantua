# ══════════════════════════════════════════════════════════════════════════════
# Gargantua Runtime — the generic image that executes an agent bundle.
#
# The image is the executor; the bundle is the payload. They version
# independently: roll a bundle forward without rebuilding the image, or patch
# the image without republishing bundles.
#
#   docker build -f agent-runtime/Dockerfile -t gargantua-runtime:1.0 .
#   docker run -v ./customer-agent.gbundle:/bundle:ro \
#              -e LLM_PRIMARY_API_KEY=sk-... gargantua-runtime:1.0
#
# Teams needing bespoke Java tools build their own image FROM this one, adding
# compiled @AgentTool beans — that is library mode applied to the runtime, and
# it is the only supported way to get Java tools into runtime mode (ADR-003).
# ══════════════════════════════════════════════════════════════════════════════

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Dependency layer: copy poms first so a source-only change does not refetch.
COPY pom.xml .
COPY agent-core/pom.xml agent-core/
COPY agent-memory-sdk/pom.xml agent-memory-sdk/
COPY agent-mcp-client/pom.xml agent-mcp-client/
COPY agent-bundle/pom.xml agent-bundle/
COPY agent-engine/pom.xml agent-engine/
COPY agent-runtime/pom.xml agent-runtime/
COPY agent-mcp-server/pom.xml agent-mcp-server/
COPY agent-skill-linter-maven-plugin/pom.xml agent-skill-linter-maven-plugin/
COPY agent-archetype/pom.xml agent-archetype/

COPY agent-core/src agent-core/src
COPY agent-memory-sdk/src agent-memory-sdk/src
COPY agent-mcp-client/src agent-mcp-client/src
COPY agent-bundle/src agent-bundle/src
COPY agent-engine/src agent-engine/src
COPY agent-runtime/src agent-runtime/src

RUN mvn -B -q -pl agent-core,agent-memory-sdk,agent-mcp-client,agent-bundle,agent-engine,agent-runtime \
        -am package -DskipTests

# ── Runtime image ─────────────────────────────────────────────────────────────
# Jammy rather than Alpine: semantic routing pulls in ONNX Runtime, whose native
# library needs glibc.
FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system gargantua && useradd --system --gid gargantua gargantua

WORKDIR /app
COPY --from=builder /build/agent-runtime/target/agent-runtime-*.jar /app/runtime.jar

# Conventional mount point; override with GARGANTUA_BUNDLE or an argument.
ENV GARGANTUA_BUNDLE=/bundle
RUN mkdir -p /bundle && chown -R gargantua:gargantua /app /bundle

USER gargantua
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD ["/bin/sh", "-c", "curl -fsS http://localhost:8080/actuator/health/readiness || exit 1"]

ENTRYPOINT ["java", "-jar", "/app/runtime.jar"]
CMD ["run"]
