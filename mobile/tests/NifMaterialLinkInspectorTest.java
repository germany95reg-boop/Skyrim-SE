package dev.skyrimsemobile.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Generated multi-part NIF fixtures; no files from the commercial game. */
public final class NifMaterialLinkInspectorTest {
    static int checks=0;
    static void ok(boolean condition,String reason){
        checks++;
        if(!condition)throw new AssertionError(reason);
    }
    static void u16(ByteArrayOutputStream b,int x){
        b.write(x&255);b.write((x>>>8)&255);
    }
    static void u32(ByteArrayOutputStream b,int x){
        for(int i=0;i<4;i++)b.write((x>>>(8*i))&255);
    }
    static void name(ByteArrayOutputStream b,String s){
        byte[] c=s.getBytes(StandardCharsets.US_ASCII);u32(b,c.length);b.write(c,0,c.length);
    }
    static void write(ByteArrayOutputStream b,byte[] raw){b.write(raw,0,raw.length);}
    static void set(byte[] raw,int pos,int n){
        for(int i=0;i<4;i++)raw[pos+i]=(byte)(n>>>(i*8));
    }
    static byte[] shader(int textureSet){
        return shader(textureSet,0);
    }
    /** Real SSE shader prefix: Shader Type (4 bytes), followed by NiObjectNET
     * Name, Extra Count, Extra refs and Controller. This is the v2.10.1.1 fix.
     */
    static byte[] shader(int textureSet,int extraCount){
        byte[] b=new byte[68+extraCount*4];
        set(b,0,1); // shader type: Environment Map (a valid SSE shader type)
        set(b,4,-1); // NiObjectNET Name (string-table reference)
        set(b,8,extraCount); // the true extra-data count is at offset +8
        for(int i=0;i<extraCount;i++)set(b,12+i*4,-1);
        set(b,12+extraCount*4,-1); // NiObjectNET Controller
        set(b,40+extraCount*4,textureSet); // set ref after flags + UVs
        return b;
    }
    static byte[] example(){
        return example(false);
    }
    static byte[] example(boolean secondHasExtra){
        byte[] rawShape=NifGeometryDecoderTest.valid();
        byte[] shape=new byte[116+3*16+6];
        System.arraycopy(rawShape,NifGeometryDecoderTest.blockPos,shape,0,shape.length);
        byte[] a=shape.clone(),b=shape.clone();
        set(a,92,2); // shape #0 -> shader #2
        set(b,92,3); // shape #1 -> shader #3
        byte[][] blocks={
            a,b,shader(4),shader(5,secondHasExtra?1:0),
            NifMaterialInspectorTest.textureSet("textures/a_d.dds","textures/a_n.dds"),
            NifMaterialInspectorTest.textureSet("textures/b_d.dds","textures/b_n.dds")
        };
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        write(out,"Gamebryo File Format, Version 20.2.0.7\n"
            .getBytes(StandardCharsets.US_ASCII));
        u32(out,0x14020007);out.write(1);u32(out,12);u32(out,6);u32(out,100);
        for(int i=0;i<3;i++)out.write(0);
        u16(out,3);name(out,"BSTriShape");name(out,"BSLightingShaderProperty");
        name(out,"BSShaderTextureSet");
        for(int type:new int[]{0,0,1,1,2,2})u16(out,type);
        for(byte[] block:blocks)u32(out,block.length);
        u32(out,0);u32(out,0);u32(out,0); // string table + groups
        for(byte[] block:blocks)write(out,block);
        u32(out,1);u32(out,0);
        return out.toByteArray();
    }
    static void rejects(byte[] nif,String reason){
        try{NifMaterialLinkInspector.inspect(nif);throw new AssertionError(reason);}
        catch(IOException expected){checks++;}
    }
    public static void main(String[] args)throws Exception{
        byte[] file=example();
        NifBlockInspector.Result blocks=NifBlockInspector.inspect(file);
        NifMaterialLinkInspector.Result r=NifMaterialLinkInspector.inspect(file);
        ok(r.links.size()==2&&r.resolved==2,"two parts, two valid links");
        ok(r.links.get(0).shape==0&&r.links.get(1).shape==1,"shape indexes");
        ok(r.links.get(0).shader==2&&r.links.get(1).shader==3,"shader indexes");
        ok(r.links.get(0).textureSet==4&&r.links.get(1).textureSet==5,
            "texture set indexes not guessed by order");
        ok("textures/a_d.dds".equals(r.links.get(0).diffuse),"first diffuse");
        ok("textures/b_d.dds".equals(r.links.get(1).diffuse),"second diffuse");
        ok("textures/b_n.dds".equals(r.links.get(1).normal),"second normal");
        ok(r.report().contains("пока открывается без текстур"),"no false Vulkan claim");
        // Regression for the user's real SSE #6 failure: name at offset +4
        // is -1 (0xFFFFFFFF), not an invalid count of extra-data refs.
        ok(r.resolved==2,"SSE shader Name -1 does not trigger extra-data error");
        byte[] withShaderExtra=example(true);
        NifMaterialLinkInspector.Result withExtra=
            NifMaterialLinkInspector.inspect(withShaderExtra);
        ok(withExtra.resolved==2&&withExtra.links.get(1).textureSet==5,
            "SSE lighting shader with one real NiObjectNET extra ref");
        ok(withExtra.report().contains("textures/b_n.dds"),
            "texture-set slot remains correct after extra-data refs");

        byte[] shared=file.clone();
        set(shared,blocks.blocks.get(1).byteOffset+92,2);
        NifMaterialLinkInspector.Result sharedResult=NifMaterialLinkInspector.inspect(shared);
        ok(sharedResult.resolved==2&&sharedResult.links.get(1).textureSet==4,
            "two shapes may share the same material link");

        byte[] missing=file.clone();
        set(missing,blocks.blocks.get(1).byteOffset+92,-1);
        NifMaterialLinkInspector.Result partial=NifMaterialLinkInspector.inspect(missing);
        ok(partial.resolved==1&&partial.links.get(1).problem!=null,
            "missing shader is explicitly unresolved, other part remains");

        byte[] wrongType=file.clone();
        set(wrongType,blocks.blocks.get(0).byteOffset+92,5);
        ok(NifMaterialLinkInspector.inspect(wrongType).resolved==1,
            "shape referring to a texture set is not a shader");

        byte[] wrongSet=file.clone();
        set(wrongSet,blocks.blocks.get(2).byteOffset+40,3);
        ok(NifMaterialLinkInspector.inspect(wrongSet).resolved==1,
            "shader referring to another shader is not a texture set");

        byte[] outOfRange=file.clone();
        set(outOfRange,blocks.blocks.get(3).byteOffset+40,100000);
        ok(NifMaterialLinkInspector.inspect(outOfRange).resolved==1,
            "out of range reference is rejected without crash");

        byte[] invalidShaderExtras=file.clone();
        set(invalidShaderExtras,blocks.blocks.get(3).byteOffset+8,999);
        NifMaterialLinkInspector.Result brokenShader=
            NifMaterialLinkInspector.inspect(invalidShaderExtras);
        ok(brokenShader.resolved==1&&brokenShader.links.get(1).problem!=null,
            "invalid shader extra-data count is reported for only one part");

        byte[] extraCount=file.clone();
        set(extraCount,blocks.blocks.get(0).byteOffset+4,999);
        ok(NifMaterialLinkInspector.inspect(extraCount).resolved==1,
            "malformed shape extra-list does not hide second part");

        byte[] truncated=file.clone();
        truncated=java.util.Arrays.copyOf(truncated,truncated.length-12);
        rejects(truncated,"truncated file rejected");
        System.out.println("PASS v2.10.1 material part links: "+checks+" synthetic checks");
    }
}
