# Skyrim SE Mobile — Vulkan 3D v2.8

ARM64 Vulkan inspector for user-provided Skyrim Special Edition BSA/NIF/DDS resources; **not** a playable game engine.

## New in v2.8 — tap to choose a model

- NIF filenames in «Совпадения» are now clickable. Tap any filename or use «Выбрать NIF из списка · v2.8» near the search box. Up to 60 indexed matches can be shown, and narrowing search reveals more.
- Clicking an NIF highlights its path, places its **full path** in the search field, and locks all model actions (NIF blocks, individual shape, composite, DDS preview) to that **exact archive entry**. Duplicate filenames in different directories can no longer resolve to an unrelated first match.
- Manual search edits and changing BSA clear the explicit selection and cached DDS paths/results. Background inspection callbacks reject stale work after the selected model changes.
- Legacy first-result search stays available when the user types a unique model without tapping a row.
- Preserves v2.7 composite geometry, textured single-shape DDS/normal maps, lighting/gloss controls, camera, compact controls and read-only BSA handling.
- `versionCode 30`, `versionName 2.8.0`, same Android package and same persistent **test** signing certificate for in-place upgrades from v2.7.

## Boundaries

Catalog can index a limited prefix of very large BSA archives and displays at most 60 results per query. Composite multi-part NIF preview remains untextured; parent NiNode transforms and per-shape material assignment are future work.

Read [DEVICE_TEST_V28_RU.md](DEVICE_TEST_V28_RU.md) for app-only testing instructions.