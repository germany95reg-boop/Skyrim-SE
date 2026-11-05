# Skyrim SE Mobile — Vulkan 3D v2.4

Android ARM64 Vulkan viewer for selected Skyrim SE NIF meshes and DDS resources; not a playable game engine.

## New in v2.4

- Per-pixel **experimental material highlight** from alpha of the normal DDS (`LureArgonian_n.dds` BC3/DXT5) plus existing tangent-space normal lighting.
- Two controls: **Блик: 0/25/50/100%** and **Ширина: Широкий/Средний/Узкий**.
- Real-time updates use atomic C++ values and a 52-byte Vulkan push constant; no BSA re-scan or Vulkan swapchain rebuild.
- **Default highlight OFF**: the user-confirmed v2.3 look (DDS, normal map, light presets and relief) is preserved.
- Controls disabled when no matched normal DDS atlas exists. CPU assets and game BSA remain read-only.
- Test-only signing fingerprint preserved, versionCode 26, versionName 2.4.0, applicationId `dev.skyrimsemobile.launcher`; supports upgrade over signed v2.3.

### Limitations

This is not Skyrim engine material rendering or full PBR; gloss alpha interpretation varies between actual textures. Highlight position and width are approximate, with a fixed preview camera vector. One BSTriShape + one texture set, first mip only. Real device GPU comparison is required.

## Phone procedure (only inside app)

See [DEVICE_TEST_V24_RU.md](DEVICE_TEST_V24_RU.md).
