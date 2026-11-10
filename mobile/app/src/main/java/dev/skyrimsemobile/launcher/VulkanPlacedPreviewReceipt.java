package dev.skyrimsemobile.launcher;

import java.util.Locale;

/** Pure-Java sanity result for a real on-device Vulkan static-NIF preview.
 * Native counters prove command/frame progress; they are NOT a pixel
 * comparison and must never be reported as a confirmed correct image.
 */
public final class VulkanPlacedPreviewReceipt {
    private VulkanPlacedPreviewReceipt(){}
    public static final class Result {
        public final boolean passed;
        public final String report;
        Result(boolean passed,String report){this.passed=passed;this.report=report;}
    }
    public static Result assess(String expected,String actual,boolean running,
           boolean error,long frames,int edges,int triangles,int width,int height,
           double fps){
        String cause=null;
        if(expected==null||expected.isEmpty())
            cause="Отсутствует ожидаемое имя модели";
        else if(actual==null||!actual.equals(expected))
            cause="Рендерер сообщает другую геометрию: "+(actual==null?"":actual);
        else if(error)cause="Рендерер сообщил ошибку";
        else if(!running||frames<2)cause="Нет подтверждения двух кадров Vulkan";
        else if(edges<1||triangles<1)cause="Рендерер не подтвердил треугольники и рёбра";
        else if(width<1||height<1)cause="Рендерер не сообщил размер поверхности";
        else if(!Double.isFinite(fps)||fps<0)cause="Некорректная скорость рендеринга";
        boolean ok=cause==null;
        String report=String.format(Locale.ROOT,
            "3D-предпросмотр Vulkan: %s\n"+
            "Модель: %s\n"+
            "Рёбра: %d · треугольники: %d · кадры: %d · FPS: %.1f · поверхность: %d×%d\n"+
            "%s",
            ok?"графический конвейер подтвердил обработку модели":
               "не подтверждён ("+cause+")",
            expected==null?"?":expected,edges,triangles,frames,fps,width,height,
            "Это счётчики Vulkan, а не проверка изображения: для визуальной "+
            "проверки нужен скриншот с телефона.");
        return new Result(ok,report);
    }
    /**
     * v2.43: native counters and on-device PixelCopy are distinct checks.
     * PixelCopy only detects pixels unlike the Vulkan clear background.
     * It cannot verify that geometry, textures or game lighting are correct.
     */
    public static Result assessWithPixelCopy(String expected,String actual,boolean running,
           boolean error,long frames,int edges,int triangles,int width,int height,
           double fps,String captureStatus,String captureNote,
           int nonBackground,int sampledPixels,int foregroundWidth,int foregroundHeight){
        Result nativeReceipt=assess(expected,actual,running,error,
            frames,edges,triangles,width,height,fps);
        String status=captureStatus==null?"not-requested":captureStatus;
        String image;
        boolean pixelMismatch=false;
        if("visible".equals(status)||"small".equals(status)){
            if(sampledPixels<=0||nonBackground<0||nonBackground>sampledPixels||
               foregroundWidth<1||foregroundHeight<1){
                image="Недостоверные счётчики PixelCopy — изображение не подтверждено.";
            }else{
                image=String.format(Locale.ROOT,
                    "PixelCopy: %s · %d/%d отличающихся от фона пикселей · "+
                    "границы %d×%d. Это эвристический признак, а не сверка Skyrim.",
                    "small".equals(status)?"WARN: изображение слишком мало":
                                   "обнаружен рисунок на Vulkan-поверхности",
                    nonBackground,sampledPixels,foregroundWidth,foregroundHeight);
            }
        }else if("blank".equals(status)){
            pixelMismatch=true;
            image="PixelCopy: WARN — реальный Vulkan-кадр не содержит "+
                  "достаточных признаков видимой геометрии.";
        }else if("timeout".equals(status)){
            image="PixelCopy: WARN — копирование кадра превысило лимит ожидания.";
        }else if("error".equals(status)){
            image="PixelCopy: WARN — не удалось прочитать кадр Vulkan.";
        }else{
            image="PixelCopy: не проверен; нативные счётчики не подтверждают изображение.";
        }
        if(captureNote!=null&&!captureNote.isEmpty())
            image+="\nДетали PixelCopy: "+captureNote;
        if(pixelMismatch&&nativeReceipt.passed)
            return new Result(false,nativeReceipt.report+"\n"+image);
        return new Result(nativeReceipt.passed,nativeReceipt.report+"\n"+image);
    }

}
