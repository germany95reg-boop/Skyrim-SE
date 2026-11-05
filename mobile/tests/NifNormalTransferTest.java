package dev.skyrimsemobile.launcher;
public final class NifNormalTransferTest {
    private static int assertions=0;
    private static void ok(boolean b,String info){assertions++;if(!b)throw new AssertionError(info);}
    public static void main(String[] args){
        float[] triangles={0,0,0,1,0,0,0,1,0};
        float[] uv={0,0,1,0,0,1};
        ObjWireParser.Model model=new ObjWireParser.Model(
            new float[]{0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0},3,3);
        int[] rgba={0xff8888ff,0xff6688ff,0xff8888ff,0xff8888ff};
        DdsBcDecoder.Image diffuse=new DdsBcDecoder.Image(2,2,rgba,"BC1/DXT1");
        DdsBcDecoder.Image normal=new DdsBcDecoder.Image(2,2,rgba,"BC3/DXT5");
        NifGeometryTransfer.offerTextured(model,triangles,uv,diffuse,normal,"Test","details");
        rgba[0]=0;
        NifGeometryTransfer.Selected loaded=NifGeometryTransfer.take();
        ok(loaded!=null,"selected");
        ok(loaded.normalArgb!=null&&loaded.normalArgb.length==4,"normal retained");
        ok(loaded.normalArgb[0]==0xff8888ff,"normal copy");
        ok(loaded.argb[0]==0xff8888ff,"diffuse copy");
        ok(loaded.normalWidth==2&&loaded.normalHeight==2,"normal bounds");
        ok(NifGeometryTransfer.take()==null,"one-shot handoff");
        NifGeometryTransfer.offerTextured(model,triangles,uv,diffuse,"Test","diffuse-only");
        ok(NifGeometryTransfer.take().normalArgb==null,"diffuse-only fallback");
        try{
            NifGeometryTransfer.offerTextured(model,triangles,uv,diffuse,
                new DdsBcDecoder.Image(1,1,new int[]{0},"BC3"),"Test","invalid");
            throw new AssertionError("bad normal size accepted");
        }catch(IllegalArgumentException e){assertions++;}
        try{
            NifGeometryTransfer.offerTextured(model,triangles,new float[]{0,0},diffuse,normal,"Test","invalid");
            throw new AssertionError("bad UV count accepted");
        }catch(IllegalArgumentException e){assertions++;}
        System.out.println("PASS v2.2 NIF normal transfer: "+assertions+" assertions");
    }
}
