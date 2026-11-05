package dev.skyrimsemobile.launcher;

/** Immutable, JVM-testable specular-preview presets; no Android dependencies. */
public final class PreviewMaterial {
    private static final int[] STRENGTH={0,25,50,100};
    private static final String[] SIZES={"Широкий","Средний","Узкий"};
    private PreviewMaterial(){}
    public static int clampSpecular(int index) {
        return index>=0&&index<STRENGTH.length?index:0;
    }
    public static int clampSize(int index) {
        return index>=0&&index<SIZES.length?index:1;
    }
    public static int nextSpecular(int index) {
        return (clampSpecular(index)+1)%STRENGTH.length;
    }
    public static int nextSize(int index) {
        return (clampSize(index)+1)%SIZES.length;
    }
    public static float specularStrength(int index) {
        return STRENGTH[clampSpecular(index)]/100f;
    }
    public static String specularLabel(int index) {
        return "Блик: "+STRENGTH[clampSpecular(index)]+"%";
    }
    public static String sizeLabel(int index) {
        return "Ширина: "+SIZES[clampSize(index)];
    }
}
