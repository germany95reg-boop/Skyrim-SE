package dev.skyrimsemobile.launcher;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public final class NifCatalogSelectionTest {
    private static int checks=0;
    private static void ok(boolean valid,String msg){
        checks++;if(!valid)throw new AssertionError(msg);
    }
    public static void main(String[] args){
        BsaCatalogReader.Entry a=new BsaCatalogReader.Entry("meshes/a/gear.nif",200,40,false);
        BsaCatalogReader.Entry b=new BsaCatalogReader.Entry("meshes/b/gear.nif",200,80,true);
        BsaCatalogReader.Entry c=new BsaCatalogReader.Entry("meshes/b/gear_part.nif",200,120,false);
        BsaCatalogReader.Entry txt=new BsaCatalogReader.Entry("meshes/b/gear.txt",12,30,false);
        BsaCatalogReader.Catalog cat=new BsaCatalogReader.Catalog(
            2,4,4,3,3,new ArrayList<>(Arrays.asList(a,b,c,txt)));
        List<BsaCatalogReader.Entry> first=NifCatalogSelection.matches(cat,"","gear.nif");
        ok(first.size()==1&&first.get(0)==a,"search fallback picks first");
        ok(NifCatalogSelection.matches(cat,b.path,"gear").get(0)==b,
           "tap exact model in second directory");
        ok(NifCatalogSelection.matches(cat,c.path,"gear").get(0)==c,
           "tap longer filename cannot pick prefix");
        ok(NifCatalogSelection.matches(cat,"meshes/b/GEAR.nif","gear").isEmpty(),
           "explicit path must be exact");
        ok(NifCatalogSelection.matches(cat,"meshes/z/missing.nif","gear").isEmpty(),
           "stale explicit selection never chooses another NIF");
        ok(NifCatalogSelection.matches(cat,txt.path,"gear").isEmpty(),
           "non-NIF explicit selection rejected");
        ok(NifCatalogSelection.matches(null,"","gear").isEmpty(),"not loaded");
        ok(NifCatalogSelection.matches(cat,"","none").isEmpty(),"no result");
        ok(cat.find("gear",true,60).size()==3,"click list excludes non-NIF");
        System.out.println("PASS v2.8 exact NIF model selection: "+checks+" tests");
    }
}
