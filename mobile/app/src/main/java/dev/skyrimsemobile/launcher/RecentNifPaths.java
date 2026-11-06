package dev.skyrimsemobile.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compact, pure-JVM history of at most eight paths for a single BSA. */
public final class RecentNifPaths {
    public static final int LIMIT=8;
    private RecentNifPaths(){}

    public static List<String> parse(String encoded){
        if(encoded==null||encoded.isEmpty())return Collections.emptyList();
        List<String> paths=new ArrayList<>();
        for(String path:encoded.split("\n")){
            if(valid(path)&&!paths.contains(path)){
                paths.add(path);
                if(paths.size()==LIMIT)break;
            }
        }
        return Collections.unmodifiableList(paths);
    }

    public static boolean valid(String path){
        return path!=null&&!path.isEmpty()&&path.length()<=512&&
            path.indexOf('\n')<0&&path.toLowerCase(java.util.Locale.ROOT)
                .endsWith(".nif");
    }

    public static String insert(String encoded,String selected){
        if(!valid(selected))return encoded==null?"":encoded;
        List<String> paths=new ArrayList<>(parse(encoded));
        paths.remove(selected);
        paths.add(0,selected);
        if(paths.size()>LIMIT)paths=paths.subList(0,LIMIT);
        return String.join("\n",paths);
    }
}
