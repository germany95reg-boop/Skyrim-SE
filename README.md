# Skyrim SE Mobile Launcher

Experimental **Android ARM64 / Vulkan** prototype for reading Skyrim Special Edition resources and viewing NIF meshes. **This is not a port of SkyrimSE.exe**: SKSE64, actual game execution, Havok, gameplay, animation and the Skyrim rendering engine are not included.

## Confirmed on Samsung / Adreno 840

- v0.6: native Vulkan OBJ pyramid and touch gestures.
- v0.7–v1.0: real game Data and BSA 105 catalogues, NIF block structures (read-only).
- v1.1–v1.3: real `lureargonian01.nif` wireframe, 112 vertices, 140 triangles, 248 unique edges; centered auto-fit verified.
- **v1.4: user supplied a screenshot of the real NIF rendered with filled purple surfaces** at reported 120 FPS, 751 frames, 1440×2915; the object is fully visible and the surface mode runs. These figures are graphics-demo metrics, not Skyrim game performance.

## v1.5 — enumerate NIF material texture paths

**[GitHub Actions #42068104478 — SUCCESS](https://github.com/germany95reg-boop/Skyrim-SE/actions/runs/42068104478)**.

Artifact name: `Skyrim-SE-Vulkan-3D-v1.5-APK`, containing `app-debug.apk`.
APK: 3,468,994 bytes; SHA-256 `5cb4450738e6c4ebad32bd48075cfbfd825c4daf0df028c6b2f44503d0edf929`.

### Changes

- Added the **«Пути текстур NIF · v1.5»** action in the BSA v105 catalogue screen.
- Reads *one* selected NIF entry in RAM, validates NIF 20.2.0.7 block table and enumerates all `BSShaderTextureSet` blocks. The output shows named slots: diffuse, normal/gloss, glow/skin, parallax/height, environment, masks and additional slots.
- Counts `BSLightingShaderProperty` and `BSTriShape` blocks to provide context. The relationship between specific shader and texture-set blocks is **not yet validated**.
- Bounds-checks 64 sets, 32 slots/set, 1024 UTF-8 bytes/path. Paths are strings for display, **never treated as writable filesystem paths**.
- Existing solid / wireframe Vulkan renderer, depth test, camera auto-fit, OBJ test, BSA 105 index, and single NIF LZ4 reader remain intact.

**Not implemented:** DDS decoding, looking up those DDS in other BSA archives, UV coordinate sampling, applying textures on triangles, actual Skyrim material rendering. The NIF is still displayed with the same simple neutral purple solid shading.

### On-device test

1. Install v1.5 (if Android refuses an update due to debug signature, back up relevant app data before uninstalling an older build).
2. Go to **Настройки → Ресурсы Skyrim SE → Каталог BSA v105**.
3. Open `ccBGSSSE001-Fish.bsa`, search for `lureargonian01.nif`.
4. Tap **«Пути текстур NIF · v1.5»**, then **«Скопировать список и отчёт»**.
5. Send the resulting text to determine which `Skyrim - Textures*.bsa` / Creation Club archive contains the referenced DDS files.

Tests in CI: 25 synthetic material-table checks, 60 geometry checks, 14 NIF block checks, 13 camera tests, BSA/LZ4 checks, Android/NDK build and all three SPIR-V shaders. APK was examined: `NifMaterialInspector` exists in the DEX, three ARM64 renderer libraries and three SPIR-V shaders are present. **The new material inspector has not yet been exercised on the user's actual NIF on device.**

Source snapshot in this repository: `.skyrimse-v15-source.tar.xz`. The game directory is read-only.
