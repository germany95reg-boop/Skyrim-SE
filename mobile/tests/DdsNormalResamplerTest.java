package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.Arrays;

/** Synthetic normal-map size mismatch; no commercial DDS files used. */
public final class DdsNormalResamplerTest {
    private static int checks;
    private static void ok(boolean condition,String reason){
        checks++;if(!condition)throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception{
        int[] px={0xff000000,0xffff0000,0xff00ff00,0xff0000ff};
        DdsBcDecoder.Image small=new DdsBcDecoder.Image(2,2,px,"BC5 RG channels");
        DdsBcDecoder.Image large=DdsNormalResampler.match(small,4,4);
        ok(large.width==4&&large.height==4,"resize to diffuse mip");
        ok(large.argb.length==16,"correct RGBA pixel count");
        ok(large.argb[0]==px[0]&&large.argb[3]==px[1]&&
           large.argb[12]==px[2]&&large.argb[15]==px[3],
           "corner normal vectors preserved");
        ok(large.argb[1]!=px[0],"bilinear interpolation, not nearest neighbours");
        ok(DdsNormalResampler.match(small,2,2)==small,"matching dimensions preserved");
        DdsBcDecoder.Image tall=new DdsBcDecoder.Image(1,2,
            new int[]{0xff208080,0xffe08080},"BC3/DXT5");
        ok(DdsNormalResampler.match(tall,4,4).argb.length==16,
            "height/width differences accepted");
        try{
            DdsNormalResampler.match(small,2048,2048);
            throw new AssertionError("unbounded atlas normal size accepted");
        }catch(IOException expected){checks++;}
        try{
            DdsNormalResampler.match(new DdsBcDecoder.Image(4,4,new int[1],"BC5"),
                2,2);
            throw new AssertionError("truncated DDS image accepted");
        }catch(IOException expected){checks++;}
        System.out.println("PASS v2.13 DDS normal first-mip resampling: "+checks+" checks");
    }
}
