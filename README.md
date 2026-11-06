# Skyrim SE Mobile — Vulkan 3D v2.9

Android ARM64 Vulkan **read-only NIF/BSA/DDS viewer**, not a playable Skyrim game.

## New in v2.9: remember the archive, NIF model and Data folder

- When the Android Storage Access Framework (SAF) file picker grants persistable read access, v2.9 saves the selected **BSA URI and display name** in the app's private SharedPreferences. It re-opens and validates this archive **asynchronously** when the catalogue is next opened, without requiring another file picker.
- The **exact NIF path** chosen through the catalogue is saved against that archive. After reopening, the NIF is restored automatically and its material paths inspected in a bounded, read-only background operation.
- **Data folder** selection is saved with a persistable tree read grant; **«Найти DDS в папке Data»** reuses this folder without opening Android's folder picker again. **«Изменить папку Data»** lets you override the saved location.
- **«Недавние модели NIF»** shows up to eight recent exact NIF paths in the current BSA; paths are de-duplicated, bounded and never resolve to a different similarly named entry.
- **«Забыть сохранённые пути»** deletes the preferences and releases persistable read grants held by this app. No BSA/NIF/DDS content is stored or modified; only the SAF URIs and chosen NIF names are saved.
- Stale/missing/revoked documents cannot be restored; in this case the UI explains the problem and allows selecting a new BSA or Data folder. Concurrent loader results are guarded against older selections.
- The user-tested v2.8 exact NIF picker, v2.7 composite preview, prior textured NIF, diffuse and normal DDS, Vulkan lighting, material controls, camera/FPS are preserved.
- Stable app ID `dev.skyrimsemobile.launcher`, versionCode 31 / versionName 2.9.0 and the original private **test-only** signing certificate for in-place upgrades.

**First run note:** v2.8 did not retain SAF permissions; choose the BSA and Data folder **once in v2.9** before automatic subsequent restoration. Some Android providers do not support persistable grants; in that case restoration cannot bypass the system's file-picker security rules.

**Testing only inside the app:** [DEVICE_TEST_V29_RU.md](DEVICE_TEST_V29_RU.md).

**Limitations:** not a complete Skyrim runtime. Composite geometry still lacks per-part DDS materials and parent NiNode transform support.
