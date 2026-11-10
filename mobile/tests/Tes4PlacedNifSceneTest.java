package dev.skyrimsemobile.launcher;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Synthetic pose linkage and end-to-end NIF geometry -> placed preview tests. */
public final class Tes4PlacedNifSceneTest {
    private static Tes4PlacementSubrecordReader.Placement pose(int ref,int base,
            float x,float y,float z,float rx,float ry,float rz,float scale)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        Tes4PlacementSubrecordReaderTest.field(out,"NAME",
            Tes4PlacementSubrecordReaderTest.i32(base));
        java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(24)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putFloat(x).putFloat(y).putFloat(z);
        b.putFloat(rx).putFloat(ry).putFloat(rz);
        Tes4PlacementSubrecordReaderTest.field(out,"DATA",b.array());
        byte[] f=java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putFloat(scale).array();
        Tes4PlacementSubrecordReaderTest.field(out,"XSCL",f);
        return Tes4PlacementSubrecordReader.inspect("REFR",ref,out.toByteArray());
    }
    private static void close(double a,double b){
        assert Math.abs(a-b)<1e-3:a+" != "+b;
    }
    public static void main(String[] args)throws Exception{
        var sample=pose(0x1001,0x2222,10000f,-20000f,300f,
            0f,0f,(float)Math.PI/2,2f);
        assert sample.complete()&&sample.scale==2f;
        double[] moved=Tes4PlacedNifScene.applyPose(1,0,0,sample);
        close(moved[0],10000);close(moved[1],-19998);close(moved[2],300);
        var marker=pose(0x1002,0x3333,2,3,4,0,0,0,1);
        Map<Long,Tes4BaseFormResolver.Base> bases=new LinkedHashMap<>();
        bases.put(0x3333L,new Tes4BaseFormResolver.Base(0x3333,"STAT","XMarker","MarkerX.nif",false));
        bases.put(0x2222L,new Tes4BaseFormResolver.Base(0x2222,"ACTI",
            "StockadeBarricade01Activator","Clutter/Stockade/Barricade.nif",false));
        assert Tes4PlacedNifScene.findVisible(Arrays.asList(marker,sample),
            bases,"meshes/markerx.nif")==null;
        var link=Tes4PlacedNifScene.findVisible(Arrays.asList(marker,sample),
            bases,"meshes/clutter/stockade/barricade.nif");
        assert link!=null && link.pose.recordFormId==0x1001;
        var mesh=NifGeometryDecoder.scan(NifGeometryDecoderTest.valid());
        var preview=Tes4PlacedNifScene.compose(mesh,link);
        assert preview.shapes==1 && preview.triangleCount==1 && preview.lines.edges==3;
        assert preview.triangles.length==9 && preview.details.contains("10000.00");
        for(float v:preview.triangles)assert Float.isFinite(v)&&Math.abs(v)<=1.001f;
        assert preview.details.contains("масштаб XSCL: 2.000");
        var rotated=pose(0x1001,0x2222,10000,-20000,300,0,0,0,1);
        var link2=Tes4PlacedNifScene.findVisible(Arrays.asList(rotated),bases,
            "meshes/clutter/stockade/barricade.nif");
        var preview2=Tes4PlacedNifScene.compose(mesh,link2);
        assert !Arrays.equals(preview.triangles,preview2.triangles);
        // Unresolved base and no visible shapes must not create a preview.
        assert Tes4PlacedNifScene.findVisible(Arrays.asList(sample),
            new LinkedHashMap<>(),"meshes/clutter/stockade/barricade.nif")==null;
        // Device report v2.46 selected two editor markers as a "world scene":
        // DragonPerchRockL02/MarkerRock and DragonPerchWordwall/MarkerWordWallTower.
        // Their NIF contains triangles but must NEVER become world architecture.
        bases.put(0xAA934L,new Tes4BaseFormResolver.Base(
            0xAA934L,"FURN","DragonPerchRockL02",
            "Furniture/DragonFurniture/MarkerRock.nif",false));
        bases.put(0x1CC4AL,new Tes4BaseFormResolver.Base(
            0x1CC4AL,"FURN","DragonPerchWordwall",
            "Furniture/DragonFurniture/MarkerWordWallTower.nif",false));
        var editorRock=pose(0x10ff37,0xAA934,58468,-48523,36815,0,0,0,1);
        var editorTower=pose(0x10ff36,0x1CC4A,55748,-46388,36642,0,0,0,1);
        assert !Tes4PlacedNifScene.isWorldVisibleBase(bases.get(0xAA934L));
        assert !Tes4PlacedNifScene.isWorldVisibleBase(bases.get(0x1CC4AL));
        assert Tes4PlacedNifScene.findVisibleAll(
            Arrays.asList(editorRock,editorTower),bases,
            "meshes/furniture/dragonfurniture/markerrock.nif").isEmpty();
        assert Tes4PlacedNifScene.findVisibleAll(
            Arrays.asList(editorRock,editorTower),bases,
            "meshes/furniture/dragonfurniture/markerwordwalltower.nif").isEmpty();
        bases.put(0x6A166L,new Tes4BaseFormResolver.Base(
            0x6A166L,"ACTI","WRDrawBridge01",
            "Architecture/WhiteRun/WRDrawBridge01.nif",false));
        assert Tes4PlacedNifScene.isWorldVisibleBase(bases.get(0x6A166L));
        assert Tes4PlacedNifScene.findVisibleAll(
            Arrays.asList(pose(0x17055,0x6A166,18095,-5330,-3802,0,0,0,1)),
            bases,"meshes/architecture/whiterun/wrdrawbridge01.nif").size()==1;
        System.out.println("PASS TES4 REFR pose & scale -> matched NIF BSTriShape -> bounded Vulkan preview");
    }
}
