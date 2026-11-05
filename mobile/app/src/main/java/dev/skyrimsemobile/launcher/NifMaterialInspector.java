package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only BSShaderTextureSet table inspector for NIF 20.2.0.7.
 * Does not resolve assets, decode DDS, or change BSA/NIF bytes.
 * Uses NifBlockInspector's already-validated byte ranges and exact block types.
 */
public final class NifMaterialInspector {
    private NifMaterialInspector() {}
    private static final int MAX_SETS=64, MAX_TEXTURES_PER_SET=32;
    private static final int MAX_PATH_BYTES=1024, MAX_DISPLAY_CHARS=1024;
    private static final String[] SLOT_NAMES={"Диффузная карта", "Нормали / блеск", "Свечение / кожа", 
            "Высота / параллакс", "Окружение", "Маска окружения", 
            "Подповерхностная", "Подсветка сзади"};

    public static final class Slot {
        public final int index;
        public final String role, path;
        Slot(int i,String role,String path){index=i;this.role=role;this.path=path;}
    }
    public static final class TextureSet {
        public final int blockIndex;
        public final List<Slot> slots;
        TextureSet(int i,List<Slot> slots){blockIndex=i;this.slots=Collections.unmodifiableList(slots);}
    }
    public static final class Result {
        public final int shaderPropertyCount, shapeCount;
        public final List<TextureSet> textureSets;
        Result(int shaders,int shapes,List<TextureSet> sets){
            shaderPropertyCount=shaders;shapeCount=shapes;
            textureSets=Collections.unmodifiableList(sets);
        }
        public String report(){
            StringBuilder s=new StringBuilder("NIF v1.5 · ссылки на текстуры (только чтение)\n");
            s.append("BSLightingShaderProperty: ").append(shaderPropertyCount)
             .append(" · BSTriShape: ").append(shapeCount)
             .append(" · BSShaderTextureSet: ").append(textureSets.size()).append('\n');
            if(textureSets.isEmpty())s.append("Наборы текстур не обнаружены в таблице блоков.\n");
            for(TextureSet set:textureSets){
                s.append("\nБлок #").append(set.blockIndex).append(" · ").append(set.slots.size()).append(" слотов:\n");
                for(Slot slot:set.slots){
                    s.append("• [").append(slot.index).append("] ").append(slot.role)
                     .append(": ").append(slot.path.isEmpty()?"(пусто)":slot.path).append('\n');
                }
            }
            s.append("\nПоказаны пути, объявленные в NIF. Связи с конкретными шейдерными блоками ")
             .append("и наличие DDS в других BSA ещё не проверены. ")
             .append("Изображения DDS не декодированы и на модель не наложены. ")
             .append("Данные игры не изменены.");
            return s.toString();
        }
    }

    public static Result inspect(byte[] nif) throws IOException {
        NifBlockInspector.Result index=NifBlockInspector.inspect(nif);
        int shaders=0,shapes=0;
        List<TextureSet> sets=new ArrayList<>();
        for(NifBlockInspector.Block b:index.blocks){
            if("BSLightingShaderProperty".equals(b.type))shaders++;
            if("BSTriShape".equals(b.type))shapes++;
            if(!"BSShaderTextureSet".equals(b.type))continue;
            if(sets.size()>=MAX_SETS)throw new IOException("Слишком много BSShaderTextureSet");
            sets.add(readSet(nif,b));
        }
        return new Result(shaders,shapes,sets);
    }
    private static TextureSet readSet(byte[] nif,NifBlockInspector.Block block)throws IOException {
        if(block.byteLength<4)throw new IOException("BSShaderTextureSet #"+block.index+" обрезан");
        ByteBuffer b=ByteBuffer.wrap(nif,block.byteOffset,block.byteLength).order(ByteOrder.LITTLE_ENDIAN);
        long count=Integer.toUnsignedLong(b.getInt());
        if(count>MAX_TEXTURES_PER_SET)throw new IOException("Слишком много слотов BSShaderTextureSet #"+block.index+": "+count);
        List<Slot> slots=new ArrayList<>();
        for(int i=0;i<count;i++){
            if(b.remaining()<4)throw new IOException("Отсутствует длина пути DDS в слоте "+i);
            long len=Integer.toUnsignedLong(b.getInt());
            if(len>MAX_PATH_BYTES||len>b.remaining())throw new IOException("Недопустимая длина пути текстуры в слоте "+i);
            byte[] raw=new byte[(int)len];b.get(raw);
            String path;
            try {
                path=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw)).toString();
            }catch(CharacterCodingException ex){throw new IOException("Некорректный UTF-8 путь текстуры, слот "+i,ex);}
            if(path.length()>MAX_DISPLAY_CHARS)throw new IOException("Слишком длинный путь текстуры");
            if(path.indexOf('\0')>=0||path.indexOf('\n')>=0||path.indexOf('\r')>=0)
                throw new IOException("Недопустимые управляющие символы пути текстуры");
            path=path.replace('\\','/');
            // These strings are metadata, NOT trusted paths to open or write.
            slots.add(new Slot(i,i<SLOT_NAMES.length?SLOT_NAMES[i]:"Дополнительный слот",path));
        }
        // Some NIF writers append 4-byte zero alignment; reject other dangling data.
        if(b.hasRemaining()){
            if(b.remaining()>8)throw new IOException("Неожиданные данные после набора текстур");
            while(b.hasRemaining())if(b.get()!=0)throw new IOException("Ненулевые остаточные данные набора текстур");
        }
        return new TextureSet(block.index,slots);
    }
}
