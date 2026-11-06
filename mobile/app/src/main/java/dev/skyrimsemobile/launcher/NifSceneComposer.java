package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * v2.7 diagnostic composite: join separately decoded static BSTriShape geometry
 * in one common coordinate system. This is geometry-only; the per-part local
 * NiAVObject transform was already applied by NifGeometryDecoder. Parent
 * NiNode/BSFadeNode transforms and per-shape materials are NOT interpreted.
 */
public final class NifSceneComposer {
    private NifSceneComposer(){}
    /** Native Vulkan accepts at most 30,000 distinct wire edges and 30,000 faces. */
    public static final int MAX_EDGES=30000,MAX_TRIANGLES=30000;

    public static final class Combined {
        public final ObjWireParser.Model lines;
        public final float[] triangles;
        public final int included,skipped,triangleCount;
        public final String info;
        private Combined(ObjWireParser.Model model,float[] triangles,int included,
                         int skipped,int faces,String info){
            this.lines=model;this.triangles=triangles;this.included=included;
            this.skipped=skipped;triangleCount=faces;this.info=info;
        }
    }
    /** Merge all valid parts. Invalid blocks are explicitly counted, not hidden.
     * Never substitute the individual shape's normalization for the global one.
     */
    public static Combined combine(NifGeometryDecoder.Parts parts)throws IOException{
        if(parts==null||parts.items==null||parts.items.isEmpty())
            throw new IOException("Нет частей NIF для объединения");
        List<NifGeometryDecoder.Mesh> meshes=new ArrayList<>();
        int edges=0,triangles=0,vertices=0,skipped=0;
        StringBuilder ids=new StringBuilder();
        for(NifGeometryDecoder.Part part:parts.items){
            if(part.mesh==null){skipped++;continue;}
            NifGeometryDecoder.Mesh mesh=part.mesh;
            int e=mesh.lines.edges,t=mesh.triangles;
            if(e<=0||t<=0||mesh.lines.segments.length!=(long)e*6||
               mesh.surfaceTriangles.length!=(long)t*9)
                throw new IOException("Некорректный размер геометрии части #"+part.blockIndex);
            if((long)edges+e>MAX_EDGES||(long)triangles+t>MAX_TRIANGLES)
                throw new IOException("Составная модель превышает предел Vulkan: "+
                    MAX_EDGES+" рёбер / "+MAX_TRIANGLES+" треугольников");
            edges+=e;triangles+=t;vertices+=mesh.lines.vertices;
            meshes.add(mesh);
            if(ids.length()>0)ids.append(", ");
            ids.append('#').append(part.blockIndex);
        }
        if(meshes.isEmpty())throw new IOException("Все части NIF неподдерживаемые");

        double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
        double[] hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(NifGeometryDecoder.Mesh m:meshes){
            // Every face uses existing validated wire endpoints; preserve exact
            // relative placement using original shape-local bounding box.
            for(int i=0;i<m.lines.segments.length;i+=3)for(int k=0;k<3;k++){
                double p=world(m,m.lines.segments[i+k],k);
                if(!Double.isFinite(p))throw new IOException("Некорректная координата при объединении");
                lo[k]=Math.min(lo[k],p);
                hi[k]=Math.max(hi[k],p);
            }
        }
        double span=Math.max(hi[0]-lo[0],Math.max(hi[1]-lo[1],hi[2]-lo[2]));
        if(!Double.isFinite(span)||span<1.0e-9)
            throw new IOException("Вырожденные границы объединённой геометрии");

        float[] combinedLines=new float[edges*6];
        float[] combinedTriangles=new float[triangles*9];
        int lp=0,tp=0;
        for(NifGeometryDecoder.Mesh m:meshes){
            for(int i=0;i<m.lines.segments.length;i++)
                combinedLines[lp++]=normal(world(m,m.lines.segments[i],i%3),
                                           lo[i%3],span);
            for(int i=0;i<m.surfaceTriangles.length;i++)
                combinedTriangles[tp++]=normal(world(m,m.surfaceTriangles[i],i%3),
                                               lo[i%3],span);
        }
        String label="Частей: "+meshes.size()+" · пропущено: "+skipped+
            " · треугольников: "+triangles+" · рёбер: "+edges+
            "\nБлоки: "+ids+
            "\nОдин общий масштаб с сохранением взаимного расположения "+
            "локально преобразованных геометрических частей."+
            "\nЭто диагностический просмотр БЕЗ DDS. Родительские "+
            "NiNode/BSFadeNode-преобразования не учитываются; "+
            "расположение в сложной сцене может отличаться от Skyrim."+
            "\nИгровой BSA не изменён.";
        return new Combined(new ObjWireParser.Model(combinedLines,vertices,edges),
                            combinedTriangles,meshes.size(),skipped,triangles,label);
    }
    private static double world(NifGeometryDecoder.Mesh m,float normalized,int axis){
        return m.localMin[axis]+
            ((double)normalized*0.5+0.5)*m.localSpan;
    }
    private static float normal(double original,double minimum,double span)throws IOException{
        double n=(original-minimum)/span*2.0-1.0;
        if(!Double.isFinite(n)||Math.abs(n)>1.00001)
            throw new IOException("Неверное нормализованное значение составной модели");
        return (float)n;
    }
}
