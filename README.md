# Skyrim SE Mobile Launcher

**Experimental Android ARM64 / Vulkan launcher.** This repository contains a Vulkan graphics test and read-only Skyrim SE resource inspectors — **not** a complete Skyrim Special Edition engine port.

## Confirmed on Samsung Android 17 / Adreno 840

- v0.6: user saw the OBJ pyramid and verified touch camera controls on the real device.
- v0.7: user enumerated 23 BSA archives and 10 ESM/ESP/ESL plugins in Skyrim SE `Data`, and read BSA v105 headers.
- v0.8: user read the complete catalogue of `ccBGSSSE001-Fish.bsa`: 110 folders, 744 files, 231 NIF entries, flags `0x83`, searchable index 744/744.
- **v0.9: user successfully read real NIF bytes** from the BSA: `meshes/creationclub/bgssse001/clutter/lureargonian01.nif`, offset `0x34d22fe`, 5092 bytes, not compressed, **Gamebryo File Format v20.2.0.7**, without modifying the archive.

## v1.0 — inspect a real NIF's block table

**GitHub Actions: [v1.0 SUCCESS](https://github.com/germany95reg-boop/Skyrim-SE/actions/runs/42046644853)**. Download artifact `Skyrim-SE-Vulkan-3D-v1.0-APK` and install the contained `app-debug.apk`.

On the device, open **Настройки → Ресурсы Skyrim SE → Каталог BSA v105**, select `ccBGSSSE001-Fish.bsa`, search `lureargonian01.nif`, and tap **Исследовать блоки выбранного NIF · v1.0**.

The new read-only `NifBlockInspector` handles NIF 20.2.0.7, little-endian, Bethesda user version 12 and stream versions 83/100. It:

- Parses header export strings, block type names, block type indices, per-block byte sizes, NIF string table, group table and footer root references.
- Lists counts of block types and first 32 blocks with bounded offsets.
- Detects *candidate mesh block types* (`BSTriShape`, `BSDynamicTriShape`, `BSSubIndexTriShape`, `BSMeshLODTriShape`, older `NiTriShape`).
- Rejects truncated data, oversized tables, invalid type indices, invalid root references and inconsistent byte ranges.
- Does not write or export Skyrim files. BSA entry extraction remains bounded to 8 MiB packed and 16 MiB decoded.

**Tests passed in CI:** BSA catalogue contracts, synthetic NIF block table unit tests (14 checks), single-entry NIF extraction and LZ4 tests, shader compilation and Android/NDK build. The built APK contains the `NifBlockInspector` bytecode, both compiled SPIR-V shaders and the existing three ARM64 libraries.

**Not yet validated on the actual NIF:** the new block table parser v1.0; this needs an on-device test and report.

**Not implemented:** actual decoding of geometry and triangle indices from `BSTriShape`, rendering the real NIF in Vulkan, textures, skeleton/animation, SkyrimSE.exe execution, SKSE64 or game mod integration. All previously working Vulkan and OBJ graphics tests remain available.

## Source layout and testing

Android SDK 35, Java 17, Gradle 8.9, NDK 27, CMake, GLSL → SPIR-V. CI source: `.skyrimse-v10-source.tar.xz.b64`; the archived source is unpacked by the workflow before build. This packed arrangement is temporary; a conventional Git source tree can replace it later.
