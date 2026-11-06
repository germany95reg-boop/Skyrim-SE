package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only scene graph links for Skyrim SE NIF 20.2.0.7 / stream 100.
 *
 * NiNode/BSFadeNode are NiAVObject + uint child count + Ref[] children.
 * Source: NifTools nif.xml (NiObjectNET -> NiAVObject -> NiNode).
 * The already decoded BSTriShape local transform is NOT reapplied here.
 * A node's transform affects all descendants; traversal starts at NIF roots.
 * Malformed links, cycles and ambiguous parents fail closed: only legacy
 * shape-local geometry is used, and the problem is included in the report.
 */
final class NifHierarchyTransforms {
    private static final int MAX_DEPTH=32;
    private static final int MAX_CHILDREN=1024;
    private static final int MAX_EDGES=8192;

    static final class Transform {
        final double[] m; // 3x3 row-major, includes uniform scale
        final double[] t; // xyz translation

        private Transform(double[] matrix,double[] xyz) {
            m=matrix.clone();t=xyz.clone();
        }
        static Transform identity(){
            return new Transform(new double[]{1,0,0,0,1,0,0,0,1},
                                 new double[]{0,0,0});
        }
        double[] apply(double x,double y,double z)throws IOException{
            double[] p={
                t[0]+m[0]*x+m[1]*y+m[2]*z,
                t[1]+m[3]*x+m[4]*y+m[5]*z,
                t[2]+m[6]*x+m[7]*y+m[8]*z
            };
            for(double v:p)if(!Double.isFinite(v)||Math.abs(v)>1.e12)
                throw new IOException("Переполнение мировой координаты NIF");
            return p;
        }
        Transform after(Transform child)throws IOException{
            // parent * child: world = parent.t + parent.RS * (child.t + child.RS*x)
            double[] matrix=new double[9];
            for(int row=0;row<3;row++)
                for(int col=0;col<3;col++)
                    for(int k=0;k<3;k++)
                        matrix[row*3+col]+=m[row*3+k]*child.m[k*3+col];
            double[] translation=apply(child.t[0],child.t[1],child.t[2]);
            for(double v:matrix)if(!Double.isFinite(v)||Math.abs(v)>1.e10)
                throw new IOException("Переполнение матрицы NiNode");
            return new Transform(matrix,translation);
        }
    }
    static final class Result {
        final Map<Integer,Transform> parentWorld;
        final int nodeCount,connectedShapes,unlinkedShapes;
        final String status;
        Result(Map<Integer,Transform> transforms,int count,int linked,int unlinked,String status){
            parentWorld=Collections.unmodifiableMap(new HashMap<>(transforms));
            nodeCount=count;connectedShapes=linked;unlinkedShapes=unlinked;
            this.status=status;
        }
    }

    private static final class Node {
        final int blockIndex;
        final Transform transform;
        final int[] children;
        Node(int index,Transform transform,int[] children){
            blockIndex=index;this.transform=transform;this.children=children;
        }
    }

    private static boolean group(String type){
        return "NiNode".equals(type)||"BSFadeNode".equals(type);
    }
    private static int u32(byte[] bytes,int offset,int end)throws IOException{
        if(offset<0 || offset>end-4)throw new IOException("Обрезан uint NiNode");
        return (bytes[offset]&255)|((bytes[offset+1]&255)<<8)|
            ((bytes[offset+2]&255)<<16)|((bytes[offset+3]&255)<<24);
    }
    private static float f32(byte[] bytes,int at,int end)throws IOException{
        float v=Float.intBitsToFloat(u32(bytes,at,end));
        if(!Float.isFinite(v))throw new IOException("NiNode содержит NaN/Infinity");
        return v;
    }

    private static Node parse(byte[] source,NifBlockInspector.Block b,int blockCount)throws IOException{
        int start=b.byteOffset;
        long endLong=(long)start+b.byteLength;
        if(start<0||endLong>source.length||endLong>Integer.MAX_VALUE)
            throw new IOException("NiNode выходит за границы NIF");
        int end=(int)endLong;
        if(b.byteLength<80)throw new IOException("Обрезан NiNode/BSFadeNode #"+b.index);
        long extras=Integer.toUnsignedLong(u32(source,start+4,end));
        if(extras>128)throw new IOException("Слишком много extra-data NiNode #"+b.index);
        int more=(int)extras*4;
        int countOffset=start+72+more;
        if((long)countOffset+4>end)throw new IOException("Обрезан список детей NiNode #"+b.index);
        int count=u32(source,countOffset,end);
        if(count<0||count>MAX_CHILDREN)
            throw new IOException("Недопустимое число детей NiNode #"+b.index+": "+count);
        if((long)countOffset+4+(long)count*4>end)
            throw new IOException("Обрезаны ссылки NiNode #"+b.index);
        double[] t=new double[3],m=new double[9];
        int local=start+16+more;
        for(int k=0;k<3;k++){
            t[k]=f32(source,local+4*k,end);
            if(Math.abs(t[k])>1e8)throw new IOException("Большое смещение NiNode #"+b.index);
        }
        double scale=f32(source,local+48,end);
        if(Math.abs(scale)<1e-7||Math.abs(scale)>1e6)
            throw new IOException("Масштаб NiNode вне безопасного диапазона #"+b.index);
        for(int k=0;k<9;k++){
            m[k]=scale*f32(source,local+12+k*4,end);
            if(Math.abs(m[k])>1e8)throw new IOException("Матрица NiNode вне диапазона #"+b.index);
        }
        int[] children=new int[count];
        Set<Integer> unique=new HashSet<>();
        for(int k=0;k<count;k++){
            int target=u32(source,countOffset+4+k*4,end);
            if(target < -1 || target>=blockCount)
                throw new IOException("Некорректная ссылка NiNode #"+b.index+
                    " на блок #"+target);
            if(target==b.index)
                throw new IOException("NiNode ссылается на себя: #"+b.index);
            if(target>=0&&!unique.add(target))
                throw new IOException("Повторён ребёнок #"+target+" в NiNode #"+b.index);
            children[k]=target;
        }
        return new Node(b.index,new Transform(m,t),children);
    }

    static Result read(byte[] bytes,NifBlockInspector.Result nif)throws IOException{
        if(bytes==null||nif==null || nif.blocks==null)
            throw new IOException("Нет таблицы NIF для иерархии");
        Map<Integer,Node> nodes=new HashMap<>();
        int shapes=0;
        for(NifBlockInspector.Block b:nif.blocks){
            if(group(b.type))nodes.put(b.index,parse(bytes,b,nif.blockCount));
            if("BSTriShape".equals(b.type))shapes++;
        }
        if(nodes.isEmpty())return new Result(Collections.emptyMap(),0,0,shapes,
            "Нет NiNode/BSFadeNode; применены только локальные BSTriShape");
        Map<Integer,Transform> descendants=new HashMap<>();
        Set<Integer> visiting=new HashSet<>();
        int[] edges={0};
        for(int root:nif.rootIndices) {
            if(root<0||root>=nif.blockCount)
                throw new IOException("Некорректная ссылка корневого NiNode");
            walk(root,Transform.identity(),nodes,nif,visiting,descendants,edges,0);
        }
        int connected=0;
        Map<Integer,Transform> parents=new HashMap<>();
        for(NifBlockInspector.Block b:nif.blocks){
            if(!"BSTriShape".equals(b.type))continue;
            Transform p=descendants.get(b.index);
            if(p!=null){
                connected++;
                parents.put(b.index,p);
            }
        }
        int unlinked=shapes-connected;
        return new Result(parents,nodes.size(),connected,unlinked,
            "NiNode/BSFadeNode: "+nodes.size()+" узлов · "+
            connected+" связанных BSTriShape · "+unlinked+" вне дерева");
    }

    private static void walk(int block,Transform world,Map<Integer,Node> nodes,
                             NifBlockInspector.Result nif,Set<Integer> visiting,
                             Map<Integer,Transform> outputs,int[] edges,int depth)
                             throws IOException{
        if(depth>MAX_DEPTH)throw new IOException("Иерархия NiNode глубже "+MAX_DEPTH);
        if(!visiting.add(block))
            throw new IOException("Цикл обнаружен в NIF: блок #"+block);
        if(outputs.containsKey(block))
            throw new IOException("У блока #"+block+" несколько родителей в NIF");
        Node node=nodes.get(block);
        if(node!=null){
            Transform combined=world.after(node.transform);
            outputs.put(block,combined);
            for(int child:node.children){
                if(child<0)continue; // NiNode allows -1 null references.
                if(++edges[0]>MAX_EDGES)
                    throw new IOException("В NIF слишком много связей NiNode");
                walk(child,combined,nodes,nif,visiting,outputs,edges,depth+1);
            }
        } else {
            outputs.put(block,world);
        }
        visiting.remove(block);
    }
}
