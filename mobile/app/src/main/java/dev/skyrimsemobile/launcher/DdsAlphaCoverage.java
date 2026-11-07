package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only alpha coverage of the *diffuse* first mip (never normal gloss).
 *
 * This is a conservative MASK/HOLE preview, not translucent alpha blending.
 * Matching Vulkan shaders discard only alpha < 0.05 (0..12 in RGBA8).
 * An Image shared by several BSTriShape is sampled once in CPU diagnostics.
 */
public final class DdsAlphaCoverage {
    public static final int CUTOUT_ALPHA_EXCLUSIVE=13;
    public static final long MAX_SOURCE_PIXELS=8L*1024*1024;
    private DdsAlphaCoverage(){}

    public static final class Result {
        public final int diffuseParts,uniqueTextures,partsWithCutouts;
        public final long totalPixels,cutoutPixels,partialAlphaPixels;
        Result(int parts,int images,int masks,long pixels,long cutouts,long partial){
            diffuseParts=parts;uniqueTextures=images;partsWithCutouts=masks;
            totalPixels=pixels;cutoutPixels=cutouts;partialAlphaPixels=partial;
        }
        public boolean hasCutouts(){return cutoutPixels>0;}
        public String report(){
            return String.format(Locale.ROOT,
                "Прозрачных (<5%%) пикселей: %d из %d · частей с вырезами: %d из %d"+
                " · полупрозрачных: %d · уникальных diffuse DDS: %d",
                cutoutPixels,totalPixels,partsWithCutouts,diffuseParts,
                partialAlphaPixels,uniqueTextures);
        }
    }

    private static final class Sample {
        final long cutouts,partial;
        Sample(long cutouts,long partial){
            this.cutouts=cutouts;this.partial=partial;
        }
    }

    public static Result inspect(Map<Integer,DdsBcDecoder.Image> perShape)
            throws IOException {
        if(perShape==null)throw new IOException("Нет diffuse DDS для альфа-проверки");
        if(perShape.size()>NifGeometryDecoder.MAX_PARTS)
            throw new IOException("Слишком много BSTriShape для альфа-проверки");
        IdentityHashMap<DdsBcDecoder.Image,Sample> seen=new IdentityHashMap<>();
        long allPixels=0,cutoutPixels=0,partialPixels=0;
        int maskParts=0,parts=0;
        for(DdsBcDecoder.Image image:perShape.values()){
            if(image==null)continue;
            parts++;
            if(image.width<1||image.height<1||image.width>2048||image.height>2048||
               image.argb==null||image.argb.length!=(long)image.width*image.height)
                throw new IOException("Повреждённый diffuse mip DDS");
            Sample one=seen.get(image);
            if(one==null){
                long size=(long)image.width*image.height;
                if(allPixels+size>MAX_SOURCE_PIXELS)
                    throw new IOException("Превышен лимит памяти альфа-проверки");
                long cut=0,partial=0;
                for(int color:image.argb){
                    int alpha=color>>>24;
                    if(alpha<CUTOUT_ALPHA_EXCLUSIVE)cut++;
                    else if(alpha<255)partial++;
                }
                one=new Sample(cut,partial);
                seen.put(image,one);
                allPixels+=size;cutoutPixels+=cut;partialPixels+=partial;
            }
            if(one.cutouts>0)maskParts++;
        }
        return new Result(parts,seen.size(),maskParts,allPixels,
            cutoutPixels,partialPixels);
    }
}
