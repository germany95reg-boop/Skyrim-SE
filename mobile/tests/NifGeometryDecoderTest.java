package dev.skyrimsemobile.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** SSE BSTriShape wire geometry fixture with corruption/fuzz-type bounds checks. */
public final class NifGeometryDecoderTest {
    static int checks;
    static int blockPos,sizePos;
    static void ok(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
    static void w16(ByteArrayOutputStream out,int v){out.write(v&255);out.write((v>>>8)&255);}
    static void w32(ByteArrayOutputStream out,int v){for(int i=0;i<4;i++)out.write((v>>>(i*8))&255);}
    static void w64(ByteArrayOutputStream out,long v){w32(out,(int)v);w32(out,(int)(v>>>32));}
    static void f32(ByteArrayOutputStream out,float v){w32(out,Float.floatToIntBits(v));}
    static void text(ByteArrayOutputStream out,String s){byte[] x=s.getBytes(StandardCharsets.US_ASCII);w32(out,x.length);out.write(x,0,x.length);}
    static byte[] valid() {
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        byte[] header="Gamebryo File Format, Version 20.2.0.7\n".getBytes(StandardCharsets.US_ASCII);
        b.write(header,0,header.length);w32(b,0x14020007);b.write(1);w32(b,12);w32(b,1);w32(b,100);
        for(int k=0;k<3;k++)b.write(0); // export strings
        w16(b,1);text(b,"BSTriShape");w16(b,0);sizePos=b.size();w32(b,116+3*16+6);
        w32(b,0);w32(b,0);w32(b,0); // no strings/groups
        blockPos=b.size();
        w32(b,-1);w32(b,0);w32(b,-1); // NiObjectNET
        w32(b,0x8000e); // NiAVObject flags
        f32(b,0);f32(b,0);f32(b,0); // translation
        for(int i=0;i<9;i++)f32(b,(i==0||i==4||i==8)?1:0); // rotation
        f32(b,1);w32(b,-1); // scale, collision ref
        f32(b,0);f32(b,0);f32(b,0);f32(b,1); // bounding sphere
        w32(b,-1);w32(b,-1);w32(b,-1); // skin/shader/alpha refs
        w64(b,4L|(1L<<44)); // stride: 4 * 4, vertex attributes: position
        w16(b,1);w16(b,3);w32(b,54);
        for(float[] v:new float[][]{{0,0,0},{1,0,0},{0,1,0}}) {
            for(float c:v)f32(b,c);f32(b,0); // float xyz + w
        }
        w16(b,0);w16(b,1);w16(b,2);
        w32(b,1);w32(b,0); // one root
        return b.toByteArray();
    }
    static void set16(byte[] b,int p,int v){b[p]=(byte)v;b[p+1]=(byte)(v>>>8);}
    static void set32(byte[] b,int p,int v){for(int i=0;i<4;i++)b[p+i]=(byte)(v>>>(8*i));}
    static void set64(byte[] b,int p,long value){set32(b,p,(int)value);set32(b,p+4,(int)(value>>>32));}
    static byte[] validUv(){
        byte[] original=valid();
        int oldBase=blockPos+116;
        byte[] expanded=new byte[original.length+12];
        System.arraycopy(original,0,expanded,0,oldBase);
        int[] halfU={0,0x3c00,0},halfV={0,0,0x3c00};
        for(int vertex=0;vertex<3;vertex++){
            int dst=oldBase+vertex*20;
            System.arraycopy(original,oldBase+vertex*16,expanded,dst,16);
            set16(expanded,dst+16,halfU[vertex]);
            set16(expanded,dst+18,halfV[vertex]);
        }
        System.arraycopy(original,oldBase+48,expanded,oldBase+60,original.length-(oldBase+48));
        set32(expanded,sizePos,116+60+6);
        set32(expanded,blockPos+112,60+6);
        set64(expanded,blockPos+100,5L|(4L<<4)|(3L<<44));
        return expanded;
    }
    static void bad(byte[] bytes,String label){try{NifGeometryDecoder.decode(bytes);throw new AssertionError("Expected rejection: "+label);}catch(IOException ok){checks++;}}
    public static void main(String[] args)throws Exception {
        byte[] original=valid();
        NifGeometryDecoder.Mesh mesh=NifGeometryDecoder.decode(original);
        ok(mesh.shapeIndex==0,"shape index");
        ok(mesh.surfaceUVs.length==0,"UV absent in position-only BSTriShape");
        ok(NifGeometryDecoder.halfFloat(0x3c00)==1f,"half +1");
        ok(NifGeometryDecoder.halfFloat(0xbc00)==-1f,"half -1");
        ok(NifGeometryDecoder.halfFloat(0x0001)>0f,"half subnormal");
        ok(Float.isNaN(NifGeometryDecoder.halfFloat(0x7e00)),"half NaN");
        ok(mesh.lines.vertices==3,"vertex count");
        ok(mesh.triangles==1 && mesh.lines.edges==3,"triangle and edges");
        ok(mesh.stride==16 && mesh.vertexFlags==1,"descriptor");
        ok(mesh.lines.segments.length==18,"line buffer");
        ok(mesh.surfaceTriangles.length==9,"one triangle is three xyz vertices");
        float[] a=mesh.surfaceTriangles;
        ok(a[0]==-1f && a[1]==-1f && a[2]==-1f,"normalized origin matches line buffer");
        ok(a[3]==1f && a[4]==-1f && a[5]==-1f,"normalized triangle vertex 1");
        ok(a[6]==-1f && a[7]==1f && a[8]==-1f,"normalized triangle vertex 2");
        for(float coordinate:a)ok(Float.isFinite(coordinate)&&Math.abs(coordinate)<=1.001f,"valid bounded surface position");
        for(float f:mesh.lines.segments)ok(Float.isFinite(f)&&f>=-1.001f&&f<=1.001f,"normalized vertices");
        ok(mesh.report().contains("уникальных рёбер"),"report");
        byte[] uvBytes=validUv();
        NifGeometryDecoder.Mesh textured=NifGeometryDecoder.decode(uvBytes);
        ok(textured.stride==20 && textured.surfaceUVs.length==6,"UV stride and triangle count");
        ok(textured.surfaceUVs[0]==0f&&textured.surfaceUVs[1]==0f,"UV vertex zero");
        ok(textured.surfaceUVs[2]==1f&&textured.surfaceUVs[3]==0f,"UV vertex one");
        ok(textured.surfaceUVs[4]==0f&&textured.surfaceUVs[5]==1f,"UV vertex two");
        byte[] badUv=uvBytes.clone();set64(badUv,blockPos+100,5L|(1L<<4)|(3L<<44));
        bad(badUv,"UV offset points inside position");
        badUv=uvBytes.clone();set16(badUv,blockPos+116+16,0x7e00);
        bad(badUv,"UV half NaN");
        byte[] moreExtras=new byte[original.length+4];
        System.arraycopy(original,0,moreExtras,0,blockPos+8);
        for(int i=0;i<4;i++)moreExtras[blockPos+8+i]=(byte)0xff;
        System.arraycopy(original,blockPos+8,moreExtras,blockPos+12,original.length-(blockPos+8));
        set32(moreExtras,blockPos+4,1);
        set32(moreExtras,sizePos,116+4+3*16+6);
        ok(NifGeometryDecoder.decode(moreExtras).lines.edges==3,"variable extra-data reference list");
        byte[] x=original.clone();set16(x,blockPos+116+48+2,3);bad(x,"bad triangle index");
        x=original.clone();set32(x,blockPos+112,52);bad(x,"bad declared size");
        x=original.clone();set32(x,blockPos+4,129);bad(x,"excessive extra references");
        x=original.clone();x[blockPos+100]=1;bad(x,"stride too short");
        x=original.clone();for(int i=0;i<8;i++)x[blockPos+100+i]=0;bad(x,"missing position flags");
        x=original.clone();set16(x,blockPos+110,40001);bad(x,"vertex count too large");
        x=original.clone();set32(x,blockPos+116,0x7fc00000);bad(x,"NaN vertex");
        x=original.clone();set16(x,blockPos+108,0);bad(x,"zero triangle count");
        x=original.clone();for(int i=16;i<48;i+=4)set32(x,blockPos+116+i,0);bad(x,"collapsed vertices");
        bad(Arrays.copyOf(original,original.length-4),"truncated footer");
        // v1.2 NiAVObject local transform: 90-degree rotation around X, scale 2,
        // translation (5, 6, 7). The X/Y triangle becomes X/Z, with 2-unit extents.
        byte[] transformed=original.clone();
        set32(transformed,blockPos+16,Float.floatToIntBits(5f));
        set32(transformed,blockPos+20,Float.floatToIntBits(6f));
        set32(transformed,blockPos+24,Float.floatToIntBits(7f));
        float[] rotateX90={1,0,0,0,0,-1,0,1,0};
        for(int i=0;i<9;i++)set32(transformed,blockPos+28+i*4,Float.floatToIntBits(rotateX90[i]));
        set32(transformed,blockPos+64,Float.floatToIntBits(2f));
        NifGeometryDecoder.Mesh local=NifGeometryDecoder.decode(transformed);
        ok(local.rawDimensions[0]==1f && local.rawDimensions[1]==1f && local.rawDimensions[2]==0f,"original XYZ dimensions");
        ok(local.localDimensions[0]==2f && local.localDimensions[1]==0f && local.localDimensions[2]==2f,"local rotation and scale dimensions");
        ok(local.translation[0]==5f && local.translation[1]==6f && local.translation[2]==7f,"local translation decoded");
        ok(local.localScale==2f,"local uniform scale decoded");
        ok(local.surfaceTriangles.length==9,"transformed triangle vertex count");
        ok(local.surfaceTriangles[0]==-1f && local.surfaceTriangles[1]==-1f && local.surfaceTriangles[2]==-1f,"local triangles share transformed normalization");
        ok(local.report().contains("локального преобразования"),"diagnostic report includes transformed bounds");
        ok(local.compactInfo().contains("NIF XYZ"),"scene diagnostics include dimensions");
        byte[] badScale=original.clone();set32(badScale,blockPos+64,Float.floatToIntBits(0f));bad(badScale,"zero local scale");
        badScale=original.clone();set32(badScale,blockPos+64,Float.floatToIntBits(Float.NaN));bad(badScale,"nonfinite local scale");
        byte[] badRotation=original.clone();set32(badRotation,blockPos+28,0x7fc00000);bad(badRotation,"NaN rotation matrix");
        byte[] badTranslation=original.clone();set32(badTranslation,blockPos+16,0x7f800000);bad(badTranslation,"infinite translation");
        System.out.println("PASS: v1.4 SSE BSTriShape triangles + wireframe + local transforms: "+checks+" checks");
    }
}
