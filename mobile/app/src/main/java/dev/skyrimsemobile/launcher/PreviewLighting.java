package dev.skyrimsemobile.launcher;

/** Pure-JVM validated lighting presets for the ARM64/Vulkan asset preview. */
public final class PreviewLighting {
    private static final String[] LIGHT={"Сбоку","Спереди","Слева","Сверху"};
    private static final int[] RELIEF={50,100,150};
    private PreviewLighting(){}
    public static int nextLight(int mode){return (clampLight(mode)+1)%LIGHT.length;}
    public static int nextRelief(int preset){return (clampRelief(preset)+1)%RELIEF.length;}
    public static String lightLabel(int mode){return "Свет: "+LIGHT[clampLight(mode)];}
    public static String reliefLabel(int preset){return "Рельеф: "+RELIEF[clampRelief(preset)]+"%";}
    public static float reliefStrength(int preset){return RELIEF[clampRelief(preset)]/100.0f;}
    public static int clampLight(int mode){return mode<0||mode>=LIGHT.length?0:mode;}
    public static int clampRelief(int preset){return preset<0||preset>=RELIEF.length?1:preset;}
}
