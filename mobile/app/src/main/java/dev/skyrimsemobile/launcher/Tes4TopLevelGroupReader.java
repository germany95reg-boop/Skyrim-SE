package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Read-only directory of TOP-LEVEL Skyrim TES4 GRUPs.
 * Does not recursively inspect record contents, decode compressed records,
 * resolve FormIDs, or load a playable world.
 */
public final class Tes4TopLevelGroupReader {
    private static final int GROUP_HEADER_BYTES=24;
    private static final int MAX_TOP_GROUPS=512;

    private Tes4TopLevelGroupReader(){}

    public static final class Group {
        public final String label;
        public final long fileOffset, byteLength;
        Group(String label,long fileOffset,long byteLength) {
            this.label=label;this.fileOffset=fileOffset;this.byteLength=byteLength;
        }
    }

    public static final class Result {
        public final float hedrVersion;
        public final long declaredRecords, indexedBytes, knownFileBytes;
        public final List<Group> groups;
        Result(float v,long count,long bytes,long fileBytes,List<Group> groups) {
            hedrVersion=v;declaredRecords=count;indexedBytes=bytes;knownFileBytes=fileBytes;
            this.groups=Collections.unmodifiableList(new ArrayList<>(groups));
        }
        public boolean hasType(String label) {
            for(Group group:groups)if(group.label.equals(label))return true;
            return false;
        }
        @Override public String toString() {
            StringBuilder out=new StringBuilder("Индекс TES4 · верхние группы GRUP\n");
            out.append("HEDR: ").append(hedrVersion)
               .append(" · записей заявлено: ").append(declaredRecords).append('\n');
            out.append("Верхних GRUP: ").append(groups.size())
               .append(" · обработано (по смещениям): ").append(indexedBytes).append(" байт\n");
            if(knownFileBytes<0)out.append("Размер файла не предоставлен: границы проверены по потоку.\n");
            else out.append("Размер файла по Android: ").append(knownFileBytes).append(" байт\n");
            out.append("Раздел WRLD: ").append(hasType("WRLD")?"найден":"не найден")
               .append(" · CELL: ").append(hasType("CELL")?"найден":"не найден").append('\n');
            for(Group g:groups) {
                out.append("• ").append(g.label)
                   .append(" · смещение ").append(g.fileOffset)
                   .append(" · размер ").append(g.byteLength).append(" байт\n");
            }
            out.append("\nЭто только индекс верхних GRUP; вложенные CELL/WRLD/REFR,");
            out.append(" игровые записи и запуск SkyrimSE.exe пока не реализованы.");
            return out.toString();
        }
    }

    /**
     * @param fileBytes Android Storage Access Framework size, or -1 if unknown.
     *                  A known size guards GRUP against out-of-file boundaries.
     */
    public static Result scan(InputStream in,long fileBytes)throws IOException {
        if(in==null)throw new IOException("Файл ESM не открыт");
        if(fileBytes< -1)throw new IOException("Неверный размер файла");
        Tes4PluginHeaderReader.Result plugin=Tes4PluginHeaderReader.inspect(in);
        long pos=24L+plugin.headerBytes;
        if(fileBytes>=0&&pos>fileBytes)throw new IOException("TES4 выходит за границы файла");
        List<Group> groups=new ArrayList<>();
        for(;;) {
            if(fileBytes>=0 && pos==fileBytes)break;
            if(groups.size()>=MAX_TOP_GROUPS)throw new IOException("Превышен лимит верхних GRUP");
            byte[] h=readHeaderOrEnd(in);
            if(h==null) {
                if(fileBytes>=0 && pos!=fileBytes)
                    throw new IOException("Файл ESM обрезан относительно заявленного размера");
                break;
            }
            if(!"GRUP".equals(tag(h,0)))throw new IOException("Ожидалась верхняя GRUP на смещении "+pos);
            long bytes=u32(h,4);
            if(bytes<GROUP_HEADER_BYTES)throw new IOException("Неправильный размер GRUP: "+bytes);
            long groupType=u32(h,12);
            if(groupType!=0)throw new IOException("Ожидалась GRUP типа 0; найден "+groupType);
            if(fileBytes>=0&&bytes>fileBytes-pos)throw new IOException("GRUP выходит за границы файла");
            String label=tag(h,8);
            for(int i=0;i<label.length();i++){
                char c=label.charAt(i);
                if(c<32||c>126)throw new IOException("Некорректная метка GRUP");
            }
            groups.add(new Group(label,pos,bytes));
            skipExactly(in,bytes-GROUP_HEADER_BYTES);
            pos+=bytes;
        }
        return new Result(plugin.hedrVersion,plugin.recordCount,pos,fileBytes,groups);
    }

    private static byte[] readHeaderOrEnd(InputStream in)throws IOException {
        int first=in.read();
        if(first<0)return null;
        byte[] h=new byte[GROUP_HEADER_BYTES];
        h[0]=(byte)first;
        int at=1;
        while(at<h.length){
            int got=in.read(h,at,h.length-at);
            if(got<0)throw new IOException("Обрезанный заголовок GRUP");
            if(got==0){
                int c=in.read();
                if(c<0)throw new IOException("Обрезанный заголовок GRUP");
                h[at++]=(byte)c;
            }else at+=got;
        }
        return h;
    }
    private static void skipExactly(InputStream in,long bytes)throws IOException {
        byte[] scratch=new byte[8192];
        while(bytes>0){
            long skipped=in.skip(bytes);
            if(skipped>0){
                bytes-=skipped;
                continue;
            }
            int count=(int)Math.min(bytes,scratch.length);
            int got=in.read(scratch,0,count);
            if(got<=0)throw new IOException("Обрезанное содержимое GRUP");
            bytes-=got;
        }
    }
    private static long u32(byte[] b,int pos) {
        return ((b[pos]&255L)|((b[pos+1]&255L)<<8)|((b[pos+2]&255L)<<16)|((b[pos+3]&255L)<<24));
    }
    private static String tag(byte[] b,int pos) {
        return new String(b,pos,4,StandardCharsets.US_ASCII);
    }
}
