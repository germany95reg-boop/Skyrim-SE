package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only, bounded NIF 20.2.0.7 block-table inventory.
 * Identifies block types and byte ranges; DOES NOT decode vertices, transforms or edges.
 * Designed for BSA records already bounded to 16 MiB by BsaEntryInspector.
 */
public final class NifBlockInspector {
    private NifBlockInspector() {}
    public static final int MAX_BYTES=16*1024*1024;
    private static final int MAX_BLOCKS=8192,MAX_TYPES=512,MAX_STRINGS=16384;
    private static final int MAX_NAME=256,MAX_STRING=4096,MAX_GROUPS=4096;

    public static final class Block {
        public final int index, byteOffset, byteLength;
        public final String type;
        Block(int index, int byteOffset, int byteLength, String type) {
            this.index=index;this.byteOffset=byteOffset;this.byteLength=byteLength;this.type=type;
        }
    }

    public static final class Result {
        public final int fileVersion,userVersion,streamVersion,blockCount,blockTypeCount,stringCount;
        public final int blockSectionOffset,meshCandidateCount,rootCount;
        public final List<Block> blocks;
        public final int[] rootIndices;
        public final Map<String,Integer> counts;
        Result(int fv,int uv,int sv,int num,int typeCount,int strings,int start,int shapes,
               int[] rootIds,List<Block> blocks,Map<String,Integer> counts){
            fileVersion=fv;userVersion=uv;streamVersion=sv;blockCount=num;
            blockTypeCount=typeCount;stringCount=strings;blockSectionOffset=start;
            meshCandidateCount=shapes;rootCount=rootIds.length;rootIndices=rootIds.clone();
            this.blocks=blocks;this.counts=counts;
        }
        public String report() {
            StringBuilder sb=new StringBuilder("NIF v1.0 · таблица блоков (без декодирования геометрии)\n");
            sb.append("Формат: 20.2.0.7 · User Version: ").append(userVersion)
              .append(" · Bethesda Stream: ").append(streamVersion).append('\n');
            sb.append("Всего блоков: ").append(blockCount).append(" · типов: ")
              .append(blockTypeCount).append(" · строк: ").append(stringCount)
              .append(" · корней: ").append(rootCount).append('\n');
            sb.append("Потенциальные блоки геометрии: ").append(meshCandidateCount).append('\n');
            sb.append("Группы типов:\n");
            int shown=0;
            for(Map.Entry<String,Integer> e:counts.entrySet()) {
                if(shown++==32){sb.append("  … и другие типы\n");break;}
                sb.append("• ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
            sb.append("\nПервые ").append(Math.min(32,blocks.size())).append(" блока(ов):\n");
            for(int i=0;i<Math.min(32,blocks.size());i++){
                Block b=blocks.get(i);
                sb.append('#').append(b.index).append(' ').append(b.type)
                  .append(" · ").append(b.byteLength).append(" Б · +0x")
                  .append(Integer.toHexString(b.byteOffset)).append('\n');
            }
            sb.append("\nПроверены границы и индексы таблиц, но ссылки между всеми типами блоков пока не разобраны.")
              .append(" Вершины, текстуры, скелет и геометрия NIF НЕ декодировались.")
              .append(" Объект не создавался в Vulkan. Данные исходного BSA не изменены.");
            return sb.toString();
        }
    }

    public static Result inspect(byte[] bytes) throws IOException {
        if(bytes==null||bytes.length<80||bytes.length>MAX_BYTES)
            throw new IOException("Размер NIF не соответствует безопасному диапазону");
        Reader r=new Reader(bytes);
        int start=r.position();
        while(r.position()-start<=128 && r.remaining()>0 && r.getUnsignedByte()!='\n') {}
        int lineEnd=r.position();
        if(lineEnd-start>129||bytes[lineEnd-1]!='\n')throw new IOException("Неверный NIF header line");
        String header=new String(bytes,start,lineEnd-start-1,StandardCharsets.US_ASCII);
        if(!header.equals("Gamebryo File Format, Version 20.2.0.7") &&
           !header.equals("NetImmerse File Format, Version 20.2.0.7"))
            throw new IOException("Поддерживается таблица блоков NIF только версии 20.2.0.7");
        int ver=(int)r.u32();
        if(ver!=0x14020007)throw new IOException("Версия NIF в бинарном поле не совпадает с заголовком");
        if(r.getUnsignedByte()!=1)throw new IOException("Большой порядок байтов NIF не поддерживается");
        int user=(int)r.u32();
        long num=r.u32();
        if(num<1||num>MAX_BLOCKS)throw new IOException("Превышен безопасный лимит блоков NIF: "+num);
        int stream=(int)r.u32();
        if(user!=12 || (stream!=83 && stream!=100))
            throw new IOException("Неподдерживаемая пара User/Stream NIF: "+user+"/"+stream);
        for(int i=0;i<3;i++)r.shortString(); // Bethesda export strings
        int typeCount=r.u16();
        if(typeCount<1||typeCount>MAX_TYPES)throw new IOException("Недопустимое число типов NIF");
        List<String> types=new ArrayList<>();
        for(int i=0;i<typeCount;i++)types.add(r.sizedString(MAX_NAME));
        int[] indexes=new int[(int)num];
        for(int i=0;i<num;i++){
            indexes[i]=r.u16();
            if(indexes[i]>=typeCount)throw new IOException("Некорректный индекс типа блока #"+i);
        }
        int[] lengths=new int[(int)num];
        long total=0;
        for(int i=0;i<num;i++){
            long n=r.u32();
            if(n>MAX_BYTES || total+n>MAX_BYTES)throw new IOException("Недопустимый размер блока #"+i);
            lengths[i]=(int)n;total+=n;
        }
        long ns=r.u32();
        if(ns>MAX_STRINGS)throw new IOException("Недопустимое число строк NIF");
        long maxStringLength=r.u32();
        if(maxStringLength>MAX_STRING)throw new IOException("Слишком длинная строка в заголовке NIF");
        long totalStringBytes=0;
        for(int i=0;i<ns;i++){
            int before=r.position();
            r.sizedString(MAX_STRING);
            totalStringBytes+=r.position()-before;
            if(totalStringBytes>4*1024*1024L)throw new IOException("Чрезмерный объём строк NIF");
        }
        long groupCount=r.u32();
        if(groupCount>MAX_GROUPS)throw new IOException("Слишком много групп NIF");
        for(int i=0;i<groupCount;i++)r.u32();
        int blocksStart=r.position();
        if(total>r.remaining()-4L)throw new IOException("Блоки NIF выходят за границу файла");
        List<Block> blocks=new ArrayList<>();Map<String,Integer> counts=new LinkedHashMap<>();
        int shapes=0;
        for(int i=0;i<num;i++){
            String type=types.get(indexes[i]);
            blocks.add(new Block(i,r.position(),lengths[i],type));
            counts.put(type,counts.getOrDefault(type,0)+1);
            if(type.equals("BSTriShape")||type.equals("BSDynamicTriShape")||
               type.equals("BSSubIndexTriShape")||type.equals("BSMeshLODTriShape")||
               type.equals("NiTriShape")||type.equals("NiTriShapeData"))shapes++;
            r.skip(lengths[i]);
        }
        long roots=r.u32();
        if(roots>num)throw new IOException("Число корневых блоков NIF превышает общее число блоков");
        int[] rootRefs=new int[(int)roots];
        for(int i=0;i<roots;i++){
            long blockIndex=r.u32();
            if(blockIndex>=num)throw new IOException("Некорректная ссылка на корневой блок NIF");
            rootRefs[i]=(int)blockIndex;
        }
        return new Result(ver,user,stream,(int)num,typeCount,(int)ns,
            blocksStart,shapes,rootRefs,blocks,counts);
    }
    private static final class Reader {
        private final byte[] b;private int p;
        Reader(byte[] bytes){b=bytes;}
        int position(){return p;}
        int remaining(){return b.length-p;}
        void ensure(int n)throws IOException{if(n<0||n>remaining())throw new IOException("Обрезанные данные NIF на позиции 0x"+Integer.toHexString(p));}
        int getUnsignedByte()throws IOException{ensure(1);return b[p++]&255;}
        int u16()throws IOException{ensure(2);int v=(b[p]&255)|((b[p+1]&255)<<8);p+=2;return v;}
        long u32()throws IOException{ensure(4);long v=((long)b[p]&255)|(((long)b[p+1]&255)<<8)|(((long)b[p+2]&255)<<16)|(((long)b[p+3]&255)<<24);p+=4;return v;}
        void skip(int n)throws IOException{ensure(n);p+=n;}
        String shortString()throws IOException {int n=getUnsignedByte();if(n>MAX_NAME)throw new IOException("Слишком длинная экспортная строка");return readString(n);}
        String sizedString(int max)throws IOException {long n=u32();if(n>max)throw new IOException("Строка NIF превысила ограничение "+max);return readString((int)n);}
        String readString(int n)throws IOException {ensure(n);String s=new String(b,p,n,StandardCharsets.UTF_8);p+=n;return s;}
    }
}
