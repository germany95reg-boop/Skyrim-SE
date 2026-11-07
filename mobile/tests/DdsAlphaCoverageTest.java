package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Synthetic diffuse-alpha coverage; includes shared DDS and normal-map exclusion. */
public final class DdsAlphaCoverageTest {
    static int checks=0;
    static void ok(boolean c,String name){checks++;if(!c)throw new AssertionError(name);}
    static DdsBcDecoder.Image image(int... argb){
        return new DdsBcDecoder.Image(2,2,argb,"RGBA8 synthetic");
    }
    static void reject(Map<Integer,DdsBcDecoder.Image> map,String name){
        try { DdsAlphaCoverage.inspect(map);throw new AssertionError(name); }
        catch(IOException expected){checks++;}
    }
    public static void main(String[] args)throws Exception{
        Map<Integer,DdsBcDecoder.Image> data=new HashMap<>();
        DdsBcDecoder.Image opaque=image(0xffffffff,0xff00ff00,0xff000000,0xff998877);
        DdsBcDecoder.Image cutout=image(0x00000000,0x0cffffff,0x0dff0000,0x80aabbcc);
        data.put(5,opaque);data.put(8,cutout);data.put(12,cutout);
        DdsAlphaCoverage.Result result=DdsAlphaCoverage.inspect(data);
        ok(result.diffuseParts==3,"3 shape references");
        ok(result.uniqueTextures==2,"same diffuse decoded once");
        ok(result.partsWithCutouts==2,"two parts reuse transparent map");
        ok(result.totalPixels==8,"8 pixels, no duplicate cache counting");
        ok(result.cutoutPixels==2,"exact alpha values 0 and 12 discarded");
        ok(result.partialAlphaPixels==2,"alpha 13 and 128 are NOT discarded");
        ok(result.hasCutouts(),"actual cutouts present");
        ok(result.report().contains("Прозрачных"),"Russian report");
        ok(result.report().contains("частей с вырезами: 2 из 3"),"shape counts visible");
        ok(DdsAlphaCoverage.CUTOUT_ALPHA_EXCLUSIVE==13,"matches GLSL alpha < 0.05");
        data.clear();
        data.put(1,opaque);
        DdsAlphaCoverage.Result solid=DdsAlphaCoverage.inspect(data);
        ok(!solid.hasCutouts(),"full alpha is entirely opaque");
        ok(solid.partsWithCutouts==0,"no falsely detected alpha mask");
        ok(solid.partialAlphaPixels==0,"no falsely detected translucent pixels");
        data.put(2,image(0x01ffffff,0x02ffffff,0x03ffffff,0x04ffffff));
        DdsAlphaCoverage.Result masked=DdsAlphaCoverage.inspect(data);
        ok(masked.cutoutPixels==4,"all four alpha values < 13 are masked");
        data.put(3,new DdsBcDecoder.Image(2,2,new int[3],"broken"));
        reject(data,"reject wrong image length");
        data.remove(3);
        data.put(3,new DdsBcDecoder.Image(4096,4,new int[16],"broken"));
        reject(data,"reject excessively wide diffuse image");
        reject(null,"reject null maps");
        Map<Integer,DdsBcDecoder.Image> many=new HashMap<>();
        for(int i=0;i<NifGeometryDecoder.MAX_PARTS+1;i++)many.put(i,opaque);
        reject(many,"reject excessive parts");
        // Normal DDS alpha may store gloss and MUST NOT be counted here.
        data.clear();data.put(9,opaque);
        ok(DdsAlphaCoverage.inspect(data).cutoutPixels==0,
            "normal gloss alpha excluded: source map is diffuse only");
        System.out.println("PASS v2.21 diffuse-alpha cutout diagnostics: "+checks+" checks");
    }
}
