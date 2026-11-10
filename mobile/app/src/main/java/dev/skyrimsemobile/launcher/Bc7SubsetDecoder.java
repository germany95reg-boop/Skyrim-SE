package dev.skyrimsemobile.launcher;

import java.io.IOException;

/**
 * Bounded CPU decode of BC7 modes 5 and 6 for DX10 DDS first-mip previews.
 * Mode layouts and anchor/fixup indices follow the Khronos BPTC specification.
 * Rejects unsupported modes instead of silently manufacturing texture colors.
 */
public final class Bc7SubsetDecoder {
    private Bc7SubsetDecoder(){}
    private static final int[] W4={0,4,9,13,17,21,26,30,34,38,43,47,51,55,60,64};
    private static final int[] W2={0,21,43,64};

    private static final class Bits {
        private final byte[] data;
        private final int start;
        private int at;
        Bits(byte[] data,int start){
            this.data=data;this.start=start;
        }
        int read(int count)throws IOException{
            if(count<0||count>8||at+count>128)
                throw new IOException("BC7: выход за пределы 128-битного блока");
            int value=0;
            for(int b=0;b<count;b++){
                int pos=at++;
                value|=((data[start+(pos>>>3)]>>>(pos&7))&1)<<b;
            }
            return value;
        }
    }
    private static int interpolate(int a,int b,int weight){
        return (a*(64-weight)+b*weight+32)>>>6;
    }
    private static int expand7(int v){return (v<<1)|(v>>>6);}
    /** Decodes exactly one BC7 4x4 block; texels stay row-major. */
    public static int[] decodeBlock(byte[] data,int offset)throws IOException{
        if(data==null||offset<0||offset>data.length-16)
            throw new IOException("BC7: обрезан сжатый блок");
        Bits bits=new Bits(data,offset);
        int mode=-1;
        for(int i=0;i<8;i++){
            if(bits.read(1)==1){mode=i;break;}
        }
        if(mode!=5&&mode!=6)
            throw new IOException("BC7: режим "+mode+
                " ещё не поддерживается CPU (доступны 5 и 6)");
        int rotation=mode==5?bits.read(2):0;
        int[][] ends=new int[4][2];
        for(int channel=0;channel<4;channel++){
            for(int endpoint=0;endpoint<2;endpoint++){
                ends[channel][endpoint]=bits.read(
                    mode==5&&channel==3?8:7);
            }
        }
        if(mode==6){
            int p0=bits.read(1),p1=bits.read(1);
            for(int channel=0;channel<4;channel++){
                ends[channel][0]=(ends[channel][0]<<1)|p0;
                ends[channel][1]=(ends[channel][1]<<1)|p1;
            }
        }else for(int channel=0;channel<3;channel++){
            ends[channel][0]=expand7(ends[channel][0]);
            ends[channel][1]=expand7(ends[channel][1]);
        }
        int[] colorIndex=new int[16],alphaIndex=new int[16];
        int indexBits=mode==6?4:2;
        for(int p=0;p<16;p++)
            colorIndex[p]=bits.read(p==0?indexBits-1:indexBits);
        if(mode==5)for(int p=0;p<16;p++)
            alphaIndex[p]=bits.read(p==0?1:2);
        if(bits.at!=128)throw new IOException("BC7: неверная длина режима");
        int[] pixels=new int[16];
        for(int p=0;p<16;p++){
            int wc=mode==6?W4[colorIndex[p]]:W2[colorIndex[p]];
            int wa=mode==6?wc:W2[alphaIndex[p]];
            int red=interpolate(ends[0][0],ends[0][1],wc);
            int green=interpolate(ends[1][0],ends[1][1],wc);
            int blue=interpolate(ends[2][0],ends[2][1],wc);
            int alpha=interpolate(ends[3][0],ends[3][1],wa);
            if(rotation==1){int temp=red;red=alpha;alpha=temp;}
            else if(rotation==2){int temp=green;green=alpha;alpha=temp;}
            else if(rotation==3){int temp=blue;blue=alpha;alpha=temp;}
            pixels[p]=(alpha<<24)|(red<<16)|(green<<8)|blue;
        }
        return pixels;
    }
    public static DdsBcDecoder.Image firstMip(byte[] dds,int offset,
            int width,int height)throws IOException{
        if(dds==null||width<1||height<1||
           width>2048||height>2048||(long)width*height>DdsBcDecoder.MAX_PIXELS)
            throw new IOException("BC7: слишком большое изображение");
        long bx=(width+3L)/4,by=(height+3L)/4;
        if(offset<0||(long)offset+bx*by*16>dds.length)
            throw new IOException("BC7: отсутствуют блоки первого mip");
        int[] pixels=new int[width*height];
        for(int row=0;row<by;row++)for(int col=0;col<bx;col++){
            int[] tile=decodeBlock(dds,offset+(int)((row*bx+col)*16));
            for(int y=0;y<4;y++)for(int x=0;x<4;x++){
                int xx=col*4+x,yy=row*4+y;
                if(xx<width&&yy<height)pixels[yy*width+xx]=tile[y*4+x];
            }
        }
        return new DdsBcDecoder.Image(width,height,pixels,"BC7 mode 5/6");
    }
}
