# Skyrim SE Mobile Launcher

Android ARM64 project with a separate Vulkan graphics test. **The Skyrim Special Edition game engine is not connected**. The launcher does not execute SkyrimSE.exe or SKSE64.

## On-device progress

- **v0.6:** Owner confirmed native Vulkan display of a touch-controlled OBJ pyramid on Samsung Android 17 / Adreno 840.
- **v0.7:** Owner confirmed BSA v105 header reading and Data directory enumeration.
- **v0.8:** Owner confirmed the **complete directory listing** for `ccBGSSSE001-Fish.bsa`: **110 folders, 744 files, 231 NIF entries, flags 0x83**, searchable index 744/744. No game files were modified.

## v0.9 — Inspect one NIF entry in a BSA v105 archive

**CI status: SUCCESS.** [GitHub Actions v0.9](https://github.com/germany95reg-boop/Skyrim-SE/actions/runs/42044148245). Download artifact `Skyrim-SE-Vulkan-3D-v0.9-APK`; it contains `app-debug.apk`.

The new BSA screen can now read exactly **one** selected NIF record from a BSA v105 archive and check its Gamebryo/NetImmerse header:

1. Open **Настройки → Ресурсы Skyrim SE → Каталог BSA v105**.
2. Select `ccBGSSSE001-Fish.bsa` via Android's document picker.
3. In search, type `lureargonian01.nif`. It should match `meshes/creationclub/bgssse001/clutter/lureargonian01.nif`, 5092 bytes, reported as uncompressed in v0.8.
4. Tap **Прочитать первый найденный NIF (только заголовок)**.
5. Copy the report and check whether it says `Распознано: true` and shows a NIF format version.

The app reads only the chosen archive record through Android's read-only document URI. It supports uncompressed records, optional embedded file-name prefixes, and **LZ4 Frame** compressed records using a pure-Java decoder. It enforces **8 MiB maximum packed** and **16 MiB maximum decoded**, checks for truncation and mismatched decompressed sizes, and **never writes files** to the game directory or local disk.

**Tests:** the v0.9 GitHub Actions run succeeded, including an executable synthetic fixture for LZ4 frame + optional filename prefix, bounded length checks, Java/Gradle compilation and Vulkan shader build. The actual NIF payload from the user's BSA archive has **not yet been tested in v0.9**.

### Not yet implemented

- NIF block/mesh parser, textures, rendering actual NIF models.
- Skyrim game engine, executable translation/recompilation, game-world scripting and SKSE64 integration.
- APK installation and runtime validation of v0.9 on the owner's Samsung.

Development continues exclusively in this `Skyrim-SE` repository; previous Vulkan/OBJ checks remain available.
