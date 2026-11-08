package dev.skyrimsemobile.launcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Pure synthetic test data: no copyrighted game files. */
public final class Tes4NestedRecordSamplerTest {
    private static byte[] le(int v){
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    }
    private static void add(ByteArrayOutputStream out,String text)throws Exception {
        out.write(text.getBytes(StandardCharsets.US_ASCII));
    }
    private static byte[] header()throws Exception{
        ByteArrayOutputStream sub=new ByteArrayOutputStream();
        add(sub,"HEDR");sub.write(12);sub.write(0);
        sub.write(ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(1.71f).putInt(920181).putInt(0xff000f93).array());
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        add(result,"TES4");result.write(le(sub.size()));result.write(new byte[12]);
        result.write(new byte[]{44,0,0,0});result.write(sub.toByteArray());
        return result.toByteArray();
    }
    private static byte[] record(String type,int formId,int flags,byte[] payload)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        add(out,type);out.write(le(payload.length));out.write(le(flags));out.write(le(formId));
        out.write(new byte[8]);out.write(payload);
        return out.toByteArray();
    }
    private static byte[] grup(int type,String label,byte[]... contents)throws Exception {
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        for(byte[] content:contents)body.write(content);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        add(out,"GRUP");out.write(le(24+body.size()));
        add(out,label);out.write(le(type));out.write(new byte[8]);out.write(body.toByteArray());
        return out.toByteArray();
    }
    private static byte[] join(byte[]... inputs)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(byte[] input:inputs)out.write(input);
        return out.toByteArray();
    }
    private static void fail(byte[] input,String label,long size)throws Exception{
        try{
            Tes4NestedRecordSampler.scan(new ByteArrayInputStream(input),label,size);
            throw new AssertionError("Invalid fixture should fail");
        }catch(IOException ok){}
    }
    public static void main(String[] args)throws Exception{
        byte[] start=header();
        byte[] wrong=grup(0,"GMST",record("GMST",1,0,new byte[3]));
        byte[] worldRecord=record("WRLD",0x1000,0,new byte[0]);
        byte[] reference=record("REFR",0xabcdef,0x40000,new byte[]{1,2,3,4});
        byte[] cell=record("CELL",0x2000,0,new byte[0]);
        byte[] children=grup(1,"xxxx",grup(4,"yyyy",grup(5,"zzzz",cell,grup(6,"cccc",reference))));
        byte[] world=grup(0,"WRLD",worldRecord,children);
        byte[] cells=grup(0,"CELL",record("CELL",0x1234,0,new byte[]{5}));
        byte[] input=join(start,wrong,world,cells);
        Tes4NestedRecordSampler.Result wrld=
             Tes4NestedRecordSampler.scan(new ByteArrayInputStream(input),"WRLD",input.length);
        assert wrld.recordsSeen==3:wrld.recordsSeen;
        assert wrld.groupsSeen==4:wrld.groupsSeen;
        assert wrld.recordTypes.get("REFR")==1;
        assert wrld.samples.get(2).formId==0xabcdefL;
        assert wrld.samples.get(2).depth==4;
        assert !wrld.partial;
        assert wrld.toString().contains("0x00ABCDEF");
        Tes4NestedRecordSampler.Result cellResult=
             Tes4NestedRecordSampler.scan(new ByteArrayInputStream(input),"CELL",input.length);
        assert cellResult.recordsSeen==1 && cellResult.recordTypes.get("CELL")==1;
        assert cellResult.sectionOffset==start.length+wrong.length+world.length;
        byte[] bad=input.clone();
        int referenceOffset=start.length+wrong.length+24+worldRecord.length+24+24+24+24+24;
        // Corrupt a main record body length to cross a cell-child GRUP boundary.
        System.arraycopy(le(500000),0,bad,referenceOffset+4,4);
        fail(bad,"WRLD",bad.length);
        byte[] shortened=new byte[input.length-1];
        System.arraycopy(input,0,shortened,0,shortened.length);
        fail(shortened,"CELL",input.length);
        fail(input,"KYWD",input.length);
        byte[] headerOnly=join(start,grup(0,"WRLD",new byte[]{1,2,3}));
        fail(headerOnly,"WRLD",headerOnly.length);
        System.out.println("PASS nested GRUP and CELL/WRLD/REFR headers, FormIDs, bounds, malformed cases");
    }
}
