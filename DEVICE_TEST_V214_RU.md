# Skyrim SE Mobile v2.14 — Vulkan DDS compatibility

The provided v2.13 device screenshots showed:
`vkCreateGraphicsPipelines(textured) VkResult=-13` (`VK_ERROR_UNKNOWN`).
The line and solid pipeline setup succeeded before the optional textured
pipeline failed; the exception stopped the complete render loop.

## Fix

- Attempt the original detailed multi-material DDS shader first.
- If the driver rejects its pipeline, automatically use a lightweight
  multi-material atlas shader (no per-pixel derivatives, no runtime integer
  division or modulo).
- If it also fails, try a minimal single-texture sampler.
- If all DDS pipelines fail, continue to display wireframe and solid geometry
  without uploading unavailable texture resources.
- Display the active compatibility mode in the 3D status area and native JSON.
- Keep the Android package, persistent debug signing certificate and application
  data across an in-place upgrade; bump versionCode to 37.

## Automated CI

`mobile/tests/v213_contract_test.py` verifies all previous multi-part and
DDS atlas features. `mobile/tests/v214_vulkan_fallback_contract_test.py`
checks the fallback order, shader interfaces and upgrade rules. CI compiles
every GLSL variant and checks the resulting SPIR-V with `spirv-val`.

## Device acceptance test

1. Install v2.14 **over** v2.13 (do not uninstall).
2. Reopen `fishinggear_cluster01.nif`; choose `Показать ВСЕ части NIF с DDS · v2.13`.
3. In the viewer press `Поверхность` and leave `DDS: Вкл`.
4. Confirm the entire mesh appears and note the displayed DDS mode:
   - full: full multi-material + lighting;
   - atlas-safe: multi-material DDS, basic unlit preview;
   - basic: single sampled DDS only, multi-material atlas unavailable;
   - off: Vulkan driver rejected all DDS pipelines, solid surface still works.
5. Check `lureargonian01.nif` (selected part, diffuse and optional normal).
6. Return to prior wireframe and single-DDS actions. Confirm archive reading
   remains read-only and permissions persist.

GPU-specific success cannot be guaranteed by static checks; device proof is
needed only after the major renderer recovery is built. No individual UI
button-by-button manual testing is required.
