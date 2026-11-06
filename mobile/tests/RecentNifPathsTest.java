package dev.skyrimsemobile.launcher;

import java.util.List;

public final class RecentNifPathsTest{
    private static int count;
    private static void check(boolean value,String description){
        count++;if(!value)throw new AssertionError(description);
    }

    public static void main(String[] args){
        String history="";
        for(int i=0;i<12;i++)
            history=RecentNifPaths.insert(history,"meshes/fishing/part"+i+".nif");
        List<String> all=RecentNifPaths.parse(history);
        check(all.size()==8,"bounded eight recent entries");
        check(all.get(0).endsWith("part11.nif"),"newest model first");
        check(all.get(7).endsWith("part4.nif"),"oldest entries evicted");
        history=RecentNifPaths.insert(history,"meshes/fishing/part7.nif");
        all=RecentNifPaths.parse(history);
        check(all.size()==8,"deduplicated history");
        check(all.get(0).endsWith("part7.nif"),"existing model moves to front");
        check(RecentNifPaths.parse(null).isEmpty(),"null history");
        check(RecentNifPaths.parse("").isEmpty(),"empty history");
        String same=RecentNifPaths.insert(history,"not-a-model.txt");
        check(same.equals(history),"only NIF stored");
        check(RecentNifPaths.insert(history,"bad\nname.nif").equals(history),
              "line breaks cannot inject entries");
        check(RecentNifPaths.parse("a.nif\na.nif\nb.nif").size()==2,
              "duplicates removed from saved data");
        check(RecentNifPaths.parse("bad.txt\nvalid.nif").size()==1,
              "bad saved paths discarded");
        check(RecentNifPaths.insert(null,"meshes/a.nif").equals("meshes/a.nif"),
              "first selected model stored");
        System.out.println("PASS v2.9 recent models: "+count+" checks");
    }
}
