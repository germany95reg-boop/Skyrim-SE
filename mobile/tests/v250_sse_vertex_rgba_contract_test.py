"""v2.50: preserve real SSE RGBA for UV-less BSTriShape #100; no DDS invention."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
geom=r("app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryDecoder.java")
world=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4LocalWorldScene.java")
atlas=r("app/src/main/java/dev/skyrimsemobile/launcher/NifCompositeDdsAtlas.java")
bridge=r("app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryTransfer.java")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
reader=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
receipt=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4AutomaticSceneReceipt.java")
cpp=r("app/src/main/cpp/vulkan_scene_test.cpp")
shader_root=root/"app/src/main/shaders"
for name in ("textured.vert","textured_safe.vert"):
    s=(shader_root/name).read_text(encoding="utf-8")
    assert "layout(location=3) in vec4 inVertexColor" in s
    assert "layout(location=3) out vec4 vertexColor" in s
    assert "vertexColor=inVertexColor;" in s
for name in ("textured.frag","textured_safe.frag","textured_normal_safe.frag"):
    s=(shader_root/name).read_text(encoding="utf-8")
    assert "layout(location=3) in vec4 vertexColor" in s
    assert "vec4(vertexColor.rgb,1.0)" in s
assert "(desc>>>24)&15L" in geom
assert "boolean hasColors=(attributes&0x20)!=0;" in geom
assert "surfaceColors" in geom
assert "RGBA: неверное смещение" in geom
assert "slot=-1;vertexColorFaces+=mesh.triangles" in world
assert "vertexColors[(offset+v)*4+c]=mesh.surfaceColors[v*4+c]" in world
assert "RGBA ВЕРШИН" in world
assert "public final int vertexColorTriangles;" in atlas
assert "final float[] vertexColors;" in bridge
assert "nativeSetVertexColors(vertexColors)" in ui
assert "nif.materialSlots,nif.diffuseAtlasGrid,nif.vertexColors" in ui
assert "nativeSetVertexColors(" in cpp
assert "uvBinding.stride=10*sizeof(float)" in cpp
assert "VK_FORMAT_R32G32B32A32_SFLOAT" in cpp
assert "selectedVertexColors=std::move(data)" in cpp
assert "id< -1" in cpp
assert "lastSceneVertexColorFaces" in reader
assert "SSE vertex RGBA: подготовлены реальные цвета" in receipt
assert "validColor()" in r("tests/NifGeometryDecoderTest.java")
assert "vertexColorTriangles==1" in r("tests/Tes4LocalWorldSceneTest.java")
assert "versionCode 73" in r("app/build.gradle")
assert "2.50.0" in r("app/build.gradle")
wf=r("../.github/workflows/android-quality-gates.yml")
assert "v250_sse_vertex_rgba_contract_test.py" in wf
assert "Skyrim-SE-Mobile-v2.50.0-ARM64.apk" in wf
print("PASS real SSE vertex RGBA -> Java atlas -> JNI Vulkan shaders; pure-color fallback and no fake UV")
