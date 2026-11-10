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
        final float[] materialSlots;
        final float[] vertexColors; // RGBA per triangle vertex, real SSE data only
        final int diffuseAtlasGrid;
        final String label, details;
        Selected(ObjWireParser.Model model,float[] surfaceTriangles,float[] uv,int[] pixels,
                 int width,int height,int[] normal,int nw,int nh,String label,String details,
                 float[] slots,int grid){
            this(model,surfaceTriangles,uv,pixels,width,height,normal,nw,nh,
                 label,details,slots,grid,null);
        }
        Selected(ObjWireParser.Model model,float[] surfaceTriangles,float[] uv,int[] pixels,
                 int width,int height,int[] normal,int nw,int nh,String label,String details,
                 float[] slots,int grid,float[] colors){
            this.model=model;this.surfaceTriangles=surfaceTriangles;
            this.surfaceUVs=uv;this.argb=pixels;textureWidth=width;textureHeight=height;
            normalArgb=normal;normalWidth=nw;normalHeight=nh;
            materialSlots=slots;diffuseAtlasGrid=grid;
            vertexColors=colors;
            this.label=label;this.details=details;}
    }
    static synchronized void offer(ObjWireParser.Model model,float[] surfaceTriangles,String label,String details){
        pending=new Selected(model,surfaceTriangles,null,null,0,0,null,0,0,label,details,null,0);
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
            normal==null?0:normal.width,normal==null?0:normal.height,label,details,null,0);
    }
    /** Composite first-mip diffuse atlas, one material slot per triangle vertex. */
    static synchronized void offerAtlas(ObjWireParser.Model model,float[] triangles,
            NifCompositeDdsAtlas.Result atlas,String label,String details){
        if(model==null||triangles==null||atlas==null||atlas.argb==null||
           atlas.width<1||atlas.width>2048||atlas.height!=atlas.width||
           (long)atlas.width*atlas.height!=atlas.argb.length||
           atlas.grid<1||atlas.grid>4||
           triangles.length<9||triangles.length%9!=0||
           atlas.uvAndSlot.length!=(triangles.length/3)*3||
           (atlas.vertexColors!=null&&
            atlas.vertexColors.length!=(triangles.length/3)*4))
            throw new IllegalArgumentException("Composite NIF UV/atlas size mismatch");
        int verts=triangles.length/3;
        float[] uv=new float[verts*2],slots=new float[verts];
        for(int i=0;i<verts;i++){
            uv[i*2]=atlas.uvAndSlot[i*3];
            uv[i*2+1]=atlas.uvAndSlot[i*3+1];
            slots[i]=atlas.uvAndSlot[i*3+2];
        }
        pending=new Selected(model,triangles.clone(),uv,atlas.argb.clone(),
            atlas.width,atlas.height,null,0,0,label,details,slots,atlas.grid,
            atlas.vertexColors==null?null:atlas.vertexColors.clone());
    }
    /**
     * The v2.18 normal atlas is a full-height CPU-packed image with diffuse
     * above and matching normal-map slots below. No second GPU descriptor.
     */
    static synchronized void offerAtlasNormal(ObjWireParser.Model model,float[] triangles,
            NifCompositeDdsAtlas.Result diffuse,NifCompositeNormalAtlas.Result normals,
            String label,String details){
        if(normals==null||diffuse==null||
           normals.width!=diffuse.width||normals.height!=diffuse.height||
           normals.grid!=diffuse.grid||normals.stackedArgb==null||
           normals.stackedArgb.length!=(long)normals.width*normals.height)
            throw new IllegalArgumentException("Normal atlas dimensions differ from diffuse");
        offerAtlas(model,triangles,diffuse,label,details);
        Selected saved=pending;
        pending=new Selected(saved.model,saved.surfaceTriangles,saved.surfaceUVs,
            saved.argb,saved.textureWidth,saved.textureHeight,
            normals.stackedArgb.clone(),normals.width,normals.height,
            saved.label,saved.details,saved.materialSlots,saved.diffuseAtlasGrid,
            saved.vertexColors);
    }
    static synchronized Selected take(){
        Selected result=pending;pending=null;return result;
    }
}
