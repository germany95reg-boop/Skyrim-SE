package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.Arrays;

/** Synthetic BSA indexes only. */
public final class Tes4MultiBsaModelMatcherTest {
    private static Tes4BaseFormResolver.Base base(int id,String model){
        return new Tes4BaseFormResolver.Base(id,"STAT","Test"+id,model,false);
    }
    private static BsaCatalogReader.Catalog cat(boolean partial,String... names){
        java.util.List<BsaCatalogReader.Entry> entries=new java.util.ArrayList<>();
        for(int i=0;i<names.length;i++)
            entries.add(new BsaCatalogReader.Entry(names[i],100+i,10+i,false));
        return new BsaCatalogReader.Catalog(1,partial?names.length+4:names.length,
                names.length,names.length,0x83L,entries);
    }
    public static void main(String[] args)throws Exception{
        var search=Tes4MultiBsaModelMatcher.begin(Arrays.asList(
            base(1,"MarkerX.nif"),base(2,"Clutter\\stockade\\Barricade01.nif"),
            base(3,"MarkerX.nif"),base(4,""),base(5,"../evil.nif")));
        assert search.neededPaths()==2;
        assert search.missingModl==1&&search.invalidModl==1;
        search.accept("Fish.bsa",cat(false,"meshes/fish/fishingrod.nif"));
        assert search.foundPaths()==0;
        search.accept("Meshes0.bsa",cat(false,"meshes/markerx.NIF","meshes/other.nif"));
        assert search.foundPaths()==1;
        search.accept("Meshes1.bsa",cat(true,"MESHEs\\clutter\\stockade\\barricade01.nif"));
        assert search.allFound();
        String report=search.report(3);
        assert report.contains("НАЙДЕНО в Meshes0.bsa");
        assert report.contains("НАЙДЕНО в Meshes1.bsa");
        assert report.contains("неполных каталогов: 1");
        var notFound=Tes4MultiBsaModelMatcher.begin(Arrays.asList(base(4,"notfound.nif")));
        notFound.accept("Incomplete.bsa",cat(true,"meshes/another.nif"));
        assert notFound.report(1).contains("НЕ ПОДТВЕРЖДЕНО");
        var complete=Tes4MultiBsaModelMatcher.begin(Arrays.asList(base(6,"missing.nif")));
        complete.accept("Complete.bsa",cat(false,"meshes/another.nif"));
        assert complete.report(1).contains("нет в проверенных BSA");
        complete.failed("broken.bsa",new IOException("bad header"));
        assert complete.report(2).contains("НЕ ПОДТВЕРЖДЕНО");
        var capped=Tes4MultiBsaModelMatcher.begin(Arrays.asList(base(7,"missing.nif")));
        capped.markCapped();
        assert capped.report(49).contains("лимит 48");
        System.out.println("PASS multi-BSA exact NIF match, repeated base, partial, failure, coverage cap");
    }
}
