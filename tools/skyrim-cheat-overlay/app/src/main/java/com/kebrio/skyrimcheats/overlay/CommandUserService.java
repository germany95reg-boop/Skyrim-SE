package com.kebrio.skyrimcheats.overlay;

import android.os.Process;
import java.util.concurrent.TimeUnit;

/** Runs in the separate UID 2000 (adb shell) process launched by Shizuku. */
public final class CommandUserService extends ICommandService.Stub {
 public CommandUserService() { }
 @Override public String send(String cmd) {
  if (cmd == null || cmd.length() > 120) return "Команда отклонена";
  if (!CommandCatalog.isAllowed(cmd)) return "Команда не разрешена";
  try {
   if (cmd.equals("#console")) return execute("input","keyevent","68");
   if (cmd.equals("#enter")) return execute("input","keyevent","66");
   if (cmd.equals("#backspace")) return execute("input","keyevent","67");
   String text = cmd.replace(" ","%s");
   String result=execute("input","text",text);
   if (!result.equals("OK")) return result;
   Thread.sleep(140);
   return execute("input","keyevent","66");
  } catch (Exception e) { return "Ошибка ввода: "+e.getClass().getSimpleName(); }
 }
 private String execute(String... command) throws Exception {
  java.lang.Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
  if (!p.waitFor(9,TimeUnit.SECONDS)) {p.destroyForcibly(); return "Таймаут системного ввода";}
  if (p.exitValue()!=0) return "Системный ввод отклонён (код "+p.exitValue()+")";
  return "OK";
 }
}
