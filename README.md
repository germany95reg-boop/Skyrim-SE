# Skyrim SE Mobile — Vulkan 3D v2.10.1

Android ARM64 Vulkan viewer of user-provided Skyrim Special Edition BSA/NIF/DDS resources; **not** a playable Skyrim game.

## Fixed: BSLightingShaderProperty layout in Skyrim SE

The first 4 bytes of Skyrim SE `BSLightingShaderProperty` store `Skyrim Shader Type` before the inherited NiObjectNET Name, Extra Data Count and Controller. The old v2.10 parser accidentally read Name (often `-1`) as extra-data count, which yielded `Некорректное количество extra-data ссылок в #6` on the real `lureargonian01.nif`.

- Shader Extra Data Count is now read at **byte offset 8**, not 4.
- Shader `BSShaderTextureSet` reference is read at **byte offset `40 + 4 * extras`**, not `36 + 4 * extras`.
- Original `BSTriShape` handling is untouched (extra-data count at 4, shader reference at `92 + 4 * extras`).
- Synthetic regression tests include an unnamed shader (`Name = -1`), nonzero Skyrim Shader Type, one extra-data reference, separate/shared materials, missing and corrupt references.
- UI reports version v2.10.1. Identical Android application ID, stable test certificate, `versionCode 33`, `versionName 2.10.1`.
- Existing user-verified persistence of last BSA/NIF/Data, composite NIF, DDS and normal-map Vulkan, camera and FPS retained.

**Testing inside the app:** [DEVICE_TEST_V2101_RU.md](DEVICE_TEST_V2101_RU.md).

Composite rendering remains untextured; this is accurate per-part material link **diagnostics**, not complete Skyrim material rendering.
