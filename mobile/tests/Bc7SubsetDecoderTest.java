package dev.skyrimsemobile.launcher;

import java.io.IOException;

/** Independent deterministic bitstream fixtures, no proprietary game DDS. */
public final class Bc7SubsetDecoderTest {
    private static int count;
    private static void check(boolean b,String msg){count++;if(!b)throw new AssertionError(msg);}
    static final class Writer {
        final byte[] bytes=new byte[16];int at=0;
        void bits(int v,int n){for(int i=0;i<n;i++,at++)
            bytes[at>>>3]|=((v>>>i)&1)<<(at&7);}
    }
    private static byte[] mode6(){
        Writer b=new Writer();
        b.bits(1<<6,7);
        int[] start={127,0,0,127},end={0,0,127,127};
        for(int ch=0;ch<4;ch++){
            b.bits(start[ch],7);b.bits(end[ch],7);
        }
        b.bits(1,1);b.bits(1,1);
        b.bits(0,3);
        for(int i=1;i<16;i++)b.bits(15,4);
        check(b.at==128,"mode6 totals exactly 128 bits");
        return b.bytes;
    }
    private static byte[] mode5(){
        Writer b=new Writer();
        b.bits(1<<5,6);b.bits(0,2);
        int[] start={127,0,0,255},end={0,0,127,64};
        for(int ch=0;ch<4;ch++){
            int bits=ch==3?8:7;
            b.bits(start[ch],bits);b.bits(end[ch],bits);
        }
        b.bits(0,1);for(int i=1;i<16;i++)b.bits(3,2);
        b.bits(0,1);for(int i=1;i<16;i++)b.bits(3,2);
        check(b.at==128,"mode5 totals exactly 128 bits");
        return b.bytes;
    }
    private static byte[] dds(byte[] block,int width,int height,int dxgi){
        int blocks=((width+3)/4)*((height+3)/4);
        byte[] file=new byte[148+16*blocks];
        file[0]='D';file[1]='D';file[2]='S';file[3]=' ';
        put(file,4,124);put(file,12,height);put(file,16,width);
        put(file,28,1);put(file,76,32);put(file,80,4);
        file[84]='D';file[85]='X';file[86]='1';file[87]='0';
        put(file,128,dxgi);put(file,132,3);put(file,140,1);
        for(int i=0;i<blocks;i++)System.arraycopy(block,0,file,148+16*i,16);
        return file;
    }
    private static void put(byte[] b,int p,int v){
        for(int i=0;i<4;i++)b[p+i]=(byte)(v>>>(8*i));
    }
    public static void main(String[] args)throws Exception{
        byte[] first=mode6();int[] redBlue=Bc7SubsetDecoder.decodeBlock(first,0);
        check(redBlue[0]==0xffff0101,"mode6 first endpoint incl pbit");
        check(redBlue[15]==0xff0101ff,"mode6 last endpoint incl pbit");
        byte[] second=mode5();int[] colors=Bc7SubsetDecoder.decodeBlock(second,0);
        check(colors[0]==0xffff0000,"mode5 red initial endpoint");
        check(colors[15]==0x400000ff,"mode5 blue final endpoint with alpha");
        var image=DdsBcDecoder.decode(dds(first,4,4,98));
        check(image.argb.length==16 && image.argb[0]==redBlue[0],"DX10 mode6 integrated");
        check(image.format.contains("BC7"),"real BC7 mode label");
        var clipped=DdsBcDecoder.decode(dds(second,5,3,99));
        check(clipped.argb.length==15 && clipped.argb[0]==colors[0],
            "mode5 DXGI 99 uneven dimensions");
        byte[] invalid=new byte[16];
        try{Bc7SubsetDecoder.decodeBlock(invalid,0);
            throw new AssertionError("zero BC7 block accepted");}
        catch(IOException ok){count++;}
        invalid=first.clone();invalid[0]=1; // mode0, explicitly unsupported
        try{Bc7SubsetDecoder.decodeBlock(invalid,0);
            throw new AssertionError("unsupported BC7 mode silently decoded");}
        catch(IOException ok){count++;}
        try{Bc7SubsetDecoder.decodeBlock(first,1);
            throw new AssertionError("truncated block accepted");}
        catch(IOException ok){count++;}
        System.out.println("PASS BC7 CPU mode5/6 first mip RGB alpha fixtures: "+count);
    }
}
