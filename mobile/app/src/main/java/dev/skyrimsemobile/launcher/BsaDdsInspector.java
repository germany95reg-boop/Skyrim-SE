package dev.skyrimsemobile.launcher;

import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.xxhash.XXHashFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Reads precisely one BSA record, storing at most the 148-byte DDS header. */
public final class BsaDdsInspector {
    private static final long MAX_PACKED=8L*1024*1024,MAX_DECODED=16L*1024*1024;
    private BsaDdsInspector() {}
    public static final class Result {
        public final String path;
        public final long offset,recordBytes,decodedBytes;
        public final boolean bsaCompressed;
        public final DdsHeaderInspector.Result dds;
        Result(BsaCatalogReader.Entry e,long unpacked,DdsHeaderInspector.Result d){
            path=e.path;offset=e.offset;recordBytes=e.storedBytes;decodedBytes=unpacked;
            bsaCompressed=e.compressed;dds=d;
        }
        public String report(){
            return "Ресурс: "+path+"\nСмещение BSA: 0x"+Long.toHexString(offset)+
                " · запись: "+recordBytes+" Б"+
                "\nСжатие записи BSA: "+(bsaCompressed?"LZ4 Frame":"нет")+"\n"+dds.report();
        }
    }
    public static Result inspect(InputStream in,BsaCatalogReader.Entry entry,long flags) throws IOException {
        if(in==null||entry==null)throw new IOException("Архив или DDS недоступны");
        DdsArchiveFinder.normalizeDds(entry.path); // only safe DDS paths
        if(entry.offset<36||entry.storedBytes<(entry.compressed?8:128)||entry.storedBytes>MAX_PACKED)
            throw new IOException("DDS: запись BSA вне ограничений размера (128 Б–8 МиБ)");
        if((flags&0x40L)!=0)throw new IOException("BSA: неподдерживаемый порядок байтов");
        skipExactly(in,entry.offset);
        long remain=entry.storedBytes;
        if((flags&0x100L)!=0) {
            int n=in.read();if(n<0)throw new IOException("BSA: отсутствует длина встроенного имени");
            remain--;if(n>remain)throw new IOException("BSA: встроенное имя обрезано");
            skipExactly(in,n);remain-=n;
        }
        if(!entry.compressed){
            if(remain<128)throw new IOException("DDS слишком короткий");
            int toRead=(int)Math.min(remain,148L);
            byte[] prefix=new byte[toRead];readFully(in,prefix);
            return new Result(entry,remain,DdsHeaderInspector.inspect(prefix,remain));
        }
        if(remain<8)throw new IOException("BSA: обрезана сжатая DDS-запись");
        byte[] expectedBytes=new byte[4];readFully(in,expectedBytes);remain-=4;
        long expected=((long)expectedBytes[0]&255)|(((long)expectedBytes[1]&255)<<8)|
            (((long)expectedBytes[2]&255)<<16)|(((long)expectedBytes[3]&255)<<24);
        if(expected<128||expected>MAX_DECODED)
            throw new IOException("Размер распакованной DDS вне пределов (128 Б–16 МиБ)");
        // A bounded stream prevents the LZ4 decoder from reading the next archive entry.
        try(LZ4FrameInputStream unpacked=new LZ4FrameInputStream(new Bounded(in,remain),
              LZ4Factory.fastestJavaInstance().safeDecompressor(),
              XXHashFactory.fastestJavaInstance().hash32(),true)) {
            byte[] prefix=new byte[(int)Math.min(148L,expected)];
            byte[] chunk=new byte[8192];int have=0;long total=0;
            while(true){int got=unpacked.read(chunk);if(got<0)break;
                if(got==0)continue;
                if(total+got>expected)throw new IOException("Распакованная DDS больше заявленного размера");
                int copy=(int)Math.min(got,Math.max(0,prefix.length-have));
                if(copy>0){System.arraycopy(chunk,0,prefix,have,copy);have+=copy;}
                total+=got;
            }
            if(total!=expected)throw new IOException("Неверный распакованный размер DDS: "+total+" / "+expected);
            return new Result(entry,expected,DdsHeaderInspector.inspect(prefix,expected));
        }catch(RuntimeException ex){throw new IOException("Ошибка распаковки DDS LZ4",ex);}
    }
    private static void readFully(InputStream in,byte[] b) throws IOException {
        int n=0;while(n<b.length){int k=in.read(b,n,b.length-n);
            if(k<0)throw new IOException("BSA: обрезанная запись DDS");
            if(k==0){int v=in.read();if(v<0)throw new IOException("BSA: обрезанная запись DDS");b[n++]=(byte)v;}
            else n+=k;
        }
    }
    private static void skipExactly(InputStream in,long count)throws IOException{
        while(count>0){long n=in.skip(count);if(n>0)count-=n;
            else if(in.read()<0)throw new IOException("BSA закончился до указанного смещения");
            else count--;
        }
    }
    static final class Bounded extends InputStream {
        final InputStream in;long remain;
        Bounded(InputStream in,long length){this.in=in;remain=length;}
        @Override public int read()throws IOException {if(remain==0)return -1;int v=in.read();if(v>=0)remain--;return v;}
        @Override public int read(byte[] b,int o,int n)throws IOException{
            if(remain==0)return -1;
            int z=in.read(b,o,(int)Math.min((long)n,remain));if(z>0)remain-=z;return z;
        }
        // Do not close the whole SAF stream separately when the decoder ends.
        @Override public void close(){}
    }
}
