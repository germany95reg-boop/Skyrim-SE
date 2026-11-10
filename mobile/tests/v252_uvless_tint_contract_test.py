"""v2.52 regression: do not tint UV-capable meshes when diffuse DDS is missing."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
world = (root / "app/src/main/java/dev/skyrimsemobile/launcher/Tes4LocalWorldScene.java").read_text(encoding="utf-8")
tint = (root / "app/src/main/java/dev/skyrimsemobile/launcher/NifVertexTintPreview.java").read_text(encoding="utf-8")

assert "if(slot==0&&!goodUv&&hasColor){" in world, (
    "UV-capable geometry must not receive the UV-less vertex tint fallback"
)
assert "if(slot!=-1f)continue;" in tint, (
    "Tint shading must leave DDS and neutral material slots untouched"
)
assert "NifVertexTintPreview.shade(" in world
assert "build().triangles,packed,vertexColors" in world
print("PASS v2.52: UV-less tint isolation and DDS-neutral fallback contract")
