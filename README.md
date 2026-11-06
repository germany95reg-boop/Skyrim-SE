# Skyrim SE Mobile — Vulkan 3D v2.11

Android ARM64 read-only Skyrim SE BSA/NIF/DDS diagnostic Vulkan viewer, not a complete game engine.

## v2.11: Verify which DDS belong to every BSTriShape, inside Data

- The new one-tap **«Найти DDS каждой части NIF · v2.11»** action resolves the exact `BSTriShape` -> `BSLightingShaderProperty` -> `BSShaderTextureSet` indices demonstrated on the real `lureargonian01.nif`, and collects only its linked diffuse DDS [0] / normal DDS [1].
- Uses the stored Android SAF read-only `Data` directory and scans at most 48 BSA indexes through the already tested read-only path (<= 4,096 Data children). No repeat folder selection when the permanent grant remains valid.
- Reports **for every separate BSTriShape**: its NIF block index, shader and texture-set index, and per-slot DDS found/not found (only in scanned BSA indexes), plus a clear warning if any archive scan was partial, incomplete or failed.
- Reused DDS referenced by several parts are searched once but associated with each relevant geometry part. Unresolvable shader links do not get guessed materials.
- The exact NIF path is protected against asynchronous stale lookups and changing model resets all cached DDS and link status.
- No commercial Skyrim DDS are copied into the APK; BSA/NIF/DDS files remain unchanged.
- Existing last-used BSA/NIF/Data restoration, recent NIFs, Vulkan composite and single-model textured DDS/normal preview, camera, FPS and all UI controls remain intact.
- VersionCode **34**, versionName **2.11.0**, stable Android appId and persistent private **test** signature for update over v2.10.1.

**Limitation:** this stage verifies DDS availability per shape, but **does not yet render multiple DDS simultaneously** in the composite Vulkan model; parent node transforms and full Skyrim shading remain unsupported.

See [DEVICE_TEST_V211_RU.md](DEVICE_TEST_V211_RU.md) for app-only testing.