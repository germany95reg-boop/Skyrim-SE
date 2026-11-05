package dev.skyrimsemobile.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Synthetic BSShaderTextureSet fixtures; no proprietary game bytes. */
public final class NifMaterialInspectorTest {
    static int checks;
    static void ok(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg);}
    static void u16(ByteArrayOutputStream out,int n){out.write(n&255);out.write((n>>>8)&255);}
    static void u32(ByteArrayOutputStream out,int n){for(int j=0;j<4;j++)out.write((n>>>(8*j))&255);}
    static void shortText(ByteArrayOutputStream out,String s){byte[] b=s.getBytes(StandardCharsets.UTF_8);out.write(b.length);out.write(b,0,b.length);}
    static void text(ByteArrayOutputStream out,String s){byte[] b=s.getBytes(StandardCharsets.UTF_8);u32(out,b.length);out.write(b,0,b.length);}
    static void raw(ByteArrayOutputStream out,String s){byte[] b=s.getBytes(StandardCharsets.US_ASCII);out.write(b,0,b.length);}
    static byte[] textureSet(String... strings){ByteArrayOutputStream b=new ByteArrayOutputStream();u32(b,strings.length);for(String s:strings)text(b,s);return b.toByteArray();}
    static byte[] file(byte[] set){
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        raw(b,"Gamebryo File Format, Version 20.2.0.7\n");
        u32(b,0x14020007);b.write(1);u32(b,12);u32(b,3);u32(b,100);
        shortText(b,"Blender");shortText(b,"");shortText(b,"");
        u16(b,3);text(b,"BSTriShape");text(b,"BSLightingShaderProperty");text(b,"BSShaderTextureSet");
        u16(b,0);u16(b,1);u16(b,2);
        u32(b,4);u32(b,4);u32(b,set.length);
        u32(b,0);u32(b,0); // strings and max string length
        u32(b,0); // groups
        u32(b,0);u32(b,0); // dummy mesh / shader blocks
        b.write(set,0,set.length);
        u32(b,1);u32(b,0); // 1 root
        return b.toByteArray();
    }
    static void fail(byte[] file,String msg){try{NifMaterialInspector.inspect(file);throw new AssertionError("should reject "+msg);}catch(IOException expected){checks++;}}
    static void setInt(byte[] b,int pos,int n){for(int i=0;i<4;i++)b[pos+i]=(byte)(n>>>(i*8));}
    public static void main(String[] args)throws Exception {
        String diffuse="textures\\creationclub\\bgssse001\\fishing\\lure_d.dds";
        String normal="textures/creationclub/bgssse001/fishing/lure_n.dds";
        byte[] raw=textureSet(diffuse,normal,"","", "", "");
        byte[] bytes=file(raw);
        NifMaterialInspector.Result r=NifMaterialInspector.inspect(bytes);
        ok(r.shapeCount==1,"one shape");ok(r.shaderPropertyCount==1,"one shader");
        ok(r.textureSets.size()==1,"one texture set");
        ok(r.textureSets.get(0).blockIndex==2,"block reference 2");
        ok(r.textureSets.get(0).slots.size()==6,"six slots");
        ok(r.textureSets.get(0).slots.get(0).path.equals(diffuse.replace('\\','/')),"normalized path");
        ok(r.textureSets.get(0).slots.get(1).path.equals(normal),"normal path");
        ok(r.textureSets.get(0).slots.get(2).path.isEmpty(),"empty slots allowed");
        ok(r.report().contains("Диффузная карта"),"channel roles");
        ok(r.report().contains("BSLightingShaderProperty"),"shader inventory");
        ok(r.report().contains("не проверены"),"no false associations");
        ok(r.report().contains("не наложены"),"no false texture rendering");
        ok(NifMaterialInspector.inspect(file(textureSet())).textureSets.get(0).slots.isEmpty(),"empty set");
        ok(NifMaterialInspector.inspect(file(textureSet("t/a.dds"))).textureSets.get(0).slots.size()==1,"one path");
        // The final block can contain 4 bytes of zero padding.
        ByteArrayOutputStream padded=new ByteArrayOutputStream();padded.write(raw);u32(padded,0);
        ok(NifMaterialInspector.inspect(file(padded.toByteArray())).textureSets.size()==1,"padding allowed");
        byte[] bad=raw.clone();setInt(bad,0,33);fail(file(bad),"slot count > 32");
        bad=raw.clone();setInt(bad,4,2048);fail(file(bad),"length > limit");
        bad=raw.clone();setInt(bad,4,999);fail(file(bad),"out of block");
        bad=raw.clone();setInt(bad,4,-1);fail(file(bad),"unsigned invalid length");
        fail(file(new byte[]{0,0,0}),"short block");
        bad=raw.clone();bad[8]=0;fail(file(bad),"embedded zero byte");
        bad=raw.clone();bad[8]=10;fail(file(bad),"embedded newline");
        bad=raw.clone();bad[8]=(byte)0xff;fail(file(bad),"invalid UTF8");
        bad=padded.toByteArray();bad[bad.length-1]=1;fail(file(bad),"nonzero padding");
        ByteArrayOutputStream huge=new ByteArrayOutputStream();u32(huge,0);for(int i=0;i<12;i++)huge.write(0);
        fail(file(huge.toByteArray()),"too much padding");
        System.out.println("PASS: NIF texture path v1.5: "+checks+" synthetic checks");
    }
}
