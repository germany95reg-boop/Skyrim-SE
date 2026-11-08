package dev.skyrimsemobile.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class Tes4PlacementSubrecordReaderTest {
    static byte[] i32(int n){return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array();}
    static byte[] pose(float x,float y,float z) {
        return ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(x).putFloat(y).putFloat(z).putFloat(.1f).putFloat(.2f).putFloat(.3f).array();
    }
    static void field(ByteArrayOutputStream b,String name,byte[] data)throws Exception {
        b.write(name.getBytes(StandardCharsets.US_ASCII));
        b.write(data.length&255);b.write((data.length>>>8)&255);b.write(data);
    }
    static void fail(byte[] payload)throws Exception{
        try{
            Tes4PlacementSubrecordReader.inspect("REFR",9,payload);
            throw new AssertionError("corrupt placement accepted");
        }catch(IOException ok){}
    }
    public static void main(String[] a)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        field(b,"EDID",new byte[]{'T',0});
        field(b,"NAME",i32(0x00ABCDEF));
        field(b,"DATA",pose(100.25f,-204.5f,33.125f));
        var p=Tes4PlacementSubrecordReader.inspect("REFR",0x120030,b.toByteArray());
        assert p.complete()&&p.recordFormId==0x120030&&p.baseFormId==0x00ABCDEFL;
        assert Math.abs(p.x-100.25f)<0.01&&Math.abs(p.y+204.5f)<0.01;
        assert p.format().contains("NAME 0x00ABCDEF");
        var actor=Tes4PlacementSubrecordReader.inspect("ACHR",7,b.toByteArray());
        assert actor.complete()&&actor.type.equals("ACHR");
        ByteArrayOutputStream ext=new ByteArrayOutputStream();
        field(ext,"XXXX",i32(4));field(ext,"NAME",i32(0x11111111));
        field(ext,"DATA",pose(0,0,0));
        assert Tes4PlacementSubrecordReader.inspect("REFR",1,ext.toByteArray()).baseFormId==0x11111111L;
        ByteArrayOutputStream incomplete=new ByteArrayOutputStream();
        field(incomplete,"NAME",i32(4));
        assert !Tes4PlacementSubrecordReader.inspect("REFR",3,incomplete.toByteArray()).complete();
        byte[] bad=b.toByteArray();
        bad=bad.clone();bad[0]='X'; // unknown subrecord is allowed; NUL from EDID retained
        assert Tes4PlacementSubrecordReader.inspect("REFR",2,bad).complete();
        bad=b.toByteArray().clone();bad[4]=(byte)255;bad[5]=(byte)255;fail(bad);
        ByteArrayOutputStream duplicate=new ByteArrayOutputStream();
        field(duplicate,"NAME",i32(1));field(duplicate,"NAME",i32(2));fail(duplicate.toByteArray());
        ByteArrayOutputStream nan=new ByteArrayOutputStream();
        field(nan,"NAME",i32(2));
        byte[] pos=pose(0,0,0);System.arraycopy(i32(0x7fc00000),0,pos,0,4);
        field(nan,"DATA",pos);fail(nan.toByteArray());
        ByteArrayOutputStream dangling=new ByteArrayOutputStream();
        field(dangling,"XXXX",i32(4));fail(dangling.toByteArray());
        fail(new byte[]{'N','A','M','E',4});
        try{
            Tes4PlacementSubrecordReader.inspect("CELL",1,b.toByteArray());
            throw new AssertionError();
        }catch(IOException expected){}
        System.out.println("PASS REFR/ACHR NAME base FormID, xyz, radians, XXXX and corrupt subrecords");
    }
}
