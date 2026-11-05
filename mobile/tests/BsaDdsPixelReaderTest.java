package dev.skyrimsemobile.launcher;
import java.io.*;
import java.util.Arrays;
import net.jpountz.lz4.LZ4FrameOutputStream;

/** BSA offset/bounded single-entry, optional name and LZ4 tests. */
public final class BsaDdsPixelReaderTest {
 private static int count;
 private static void check(boolean ok,String msg){count++;if(!ok)throw new AssertionError(msg);}
 private static void u32(byte[] d,int p,int v){for(int i=0;i<4;i++)d[p+i]=(byte)(v>>(i*8));}
 private static byte[] dds(){
    byte[] d=new byte[136];d[0]='D';d[1]='D';d[2]='S';d[3]=' ';
    u32(d,4,124);u32(d,12,4);u32(d,16,4);u32(d,28,1);u32(d,76,32);u32(d,80,4);
    byte[] cc="DXT1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    System.arraycopy(cc,0,d,84,4);d[128]=0;d[129]=(byte)0xf8;d[130]=(byte)0xe0;d[131]=7;
    return d;
 }
 private static byte[] file(byte[] record){byte[] b=new byte[36+record.length];System.arraycopy(record,0,b,36,record.length);return b;}
 public static void main(String[] args)throws Exception{
    byte[] d=dds();
    BsaCatalogReader.Entry e=new BsaCatalogReader.Entry("textures/test.dds",d.length,36,false);
    DdsBcDecoder.Image img=BsaDdsPixelReader.preview(new ByteArrayInputStream(file(d)),e,0);
    check(img.argb[0]==0xffff0000,"uncompressed BSA decoded BC1");
    byte[] named=new byte[d.length+4];named[0]=3;named[1]='f';named[2]='o';named[3]='o';System.arraycopy(d,0,named,4,d.length);
    e=new BsaCatalogReader.Entry("textures/test.dds",named.length,36,false);
    check(BsaDdsPixelReader.preview(new ByteArrayInputStream(file(named)),e,0x100).argb[0]==0xffff0000,"embedded name");
    ByteArrayOutputStream compression=new ByteArrayOutputStream();
    try(LZ4FrameOutputStream lz=new LZ4FrameOutputStream(compression)){lz.write(d);}
    byte[] compressed=compression.toByteArray();
    byte[] record=new byte[4+compressed.length];u32(record,0,d.length);System.arraycopy(compressed,0,record,4,compressed.length);
    e=new BsaCatalogReader.Entry("textures/test.dds",record.length,36,true);
    check(BsaDdsPixelReader.preview(new ByteArrayInputStream(file(record)),e,0).argb.length==16,"LZ4 BSA decoded BC1");
    byte[] damaged=Arrays.copyOf(record,record.length);u32(damaged,0,d.length+1);
    try{BsaDdsPixelReader.read(new ByteArrayInputStream(file(damaged)),e,0);throw new AssertionError("mismatched LZ4 size accepted");}
    catch(IOException expected){count++;}
    try{BsaDdsPixelReader.read(new ByteArrayInputStream(file(new byte[127])),
        new BsaCatalogReader.Entry("textures/test.dds",127,36,false),0);throw new AssertionError("truncated accepted");}
    catch(IOException expected){count++;}
    try{BsaDdsPixelReader.read(new ByteArrayInputStream(file(d)),
        new BsaCatalogReader.Entry("meshes/evil.dds",d.length,36,false),0);throw new AssertionError("unsafe path accepted");}
    catch(IOException expected){count++;}
    System.out.println("PASS BsaDdsPixelReaderTest: "+count+" assertions");
 }
}
