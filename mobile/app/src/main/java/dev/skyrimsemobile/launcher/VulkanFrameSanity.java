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
        public final int foregroundWidth,foregroundHeight;
        public final boolean visibleGeometryEvidence,smallProjectedFootprint;
        Result(int pixels,int different,int range,int buckets,int boundWidth,int boundHeight,
               boolean visible,boolean small){
            sampledPixels=pixels;nonBackground=different;
            brightnessRange=range;distinctBrightnessBuckets=buckets;
            foregroundWidth=boundWidth;foregroundHeight=boundHeight;
            visibleGeometryEvidence=visible;smallProjectedFootprint=small;
        }
        public String summary(){
            return String.format(Locale.ROOT,
                "%d из %d пикселей отличаются от фона (%.2f%% кадра) · "+
                "границы изображения %d×%d пикселей · диапазон яркости %d · оттенков яркости %d",
                nonBackground,sampledPixels,sampledPixels>0?nonBackground*100.0/sampledPixels:0,
                foregroundWidth,foregroundHeight,brightnessRange,distinctBrightnessBuckets);
        }
    }

    public static Result analyze(int[] argb,int width,int height)throws IOException {
        if(argb==null||width<16||height<16||width>512||height>512||
           (long)width*height!=argb.length)
            throw new IOException("Некорректный размер PixelCopy-буфера");
        int nonBackground=0,min=255,max=0;
        int minX=width,minY=height,maxX=-1,maxY=-1;
        boolean[] buckets=new boolean[16];
        int bucketCount=0;
        for(int i=0;i<argb.length;i++) {
            int color=argb[i];
            int r=(color>>>16)&255,g=(color>>>8)&255,b=color&255;
            int distance=Math.abs(r-CLEAR_R)+Math.abs(g-CLEAR_G)+Math.abs(b-CLEAR_B);
            if(distance>=45){
                nonBackground++;
                int x=i%width,y=i/width;
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);
                minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            }
            int lum=(54*r+183*g+19*b+128)>>>8;
            min=Math.min(min,lum);
            max=Math.max(max,lum);
            int bucket=Math.min(15,lum/16);
            if(!buckets[bucket]){
                buckets[bucket]=true;
                bucketCount++;
            }
        }
        // An elongated model (e.g. fishing rope, wire mesh) can cover few
        // pixels but span a large axis. A SMALL bounding box in BOTH axes is
        // stronger evidence for poor camera framing than percent alone.
        int boundWidth=nonBackground>0?maxX-minX+1:0;
        int boundHeight=nonBackground>0?maxY-minY+1:0;
        int minForeground=Math.max(48,argb.length/1000);
        boolean evidence=nonBackground>=minForeground && max-min>=20 && bucketCount>=2;
        boolean tiny=evidence &&
            (long)boundWidth*100<=(long)width*18 &&
            (long)boundHeight*100<=(long)height*18;
        return new Result(argb.length,nonBackground,max-min,bucketCount,
                          boundWidth,boundHeight,evidence,tiny);
    }
}
