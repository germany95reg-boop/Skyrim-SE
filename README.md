# Skyrim SE Mobile — Vulkan 3D v2.12

Android ARM64 read-only Skyrim SE BSA/NIF/DDS diagnostic Vulkan viewer, **not a complete game engine**.

## v2.12: Textured Vulkan preview of the selected BSTriShape

- New button **«Показать выбранную часть NIF с её DDS · v2.12»** after **«Найти DDS каждой части NIF»**.
- Allows choosing an individual `BSTriShape` from a multi-shape NIF and identifies its own validated shader and diffuse/normal DDS by its **block ID**, never by shape order.
- Reads only its linked DDS from the user's approved Data BSA archives. CPU-decodes the first mip and passes UV + diffuse + optional normal to the existing Vulkan textured renderer.
- If one part has missing/unsupported materials, its failure is shown without assigning a different part's texture. A missing/unsupported normal texture does not block valid diffuse.
- Uses only in-memory bounded resources. Skyrim archives remain unchanged. All earlier single-shape previews, composite **geometry-only** scene, saved SAF paths and camera controls remain available.
- VersionCode **35**, versionName **2.12.0**, same `applicationId` and stable private development certificate to update installed v2.11.
- Synthetic JVM regression tests cover exact shape → shader → texture set → DDS selection.

**Limitations:** This mode displays **one chosen shape at a time**. It is not yet multi-material composite rendering or a Skyrim game port. Parent NiNode/BSFadeNode transforms and full Skyrim shading are not implemented. If one BSA index is partial, some other missing DDS may remain undiscovered.

Follow [DEVICE_TEST_V212_RU.md](DEVICE_TEST_V212_RU.md) for the Android testing procedure.
