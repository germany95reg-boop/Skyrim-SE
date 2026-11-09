#!/usr/bin/env bash
# Release candidate checks: installation identity, real Vulkan assets and ABI.
# A successful Gradle task alone is NOT sufficient evidence of a valid APK.
set -euo pipefail
APK="${1:-}"
if [[ ! -s "$APK" ]]; then echo "Usage: verify_apk.sh <built APK>" >&2; exit 2; fi
AAPT="$ANDROID_HOME/build-tools/35.0.0/aapt"
SIGNER="$ANDROID_HOME/build-tools/35.0.0/apksigner"
[[ -x "$AAPT" && -x "$SIGNER" ]] || { echo "Android build-tools 35.0.0 missing" >&2; exit 2; }

INFO="$("$AAPT" dump badging "$APK" | head -1)"
echo "$INFO"
grep -Fq "name='dev.skyrimsemobile.launcher'" <<< "$INFO"
grep -Fq "versionCode='59'" <<< "$INFO"
grep -Fq "versionName='2.36.0'" <<< "$INFO"

FILES="$(unzip -Z1 "$APK")"
for path in \
    'lib/arm64-v8a/libskyrimse_scene.so' \
    'lib/arm64-v8a/libskyrimse_renderer.so' \
    'assets/textured.vert.spv' \
    'assets/textured.frag.spv' \
    'assets/cube.vert.spv' \
    'assets/cube.frag.spv' \
    'assets/solid.frag.spv' \
    'assets/textured_safe.vert.spv' \
    'assets/textured_safe.frag.spv' \
    'assets/textured_normal_safe.frag.spv' \
    'assets/textured_legacy.frag.spv'; do
    grep -Fxq "$path" <<< "$FILES" || { echo "APK is missing $path" >&2; exit 1; }
done

# Normal end-user APK MUST be ARM64 only. x86_64 is opted in only for CI emulation.
if grep -Eq '^lib/x86_64/' <<< "$FILES"; then
  echo "Release APK unexpectedly includes x86_64 emulator binaries" >&2
  exit 1
fi
"$SIGNER" verify --verbose --print-certs "$APK"
"$SIGNER" verify --print-certs "$APK" | grep -Fiq \
    "16b9f10c71fe290e4455df39b9344b9b8b282479f5b0a135f358497e593bb2d7"
echo "PASS APK identity, in-place upgrade certificate, ARM64 libs and compiled Vulkan shaders"
