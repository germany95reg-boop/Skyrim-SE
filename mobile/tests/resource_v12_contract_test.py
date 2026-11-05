from pathlib import Path
p=Path(__file__).resolve().parents[1]
base=p/'app/src/main/java/dev/skyrimsemobile/launcher'
n=(base/'NifGeometryDecoder.java').read_text()
b=(base/'BsaCatalogActivity.java').read_text()
s=(base/'VulkanSceneActivity.java').read_text()
t=(base/'NifGeometryTransfer.java').read_text()
g=(p/'app/build.gradle').read_text()
assert "versionCode 13" in g and "versionName '1.3.0'" in g
assert "transformOffset=p+16+extraBytes" in n
assert "localScale" in n and "rotation[row*3]" in n
assert "rawDimensions" in n and "localDimensions" in n and "translation" in n
assert "finiteFloat" in n and "Данные BSTriShape выходят за границы блока" in n
assert "Math.abs(localScale)>1e6f" in n
assert "mesh.compactInfo()" in b and "NifGeometryTransfer.offer" in b
assert "if(nif != null){ nifDimensions=nif.details" in s
assert all('setCameraPreset("'+x+'"' in s for x in ("Спереди", "Сбоку", "Сверху"))
assert "autoSpin=false" in s
assert "pending=null" in t
assert all("openOutputStream" not in c and "TamrielVoice" not in c for c in (n,b,s,t))
print("PASS: v1.2 local-transform, camera presets, metadata, read-only contract")
