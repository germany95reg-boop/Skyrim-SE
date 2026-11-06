# Skyrim SE Mobile — Vulkan 3D v2.7

ARM64 Vulkan viewer of user-approved Skyrim Special Edition NIF/BSA/DDS resources, not a playable Skyrim game.

## v2.7: Composite static NIF preview

- **«Показать все части NIF в Vulkan · v2.7»** combines the valid static `BSTriShape` blocks of one selected NIF into **a single untextured** 3D object.
- Geometry is transformed by each shape's local matrix, then all parts use the **same shared bounding box and normalization**, preserving their relative position and size. Individual shapes are no longer separately normalized for the combined view.
- Safe limits match the proven native Vulkan renderer: at most 30,000 distinct wireframe edges and 30,000 triangles; at most 24 scanned shape parts. Invalid parts are explicitly counted as skipped, and zero valid parts is an error.
- **Scope limitation:** parent NiNode/BSFadeNode transforms, skinning, animations, and per-shape material binding are NOT resolved. This is an approximate diagnostic composite, not a complete reconstruction of Skyrim's scene graph.
- The earlier individual-part dialog and the previously user-confirmed single-shape **textured diffuse/normal DDS** path are retained. Read-only BSA access; no game data modifications.
- Tested on synthetic dual-shape files including a second part translated by 10 world units and a partially corrupt NIF; no copyrighted game assets bundled.
- VersionCode 29 / versionName 2.7.0, unchanged applicationId and persistent private test signing certificate to upgrade on top of v2.6.

Full app-only test instructions: [DEVICE_TEST_V27_RU.md](DEVICE_TEST_V27_RU.md).
