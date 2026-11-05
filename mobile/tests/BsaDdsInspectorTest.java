package dev.skyrimsemobile.launcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import net.jpountz.lz4.LZ4FrameOutputStream;

/** Integration fixture for bounded BSA v105 DDS payload reading. */
public final class BsaDdsInspectorTest {
    static int checks=0;
    static void ok(boolean b,String name){checks++;if(!b)throw new AssertionError(name);}
    static void le(byte[] a,int offset,int n){for(int i=0;i<4;i++)a[offset+i]=(byte)(n>>(i*8));}
    static byte[] data(){
        byte[] x=new byte[128+2048];x[0]='D';x[1]='D';x[2]='S';x[3]=' ';
        le(x,4,124);le(x,12,64);le(x,16,64);le(x,28,1);le(x,76,32);le(x,80,4);
        x[84]='D';x[85]='X';x[86]='T';x[87]='1';return x;
    }
    static byte[] place(byte[] a,int off){byte[] v=new byte[off+a.length];System.arraycopy(a,0,v,off,a.length);return v;}
    static byte[] compressed(byte[] in)throws IOException{
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(LZ4FrameOutputStream frame=new LZ4FrameOutputStream(output)){frame.write(in);}
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        for(int i=0;i<4;i++)b.write((in.length>>(i*8))&255);
        b.write(output.toByteArray());return b.toByteArray();
    }
    static BsaDdsInspector.Result inspect(byte[] a,int at,int bytes,boolean compressed,long flags)throws IOException{
        return BsaDdsInspector.inspect(new ByteArrayInputStream(a),
            new BsaCatalogReader.Entry("textures/creationclub/bgssse001/rods/lureargonian.dds",bytes,at,compressed),flags);
    }
    static void rejected(byte[] a,int at,int bytes,boolean compressed,long flags){
        try{inspect(a,at,bytes,compressed,flags);throw new AssertionError("accepted invalid BSA DDS");}
        catch(IOException expected){checks++;}
    }
    public static void main(String[] args)throws Exception{
        byte[] plain=data();int offset=2048;
        var a=inspect(place(plain,offset),offset,plain.length,false,0x83);
        ok(a.dds.width==64&&a.dds.format.contains("BC1"),"plain DDS");
        ok(a.recordBytes==plain.length&&a.offset==offset,"BSA record bounds");
        ok(!a.bsaCompressed&&a.decodedBytes==plain.length,"plain payload size");
        byte[] packed=compressed(plain);
        a=inspect(place(packed,offset),offset,packed.length,true,0x83);
        ok(a.bsaCompressed&&a.dds.width==64,"LZ4 frame DDS");
        ByteArrayOutputStream wrapped=new ByteArrayOutputStream();
        wrapped.write(3);wrapped.write("abc".getBytes());wrapped.write(packed);
        byte[] withName=wrapped.toByteArray();
        a=inspect(place(withName,offset),offset,withName.length,true,0x183);
        ok(a.dds.height==64,"embedded name and LZ4");
        byte[] wrong=packed.clone();le(wrong,0,plain.length+1);
        rejected(place(wrong,offset),offset,wrong.length,true,0x83);
        rejected(place(Arrays.copyOf(plain,100),offset),offset,plain.length,false,0x83);
        rejected(place(plain,offset),offset,8*1024*1024+1,false,0x83);
        byte[] corrupt=plain.clone();corrupt[0]='X';rejected(place(corrupt,offset),offset,corrupt.length,false,0x83);
        rejected(place(plain,offset),offset,plain.length,false,0xC3);
        System.out.println("PASS: "+checks+" read-only BSA DDS single-entry and LZ4 bounds checks");
    }
}
