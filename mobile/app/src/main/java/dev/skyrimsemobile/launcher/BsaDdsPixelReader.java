package dev.skyrimsemobile.launcher;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.xxhash.XXHashFactory;

/** Reads one explicitly selected DDS entry from a BSA (never writes the game's files). */
public final class BsaDdsPixelReader {
    private static final int MAX_PACKED=8*1024*1024,MAX_DECODED=16*1024*1024;
    private BsaDdsPixelReader(){}
    public static DdsBcDecoder.Image preview(InputStream archive,BsaCatalogReader.Entry entry,long flags)
            throws IOException { return DdsBcDecoder.decode(read(archive,entry,flags)); }

    public static byte[] read(InputStream archive,BsaCatalogReader.Entry entry,long flags) throws IOException {
        if(archive==null||entry==null)throw new IOException("BSA/DDS недоступен");
        DdsArchiveFinder.normalizeDds(entry.path);
        if(entry.offset<36||entry.storedBytes<(entry.compressed?8:128)||entry.storedBytes>MAX_PACKED)
            throw new IOException("BSA: DDS-запись превышает предел 8 МиБ или слишком мала");
        if((flags&0x40L)!=0)throw new IOException("BSA: неподдерживаемый порядок байтов");
        BufferedInputStream in=new BufferedInputStream(archive,65536);
        skip(in,entry.offset);
        byte[] stored=new byte[(int)entry.storedBytes];readFully(in,stored);
        int p=0;
        if((flags&0x100)!=0){int n=stored[0]&255;p=n+1;
            if(p>=stored.length)throw new IOException("BSA: обрезано встроенное имя DDS");}
        byte[] dds;
        if(entry.compressed){
            if(stored.length-p<8)throw new IOException("BSA: слишком короткая LZ4-запись");
            long size=((long)stored[p]&255)|(((long)stored[p+1]&255)<<8)|(((long)stored[p+2]&255)<<16)|(((long)stored[p+3]&255)<<24);
            p+=4;
            if(size<128||size>MAX_DECODED)throw new IOException("BSA: распакованная DDS превышает предел 16 МиБ");
            try(LZ4FrameInputStream unpacked=new LZ4FrameInputStream(
                new ByteArrayInputStream(stored,p,stored.length-p),
                LZ4Factory.fastestJavaInstance().safeDecompressor(),
                XXHashFactory.fastestJavaInstance().hash32(),true)){
                ByteArrayOutputStream result=new ByteArrayOutputStream((int)Math.min(size,65536));
                byte[] buf=new byte[8192];int n;
                while((n=unpacked.read(buf))!=-1){
                    if(n==0)continue;
                    if(result.size()+n>size)throw new IOException("BSA: LZ4 данных больше заявленного размера");
                    result.write(buf,0,n);
                }
                if(result.size()!=size)throw new IOException("BSA: LZ4 распаковано "+result.size()+" / "+size);
                dds=result.toByteArray();
            }catch(RuntimeException ex){throw new IOException("BSA: ошибка LZ4 для DDS",ex);}
        }else{
            int size=stored.length-p;if(size<128)throw new IOException("BSA: DDS-запись обрезана");
            dds=new byte[size];System.arraycopy(stored,p,dds,0,size);
        }
        // Validates declared dimensions, format, mip byte totals before rendering any pixels.
        DdsHeaderInspector.inspect(dds,dds.length);
        return dds;
    }
    private static void readFully(InputStream in,byte[] b)throws IOException{
        int n=0;while(n<b.length){int k=in.read(b,n,b.length-n);
            if(k<0)throw new IOException("BSA: запись DDS обрезана");
            if(k==0){int v=in.read();if(v<0)throw new IOException("BSA: запись DDS обрезана");b[n++]=(byte)v;}
            else n+=k;
        }
    }
    private static void skip(InputStream in,long n)throws IOException{
        while(n>0){long k=in.skip(n);if(k>0)n-=k;
            else if(in.read()<0)throw new IOException("BSA: неверное смещение DDS");else n--;}
    }
}
