package dev.skyrimsemobile.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;

public class DdsArchiveFinderTest {
    private static int count=0;
    private static void check(boolean passed,String text){count++;if(!passed)throw new AssertionError(text);}
    private static void invalid(String raw){
        try{DdsArchiveFinder.normalizeDds(raw);throw new AssertionError("accepted unsafe path: "+raw);}
        catch(IOException expected){count++;}
    }
    public static void main(String[] args)throws Exception{
        String diffuse="textures/creationclub/bgssse001/rods/LureArgonian.dds";
        String normal="textures/creationclub/bgssse001/rods/LureArgonian_n.dds";
        check(DdsArchiveFinder.normalizeDds(diffuse).equals(diffuse.toLowerCase()),"lowercase");
        check(DdsArchiveFinder.normalizeDds("TEXTURES\\CREATIONCLUB\\BGSSSE001\\RODS\\LureArgonian.DDS")
            .equals(diffuse.toLowerCase()),"backslash/case");
        check(DdsArchiveFinder.normalizeDds("./"+normal).equals(normal.toLowerCase()),"relative dot prefix");
        invalid("../textures/creationclub/bgssse001/rods/LureArgonian.dds");
        invalid("textures/../LureArgonian.dds");
        invalid("textures//LureArgonian.dds");
        invalid("C:/textures/LureArgonian.dds");
        invalid("/textures/LureArgonian.dds");
        invalid("meshes/something.nif");
        invalid("textures/rods/LureArgonian.png");
        invalid("textures/rods/LureArgonian.dds\nother");
        Map<String,String> targets=DdsArchiveFinder.targets(Arrays.asList(diffuse,normal,"",diffuse.toUpperCase()));
        check(targets.size()==2,"deduplicate and ignore empty slots");
        check(targets.containsKey(diffuse.toLowerCase()),"diffuse target");
        check(targets.containsKey(normal.toLowerCase()),"normal target");
        List<BsaCatalogReader.Entry> entries=new ArrayList<>();
        entries.add(new BsaCatalogReader.Entry("TEXTURES\\CreationClub\\bgssse001\\rods\\LureArgonian.DDS",24355,0x123456L,true));
        entries.add(new BsaCatalogReader.Entry(normal,14443,0x123899L,false));
        entries.add(new BsaCatalogReader.Entry("textures/creationclub/bgssse001/rods/LureArgonian_fake.dds",1,15,false));
        entries.add(new BsaCatalogReader.Entry("textures/creationclub/bgssse001/rods/LureArgonian.dds.bak",1,20,false));
        BsaCatalogReader.Catalog archive=new BsaCatalogReader.Catalog(2,4,4,0,0x83,entries);
        List<DdsArchiveFinder.Hit> hits=DdsArchiveFinder.find(archive,targets,"Skyrim - Textures0.bsa");
        check(hits.size()==2,"exactly matching slots");
        check(hits.get(0).compressed&&hits.get(0).storedBytes==24355,"compressed metadata");
        check(!hits.get(1).compressed&&hits.get(1).offset==0x123899L,"normal metadata");
        check(hits.get(0).archive.equals("Skyrim - Textures0.bsa"),"archive name");
        check(hits.get(0).entryPath.endsWith("LureArgonian.DDS"),"preserve original path");
        check(DdsArchiveFinder.find(archive,DdsArchiveFinder.targets(Arrays.asList("textures/other/none.dds")),"test").isEmpty(),"no false match");
        check(DdsArchiveFinder.find(null,targets,"test").isEmpty(),"null guard");
        check(DdsArchiveFinder.find(archive,Map.of(),"test").isEmpty(),"empty guard");
        List<String> many=new ArrayList<>();for(int i=0;i<33;i++)many.add("textures/set"+i+"/model.dds");
        try{DdsArchiveFinder.targets(many);throw new AssertionError("accepted >32 paths");}
        catch(IOException expected){count++;}
        System.out.println("PASS: "+count+" DDS BSA index search and path safety checks");
    }
}
