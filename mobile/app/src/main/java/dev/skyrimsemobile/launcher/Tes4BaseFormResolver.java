package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read-only, size-capped, same-plugin lookup of NAME FormIDs in flat top-level
 * base-record groups. No load-order, master, override or plugin-index resolution.
 * Scans only allowed base groups BEFORE CELL/WRLD to avoid scanning world data.
 */
public final class Tes4BaseFormResolver {
    private static final int HEADER=24;
    private static final int MAX_GROUPS=512;
    private static final int MAX_HEADERS=60000;
    private static final long MAX_SCANNED_BYTES=24L*1024*1024;
    private static final int MAX_RECORD_BODY=65536;
    private static final int MAX_TARGET_IDS=16;
    private static final Charset TEXT=Charset.forName("windows-1252");
    private static final Set<String> TYPES;
    static{
        LinkedHashSet<String> t=new LinkedHashSet<>();
        Collections.addAll(t,"STAT","MSTT","ACTI","DOOR","CONT","FURN",
            "FLOR","TREE","MISC","WEAP","ARMO","NPC_","LIGH","BOOK","INGR");
        TYPES=Collections.unmodifiableSet(t);
    }
    private Tes4BaseFormResolver(){}

    public static final class Base {
        public final long formId;
        public final String type,editorId,modelPath;
        public final boolean compressed;
        Base(long id,String type,String editor,String model,boolean compressed){
            this.formId=id;this.type=type;this.editorId=editor;this.modelPath=model;
            this.compressed=compressed;
        }
    }
    public static final class Result {
        public final Map<Long,Base> found;
        public final int targets, recordsSeen, groupsSeen, malformedBodies;
        public final long scannedBytes;
        public final boolean partial;
        private Result(Map<Long,Base> found,int targets,int records,int groups,
                       int malformed,long bytes,boolean partial){
            this.found=Collections.unmodifiableMap(new LinkedHashMap<>(found));
            this.targets=targets;this.recordsSeen=records;this.groupsSeen=groups;
            this.malformedBodies=malformed;this.scannedBytes=bytes;this.partial=partial;
        }
        public String report(List<Tes4PlacementSubrecordReader.Placement> placements){
            StringBuilder b=new StringBuilder("Базовые записи NAME · тот же ESM/ESP/ESL\n");
            b.append("Найдено уникальных баз: ").append(found.size()).append(" из ")
             .append(targets).append(" искомых FormID\n");
            b.append("Просмотрено: ").append(groupsSeen).append(" базовых GRUP, ")
             .append(recordsSeen).append(" заголовков, ")
             .append(scannedBytes).append(" байт\n");
            if(malformedBodies>0)b.append("Пропущено некорректных payload: ").append(malformedBodies).append('\n');
            if(partial)b.append("Выборка ограничена лимитами чтения.\n");
            for(Tes4PlacementSubrecordReader.Placement p:placements){
                Base record=found.get(p.baseFormId);
                b.append("• ").append(p.type).append(" 0x").append(hex(p.recordFormId))
                 .append(" → NAME 0x").append(hex(p.baseFormId));
                if(record==null)b.append(" · база НЕ НАЙДЕНА в ограниченной выборке");
                else{
                    b.append(" · ").append(record.type);
                    if(!record.editorId.isEmpty())b.append(" · EDID ").append(record.editorId);
                    if(!record.modelPath.isEmpty())b.append(" · MODL ").append(record.modelPath);
                    else b.append(record.compressed?" · данные сжаты":" · путь NIF не найден");
                }
                b.append('\n');
            }
            b.append("Только прямые базовые записи указанных типов до CELL/WRLD.");
            b.append(" Мастера, моды, переопределения, локализация и полная связь FormID НЕ обработаны.");
            b.append(" Путь MODL не означает, что модель найдена в BSA или отрисована.");
            return b.toString();
        }
    }

    public static Result resolve(InputStream in,
             List<Tes4PlacementSubrecordReader.Placement> placements,long fileSize)
             throws IOException {
        if(in==null)throw new IOException("Не удалось открыть ESM");
        if(fileSize < -1)throw new IOException("Недопустимый размер ESM");
        LinkedHashSet<Long> targets=new LinkedHashSet<>();
        if(placements!=null)for(Tes4PlacementSubrecordReader.Placement p:placements){
            if(p.hasBase)targets.add(p.baseFormId);
            if(targets.size()>MAX_TARGET_IDS)throw new IOException("Слишком много искомых FormID");
        }
        Map<Long,Base> found=new LinkedHashMap<>();
        if(targets.isEmpty())return new Result(found,0,0,0,0,0,false);
        Tes4PluginHeaderReader.Result header=Tes4PluginHeaderReader.inspect(in);
        long pos=24L+header.headerBytes, scanned=0;
        if(fileSize>=0&&pos>fileSize)throw new IOException("TES4 выходит за границы");
        int groups=0,records=0,malformed=0;
        boolean partial=false;
        for(int groupIndex=0;groupIndex<MAX_GROUPS;groupIndex++){
            if(fileSize>=0&&pos==fileSize)break;
            byte[] h=headerOrEnd(in);
            if(h==null){
                if(fileSize>=0&&pos!=fileSize)throw new IOException("ESM обрезан");
                break;
            }
            String tag=tag(h,0);
            long groupSize=u32(h,4);
            String groupType=tag(h,8);
            if(!"GRUP".equals(tag)||groupSize<HEADER||u32(h,12)!=0)
                throw new IOException("Неверная верхняя GRUP при offset="+pos);
            if(fileSize>=0&&groupSize>fileSize-pos)
                throw new IOException("GRUP за пределами ESM");
            if("CELL".equals(groupType)||"WRLD".equals(groupType))break;
            if(!TYPES.contains(groupType)){
                skipExactly(in,groupSize-HEADER);
                pos+=groupSize;
                continue;
            }
            groups++;
            long end=pos+groupSize;
            pos+=HEADER;
            while(pos<end){
                if(records>=MAX_HEADERS||scanned>=MAX_SCANNED_BYTES){
                    partial=true;
                    return new Result(found,targets.size(),records,groups,malformed,scanned,partial);
                }
                if(end-pos<HEADER)throw new IOException("Неполная запись в "+groupType);
                byte[] rh=exact(in,HEADER);
                pos+=HEADER;
                scanned+=HEADER;
                String recordType=tag(rh,0);
                long len=u32(rh,4), flags=u32(rh,8),formId=u32(rh,12);
                if("GRUP".equals(recordType))
                    throw new IOException("Неожиданная вложенная GRUP в базовом разделе");
                if(len>end-pos)throw new IOException("Запись за границей "+groupType);
                records++;
                if(targets.contains(formId) && !found.containsKey(formId)){
                    if((flags&0x00040000L)!=0){
                        found.put(formId,new Base(formId,recordType,"","",true));
                        skipExactly(in,len);
                    }else if(len<=MAX_RECORD_BODY){
                        byte[] payload=exact(in,(int)len);
                        try{
                            String[] metadata=parseMetadata(payload);
                            found.put(formId,new Base(formId,recordType,metadata[0],metadata[1],false));
                        }catch(IOException ignored){malformed++;}
                    }else skipExactly(in,len);
                }else skipExactly(in,len);
                pos+=len;
                scanned+=len;
                if(found.size()==targets.size())
                    return new Result(found,targets.size(),records,groups,malformed,scanned,false);
            }
            if(pos!=end)throw new IOException("Неверные смещения GRUP");
        }
        return new Result(found,targets.size(),records,groups,malformed,scanned,partial);
    }

    private static String[] parseMetadata(byte[] payload)throws IOException {
        String editor="",model="";
        int pos=0,subrecords=0;
        long extended=-1;
        while(pos<payload.length){
            if(++subrecords>8192||payload.length-pos<6)
                throw new IOException("Некорректная базовая подзапись");
            String tag=tag(payload,pos);
            int n=u16(payload,pos+4);
            pos+=6;
            if("XXXX".equals(tag)){
                if(n!=4||extended>=0||payload.length-pos<4)
                    throw new IOException("Неверный XXXX");
                extended=u32(payload,pos);
                pos+=4;
                continue;
            }
            long size=extended>=0?extended:n;
            extended=-1;
            if(size>payload.length-pos)throw new IOException("Некорректный размер "+tag);
            if("EDID".equals(tag)&&editor.isEmpty())
                editor=cleanString(payload,pos,(int)size,120);
            else if("MODL".equals(tag)&&model.isEmpty()){
                String candidate=cleanString(payload,pos,(int)size,260).replace('\\','/');
                String low=candidate.toLowerCase(Locale.ROOT);
                if(low.endsWith(".nif")&&!candidate.startsWith("/")&&!candidate.contains(".."))
                    model=candidate;
            }
            pos+=(int)size;
        }
        if(extended>=0)throw new IOException("XXXX без подзаписи");
        return new String[]{editor,model};
    }
    private static String cleanString(byte[] bytes,int start,int size,int limit){
        int end=start;
        while(end<start+size&&end-start<limit&&bytes[end]!=0)end++;
        String s=new String(bytes,start,end-start,TEXT).trim();
        return s.replace("\n"," ").replace("\r"," ");
    }
    private static byte[] headerOrEnd(InputStream in)throws IOException {
        int first=in.read();
        if(first<0)return null;
        byte[] result=new byte[HEADER];result[0]=(byte)first;
        int at=1;
        while(at<HEADER){
            int read=in.read(result,at,HEADER-at);
            if(read<0)throw new IOException("Обрезанный заголовок GRUP");
            if(read==0){int x=in.read();if(x<0)throw new IOException("Обрезанный заголовок");result[at++]=(byte)x;}
            else at+=read;
        }
        return result;
    }
    private static byte[] exact(InputStream in,int bytes)throws IOException{
        byte[] out=new byte[bytes];int at=0;
        while(at<bytes){
            int n=in.read(out,at,bytes-at);
            if(n<0)throw new IOException("Обрезанный payload");
            if(n==0){int a=in.read();if(a<0)throw new IOException("Обрезанный payload");out[at++]=(byte)a;}
            else at+=n;
        }
        return out;
    }
    private static void skipExactly(InputStream in,long bytes)throws IOException{
        byte[] scratch=new byte[8192];
        while(bytes>0){
            long skipped=in.skip(bytes);
            if(skipped>0){bytes-=skipped;continue;}
            int count=in.read(scratch,0,(int)Math.min(scratch.length,bytes));
            if(count<=0)throw new IOException("Обрезанный ESM");
            bytes-=count;
        }
    }
    private static int u16(byte[] b,int at){return (b[at]&255)|((b[at+1]&255)<<8);}
    private static long u32(byte[] b,int at){return (b[at]&255L)|((b[at+1]&255L)<<8)|((b[at+2]&255L)<<16)|((b[at+3]&255L)<<24);}
    private static String tag(byte[] b,int at){return new String(b,at,4,StandardCharsets.US_ASCII);}
    private static String hex(long n){return String.format(Locale.ROOT,"%08X",n);}
}
