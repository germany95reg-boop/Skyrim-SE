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
}
