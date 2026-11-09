package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.Arrays;

/** Synthetic catalogues: verifies exact path matching, incomplete index honesty, safety. */
public final class Tes4BsaModelMatcherTest {
    private static BsaCatalogReader.Catalog catalog(boolean partial){
        var entries=Arrays.asList(
            new BsaCatalogReader.Entry("Meshes\\Clutter\\rock01.NIF",123,100,false),
            new BsaCatalogReader.Entry("meshes/architecture/riverwood/home01.nif",42,300,true),
            new BsaCatalogReader.Entry("textures/clutter/rock01.dds",56,350,false),
            new BsaCatalogReader.Entry("meshes/../clutter/evil.nif",70,400,false));
        return new BsaCatalogReader.Catalog(2,partial?12:entries.size(),
            entries.size(),3,0x83,entries);
    }
    private static Tes4BaseFormResolver.Base base(long id,String path){
        return new Tes4BaseFormResolver.Base(id,"STAT","Test"+id,path,false);
    }
    private static void invalid(String path)throws Exception{
        try{Tes4BsaModelMatcher.canonicalNif(path);throw new AssertionError(path);}
        catch(IOException ok){}
    }
    public static void main(String[] args)throws Exception{
        assert Tes4BsaModelMatcher.canonicalNif("clutter\\ROCK01.nif")
            .equals("meshes/clutter/rock01.nif");
        assert Tes4BsaModelMatcher.canonicalNif("meshes/clutter/rock01.NIF")
            .equals("meshes/clutter/rock01.nif");
        invalid("../clutter/rock01.nif");
        invalid("c:\\models\\rock01.nif");
        invalid("/meshes/clutter/rock01.nif");
        invalid("meshes//clutter/rock01.nif");
        invalid("meshes/./rock01.nif");
        invalid("rock01.dds");
        invalid("meshes/m\nodel.nif");
        var records=Arrays.asList(base(0x1234,"clutter\\rock01.nif"),
            base(0x2222,"architecture/riverwood/home01.nif"),
            base(0x3333,"clutter/missing.nif"),
            base(0x4444,""),base(0x5555,"../bad.nif"),
            base(0x6666,"clutter/rock01.nif.bak"),
            base(0x7777,"clutter/evil.nif"));
        var full=Tes4BsaModelMatcher.match(records,catalog(false),"Skyrim - Meshes.bsa");
        assert full.foundCount==2:full.foundCount;
        assert full.missingCount==2:full.missingCount;
        assert full.invalidCount==2:full.invalidCount;
        assert full.noModelCount==1:full.noModelCount;
        assert full.items.get(0).match!=null;
        assert full.items.get(1).match.compressed;
        assert full.toString().contains("нет в ЭТОМ BSA");
        var partial=Tes4BsaModelMatcher.match(records,catalog(true),"Sample.bsa");
        assert partial.partialIndex;
        assert partial.toString().contains("индекс НЕПОЛНЫЙ");
        assert partial.toString().contains("НЕ ПОДТВЕРЖДЕНО");
        assert !partial.toString().contains("нет в ЭТОМ BSA");
        System.out.println("PASS TES4 MODL NIF BSA indexed exact-match, partial coverage, path security");
    }
}
