package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * Bounded Skyrim Special Edition BSTriShape (NIF 20.2.0.7, Bethesda stream 100)
 * static, inline geometry -> normalized, untextured line segments for the Vulkan demo.
 *
 * Layout reference: niftools nif.xml BSTriShape/BSVertexDesc/BSVertexDataSSE.
 * This is NOT a general NIF reader: no skin partitions, textures, parent transforms,
 * collision, animation or particle vertices are interpreted.
 */
public final class NifGeometryDecoder {
    private NifGeometryDecoder() {}
    public static final int MAX_VERTICES = 40000;
    public static final int MAX_EDGES = 30000;
    public static final int MAX_TRIANGLES = 30000;
    private static final int SHAPE_HEADER_BYTES = 116;
    private static final int MAX_VERTEX_STRIDE = 60;
    /** No more than 24 individually decodable parts and 60k total triangles. */
    public static final int MAX_PARTS=24,MAX_PART_TRIANGLES=60000;

    /** A single selectable static BSTriShape, or a reason it cannot be previewed. */
    public static final class Part {
        public final int blockIndex;
        public final Mesh mesh;
        public final String problem;
        Part(int index,Mesh mesh,String problem){
            blockIndex=index;this.mesh=mesh;this.problem=problem;
        }
        public String label(){
            return mesh!=null?"#"+blockIndex+" · "+mesh.lines.vertices+
                " вершин · "+mesh.triangles+" треугольников"+
                (mesh.surfaceUVs.length>0?" · UV":""):
                "#"+blockIndex+" · неподдерживаемая часть";
        }
    }
    public static final class Parts {
        public final List<Part> items;
        public final int available;
        Parts(List<Part> list,int ready){
            items=Collections.unmodifiableList(new ArrayList<>(list));
            available=ready;
        }
        public String report(){
            StringBuilder text=new StringBuilder("Части NIF v2.7 · BSTriShape (только чтение)\n");
            text.append("Всего частей: ").append(items.size())
                .append(" · доступны для Vulkan: ").append(available).append('\n');
            for(Part part:items){
                text.append("• ").append(part.label());
                if(part.problem!=null)text.append(" · ").append(part.problem);
                text.append('\n');
            }
            text.append("\nВыбирай отдельную часть для просмотра без материалов. "+
                "Объединение всех частей, связи шейдеров с DDS и "+
                "родительские трансформации пока не поддерживаются. "+
                "Файл BSA не изменён.");
            return text.toString();
        }
    }

    /** Inspect at most 24 static shapes in one bounded NIF, without touching files.
     * Broken blocks stay visible as errors, never replacing a previously valid one.
     */
    public static Parts scan(byte[] bytes)throws IOException{
        NifBlockInspector.Result nif=NifBlockInspector.inspect(bytes);
        if(nif.streamVersion!=100||nif.userVersion!=12)
            throw new IOException("Поддерживаются BSTriShape только Skyrim SE stream 100");
        List<Part> list=new ArrayList<>();
        int accepted=0,totalTriangles=0;
        for(NifBlockInspector.Block block:nif.blocks){
            if(!"BSTriShape".equals(block.type))continue;
            if(list.size()>=MAX_PARTS)
                throw new IOException("Превышен безопасный предел "+MAX_PARTS+" BSTriShape в NIF");
            try{
                Mesh m=parseShape(bytes,block);
                if((long)totalTriangles+m.triangles>MAX_PART_TRIANGLES)
                    throw new IOException("Превышен общий предел "+MAX_PART_TRIANGLES+" треугольников");
                list.add(new Part(block.index,m,null));
                totalTriangles+=m.triangles;accepted++;
            }catch(IOException e){
                String problem=e.getMessage();
                if(problem==null)problem="Некорректная геометрия";
                list.add(new Part(block.index,null,
                    problem.length()>180?problem.substring(0,180)+"…":problem));
            }
        }
        if(list.isEmpty())
            throw new IOException("В выбранном NIF нет статических BSTriShape");
        return new Parts(list,accepted);
    }

    public static final class Mesh {
        public final ObjWireParser.Model lines;
        /** xyz xyz xyz per face, normalized exactly like the existing line model. */
        public final float[] surfaceTriangles;
        /** SSE half-float UV pairs, expanded per triangle in the same vertex order. Empty when absent. */
        public final float[] surfaceUVs;
        public final int shapeIndex, triangles, stride, vertexFlags;
        public final String shapeType;
        public final float[] rawDimensions, localDimensions, translation;
        public final float localScale;
        /** Bounds of the used, locally transformed geometry BEFORE per-part normalization. */
        public final float[] localMin;
        public final float localSpan;
        Mesh(ObjWireParser.Model lines,float[] surfaceTriangles,float[] surfaceUVs,int shapeIndex,int triangles,int stride,int flags,String type,
             float[] rawDimensions,float[] localDimensions,float[] translation,float localScale,
             float[] localMin,float localSpan) {
            this.lines=lines;this.surfaceTriangles=surfaceTriangles.clone();
            this.surfaceUVs=surfaceUVs.clone();
            this.shapeIndex=shapeIndex;this.triangles=triangles;
            this.stride=stride;this.vertexFlags=flags;this.shapeType=type;
            this.rawDimensions=rawDimensions.clone();this.localDimensions=localDimensions.clone();
            this.translation=translation.clone();this.localScale=localScale;
            this.localMin=localMin.clone();this.localSpan=localSpan;
        }
        public String report() {
            return "NIF v1.4 · геометрия BSTriShape (каркас + поверхность)\n"+
                "Блок: #"+shapeIndex+" "+shapeType+"\n"+
                "Вершин: "+lines.vertices+" · треугольников: "+triangles+
                " · уникальных рёбер: "+lines.edges+"\n"+
                "UV: "+(surfaceUVs.length>0?"да, "+surfaceUVs.length/2+" развёрнутых вершин":"отсутствуют")+"\n"+
                "Шаг вершины: "+stride+" байт · атрибуты: 0x"+
                Integer.toHexString(vertexFlags)+"\n"+
                "Размер исходной модели XYZ: "+fmt3(rawDimensions)+"\n"+
                "После локального преобразования XYZ: "+fmt3(localDimensions)+"\n"+
                String.format(Locale.US,"Локальный масштаб: %.3f · смещение: %s\n",localScale,fmt3(translation))+
                "Геометрия нормализована для режимов каркаса и закрашенных треугольников. " +
                "Локальное преобразование BSTriShape применено; преобразования родительских узлов, " +
                "текстуры, шейдеры Skyrim и анимация пока не применяются. " +
                "Исходный BSA не изменён.";
        }
        public String compactInfo(){ return "NIF XYZ: "+fmt3(localDimensions)+" · лок. масштаб: "+
            String.format(Locale.US,"%.2f",localScale); }
    }
    private static String fmt3(float[] values){
        return String.format(Locale.US,"%.2f × %.2f × %.2f",values[0],values[1],values[2]);
    }

    public static Mesh decode(byte[] bytes) throws IOException {
        NifBlockInspector.Result nif=NifBlockInspector.inspect(bytes);
        if(nif.streamVersion!=100 || nif.userVersion!=12)
            throw new IOException("Геометрия поддерживается только для Skyrim SE stream 100");
        IOException rejected=null;
        for(NifBlockInspector.Block block:nif.blocks) {
            if(!block.type.equals("BSTriShape"))continue;
            try { return parseShape(bytes,block); }
            catch(IOException e) { rejected=e; }
        }
        if(rejected!=null)throw new IOException("BSTriShape обнаружен, но формат геометрии не поддерживается: "+rejected.getMessage(),rejected);
        throw new IOException("В NIF нет поддерживаемого статического BSTriShape (или используется skin partition)");
    }

    private static Mesh parseShape(byte[] b, NifBlockInspector.Block block)throws IOException {
        int p=block.byteOffset,end=p+block.byteLength;
        if(block.byteLength<SHAPE_HEADER_BYTES || end< p || end> b.length)
            throw new IOException("Слишком короткий блок BSTriShape");
        // NiObjectNET contains a variable-length extra-data reference list.
        long extraCount=u32(b,p+4,end);
        if(extraCount>128)throw new IOException("Слишком много дополнительных ссылок NiObjectNET");
        int extraBytes=(int)extraCount*4;
        int headerBytes=SHAPE_HEADER_BYTES+extraBytes;
        if(headerBytes>block.byteLength)throw new IOException("Обрыв заголовка BSTriShape");
        // NiAVObject (flags/transform/collision), NiBound, skin/shader/alpha refs.
        int descOffset=p+100+extraBytes;
        long desc=u64(b,descOffset,end);
        int stride=(int)(desc&15L)*4;
        // BSVertexDesc low nibble: vertex stride. Next nibble: dynamic vertex size.
        // UV1 offset is the THIRD nibble (bits 8..11), in four-byte words.
        // v2.1 mistakenly read the dynamic-size nibble and rejected vanilla SSE NIF.
        int uvOffset=(int)((desc>>>8)&15L)*4;
        int attributes=(int)((desc>>>44)&0xfff);
        boolean hasUv=(attributes&2)!=0;
        int triangles=u16(b,descOffset+8,end);
        int vertices=u16(b,descOffset+10,end);
        long declaredSize=u32(b,descOffset+12,end);
        if(vertices<3 || vertices>MAX_VERTICES || triangles<1 || triangles>MAX_TRIANGLES)
            throw new IOException("Неверное число вершин/треугольников: "+vertices+"/"+triangles);
        if((attributes&1)==0 || stride<16 || stride>MAX_VERTEX_STRIDE)
            throw new IOException("Нет float32-координат или неверный шаг BSVertexDesc");
        if(hasUv&&(uvOffset<16||uvOffset+4>stride))
            throw new IOException("SSE UV: неверное смещение BSVertexDesc"+
                " (desc=0x"+Long.toHexString(desc)+", stride="+stride+
                ", uvOffset="+uvOffset+")");
        long expected=(long)vertices*stride+(long)triangles*6;
        if(declaredSize != expected)
            throw new IOException("Data Size не совпадает с форматом вершин/граней: "+declaredSize+" / "+expected);
        int dataStart=p+headerBytes;
        long dataEnd=(long)dataStart+declaredSize;
        if(dataEnd>end)
            throw new IOException("Данные BSTriShape выходят за границы блока");
        // NiAVObject local transform comes BEFORE the bounding sphere and vertex descriptor:
        // Name, extra-data refs, controller, flags, Translation, Rotation (row-major), Scale.
        int transformOffset=p+16+extraBytes;
        float[] translation=new float[3],rotation=new float[9];
        for(int k=0;k<3;k++)translation[k]=finiteFloat(b,transformOffset+4*k,end,"смещение",k);
        for(int k=0;k<9;k++)rotation[k]=finiteFloat(b,transformOffset+12+4*k,end,"матрица поворота",k);
        float localScale=finiteFloat(b,transformOffset+48,end,"локальный масштаб",0);
        if(Math.abs(localScale)<1e-7f || Math.abs(localScale)>1e6f)
            throw new IOException("Масштаб BSTriShape вне безопасного диапазона");
        float[] positions=new float[vertices*3];
        float[] uvs=hasUv?new float[vertices*2]:new float[0];
        float[] rawLow={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
        float[] rawHigh={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(int v=0;v<vertices;v++) {
            int address=dataStart+v*stride;
            float[] xyz=new float[3];
            for(int k=0;k<3;k++) {
                float f=finiteFloat(b,address+4*k,end,"вершина",v);
                if(Math.abs(f)>1e9f)throw new IOException("Слишком большая координата вершины #"+v);
                rawLow[k]=Math.min(rawLow[k],f);rawHigh[k]=Math.max(rawHigh[k],f);
                xyz[k]=f;
            }
            if(hasUv){
                float u=halfFloat(u16(b,address+uvOffset,end));
                float vCoord=halfFloat(u16(b,address+uvOffset+2,end));
                if(!Float.isFinite(u)||!Float.isFinite(vCoord)||
                   Math.abs(u)>8192f||Math.abs(vCoord)>8192f)
                    throw new IOException("SSE UV: недопустимые half-float координаты вершины #"+v);
                uvs[2*v]=u;
                uvs[2*v+1]=vCoord;
            }
            for(int row=0;row<3;row++) {
                double transformed=translation[row]+(double)localScale*(
                    (double)rotation[row*3]*xyz[0]+(double)rotation[row*3+1]*xyz[1]+
                    (double)rotation[row*3+2]*xyz[2]);
                if(!Double.isFinite(transformed)||Math.abs(transformed)>1e12)
                    throw new IOException("Недопустимый результат локального преобразования вершины #"+v);
                positions[v*3+row]=(float)transformed;
            }
        }
        int triStart=dataStart+vertices*stride;
        LinkedHashSet<Long> edges=new LinkedHashSet<>();
        int[] indices=new int[triangles*3];
        for(int t=0;t<triangles;t++) {
            int off=triStart+t*6;
            int a=u16(b,off,end),c=u16(b,off+2,end),d=u16(b,off+4,end);
            if(a>=vertices||c>=vertices||d>=vertices)
                throw new IOException("Треугольник #"+t+" ссылается на несуществующую вершину");
            indices[t*3]=a;indices[t*3+1]=c;indices[t*3+2]=d;
            edge(edges,a,c);edge(edges,c,d);edge(edges,d,a);
            if(edges.size()>MAX_EDGES)
                throw new IOException("Модель превышает лимит "+MAX_EDGES+" рёбер для Vulkan-теста");
        }
        if(edges.isEmpty())throw new IOException("В BSTriShape нет рёбер");
        float[] low={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
        float[] high={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(long e:edges) {
            int a=(int)(e>>>32),c=(int)e;
            for(int v:new int[]{a,c})for(int k=0;k<3;k++) {
                float f=positions[v*3+k];low[k]=Math.min(low[k],f);high[k]=Math.max(high[k],f);
            }
        }
        float span=Math.max(high[0]-low[0],Math.max(high[1]-low[1],high[2]-low[2]));
        if(!Float.isFinite(span)||span<=1e-9f)
            throw new IOException("Вырожденные пространственные границы модели");
        float[] segments=new float[edges.size()*6];int out=0;
        for(long e:edges)for(int v:new int[]{(int)(e>>>32),(int)e})for(int k=0;k<3;k++)
            segments[out++]=((positions[v*3+k]-low[k])/span-0.5f)*2f;
        // Use the very same bounding box as the validated wireframe. Never create
        // a separately normalized surface: the two modes must overlap exactly.
        float[] surfaceTriangles=new float[indices.length*3];
        float[] surfaceUVs=hasUv?new float[indices.length*2]:new float[0];
        for(int i=0;i<indices.length;i++){
            for(int k=0;k<3;k++)
                surfaceTriangles[i*3+k]=((positions[indices[i]*3+k]-low[k])/span-0.5f)*2f;
            if(hasUv){
                surfaceUVs[i*2]=uvs[indices[i]*2];
                surfaceUVs[i*2+1]=uvs[indices[i]*2+1];
            }
        }
        float[] rawDimensions=new float[3],localDimensions=new float[3];
        for(int k=0;k<3;k++){
            rawDimensions[k]=rawHigh[k]-rawLow[k];
            localDimensions[k]=high[k]-low[k];
        }
        return new Mesh(new ObjWireParser.Model(segments,vertices,edges.size()),surfaceTriangles,surfaceUVs,
            block.index,triangles,stride,attributes,block.type,rawDimensions,localDimensions,
            translation,localScale,low,span);
    }
    private static void edge(LinkedHashSet<Long> edges,int a,int b){
        if(a==b)return;int lo=Math.min(a,b),hi=Math.max(a,b);
        edges.add((((long)lo)<<32)|(hi&0xffffffffL));
    }
    private static float finiteFloat(byte[] b,int p,int end,String field,int index)throws IOException{
        float f=Float.intBitsToFloat((int)u32(b,p,end));
        if(!Float.isFinite(f))throw new IOException("Некорректное значение: "+field+" #"+index);
        return f;
    }
    /** IEEE 754 binary16 -> float32, including signed zero, subnormals and Inf/NaN. */
    static float halfFloat(int bits){
        int sign=(bits&0x8000)<<16;
        int exponent=(bits>>>10)&31;
        int mantissa=bits&0x3ff;
        if(exponent==31)return Float.intBitsToFloat(sign|0x7f800000|(mantissa<<13));
        if(exponent==0){
            if(mantissa==0)return Float.intBitsToFloat(sign);
            int e=-14;
            while((mantissa&0x400)==0){mantissa<<=1;e--;}
            return Float.intBitsToFloat(sign|((e+127)<<23)|((mantissa&0x3ff)<<13));
        }
        return Float.intBitsToFloat(sign|((exponent-15+127)<<23)|(mantissa<<13));
    }
    private static int u16(byte[] b,int p,int end)throws IOException{
        if(p<0||p+2>end)throw new IOException("Обрезанное поле ushort BSTriShape");
        return (b[p]&255)|((b[p+1]&255)<<8);
    }
    private static long u32(byte[] b,int p,int end)throws IOException{
        if(p<0||p+4>end)throw new IOException("Обрезанное поле uint BSTriShape");
        return ((long)b[p]&255)|(((long)b[p+1]&255)<<8)|(((long)b[p+2]&255)<<16)|(((long)b[p+3]&255)<<24);
    }
    private static long u64(byte[] b,int p,int end)throws IOException{
        return u32(b,p,end)|(u32(b,p+4,end)<<32);
    }
}
