package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Small, read-only TES5 REFR/ACHR subrecord inspector.
 * NAME is an unresolved base FormID; DATA is position (xyz) + rotation (radians).
 * Compressed record data MUST be handled by the caller, not sent to this parser.
 */
public final class Tes4PlacementSubrecordReader {
    public static final int MAX_BODY=65536;
    private Tes4PlacementSubrecordReader(){}

    public static final class Placement {
        public final String type;
        public final long recordFormId, baseFormId;
        public final boolean hasBase, hasPose;
        public final float x,y,z,rx,ry,rz;
        private Placement(String type,long formId,long base,boolean hasBase,boolean hasPose,
                          float x,float y,float z,float rx,float ry,float rz) {
            this.type=type;this.recordFormId=formId;this.baseFormId=base;
            this.hasBase=hasBase;this.hasPose=hasPose;
            this.x=x;this.y=y;this.z=z;this.rx=rx;this.ry=ry;this.rz=rz;
        }
        public boolean complete(){return hasBase&&hasPose;}
        public String format(){
            return String.format(Locale.ROOT,
                "%s 0x%08X → NAME 0x%08X · xyz [%.2f, %.2f, %.2f] · поворот [%.3f, %.3f, %.3f] рад",
                type,recordFormId,baseFormId,x,y,z,rx,ry,rz);
        }
    }

    /** Strict subrecord bounds, no resolution of FormIDs, no asset loading. */
    public static Placement inspect(String recordType,long formId,byte[] payload)throws IOException{
        if(!"REFR".equals(recordType)&&!"ACHR".equals(recordType))
            throw new IOException("Ожидалась размещённая запись REFR / ACHR");
        if(payload==null||payload.length>MAX_BODY)
            throw new IOException("Подзапись слишком велика");
        long base=0,extended=-1;
        boolean hasBase=false,hasPose=false;
        float x=0,y=0,z=0,rx=0,ry=0,rz=0;
        int pos=0, fields=0;
        while(pos<payload.length){
            if(++fields>8192||payload.length-pos<6)
                throw new IOException("Обрезанный заголовок подзаписи");
            String key=new String(payload,pos,4,StandardCharsets.US_ASCII);
            int n=u16(payload,pos+4);
            pos+=6;
            if("XXXX".equals(key)){
                if(n!=4||extended>=0||payload.length-pos<4)
                    throw new IOException("Неверный XXXX");
                extended=u32(payload,pos);
                if(extended>MAX_BODY)throw new IOException("Расширенное поле превышает лимит");
                pos+=4;
                continue;
            }
            long actual=extended>=0?extended:n;
            extended=-1;
            if(actual>payload.length-pos)throw new IOException("Подзапись за границами REFR/ACHR");
            int bytes=(int)actual;
            if("NAME".equals(key)){
                if(bytes!=4||hasBase)throw new IOException("Некорректный или повторный NAME");
                base=u32(payload,pos);hasBase=true;
            }else if("DATA".equals(key)){
                if(bytes!=24||hasPose)throw new IOException("Некорректный или повторный DATA");
                x=f32(payload,pos);y=f32(payload,pos+4);z=f32(payload,pos+8);
                rx=f32(payload,pos+12);ry=f32(payload,pos+16);rz=f32(payload,pos+20);
                if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)
                   ||!Float.isFinite(rx)||!Float.isFinite(ry)||!Float.isFinite(rz))
                    throw new IOException("Недопустимые координаты или вращение");
                hasPose=true;
            }
            pos+=bytes;
        }
        if(extended>=0)throw new IOException("XXXX не сопровождается полем");
        return new Placement(recordType,formId,base,hasBase,hasPose,x,y,z,rx,ry,rz);
    }

    private static int u16(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8);}
    private static long u32(byte[] b,int p){
        return (b[p]&255L)|((b[p+1]&255L)<<8)|((b[p+2]&255L)<<16)|((b[p+3]&255L)<<24);
    }
    private static float f32(byte[] b,int p){
        return Float.intBitsToFloat((int)u32(b,p));
    }
}
