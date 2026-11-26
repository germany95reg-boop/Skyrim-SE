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
        c.clear();check(c.size()==0&&c.pixelBytes()==0);
        DdsPreviewCache zero=new DdsPreviewCache(0);
        zero.put("a",a);check(zero.size()==0);
        System.out.println("PASS DdsPreviewCacheTest");
    }
}
