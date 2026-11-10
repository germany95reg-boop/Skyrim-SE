package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.Arrays;

public final class DdsPreviewDownsamplerTest {
    public static void main(String[] args)throws Exception{
        int[] original=new int[2048*2048];
        Arrays.fill(original,0xff224466);
        var source=new DdsBcDecoder.Image(2048,2048,original,"BC3");
        var preview=DdsPreviewDownsampler.fit(source,512);
        assert preview.width==512 && preview.height==512;
        assert preview.argb.length==512*512;
        assert preview.argb[0]==0xff224466 && preview.argb[preview.argb.length-1]==0xff224466;
        var stretched=DdsPreviewDownsampler.fit(new DdsBcDecoder.Image(
            1024,2048,new int[1024*2048],"BC7"),512);
        assert stretched.width==256&&stretched.height==512;
        var small=new DdsBcDecoder.Image(2,2,
            new int[]{0xffff0000,0xff00ff00,0xff0000ff,0xffffffff},"RGBA");
        assert DdsPreviewDownsampler.fit(small,512)==small;
        int[] colorful={0xffff0000,0xff00ff00,0xff0000ff,0xffffffff};
        var reduced=DdsPreviewDownsampler.fit(new DdsBcDecoder.Image(
            2,2,colorful,"test"),1);
        assert reduced.width==1&&reduced.height==1;
        assert reduced.argb[0]==0xff808080:
            Integer.toHexString(reduced.argb[0]);
        int[] transparency={0x00ff0000,0xff0000ff,0x0000ff00,0xff0000ff};
        var alpha=DdsPreviewDownsampler.fit(
            new DdsBcDecoder.Image(2,2,transparency,"alpha"),1);
        assert alpha.argb[0]==0x800000ff:
            Integer.toHexString(alpha.argb[0]);
        try{
            DdsPreviewDownsampler.fit(new DdsBcDecoder.Image(
                2,2,new int[1],"broken"),512);
            throw new AssertionError("Invalid DDS accepted");
        }catch(IOException expected){}
        long total=(long)DdsPreviewDownsampler.MAX_SIDE*
            DdsPreviewDownsampler.MAX_SIDE*15;
        assert total<=4L*1024*1024:
            "Fifteen bounded DDS previews must stay within 4 MP";
        System.out.println("PASS v2.48 bounded DDS CPU alpha-aware downsample");
    }
}
