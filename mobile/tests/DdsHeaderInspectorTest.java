package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.Arrays;

/** Stand-alone Java tests for DDS, no Android SDK and no game files. */
public final class DdsHeaderInspectorTest {
    private static int checks=0;
    static void yes(boolean cond,String what){checks++;if(!cond)throw new AssertionError(what);}
    static void bad(byte[] b,long count){try{DdsHeaderInspector.inspect(b,count);throw new AssertionError("Invalid DDS accepted");}catch(IOException expected){checks++;}}
    static void dword(byte[] a,int p,int n){for(int i=0;i<4;i++)a[p+i]=(byte)(n>>(i*8));}
    static byte[] header(int w,int h,String fourCC,int mips){
        byte[] b=new byte[148];b[0]='D';b[1]='D';b[2]='S';b[3]=' ';
        dword(b,4,124);dword(b,8,0x1007);dword(b,12,h);dword(b,16,w);
        dword(b,28,mips);dword(b,76,32);dword(b,80,4);
        for(int i=0;i<4;i++)b[84+i]=(byte)fourCC.charAt(i);
        return b;
    }
    public static void main(String[] args)throws Exception{
        byte[] b=header(64,64,"DXT1",1);
        var a=DdsHeaderInspector.inspect(b,128+2048);
        yes(a.width==64&&a.height==64,"dimensions");
        yes(a.format.contains("BC1")&&a.blockCompressed,"legacy DXT1 BC1");
        yes(a.minimumBytesForMips==2048,"BC1 payload size");
        yes(a.headerBytes==128&&!a.isDx10,"classic DDS 128 header");
        yes(a.mipMaps==1&&a.arraySize==1,"classic levels");
        yes(a.report().contains("64 × 64"),"report dimensions");
        b=header(32,16,"DXT5",3);
        a=DdsHeaderInspector.inspect(b,128+672);
        yes(a.minimumBytesForMips==672,"BC3 mip chain");
        yes(a.mipMaps==3,"BC3 mip count");
        yes(a.format.contains("BC3"),"legacy DXT5 BC3");
        b=header(32,16,"DX10",3);dword(b,128,83);dword(b,132,3);dword(b,140,2);
        a=DdsHeaderInspector.inspect(b,148+672*2);
        yes(a.isDx10&&a.dxgiFormat==83,"DX10 BC5");
        yes(a.arraySize==2,"DX10 array size");
        yes(a.minimumBytesForMips==1344,"BC5 array mip size");
        yes(a.headerBytes==148,"DX10 extension");
        yes(a.format.equals("BC5"),"BC5 name");
        byte[] base=Arrays.copyOf(b,128);
        bad(base,148+1344);
        bad(Arrays.copyOf(b,127),1000);
        b=header(4,4,"DXT1",1);b[0]='X';bad(b,256);
        b=header(4,4,"DXT1",1);dword(b,4,125);bad(b,256);
        b=header(4,4,"DXT1",1);dword(b,76,16);bad(b,256);
        b=header(0,4,"DXT1",1);bad(b,256);
        b=header(4,16385,"DXT1",1);bad(b,256);
        b=header(4,4,"DXT1",17);bad(b,256);
        b=header(4,4,"DXT1",1);bad(b,128);
        b=header(4,4,"DX10",1);dword(b,128,98);dword(b,140,0);bad(b,300);
        b=header(4,4,"DX10",1);dword(b,128,98);dword(b,140,1);
        a=DdsHeaderInspector.inspect(b,148+16);
        yes(a.format.equals("BC7"),"DXGI BC7 support");
        b=header(4,4,"DX10",1);dword(b,128,28);dword(b,140,1);
        a=DdsHeaderInspector.inspect(b,148+64);
        yes(a.format.equals("RGBA8")&&!a.blockCompressed,"uncompressed DXGI color");
        b=header(4,4,"ZZZZ",1);
        a=DdsHeaderInspector.inspect(b,128+256);
        yes(a.format.contains("ZZZZ"),"unknown FourCC reported honestly");
        b=header(4,4,"DXT1",0);
        a=DdsHeaderInspector.inspect(b,136);
        yes(a.mipMaps==1,"zero mip default");
        System.out.println("PASS: "+checks+" DDS header, DX10/BCn and malformed input checks");
    }
}
