package dev.skyrimsemobile.launcher;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

public final class Tes4PluginHeaderReaderTest {
    static byte[] le(int n){return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array();}
    static byte[] hedr(){return ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putFloat(1.7f).putInt(1000).putInt(2048).array();}
    static void sub(ByteArrayOutputStream b,String name,byte[] data)throws Exception{
        b.write(name.getBytes(StandardCharsets.US_ASCII));b.write(data.length&255);b.write(data.length>>>8);b.write(data);
    }
    static byte[] plugin(byte[] payload)throws Exception{
        ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.put("TES4".getBytes(StandardCharsets.US_ASCII)).putInt(payload.length).putInt(0x281);
        h.putInt(0).putInt(0).putShort((short)44).putShort((short)0);
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(h.array());out.write(payload);
        return out.toByteArray();
    }
    static void fails(byte[] data)throws Exception{
        try{Tes4PluginHeaderReader.inspect(new ByteArrayInputStream(data));
            throw new AssertionError("Expected failure");}
        catch(IOException ok){}
    }
    public static void main(String[] args)throws Exception{
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        sub(body,"HEDR",hedr());sub(body,"MAST","Skyrim.esm\0".getBytes(StandardCharsets.US_ASCII));
        sub(body,"CNAM","Synthetic\0".getBytes(StandardCharsets.US_ASCII));
        byte[] valid=plugin(body.toByteArray());
        Tes4PluginHeaderReader.Result r=Tes4PluginHeaderReader.inspect(new ByteArrayInputStream(valid));
        assert r.formVersion==44&&r.recordCount==1000&&r.nextObjectId==2048;
        assert r.masters.size()==1&&r.masters.get(0).equals("Skyrim.esm");
        assert r.author.equals("Synthetic")&&r.toString().contains("ESL=true");
        byte[] bad=valid.clone();bad[0]='X';fails(bad);
        bad=valid.clone();System.arraycopy(le(4194305),0,bad,4,4);fails(bad);
        bad=new byte[valid.length-1];System.arraycopy(valid,0,bad,0,bad.length);fails(bad);
        ByteArrayOutputStream missing=new ByteArrayOutputStream();
        sub(missing,"MAST","Skyrim.esm\0".getBytes(StandardCharsets.US_ASCII));fails(plugin(missing.toByteArray()));
        ByteArrayOutputStream duplicate=new ByteArrayOutputStream();
        sub(duplicate,"HEDR",hedr());sub(duplicate,"HEDR",hedr());fails(plugin(duplicate.toByteArray()));
        ByteArrayOutputStream invalid=new ByteArrayOutputStream();
        sub(invalid,"HEDR",hedr());invalid.write("MAST".getBytes(StandardCharsets.US_ASCII));invalid.write(90);invalid.write(0);invalid.write('x');
        fails(plugin(invalid.toByteArray()));
        ByteArrayOutputStream dangling=new ByteArrayOutputStream();
        sub(dangling,"HEDR",hedr());sub(dangling,"XXXX",le(11));fails(plugin(dangling.toByteArray()));
        ByteArrayOutputStream ext=new ByteArrayOutputStream();
        sub(ext,"HEDR",hedr());sub(ext,"XXXX",le(11));sub(ext,"MAST","Skyrim.esm\0".getBytes(StandardCharsets.US_ASCII));
        assert Tes4PluginHeaderReader.inspect(new ByteArrayInputStream(plugin(ext.toByteArray()))).masters.size()==1;
        System.out.println("PASS TES4 bounded header, HEDR, MAST, extended lengths and malformed inputs");
    }
}
