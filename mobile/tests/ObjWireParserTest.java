package dev.skyrimsemobile.launcher;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.IOException;
public final class ObjWireParserTest {
  static ObjWireParser.Model read(String obj) throws Exception {
    return ObjWireParser.parse(new ByteArrayInputStream(obj.getBytes(StandardCharsets.UTF_8)));
  }
  static void ok(boolean value, String msg) {if(!value)throw new AssertionError(msg);}
  static void expectError(String obj) throws Exception {try{read(obj);throw new AssertionError("Invalid OBJ accepted");}catch(IOException expected){}}
  public static void main(String[] args) throws Exception {
    String cube="v -1 -1 -1\nv 1 -1 -1\nv 1 1 -1\nv -1 1 -1\nf 1 2 3 4\n";
    ObjWireParser.Model a=read(cube);ok(a.vertices==4 && a.edges==4,"Quad edges");
    ok(a.segments.length==24,"Quad float count");
    ObjWireParser.Model b=read("v 0 0 0\nv 2 0 0\nv 0 2 0\nf -3/-1/-1 -2/-2/-1 -1/-3/-1\n");
    ok(b.edges==3,"Negative indexing + slash");
    ObjWireParser.Model c=read("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\nf 1 3 2\n");
    ok(c.edges==3,"Duplicate edge removed");
    ObjWireParser.Model d=read("v 0 0 0\nv 1 0 0\nl 1 2\n");
    ok(d.edges==1,"Line support");
    expectError("v 0 0 0\nv 1 0 0\nf 1 9 2\n");
    expectError("v 0 0 0\nv nan 0 0\nl 1 2\n");
    expectError("v 1 1 1\nv 1 1 1\nl 1 2\n");
    System.out.println("ObjWireParser: 7 checks OK");
  }
}
