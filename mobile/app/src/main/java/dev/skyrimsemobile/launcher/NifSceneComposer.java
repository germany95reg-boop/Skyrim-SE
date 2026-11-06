package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only composite static SSE NIF scene: reconstruct shape-local geometry,
 * apply the validated NiNode/BSFadeNode ancestor chain, and normalize ONCE.
 * Vertex ordering stays unchanged for per-BSTriShape diffuse DDS atlas slots.
 */
public final class NifSceneComposer {
    private NifSceneComposer(){}
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

    public static Combined combine(NifGeometryDecoder.Parts parts)throws IOException{
        if(parts==null||parts.items==null||parts.items.isEmpty())
            throw new IOException("Нет частей NIF для объединения");
        List<NifGeometryDecoder.Part> validParts=new ArrayList<>();
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
            validParts.add(part);
            if(ids.length()>0)ids.append(", ");
            ids.append('#').append(part.blockIndex);
        }
        if(validParts.isEmpty())throw new IOException("Все части NIF неподдерживаемые");

        double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
        double[] hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(NifGeometryDecoder.Part part:validParts){
            NifGeometryDecoder.Mesh m=part.mesh;
            NifHierarchyTransforms.Transform parent=parts.parentTransforms.get(part.blockIndex);
            // Bounds from actual used wire endpoints, not an approximate transformed AABB.
            for(int i=0;i<m.lines.segments.length;i+=3){
                double[] p=world(m,m.lines.segments,i,parent);
                for(int k=0;k<3;k++){
                    lo[k]=Math.min(lo[k],p[k]);
                    hi[k]=Math.max(hi[k],p[k]);
                }
            }
        }
        double span=Math.max(hi[0]-lo[0],Math.max(hi[1]-lo[1],hi[2]-lo[2]));
        if(!Double.isFinite(span)||span<1.0e-9)
            throw new IOException("Вырожденные границы объединённой геометрии");

        float[] combinedLines=new float[edges*6];
        float[] combinedTriangles=new float[triangles*9];
        int lp=0,tp=0;
        for(NifGeometryDecoder.Part part:validParts){
            NifGeometryDecoder.Mesh m=part.mesh;
            NifHierarchyTransforms.Transform parent=parts.parentTransforms.get(part.blockIndex);
            for(int i=0;i<m.lines.segments.length;i+=3){
                double[] p=world(m,m.lines.segments,i,parent);
                for(int k=0;k<3;k++)combinedLines[lp++]=normal(p[k],lo[k],span);
            }
            for(int i=0;i<m.surfaceTriangles.length;i+=3){
                double[] p=world(m,m.surfaceTriangles,i,parent);
                for(int k=0;k<3;k++)combinedTriangles[tp++]=normal(p[k],lo[k],span);
            }
        }
        String label="Частей: "+validParts.size()+" · пропущено: "+skipped+
            " · треугольников: "+triangles+" · рёбер: "+edges+
            "\nБлоки: "+ids+
            "\nОбщий масштаб с локальными преобразованиями BSTriShape"+
            " и проверенными NiNode/BSFadeNode родительскими преобразованиями."+
            "\n"+parts.hierarchyNote+
            "\nДиагностический просмотр БЕЗ DDS на этом экране;"+
            " анимация и скелет не интерпретируются."+
            "\nИгровой BSA не изменён.";
        return new Combined(new ObjWireParser.Model(combinedLines,vertices,edges),
                            combinedTriangles,validParts.size(),skipped,triangles,label);
    }
    private static double[] world(NifGeometryDecoder.Mesh mesh,float[] array,int offset,
                                  NifHierarchyTransforms.Transform parent)throws IOException{
        double x=mesh.localMin[0]+((double)array[offset]*0.5+0.5)*mesh.localSpan;
        double y=mesh.localMin[1]+((double)array[offset+1]*0.5+0.5)*mesh.localSpan;
        double z=mesh.localMin[2]+((double)array[offset+2]*0.5+0.5)*mesh.localSpan;
        if(parent!=null)return parent.apply(x,y,z);
        return new double[]{x,y,z};
    }
    private static float normal(double original,double minimum,double span)throws IOException{
        double n=(original-minimum)/span*2.0-1.0;
        if(!Double.isFinite(n)||Math.abs(n)>1.00001)
            throw new IOException("Неверное нормализованное значение составной модели");
        return (float)n;
    }
}
