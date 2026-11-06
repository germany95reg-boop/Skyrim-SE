# Skyrim SE Mobile — Vulkan 3D v2.6

Android ARM64/Vulkan inspection of user-provided Skyrim Special Edition BSA/NIF/DDS assets; this is NOT a playable Skyrim engine.

## New in v2.6: inspect multiple separate static BSTriShape parts

- New catalog action **«Части NIF · выбрать BSTriShape · v2.6»** reads a single NIF from a user-approved BSA in memory and presents an ordered dialog of its static BSTriShape blocks.
- Each part can be opened individually as a geometry-only wireframe/surface in the existing Vulkan viewer. Unsupported parts display a reason without preventing selection of valid parts.
- Hard limits: 24 parts and 60,000 accepted triangle faces across the scan; the BSA extract retains its existing 8 MiB packed / 16 MiB decoded limits.
- Existing single-shape preview and textured single-material preview are unchanged, including user-tested compact UI, diffuse/normal DDS, light and gloss controls.
- **No incorrect DDS mapping**: parts selected via the new action are deliberately untextured because links from shapes to BSLightingShaderProperty / BSShaderTextureSet are not yet reliably resolved.
- This is a building block toward multi-material NIF, not a completed multi-shape composite renderer. Shape-local transforms are applied, parent node transforms remain unsupported.
- Existing applicationId `dev.skyrimsemobile.launcher` and persistent private test signing key are preserved; `versionCode 28`, `versionName 2.6.0`, upgrade over signed v2.5.

Automated tests include synthetic single and double BSTriShape files, invalid second shape handling, CI's BSA/NIF/DDS/Vulkan tests, Android compilation and signing.

[Пошаговая инструкция только внутри приложения](DEVICE_TEST_V26_RU.md)
