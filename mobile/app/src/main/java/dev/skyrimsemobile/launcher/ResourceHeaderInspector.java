package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Read-only bounded format probe; does NOT extract or parse meshes. Pure Java. */
public final class ResourceHeaderInspector {
    private static final int MAX_HEADER=256;
    private ResourceHeaderInspector() {}
    public static final class Result {
        public final String format;
        public final String details;
        public final boolean recognized;
        public Result(String format,String details,boolean recognized){
            this.format=format; this.details=details; this.recognized=recognized;
        }
        @Override public String toString(){return format+" — "+details;}
    }
    public static Result inspect(InputStream stream,String displayName) throws IOException {
        if(stream==null) throw new IOException("Файл недоступен для чтения");
        byte[] h=new byte[MAX_HEADER];int n=0;
        while(n<h.length) {int r=stream.read(h,n,h.length-n);if(r<0)break;if(r==0) {int single=stream.read();if(single<0)break;h[n++]=(byte)single;}else n+=r;}
        String name=displayName==null?"":displayName.toLowerCase(Locale.ROOT);
        if(n>=4 && h[0]=='B' && h[1]=='S' && h[2]=='A' && h[3]==0){
            if(n<36)return new Result("BSA","Повреждённый или слишком короткий заголовок (меньше 36 байт)",false);
            long version=le32(h,4),offset=le32(h,8),flags=le32(h,12),folders=le32(h,16),files=le32(h,20);
            boolean valid=(version==103||version==104||version==105) && offset>=36 && offset<=65536 && folders<=1000000 && files<=10000000;
            if(!valid)return new Result("BSA","Подпись обнаружена, но поля заголовка подозрительны: версия "+version,false);
            String family=version==105?"Skyrim SE / VR":version==104?"Skyrim LE / Fallout 3":"Oblivion";
            return new Result("BSA", "версия "+version+" ("+family+"); каталогов "+folders+"; файлов "+files+"; флаги 0x"+Long.toHexString(flags)+". Данные не распакованы.",true);
        }
        if(n>=8 && h[0]=='B' && h[1]=='T' && h[2]=='D' && h[3]=='X'){
            long version=le32(h,4);
            return new Result("BA2","Архив BTDX, версия "+version+". Это отдельный формат, в v0.7 только определяется заголовок.",true);
        }
        if(n>=22){
            String prefix=new String(h,0,Math.min(n,128),StandardCharsets.US_ASCII);
            if(prefix.startsWith("Gamebryo File Format, Version ") || prefix.startsWith("NetImmerse File Format, Version ")){
                int end=prefix.indexOf('\n');
                if(end<0||end>110)return new Result("NIF","Заголовок NIF неполный",false);
                String head=prefix.substring(0,end).replace("\r","").trim();
                if(!head.matches("(Gamebryo|NetImmerse) File Format, Version [0-9]+(\\.[0-9]+){2,4}"))
                    return new Result("NIF","Необычная версия в заголовке: "+head,false);
                return new Result("NIF",head+". Геометрия и блоки NIF пока не декодируются.",true);
            }
        }
        return new Result("Неизвестно",name.endsWith(".nif")||name.endsWith(".bsa")?"Расширение знакомое, но заголовок не распознан":"Не BSA, BA2 или NIF",false);
    }
    private static long le32(byte[] h,int p){return ((long)h[p]&255)|(((long)h[p+1]&255)<<8)|(((long)h[p+2]&255)<<16)|(((long)h[p+3]&255)<<24);}
}
