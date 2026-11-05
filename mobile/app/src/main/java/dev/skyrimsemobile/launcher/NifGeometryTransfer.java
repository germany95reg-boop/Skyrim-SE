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
        final String label, details;
        Selected(ObjWireParser.Model model,float[] surfaceTriangles,float[] uv,int[] pixels,
                 int width,int height,String label,String details){
            this.model=model;this.surfaceTriangles=surfaceTriangles;
            this.surfaceUVs=uv;this.argb=pixels;textureWidth=width;textureHeight=height;
            this.label=label;this.details=details;}
    }
    static synchronized void offer(ObjWireParser.Model model,float[] surfaceTriangles,String label,String details){
        pending=new Selected(model,surfaceTriangles,null,null,0,0,label,details);
    }
    static synchronized void offerTextured(ObjWireParser.Model model,float[] surfaceTriangles,
            float[] uv,DdsBcDecoder.Image image,String label,String details){
        if(uv==null||image==null||surfaceTriangles==null||
           uv.length!=(surfaceTriangles.length/3)*2)
            throw new IllegalArgumentException("NIF UV triangle payload and DDS must match");
        pending=new Selected(model,surfaceTriangles,uv.clone(),image.argb.clone(),
                             image.width,image.height,label,details);
    }
    static synchronized Selected take(){
        Selected result=pending;pending=null;return result;
    }
}
