package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * One Vulkan sampler, two separately aligned material atlases: diffuse tiles
 * in the upper half, their matching tangent-space normal DDS in the lower half.
 * No GPU readback, BSA write, external files or new Android permissions.
 *
 * Reuses the existing diffuse slot IDs exactly. Different normal images that
 * share one diffuse slot are deliberately rejected rather than misassigned.
 * Such a model can still fall back to the verified diffuse-only atlas.
 */
public final class NifCompositeNormalAtlas {
    private static final int NEUTRAL_NORMAL=0xff8080ff;
    private NifCompositeNormalAtlas(){}

    public static final class Result {
        public final int[] stackedArgb;
        public final int width,height,grid,normalParts,neutralNormalParts,uniqueNormals;
        Result(int[] pixels,int width,int height,int grid,int normalParts,
               int neutralNormalParts,int uniqueNormals){
            stackedArgb=pixels;this.width=width;this.height=height;this.grid=grid;
            this.normalParts=normalParts;this.neutralNormalParts=neutralNormalParts;
            this.uniqueNormals=uniqueNormals;
        }
    }
    public static Result build(NifGeometryDecoder.Parts parts,
                               NifCompositeDdsAtlas.Result diffuse,
                               Map<Integer,DdsBcDecoder.Image> perShapeNormals)
                               throws IOException {
        if(parts==null||diffuse==null||perShapeNormals==null||diffuse.argb==null)
            throw new IOException("Нет данных NIF или diffuse atlas");
        int width=diffuse.width,height=diffuse.height,grid=diffuse.grid;
        if(width<2||height<2||width>2048||height>2048||
           height%2!=0||width%grid!=0||(height/2)%grid!=0||
           (long)width*height!=diffuse.argb.length||grid<1||grid>4)
            throw new IOException("Некорректный размер DDS-атласа для normal mapping");
        int halfHeight=height/2;
        DdsBcDecoder.Image[] slotNormals=new DdsBcDecoder.Image[grid*grid];
        boolean[] usedSlot=new boolean[grid*grid];
        int vertexOffset=0,normalParts=0,neutralParts=0;
        IdentityHashMap<DdsBcDecoder.Image,Boolean> unique=new IdentityHashMap<>();
        for(NifGeometryDecoder.Part part:parts.items){
            if(part.mesh==null)continue;
            int vertices=part.mesh.triangles*3;
            if(vertices<3||vertexOffset+vertices>diffuse.uvAndSlot.length/3)
                throw new IOException("Части NIF не совпадают с атласом");
            int slot=Math.round(diffuse.uvAndSlot[vertexOffset*3+2]);
            if(slot<0||slot>=slotNormals.length)
                throw new IOException("Неверный номер слота материала");
            for(int v=1;v<vertices;v++)
                if(Math.round(diffuse.uvAndSlot[(vertexOffset+v)*3+2])!=slot)
                    throw new IOException("Материал меняется посреди одной части NIF");
            DdsBcDecoder.Image normal=slot==0?null:perShapeNormals.get(part.blockIndex);
            if(normal!=null){
                if(normal.argb==null||normal.width<1||normal.height<1||
                   normal.width>2048||normal.height>2048||
                   normal.argb.length!=(long)normal.width*normal.height)
                    throw new IOException("Повреждённая normal DDS у #"+part.blockIndex);
                normalParts++;unique.put(normal,Boolean.TRUE);
            } else neutralParts++;
            if(usedSlot[slot]&&slotNormals[slot]!=normal)
                throw new IOException("Один diffuse slot "+slot+
                    " содержит несовместимые normal DDS; нужна отдельная пара материалов");
            usedSlot[slot]=true;slotNormals[slot]=normal;
            vertexOffset+=vertices;
        }
        if(vertexOffset*3!=diffuse.uvAndSlot.length)
            throw new IOException("Количество NIF-треугольников не совпадает с UV");
        if(normalParts==0)throw new IOException("Нет декодированных normal DDS");
        int[] stacked=new int[width*height];
        for(int y=0;y<halfHeight;y++){
            int sy=(int)((long)y*height/halfHeight);
            for(int x=0;x<width;x++)
                stacked[y*width+x]=diffuse.argb[sy*width+x];
        }
        Arrays.fill(stacked,halfHeight*width,stacked.length,NEUTRAL_NORMAL);
        int cellWidth=width/grid,cellHeight=halfHeight/grid;
        for(int slot=1;slot<slotNormals.length;slot++){
            DdsBcDecoder.Image normal=slotNormals[slot];
            if(normal==null)continue;
            int x0=(slot%grid)*cellWidth;
            int y0=halfHeight+(slot/grid)*cellHeight;
            for(int y=0;y<cellHeight;y++){
                int sy=(int)((long)y*normal.height/cellHeight);
                for(int x=0;x<cellWidth;x++){
                    int sx=(int)((long)x*normal.width/cellWidth);
                    stacked[(y0+y)*width+x0+x]=normal.argb[sy*normal.width+sx];
                }
            }
        }
        return new Result(stacked,width,height,grid,normalParts,neutralParts,
            unique.size());
    }
}
