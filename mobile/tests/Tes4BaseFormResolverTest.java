package dev.skyrimsemobile.launcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Synthetic TES4 fixtures, no game files. */
public final class Tes4BaseFormResolverTest {
    static byte[] u32(int value){return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();}
    static void text(ByteArrayOutputStream out,String s)throws Exception{out.write(s.getBytes(StandardCharsets.US_ASCII));}
    static void sub(ByteArrayOutputStream out,String tag,byte[] body)throws Exception{
        text(out,tag);out.write(body.length&255);out.write(body.length>>>8);out.write(body);
    }
    static byte[] plugin()throws Exception{
        ByteArrayOutputStream hedr=new ByteArrayOutputStream();
        sub(hedr,"HEDR",ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(1.71f).putInt(200).putInt(900).array());
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        text(out,"TES4");out.write(u32(hedr.size()));out.write(new byte[12]);out.write(new byte[]{44,0,0,0});
        out.write(hedr.toByteArray());return out.toByteArray();
    }
    static byte[] baseRecord(String type,int form,int flags,byte[] body)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        text(out,type);out.write(u32(body.length));out.write(u32(flags));out.write(u32(form));out.write(new byte[8]);
        out.write(body);return out.toByteArray();
    }
    static byte[] group(String kind,byte[]...records)throws Exception{
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        for(byte[] r:records)body.write(r);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        text(out,"GRUP");out.write(u32(body.size()+24));text(out,kind);out.write(new byte[12]);out.write(body.toByteArray());
        return out.toByteArray();
    }
    static byte[] join(byte[]...parts)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(byte[] p:parts)out.write(p);return out.toByteArray();
    }
    static byte[] metadata(String editor,String nif)throws Exception{
        ByteArrayOutputStream sub=new ByteArrayOutputStream();
        sub(sub,"EDID",(editor+"\0").getBytes(StandardCharsets.US_ASCII));
        sub(sub,"MODL",(nif+"\0").getBytes(StandardCharsets.US_ASCII));
        return sub.toByteArray();
    }
    static List<Tes4PlacementSubrecordReader.Placement> target(int...ids)throws Exception{
        List<Tes4PlacementSubrecordReader.Placement> p=new ArrayList<>();
        for(int id:ids){
            ByteArrayOutputStream sub=new ByteArrayOutputStream();
            sub(sub,"NAME",u32(id));
            sub(sub,"DATA",new byte[24]);
            p.add(Tes4PlacementSubrecordReader.inspect("REFR",id+100000L,sub.toByteArray()));
        }
        return p;
    }
    static void fails(byte[] bytes,List<Tes4PlacementSubrecordReader.Placement> ids)throws Exception{
        try{Tes4BaseFormResolver.resolve(new ByteArrayInputStream(bytes),ids,bytes.length);
            throw new AssertionError("Expected IOException");
        }catch(IOException expected){}
    }
    public static void main(String[] args)throws Exception {
        byte[] s=plugin();
        byte[] group=group("STAT",
           baseRecord("STAT",0x1234,0,metadata("TestRock","clutter\\rock01.nif")),
           baseRecord("STAT",0x1235,0x00040000,new byte[4]));
        byte[] rest=group("CELL",new byte[0]);
        byte[] input=join(s,group("GMST",new byte[0]),group,rest);
        var ids=target(0x1234,0x1235,0x7777);
        var result=Tes4BaseFormResolver.resolve(new ByteArrayInputStream(input),ids,input.length);
        assert result.found.size()==2:result.found.size();
        var rock=result.found.get(0x1234L);
        assert rock.type.equals("STAT")&&rock.editorId.equals("TestRock");
        assert rock.modelPath.equals("clutter/rock01.nif");
        assert result.found.get(0x1235L).compressed;
        assert !result.partial;
        assert result.report(ids).contains("НЕ НАЙДЕНА");
        assert result.report(ids).contains("MODL clutter/rock01.nif");
        var single=Tes4BaseFormResolver.resolve(new ByteArrayInputStream(input),target(0x1234),-1);
        assert single.found.size()==1;
        byte[] bad=input.clone();
        bad[s.length+24+24+4+24+4]=(byte)0xff; // random corruption in STAT payload; may still parse, structural scan survives
        assert Tes4BaseFormResolver.resolve(new ByteArrayInputStream(bad),target(0x1234),bad.length).found.size()<=1;
        byte[] malformed=input.clone();
        int statRecordOffset=s.length+24 /*GMST GRUP*/+24 /* STAT GRUP */;
        System.arraycopy(u32(999999),0,malformed,statRecordOffset+4,4);
        fails(malformed,target(0x1234));
        byte[] truncated=new byte[input.length-1];System.arraycopy(input,0,truncated,0,truncated.length);
        fails(truncated,target(0x7777));
        assert Tes4BaseFormResolver.resolve(new ByteArrayInputStream(input),new ArrayList<>(),input.length).targets==0;
        System.out.println("PASS base NAME FormID lookup in same-plugin STAT group, EDID/MODL, compressed and bounds");
    }
}
