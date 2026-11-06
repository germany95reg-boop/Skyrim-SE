package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Synthetic coloured tiles and UV-carrying NIF meshes. No game data needed. */
public final class NifCompositeDdsAtlasTest {
    private static int checks=0;
    private static void ok(boolean condition,String description){
        checks++;if(!condition)throw new AssertionError(description);
    }
    private static DdsBcDecoder.Image solid(int argb){
        int[] c=new int[16];Arrays.fill(c,argb);
        return new DdsBcDecoder.Image(4,4,c,"test RGBA8");
    }
    private static NifGeometryDecoder.Parts fixture()throws IOException{
        NifGeometryDecoder.Mesh mesh=NifGeometryDecoder.decode(NifGeometryDecoderTest.validUv());
        return new NifGeometryDecoder.Parts(Arrays.asList(
            new NifGeometryDecoder.Part(5,mesh,null),
            new NifGeometryDecoder.Part(8,mesh,null)),2);
    }
    public static void main(String[] args)throws Exception{
        NifGeometryDecoder.Parts parts=fixture();
        NifSceneComposer.Combined combined=NifSceneComposer.combine(parts);
        Map<Integer,DdsBcDecoder.Image> perPart=new HashMap<>();
        DdsBcDecoder.Image red=solid(0xffff0000),green=solid(0xff00ff00);
        perPart.put(5,red);perPart.put(8,green);
        NifCompositeDdsAtlas.Result atlas=NifCompositeDdsAtlas.build(parts,combined,perPart);
        ok(atlas.textureCount==2&&atlas.neutralParts==0,"two independent materials");
        ok(atlas.grid==2&&atlas.width==2048,"bounded atlas");
        ok(atlas.uvAndSlot.length==combined.triangleCount*9,"one slot per vertex");
        ok(atlas.uvAndSlot[2]==1f,"first shape uses red slot 1");
        int secondStart=parts.items.get(0).mesh.triangles*9;
        ok(atlas.uvAndSlot[secondStart+2]==2f,"second shape uses green slot 2");
        ok(atlas.argb[1024]==0xffff0000,"red tile 1");
        ok(atlas.argb[1024*atlas.width]==0xff00ff00,"green tile 2");
        ok(atlas.argb[0]==0xff73777e,"neutral fallback tile 0");

        perPart.remove(8);
        NifCompositeDdsAtlas.Result missing=NifCompositeDdsAtlas.build(parts,combined,perPart);
        ok(missing.textureCount==1&&missing.neutralParts==1,"missing texture keeps geometry");
        ok(missing.uvAndSlot[secondStart+2]==0f,"missing shape assigned neutral");
        perPart.put(8,red);
        NifCompositeDdsAtlas.Result shared=NifCompositeDdsAtlas.build(parts,combined,perPart);
        ok(shared.textureCount==1,"reused texture decoded only once");
        ok(shared.uvAndSlot[secondStart+2]==1f,"shared material uses same slot");
        perPart.clear();
        try{
            NifCompositeDdsAtlas.build(parts,combined,perPart);
            throw new AssertionError("atlas with zero DDS accepted");
        }catch(IOException expected){checks++;}
        System.out.println("PASS v2.13 composite material atlas: "+checks+" checks");
    }
}
