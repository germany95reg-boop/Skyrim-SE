package dev.skyrimsemobile.launcher;

import java.io.IOException;

/** Safe CPU preview of the first mip of BC1/DXT1 and BC3/DXT5 DDS files; no file writes. */
public final class DdsBcDecoder {
    public static final int MAX_PIXELS=2048*2048;
    private DdsBcDecoder() {}

    public static final class Image {
        public final int width,height;
        /** Android ARGB_8888 pixels, stored independently of android.graphics for JVM tests. */
        public final int[] argb;
        public final String format;
        Image(int w,int h,int[] pixels,String format) {width=w;height=h;argb=pixels;this.format=format;}
    }

    private static int u16(byte[] b,int off) {return (b[off]&255)|((b[off+1]&255)<<8);}
    private static long u32(byte[] b,int off) {return ((long)b[off]&255)|(((long)b[off+1]&255)<<8)|(((long)b[off+2]&255)<<16)|(((long)b[off+3]&255)<<24);}
    private static int rgb565(int c) {
        int r=(c>>11)&31,g=(c>>5)&63,b=c&31;
        return 0xff000000 | (((r<<3)|(r>>2))<<16) | (((g<<2)|(g>>4))<<8) | ((b<<3)|(b>>2));
    }
    private static int lerp(int a,int b,int wA,int wB,int divisor) {
        int ar=(a>>16)&255,ag=(a>>8)&255,ab=a&255;
        int br=(b>>16)&255,bg=(b>>8)&255,bb=b&255;
        return 0xff000000|(((wA*ar+wB*br)/divisor)<<16)|(((wA*ag+wB*bg)/divisor)<<8)|((wA*ab+wB*bb)/divisor);
    }
    private static int[] palette(byte[] src,int off,boolean bc3) {
        int c0=u16(src,off),c1=u16(src,off+2);
        int[] p=new int[4];p[0]=rgb565(c0);p[1]=rgb565(c1);
        if(c0>c1||bc3) {
            p[2]=lerp(p[0],p[1],2,1,3);
            p[3]=lerp(p[0],p[1],1,2,3);
        }else {
            p[2]=lerp(p[0],p[1],1,1,2);
            p[3]=0x00000000;
        }
        return p;
    }
    private static int[] alphaPalette(int a0,int a1){
        int[] a=new int[8];a[0]=a0;a[1]=a1;
        if(a0>a1) {
            for(int i=1;i<=6;i++)a[i+1]=((7-i)*a0+i*a1)/7;
        }else {
            for(int i=1;i<=4;i++)a[i+1]=((5-i)*a0+i*a1)/5;
            a[6]=0;a[7]=255;
        }
        return a;
    }
    /** Decodes one first-mip image into pixels, requiring valid DDS header and complete first mip. */
    public static Image decode(byte[] dds) throws IOException {
        if(dds==null||dds.length<128)throw new IOException("DDS: недостаточно данных");
        DdsHeaderInspector.Result h=DdsHeaderInspector.inspect(dds,dds.length);
        if(h.width>2048||h.height>2048||(long)h.width*h.height>MAX_PIXELS)
            throw new IOException("DDS: изображение больше безопасного предела 2048×2048");
        if(h.depth>1||h.arraySize!=1)throw new IOException("DDS: только одна 2D-текстура");
        boolean bc1="DXT1".equals(h.fourCC)||(h.isDx10&&(h.dxgiFormat==71||h.dxgiFormat==72));
        boolean bc3="DXT5".equals(h.fourCC)||(h.isDx10&&(h.dxgiFormat==77||h.dxgiFormat==78));
        if(!bc1&&!bc3)throw new IOException("Предпросмотр доступен только для BC1/DXT1 и BC3/DXT5");
        int bytesPerBlock=bc1?8:16;
        long blocksX=((long)h.width+3)/4,blocksY=((long)h.height+3)/4;
        long firstMip=blocksX*blocksY*bytesPerBlock;
        if(firstMip>Integer.MAX_VALUE||h.headerBytes+firstMip>dds.length)
            throw new IOException("DDS: данные первого mip-уровня обрезаны");
        int[] pixels=new int[h.width*h.height];
        for(int by=0;by<(int)blocksY;by++)for(int bx=0;bx<(int)blocksX;bx++){
            int off=h.headerBytes+(int)((by*blocksX+bx)*bytesPerBlock);
            int colorsOffset=off+(bc3?8:0);
            int[] p=palette(dds,colorsOffset,bc3);
            long selections=u32(dds,colorsOffset+4);
            int[] alphas=null;long alphaSelection=0;
            if(bc3){
                alphas=alphaPalette(dds[off]&255,dds[off+1]&255);
                for(int i=0;i<6;i++)alphaSelection|=((long)dds[off+2+i]&255)<<(8*i);
            }
            for(int i=0;i<16;i++){
                int xx=bx*4+i%4,yy=by*4+i/4;
                if(xx>=h.width||yy>=h.height)continue;
                int value=p[(int)((selections>>(2*i))&3)];
                if(bc3)value=(value&0x00ffffff)|(alphas[(int)((alphaSelection>>(3*i))&7)]<<24);
                pixels[yy*h.width+xx]=value;
            }
        }
        return new Image(h.width,h.height,pixels,bc1?"BC1/DXT1":"BC3/DXT5");
    }
}
