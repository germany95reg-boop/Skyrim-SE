#!/usr/bin/env bash
# Reproducible Android 35 + NDK/CMake + GLSL toolchain for GitHub CI.
set -euo pipefail
: "${ANDROID_HOME:?ANDROID_HOME must be set by Android SDK setup action}"
sdkmanager --install "platforms;android-35" "build-tools;35.0.0" "ndk;27.2.12479018"
if [[ ! -x "$ANDROID_HOME/cmake/3.22.1/bin/cmake" ]]; then
  ok=0
  for attempt in 1 2 3; do
    echo "CMake 3.22.1 install attempt $attempt/3"
    if timeout 160s sdkmanager --install "cmake;3.22.1"; then
      ok=1
      break
    fi
    rm -rf "$ANDROID_HOME/cmake/3.22.1"
    sleep 5
  done
  if (( !ok )); then echo "ERROR: CMake SDK download failed" >&2; exit 1; fi
fi
test -x "$ANDROID_HOME/cmake/3.22.1/bin/cmake"
sudo apt-get update -qq
sudo apt-get install -y glslang-tools spirv-tools
bash "$(dirname "$0")/compile_shaders.sh"
