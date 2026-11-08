package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A bounded read-only SAMPLE of nested GRUPs and record headers in one top-level
 * CELL or WRLD section of a TES4 plugin. Never decodes record payloads.
 */
public final class Tes4NestedRecordSampler {
    private static final int HEADER=24;
    private static final int MAX_TOP_GROUPS=512;
    private static final int MAX_HEADERS=1000;
    private static final int MAX_DEPTH=24;
    private static final long MAX_SECTION_BYTES=16L*1024*1024;
    private static final int MAX_SAMPLES=16;
    private Tes4NestedRecordSampler(){}

    public static final class Record {
        public final String type;
        public final long offset,formId,flags,bodyBytes;
        public final int depth;
        private Record(String type,long offset,long formId,long flags,long bodyBytes,int depth){
            this.type=type;this.offset=offset;this.formId=formId;this.flags=flags;
            this.bodyBytes=bodyBytes;this.depth=depth;
        }
    }
    public static final class Result {
        public final String section;
        public final long sectionOffset,sectionBytes,examinedBytes;
        public final int groupsSeen,recordsSeen,maxDepth;
        public final boolean partial;
        public final Map<String,Integer> recordTypes;
        public final List<Record> samples;
        private Result(String section,long sectionOffset,long sectionBytes,long examinedBytes,
                       int groupsSeen,int recordsSeen,int maxDepth,boolean partial,
                       Map<String,Integer> types,List<Record> samples){
            this.section=section;this.sectionOffset=sectionOffset;this.sectionBytes=sectionBytes;
            this.examinedBytes=examinedBytes;this.groupsSeen=groupsSeen;this.recordsSeen=recordsSeen;
            this.maxDepth=maxDepth;this.partial=partial;
            this.recordTypes=java.util.Collections.unmodifiableMap(new LinkedHashMap<>(types));
            this.samples=java.util.Collections.unmodifiableList(new ArrayList<>(samples));
        }
        @Override public String toString(){
            StringBuilder b=new StringBuilder();
            b.append("TES4 · вложенные записи раздела ").append(section).append('\n')
             .append("Начало раздела: ").append(sectionOffset)
             .append(" · длина GRUP: ").append(sectionBytes).append(" байт\n")
             .append("Проанализировано в разделе: ").append(examinedBytes).append(" байт\n")
             .append("Пройдено вложенных GRUP: ").append(groupsSeen)
             .append(" · заголовков записей: ").append(recordsSeen)
             .append(" · максимальная глубина: ").append(maxDepth).append('\n')
             .append("Состояние: ").append(partial?"ЧАСТИЧНЫЙ ОБРАЗЕЦ (ограничение чтения)":"раздел пройден полностью").append('\n')
             .append("Типы записей в прочитанном фрагменте:");
            if(recordTypes.isEmpty())b.append(" отсутствуют\n");
            else {
                for(Map.Entry<String,Integer> e:recordTypes.entrySet())
                    b.append("\n• ").append(e.getKey()).append(": ").append(e.getValue());
                b.append('\n');
            }
            b.append("Первые FormID:");
            for(Record r:samples) {
                b.append("\n• ").append(r.type).append(" 0x")
                 .append(hex(r.formId)).append(" · offset ").append(r.offset)
                 .append(" · depth ").append(r.depth)
                 .append(" · compressed=").append((r.flags&0x00040000L)!=0);
            }
            b.append("\n\nТолько заголовки без содержимого записей, разрешения FormID,");
            b.append(" распаковки, NPC или запуска SkyrimSE.exe. Это НЕ весь игровой мир.");
            return b.toString();
        }
    }
    public static Result scan(InputStream in,String section,long fileSize)throws IOException{
        if(in==null)throw new IOException("Файл ESM не открыт");
        if(!"CELL".equals(section)&&!"WRLD".equals(section))
            throw new IOException("Допустимые разделы: CELL или WRLD");
        if(fileSize< -1)throw new IOException("Неверная длина файла");
        Tes4PluginHeaderReader.Result tes4=Tes4PluginHeaderReader.inspect(in);
        long offset=24L+tes4.headerBytes;
        if(fileSize>=0&&offset>fileSize)throw new IOException("TES4 вне границ файла");

        long start=-1,groupSize=-1;
        for(int top=0;top<MAX_TOP_GROUPS;top++){
            if(fileSize>=0 && offset==fileSize)break;
            byte[] header=readHeaderOrEof(in);
            if(header==null)break;
            long size=u32(header,4);
            if(!"GRUP".equals(tag(header,0))||size<HEADER||u32(header,12)!=0)
                throw new IOException("Некорректная верхняя GRUP на смещении "+offset);
            if(fileSize>=0&&size>fileSize-offset)
                throw new IOException("GRUP выходит за границы ESM");
            if(section.equals(tag(header,8))){
                start=offset;groupSize=size;
                break;
            }
            skipExactly(in,size-HEADER);
            offset+=size;
        }
        if(start<0)throw new IOException("Верхняя группа "+section+" не найдена");
        long end=start+groupSize;
        long pos=start+HEADER;
        Deque<Long> parents=new ArrayDeque<>();
        parents.push(end);
        Map<String,Integer> types=new LinkedHashMap<>();
        List<Record> records=new ArrayList<>();
        int children=0,recordCount=0,headers=0,depth=0;
        boolean partial=false;

        while(!parents.isEmpty()){
            if(pos==parents.peek()){parents.pop();continue;}
            if(pos>parents.peek())throw new IOException("Группа GRUP вышла за границы родителя");
            if(headers>=MAX_HEADERS || pos-(start+HEADER)>=MAX_SECTION_BYTES){
                partial=true;break;
            }
            if(parents.peek()-pos<HEADER)throw new IOException("Неполный вложенный заголовок");
            byte[] h=readExactly(in,HEADER);
            long recordOffset=pos;
            pos+=HEADER;
            headers++;
            if("GRUP".equals(tag(h,0))){
                long bytes=u32(h,4);
                if(bytes<HEADER||bytes-HEADER>parents.peek()-pos)
                    throw new IOException("Вложенная GRUP выходит за границы родителя");
                children++;
                long childEnd=recordOffset+bytes;
                if(parents.size()>=MAX_DEPTH) {
                    // Skip overly deep subtrees instead of risking recursive parsing.
                    skipExactly(in,childEnd-pos);
                    pos=childEnd;partial=true;
                    continue;
                }
                parents.push(childEnd);
                depth=Math.max(depth,parents.size()-1);
            }else{
                String type=tag(h,0);
                for(int i=0;i<type.length();i++){
                    char ch=type.charAt(i);
                    if(ch<32||ch>126)throw new IOException("Повреждённый тип записи");
                }
                long bytes=u32(h,4);
                if(bytes>parents.peek()-pos)
                    throw new IOException("Запись "+type+" выходит за границы группы");
                if(pos+bytes-(start+HEADER)>MAX_SECTION_BYTES){
                    partial=true;break;
                }
                long flags=u32(h,8),formId=u32(h,12);
                recordCount++;
                types.put(type,types.containsKey(type)?types.get(type)+1:1);
                if(records.size()<MAX_SAMPLES)
                    records.add(new Record(type,recordOffset,formId,flags,bytes,parents.size()-1));
                skipExactly(in,bytes);
                pos+=bytes;
            }
        }
        return new Result(section,start,groupSize,pos-(start+HEADER),children,
                          recordCount,depth,partial,types,records);
    }
    private static byte[] readHeaderOrEof(InputStream in)throws IOException{
        int first=in.read();
        if(first<0)return null;
        byte[] header=new byte[HEADER];
        header[0]=(byte)first;
        int at=1;
        while(at<HEADER){
            int got=in.read(header,at,HEADER-at);
            if(got<0)throw new IOException("Обрезанный заголовок ESM");
            if(got==0){
                int b=in.read();
                if(b<0)throw new IOException("Обрезанный заголовок ESM");
                header[at++]=(byte)b;
            }else at+=got;
        }
        return header;
    }
    private static byte[] readExactly(InputStream in,int count)throws IOException{
        byte[] b=new byte[count];int at=0;
        while(at<count){
            int n=in.read(b,at,count-at);
            if(n<0)throw new IOException("Обрезанный файл ESM");
            if(n==0){
                int c=in.read();
                if(c<0)throw new IOException("Обрезанный файл ESM");
                b[at++]=(byte)c;
            }else at+=n;
        }
        return b;
    }
    private static void skipExactly(InputStream in,long bytes)throws IOException{
        byte[] scratch=new byte[8192];
        while(bytes>0){
            long n=in.skip(bytes);
            if(n>0){bytes-=n;continue;}
            int got=in.read(scratch,0,(int)Math.min(bytes,scratch.length));
            if(got<=0)throw new IOException("Обрезанное содержимое ESM");
            bytes-=got;
        }
    }
    private static long u32(byte[] b,int at){
        return (b[at]&255L)|((b[at+1]&255L)<<8)|((b[at+2]&255L)<<16)|((b[at+3]&255L)<<24);
    }
    private static String tag(byte[] b,int pos){
        return new String(b,pos,4,StandardCharsets.US_ASCII);
    }
    private static String hex(long n){return String.format(Locale.ROOT,"%08X",n);}
}
