package dev.skyrimsemobile.launcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.jpountz.lz4.LZ4FrameOutputStream;

/** Synthetic LZ4 and malformed NIF tests, no game assets. */
public final class Tes4MatchedNifVerifierTest {
    static byte[] nif(){
        byte[] content=new byte[256];
        byte[] header="Gamebryo File Format, Version 20.2.0.7\n"
            .getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header,0,content,0,header.length);
        return content;
    }
    static byte[] writeU32(int n){
        return new byte[]{(byte)n,(byte)(n>>>8),(byte)(n>>>16),(byte)(n>>>24)};
    }
    static byte[] packed(byte[] content)throws Exception{
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        try(LZ4FrameOutputStream frame=new LZ4FrameOutputStream(compressed)){
            frame.write(content);
        }
        ByteArrayOutputStream wrapped=new ByteArrayOutputStream();
        wrapped.write(writeU32(content.length));wrapped.write(compressed.toByteArray());
        return wrapped.toByteArray();
    }
    static byte[] place(byte[] data,int offset){
        byte[] b=new byte[offset+data.length];
        System.arraycopy(data,0,b,offset,data.length);
        return b;
    }
    static void fails(byte[] bsa,int offset,int size,boolean compressed)throws Exception{
        try{
            Tes4MatchedNifVerifier.verify(
                new ByteArrayInputStream(bsa),
                new BsaCatalogReader.Entry("meshes/test.nif",size,offset,compressed),
                0x83,"Sample.bsa");
            throw new AssertionError("Expected invalid NIF rejection");
        }catch(IOException ok){}
    }
    public static void main(String[] args)throws Exception{
        final int offset=512;
        byte[] plain=nif();
        var uncompressed=Tes4MatchedNifVerifier.verify(
            new ByteArrayInputStream(place(plain,offset)),
            new BsaCatalogReader.Entry("meshes/markerx.nif",plain.length,offset,false),
            0x83,"Skyrim - Meshes0.bsa");
        assert !uncompressed.compressed&&uncompressed.decodedBytes==plain.length;
        assert uncompressed.report().contains("NIF-заголовок: РАСПОЗНАН");
        assert uncompressed.totalBlocks==-1;
        assert uncompressed.report().contains("Таблица NIF: не разобрана");
        byte[] lz4=packed(plain);
        var compressed=Tes4MatchedNifVerifier.verify(
            new ByteArrayInputStream(place(lz4,offset)),
            new BsaCatalogReader.Entry("meshes/barrier.nif",lz4.length,offset,true),
            0x83,"Skyrim - Meshes0.bsa");
        assert compressed.compressed&&compressed.storedBytes==lz4.length;
        assert compressed.decodedBytes==plain.length&&compressed.report().contains("LZ4");
        assert compressed.totalBlocks==-1 && compressed.staticParts==0;
        byte[] invalid="NOT A NIF HEADER".getBytes(StandardCharsets.US_ASCII);
        fails(place(invalid,offset),offset,invalid.length,false);
        byte[] broken=lz4.clone();broken[0]=0;broken[1]=0;
        fails(place(broken,offset),offset,broken.length,true);
        System.out.println("PASS bounded NIF payload validation, LZ4 and malformed data");
    }
}
