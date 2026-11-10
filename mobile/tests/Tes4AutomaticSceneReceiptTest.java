package dev.skyrimsemobile.launcher;

public final class Tes4AutomaticSceneReceiptTest {
    private static Tes4AutomaticSceneReceipt.Result inspect(String actual,
          boolean completed,int triangles,boolean withAtlas,String textureMode,
          String pixelStatus,int[] fps,boolean[] ok){
        return Tes4AutomaticSceneReceipt.assess(
            "Skyrim · локальная 3D-сцена (2 REFR)",actual,completed,true,false,
            240,33,triangles,6,1920,1080,47.2,withAtlas,textureMode,
            pixelStatus,12500,49152,fps,ok,"Сэмпл PixelCopy",null);
    }
    public static void main(String[] args){
        String model="Skyrim · локальная 3D-сцена (2 REFR)";
        int[] caps={30,60,0};boolean[] good={true,true,true};
        var all=inspect(model,true,6,true,"atlas-safe","visible",caps,good);
        assert all.passed && all.failedChecks==0 && all.warnings==0:all.report;
        assert all.passedChecks==7:all.report;
        assert all.report.contains("PASS 7 · WARN 0 · FAIL 0");
        assert all.report.contains("совпадение") && all.report.contains("PixelCopy");
        var untextured=inspect(model,true,6,false,"off","visible",caps,good);
        assert untextured.passed && untextured.warnings==1:untextured.report;
        var wrong=inspect("Куб (встроенный)",true,6,false,"off","visible",caps,good);
        assert !wrong.passed && wrong.failedChecks>=1 &&
            wrong.report.contains("Вместо ожидаемой сцены");
        var damaged=inspect(model,true,0,true,"off","blank",caps,good);
        assert !damaged.passed && damaged.failedChecks>=3:damaged.report;
        var incomplete=inspect(model,false,6,true,"atlas-safe","capturing",
            new int[]{30},new boolean[]{true});
        assert !incomplete.passed && incomplete.failedChecks>=3:
            incomplete.report;
        var fpsBroken=inspect(model,true,6,true,"atlas-safe","visible",
            caps,new boolean[]{true,false,true});
        assert !fpsBroken.passed && fpsBroken.failedChecks==1:fpsBroken.report;
        var pixelSmall=inspect(model,true,6,true,"atlas-safe","small",caps,good);
        assert pixelSmall.passed && pixelSmall.warnings==1:pixelSmall.report;
        var screenshot=Tes4AutomaticSceneReceipt.withVisualQuality(all,
            1,5,1,200,5879,3064,49152);
        assert screenshot.passed && screenshot.failedChecks==0:
            "Valid Vulkan is not a broken GPU";
        assert screenshot.warnings==4:screenshot.report;
        assert screenshot.report.contains("PASS 7 · WARN 4 · FAIL 0");
        assert screenshot.report.contains("только 1 размещение REFR");
        assert screenshot.report.contains("декодировано 1 из 5");
        assert screenshot.report.contains("200 из 5879");
        assert screenshot.report.contains("6.2%");
        var complete=Tes4AutomaticSceneReceipt.withVisualQuality(all,
            4,5,5,5879,5879,7000,49152);
        assert complete.warnings==0 && complete.passed:
            "Real complete scene should not invent warnings";
        System.out.println("PASS automated REAL REFR/NIF Vulkan + PixelCopy + 30/60/unlimited result honesty");
    }
}
