package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** No commercial Skyrim assets. Validate exact material/normal slots. */
public final class NifCompositeNormalAtlasTest {
    private static int checks;
    static void ok(boolean condition,String message){
        checks++;
        if(!condition)throw new AssertionError(message);
    }
    static DdsBcDecoder.Image image(int color){
        int[] pixels=new int[16];Arrays.fill(pixels,color);
        return new DdsBcDecoder.Image(4,4,pixels,"synthetic");
    }
    static void reject(NifGeometryDecoder.Parts p,NifCompositeDdsAtlas.Result d,
                       Map<Integer,DdsBcDecoder.Image> n,String message){
        try {
            NifCompositeNormalAtlas.build(p,d,n);
            throw new AssertionError("Accepted "+message);
        }catch(IOException ex){checks++;}
    }
    public static void main(String[] args)throws Exception{
        NifGeometryDecoder.Parts parts=NifCompositeDdsAtlasTest.fixture();
        NifSceneComposer.Combined scene=NifSceneComposer.combine(parts);
        DdsBcDecoder.Image red=image(0xffff0000),green=image(0xff00ff00);
        Map<Integer,DdsBcDecoder.Image> diffuse=new HashMap<>();
        diffuse.put(5,red);diffuse.put(8,green);
        NifCompositeDdsAtlas.Result atlas=
            NifCompositeDdsAtlas.build(parts,scene,diffuse);
        Map<Integer,DdsBcDecoder.Image> normals=new HashMap<>();
        normals.put(5,image(0xff1122cc));
        normals.put(8,image(0xffaabbdd));
        NifCompositeNormalAtlas.Result stacked=
            NifCompositeNormalAtlas.build(parts,atlas,normals);
        ok(stacked.width==2048 && stacked.height==2048,"same sampler bounds");
        ok(stacked.grid==2,"normal tiles use same diffuse grid");
        ok(stacked.normalParts==2&&stacked.neutralNormalParts==0,"2 valid shape normals");
        ok(stacked.uniqueNormals==2,"unique textures counted accurately");
        ok(stacked.stackedArgb.length==2048*2048,"one GPU sampler RGBA8");
        ok(stacked.stackedArgb[1024]==0xffff0000,"first diffuse red in top half");
        ok(stacked.stackedArgb[512*2048]==0xff00ff00,
            "second diffuse green in top half");
        ok(stacked.stackedArgb[1024*2048+1024]==0xff1122cc,
            "normal red-material tile mapped by slot, not NIF order");
        ok(stacked.stackedArgb[1536*2048]==0xffaabbdd,
            "normal green-material tile mapped independently");
        ok(stacked.stackedArgb[1024*2048]==0xff8080ff,
            "neutral slot normal is flat, never arbitrary missing DDS");
        ok(scene.triangleCount==2&&scene.triangles.length==18,
            "stacking does not modify any 3D face or world transform");
        ok(atlas.uvAndSlot[2]==1 && atlas.uvAndSlot[11]==2,
            "normal tiles use exact diffuse material slot IDs");

        normals.remove(8);
        NifCompositeNormalAtlas.Result missing=
            NifCompositeNormalAtlas.build(parts,atlas,normals);
        ok(missing.normalParts==1&&missing.neutralNormalParts==1,
            "missing normal uses neutral, keeps diffuse");
        ok(missing.stackedArgb[1536*2048]==0xff8080ff,
            "missing normal will not invent texture from another BSTriShape");
        normals.clear();
        reject(parts,atlas,normals,"no normal DDS");
        // Same diffuse reused with two different normal DDS MUST NOT be
        // silently coalesced into a single incorrect material tile.
        diffuse.put(8,red);
        NifCompositeDdsAtlas.Result shared=
            NifCompositeDdsAtlas.build(parts,scene,diffuse);
        normals.put(5,image(0xff1122dd));normals.put(8,image(0xff445566));
        reject(parts,shared,normals,"shared diffuse slot with conflicting normals");
        normals.put(8,normals.get(5));
        NifCompositeNormalAtlas.Result reused=
            NifCompositeNormalAtlas.build(parts,shared,normals);
        ok(reused.normalParts==2&&reused.uniqueNormals==1,
            "shared diffuse and normal DDS decoded once, correctly reused");
        normals.put(8,new DdsBcDecoder.Image(4,4,new int[2],"broken"));
        reject(parts,atlas,normals,"normal DDS pixel dimensions mismatch");
        System.out.println("PASS v2.18 composite NIF normal atlas: "+checks+" checks");
    }
}
