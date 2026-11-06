package dev.skyrimsemobile.launcher;

import java.util.Locale;

/** Small bounded plain-text report; no SAF URIs, file bytes or credentials. */
public final class DeviceSelfTestReport {
    private final StringBuilder rows = new StringBuilder();
    private int passed, warnings, failed, skipped;

    private static String safe(String value){
        if(value==null)return "нет сведений";
        String s=value.replaceAll("[\\p{Cntrl}&&[^\n\t]]"," ")
                .replace('\n',' ').replace('\r',' ').trim();
        if(s.length()>260)s=s.substring(0,260)+"…";
        return s.isEmpty()?"нет сведений":s;
    }
    private void record(String verdict,String title,String detail){
        rows.append(verdict).append(" · ").append(safe(title))
            .append(": ").append(safe(detail)).append('\n');
    }
    public synchronized void pass(String title,String detail){passed++;record("PASS",title,detail);}
    public synchronized void warn(String title,String detail){warnings++;record("WARN",title,detail);}
    public synchronized void fail(String title,String detail){failed++;record("FAIL",title,detail);}
    public synchronized void skip(String title,String detail){skipped++;record("SKIP",title,detail);}

    public synchronized int passed(){return passed;}
    public synchronized int warnings(){return warnings;}
    public synchronized int failed(){return failed;}
    public synchronized int skipped(){return skipped;}

    public synchronized String finish(long elapsedMs){
        return "SKYRIM SE MOBILE · АВТОТЕСТ УСТРОЙСТВА\n"
            +"Результаты: PASS "+passed+" · WARN "+warnings+" · FAIL "+failed
            +" · SKIP "+skipped+"\n"
            +"Длительность: "+String.format(Locale.ROOT,"%.1f",Math.max(0,elapsedMs)/1000.0)
            +" с\n\n"
            +rows
            +"\nПроверка проведена на настоящем Android-устройстве без эмулятора.\n"
            +"Файлы игры не изменялись. Тест подтверждает доступ к данным и работу Vulkan,\n"
            +"но не доказывает правильность всех пикселей, FPS в игре или запуск SkyrimSE.exe.\n"
            +"Отчёт не отправлялся автоматически; пользователь решает, кому его передать.";
    }
}
