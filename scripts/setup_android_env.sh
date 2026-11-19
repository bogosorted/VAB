#!/usr/bin/env bash
set -euo pipefail

ANDROID_BASE="/home/aragao/Android"
JDK_DIR="$ANDROID_BASE/jdk-17"
SDK_DIR="$ANDROID_BASE/Sdk"
GRADLE_DIR="$ANDROID_BASE/gradle-8.10.2"

mkdir -p "$ANDROID_BASE" "$SDK_DIR/cmdline-tools"

echo "[1/4] Provisionando OpenJDK 17..."
if [ ! -x "$JDK_DIR/bin/java" ]; then
  rm -rf "$JDK_DIR"
  mkdir -p "$JDK_DIR"
  curl -fSL "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" -o /tmp/jdk17.tar.gz
  tar -xzf /tmp/jdk17.tar.gz -C "$JDK_DIR" --strip-components=1
  rm -f /tmp/jdk17.tar.gz
fi

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

echo "[2/4] Provisionando Android Command-line Tools..."
if [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  curl -fSL "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -o /tmp/cmdtools.zip
  rm -rf /tmp/cmdline-tools-extract "$SDK_DIR/cmdline-tools/latest"
  mkdir -p /tmp/cmdline-tools-extract
  unzip -q /tmp/cmdtools.zip -d /tmp/cmdline-tools-extract
  mv /tmp/cmdline-tools-extract/cmdline-tools "$SDK_DIR/cmdline-tools/latest"
  rm -rf /tmp/cmdtools.zip /tmp/cmdline-tools-extract
fi

export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$PATH"

echo "[3/4] Aceitando licencas e instalando Platform 35, Build-Tools, NDK e CMake..."
yes | sdkmanager --licenses > /dev/null || true
sdkmanager --install \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0" \
  "ndk;27.0.12077973" \
  "cmake;3.22.1"

echo "[4/4] Provisionando Gradle 8.10.2..."
if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  curl -fSL "https://services.gradle.org/distributions/gradle-8.10.2-bin.zip" -o /tmp/gradle.zip
  unzip -q /tmp/gradle.zip -d "$ANDROID_BASE"
  rm -f /tmp/gradle.zip
fi

echo "Ambiente Android configurado com sucesso em $ANDROID_BASE!"
