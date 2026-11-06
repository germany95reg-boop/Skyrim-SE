#!/usr/bin/env bash
# Compile every shader for Android Vulkan, fail on GLSL/SPIR-V incompatibility.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/app/src/main/shaders"
DST="$ROOT/app/src/main/assets"
command -v glslangValidator >/dev/null || { echo "Missing glslangValidator" >&2; exit 2; }
mkdir -p "$DST"
for spec in "cube.vert:vert" "cube.frag:frag" "solid.frag:frag" \
            "textured.vert:vert" "textured.frag:frag" \
            "textured_safe.vert:vert" "textured_safe.frag:frag" \
            "textured_normal_safe.frag:frag" \
            "textured_legacy.frag:frag"; do
    filename="${spec%%:*}"
    stage="${spec##*:}"
    glslangValidator -V --target-env vulkan1.0 -S "$stage" \
        "$SRC/$filename" -o "$DST/$filename.spv"
    test -s "$DST/$filename.spv"
    spirv-val --target-env vulkan1.0 "$DST/$filename.spv"
done
echo "PASS nine Vulkan GLSL shaders compiled and SPIR-V validated for Vulkan 1.0"
