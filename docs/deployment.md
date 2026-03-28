# Deployment

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
| MONGODB_URI | MongoDB connection | mongodb://localhost:27017/gargantua |
| REDIS_URL | Redis connection | redis://localhost:6379 |
| OPENAI_API_KEY | OpenAI API key | — |
| AZURE_OPENAI_KEY | Azure OpenAI key | — |
| ANTHROPIC_API_KEY | Anthropic key | — |
| SPRING_PROFILES_ACTIVE | Spring profile | dev |

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
