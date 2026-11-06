package dev.skyrimsemobile.launcher;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Synthetic hierarchy tests. No copyrighted Skyrim files are required.
 * Verify nested parent rotation, translation, scale, invalid child refs,
 * cycles, repeated links, and unchanged per-shape DDS atlas assignments.
 */
public final class NifHierarchyTransformsTest {
    private static int checks;
    private static void ok(boolean cond,String label){
        checks++;
        if(!cond)throw new AssertionError(label);
    }
    private static void f32(ByteArrayOutputStream b,float v){
        NifGeometryDecoderTest.w32(b,Float.floatToIntBits(v));
    }
    private static void w(ByteArrayOutputStream b,int v){NifGeometryDecoderTest.w32(b,v);}
    private static void type(ByteArrayOutputStream b,String s){
        NifGeometryDecoderTest.text(b,s);
    }
    private static void data(ByteArrayOutputStream b,byte[] s){
        b.write(s,0,s.length);
    }

    private static byte[] node(float x,float y,float z,float scale,boolean rotateZ,
                               int...children){
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        w(b,-1);w(b,0);w(b,-1); // name / 0 extras / controller
        w(b,0x8000e); // flags
        f32(b,x);f32(b,y);f32(b,z);
        float[] R=rotateZ?
            new float[]{0,-1,0,1,0,0,0,0,1}:
            new float[]{1,0,0,0,1,0,0,0,1};
        for(float v:R)f32(b,v);
        f32(b,scale);w(b,-1); // NiAVObject scale/collision
        w(b,children.length);
        for(int child:children)w(b,child);
        w(b,0); // effects count in Skyrim stream 100
        return b.toByteArray();
    }
    private static byte[] shape(float x,float y,float z,boolean withUV){
        byte[] source=withUV?NifGeometryDecoderTest.validUv():NifGeometryDecoderTest.valid();
        NifBlockInspector.Result result;
        try{result=NifBlockInspector.inspect(source);}
        catch(Exception e){throw new AssertionError(e);}
        NifBlockInspector.Block b=result.blocks.get(0);
        byte[] data=java.util.Arrays.copyOfRange(source,b.byteOffset,b.byteOffset+b.byteLength);
        NifGeometryDecoderTest.set32(data,16,Float.floatToIntBits(x));
        NifGeometryDecoderTest.set32(data,20,Float.floatToIntBits(y));
        NifGeometryDecoderTest.set32(data,24,Float.floatToIntBits(z));
        return data;
    }
    private static byte[] file(byte[][] blocks,int[] types,int...roots){
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        data(b,"Gamebryo File Format, Version 20.2.0.7\n"
            .getBytes(StandardCharsets.US_ASCII));
        w(b,0x14020007);b.write(1);w(b,12);w(b,blocks.length);w(b,100);
        b.write(0);b.write(0);b.write(0);
        NifGeometryDecoderTest.w16(b,3);
        type(b,"BSFadeNode");type(b,"NiNode");type(b,"BSTriShape");
        for(int t:types)NifGeometryDecoderTest.w16(b,t);
        for(byte[] block:blocks)w(b,block.length);
        w(b,0);w(b,0);w(b,0); // strings and groups
        for(byte[] block:blocks)data(b,block);
        w(b,roots.length);
        for(int root:roots)w(b,root);
        return b.toByteArray();
    }
    private static byte[] nested(){
        return file(new byte[][]{
            node(100,0,0,1,false,1,3),       // BSFadeNode #0 root
            node(0,4,0,2,true,2),           // NiNode #1 nested 90-degree Z, scale x2
            shape(0,0,0,true),              // BSTriShape #2 child of #1
            shape(10,0,0,true)              // BSTriShape #3 child of root
        },new int[]{0,1,2,2},0);
    }
    public static void main(String[] args)throws Exception{
        byte[] file=nested();
        NifBlockInspector.Result table=NifBlockInspector.inspect(file);
        ok(table.rootCount==1&&table.rootIndices[0]==0,"root references retained");
        NifHierarchyTransforms.Result graph=NifHierarchyTransforms.read(file,table);
        ok(graph.nodeCount==2,"2 nodes decoded");
        ok(graph.connectedShapes==2&&graph.unlinkedShapes==0,
            "both shapes have inherited parents");
        double[] first=graph.parentWorld.get(2).apply(1,0,0);
        ok(Math.abs(first[0]-100)<1e-5,"nested transform rotates x into y");
        ok(Math.abs(first[1]-6)<1e-5,"nested translation and scale applied");
        double[] second=graph.parentWorld.get(3).apply(10,0,0);
        ok(Math.abs(second[0]-110)<1e-5 && Math.abs(second[1])<1e-5,
            "root transform applies to second shape without nested transform");

        NifGeometryDecoder.Parts parsed=NifGeometryDecoder.scan(file);
        ok(parsed.available==2,"both BSTriShape supported");
        ok(parsed.linkedParentShapes==2,"parent transform linked into scanned parts");
        NifSceneComposer.Combined composed=NifSceneComposer.combine(parsed);
        ok(composed.included==2&&composed.triangleCount==2,
            "combined triangles preserve part count");
        ok(composed.triangles[0]<composed.triangles[9],
            "nested first part stays left of second");
        ok(composed.triangles[1]>composed.triangles[10],
            "nested rotated geometry has higher Y than second");
        ok(composed.info.contains("NiNode/BSFadeNode"),
            "diagnostic report includes applied parent hierarchy");
        for(float v:composed.triangles)
            ok(Float.isFinite(v)&&Math.abs(v)<=1.00001f,
                "all world-coordinates normalized inside Vulkan bounds");

        Map<Integer,DdsBcDecoder.Image> images=new HashMap<>();
        int[] red={0xffff0000},green={0xff00ff00};
        images.put(2,new DdsBcDecoder.Image(1,1,red,"test"));
        images.put(3,new DdsBcDecoder.Image(1,1,green,"test"));
        NifCompositeDdsAtlas.Result atlas=NifCompositeDdsAtlas.build(parsed,composed,images);
        ok(atlas.textureCount==2&&atlas.neutralParts==0,"hierarchy keeps per-shape atlas");
        ok(atlas.uvAndSlot[2]==1&&atlas.uvAndSlot[11]==2,
            "DDS material slots stay aligned with transformed triangles");

        byte[] invalid=file(new byte[][]{node(0,0,0,1,false,7),shape(0,0,0,false)},
            new int[]{0,2},0);
        NifGeometryDecoder.Parts failed=NifGeometryDecoder.scan(invalid);
        ok(failed.available==1 && failed.linkedParentShapes==0,
            "invalid ref does not remove safe geometry");
        ok(failed.hierarchyNote.contains("ПРЕДУПРЕЖДЕНИЕ"),
            "invalid ref explicitly reports safe fallback");

        byte[] cyclic=file(new byte[][]{
            node(0,0,0,1,false,1),
            node(0,0,0,1,false,0),
            shape(0,0,0,false)
        },new int[]{0,1,2},0);
        NifGeometryDecoder.Parts cycle=NifGeometryDecoder.scan(cyclic);
        ok(cycle.available==1&&cycle.hierarchyNote.contains("ПРЕДУПРЕЖДЕНИЕ"),
            "cycle falls back without hanging");

        byte[] shared=file(new byte[][]{
            node(0,0,0,1,false,1,2),
            node(0,0,0,1,false,2),
            shape(0,0,0,false)
        },new int[]{0,1,2},0);
        NifGeometryDecoder.Parts ambiguous=NifGeometryDecoder.scan(shared);
        ok(ambiguous.hierarchyNote.contains("ПРЕДУПРЕЖДЕНИЕ"),
            "two parents do not silently overwrite matrices");

        byte[] orphan=file(new byte[][]{
            node(0,0,0,1,false,1),
            shape(0,0,0,false),
            shape(20,0,0,false)
        },new int[]{0,2,2},0);
        NifHierarchyTransforms.Result partial=NifHierarchyTransforms.read(orphan,
            NifBlockInspector.inspect(orphan));
        ok(partial.connectedShapes==1&&partial.unlinkedShapes==1,
            "unlinked shapes explicitly counted");

        byte[] tooMany=file(new byte[][]{
            node(0,0,0,1,false,new int[1025]),shape(0,0,0,false)
        },new int[]{0,2},0);
        NifGeometryDecoder.Parts bound=NifGeometryDecoder.scan(tooMany);
        ok(bound.hierarchyNote.contains("ПРЕДУПРЕЖДЕНИЕ"),"bounded child count");
        System.out.println("PASS v2.17 NiNode/BSFadeNode ancestor transforms: "+
            checks+" checks");
    }
}
