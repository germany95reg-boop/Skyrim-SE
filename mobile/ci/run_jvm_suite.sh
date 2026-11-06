#!/usr/bin/env bash
# Run the complete CURRENT pure-Java Skyrim SE test suite on a JVM.
# No device, APK, Android SDK or proprietary BSA contents required.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_DIR="$ROOT/app/src/main/java/dev/skyrimsemobile/launcher"
TEST_DIR="$ROOT/tests"
OUT="${JVM_TEST_OUT:-$(mktemp -d)}"
cleanup() {
  if [[ -z "${JVM_TEST_OUT:-}" ]]; then rm -rf "$OUT"; fi
}
trap cleanup EXIT
mkdir -p "$OUT"

# Match the Gradle dependency; no unversioned or silently substituted JAR.
if [[ -z "${LZ4_JAR:-}" ]]; then
  CACHE="$HOME/.gradle/caches/modules-2/files-2.1/org.lz4/lz4-java/1.8.0"
  LZ4_JAR="$(find "$CACHE" -name 'lz4-java-1.8.0.jar' -type f -print -quit 2>/dev/null || true)"
fi
if [[ ! -s "${LZ4_JAR:-}" ]]; then
  echo "ERROR: lz4-java 1.8.0 is missing. Run Gradle dependency resolution first." >&2
  exit 2
fi

# Android Activity/Context-bound classes are tested by the separate emulator
# suite. This explicit list prevents Android imports accidentally entering JVM CI.
sources=(
  BsaCatalogReader BsaDdsInspector BsaDdsPixelReader BsaEntryInspector
  DdsArchiveFinder DdsBcDecoder DdsHeaderInspector DdsNormalResampler
  NifBlockInspector NifCatalogSelection NifCompositeDdsAtlas
  NifGeometryDecoder NifGeometryTransfer NifMaterialInspector
  NifMaterialLinkInspector NifPartDdsAvailability NifPartTextureSelection
  NifSceneComposer ObjWireParser PeHeaderInspector PreviewLighting
  PreviewMaterial RecentNifPaths ResourceHeaderInspector SceneFraming
)
args=()
for name in "${sources[@]}"; do
  [[ -s "$SOURCE_DIR/$name.java" ]] || { echo "Missing source $name.java" >&2; exit 2; }
  args+=("$SOURCE_DIR/$name.java")
done

shopt -s nullglob
tests=("$TEST_DIR"/*Test.java)
if (( ${#tests[@]} < 20 )); then
  echo "ERROR: expected at least 20 JVM regression test classes, found ${#tests[@]}" >&2
  exit 2
fi
echo "Compiling ${#sources[@]} pure-Java modules + ${#tests[@]} regression tests"
javac -encoding UTF-8 -cp "$LZ4_JAR" -d "$OUT" "${args[@]}" "${tests[@]}"

passed=0
for test in "${tests[@]}"; do
  name="${test##*/}"
  name="${name%.java}"
  if grep -Eq '^[[:space:]]*package[[:space:]]+dev\.skyrimsemobile\.launcher;' "$test"; then
    class="dev.skyrimsemobile.launcher.$name"
  else
    class="$name"
  fi
  echo "==> $class"
  java -ea -cp "$OUT:$LZ4_JAR" "$class"
  ((passed+=1))
done

echo "PASS all $passed JVM regression programs (synthetic fixtures; no game assets)"
