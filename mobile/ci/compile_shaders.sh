#!/usr/bin/env bash
# Compile every shader for Android Vulkan, fail on GLSL/SPIR-V incompatibility.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/app/src/main/shaders"
DST="$ROOT/app/src/main/assets"
command -v glslangValidator >/dev/null || { echo "Missing glslangValidator" >&2; exit 2; }
mkdir -p "$DST"
for spec in "cube.vert:vert" "cube.frag:frag" "solid.frag:frag" \
            "textured.vert:vert" "textured.frag:frag"; do
    filename="${spec%%:*}"
    stage="${spec##*:}"
    glslangValidator -V --target-env vulkan1.0 -S "$stage" \
        "$SRC/$filename" -o "$DST/$filename.spv"
    test -s "$DST/$filename.spv"
done
echo "PASS five Vulkan GLSL shaders compiled for Vulkan 1.0"
