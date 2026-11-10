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
        // v2.42 regression: ESM/BSA scan order is not a scene-selection rule.
        // One early isolated REFR must not hide a later real cluster.
        var isolated=pose(200,10,0,0,0,1);
        var nearA=pose(201,10,100000,0,0,1);
        var nearB=pose(202,10,100004,0,0,1);
        var nearC=pose(203,10,100009,0,0,1);
        var denseLinks=Tes4PlacedNifScene.findVisibleAll(
            Arrays.asList(isolated,nearA,nearB,nearC),bases,"meshes/clutter/test.nif");
        assert denseLinks.size()==4;
        var planner=new Tes4LocalWorldScene.Planner();
        for(var link:denseLinks)planner.offer(parts,link);
        planner.offer(parts,denseLinks.get(2)); // same REFR, no extra instance
        var cluster=planner.best();
        assert cluster.instances()==3:cluster.instances();
        assert cluster.distantCount()==1:cluster.distantCount();
        var composed=cluster.build();
        assert composed.triangleCount==3;
        assert composed.details.contains("REFR 0x000000C9");
        assert composed.details.contains("REFR 0x000000CA");
        assert composed.details.contains("REFR 0x000000CB");
        assert !composed.details.contains("REFR 0x000000C8");
        assert planner.summary().contains("повторов 1");
        var reversed=new java.util.ArrayList<>(denseLinks);
        java.util.Collections.reverse(reversed);
        var plannerReverse=new Tes4LocalWorldScene.Planner();
        for(var link:reversed)plannerReverse.offer(parts,link);
        var same=plannerReverse.best().build();
        assert same.triangleCount==3;
        assert !same.details.contains("REFR 0x000000C8");
        for(float v:same.triangles)assert Float.isFinite(v)&&Math.abs(v)<=1.001f;
        // v2.44: real surface UV ordering must survive multiple REFR copies
        // and a mixed no-UV part. One DDS image is reused for the same NIF.
        var withUv=NifGeometryDecoder.scan(NifGeometryDecoderTest.validUv());
        var sceneDds=new Tes4LocalWorldScene.Builder();
        assert sceneDds.add(withUv,denseLinks.get(1));
        assert sceneDds.add(withUv,denseLinks.get(2));
        assert sceneDds.add(parts,denseLinks.get(3)); // third shape has no UV
        String nifPath=denseLinks.get(1).canonicalPath;
        Map<String,Map<Integer,String>> materialPaths=new LinkedHashMap<>();
        Map<Integer,String> partPaths=new LinkedHashMap<>();
        partPaths.put(0,"textures\\clutter\\rock.dds");
        materialPaths.put(nifPath,partPaths);
        var requested=sceneDds.materialPaths(materialPaths);
        assert requested.size()==1 && "textures/clutter/rock.dds".equals(requested.get(0)):
            requested.toString();
        assert sceneDds.diffuseAtlas(materialPaths,
            java.util.Collections.emptyMap())==null;
        Map<String,DdsBcDecoder.Image> pixels=new LinkedHashMap<>();
        pixels.put(requested.get(0),
            new DdsBcDecoder.Image(2,2,new int[]{
                0xffff0000,0xff00ff00,0xff0000ff,0xffffffff},"synthetic RGBA"));
        var diffuse=sceneDds.diffuseAtlas(materialPaths,pixels);
        assert diffuse!=null;
        assert diffuse.textureCount==1 && diffuse.neutralParts==1;
        assert diffuse.texturedTriangles==2:"two textured instances, third neutral";
        assert diffuse.grid==2 && diffuse.width==1024;
        assert diffuse.uvAndSlot.length==sceneDds.build().triangles.length;
        for(int v=0;v<6;v++)assert diffuse.uvAndSlot[v*3+2]==1f;
        for(int v=6;v<9;v++)assert diffuse.uvAndSlot[v*3+2]==0f;
        near(diffuse.uvAndSlot[0],0);
        near(diffuse.uvAndSlot[3],1);
        near(diffuse.uvAndSlot[8],1);
        near(diffuse.uvAndSlot[9],0); // UV repeated from original mesh
        assert diffuse.argb[0]==0xff73777e:"Neutral tile 0";
        assert diffuse.argb[diffuse.width/2]==0xffff0000:"Real DDS tile 1";
        assert sceneDds.build().triangleCount==3:
            "DDS fallback must not drop world-space geometry";
        var invalidPixels=new LinkedHashMap<String,DdsBcDecoder.Image>();
        invalidPixels.put(requested.get(0),
            new DdsBcDecoder.Image(2,2,new int[]{0xff000000},"bad"));
        try{
            sceneDds.diffuseAtlas(materialPaths,invalidPixels);
            throw new AssertionError("Accepted invalid DDS pixel count");
        }catch(java.io.IOException expected){}
        // v2.47: do not score a sparse pair of small clutter props above
        // one real architecture object; no arbitrary marker fragments.
        var clutter1=pose(301,10,500,0,0,1);
        var clutter2=pose(302,10,505,0,0,1);
        bases.put(20L,new Tes4BaseFormResolver.Base(
            20L,"ACTI","WRDrawBridge01",
            "Architecture/WhiteRun/WRDrawBridge01.nif",false));
        var architecture=pose(303,20,10000,0,0,1);
        var sameClutter=Tes4PlacedNifScene.findVisibleAll(
            Arrays.asList(clutter1,clutter2,architecture),bases,
            "meshes/clutter/test.nif");
        var archLinks=Tes4PlacedNifScene.findVisibleAll(
            Arrays.asList(clutter1,clutter2,architecture),bases,
            "meshes/architecture/whiterun/wrdrawbridge01.nif");
        assert sameClutter.size()==2 && archLinks.size()==1;
        var priority=new Tes4LocalWorldScene.Planner();
        for(var one:sameClutter)priority.offer(parts,one);
        priority.offer(parts,archLinks.get(0));
        var chosen=priority.best();
        assert chosen.instances()==1:"real architecture has semantic priority";
        assert chosen.build().details.contains("WRDrawBridge01");
        var smallScene=new Tes4LocalWorldScene.Builder();
        assert smallScene.add(parts,sameClutter.get(0));
        assert smallScene.add(parts,sameClutter.get(1));
        assert smallScene.sceneQualityScore()<chosen.sceneQualityScore();
        assert Tes4LocalWorldScene.MAX_DISTANCE<=2048:
            "excessive world spacing made tiny floating meshes";
        System.out.println("PASS bounded nearby Skyrim REFR multi-mesh local scene and safe distant exclusion");
    }
}
