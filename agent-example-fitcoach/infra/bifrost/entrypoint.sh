#!/bin/sh
set -e

MODEL="${LLM_MODEL:-gpt-4o-mini}"
ROUTING_MODEL="${LLM_ROUTING_MODEL:-gpt-4o-mini}"
API_KEY="${AZURE_OPENAI_API_KEY}"
ENDPOINT="${AZURE_OPENAI_BASE_URL}"
API_VERSION="${AZURE_OPENAI_API_VERSION:-2024-02-01}"

# Costruisci la lista modelli Azure (deduplica se uguali)
if [ "$MODEL" = "$ROUTING_MODEL" ]; then
  AZURE_MODELS_JSON="\"${MODEL}\""
  AZURE_DEPLOYMENTS_JSON="\"${MODEL}\": \"${MODEL}\""
else
  AZURE_MODELS_JSON="\"${MODEL}\", \"${ROUTING_MODEL}\""
  AZURE_DEPLOYMENTS_JSON="\"${MODEL}\": \"${MODEL}\", \"${ROUTING_MODEL}\": \"${ROUTING_MODEL}\""
fi

# Genera config.json con Azure + Ollama
cat > /app/data/config.json <<ENDOFCONFIG
{
  "\$schema": "https://www.getbifrost.ai/schema",
  "providers": {
    "azure": {
      "keys": [
        {
          "name": "azure-primary",
          "value": "${API_KEY}",
          "models": [${AZURE_MODELS_JSON}],
          "weight": 1.0,
          "azure_key_config": {
            "endpoint": "${ENDPOINT}",
            "deployments": {
              ${AZURE_DEPLOYMENTS_JSON}
            },
            "api_version": "${API_VERSION}"
          }
        }
      ]
    },
    "ollama": {
      "keys": [
        {
          "name": "ollama-local",
          "value": "dummy",
          "models": [],
          "weight": 1.0
        }
      ],
      "network_config": {
        "base_url": "http://ollama:11434"
      }
    }
  }
}
ENDOFCONFIG

echo "=== Bifrost config generated ==="
cat /app/data/config.json
echo "================================"

# Avvia Bifrost
exec /app/main -app-dir /app/data -port "${APP_PORT:-8080}" -host "${APP_HOST:-0.0.0.0}" -log-level "${LOG_LEVEL:-info}" -log-style "${LOG_STYLE:-json}"
