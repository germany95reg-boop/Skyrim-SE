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
    private static int[] channelBlock(byte[] src,int off) {
        int[] codes=alphaPalette(src[off]&255,src[off+1]&255),pixels=new int[16];
        long selector=0;
        for(int i=0;i<6;i++)selector|=((long)src[off+2+i]&255)<<(8*i);
        for(int i=0;i<16;i++)pixels[i]=codes[(int)((selector>>>(3*i))&7)];
        return pixels;
    }
    /** Safe CPU preview of one first mip. BC4/5 signed and BC7 need separate decoders. */
    public static Image decode(byte[] dds) throws IOException {
        if(dds==null||dds.length<128)throw new IOException("DDS: недостаточно данных");
        DdsHeaderInspector.Result h=DdsHeaderInspector.inspect(dds,dds.length);
        if(h.width>2048||h.height>2048||(long)h.width*h.height>MAX_PIXELS)
            throw new IOException("DDS: изображение больше безопасного предела 2048×2048");
        if(h.depth>1||h.arraySize!=1)throw new IOException("DDS: только одна плоская 2D-текстура");
        String f=h.fourCC;int dg=h.dxgiFormat;
        if("BC4S".equals(f)||"BC5S".equals(f)||
           (h.isDx10&&(dg==81||dg==84)))
            throw new IOException("Signed BC4/BC5 SNORM требует отдельного декодера");
        int kind=0;
        if("DXT1".equals(f)||(h.isDx10&&(dg==71||dg==72)))kind=1;
        else if("DXT3".equals(f)||(h.isDx10&&(dg==74||dg==75)))kind=2;
        else if("DXT5".equals(f)||(h.isDx10&&(dg==77||dg==78)))kind=3;
        else if("ATI1".equals(f)||"BC4U".equals(f)||(h.isDx10&&dg==80))kind=4;
        else if("ATI2".equals(f)||"BC5U".equals(f)||(h.isDx10&&dg==83))kind=5;
        else if(h.isDx10&&(dg==28||dg==29))kind=6;
        else if(h.isDx10&&(dg==87||dg==91))kind=7;
        if(kind==0)throw new IOException("Для формата "+h.format+" CPU-предпросмотр пока недоступен");
        int[] pixels=new int[h.width*h.height];
        if(kind>=6){
            long length=(long)h.width*h.height*4;
            if(h.headerBytes+length>dds.length)throw new IOException("DDS RGBA: первый mip обрезан");
            for(int i=0;i<pixels.length;i++){
                int at=h.headerBytes+4*i;
                int red=dds[at+(kind==7?2:0)]&255,green=dds[at+1]&255;
                int blue=dds[at+(kind==7?0:2)]&255,alpha=dds[at+3]&255;
                pixels[i]=(alpha<<24)|(red<<16)|(green<<8)|blue;
            }
            return new Image(h.width,h.height,pixels,kind==6?"RGBA8":"BGRA8");
        }
        int bytesPerBlock=(kind==1||kind==4)?8:16;
        long bxCount=((long)h.width+3)/4,byCount=((long)h.height+3)/4;
        long need=bxCount*byCount*bytesPerBlock;
        if(h.headerBytes+need>dds.length)
            throw new IOException("DDS: данные первого mip-уровня обрезаны");
        for(int by=0;by<(int)byCount;by++)for(int bx=0;bx<(int)bxCount;bx++){
            int off=h.headerBytes+(int)(((long)by*bxCount+bx)*bytesPerBlock);
            int[] colors=null,first=null,second=null;long selectors=0;
            if(kind<=3){
                int colorOff=off+(kind==1?0:8);
                colors=palette(dds,colorOff,kind!=1);
                selectors=u32(dds,colorOff+4);
                if(kind==3)first=channelBlock(dds,off);
            }else{
                first=channelBlock(dds,off);
                if(kind==5)second=channelBlock(dds,off+8);
            }
            for(int i=0;i<16;i++){
                int x=bx*4+i%4,y=by*4+i/4;
                if(x>=h.width||y>=h.height)continue;
                int pixel;
                if(kind<=3){
                    pixel=colors[(int)((selectors>>>(2*i))&3)];
                    if(kind==2){
                        int nibble=((dds[off+i/2]&255)>>>((i%2)*4))&15;
                        pixel=(pixel&0x00ffffff)|((nibble*17)<<24);
                    }else if(kind==3)pixel=(pixel&0x00ffffff)|(first[i]<<24);
                }else if(kind==4){
                    int v=first[i];pixel=0xff000000|(v<<16)|(v<<8)|v;
                }else{
                    // Preview shows unsigned R/G channels; blue is a visualization aid.
                    pixel=0xff0000ff|(first[i]<<16)|(second[i]<<8);
                }
                pixels[y*h.width+x]=pixel;
            }
        }
        String label=kind==1?"BC1/DXT1":kind==2?"BC2/DXT3":
          kind==3?"BC3/DXT5":kind==4?"BC4 grayscale":"BC5 RG channels";
        return new Image(h.width,h.height,pixels,label);
    }
}
