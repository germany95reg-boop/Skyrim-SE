package dev.skyrimsemobile.launcher;
import java.io.IOException;
import java.util.Arrays;
public final class DdsBcDecoderTest {
    private static int count=0;
    private static void check(boolean ok,String msg){count++;if(!ok)throw new AssertionError(msg);}
    private static void u32(byte[] d,int p,long v){for(int i=0;i<4;i++)d[p+i]=(byte)(v>>(i*8));}
    private static byte[] make(int width,int height,String fourCC, int mip){
        int blockBytes=fourCC.equals("DXT1")?8:16;int total=128;int w=width,h=height;
        for(int i=0;i<mip;i++){total+=((w+3)/4)*((h+3)/4)*blockBytes;w=Math.max(1,w/2);h=Math.max(1,h/2);}
        byte[] d=new byte[total];d[0]='D';d[1]='D';d[2]='S';d[3]=' ';
        u32(d,4,124);u32(d,12,height);u32(d,16,width);u32(d,28,mip);u32(d,76,32);u32(d,80,4);
        for(int i=0;i<4;i++)d[84+i]=(byte)fourCC.charAt(i);
        return d;
    }
    public static void main(String[] args)throws Exception{
        byte[] bc1=make(4,4,"DXT1",1);
        bc1[128]=(byte)0x00;bc1[129]=(byte)0xf8; // red endpoint RGB565 F800
        bc1[130]=(byte)0xe0;bc1[131]=(byte)0x07; // green 07E0
        DdsBcDecoder.Image img=DdsBcDecoder.decode(bc1);
        check(img.width==4&&img.height==4,"dimensions");
        check(img.argb.length==16,"pixels");
        check(img.argb[0]==0xffff0000,"BC1 red");
        bc1[132]=0x55; img=DdsBcDecoder.decode(bc1);check(img.argb[0]==0xff00ff00,"BC1 green selector");
        bc1[132]=(byte)0xaa;img=DdsBcDecoder.decode(bc1);check((img.argb[0]&0xff000000)==0xff000000,"BC1 interpolated alpha");
        // DXT1 transparency: endpoint 0 <= endpoint 1, selector 3
        bc1[128]=0;bc1[129]=0;bc1[132]=(byte)0xff;img=DdsBcDecoder.decode(bc1);
        check((img.argb[0]>>>24)==0,"BC1 transparent palette");
        byte[] bc3=make(4,4,"DXT5",1);
        bc3[128]=(byte)255;bc3[129]=0; // alpha endpoints
        bc3[136]=0;bc3[137]=(byte)0xf8;bc3[138]=(byte)0xe0;bc3[139]=0x07;
        img=DdsBcDecoder.decode(bc3);check(img.argb[0]==0xffff0000,"BC3 red alpha 255");
        bc3[130]=1;img=DdsBcDecoder.decode(bc3);check((img.argb[0]>>>24)==0,"BC3 transparent alpha endpoint");
        // nonmultiple-of-four edge, all valid pixel bounds
        byte[] edge=make(5,3,"DXT1",1);
        edge[128]=0;edge[129]=(byte)0xf8;edge[136]=0;edge[137]=(byte)0xf8;
        img=DdsBcDecoder.decode(edge);check(img.argb.length==15,"clipped BC blocks");
        // Test the actual discovered sizes, mip levels and bounded preview output.
        img=DdsBcDecoder.decode(make(128,256,"DXT1",7));check(img.argb.length==32768,"real BC1 dimensions");
        img=DdsBcDecoder.decode(make(128,256,"DXT5",7));check(img.argb.length==32768,"real BC3 dimensions");
        byte[] invalid=make(4,4,"DXT5",1);invalid=Arrays.copyOf(invalid,127);
        try {DdsBcDecoder.decode(invalid);throw new AssertionError("accepted truncated DDS");}
        catch(IOException ok){count++;}
        try{DdsBcDecoder.decode(make(4096,4096,"DXT1",1));throw new AssertionError("accepted oversized DDS");}
        catch(IOException ok){count++;}
        try{DdsBcDecoder.decode(make(4,4,"DXT3",1));throw new AssertionError("accepted BC2");}
        catch(IOException ok){count++;}
        System.out.println("PASS DdsBcDecoderTest: "+count+" assertions");
    }
}
