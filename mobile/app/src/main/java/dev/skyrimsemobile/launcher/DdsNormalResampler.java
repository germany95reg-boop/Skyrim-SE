package dev.skyrimsemobile.launcher;

import java.io.IOException;

/** CPU-only bounded bilinear resize for optional decoded DDS normal maps.
 * Useful when Skyrim diffuse/normal first mip dimensions differ; this is a
 * preview approximation, not a full Skyrim mip-chain or shader simulation.
 */
public final class DdsNormalResampler {
    private DdsNormalResampler(){}
    public static DdsBcDecoder.Image match(DdsBcDecoder.Image src,int width,int height)
            throws IOException{
        if(src==null||src.argb==null||
           src.width<1||src.height<1||src.width>2048||src.height>2048||
           src.argb.length!=(long)src.width*src.height||
           width<1||height<1||width>2048||height>1024||
           (long)width*height>2048L*1024L)
            throw new IOException("normal DDS: размеры вне безопасного предела Vulkan");
        if(src.width==width&&src.height==height)return src;
        int[] result=new int[width*height];
        for(int y=0;y<height;y++){
            double sy=Math.max(0,Math.min(src.height-1,(y+0.5)*src.height/height-0.5));
            int y0=(int)sy,y1=Math.min(src.height-1,y0+1);
            double fy=sy-y0;
            for(int x=0;x<width;x++){
                double sx=Math.max(0,Math.min(src.width-1,(x+0.5)*src.width/width-0.5));
                int x0=(int)sx,x1=Math.min(src.width-1,x0+1);
                double fx=sx-x0;
                int a=src.argb[y0*src.width+x0],b=src.argb[y0*src.width+x1];
                int c=src.argb[y1*src.width+x0],d=src.argb[y1*src.width+x1];
                int color=0;
                for(int shift:new int[]{24,16,8,0}){
                    double top=((a>>>shift)&255)*(1-fx)+((b>>>shift)&255)*fx;
                    double bottom=((c>>>shift)&255)*(1-fx)+((d>>>shift)&255)*fx;
                    int value=(int)Math.round(top*(1-fy)+bottom*fy);
                    color|=(Math.max(0,Math.min(255,value))<<shift);
                }
                result[y*width+x]=color;
            }
        }
        return new DdsBcDecoder.Image(width,height,result,src.format);
    }
}
