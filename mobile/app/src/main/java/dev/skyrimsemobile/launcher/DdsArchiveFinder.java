package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds *catalogue entries only* whose canonical texture paths match DDS paths
 * declared by a selected NIF. Does not touch payloads, decode DDS, or write files.
 */
public final class DdsArchiveFinder {
    public static final int MAX_TARGETS = 32;
    private DdsArchiveFinder() {}

    public static final class Hit {
        public final String archive, declaredPath, entryPath;
        public final long storedBytes, offset;
        public final boolean compressed;
        Hit(String archive, String declaredPath, BsaCatalogReader.Entry e) {
            this.archive=archive;this.declaredPath=declaredPath;entryPath=e.path;
            storedBytes=e.storedBytes;offset=e.offset;compressed=e.compressed;
        }
    }

    public static String normalizeDds(String raw) throws IOException {
        if(raw==null)throw new IOException("Пустой путь DDS");
        String p=raw.trim().replace('\\','/').toLowerCase(Locale.ROOT);
        while(p.startsWith("./"))p=p.substring(2);
        if(p.length()<13||p.length()>1024||!p.startsWith("textures/")||!p.endsWith(".dds"))
            throw new IOException("Неверный относительный путь DDS: "+raw);
        if(p.indexOf(':')>=0||p.indexOf('\\')>=0||p.indexOf('\0')>=0)
            throw new IOException("Небезопасный путь DDS");
        String[] parts=p.split("/",-1);
        for(String part:parts) {
            if(part.isEmpty()||part.equals(".")||part.equals(".."))
                throw new IOException("Небезопасный путь DDS");
            for(int i=0;i<part.length();i++)if(part.charAt(i)<32||part.charAt(i)==127)
                throw new IOException("Управляющие символы в пути DDS");
        }
        return p;
    }

    public static Map<String,String> targets(Collection<String> raw) throws IOException {
        if(raw==null)throw new IOException("Нет путей DDS");
        LinkedHashMap<String,String> out=new LinkedHashMap<>();
        for(String p:raw){
            if(p==null||p.isEmpty())continue; // empty slots in BSShaderTextureSet
            String normalized=normalizeDds(p);
            if(!out.containsKey(normalized)){
                if(out.size()>=MAX_TARGETS)throw new IOException("Слишком много DDS для одного поиска");
                out.put(normalized,p);
            }
        }
        if(out.isEmpty())throw new IOException("В NIF нет путей DDS");
        return Collections.unmodifiableMap(out);
    }

    /** Exact, case-insensitive relative path match; never substring match. */
    public static List<Hit> find(BsaCatalogReader.Catalog catalog,Map<String,String> wanted,String archive){
        if(catalog==null||wanted==null||wanted.isEmpty())return Collections.emptyList();
        List<Hit> hits=new ArrayList<>();
        for(BsaCatalogReader.Entry e:catalog.entries){
            String canonical;
            try{canonical=normalizeDds(e.path);}catch(IOException ignored){continue;}
            String target=wanted.get(canonical);
            if(target!=null)hits.add(new Hit(archive,target,e));
        }
        return hits;
    }
}
