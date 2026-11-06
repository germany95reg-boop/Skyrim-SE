package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** v2.11, strictly bounded shape -> lighting shader -> texture set references.
 * Read-only diagnostic; no guesses from block order or texture filenames.
 * Supports the standard Skyrim SE stream-100 NiAVObject/BSLightingShaderProperty
 * layout, and explicitly reports unsupported links.
 */
public final class NifMaterialLinkInspector {
    private NifMaterialLinkInspector(){}
    public static final int MAX_PARTS=24;
    private static final int MAX_EXTRAS=128;
    /** Skyrim SE BSLightingShaderProperty begins with an additional 4-byte
     * Shader Type field before NiObjectNET (niftools nif.xml, stream 100).
     * The previous v2.10 parser wrongly read NiObjectNET Name as Extra Count.
     */
    private static final int LIGHTING_SHADER_TYPE_BYTES=4;

    public static final class Link {
        public final int shape,shader,textureSet;
        public final String diffuse,normal,problem;
        Link(int shape,int shader,int set,String diffuse,String normal,String problem){
            this.shape=shape;this.shader=shader;textureSet=set;
            this.diffuse=diffuse;this.normal=normal;this.problem=problem;
        }
        public boolean resolved(){return problem==null;}
    }

    public static final class Result {
        public final List<Link> links;
        public final int resolved;
        Result(List<Link> result,int count){
            links=Collections.unmodifiableList(new ArrayList<>(result));
            resolved=count;
        }
        public String report(){
            StringBuilder b=new StringBuilder("Материалы частей NIF · v2.11 (диагностика)\n");
            b.append("Частей BSTriShape: ").append(links.size())
                .append(" · подтверждённых связей: ").append(resolved).append("\n");
            for(Link l:links){
                b.append("\nBSTriShape #").append(l.shape);
                if(l.shader>=0)b.append(" → BSLightingShaderProperty #").append(l.shader);
                if(l.textureSet>=0)b.append(" → BSShaderTextureSet #").append(l.textureSet);
                if(l.resolved()){
                    b.append("\n  DDS [0]: ").append(l.diffuse.isEmpty()?"(нет)":l.diffuse)
                     .append("\n  DDS [1]: ").append(l.normal.isEmpty()?"(нет)":l.normal);
                }else b.append("\n  Не разрешено: ").append(l.problem);
                b.append('\n');
            }
            b.append("\nСвязи сверены по индексам и типам блоков NIF, а не по порядку "+
                "или совпадению имён. Наличие DDS в Data не проверяется. "+
                "Составная модель в Vulkan пока открывается без текстур. "+
                "Файлы игры не изменяются.");
            return b.toString();
        }
    }

    public static Result inspect(byte[] data)throws IOException{
        NifBlockInspector.Result index=NifBlockInspector.inspect(data);
        if(index.userVersion!=12||index.streamVersion!=100)
            throw new IOException("Связи материалов поддерживаются только для Skyrim SE stream 100");
        NifMaterialInspector.Result materials=NifMaterialInspector.inspect(data);
        Map<Integer,NifMaterialInspector.TextureSet> sets=new HashMap<>();
        for(NifMaterialInspector.TextureSet set:materials.textureSets)
            sets.put(set.blockIndex,set);

        List<Link> found=new ArrayList<>();
        int resolved=0;
        for(NifBlockInspector.Block shape:index.blocks){
            if(!shape.type.equals("BSTriShape"))continue;
            if(found.size()>=MAX_PARTS)
                throw new IOException("Слишком много BSTriShape для диагностики (предел "+MAX_PARTS+")");
            Link link;
            try{link=resolve(data,index.blocks,shape,sets);}
            catch(IOException ex){link=new Link(shape.index,-1,-1,"","",ex.getMessage());}
            found.add(link);
            if(link.resolved())resolved++;
        }
        if(found.isEmpty())throw new IOException("Нет BSTriShape в NIF");
        return new Result(found,resolved);
    }

    private static Link resolve(byte[] data,List<NifBlockInspector.Block> blocks,
            NifBlockInspector.Block shape,Map<Integer,NifMaterialInspector.TextureSet> sets)
            throws IOException{
        // BSTriShape: NiObjectNET variable extra refs; validated NiAVObject
        // fields and bounding sphere, then skin/shader/alpha references.
        int extra=extras(data,shape);
        int shaderId=ref(data,shape,92+4*extra);
        if(shaderId<0)return new Link(shape.index,-1,-1,"","",
            "Нет ссылки на шейдер у BSTriShape");
        NifBlockInspector.Block shader=expect(blocks,shaderId,"BSLightingShaderProperty");
        if(shader==null)return new Link(shape.index,shaderId,-1,"","",
            "Шейдер не является BSLightingShaderProperty или индекс недопустим");

        // In SSE, ONLY BSLightingShaderProperty adds a 4-byte Shader Type
        // BEFORE NiObjectNET. The shape does not have this leading field.
        // Then NiObjectNET: Name, Extra Count/refs, Controller.
        // BSShaderProperty has no additional SSE fields in this version;
        // BSLightingShaderProperty: Flags1, Flags2, UV offset, UV scale, Set ref.
        int shaderExtras=extras(data,shader,LIGHTING_SHADER_TYPE_BYTES);
        int setId=ref(data,shader,36+LIGHTING_SHADER_TYPE_BYTES+4*shaderExtras);
        if(setId<0)return new Link(shape.index,shaderId,-1,"","",
            "У шейдера нет ссылки на BSShaderTextureSet");
        NifBlockInspector.Block texture=expect(blocks,setId,"BSShaderTextureSet");
        if(texture==null||!sets.containsKey(setId))
            return new Link(shape.index,shaderId,setId,"","",
                "Ссылка не ведёт на корректный BSShaderTextureSet");
        NifMaterialInspector.TextureSet set=sets.get(setId);
        String diffuse="",normal="";
        for(NifMaterialInspector.Slot slot:set.slots){
            if(slot.index==0)diffuse=slot.path;
            if(slot.index==1)normal=slot.path;
        }
        return new Link(shape.index,shaderId,setId,diffuse,normal,null);
    }

    private static NifBlockInspector.Block expect(List<NifBlockInspector.Block> blocks,
            int ref,String type){
        return ref>=0&&ref<blocks.size()&&type.equals(blocks.get(ref).type)
            ?blocks.get(ref):null;
    }

    private static int extras(byte[] data,NifBlockInspector.Block block)throws IOException{
        return extras(data,block,0);
    }
    private static int extras(byte[] data,NifBlockInspector.Block block,
            int leadingBytes)throws IOException{
        long n=unsignedInt(data,block,leadingBytes+4);
        if(n>MAX_EXTRAS)
            throw new IOException("Некорректное количество extra-data ссылок в #"+block.index);
        return (int)n;
    }
    private static int ref(byte[] data,NifBlockInspector.Block block,int offset)throws IOException{
        long raw=unsignedInt(data,block,offset);
        if(raw==0xffffffffL)return -1;
        if(raw>Integer.MAX_VALUE)
            throw new IOException("Недопустимый индекс ссылки в #"+block.index);
        return (int)raw;
    }
    private static long unsignedInt(byte[] data,NifBlockInspector.Block block,int offset)
            throws IOException{
        if(offset<0||block.byteLength-offset<4||(long)block.byteOffset+offset+4>data.length)
            throw new IOException("Обрезанная ссылка в NIF-блоке #"+block.index);
        int p=block.byteOffset+offset;
        return ((long)data[p]&255)|(((long)data[p+1]&255)<<8)|
            (((long)data[p+2]&255)<<16)|(((long)data[p+3]&255)<<24);
    }
}
