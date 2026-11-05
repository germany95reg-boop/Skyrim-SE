package dev.skyrimsemobile.launcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.jpountz.lz4.LZ4FrameOutputStream;

/** Plain-Java executable self-test. No Skyrim game files required. */
public final class BsaEntryInspectorTest {
    private static int checks=0;
    private static void yes(boolean ok,String description) {
        checks++; if(!ok)throw new AssertionError(description);
    }
    private static byte[] nif() {
        byte[] b=new byte[512];
        byte[] name="Gamebryo File Format, Version 20.2.0.7\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(name,0,b,0,name.length);
        return b;
    }
    private static void put32(ByteArrayOutputStream out, int v) {
        for(int i=0;i<4;i++)out.write((v >>> (8*i)) & 255);
    }
    private static byte[] frame(byte[] plain)throws IOException {
        ByteArrayOutputStream o=new ByteArrayOutputStream();
        try(LZ4FrameOutputStream stream=new LZ4FrameOutputStream(o)) {stream.write(plain);}
        return o.toByteArray();
    }
    private static byte[] atOffset(byte[] payload,int offset) {
        byte[] b=new byte[offset+payload.length];
        System.arraycopy(payload,0,b,offset,payload.length);
        return b;
    }
    private static BsaEntryInspector.Result read(byte[] bsa, String file, int offset, int size, boolean compressed, long flags)throws IOException {
        return BsaEntryInspector.inspect(new ByteArrayInputStream(bsa),
            new BsaCatalogReader.Entry(file,size,offset,compressed),flags);
    }
    private static void bad(byte[] bsa,String path,int offset,int size,boolean compressed,long flags) {
        try{read(bsa,path,offset,size,compressed,flags);throw new AssertionError("Unexpected acceptance: "+path);}
        catch(IOException expected){checks++;}
    }
    public static void main(String[] args)throws Exception {
        int offset=1024;
        byte[] plain=nif();
        byte[] archive=atOffset(plain,offset);
        var a=read(archive,"meshes/fish.nif",offset,plain.length,false,0x83);
        yes(a.header.recognized && a.decodedBytes==plain.length,"plain Skyrim NIF header");
        yes(!a.compressed && a.recordOffset==offset,"read record offset");
        byte[] compressed=frame(plain);
        ByteArrayOutputStream payload=new ByteArrayOutputStream();put32(payload,plain.length);payload.write(compressed);
        byte[] encoded=payload.toByteArray();
        a=read(atOffset(encoded,offset),"meshes/fish.nif",offset,encoded.length,true,0x83);
        yes(a.header.recognized && a.compressed,"LZ4 frame NIF");
        ByteArrayOutputStream prefixed=new ByteArrayOutputStream();
        prefixed.write(4);prefixed.write("fish".getBytes(StandardCharsets.US_ASCII));prefixed.write(encoded);
        byte[] withName=prefixed.toByteArray();
        a=read(atOffset(withName,offset),"meshes/fish.nif",offset,withName.length,true,0x183);
        yes(a.header.recognized && a.embeddedNameBytes==5,"embedded filename + LZ4");
        ByteArrayOutputStream wrongLength=new ByteArrayOutputStream();put32(wrongLength,plain.length-1);wrongLength.write(compressed);
        byte[] bomb=wrongLength.toByteArray();
        bad(atOffset(bomb,offset),"meshes/fish.nif",offset,bomb.length,true,0x83);
        bad(archive,"meshes/fish.nif",offset,plain.length+8,false,0x83);
        bad(archive,"textures/fish.dds",offset,plain.length,false,0x83);
        bad(archive,"meshes/fish.nif",offset,8*1024*1024+1,false,0x83);
        byte[] unknown=atOffset("not a NIF\n".getBytes(StandardCharsets.US_ASCII),offset);
        yes(!read(unknown,"meshes/fish.nif",offset,unknown.length-offset,false,0x83).header.recognized,
            "unknown content not accepted as NIF by extension");
        System.out.println("PASS: BSA v105 single NIF read, LZ4 frame, embedded name, bounds, corruption ("+checks+" checks)");
    }
}
