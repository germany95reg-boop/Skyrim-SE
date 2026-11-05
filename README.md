# Skyrim SE Mobile Launcher

Native Android ARM64 Vulkan prototype. **This is not a port of the Skyrim Special Edition game engine.** The application does not execute SkyrimSE.exe, SKSE64, scripts or the Skyrim game world.

## Verified on-device milestones

- v0.6: OBJ pyramid drawn using native Vulkan, working gestures on Samsung Android 17 / Adreno 840.
- v0.7–v1.0: read actual Skyrim SE `Data` contents and BSA 105 / NIF blocks read-only.
- v1.1: real `lureargonian01.nif` BSTriShape wireframe shown on Samsung, 248 edges.
- v1.2: confirmed wireframe and model dimensions 3.27 × 7.74 × 1.26.
- **v1.3: user confirmed correct fit of the entire NIF between the header and controls**; 47% frame margin, last measured display FPS 120 uncapped. This is a graphics-test number, not game performance.

## v1.4 — solid geometry preview of Skyrim SE NIF

**[Successful GitHub Actions build #42064084787](https://github.com/germany95reg-boop/Skyrim-SE/actions/runs/42064084787)**

Artifact `Skyrim-SE-Vulkan-3D-v1.4-APK` contains `app-debug.apk`.

APK size: 3,452,618 bytes. SHA-256: `7e803c60d8f0b0de6db498dba355f6989d4ebca958a59c058ae60f692b8b86f6`.

### Features

- A bounded SSE `BSTriShape` decoder now retains normalized triangle coordinates **as well as** the proven wire-edge coordinates from the same transformation and bounds.
- Android→JNI passes a separate validated array of triangle vertices; previous line rendering remains unchanged.
- A second Vulkan `VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST` graphics pipeline, its own vertex buffer, a device-local depth attachment (D16/D32) and `solid.frag.spv` provide a **neutral lit surface preview**.
- In **Настройки → Ресурсы Skyrim SE → Каталог BSA v105 → selected NIF → Vulkan**, the new **«Поверхность»** button switches from wireframe to solid. Press **«Каркас»** to return. Solid mode is disabled for the built-in cube and OBJ.
- Previous camera presets, gestures, auto-fit, Vulkan smoke tests, BSA/LZ4 read-only indexing and NIF diagnostics are retained.
- Maximum limits: 40,000 vertices, 30,000 unique edges and 30,000 triangles. The app rejects malformed triangle indices and nonfinite coordinate values.

### Verify on Samsung

1. Install v1.4 APK, preserving app data before uninstall if a debug signing mismatch prevents in-place updating.
2. Open `ccBGSSSE001-Fish.bsa`, search for `lureargonian01.nif`, and tap **«Показать BSTriShape в Vulkan · v1.4»**.
3. Confirm the original wireframe is visible. Tap **«Поверхность»** and check neutral purple faces and correct front/back depth occlusion.
4. Rotate the model, try side/top camera presets and **«Вписать»**, and toggle **«Каркас»**.
5. Send screenshots and FPS from both modes; report any crash or visual artifacts.

**CI verified:** Android/NDK compile, GLSL→SPIR-V, BSA/LZ4, NIF block tables, the geometry decoder with 60 fixture checks, adaptive camera with 13 checks, and APK contents including 3 ARM64 libraries and 3 SPIR-V shaders. **The new filled mode has not yet been tested on the Samsung**, even though the prior wireframe has.

No genuine Skyrim textures (DDS), material graph, skinning, animations, parent-node transforms, game rendering or SkyrimSE.exe execution yet. Game files are read only, never rewritten.

## Source

The source snapshot for this build is stored at `.skyrimse-v14-source.tar.xz` and unpacked by GitHub Actions before Gradle compilation; a separate source ZIP can be downloaded from the chat.
