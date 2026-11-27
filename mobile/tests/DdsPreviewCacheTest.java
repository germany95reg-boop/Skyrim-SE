package dev.skyrimsemobile.launcher;
public final class DdsPreviewCacheTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError("cache invariant");}
    private static DdsBcDecoder.Image image(int n){return new DdsBcDecoder.Image(n,1,new int[n],"test");}
    public static void main(String[] args){
        DdsPreviewCache c=new DdsPreviewCache(32);
        DdsBcDecoder.Image a=image(4),b=image(4);
        c.put("a",a);c.put("b",b);
        check(c.pixelBytes()==32&&c.size()==2);
        check(c.get("a")==a);
        c.put("c",image(4));
        check(c.get("b")==null&&c.get("a")==a);
        c.put("a",image(2));
        check(c.pixelBytes()==24);
        c.put("large",image(9));
        check(c.get("large")==null&&c.pixelBytes()==24);
        DdsPreviewCache.Stats stats=c.stats();
        check(stats.hits==2&&stats.misses==2&&stats.evictions==1&&stats.rejected==1);
        check(stats.budget==32&&stats.pixelBytes==24&&stats.entries==2);
        c.clear();check(c.size()==0&&c.pixelBytes()==0);
        check(c.stats().hits==2&&c.stats().entries==0);
        DdsPreviewCache zero=new DdsPreviewCache(0);
        zero.put("a",a);check(zero.size()==0);
        DdsPreviewCache adaptive=new DdsPreviewCache(32);
        adaptive.put("a",a);adaptive.put("b",b);
        adaptive.get("a");adaptive.resize(16);
        check(adaptive.size()==1&&adaptive.get("a")==a&&adaptive.get("b")==null);
        check(adaptive.stats().budget==16&&adaptive.stats().evictions==1);
        adaptive.resize(0);check(adaptive.size()==0&&adaptive.pixelBytes()==0);
        adaptive.resize(32);adaptive.put("b",b);check(adaptive.get("b")==b);
        try{adaptive.resize(-1);throw new AssertionError("negative budget accepted");}
        catch(IllegalArgumentException expected){check(adaptive.stats().budget==32);}
        System.out.println("PASS DdsPreviewCacheTest");
    }
}
