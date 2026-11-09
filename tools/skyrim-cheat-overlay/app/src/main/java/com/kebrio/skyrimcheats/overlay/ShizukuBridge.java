package com.kebrio.skyrimcheats.overlay;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import rikka.shizuku.Shizuku;

/** User approved Shizuku connection. Never opens hidden system interfaces in the app process. */
public final class ShizukuBridge {
 private ShizukuBridge() {}
 private static volatile ICommandService remote;
 private static boolean binding;
 private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
 private static Runnable update;
 private static ServiceConnection conn;
 public static void setListener(Runnable l){update=l;}
 public static boolean ready(){return remote!=null&&remote.asBinder().pingBinder();}
 public static boolean running() {
  try { return Shizuku.pingBinder(); } catch (Throwable e) { return false; }
 }
 public static boolean permitted() {
  try { return running() && Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED; }
  catch(Throwable e){return false;}
 }
 private static void changed(){Runnable callback=update;if(callback!=null)callback.run();}
 public static synchronized void connect(Context ctx){
  if(ready()||binding||!permitted())return;
  binding=true;
  if(conn==null){
   conn=new ServiceConnection(){
    @Override public void onServiceConnected(ComponentName name,IBinder binder) {
     remote=ICommandService.Stub.asInterface(binder);
     binding=false;changed();
    }
    @Override public void onServiceDisconnected(ComponentName name){
     remote=null;binding=false;changed();
    }
   };
  }
  try {
   Shizuku.UserServiceArgs args=new Shizuku.UserServiceArgs(
      new ComponentName(ctx.getPackageName(), CommandUserService.class.getName()))
      .daemon(false).processNameSuffix("cheat_shell").tag("skyrim_ch")
      .version(3);
   Shizuku.bindUserService(args,conn);
  }catch(Throwable e){binding=false;changed();}
 }
 public static void send(String cmd,Consumer<String> callback){
  WORKER.execute(()->{
   String result;
   ICommandService service=remote;
   if(service==null||!permitted()){result="Нет подключения к Shizuku";}else {
    try{result=service.send(cmd);}catch(RemoteException ex){remote=null;result="Связь с Shizuku прервана";}
   }
   if(callback!=null)callback.accept(result);
  });
 }
}
