#!/usr/bin/env bash
set -euo pipefail

ASSETS_MODELS_DIR="/home/aragao/VAB/app/src/demoBundled/assets/models"
mkdir -p "$ASSETS_MODELS_DIR"

USE_QWEN_1_5B="${USE_QWEN_1_5B:-0}"
for arg in "$@"; do
  if [ "$arg" = "--qwen-1.5b" ]; then
    USE_QWEN_1_5B=1
  fi
done

WHISPER_URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin"
WHISPER_SHA="ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb"
WHISPER_TARGET="$ASSETS_MODELS_DIR/whisper-default.bin"

if [ "$USE_QWEN_1_5B" = "1" ]; then
  QWEN_URL="https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
  QWEN_SHA="6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
  QWEN_LABEL="Qwen2.5-1.5B-Instruct Q4_K_M (~1.1 GB)"
else
  QWEN_URL="https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
  QWEN_SHA="74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
  QWEN_LABEL="Qwen2.5-0.5B-Instruct Q4_K_M (~469 MB)"
fi
QWEN_TARGET="$ASSETS_MODELS_DIR/qwen-default.gguf"

echo "[1/2] Baixando Whisper PT-BR aprimorado (ggml-small-q5_1.bin, ~181 MB)..."
if [ ! -s "$WHISPER_TARGET" ] || ! echo "$WHISPER_SHA  $WHISPER_TARGET" | sha256sum -c --status -; then
  curl -fSL "$WHISPER_URL" -o "$WHISPER_TARGET.tmp"
  mv "$WHISPER_TARGET.tmp" "$WHISPER_TARGET"
else
  echo "Whisper atualizado ja presente em $WHISPER_TARGET"
fi

echo "[2/2] Baixando $QWEN_LABEL (qwen-default.gguf)..."
if [ ! -s "$QWEN_TARGET" ] || ! echo "$QWEN_SHA  $QWEN_TARGET" | sha256sum -c --status -; then
  curl -fSL "$QWEN_URL" -o "$QWEN_TARGET.tmp"
  mv "$QWEN_TARGET.tmp" "$QWEN_TARGET"
else
  echo "Qwen atualizado ja presente em $QWEN_TARGET"
fi

ls -lh "$ASSETS_MODELS_DIR"

echo "Verificando integridade criptografica SHA-256 contra os repositorios oficiais..."
echo "$QWEN_SHA  $QWEN_TARGET" | sha256sum -c -
echo "$WHISPER_SHA  $WHISPER_TARGET" | sha256sum -c -

echo "Modelos verificados e prontos para empacotar no APK demoBundled!"
