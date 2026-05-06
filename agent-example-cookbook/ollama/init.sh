#!/bin/sh
# One-shot: pulla i modelli elencati in OLLAMA_MODELS (csv) e fa un warm-up
# per evitare latenza JIT al primo prompt della demo. Il container Ollama
# principale deve gia essere up (depends_on healthy).
set -u

OLLAMA_MODELS="${OLLAMA_MODELS:-qwen2.5:0.5b,gemma3:1b,llama3.2:1b}"
OLLAMA_HOST="${OLLAMA_HOST:-http://ollama:11434}"
export OLLAMA_HOST

echo "[ollama-init] target host: $OLLAMA_HOST"
echo "[ollama-init] models:      $OLLAMA_MODELS"

IFS=','
for m in $OLLAMA_MODELS; do
  m=$(echo "$m" | sed 's/^ *//;s/ *$//')
  [ -z "$m" ] && continue
  echo "[ollama-init] pulling $m ..."
  if ! ollama pull "$m"; then
    echo "[ollama-init] WARN: pull $m failed, continuing"
  fi
done

# Warm-up: un prompt minimale per forzare il load del modello in memoria.
# Se la macchina e' scarica di RAM, Ollama scarichera' gli altri a comando:
# va bene, basta che il primo hit non paghi il cold start.
for m in $OLLAMA_MODELS; do
  m=$(echo "$m" | sed 's/^ *//;s/ *$//')
  [ -z "$m" ] && continue
  echo "[ollama-init] warm-up $m ..."
  ollama run "$m" "ok" >/dev/null 2>&1 || echo "[ollama-init] WARN: warm-up $m failed"
done

echo "[ollama-init] done"
exit 0
