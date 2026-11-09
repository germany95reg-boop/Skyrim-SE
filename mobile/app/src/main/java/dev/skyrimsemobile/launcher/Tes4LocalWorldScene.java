package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A bounded local Skyrim REFR scene. Every model is decoded from an actual NIF.
 * Preserve the relative scale and XYZ of nearby objects by normalizing only
 * after all valid instances' world-space vertices have been transformed.
 * This is not terrain streaming, an open world, or a playable Skyrim runtime.
 */
public final class Tes4LocalWorldScene {
    public static final int MAX_INSTANCES=8;
    public static final double MAX_DISTANCE=8192.0; // Skyrim world units, not metres
    private Tes4LocalWorldScene(){}

    private static final class Instance {
        final NifGeometryDecoder.Parts parts;
        final Tes4PlacedNifScene.Link link;
        Instance(NifGeometryDecoder.Parts parts,Tes4PlacedNifScene.Link link){
            this.parts=parts;this.link=link;
        }
    }
    /**
     * v2.42: choose the densest genuinely observed local REFR group instead of
     * assuming that the first BSA/ESM match is the best scene centre.
     * This ranks only decoded, validated meshes. No fictional scene content.
     */
    public static final class Planner {
        public static final int MAX_CANDIDATES=32;
        private final List<Instance> candidates=new ArrayList<>();
        private final Set<Long> seen=new HashSet<>();
        private int duplicates,overflow;

        public void offer(NifGeometryDecoder.Parts parts,
                          Tes4PlacedNifScene.Link link)throws IOException{
            if(parts==null||parts.items==null||link==null||link.pose==null)return;
            if(seen.contains(link.pose.recordFormId)){duplicates++;return;}
            if(candidates.size()>=MAX_CANDIDATES){overflow++;return;}
            // Validate before recording a candidate. A corrupt NIF cannot win
            // merely because many REFR point at it.
            Builder check=new Builder();
            if(!check.add(parts,link))return;
            seen.add(link.pose.recordFormId);
            candidates.add(new Instance(parts,link));
        }

        public Builder best()throws IOException{
            Builder winner=new Builder();
            int bestCount=0;
            double bestDistance=Double.POSITIVE_INFINITY;
            long bestId=Long.MAX_VALUE;
            for(Instance anchor:candidates){
                Builder proposal=new Builder();
                if(!proposal.add(anchor.parts,anchor.link))continue;
                List<Instance> neighbours=new ArrayList<>(candidates);
                neighbours.remove(anchor);
                neighbours.sort((left,right)->{
                    int cmp=Double.compare(distanceSquared(left.link.pose,anchor.link.pose),
                                           distanceSquared(right.link.pose,anchor.link.pose));
                    return cmp!=0?cmp:Long.compare(left.link.pose.recordFormId,
                                                    right.link.pose.recordFormId);
                });
                double squaredDistance=0.0;
                for(Instance neighbour:neighbours){
                    if(proposal.add(neighbour.parts,neighbour.link))
                        squaredDistance+=distanceSquared(neighbour.link.pose,anchor.link.pose);
                }
                int count=proposal.instances();
                long id=anchor.link.pose.recordFormId;
                if(count>bestCount ||
                   (count==bestCount && (squaredDistance<bestDistance ||
                    (squaredDistance==bestDistance && id<bestId)))){
                    winner=proposal;
                    bestCount=count;
                    bestDistance=squaredDistance;
                    bestId=id;
                }
            }
            return winner;
        }

        public String summary(){
            return candidates.size()+" реальных пригодных REFR проверено, "+
                "повторов "+duplicates+", сверх лимита кандидатов "+overflow+
                "; выбран наиболее плотный доступный локальный фрагмент";
        }
    }

    private static double distanceSquared(Tes4PlacementSubrecordReader.Placement a,
                                          Tes4PlacementSubrecordReader.Placement b){
        double dx=(double)a.x-b.x,dy=(double)a.y-b.y,dz=(double)a.z-b.z;
        return dx*dx+dy*dy+dz*dz;
    }

    public static final class Builder {
        private final List<Instance> selected=new ArrayList<>();
        private final Set<Long> selectedFormIds=new HashSet<>();
        private int edges,faces,vertices,shapes,unsupported;
        private int far,duplicates,budget;
        private double anchorX,anchorY,anchorZ;
        public int instances(){return selected.size();}
        public int distantCount(){return far;}
        public int overLimitCount(){return budget;}

        public boolean add(NifGeometryDecoder.Parts parts,
                           Tes4PlacedNifScene.Link link)throws IOException{
            if(parts==null||parts.items==null||link==null||link.pose==null)
                return false;
            if(selectedFormIds.contains(link.pose.recordFormId)){
                duplicates++;return false;
            }
            double x=link.pose.x,y=link.pose.y,z=link.pose.z;
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))
                throw new IOException("Некорректные координаты REFR");
            if(!selected.isEmpty()){
                double dx=x-anchorX,dy=y-anchorY,dz=z-anchorZ;
                if(dx*dx+dy*dy+dz*dz>MAX_DISTANCE*MAX_DISTANCE){
                    far++;return false;
                }
            }
            int ne=0,nf=0,nv=0,ns=0,unavailable=0;
            for(NifGeometryDecoder.Part part:parts.items){
                if(part.mesh==null){unavailable++;continue;}
                NifGeometryDecoder.Mesh m=part.mesh;
                if(m.lines==null||m.lines.edges<1||m.triangles<1||
                   m.lines.segments.length!=(long)m.lines.edges*6||
                   m.surfaceTriangles.length!=(long)m.triangles*9)
                    throw new IOException("Повреждённая геометрия BSTriShape #"+part.blockIndex);
                ne+=m.lines.edges;nf+=m.triangles;nv+=m.lines.vertices;ns++;
                if(ne>NifSceneComposer.MAX_EDGES||nf>NifSceneComposer.MAX_TRIANGLES)
                    throw new IOException("Одна модель превышает лимит Vulkan");
            }
            if(ns==0)return false;
            if(selected.size()>=MAX_INSTANCES||
                (long)edges+ne>NifSceneComposer.MAX_EDGES||
                (long)faces+nf>NifSceneComposer.MAX_TRIANGLES){
                budget++;return false;
            }
            if(selected.isEmpty()){
                anchorX=x;anchorY=y;anchorZ=z;
            }
            selected.add(new Instance(parts,link));
            selectedFormIds.add(link.pose.recordFormId);
            edges+=ne;faces+=nf;vertices+=nv;shapes+=ns;unsupported+=unavailable;
            return true;
        }

        public Tes4PlacedNifScene.Preview build()throws IOException {
            if(selected.isEmpty())throw new IOException("Нет подходящих соседних REFR/NIF");
            double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
            double[] hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for(Instance i:selected){
                for(NifGeometryDecoder.Part part:i.parts.items){
                    if(part.mesh==null)continue;
                    float[] wire=part.mesh.lines.segments;
                    for(int n=0;n<wire.length;n+=3){
                        double[] p=Tes4PlacedNifScene.worldVertex(part.mesh,wire,n,
                            i.parts.parentTransforms.get(part.blockIndex),i.link.pose);
                        for(int k=0;k<3;k++){
                            lo[k]=Math.min(lo[k],p[k]);hi[k]=Math.max(hi[k],p[k]);
                        }
                    }
                }
            }
            double span=Math.max(hi[0]-lo[0],Math.max(hi[1]-lo[1],hi[2]-lo[2]));
            if(!Double.isFinite(span)||span<1.e-8)
                throw new IOException("Вырожденная геометрия локальной сцены");
            float[] lines=new float[edges*6],triangles=new float[faces*9];
            int lp=0,tp=0;
            for(Instance i:selected){
                for(NifGeometryDecoder.Part part:i.parts.items){
                    if(part.mesh==null)continue;
                    NifHierarchyTransforms.Transform parent=
                        i.parts.parentTransforms.get(part.blockIndex);
                    float[] wire=part.mesh.lines.segments;
                    for(int n=0;n<wire.length;n+=3){
                        double[] p=Tes4PlacedNifScene.worldVertex(part.mesh,wire,n,
                            parent,i.link.pose);
                        for(int k=0;k<3;k++)lines[lp++]=normal(p[k],lo[k],span);
                    }
                    float[] surface=part.mesh.surfaceTriangles;
                    for(int n=0;n<surface.length;n+=3){
                        double[] p=Tes4PlacedNifScene.worldVertex(part.mesh,surface,n,
                            parent,i.link.pose);
                        for(int k=0;k<3;k++)triangles[tp++]=normal(p[k],lo[k],span);
                    }
                }
            }
            StringBuilder details=new StringBuilder(String.format(Locale.ROOT,
                "Локальная сцена Skyrim.esm: %d REFR, %d частей, %d треугольников\n"+
                "Центр выборки XYZ [%.2f, %.2f, %.2f]; радиус отбора %.0f единиц Skyrim\n"+
                "Дальних размещений пропущено: %d · лимиты: %d · повторов: %d\n",
                selected.size(),shapes,faces,anchorX,anchorY,anchorZ,
                MAX_DISTANCE,far,budget,duplicates));
            for(Instance i:selected){
                details.append(String.format(Locale.ROOT,
                    "• REFR 0x%08X · %s · xyz [%.2f, %.2f, %.2f] · XSCL %.3f\n",
                    i.link.pose.recordFormId,i.link.base.editorId,
                    i.link.pose.x,i.link.pose.y,i.link.pose.z,i.link.pose.scale));
            }
            details.append("Сохранены относительные позиции и размеры объектов. ")
                .append("Одна общая нормализация выполнена для Vulkan.\n")
                .append("Euler Rz*Ry*Rx предварительный; текстуры, ")
                .append("ландшафт, NPC и игровой движок не загружены.");
            return new Tes4PlacedNifScene.Preview(
                new ObjWireParser.Model(lines,vertices,edges),
                triangles,shapes,unsupported,faces,
                "Skyrim · локальная 3D-сцена ("+selected.size()+" REFR)",
                details.toString());
        }
        public String summary(){
            return "Локальная сцена: "+selected.size()+" размещений REFR · "+
                shapes+" геометрических частей · "+faces+" треугольников · "+
                far+" дальних объектов не включено · "+budget+" сверх лимита.";
        }
    }
    private static float normal(double value,double min,double span)throws IOException{
        double n=(value-min)/span*2.0-1.0;
        if(!Double.isFinite(n)||Math.abs(n)>1.0001)
            throw new IOException("Неверное значение при общей нормализации сцены");
        return (float)n;
    }
}
