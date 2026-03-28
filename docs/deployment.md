# Deployment

## Publishing the Framework (for maintainers)

### CI Pipeline

Every push to `main` or PR triggers the CI workflow (`.github/workflows/ci.yml`):
- Builds all modules on Java 21 and Java 25
- Runs unit tests
- Verifies `agent-core` has zero Spring dependencies
- Verifies no `javax.*` imports exist

### Release to GitHub Packages

Create a version tag to trigger the release workflow (`.github/workflows/release.yml`):

```bash
git tag v1.0.0
git push origin v1.0.0
```

This will:
1. Build and test all modules
2. Publish JARs to GitHub Packages (`https://maven.pkg.github.com/giskardb/gargantua`)
3. Create a GitHub Release with changelog

**Manual release** (with optional version override):
- Go to Actions → Release → Run workflow
- Optionally set a version and/or dry-run mode

### Consuming the Framework

Users add the GitHub Packages repository to `~/.m2/settings.xml`:
```xml
<servers>
    <server>
        <id>github</id>
        <username>GITHUB_USERNAME</username>
        <password>ghp_YOUR_TOKEN</password>
    </server>
</servers>
```

And to their `pom.xml`:
```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/giskardb/gargantua</url>
    </repository>
</repositories>
```

---

## Local Development — Docker Compose

### JVM mode (default, fast rebuild ~30s)
```bash
docker compose up agent-jvm mongo redis
```

### GraalVM Native (startup < 100ms, build ~5min)
```bash
docker compose --profile native up agent-native mongo redis
```

### Infrastructure only (for running app from IDE)
```bash
docker compose up mongo redis
```

## Dockerfile — Multi-Stage Build

| Target | Base Image | Size | Startup |
|--------|-----------|------|---------|
| runtime-jvm | eclipse-temurin:21-jre-alpine | ~300MB | ~3-5s |
| runtime-native | distroless/base-debian12 | ~50-80MB | <100ms |

```bash
# Build JVM image
docker build --target runtime-jvm -t my-agent:jvm .

# Build native image
docker build --target runtime-native -t my-agent:native .
```

## GraalVM Native Image

### Maven profile
```bash
./mvnw clean package -Pnative -pl agent-example -am -DskipTests
```

### RuntimeHints
GargantuaRuntimeHints registers reflection hints for records and resource patterns for skills. If you add custom records used in JSON serialization, register them:

```java
hints.reflection().registerType(MyRecord.class, MemberCategory.values());
```

## Kubernetes

### Kustomize
```bash
# Dev
kubectl apply -k k8s/overlays/dev

# Staging
kubectl apply -k k8s/overlays/staging

# Production
kubectl apply -k k8s/overlays/prod
```

### Helm
```bash
# Dev
helm install my-agent k8s/helm -f k8s/helm/values-dev.yaml

# Production
helm install my-agent k8s/helm -f k8s/helm/values-prod.yaml
```

### Key Components
- **Deployment** — non-root container, topology spread, pod anti-affinity, graceful shutdown (30s for SSE)
- **Service** — ClusterIP 80→8080
- **HPA** — CPU/memory fallback autoscaler
- **KEDA ScaledObject** — scales on SSE connections + LLM latency p95 (recommended)
- **PDB** — minAvailable: 1 during disruptions
- **ServiceMonitor** — Prometheus scraping at /actuator/prometheus

### Ingress for SSE
Critical NGINX annotations for SSE:
```yaml
nginx.ingress.kubernetes.io/proxy-read-timeout: "60"
nginx.ingress.kubernetes.io/proxy-buffering: "off"
```

### Why Pods Are Stateless
All state is in Redis (working memory, HITL, tool cache) and MongoDB (episodic, knowledge, history, evals, costs). No session affinity needed.

### Sizing Reference
| Env | Replicas | CPU req/limit | RAM req/limit |
|-----|----------|--------------|---------------|
| Dev | 1 | 100m/500m | 256Mi/512Mi |
| Staging | 2 | 250m/1000m | 512Mi/1Gi |
| Prod JVM | 3-20 | 500m/2000m | 768Mi/1.5Gi |
| Prod Native | 3-30 | 250m/1000m | 128Mi/256Mi |

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MONGODB_URI` | MongoDB connection string | `mongodb://localhost:27017/gargantua` |
| `REDIS_URL` | Redis connection URL | `redis://localhost:6379` |
| `SERVER_PORT` | HTTP server port | `8080` |
| `LLM_PRIMARY_PROVIDER` | Primary LLM: `openai` / `azure-openai` / `anthropic` | `openai` |
| `LLM_PRIMARY_MODEL` | Primary model name (e.g. `gpt-4o`) | `gpt-4o` |
| `LLM_PRIMARY_API_KEY` | API key for the primary LLM provider | **(required)** |
| `LLM_PRIMARY_ENDPOINT` | Base URL (required for `azure-openai`) | provider default |
| `LLM_PRIMARY_TEMPERATURE` | Sampling temperature 0.0-1.0 | `0.7` |
| `LLM_PRIMARY_MAX_TOKENS` | Max response tokens | `1000` |
| `LLM_FALLBACK_PROVIDER` | Fallback provider (on primary failure) | `anthropic` |
| `LLM_FALLBACK_MODEL` | Fallback model | `claude-sonnet-4-20250514` |
| `LLM_FALLBACK_API_KEY` | Fallback API key | *(optional)* |
| `LLM_ROUTING_PROVIDER` | Routing model provider (cheap) | `openai` |
| `LLM_ROUTING_MODEL` | Routing model name | `gpt-4o-mini` |
| `LLM_ROUTING_API_KEY` | Routing model API key | same as primary |
| `ROUTING_STRATEGY` | Skill routing: `hybrid` / `semantic` / `llm` | `hybrid` |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `dev` |

## Health Probes
- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`
Custom indicators: LLM provider reachability, skill registry loaded.

## Observability

### Metrics (Micrometer → Prometheus)
Key metrics:
- agent.request.total{skill, status}
- agent.request.duration{skill}
- agent.llm.tokens.input/output{skill, provider}
- agent.tool.calls.total{tool}
- agent.guardrail.blocked{guardrail_name}
- agent.routing.decisions{skill, method}
- agent.sse.connections.active

### Tracing (OpenTelemetry)
Spans: agent.guardrail.input → agent.routing → agent.memory.compose → agent.llm.call → agent.tool.* → agent.guardrail.output

Uses GenAI Semantic Conventions (gen_ai.system, gen_ai.request.model, gen_ai.usage.input_tokens, etc.)
