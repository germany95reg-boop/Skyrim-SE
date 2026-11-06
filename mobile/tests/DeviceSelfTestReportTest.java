package dev.skyrimsemobile.launcher;

/** Synthetic report formatter regression. No Android SDK or Skyrim assets needed. */
public final class DeviceSelfTestReportTest {
    private static void ok(boolean valid,String message){
        if(!valid)throw new AssertionError(message);
    }
    public static void main(String[] args){
        DeviceSelfTestReport result=new DeviceSelfTestReport();
        result.pass("BSA", "744 файлов");
        result.warn("DDS", "2 из 3 найдено");
        result.fail("GPU", "Устройство не поддерживает Vulkan");
        result.skip("NIF", "Путь Data не выбран");
        String text=result.finish(2450);
        ok(result.passed()==1,"pass counter");
        ok(result.warnings()==1,"warning counter");
        ok(result.failed()==1,"failure counter");
        ok(result.skipped()==1,"skip counter");
        ok(text.contains("PASS 1 · WARN 1 · FAIL 1 · SKIP 1"),"summary counts");
        ok(text.contains("Длительность: 2.5 с"),"duration");
        ok(text.contains("Проверка проведена на настоящем Android-устройстве"),"hardware scope");
        ok(text.contains("не доказывает правильность всех пикселей"),"no false GPU claims");
        result.warn("User controlled\\nname", "line 1\nline 2\rline 3");
        String safe=result.finish(500);
        ok(!safe.contains("line 1\nline 2"),"report is line-safe");
        ok(safe.contains("line 1 line 2 line 3"),"message preserved in one line");
        System.out.println("PASS on-device self-test report: 10 checks");
    }
}
