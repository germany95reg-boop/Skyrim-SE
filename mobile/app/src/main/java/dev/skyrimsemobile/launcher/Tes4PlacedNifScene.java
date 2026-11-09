package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Links only confirmed same-file REFR->NAME->MODL to a matched NIF.
 * World markers and actors are not treated as visible static scene objects.
 */
public final class Tes4PlacedNifScene {
    private Tes4PlacedNifScene(){}

    public static final class Link {
        public final Tes4PlacementSubrecordReader.Placement pose;
        public final Tes4BaseFormResolver.Base base;
        public final String canonicalPath;
        private Link(Tes4PlacementSubrecordReader.Placement pose,
                     Tes4BaseFormResolver.Base base,String path){
            this.pose=pose;this.base=base;this.canonicalPath=path;
        }
    }
    public static Link findVisible(List<Tes4PlacementSubrecordReader.Placement> poses,
                                   Map<Long,Tes4BaseFormResolver.Base> bases,
                                   String nifPath)throws IOException{
        if(poses==null||bases==null)return null;
        String path=Tes4BsaModelMatcher.canonicalNif(nifPath);
        for(Tes4PlacementSubrecordReader.Placement pose:poses){
            if(!"REFR".equals(pose.type)||!pose.complete())continue;
            Tes4BaseFormResolver.Base base=bases.get(pose.baseFormId);
            if(base==null||base.modelPath==null||base.modelPath.isEmpty())continue;
            if(base.editorId.toLowerCase(Locale.ROOT).contains("marker")||
               base.editorId.toLowerCase(Locale.ROOT).contains("collision"))continue;
            if(!path.equals(Tes4BsaModelMatcher.canonicalNif(base.modelPath)))continue;
            return new Link(pose,base,path);
        }
        return null;
    }

    public static final class Preview {
        public final ObjWireParser.Model lines;
        public final float[] triangles;
        public final int shapes,skipped,triangleCount;
        public final String label,details;
        private Preview(ObjWireParser.Model lines,float[] triangles,int shapes,int skipped,
                        int triangleCount,String label,String details){
            this.lines=lines;this.triangles=triangles;
            this.shapes=shapes;this.skipped=skipped;this.triangleCount=triangleCount;
            this.label=label;this.details=details;
        }
    }

    /**
     * Actual REFR world XYZ and Euler radians are applied to decoded vertices
     * in double precision; the resulting object is then recentered and fitted
     * once for Vulkan preview. Its original world location stays in details.
     * R=Rz*Ry*Rx is a provisional Euler order: game parity still needs testing.
     */
    public static Preview compose(NifGeometryDecoder.Parts parts,Link link)throws IOException{
        if(parts==null||parts.items==null||link==null||link.pose==null)
            throw new IOException("Нет декодированной NIF или связанного REFR");
        int edges=0,triangles=0,vertices=0,shapes=0,skipped=0;
        for(NifGeometryDecoder.Part part:parts.items){
            if(part.mesh==null){skipped++;continue;}
            NifGeometryDecoder.Mesh mesh=part.mesh;
            if(mesh.lines==null||mesh.triangles<1||mesh.lines.edges<1||
               mesh.lines.segments.length!=(long)mesh.lines.edges*6||
               mesh.surfaceTriangles.length!=(long)mesh.triangles*9)
                throw new IOException("Неверная геометрия BSTriShape #"+part.blockIndex);
            if((long)edges+mesh.lines.edges>NifSceneComposer.MAX_EDGES||
               (long)triangles+mesh.triangles>NifSceneComposer.MAX_TRIANGLES)
                throw new IOException("Превышен предел геометрии Vulkan");
            edges+=mesh.lines.edges;
            triangles+=mesh.triangles;
            vertices+=mesh.lines.vertices;
            shapes++;
        }
        if(shapes==0)throw new IOException("Нет поддерживаемых BSTriShape для просмотра");
        double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
        double[] hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(NifGeometryDecoder.Part part:parts.items){
            if(part.mesh==null)continue;
            for(int i=0;i<part.mesh.lines.segments.length;i+=3){
                double[] p=worldVertex(part.mesh,part.mesh.lines.segments,i,
                    parts.parentTransforms.get(part.blockIndex),link.pose);
                for(int k=0;k<3;k++){lo[k]=Math.min(lo[k],p[k]);hi[k]=Math.max(hi[k],p[k]);}
            }
        }
        double span=Math.max(hi[0]-lo[0],Math.max(hi[1]-lo[1],hi[2]-lo[2]));
        if(!Double.isFinite(span)||span<1e-8)
            throw new IOException("Вырожденные границы размещённого NIF");
        float[] worldLines=new float[edges*6];
        float[] worldTriangles=new float[triangles*9];
        int l=0,t=0;
        for(NifGeometryDecoder.Part part:parts.items){
            if(part.mesh==null)continue;
            NifHierarchyTransforms.Transform parent=parts.parentTransforms.get(part.blockIndex);
            float[] originalLines=part.mesh.lines.segments;
            for(int i=0;i<originalLines.length;i+=3){
                double[] xyz=worldVertex(part.mesh,originalLines,i,parent,link.pose);
                for(int k=0;k<3;k++)worldLines[l++]=normalize(xyz[k],lo[k],span);
            }
            float[] originalTriangles=part.mesh.surfaceTriangles;
            for(int i=0;i<originalTriangles.length;i+=3){
                double[] xyz=worldVertex(part.mesh,originalTriangles,i,parent,link.pose);
                for(int k=0;k<3;k++)worldTriangles[t++]=normalize(xyz[k],lo[k],span);
            }
        }
        String label="Skyrim REFR · "+link.base.editorId;
        String details=String.format(Locale.ROOT,
            "REFR 0x%08X → NAME 0x%08X · %s\n"+
            "Мировые координаты XYZ: [%.2f, %.2f, %.2f]\n"+
            "Поворот XYZ: [%.3f, %.3f, %.3f] рад · масштаб XSCL: %.3f\n"+
            "%d статических частей · %d треугольников · %d пропущено\n"+
            "Один объект помещён по данным Skyrim.esm и подогнан под окно просмотра.\n"+
            "Порядок Euler Rz*Ry*Rx предварительный; нет DDS, ландшафта, NPC и игрового движка.",
            link.pose.recordFormId,link.pose.baseFormId,link.canonicalPath,
            link.pose.x,link.pose.y,link.pose.z,
            link.pose.rx,link.pose.ry,link.pose.rz,link.pose.scale,
            shapes,triangles,skipped);
        return new Preview(new ObjWireParser.Model(worldLines,vertices,edges),
                           worldTriangles,shapes,skipped,triangles,label,details);
    }

    static double[] applyPose(double x,double y,double z,
                              Tes4PlacementSubrecordReader.Placement p)throws IOException{
        double scale=p.scale;
        if(!Double.isFinite(scale)||scale<=0.0||scale>100.0)
            throw new IOException("Неверный масштаб XSCL");
        x*=scale;y*=scale;z*=scale;
        double cx=Math.cos(p.rx),sx=Math.sin(p.rx);
        double cy=Math.cos(p.ry),sy=Math.sin(p.ry);
        double cz=Math.cos(p.rz),sz=Math.sin(p.rz);
        double ay=cx*y-sx*z,az=sx*y+cx*z;
        double bx=cy*x+sy*az,bz=-sy*x+cy*az;
        double wx=p.x+cz*bx-sz*ay;
        double wy=p.y+sz*bx+cz*ay;
        double wz=p.z+bz;
        if(!Double.isFinite(wx)||!Double.isFinite(wy)||!Double.isFinite(wz)||
           Math.abs(wx)>1e12||Math.abs(wy)>1e12||Math.abs(wz)>1e12)
            throw new IOException("Переполнение мировых координат");
        return new double[]{wx,wy,wz};
    }
    private static double[] worldVertex(NifGeometryDecoder.Mesh mesh,float[] array,int offset,
                                        NifHierarchyTransforms.Transform parent,
                                        Tes4PlacementSubrecordReader.Placement p)throws IOException{
        double x=mesh.localMin[0]+((double)array[offset]*0.5+0.5)*mesh.localSpan;
        double y=mesh.localMin[1]+((double)array[offset+1]*0.5+0.5)*mesh.localSpan;
        double z=mesh.localMin[2]+((double)array[offset+2]*0.5+0.5)*mesh.localSpan;
        double[] local=parent==null?new double[]{x,y,z}:parent.apply(x,y,z);
        return applyPose(local[0],local[1],local[2],p);
    }
    private static float normalize(double v,double min,double span)throws IOException{
        double n=(v-min)/span*2.0-1.0;
        if(!Double.isFinite(n)||Math.abs(n)>1.0001)
            throw new IOException("Неправильная координата после размещения");
        return (float)n;
    }
}
