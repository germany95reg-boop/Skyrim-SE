package dev.skyrimsemobile.launcher;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Safe, read-only BSA v105 metadata indexer. This class never seeks to, reads, or
 * decompresses asset payloads. It consumes only the header, folder records,
 * file-record table, and file-name table from the supplied InputStream.
 *
 * Supports conventional archives with both folder and file names present.
 */
public final class BsaCatalogReader {
    private static final int MAX_FOLDERS = 20000;
    private static final int MAX_FILES = 250000;
    private static final int MAX_INDEXED = 60000;
    private static final int MAX_NAME_BYTES = 1024;
    private static final long MAX_METADATA_BYTES = 64L * 1024 * 1024;
    private static final int HEADER_BYTES = 36;
    private BsaCatalogReader() {}

    public static final class Entry {
        public final String path;
        public final long storedBytes;
        public final long offset;
        public final boolean compressed;
        Entry(String path, long storedBytes, long offset, boolean compressed) {
            this.path=path;this.storedBytes=storedBytes;this.offset=offset;this.compressed=compressed;
        }
        public boolean nif() { return path.toLowerCase(Locale.ROOT).endsWith(".nif"); }
    }
    public static final class Catalog {
        public final int folderCount, fileCount, indexedCount, nifCount;
        public final long flags;
        public final boolean partialIndex;
        public final List<Entry> entries;
        Catalog(int folders, int files, int indexed, int nifs, long flags, List<Entry> entries){
            folderCount=folders;fileCount=files;indexedCount=indexed;nifCount=nifs;this.flags=flags;
            this.entries=Collections.unmodifiableList(entries);partialIndex=indexed<files;
        }
        /** Search only the indexed prefix; never claim coverage beyond indexedCount. */
        public List<Entry> find(String query, boolean onlyNif, int maxResults){
            String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
            List<Entry> matches=new ArrayList<>();
            for(Entry e:entries){
                if(onlyNif&&!e.nif())continue;
                if(!q.isEmpty()&&!e.path.toLowerCase(Locale.ROOT).contains(q))continue;
                matches.add(e);
                if(matches.size()>=Math.max(1, Math.min(maxResults, 100)))break;
            }
            return matches;
        }
    }
    private static final class RawEntry {
        final String folder;
        final long size, offset;
        final boolean compressed;
        RawEntry(String f,long s,long o,boolean c){folder=f;size=s;offset=o;compressed=c;}
    }
    public static Catalog read(InputStream source) throws IOException {
        if(source==null)throw new IOException("Нет доступа к файлу BSA");
        BufferedInputStream in=new BufferedInputStream(source,65536);
        byte[] header=new byte[HEADER_BYTES];readFully(in,header);
        if(header[0]!='B'||header[1]!='S'||header[2]!='A'||header[3]!=0)
            throw new IOException("Не BSA: неверная сигнатура");
        long ver=u32(header,4), headerOffset=u32(header,8), flags=u32(header,12);
        long folderCountLong=u32(header,16),fileCountLong=u32(header,20);
        long folderNameBytes=u32(header,24),fileNameBytes=u32(header,28);
        if(ver!=105)throw new IOException("Поддерживается каталог BSA версии 105; версия файла: "+ver);
        if(headerOffset<HEADER_BYTES||headerOffset>4096)throw new IOException("Некорректное смещение каталога");
        if((flags&3)!=3)throw new IOException("Архив не содержит доступных имён папок и файлов (флаги 0x"+Long.toHexString(flags)+")");
        if((flags&0x40)!=0)throw new IOException("Неподдерживаемый порядок байтов BSA");
        if(folderCountLong>MAX_FOLDERS||fileCountLong>MAX_FILES)
            throw new IOException("Слишком большой каталог; защитное ограничение: 20000 папок / 250000 файлов");
        if(folderNameBytes>MAX_METADATA_BYTES||fileNameBytes>MAX_METADATA_BYTES)
            throw new IOException("Таблица имён слишком большая");
        final int folderCount=(int)folderCountLong,fileCount=(int)fileCountLong;
        long metadata=headerOffset+folderCountLong*24L+folderCountLong+folderNameBytes+fileCountLong*16L+fileNameBytes;
        if(metadata>MAX_METADATA_BYTES)throw new IOException("Каталог превышает 64 МиБ — чтение остановлено");
        skipExactly(in,headerOffset-HEADER_BYTES);
        int[] counts=new int[folderCount];
        long sum=0;
        byte[] buf24=new byte[24];
        for(int i=0;i<folderCount;i++){
            readFully(in,buf24);
            long n=u32(buf24,8);
            sum+=n;
            if(sum>fileCountLong)throw new IOException("Количество записей папок превышает размер архива");
            counts[i]=(int)n;
            // v105: hash(8), count(4), unknown(4), offset(8).
            // The embedded offsets are not needed for sequential metadata reading.
        }
        if(sum!=fileCountLong)throw new IOException("Несоответствие общего числа файлов в таблице BSA");
        List<RawEntry> raw=new ArrayList<>(Math.min(fileCount,MAX_INDEXED));
        byte[] fileRecord=new byte[16];
        for(int i=0;i<folderCount;i++){
            int len=in.read();
            if(len<0)throw new IOException("Неожиданный конец на имени папки");
            if(len>MAX_NAME_BYTES)throw new IOException("Имя папки слишком длинное");
            byte[] folderBytes=new byte[len];
            readFully(in,folderBytes);
            String folder=safeName(folderBytes);
            for(int j=0;j<counts[i];j++){
                readFully(in,fileRecord);
                if(raw.size()<MAX_INDEXED){
                    long sizeWord=u32(fileRecord,8);
                    boolean compressionByDefault=(flags&4)!=0;
                    boolean toggled=(sizeWord&0x40000000L)!=0;
                    raw.add(new RawEntry(folder,sizeWord&0x3fffffffL,u32(fileRecord,12),compressionByDefault^toggled));
                }
            }
        }
        // Read each NUL-terminated file name in folder-record order, not payload data.
        // File names are counted even when the user-facing list is capped.
        long consumed=0;
        List<Entry> entries=new ArrayList<>(raw.size());
        int nifCount=0;
        for(int i=0;i<fileCount;i++){
            ByteArrayOutputStream name=new ByteArrayOutputStream(48);
            boolean ended=false;
            while(consumed<fileNameBytes){
                int b=in.read();
                if(b<0)throw new IOException("Обрезана таблица имён BSA");
                consumed++;
                if(b==0){ended=true;break;}
                if(name.size()>=MAX_NAME_BYTES)throw new IOException("Слишком длинное имя файла в BSA");
                name.write(b);
            }
            if(!ended)throw new IOException("Таблица имён не содержит требуемое число записей");
            String file=safeName(name.toByteArray());
            if(file.toLowerCase(Locale.ROOT).endsWith(".nif"))nifCount++;
            if(i<raw.size()){
                RawEntry re=raw.get(i);
                String path=re.folder.isEmpty()?file:re.folder+"/"+file;
                entries.add(new Entry(path,re.size,re.offset,re.compressed));
            }
        }
        // Some BSA name tables include a few bytes of zero padding after their
        // last NUL-terminated filename. Permit up to 16 actual zero bytes only,
        // and never treat non-zero or missing trailing bytes as valid metadata.
        if(consumed!=fileNameBytes){
            long pad=fileNameBytes-consumed;
            if(pad<1||pad>16)
                throw new IOException("Длина таблицы имён не соответствует заголовку: прочитано "+
                    consumed+" / заявлено "+fileNameBytes+" байт; возможен нестандартный вариант BSA");
            for(long i=0;i<pad;i++){
                int next=in.read();
                if(next!=0)
                    throw new IOException("Длина таблицы имён не соответствует заголовку: прочитано "+
                        consumed+" / заявлено "+fileNameBytes+
                        " байт; ненулевой или отсутствующий завершающий блок BSA");
            }
        }
        return new Catalog(folderCount,fileCount,entries.size(),nifCount,flags,entries);
    }
    private static String safeName(byte[] bytes){
        int len=bytes.length;
        while(len>0&&bytes[len-1]==0)len--;
        String s=new String(bytes,0,len,StandardCharsets.UTF_8).replace('\\','/');
        StringBuilder out=new StringBuilder(s.length());
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            out.append(c>=32&&c!=127?c:'_');
        }
        return out.toString();
    }
    private static long u32(byte[] b,int p){
        return ((long)b[p]&255)|(((long)b[p+1]&255)<<8)|(((long)b[p+2]&255)<<16)|(((long)b[p+3]&255)<<24);
    }
    private static void readFully(InputStream in,byte[] b)throws IOException{
        int p=0;
        while(p<b.length){
            int n=in.read(b,p,b.length-p);
            if(n<0)throw new IOException("Неожиданный конец файла BSA");
            if(n==0){int next=in.read();if(next<0)throw new IOException("Неожиданный конец файла BSA");b[p++]=(byte)next;}
            else p+=n;
        }
    }
    private static void skipExactly(InputStream in,long bytes)throws IOException{
        while(bytes>0){long skipped=in.skip(bytes);
            if(skipped>0)bytes-=skipped;
            else if(in.read()<0)throw new IOException("Неожиданный конец файла BSA");
            else bytes--;
        }
    }
}
