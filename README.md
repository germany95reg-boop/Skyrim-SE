# Skyrim SE Mobile Launcher — v2.1

**Skyrim SE Mobile** is an experimental Android ARM64/Vulkan NIF and DDS asset inspector. It is **not** a playable Skyrim Special Edition port: game engine, NPC and quest systems are not implemented.

## New in v2.1

- UV coordinate decoding for static Skyrim SE `BSTriShape` (binary16 pairs at descriptor offset). NIF geometry, surface triangles and wireframe use the same normalized geometry.
- An optional **“Показать NIF с DDS в Vulkan · v2.1”** entry after finding DDS in a user-selected Data folder. Requires **exactly one BSTriShape, one BSShaderTextureSet**, non-empty diffuse slot 0 and an existing compatible DDS.
- CPU decoded first-mip BC1/BC2/BC3/BC4/BC5 / RGBA8 → bounded RGBA8 Vulkan image. Sampled through a new descriptor and GLSL shaders. Unsupported formats are rejected with a visible error.
- Vulkan preview now offers **“DDS: Вкл/Выкл”**, retaining the original wireframe and solid modes.
- Preserves the original `applicationId dev.skyrimsemobile.launcher` and private persistent *test-only* signing certificate. `versionCode 21`, `versionName 2.1.0`, install over v2.0.

**Supported limitations:** one static material/mesh, no animation, no multi-material shader system, no parent transforms, no BC7 GPU decoding, no full Skyrim renderer. This is a single-texture **preview**, not a game.

**GitHub Actions:** workflow **Skyrim SE Mobile Vulkan 3D APK v2.1**, artifact `Skyrim-SE-Vulkan-3D-v2.1-APK`.

### Test on phone
1. Install the new signed v2.1 APK **over v2.0**, without uninstalling.
2. In Resources → BSA load the Creation Club fishing BSA (or another BSA containing your NIF).
3. Choose NIF → “Пути текстур NIF” → “Найти DDS в папке Data”.
4. Select the new **“Показать NIF с DDS в Vulkan”** action (only works when NIF has one UV-compatible BSTriShape and exactly one texture set).
5. Tap **“Поверхность”** to see DDS; use **“DDS: Выкл”** to compare with the untextured solid.
6. If UVs appear vertically reversed on the phone, save a screenshot for a targeted fix. No original game files are written.

Real Samsung GPU/display behavior is **not yet verified** by CI tests.

## Previous release

v2.0: DDS BC1/BC2/BC3/BC4/BC5 CPU previews and editable source in `mobile/`.

Historical source archives remain in repository; source code is directly editable in `mobile/`.
