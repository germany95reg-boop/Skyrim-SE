# Skyrim SE Mobile — Vulkan 3D v2.5

Android ARM64 Vulkan preview of user-selected Skyrim SE NIF, DDS and BSA resources; **not a playable Skyrim game port**.

## v2.5 — more room for the 3D model

- The **Параметры ▾** control collapses the advanced OBJ examples, lighting, relief and material controls by default. They remain fully accessible and do not reset the GPU, the mesh or the chosen DDS.
- The **Крупнее** button moves the camera 18% closer per tap, within the renderer's 2.7–40 distance bounds, and disables auto-rotation. **Вписать** restores safe auto-fitting.
- Automatic framing increases the viewport padding efficiency from 85% to 90%, retaining a conservative 10% rotating-mesh margin. Collapsing the control rows also expands the actual visible viewport and automatically reframes the model.
- A short **← Назад** button occupies its own footer half; it cannot wrap as the previous multi-line launcher label did.
- All BSA/NIF/DDS, Vulkan material, light and normal-map features from the user-tested v2.4 are retained without native renderer or shader changes.
- Same application ID and persistent private **test-only** signing certificate; versionCode 27, versionName 2.5.0, upgrades over v2.4.

## App-only testing

See [DEVICE_TEST_V25_RU.md](DEVICE_TEST_V25_RU.md).

**Limitations:** one static NIF BSTriShape, one texture set, first DDS mip; the UI and model scale must be visually confirmed on a Samsung phone.
