package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Bounded, read-only reader for the FIRST TES4 record; not a Skyrim runtime. */
public final class Tes4PluginHeaderReader {
    private static final int MAX_HEADER_BYTES=4*1024*1024;
    private static final Charset TEXT=Charset.forName("windows-1252");
    private Tes4PluginHeaderReader(){}

    public static final class Result {
        public final int headerBytes, formVersion;
        public final long flags, recordCount, nextObjectId;
        public final float hedrVersion;
        public final String author, description;
        public final List<String> masters;
        Result(int bytes,int form,long flags,float version,long count,long id,
               String author,String description,List<String> masters){
            this.headerBytes=bytes;this.formVersion=form;this.flags=flags;
            this.hedrVersion=version;this.recordCount=count;this.nextObjectId=id;
            this.author=author;this.description=description;
            this.masters=Collections.unmodifiableList(new ArrayList<>(masters));
        }
        @Override public String toString(){
            StringBuilder b=new StringBuilder("TES4 · заголовок плагина Skyrim SE\n");
            b.append("Формат HEDR: ").append(hedrVersion).append(" · версия записи: ").append(formVersion).append('\n');
            b.append("Записей заявлено: ").append(recordCount).append(" · следующий ObjectID: 0x")
             .append(Long.toHexString(nextObjectId).toUpperCase(Locale.ROOT)).append('\n');
            b.append("Флаги: master=").append((flags&1)!=0).append(", ESL=")
             .append((flags&0x200)!=0).append(", localized=").append((flags&0x80)!=0).append('\n');
            if(!author.isEmpty())b.append("Автор: ").append(author).append('\n');
            if(!description.isEmpty())b.append("Описание: ").append(description).append('\n');
            b.append("Зависимости MAST (").append(masters.size()).append("):");
            if(masters.isEmpty())b.append(" нет");
            else for(String master:masters)b.append("\n• ").append(master);
            return b.append("\n\nТолько заголовок TES4. CELL/WRLD/REFR и запуск SkyrimSE.exe пока не реализованы.").toString();
        }
    }
    public static Result inspect(InputStream in)throws IOException{
        if(in==null)throw new IOException("Файл не открыт");
        byte[] header=exact(in,24);
        if(!"TES4".equals(tag(header,0)))throw new IOException("Нет заголовка TES4");
        long size=u32(header,4);
        if(size<18||size>MAX_HEADER_BYTES)throw new IOException("Недопустимый размер заголовка TES4");
        byte[] bytes=exact(in,(int)size);
        boolean hasHedr=false;
        long count=0,nextId=0,extended=-1;
        float version=0;
        int pos=0,subCount=0;
        String author="",description="";
        List<String> masters=new ArrayList<>();
        while(pos<bytes.length){
            if(++subCount>8192||bytes.length-pos<6)throw new IOException("Некорректные подзаписи TES4");
            String type=tag(bytes,pos);
            int n=u16(bytes,pos+4);
            pos+=6;
            if("XXXX".equals(type)){
                if(extended>=0||n!=4||bytes.length-pos<4)throw new IOException("Неверный XXXX");
                extended=u32(bytes,pos);pos+=4;
                if(extended>MAX_HEADER_BYTES)throw new IOException("XXXX слишком велик");
                continue;
            }
            long actual=extended>=0?extended:n;
            extended=-1;
            if(actual>bytes.length-pos)throw new IOException("Обрезанная подзапись "+type);
            n=(int)actual;
            if("HEDR".equals(type)){
                if(hasHedr||n!=12)throw new IOException("Повторный или неверный HEDR");
                version=Float.intBitsToFloat((int)u32(bytes,pos));
                if(!Float.isFinite(version)||version<=0||version>100)throw new IOException("Неверная версия HEDR");
                count=u32(bytes,pos+4);nextId=u32(bytes,pos+8);hasHedr=true;
            }else if("MAST".equals(type)){
                if(masters.size()>=255)throw new IOException("Слишком много MAST");
                String name=txt(bytes,pos,n,260);
                if(name.isEmpty())throw new IOException("Пустая зависимость MAST");
                masters.add(name);
            }else if("CNAM".equals(type))author=txt(bytes,pos,n,256);
            else if("SNAM".equals(type))description=txt(bytes,pos,n,512);
            pos+=n;
        }
        if(extended>=0||!hasHedr)throw new IOException("Нет HEDR или XXXX без подзаписи");
        return new Result((int)size,u16(header,20),u32(header,8),version,count,nextId,author,description,masters);
    }
    private static byte[] exact(InputStream in,int length)throws IOException{
        byte[] b=new byte[length];int at=0;
        while(at<length){
            int n=in.read(b,at,length-at);
            if(n<0)throw new IOException("Файл TES4 обрезан");
            if(n==0){int one=in.read();if(one<0)throw new IOException("Файл TES4 обрезан");b[at++]=(byte)one;}
            else at+=n;
        }
        return b;
    }
    private static int u16(byte[] b,int at){return (b[at]&255)|((b[at+1]&255)<<8);}
    private static long u32(byte[] b,int at){return (u16(b,at)|((long)u16(b,at+2)<<16))&0xffffffffL;}
    private static String tag(byte[] b,int at){return new String(b,at,4,StandardCharsets.US_ASCII);}
    private static String txt(byte[] b,int at,int size,int max){
        int end=at;while(end<at+size&&end-at<max&&b[end]!=0)end++;
        return new String(b,at,end-at,TEXT).trim();
    }
}
