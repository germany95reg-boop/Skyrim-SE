# Skyrim SE Mobile Launcher — v2.2

Android ARM64/Vulkan preview for user-supplied Skyrim Special Edition BSA/NIF/DDS resources. This is not a playable Skyrim game or complete Skyrim engine.

## New in v2.2

- Read-only loading of diffuse DDS and optional normal-map DDS from BSShaderTextureSet slots 0 and 1.
- Vulkan fragment shader derives a tangent frame from model UVs, samples a normal map and reconstructs its Z component. BC3 and BC5 use RG normal channels; gloss/specular alpha is not simulated.
- A full-width 'Нормали: Вкл / Выкл' control lets you compare normal-map lighting. Previous 'Каркас', 'Поверхность', 'DDS' and camera/FPS controls remain.
- Reuses the proven Vulkan sampled-image upload path by packing the diffuse and normal RGBA8 first mips into a two-part texture atlas. Preview limit for normal maps: up to 2048×1024.
- Unsupported or absent normal maps fall back to diffuse-only rendering; user game files are never modified.
- package dev.skyrimsemobile.launcher, versionCode 24, versionName 2.2.0. Same fixed private TEST signing certificate as v2.1.2, so APK upgrades in place.

## Known limitations

Single static BSTriShape, single BSShaderTextureSet, CPU-decoded DDS first mip. No full Skyrim engine, skeletal animation, multiple materials, parent transforms, true game lighting or full PBR. Real phone/GPU results for the v2.2 normal-map feature remain unverified until a device test.

See [DEVICE_TEST_V22_RU.md](DEVICE_TEST_V22_RU.md) for in-app-only instructions and troubleshooting.

Previous versions and tested source history are preserved in Git.
