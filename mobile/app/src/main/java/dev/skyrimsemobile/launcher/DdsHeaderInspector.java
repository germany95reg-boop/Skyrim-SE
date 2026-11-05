package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Strict, read-only DDS header inspector. No pixel decoding or file writes. */
public final class DdsHeaderInspector {
    public static final int BASE_BYTES = 128, DX10_BYTES = 148;
    private static final long MAX_DIMENSION = 16384;
    private DdsHeaderInspector() {}

    public static final class Result {
        public final int width, height, mipMaps, depth, arraySize, headerBytes;
        public final String format, fourCC;
        public final int dxgiFormat;
        public final long bytesDeclared, minimumBytesForMips;
        public final boolean blockCompressed, isDx10;
        Result(int width,int height,int mipMaps,int depth,int arraySize,int headerBytes,
                String format,String fourCC,int dxgi,long total,long minimum,
                boolean compressed,boolean dx10) {
            this.width=width;this.height=height;this.mipMaps=mipMaps;this.depth=depth;
            this.arraySize=arraySize;this.headerBytes=headerBytes;this.format=format;
            this.fourCC=fourCC;this.dxgiFormat=dxgi;bytesDeclared=total;
            minimumBytesForMips=minimum;blockCompressed=compressed;isDx10=dx10;
        }
        public String report() {
            return "DDS " + width + " × " + height + " · " + format +
                "\nMip-уровней: " + mipMaps + " · слоёв: " + arraySize +
                " · заголовок: " + headerBytes + " Б" +
                "\nДлина DDS по записи BSA: " + bytesDeclared + " Б" +
                (isDx10 ? " · DXGI=" + dxgiFormat : " · FourCC=" + fourCC) +
                (minimumBytesForMips>0?"\nМинимум для mip-уровней: " + minimumBytesForMips + " Б":"") +
                "\nПиксели DDS не декодировались; данные игры не изменены.";
        }
    }
    private static long u32(byte[] b,int off) {
        return ((long)b[off]&255)|(((long)b[off+1]&255)<<8)|(((long)b[off+2]&255)<<16)|(((long)b[off+3]&255)<<24);
    }
    private static int checkedInt(long n,String what) throws IOException {
        if(n>Integer.MAX_VALUE)throw new IOException(what+": значение слишком велико");return (int)n;
    }
    public static Result inspect(byte[] prefix,long decodedLength) throws IOException {
        if(prefix==null||prefix.length<BASE_BYTES||decodedLength<BASE_BYTES)
            throw new IOException("DDS слишком короткий: требуется не менее 128 байт");
        if(prefix[0]!='D'||prefix[1]!='D'||prefix[2]!='S'||prefix[3]!=' ')
            throw new IOException("Не DDS: неверная сигнатура");
        if(u32(prefix,4)!=124L||u32(prefix,76)!=32L)
            throw new IOException("Неверный DDS_HEADER или DDS_PIXELFORMAT");
        int h=checkedInt(u32(prefix,12),"Высота"),w=checkedInt(u32(prefix,16),"Ширина");
        int depth=checkedInt(u32(prefix,24),"Глубина"),mip=checkedInt(u32(prefix,28),"Mip maps");
        if(w<1||h<1||w>MAX_DIMENSION||h>MAX_DIMENSION||depth>MAX_DIMENSION)
            throw new IOException("DDS: недопустимые размеры изображения");
        if(mip==0)mip=1;
        if(mip>16)throw new IOException("DDS: слишком много mip-уровней");
        long flags=u32(prefix,80);
        boolean hasFourCC=(flags&0x4)!=0;
        String fourCC=hasFourCC?new String(prefix,84,4,StandardCharsets.US_ASCII):"RGB";
        boolean dx10=hasFourCC&&"DX10".equals(fourCC);
        int header=dx10?DX10_BYTES:BASE_BYTES;
        if(prefix.length<header||decodedLength<header)
            throw new IOException("DDS DX10: отсутствует расширенный заголовок 20 байт");
        int dxgi=dx10?checkedInt(u32(prefix,128),"DXGI format"):-1;
        int arrays=dx10?checkedInt(u32(prefix,140),"Array size"):1;
        if(arrays<1||arrays>256)throw new IOException("DDS: недопустимое количество слоёв");
        int blockBytes=0;String fmt="Неизвестный формат";
        if(dx10) {
            switch(dxgi) {
                case 71:case 72:fmt="BC1";blockBytes=8;break;
                case 74:case 75:fmt="BC2";blockBytes=16;break;
                case 77:case 78:fmt="BC3";blockBytes=16;break;
                case 80:case 81:fmt="BC4";blockBytes=8;break;
                case 83:case 84:fmt="BC5";blockBytes=16;break;
                case 95:case 96:fmt="BC6H";blockBytes=16;break;
                case 98:case 99:fmt="BC7";blockBytes=16;break;
                case 28:case 29:fmt="RGBA8";break;
                case 87:case 91:fmt="BGRA8";break;
                default:fmt="DXGI #"+dxgi;break;
            }
        } else if(hasFourCC) {
            switch(fourCC) {
                case "DXT1":fmt="BC1 / DXT1";blockBytes=8;break;
                case "DXT3":fmt="BC2 / DXT3";blockBytes=16;break;
                case "DXT5":fmt="BC3 / DXT5";blockBytes=16;break;
                case "ATI1":case "BC4U":case "BC4S":fmt="BC4";blockBytes=8;break;
                case "ATI2":case "BC5U":case "BC5S":fmt="BC5";blockBytes=16;break;
                default:fmt="FourCC "+fourCC.replaceAll("[^A-Za-z0-9]","?");break;
            }
        } else {
            int bits=checkedInt(u32(prefix,88),"RGB bit count");
            if(bits==32)fmt="RGBA/BGRA 32-bit";
            else if(bits==24)fmt="RGB 24-bit";
            else fmt="Без FourCC, "+bits+" бит/пиксель";
        }
        long min=0;
        if(blockBytes>0) {
            int mw=w,mh=h;
            for(int i=0;i<mip;i++) {
                min+=((mw+3L)/4)*((mh+3L)/4)*blockBytes;
                mw=Math.max(1,mw/2);mh=Math.max(1,mh/2);
            }
            min*=arrays;
            if(decodedLength-header<min)
                throw new IOException("DDS: данных меньше размера заявленных BC-mip уровней: "+
                    (decodedLength-header)+" / "+min);
        }
        return new Result(w,h,mip,depth,arrays,header,fmt,fourCC,dxgi,
            decodedLength,min,blockBytes>0,dx10);
    }
}
