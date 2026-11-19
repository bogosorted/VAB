#!/usr/bin/env bash
set -euo pipefail

ASSETS_MODELS_DIR="/home/aragao/VAB/app/src/demoBundled/assets/models"
mkdir -p "$ASSETS_MODELS_DIR"

QWEN_URL="https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
WHISPER_URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"

QWEN_TARGET="$ASSETS_MODELS_DIR/qwen-default.gguf"
WHISPER_TARGET="$ASSETS_MODELS_DIR/whisper-default.bin"

echo "[1/2] Baixando Whisper PT-BR compacto (ggml-base-q5_1.bin)..."
if [ ! -s "$WHISPER_TARGET" ]; then
  curl -fSL "$WHISPER_URL" -o "$WHISPER_TARGET.tmp"
  mv "$WHISPER_TARGET.tmp" "$WHISPER_TARGET"
else
  echo "Whisper ja presente em $WHISPER_TARGET"
fi

echo "[2/2] Baixando Qwen2.5-0.5B-Instruct Q4_K_M (qwen-default.gguf)..."
if [ ! -s "$QWEN_TARGET" ]; then
  curl -fSL "$QWEN_URL" -o "$QWEN_TARGET.tmp"
  mv "$QWEN_TARGET.tmp" "$QWEN_TARGET"
else
  echo "Qwen ja presente em $QWEN_TARGET"
fi

ls -lh "$ASSETS_MODELS_DIR"

echo "Verificando integridade criptografica SHA-256 contra os repositorios oficiais..."
echo "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db  $QWEN_TARGET" | sha256sum -c -
echo "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898  $WHISPER_TARGET" | sha256sum -c -

echo "Modelos verificados e prontos para empacotar no APK demoBundled!"
