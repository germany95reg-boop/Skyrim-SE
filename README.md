# Skyrim SE Mobile Launcher — v2.3

Android ARM64/Vulkan viewer for user-supplied Skyrim Special Edition NIF/DDS/BSA resources. This is **not** a playable Skyrim engine.

## v2.3

- Four real-time view-space light presets: Сбоку (the proven v2.2 lighting), Спереди, Слева, Сверху.
- A separate normal-map emphasis control: 50%, 100% (v2.2 default), 150%. Changes update a Vulkan push constant without reloading textures or reconstructing the swapchain.
- The existing CPU-decoded diffuse and normal DDS atlas, 3D geometry, wireframe and camera/FPS controls remain intact.
- Pure JVM preset tests, Vulkan shader contract tests and all existing BSA/NIF/DDS safety checks are in CI.
- `applicationId dev.skyrimsemobile.launcher`, `versionCode 25`, `versionName 2.3.0`, same fixed test signing certificate as v2.2 (in-place upgrade).

## Scope

One static BSTriShape, single texture set, first DDS mip, normal-map lighting preview. No Skyrim game engine, quests, animation, multiple materials or full PBR shading. Verify new lighting presets on the actual phone.

**Instructions inside app:** [DEVICE_TEST_V23_RU.md](DEVICE_TEST_V23_RU.md).
