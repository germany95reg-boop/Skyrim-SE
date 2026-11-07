package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.Locale;

/**
 * Heuristic sanity check of a small, temporary PixelCopy of the native Vulkan
 * SurfaceView (not a screenshot of Android controls). No image is retained.
 *
 * This detects uniform/clear-only frames that cannot be caught by counting GPU
 * presents. It CANNOT prove exact texture pixels, correct lighting or game FPS.
 * The renderer's clear color is RGB (0.035, 0.040, 0.065) => approx (9,10,17).
 */
public final class VulkanFrameSanity {
    private static final int CLEAR_R=9, CLEAR_G=10, CLEAR_B=17;
    public static final int CAPTURE_WIDTH=192, CAPTURE_HEIGHT=256;
    private VulkanFrameSanity(){}

    public static final class Result {
        public final int sampledPixels,nonBackground,brightnessRange,distinctBrightnessBuckets;
        public final boolean visibleGeometryEvidence;
        Result(int pixels,int different,int range,int buckets,boolean visible){
            sampledPixels=pixels;nonBackground=different;
            brightnessRange=range;distinctBrightnessBuckets=buckets;
            visibleGeometryEvidence=visible;
        }
        public String summary(){
            return String.format(Locale.ROOT,
                "%d из %d пикселей отличаются от фона · диапазон яркости %d · оттенков яркости %d",
                nonBackground,sampledPixels,brightnessRange,distinctBrightnessBuckets);
        }
    }

    public static Result analyze(int[] argb,int width,int height)throws IOException {
        if(argb==null||width<16||height<16||width>512||height>512||
           (long)width*height!=argb.length)
            throw new IOException("Некорректный размер PixelCopy-буфера");
        int nonBackground=0,min=255,max=0;
        boolean[] buckets=new boolean[16];
        int bucketCount=0;
        for(int color:argb) {
            int r=(color>>>16)&255,g=(color>>>8)&255,b=color&255;
            int distance=Math.abs(r-CLEAR_R)+Math.abs(g-CLEAR_G)+Math.abs(b-CLEAR_B);
            if(distance>=45)nonBackground++;
            int lum=(54*r+183*g+19*b+128)>>>8;
            min=Math.min(min,lum);
            max=Math.max(max,lum);
            int bucket=Math.min(15,lum/16);
            if(!buckets[bucket]){
                buckets[bucket]=true;
                bucketCount++;
            }
        }
        // A visible, shaded solid mesh normally occupies substantially more
        // than 0.1% of this surface. Use WARN rather than FAIL for dark assets.
        int minForeground=Math.max(48,argb.length/1000);
        boolean evidence=nonBackground>=minForeground && max-min>=20 && bucketCount>=2;
        return new Result(argb.length,nonBackground,max-min,bucketCount,evidence);
    }
}
