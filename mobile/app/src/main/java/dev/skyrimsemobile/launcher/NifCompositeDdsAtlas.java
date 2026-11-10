package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * v2.13 read-only CPU diffuse atlas for a composite static NIF.
 * Keeps shape-local UV tiling; the Vulkan fragment shader selects a tile per
 * triangle using a FLAT per-vertex slot (not guessed from UV or block order).
 * Missing/invalid DDS retain their geometry with a neutral grey tile.
 */
public final class NifCompositeDdsAtlas {
    public static final int MAX_GRID=4, MAX_TEXTURES=MAX_GRID*MAX_GRID-1;
    private static final int MAX_ATLAS_SIDE=2048;
    private static final int NEUTRAL=0xff73777e;
    private NifCompositeDdsAtlas(){}

    public static final class Result {
        public final float[] uvAndSlot; // expanded [U,V,slot], three floats per vertex
        public final int[] argb;
        public final int width,height,grid,textureCount,neutralParts;
        /** Actual diffuse-textured triangle count; -1 in legacy callers. */
        public final int texturedTriangles;
        /** Actual SSE vertex RGBA expanded per triangle (4 float channels each).
         * Null in legacy/standalone atlas builders that have no vertex colors.
         */
        public final float[] vertexColors;
        public final int vertexColorTriangles;
        Result(float[] uvAndSlot,int[] argb,int side,int grid,int textures,int neutrals){
            this(uvAndSlot,argb,side,grid,textures,neutrals,-1,null,0);
        }
        Result(float[] uvAndSlot,int[] argb,int side,int grid,int textures,
               int neutrals,int texturedTriangles){
            this(uvAndSlot,argb,side,grid,textures,neutrals,texturedTriangles,null,0);
        }
        Result(float[] uvAndSlot,int[] argb,int side,int grid,int textures,
               int neutrals,int texturedTriangles,float[] colors,int vertexColorTriangles){
            this.uvAndSlot=uvAndSlot;this.argb=argb;
            width=side;height=side;this.grid=grid;
            textureCount=textures;neutralParts=neutrals;
            this.texturedTriangles=texturedTriangles;
            this.vertexColors=colors==null?null:colors.clone();
            this.vertexColorTriangles=vertexColorTriangles;
        }
    }

    public static Result build(NifGeometryDecoder.Parts parts,NifSceneComposer.Combined scene,
                               Map<Integer,DdsBcDecoder.Image> perShape)throws IOException{
        return buildInternal(parts,scene,perShape,null);
    }

    /**
     * Paired-material atlas: two parts sharing ONE diffuse DDS can use DIFFERENT
     * normal DDS without corrupting each other's shader material slot.
     * Cache and dedup are per (diffuse identity, normal identity) pair, not
     * diffuse alone. This keeps exact per-BSTriShape texture-set associations.
     * The ordinary build() remains binary/visually compatible with v2.18.
     */
    public static Result buildPaired(NifGeometryDecoder.Parts parts,
                                     NifSceneComposer.Combined scene,
                                     Map<Integer,DdsBcDecoder.Image> perDiffuse,
                                     Map<Integer,DdsBcDecoder.Image> perNormal)
                                     throws IOException {
        if(perNormal==null)throw new IOException("Нет карты normal DDS по частям NIF");
        return buildInternal(parts,scene,perDiffuse,perNormal);
    }

    private static Result buildInternal(NifGeometryDecoder.Parts parts,
                               NifSceneComposer.Combined scene,
                               Map<Integer,DdsBcDecoder.Image> perShape,
                               Map<Integer,DdsBcDecoder.Image> perNormal)throws IOException{
        if(parts==null||scene==null||scene.triangles==null||perShape==null)
            throw new IOException("Нет составной геометрии или материалов NIF");
        if(scene.triangleCount<1||scene.triangleCount>NifSceneComposer.MAX_TRIANGLES||
           scene.triangles.length!=(long)scene.triangleCount*9)
            throw new IOException("Неверное количество треугольников составной NIF");

        // Slot zero always contains neutral material for unsupported/missing DDS.
        // Identity reuse is deliberate: the BSA reader caches each canonical DDS
        // only once, and several BSTriShape may share that exact decoded image.
        List<DdsBcDecoder.Image> tiles=new ArrayList<>();
        tiles.add(null);
        IdentityHashMap<DdsBcDecoder.Image,Integer> slots=new IdentityHashMap<>();
        IdentityHashMap<DdsBcDecoder.Image,
            IdentityHashMap<DdsBcDecoder.Image,Integer>> pairs=new IdentityHashMap<>();
        float[] packed=new float[scene.triangleCount*9];
        int vertexOffset=0,neutralParts=0;
        for(NifGeometryDecoder.Part part:parts.items){
            if(part.mesh==null)continue; // same ordering as NifSceneComposer.combine
            NifGeometryDecoder.Mesh mesh=part.mesh;
            int vertices=mesh.triangles*3;
            if(vertexOffset+vertices>scene.triangleCount*3)
                throw new IOException("Число частей не совпадает с составной геометрией");
            boolean hasUV=mesh.surfaceUVs.length==vertices*2;
            DdsBcDecoder.Image image=hasUV?perShape.get(part.blockIndex):null;
            int slot=0;
            if(image!=null){
                if(image.width<1||image.height<1||image.width>2048||image.height>2048||
                   image.argb==null||image.argb.length!=(long)image.width*image.height)
                    throw new IOException("Повреждённый первый mip DDS у части #"+part.blockIndex);
                // Pair identity matters only in paired mode: otherwise
                // retain the old exact diffuse-only material slot behavior.
                DdsBcDecoder.Image normal=perNormal==null?null:
                    perNormal.get(part.blockIndex);
                if(perNormal!=null&&normal!=null &&
                   (normal.width<1||normal.height<1||
                    normal.width>2048||normal.height>2048||normal.argb==null||
                    normal.argb.length!=(long)normal.width*normal.height))
                    throw new IOException("Повреждённая normal DDS у #"+part.blockIndex);
                IdentityHashMap<DdsBcDecoder.Image,Integer> normalSlots=
                    perNormal==null?null:pairs.get(image);
                if(perNormal!=null&&normalSlots==null){
                    normalSlots=new IdentityHashMap<>();
                    pairs.put(image,normalSlots);
                }
                Integer previous=perNormal==null?slots.get(image):normalSlots.get(normal);
                if(previous!=null)slot=previous;
                else if(tiles.size()<=MAX_TEXTURES){
                    slot=tiles.size();
                    tiles.add(image);
                    if(perNormal==null)slots.put(image,slot);
                    else normalSlots.put(normal,slot);
                }
            }
            if(slot==0)neutralParts++;
            for(int v=0;v<vertices;v++){
                int p=(vertexOffset+v)*3;
                if(hasUV){
                    float u=mesh.surfaceUVs[v*2], vv=mesh.surfaceUVs[v*2+1];
                    if(!Float.isFinite(u)||!Float.isFinite(vv)||
                       Math.abs(u)>8192f||Math.abs(vv)>8192f)
                        throw new IOException("Неверные UV координаты части #"+part.blockIndex);
                    packed[p]=u;packed[p+1]=vv;
                }
                packed[p+2]=slot;
            }
            vertexOffset+=vertices;
        }
        if(vertexOffset!=scene.triangleCount*3)
            throw new IOException("Составные треугольники не соответствуют NIF-частям");
        if(tiles.size()==1)throw new IOException("Нет ни одной доступной диффузной DDS для составной модели");

        int grid=1;
        while(grid*grid<tiles.size())grid++;
        if(grid>MAX_GRID)throw new IOException("Превышен лимит DDS-атласа");
        int tileSide=Math.min(1024,MAX_ATLAS_SIDE/grid);
        int side=tileSide*grid;
        int[] output=new int[side*side];
        for(int cell=0;cell<grid*grid;cell++){
            DdsBcDecoder.Image d=cell<tiles.size()?tiles.get(cell):null;
            int x0=(cell%grid)*tileSide,y0=(cell/grid)*tileSide;
            for(int y=0;y<tileSide;y++){
                int sy=d==null?0:(int)((long)y*d.height/tileSide);
                int row=(y0+y)*side+x0;
                for(int x=0;x<tileSide;x++){
                    int sx=d==null?0:(int)((long)x*d.width/tileSide);
                    output[row+x]=d==null?NEUTRAL:d.argb[sy*d.width+sx];
                }
            }
        }
        return new Result(packed,output,side,grid,tiles.size()-1,neutralParts);
    }
}
