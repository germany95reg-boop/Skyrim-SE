# Skyrim SE Mobile Launcher — v2.1.2

**Skyrim SE Mobile** is an experimental Android ARM64/Vulkan NIF and DDS asset inspector. It is **not** a playable Skyrim Special Edition port: game engine, NPC and quest systems are not implemented.

## New in v2.1.2

- Correct Android adaptive launcher icon scale. The **same original illustration** is now inset into the 72dp mask-safe viewport; SKYRIM SE lettering stays visible on Samsung rounded-square and circular launchers.
- New adaptive background + padded foreground XML, including both normal and round icon variants. All legacy icon PNG sizes remain untouched; no branding replacement.
- Correct displayed versions in the 3D header, resource inspector and home-page help text.
- **versionCode 23**, **versionName 2.1.2**, with unchanged package ID and fixed private development test signing certificate to upgrade in place.
- No change to BSA, NIF, DDS or Vulkan rendering logic; user's v2.1.1 model + textured surface tests were successful.

### Inside-app check

From **Настройки → Ресурсы Skyrim SE → Каталог BSA v105**, open `ccBGSSSE001-Fish.bsa`, filter `lureargonian01.nif`, select **Пути текстур NIF → Найти DDS в папке Data → Показать NIF с DDS в Vulkan**. In the scene tap **Поверхность**, check **DDS: Вкл** and ensure the title reads `Vulkan 3D · v2.1.2`.

The icon itself is outside the app on the Samsung launcher: compare its size and SKYRIM lettering against the provided screenshot. No device result for this icon fix yet.

## Fixed in v2.1.1

- Correct SSE BSVertexDesc UV1 offset extraction: bits 8–11 in units of four bytes. Bits 4–7 describe dynamic vertex size, **not** UVs. Fixes the `SSE UV: неверное смещение BSVertexDesc` failure reported on `meshes/creationclub/bgssse001/clutter/lureargonian01.nif`.
- Added synthetic UV regression with a nonzero dynamic-size nibble and strict invalid-offset rejection; real-device confirmation still required.
- Allow 1–16 trailing **zero bytes only** in a BSA file-name table, while still rejecting nonzero/missing trailing bytes or suspicious size discrepancies. This may handle the reported 9-byte mismatch in MarketplaceTextures.bsa; real file has not yet been verified.
- Unchanged application ID and **test signing certificate**, with versionCode 22 / versionName 2.1.1 for upgrading over v2.1.

## Features inherited from v2.1

- UV coordinate decoding for static Skyrim SE `BSTriShape` (binary16 pairs at descriptor offset). NIF geometry, surface triangles and wireframe use the same normalized geometry.
- An optional **“Показать NIF с DDS в Vulkan · v2.1”** entry after finding DDS in a user-selected Data folder. Requires **exactly one BSTriShape, one BSShaderTextureSet**, non-empty diffuse slot 0 and an existing compatible DDS.
- CPU decoded first-mip BC1/BC2/BC3/BC4/BC5 / RGBA8 → bounded RGBA8 Vulkan image. Sampled through a new descriptor and GLSL shaders. Unsupported formats are rejected with a visible error.
- Vulkan preview now offers **“DDS: Вкл/Выкл”**, retaining the original wireframe and solid modes.
- Preserves the original `applicationId dev.skyrimsemobile.launcher` and private persistent *test-only* signing certificate. `versionCode 22`, `versionName 2.1.1`, install over v2.0.

**Supported limitations:** one static material/mesh, no animation, no multi-material shader system, no parent transforms, no BC7 GPU decoding, no full Skyrim renderer. This is a single-texture **preview**, not a game.

**GitHub Actions:** workflow **Skyrim SE Mobile Vulkan 3D APK v2.1.1**, artifact `Skyrim-SE-Vulkan-3D-v2.1.1-APK`.

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
