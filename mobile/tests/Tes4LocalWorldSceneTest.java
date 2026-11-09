package dev.skyrimsemobile.launcher;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Test real multi-instance transform and bounds using synthetic BSTriShape data. */
public final class Tes4LocalWorldSceneTest {
    private static Tes4PlacementSubrecordReader.Placement pose(int ref,int base,
        float x,float y,float z,float scale)throws Exception{
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        Tes4PlacementSubrecordReaderTest.field(body,"NAME",
            Tes4PlacementSubrecordReaderTest.i32(base));
        byte[] coords=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(x).putFloat(y).putFloat(z).putFloat(0).putFloat(0).putFloat(0).array();
        Tes4PlacementSubrecordReaderTest.field(body,"DATA",coords);
        Tes4PlacementSubrecordReaderTest.field(body,"XSCL",
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(scale).array());
        return Tes4PlacementSubrecordReader.inspect("REFR",ref,body.toByteArray());
    }
    static void near(double actual,double expected) {
        assert Math.abs(actual-expected)<0.002:actual+" vs "+expected;
    }
    public static void main(String[] a)throws Exception{
        Map<Long,Tes4BaseFormResolver.Base> bases=new LinkedHashMap<>();
        bases.put(10L,new Tes4BaseFormResolver.Base(10,"STAT","Barricade","Clutter/test.nif",false));
        bases.put(11L,new Tes4BaseFormResolver.Base(11,"STAT","XMarker","markerx.nif",false));
        var first=pose(100,10,100000f,200000f,500,1);
        var second=pose(101,10,100008f,200000f,500,2);
        var far=pose(102,10,150000f,220000f,500,1);
        var marker=pose(103,11,100003f,200000f,500,1);
        var links=Tes4PlacedNifScene.findVisibleAll(
            Arrays.asList(marker,first,second,far),bases,"meshes/clutter/test.nif");
        assert links.size()==3:links.size();
        var parts=NifGeometryDecoder.scan(NifGeometryDecoderTest.valid());
        var builder=new Tes4LocalWorldScene.Builder();
        assert builder.add(parts,links.get(0));
        assert builder.add(parts,links.get(1));
        assert !builder.add(parts,links.get(1)); // exact FormID duplicate guard
        assert !builder.add(parts,links.get(2)); // far from anchor
        assert builder.instances()==2 && builder.distantCount()==1;
        var scene=builder.build();
        assert scene.shapes==2 && scene.triangleCount==2;
        assert scene.lines.edges==6 && scene.triangles.length==18;
        assert scene.details.contains("REFR 0x00000064");
        assert scene.details.contains("REFR 0x00000065");
        assert scene.details.contains("Дальних размещений пропущено: 1");
        // The copies must be separate in one coordinate system, not normalized
        // independently into identically overlapping triangles.
        near(scene.triangles[0],-1);
        assert scene.triangles[9]>scene.triangles[0]+1f:
            "Objects at different world X coordinates were overlaid";
        for(float v:scene.triangles)assert Float.isFinite(v)&&Math.abs(v)<=1.001f;
        assert builder.summary().contains("2 размещений");
        var only=new Tes4LocalWorldScene.Builder();
        assert only.add(parts,links.get(0));
        assert only.build().triangleCount==1;
        assert Tes4PlacedNifScene.findVisibleAll(
            Arrays.asList(marker),bases,"meshes/markerx.nif").isEmpty();
        System.out.println("PASS bounded nearby Skyrim REFR multi-mesh local scene and safe distant exclusion");
    }
}
