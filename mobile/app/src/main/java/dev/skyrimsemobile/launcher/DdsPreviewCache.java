package dev.skyrimsemobile.launcher;

import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;

/** Session-local cache. Keys must identify both archive and resource. */
public final class DdsPreviewCache {
    private final long budget;
    private long bytes,hits,misses,evictions,rejected;
    private final LinkedHashMap<String,DdsBcDecoder.Image> entries =
        new LinkedHashMap<>(16,0.75f,true);
    public DdsPreviewCache(long budget){
        if(budget<0)throw new IllegalArgumentException("negative budget");
        this.budget=budget;
    }
    private static long weight(DdsBcDecoder.Image image){return 4L*image.argb.length;}
    public synchronized DdsBcDecoder.Image get(String key){
        DdsBcDecoder.Image image=entries.get(key);
        if(image==null)misses++;else hits++;
        return image;
    }
    public synchronized void put(String key,DdsBcDecoder.Image image){
        if(key==null||image==null||image.argb==null||image.width<1||image.height<1||
           (long)image.width*image.height!=image.argb.length)
            throw new IllegalArgumentException("invalid texture");
        DdsBcDecoder.Image old=entries.remove(key);
        if(old!=null)bytes-=weight(old);
        long size=weight(image);
        if(size>budget){rejected++;return;}
        Iterator<Map.Entry<String,DdsBcDecoder.Image>> it=entries.entrySet().iterator();
        while(bytes>budget-size&&it.hasNext()){
            bytes-=weight(it.next().getValue());it.remove();evictions++;
        }
        entries.put(key,image);bytes+=size;
    }
    public synchronized void clear(){entries.clear();bytes=0;}
    public synchronized long pixelBytes(){return bytes;}
    public static final class Stats {
        public final long budget,pixelBytes,hits,misses,evictions,rejected;
        public final int entries;
        private Stats(long budget,long bytes,long hits,long misses,long evictions,long rejected,int entries){
            this.budget=budget;this.pixelBytes=bytes;this.hits=hits;this.misses=misses;
            this.evictions=evictions;this.rejected=rejected;this.entries=entries;
        }
    }
    public synchronized Stats stats(){return new Stats(budget,bytes,hits,misses,evictions,rejected,entries.size());}
    public synchronized int size(){return entries.size();}
}
