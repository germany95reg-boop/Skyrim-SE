package dev.skyrimsemobile.launcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Synthetic records only: no Bethesda assets shipped in tests. */
public final class Tes4TopLevelGroupReaderTest {
    private static byte[] word(int n) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array();
    }
    private static void sub(ByteArrayOutputStream out,String type,byte[] body)throws Exception {
        out.write(type.getBytes(StandardCharsets.US_ASCII));
        out.write(body.length&255);
        out.write((body.length>>>8)&255);
        out.write(body);
    }
    private static byte[] fixture()throws Exception {
        ByteArrayOutputStream hedr=new ByteArrayOutputStream();
        ByteBuffer val=ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        val.putFloat(1.71f).putInt(920181).putInt(0xff000f93);
        sub(hedr,"HEDR",val.array());
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write("TES4".getBytes(StandardCharsets.US_ASCII));
        out.write(word(hedr.size()));
        out.write(new byte[12]); // flags, FormID and revision
        out.write(new byte[]{44,0,0,0}); // form version
        out.write(hedr.toByteArray());
        return out.toByteArray();
    }
    private static byte[] group(String label,byte[] body)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write("GRUP".getBytes(StandardCharsets.US_ASCII));
        out.write(word(24+body.length));
        out.write(label.getBytes(StandardCharsets.US_ASCII));
        out.write(word(0)); // TOP group type
        out.write(new byte[8]); // timestamp and version control
        out.write(body);
        return out.toByteArray();
    }
    private static byte[] join(byte[]... parts)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(byte[] part:parts)out.write(part);
        return out.toByteArray();
    }
    private static void fails(byte[] value,long expectedBytes)throws Exception {
        try {
            Tes4TopLevelGroupReader.scan(new ByteArrayInputStream(value),expectedBytes);
            throw new AssertionError("Corrupt top-level GRUP should fail");
        }catch(IOException expected){}
    }
    public static void main(String[] args)throws Exception {
        byte[] head=fixture();
        byte[] one=group("GMST",new byte[]{1,2,3,4,5,6});
        byte[] two=group("WRLD",new byte[]{9,8,7,6});
        byte[] full=join(head,one,two);
        Tes4TopLevelGroupReader.Result r=Tes4TopLevelGroupReader.scan(new ByteArrayInputStream(full),full.length);
        assert r.groups.size()==2;
        assert r.hedrVersion>1.7f&&r.declaredRecords==920181;
        assert r.indexedBytes==full.length;
        assert r.groups.get(1).fileOffset==head.length+one.length;
        assert r.groups.get(1).byteLength==two.length;
        assert r.hasType("WRLD")&&!r.hasType("CELL");
        assert r.toString().contains("Индекс TES4");
        assert Tes4TopLevelGroupReader.scan(new ByteArrayInputStream(full),-1).groups.size()==2;
        byte[] bad=full.clone();
        bad[head.length]='X';fails(bad,bad.length);
        bad=full.clone();System.arraycopy(word(23),0,bad,head.length+4,4);fails(bad,bad.length);
        bad=full.clone();System.arraycopy(word(1),0,bad,head.length+12,4);fails(bad,bad.length);
        bad=full.clone();System.arraycopy(word(100000),0,bad,head.length+4,4);fails(bad,bad.length);
        bad=new byte[full.length-1];System.arraycopy(full,0,bad,0,bad.length);fails(bad,full.length);
        bad=new byte[full.length-1];System.arraycopy(full,0,bad,0,bad.length);fails(bad,-1);
        byte[] partial=join(head,new byte[]{'G','R','U'});
        fails(partial,-1);
        byte[] empty=join(head,group("CELL",new byte[0]));
        assert Tes4TopLevelGroupReader.scan(new ByteArrayInputStream(empty),empty.length).groups.size()==1;
        System.out.println("PASS TES4 top-level GRUP labels, offsets, bounds, corrupt and truncated cases");
    }
}
