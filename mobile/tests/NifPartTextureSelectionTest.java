package dev.skyrimsemobile.launcher;

import java.io.IOException;

/** No commercial Skyrim data: regression for matching a texture to its own NIF shape. */
public final class NifPartTextureSelectionTest {
    private static int checks;
    private static void ok(boolean yes,String detail){
        checks++;
        if(!yes)throw new AssertionError(detail);
    }
    private static void fails(NifMaterialLinkInspector.Result result,int part){
        try{
            NifPartTextureSelection.require(result,part);
            throw new AssertionError("Part "+part+" unexpectedly accepted");
        }catch(IOException expected){checks++;}
    }
    public static void main(String[] args)throws Exception{
        byte[] fixture=NifMaterialLinkInspectorTest.example();
        NifMaterialLinkInspector.Result r=NifMaterialLinkInspector.inspect(fixture);
        NifMaterialLinkInspector.Link first=NifPartTextureSelection.require(r,0);
        NifMaterialLinkInspector.Link second=NifPartTextureSelection.require(r,1);
        ok(first.shape==0&&second.shape==1,"exact part block IDs");
        ok(first.shader==2&&first.textureSet==4,"first chain");
        ok(second.shader==3&&second.textureSet==5,"second chain");
        ok("textures/a_d.dds".equals(first.diffuse),"first diffuse");
        ok("textures/b_d.dds".equals(second.diffuse),"second diffuse, no reused first DDS");
        ok("textures/b_n.dds".equals(second.normal),"second normal");
        fails(r,99);
        fails(null,0);
        NifBlockInspector.Result index=NifBlockInspector.inspect(fixture);
        byte[] bad=fixture.clone();
        NifMaterialLinkInspectorTest.set(bad,index.blocks.get(1).byteOffset+92,-1);
        NifMaterialLinkInspector.Result withBrokenSecond=NifMaterialLinkInspector.inspect(bad);
        ok(NifPartTextureSelection.require(withBrokenSecond,0).shape==0,
            "broken second part does not hide valid first");
        fails(withBrokenSecond,1);
        System.out.println("PASS v2.12 chosen NIF part textures: "+checks+" checks");
    }
}
