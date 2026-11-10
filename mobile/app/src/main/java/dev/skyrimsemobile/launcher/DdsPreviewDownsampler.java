package dev.skyrimsemobile.launcher;

import java.io.IOException;

/**
 * v2.48: bound retained DDS pixel memory BEFORE building the Vulkan atlas.
 * A 2048x2048 original is decoded once, then replaced by at most 512x512
 * pixels; preserve aspect ratio and average source pixels with alpha weighting.
 * Does not write or modify any game resources.
 */
public final class DdsPreviewDownsampler {
    public static final int MAX_SIDE=512;
    private DdsPreviewDownsampler(){}

    public static DdsBcDecoder.Image fit(DdsBcDecoder.Image source,int maxSide)
            throws IOException{
        if(source==null||source.argb==null||
           source.width<1||source.height<1||
           maxSide<1||maxSide>2048||
           source.argb.length!=(long)source.width*source.height)
            throw new IOException("Неверные размеры DDS перед уменьшением");
        int maximum=Math.max(source.width,source.height);
        if(maximum<=maxSide)return source;
        int outW=Math.max(1,(int)((long)source.width*maxSide/maximum));
        int outH=Math.max(1,(int)((long)source.height*maxSide/maximum));
        int[] result=new int[outW*outH];
        for(int y=0;y<outH;y++){
            int y0=(int)((long)y*source.height/outH);
            int y1=(int)((long)(y+1)*source.height/outH);
            y1=Math.max(y0+1,y1);
            for(int x=0;x<outW;x++){
                int x0=(int)((long)x*source.width/outW);
                int x1=(int)((long)(x+1)*source.width/outW);
                x1=Math.max(x0+1,x1);
                long alpha=0,red=0,green=0,blue=0;
                int count=0;
                for(int yy=y0;yy<y1;yy++){
                    int row=yy*source.width;
                    for(int xx=x0;xx<x1;xx++){
                        int c=source.argb[row+xx],a=(c>>>24)&255;
                        alpha+=a;red+=(long)((c>>>16)&255)*a;
                        green+=(long)((c>>>8)&255)*a;
                        blue+=(long)(c&255)*a;count++;
                    }
                }
                int a=(int)((alpha+count/2)/count);
                int r=alpha==0?0:(int)((red+alpha/2)/alpha);
                int g=alpha==0?0:(int)((green+alpha/2)/alpha);
                int b=alpha==0?0:(int)((blue+alpha/2)/alpha);
                result[y*outW+x]=(a<<24)|(r<<16)|(g<<8)|b;
            }
        }
        return new DdsBcDecoder.Image(outW,outH,result,
            source.format+" · preview "+outW+"×"+outH);
    }
}
