package dev.skyrimsemobile.launcher;

/** One-shot in-memory handoff to the existing Vulkan line renderer. No disk writes or Binder-sized arrays. */
final class NifGeometryTransfer {
    private static Selected pending;
    static final class Selected {
        final ObjWireParser.Model model;
        final float[] surfaceTriangles;
        final float[] surfaceUVs;
        final int[] argb;
        final int textureWidth,textureHeight;
        final int[] normalArgb;
        final int normalWidth,normalHeight;
        final String label, details;
        Selected(ObjWireParser.Model model,float[] surfaceTriangles,float[] uv,int[] pixels,
                 int width,int height,int[] normal,int nw,int nh,String label,String details){
            this.model=model;this.surfaceTriangles=surfaceTriangles;
            this.surfaceUVs=uv;this.argb=pixels;textureWidth=width;textureHeight=height;
            normalArgb=normal;normalWidth=nw;normalHeight=nh;
            this.label=label;this.details=details;}
    }
    static synchronized void offer(ObjWireParser.Model model,float[] surfaceTriangles,String label,String details){
        pending=new Selected(model,surfaceTriangles,null,null,0,0,null,0,0,label,details);
    }
    static synchronized void offerTextured(ObjWireParser.Model model,float[] surfaceTriangles,
            float[] uv,DdsBcDecoder.Image image,String label,String details){
        offerTextured(model,surfaceTriangles,uv,image,null,label,details);
    }
    static synchronized void offerTextured(ObjWireParser.Model model,float[] surfaceTriangles,
            float[] uv,DdsBcDecoder.Image image,DdsBcDecoder.Image normal,String label,String details){
        if(uv==null||image==null||surfaceTriangles==null||
           uv.length!=(surfaceTriangles.length/3)*2)
            throw new IllegalArgumentException("NIF UV triangle payload and DDS must match");
        if(normal!=null&&(normal.width!=image.width||normal.height!=image.height))
            throw new IllegalArgumentException("Normal/Diffuse DDS sizes differ");
        pending=new Selected(model,surfaceTriangles.clone(),uv.clone(),image.argb.clone(),
            image.width,image.height,normal==null?null:normal.argb.clone(),
            normal==null?0:normal.width,normal==null?0:normal.height,label,details);
    }
    static synchronized Selected take(){
        Selected result=pending;pending=null;return result;
    }
}
