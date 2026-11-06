# Skyrim SE Mobile — Vulkan 3D v2.10

Read-only ARM64 Vulkan inspector for user-selected Skyrim Special Edition BSA/NIF/DDS assets; not a playable game engine.

## New: per-part material references

- Button **Связи материалов частей NIF · v2.10** traces exact `BSTriShape` → `BSLightingShaderProperty` → `BSShaderTextureSet` index references. Lists diffuse `[0]` and normal `[1]` paths for each supported part.
- Invalid/missing/unsupported links are shown explicitly. Material association is not guessed by block order or filename.
- Safe bounds and Skyrim SE 100/12 restrictions; synthetic unit tests cover multiple parts with different or shared materials, missing references, wrong block types and corrupt indices.
- Existing read-only BSA inspection, selected NIF recovery, 8 recent models, DDS, Vulkan shading and composite geometry remain preserved.
- **Composite Vulkan is still untextured**. This is a diagnostic dependency for the upcoming actual multi-material render, not completion of it.
- versionCode 32 / versionName 2.10.0, original package identity and persistent private test signing certificate.

App-only test guide: [DEVICE_TEST_V210_RU.md](DEVICE_TEST_V210_RU.md).