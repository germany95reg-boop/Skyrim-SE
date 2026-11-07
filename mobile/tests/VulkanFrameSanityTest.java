package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.Arrays;

/** Synthetic pixel matrices only; no Android device and no game assets. */
public final class VulkanFrameSanityTest {
    private static int checks;
    private static void ok(boolean condition,String message){
        checks++;
        if(!condition)throw new AssertionError(message);
    }
    private static void reject(int[] pixels,int w,int h,String label){
        try{
            VulkanFrameSanity.analyze(pixels,w,h);
            throw new AssertionError("accepted invalid "+label);
        }catch(IOException expected){checks++;}
    }
    public static void main(String[] args)throws Exception {
        int w=VulkanFrameSanity.CAPTURE_WIDTH,h=VulkanFrameSanity.CAPTURE_HEIGHT;
        int[] blank=new int[w*h];
        Arrays.fill(blank,0xff090a11);
        VulkanFrameSanity.Result clear=VulkanFrameSanity.analyze(blank,w,h);
        ok(clear.sampledPixels==w*h,"correct copy dimensions");
        ok(clear.nonBackground==0,"clear-only pixels are background");
        ok(clear.brightnessRange==0,"flat frame has zero contrast");
        ok(!clear.visibleGeometryEvidence,"presenting clear-only frames cannot PASS");
        Arrays.fill(blank,0xff000000);
        VulkanFrameSanity.Result black=VulkanFrameSanity.analyze(blank,w,h);
        ok(!black.visibleGeometryEvidence,"uniform black is not proof of mesh");
        Arrays.fill(blank,0xffeeeeee);
        VulkanFrameSanity.Result solid=VulkanFrameSanity.analyze(blank,w,h);
        ok(!solid.visibleGeometryEvidence,"uniform light frame is not mesh");
        Arrays.fill(blank,0xff090a11);
        for(int y=50;y<160;y++){
            for(int x=55;x<130;x++){
                int c=(x+y)%3==0?0xffecb34c:0xff545e9e;
                blank[y*w+x]=c;
            }
        }
        VulkanFrameSanity.Result mesh=VulkanFrameSanity.analyze(blank,w,h);
        ok(mesh.visibleGeometryEvidence,"shaded colorful mesh has visible evidence");
        ok(mesh.nonBackground>=8000,"mesh occupies substantial area");
        ok(mesh.brightnessRange>90,"mesh varies brightness");
        ok(mesh.distinctBrightnessBuckets>=3,"several pixel tones seen");
        ok(mesh.summary().contains("пикселей отличаются"),
            "copyable report includes color evidence");
        Arrays.fill(blank,0xff090a11);
        for(int x=0;x<25;x++)blank[x]=0xffffffff;
        VulkanFrameSanity.Result noise=VulkanFrameSanity.analyze(blank,w,h);
        ok(!noise.visibleGeometryEvidence,
            "one tiny UI-like highlight is not sufficient evidence");
        Arrays.fill(blank,0xff0a0b12);
        VulkanFrameSanity.Result nearClear=VulkanFrameSanity.analyze(blank,w,h);
        ok(!nearClear.visibleGeometryEvidence,"anti-aliased background noise is ignored");
        reject(null,w,h,"null pixels");
        reject(new int[1],w,h,"wrong length");
        reject(new int[256],16,15,"too small height");
        reject(new int[1024],513,2,"too large width");
        System.out.println("PASS v2.20 Vulkan in-memory framebuffer sanity: "+checks+" checks");
    }
}
