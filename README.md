# Skyrim SE Mobile — Vulkan 3D v2.13

Android ARM64 **read-only** Skyrim SE BSA/NIF/DDS diagnostic Vulkan viewer, not a Skyrim game engine.

## New in v2.13: composite NIF with separate DDS for each supported part
- After «Найти DDS каждой части NIF», tap **«Показать ВСЕ части NIF с DDS · v2.13»**.
- Reuses the existing globally normalized composite geometry and the validated BSTriShape → BSLightingShaderProperty → BSShaderTextureSet references.
- Packs up to **15 distinct diffuse DDS** into a bounded **2048×2048** CPU atlas. A per-triangle flat shader attribute selects the correct tile; each shape repeats its original UV within that tile. Several shapes may reuse the same decoded DDS.
- Shapes with missing, malformed or excess diffuse DDS remain as neutral gray geometry (not silently assigned another material). A summary reports how many parts have fallback material.
- Source DDS decode cache capped at **8 million pixels**; GPU atlas and geometry remain bounded. No archive writing or game assets redistributed.
- Single-shape DDS and normal-map Vulkan preview are retained. **New:** a bounded bilinear resize can match different normal/diffuse first-mip sizes (when the native normal atlas supports the target), correcting the v2.12 “отличается размер DDS” report.
- Original composite geometry-only preview, individual shape selection, camera, FPS, persistent SAF Data permission, user icon and APK in-place upgrades are preserved.
- Android **versionCode 36 / versionName 2.13.0**, same `applicationId`, same development-only signing certificate.
- Synthetic Java tests and source contracts cover atlas material mapping, fallback, normal resampling and JNI/GLSL integration.

**Scope and limits:** This is now a **textured composite static geometry preview**; normals are *not* yet shown for multiple materials simultaneously. Parent NiNode/BSFadeNode transforms, Skyrim shading, animation, game scripts and SkyrimSE.exe porting are unsupported. A partially read BSA index may conceal other DDS files.

See [DEVICE_TEST_V213_RU.md](DEVICE_TEST_V213_RU.md) for the real-device checklist.
