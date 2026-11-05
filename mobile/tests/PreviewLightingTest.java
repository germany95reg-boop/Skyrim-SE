package dev.skyrimsemobile.launcher;
/** Deterministic presets used by the Android UI; no Android dependency. */
public final class PreviewLightingTest {
    private static int count=0;
    private static void eq(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        eq("Свет: Сбоку".equals(PreviewLighting.lightLabel(0)),"default light");
        eq("Свет: Спереди".equals(PreviewLighting.lightLabel(1)),"front light");
        eq("Свет: Слева".equals(PreviewLighting.lightLabel(2)),"left light");
        eq("Свет: Сверху".equals(PreviewLighting.lightLabel(3)),"top light");
        eq(PreviewLighting.nextLight(0)==1,"next front");
        eq(PreviewLighting.nextLight(1)==2,"next left");
        eq(PreviewLighting.nextLight(2)==3,"next top");
        eq(PreviewLighting.nextLight(3)==0,"cycle light");
        eq(PreviewLighting.nextLight(-9)==1,"invalid light resets before cycling");
        eq(PreviewLighting.clampLight(99)==0,"clamp invalid light");
        eq("Рельеф: 50%".equals(PreviewLighting.reliefLabel(0)),"half strength label");
        eq("Рельеф: 100%".equals(PreviewLighting.reliefLabel(1)),"normal strength label");
        eq("Рельеф: 150%".equals(PreviewLighting.reliefLabel(2)),"high strength label");
        eq(PreviewLighting.reliefStrength(0)==0.5f,"half strength exact");
        eq(PreviewLighting.reliefStrength(1)==1.0f,"default strength exact");
        eq(PreviewLighting.reliefStrength(2)==1.5f,"high strength exact");
        eq(PreviewLighting.nextRelief(0)==1,"strength 0->1");
        eq(PreviewLighting.nextRelief(1)==2,"strength 1->2");
        eq(PreviewLighting.nextRelief(2)==0,"strength wraps");
        eq(PreviewLighting.clampRelief(-1)==1,"invalid relief goes default");
        eq(PreviewLighting.reliefStrength(99)==1.0f,"invalid relief equals default");
        System.out.println("PASS v2.3 lighting presets: "+count+" assertions");
    }
}
