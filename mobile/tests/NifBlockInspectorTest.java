package dev.skyrimsemobile.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Pure Java fixture test for NIF 20.2.0.7 header, block index and bounds. */
public final class NifBlockInspectorTest {
    private static int checks;
    static void expect(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static void u16(ByteArrayOutputStream b,int v){b.write(v&255);b.write((v>>>8)&255);}
    static void u32(ByteArrayOutputStream b,int v){for(int i=0;i<4;i++)b.write((v>>>(8*i))&255);}
    static void text(ByteArrayOutputStream b,String s){byte[] a=s.getBytes(StandardCharsets.UTF_8);u32(b,a.length);b.write(a,0,a.length);}
    static void shortText(ByteArrayOutputStream b,String s){byte[] a=s.getBytes(StandardCharsets.UTF_8);b.write(a.length);b.write(a,0,a.length);}
    static void raw(ByteArrayOutputStream b,String s){byte[] a=s.getBytes(StandardCharsets.US_ASCII);b.write(a,0,a.length);}
    static int indicesPos;
    static int sizePos;
    static int streamPos;
    static byte[] valid(){
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        raw(b,"Gamebryo File Format, Version 20.2.0.7\n");
        u32(b,0x14020007);b.write(1);u32(b,12);u32(b,3);
        streamPos=b.size();u32(b,100);
        shortText(b,"Blender");shortText(b,"");shortText(b,"");
        u16(b,3);text(b,"BSFadeNode");text(b,"BSTriShape");text(b,"BSLightingShaderProperty");
        indicesPos=b.size();u16(b,0);u16(b,1);u16(b,2);
        sizePos=b.size();u32(b,12);u32(b,24);u32(b,8);
        u32(b,2);u32(b,8);text(b,"Scene");text(b,"Mesh");
        u32(b,0); // num groups
        for(int i=0;i<44;i++)b.write(0);
        u32(b,1);u32(b,0); // footer roots
        return b.toByteArray();
    }
    static void bad(byte[] data,String label){
        try{NifBlockInspector.inspect(data);throw new AssertionError("Expected failure: "+label);}
        catch(IOException ok){checks++;}
    }
    static void write32(byte[] b,int pos,int val){for(int i=0;i<4;i++)b[pos+i]=(byte)(val>>>(8*i));}
    public static void main(String[] args)throws Exception{
        byte[] data=valid();
        NifBlockInspector.Result r=NifBlockInspector.inspect(data);
        expect(r.blockCount==3 && r.blockTypeCount==3,"block/type count");
        expect(r.streamVersion==100 && r.userVersion==12,"Skyrim SE metadata");
        expect(r.meshCandidateCount==1,"BSTriShape detected");
        expect(r.rootCount==1,"root reference parsed");
        expect(r.blocks.get(0).byteOffset+12==r.blocks.get(1).byteOffset,"block offsets");
        expect(r.counts.get("BSTriShape")==1,"type histogram");
        expect(r.report().contains("Геометрия NIF НЕ декодировались")||r.report().contains("Вершины, текстуры"),"no false geometry claim");
        byte[] bytes=data.clone();bytes[indicesPos+2]=7;bad(bytes,"type index overflows");
        bytes=data.clone();write32(bytes,sizePos+4,0x7fffffff);bad(bytes,"block length huge");
        bytes=data.clone();bytes[streamPos]= (byte)130;bad(bytes,"FO4 unsupported");
        bytes=data.clone();bytes["Gamebryo File Format, Version 20.2.0.7\n".length()+4]=0;bad(bytes,"big endian");
        bad(Arrays.copyOf(data,data.length-5),"truncated footer");
        bytes=data.clone();write32(bytes,data.length-4,1000);bad(bytes,"invalid root ref");
        bytes=data.clone();write32(bytes,"Gamebryo File Format, Version 20.2.0.7\n".length()+4+1+4,8193);bad(bytes,"too many blocks");
        System.out.println("PASS: NIF block table v1.0 synthetic tests: "+checks+" checks");
    }
}
