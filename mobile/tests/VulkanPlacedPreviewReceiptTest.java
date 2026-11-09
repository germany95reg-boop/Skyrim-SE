package dev.skyrimsemobile.launcher;

public final class VulkanPlacedPreviewReceiptTest {
    private static final String LABEL="Skyrim REFR · StockadeBarricade01Activator";
    private static void reject(String geometry,boolean running,boolean error,
                               long frames,int edges,int tris,int width,int height,double fps){
        var result=VulkanPlacedPreviewReceipt.assess(LABEL,geometry,running,error,
            frames,edges,tris,width,height,fps);
        assert !result.passed:result.report;
        assert result.report.contains("не подтверждён");
    }
    public static void main(String[] args){
        var ok=VulkanPlacedPreviewReceipt.assess(LABEL,LABEL,true,false,
            45,9500,3910,1080,2400,51.5);
        assert ok.passed;
        assert ok.report.contains("3910");
        assert ok.report.contains("45");
        assert ok.report.contains("нужен скриншот");
        reject("Куб (встроенный)",true,false,45,12,12,1080,2400,60);
        reject(LABEL,false,false,0,9500,3910,1080,2400,0);
        reject(LABEL,true,true,30,9500,3910,1080,2400,60);
        reject(LABEL,true,false,1,9500,3910,1080,2400,60);
        reject(LABEL,true,false,45,9500,0,1080,2400,60);
        reject(LABEL,true,false,45,9500,3910,0,2400,60);
        reject(LABEL,true,false,45,9500,3910,1080,2400,Double.NaN);
        assert !VulkanPlacedPreviewReceipt.assess(null,LABEL,true,false,
            45,9500,3910,1080,2400,50).passed;
        System.out.println("PASS Vulkan placed-NIF receipt: actual geometry, frames, surface and failure paths");
    }
}
