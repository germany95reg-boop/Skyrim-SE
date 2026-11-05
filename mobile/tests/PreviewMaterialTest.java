package dev.skyrimsemobile.launcher;
public final class PreviewMaterialTest {
    private static int count=0;
    private static void check(boolean ok,String message){
        count++;
        if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args){
        check(PreviewMaterial.specularStrength(0)==0f,"default: off");
        check(PreviewMaterial.specularStrength(1)==0.25f,"25 percent");
        check(PreviewMaterial.specularStrength(2)==0.5f,"50 percent");
        check(PreviewMaterial.specularStrength(3)==1f,"100 percent");
        check("Блик: 0%".equals(PreviewMaterial.specularLabel(0)),"off label");
        check("Блик: 25%".equals(PreviewMaterial.specularLabel(1)),"25 label");
        check("Блик: 50%".equals(PreviewMaterial.specularLabel(2)),"50 label");
        check("Блик: 100%".equals(PreviewMaterial.specularLabel(3)),"100 label");
        check(PreviewMaterial.nextSpecular(0)==1,"0 -> 25");
        check(PreviewMaterial.nextSpecular(1)==2,"25 -> 50");
        check(PreviewMaterial.nextSpecular(2)==3,"50 -> 100");
        check(PreviewMaterial.nextSpecular(3)==0,"100 -> 0");
        check(PreviewMaterial.clampSpecular(-10)==0,"negative defaults off");
        check(PreviewMaterial.specularStrength(999)==0f,"oversized defaults off");
        check("Ширина: Широкий".equals(PreviewMaterial.sizeLabel(0)),"wide");
        check("Ширина: Средний".equals(PreviewMaterial.sizeLabel(1)),"medium");
        check("Ширина: Узкий".equals(PreviewMaterial.sizeLabel(2)),"narrow");
        check(PreviewMaterial.nextSize(0)==1,"wide -> medium");
        check(PreviewMaterial.nextSize(1)==2,"medium -> narrow");
        check(PreviewMaterial.nextSize(2)==0,"narrow -> wide");
        check(PreviewMaterial.sizeLabel(-1).endsWith("Средний"),"invalid width default");
        check(PreviewMaterial.clampSize(100)==1,"oversized width default");
        System.out.println("PASS v2.4 preview specular material: "+count+" assertions");
    }
}
