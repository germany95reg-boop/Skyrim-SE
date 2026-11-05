import dev.skyrimsemobile.launcher.ResourceHeaderInspector;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public final class ResourceHeaderInspectorTest {
    static void assertTrue(boolean b,String s){if(!b)throw new AssertionError(s);}
    static void le(byte[] b,int off,long v){for(int i=0;i<4;i++)b[off+i]=(byte)(v>>> (8*i));}
    static ResourceHeaderInspector.Result check(byte[] b,String name)throws Exception{
        return ResourceHeaderInspector.inspect(new ByteArrayInputStream(b),name);
    }
    public static void main(String[] args)throws Exception{
        byte[] b=new byte[36];b[0]='B';b[1]='S';b[2]='A';
        le(b,4,105);le(b,8,36);le(b,12,3);le(b,16,8);le(b,20,94);
        var a=check(b,"Skyrim - Meshes.bsa");assertTrue(a.recognized&&a.details.contains("версия 105")&&a.details.contains("94"),"SSE BSA 105");
        le(b,4,104);assertTrue(check(b,"old.bsa").details.contains("Skyrim LE"),"LE version");
        le(b,4,5000);assertTrue(!check(b,"bad.bsa").recognized,"reject unsupported");
        assertTrue(!check(new byte[]{'B','S','A',0},"short.bsa").recognized,"truncation");
        var nif="Gamebryo File Format, Version 20.2.0.7\\n".replace("\\n","\n").getBytes(StandardCharsets.US_ASCII);
        assertTrue(check(nif,"model.nif").recognized,"NIF header");
        assertTrue(!check("garbage".getBytes(StandardCharsets.US_ASCII),"model.nif").recognized,"extension not enough");
        byte[] ba2=new byte[16];ba2[0]='B';ba2[1]='T';ba2[2]='D';ba2[3]='X';le(ba2,4,1);
        assertTrue(check(ba2,"general.ba2").recognized,"BA2 detection");
        System.out.println("PASS: 7 resource header tests");
    }
}
