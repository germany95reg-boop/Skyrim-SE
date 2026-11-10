package dev.skyrimsemobile.launcher;

import java.util.Locale;

/**
 * v2.45: checks the ACTUAL placed Skyrim NIF scene launched by the one-folder
 * workflow. Cannot claim game-faithful colors, UV layout or lighting: those
 * require comparison to a trusted reference image on real hardware.
 */
public final class Tes4AutomaticSceneReceipt {
    private Tes4AutomaticSceneReceipt(){}

    public static final class Result {
        public final boolean passed;
        public final int passedChecks,warnings,failedChecks;
        public final String report;
        Result(int passed,int warnings,int failed,String report){
            this.passed=failed==0;
            passedChecks=passed;this.warnings=warnings;failedChecks=failed;
            this.report=report;
        }
    }
    private static final class Checks {
        final StringBuilder lines=new StringBuilder();
        int passed,warnings,failed;
        void pass(String name,String info){passed++;row("PASS",name,info);}
        void warn(String name,String info){warnings++;row("WARN",name,info);}
        void fail(String name,String info){failed++;row("FAIL",name,info);}
        void row(String state,String name,String info){
            lines.append('[').append(state).append("] ").append(name)
                .append(": ").append(info).append('\n');
        }
    }
    /**
     * The arrays must contain one entry for each requested FPS cap (30,60,0).
     * A missing measurement is a FAIL, never silently counted as a PASS.
     */
    public static Result assess(String expected,String actual,boolean completed,
          boolean running,boolean nativeError,long frames,int edges,
          int triangles,int expectedTriangles,int width,int height,double fps,
          boolean expectedAtlas,String textureMode,String pixelStatus,
          int nonBackground,int sampledPixels,int[] fpsCaps,boolean[] fpsPassed,
          String captureNote,String runtimeError){
        Checks c=new Checks();
        c.lines.append("Полный автоматический тест ЛОКАЛЬНОЙ сцены Skyrim SE\n");
        if(expected==null||expected.isEmpty()||!expected.equals(actual))
            c.fail("Настоящая NIF/REFR","Вместо ожидаемой сцены "+
                String.valueOf(expected)+" рендерер сообщил "+String.valueOf(actual));
        else c.pass("Настоящая NIF/REFR","Точное совпадение метки реальной сцены");
        if(!completed||nativeError||!running||frames<45||edges<=0||
           triangles<1||triangles!=expectedTriangles||width<=0||height<=0||
           !Double.isFinite(fps)||fps<0)
            c.fail("Vulkan GPU",String.format(Locale.ROOT,
                "Выполнено: %s; кадров %d, рёбер %d, треугольников %d/%d, "+
                "Surface %d×%d, FPS %.1f; ошибка: %s",completed,frames,edges,
                triangles,expectedTriangles,width,height,fps,String.valueOf(runtimeError)));
        else c.pass("Vulkan GPU",String.format(Locale.ROOT,
            "%d реальных кадров, %d рёбер, %d/%d треугольников, "+
            "Surface %d×%d, FPS %.1f",frames,edges,triangles,expectedTriangles,
            width,height,fps));

        String mode=textureMode==null?"off":textureMode;
        if(expectedAtlas){
            if(!mode.equals("off"))c.pass("Диффузные DDS",
                "GPU сообщил текстурный режим "+mode);
            else c.fail("Диффузные DDS",
                "Атлас подготовлен, но GPU отключил текстуры");
        }else c.warn("Диффузные DDS",
            "Для выбранных REFR не подготовлен атлас; геометрия проверяется без DDS");

        int[] required={30,60,0};
        for(int cap:required){
            Boolean stage=null;
            if(fpsCaps!=null&&fpsPassed!=null)
                for(int i=0;i<Math.min(fpsCaps.length,fpsPassed.length);i++)
                    if(fpsCaps[i]==cap){stage=fpsPassed[i];break;}
            String label="FPS "+(cap==0?"без ограничения":cap);
            if(Boolean.TRUE.equals(stage))c.pass(label,"Нативный режим подтверждён кадрами");
            else c.fail(label,stage==null?
                "Измерение отсутствует": "Лимит, длительность или счётчик кадров не подтверждены");
        }

        String status=pixelStatus==null?"not-requested":pixelStatus;
        if("visible".equals(status)&&sampledPixels>0&&nonBackground>0&&
           nonBackground<=sampledPixels)
            c.pass("PixelCopy",nonBackground+"/"+sampledPixels+
                " пикселей отличаются от фона Vulkan SurfaceView");
        else if("blank".equals(status))
            c.fail("PixelCopy","Кадр есть, но изображение модели не подтверждено");
        else if("small".equals(status))
            c.warn("PixelCopy","Объект занимает небольшую область кадра: "+
                String.valueOf(captureNote));
        else c.warn("PixelCopy","Нет убедительного снимка поверхности: "+
            status+"; "+String.valueOf(captureNote));
        c.lines.append(String.format(Locale.ROOT,
            "Итог: PASS %d · WARN %d · FAIL %d\n",
            c.passed,c.warnings,c.failed));
        c.lines.append("PixelCopy показывает наличие пикселей, но не доказывает "+
            "правильность текстур, освещения, ландшафта или совпадение с оригинальной игрой. "+
            "Файлы Skyrim остаются без изменений.");
        return new Result(c.passed,c.warnings,c.failed,c.lines.toString());
    }
}
