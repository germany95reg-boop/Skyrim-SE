# Skyrim SE Mobile — Vulkan 3D v2.10.1

Read-only ARM64 Vulkan inspector for user-selected Skyrim Special Edition BSA/NIF/DDS assets; not a playable game engine.

## New: per-part material references

- Button **Связи материалов частей NIF · v2.10.1** traces exact `BSTriShape` → `BSLightingShaderProperty` → `BSShaderTextureSet` index references. Lists diffuse `[0]` and normal `[1]` paths for each supported part.
- Invalid/missing/unsupported links are shown explicitly. Material association is not guessed by block order or filename.
- Safe bounds and Skyrim SE 100/12 restrictions; synthetic unit tests cover multiple parts with different or shared materials, missing references, wrong block types and corrupt indices.
- Existing read-only BSA inspection, selected NIF recovery, 8 recent models, DDS, Vulkan shading and composite geometry remain preserved.
- **Composite Vulkan is still untextured**. This is a diagnostic dependency for the upcoming actual multi-material render, not completion of it.
- versionCode 33 / versionName 2.10.0, original package identity and persistent private test signing certificate.

App-only test guide: [DEVICE_TEST_V210_RU.md](DEVICE_TEST_V210_RU.md).
## v2.10.1 — SSE material-link parser hotfix

- Fixed Skyrim SE stream 100 `BSLightingShaderProperty` layout: the 4-byte `Shader Type` precedes the inherited `NiObjectNET` fields. Extra-data count is at +8, not +4, and the BSShaderTextureSet reference is at +40+4*n, not +36+4*n.
- Original v2.10 gave a false diagnostic on `lureargonian01.nif`, `BSTriShape #5` / shader `#6` (“Некорректное количество extra-data ссылок в #6”). That could arise from reading the shader Name as the number of extra-data refs.
- Added synthetic regressions with a real SSE shader prefix (Name=-1 and Shader Type), with variable extra-data list lengths, invalid references and independent materials.
- **Real game-file confirmation pending**: run **«Связи материалов частей NIF · v2.10.1»** on lureargonian01.nif and fishinggear_cluster01.nif and send the results.
- No change to Vulkan rendering logic or game assets. Same package ID and signing key; versionCode 33 supports in-place update from v2.10.
