package com.kebrio.skyrimcheats.overlay;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import rikka.shizuku.Shizuku;
public final class MainActivity extends Activity {
 private static final int PERMISSION_CODE=3421;
 private TextView info;
 private final Shizuku.OnRequestPermissionResultListener permissionListener=(code,grant)->{
  if(code==PERMISSION_CODE)runOnUiThread(this::status);
 };
 private int dp(float a){return (int)(getResources().getDisplayMetrics().density*a+.5f);}
 private TextView tv(String text,int size,int color){
  TextView t=new TextView(this);t.setText(text);t.setTextSize(size);
  t.setTextColor(color);t.setPadding(0,dp(8),0,dp(9));return t;
 }
 private void button(LinearLayout root,String title,View.OnClickListener click){
  Button b=new Button(this);b.setText(title);b.setAllCaps(false);
  b.setTextSize(16);b.setTextColor(0xFFEBFCFF);
  GradientDrawable shape=new GradientDrawable();
  shape.setColor(0xFF17303B);shape.setCornerRadius(dp(12));shape.setStroke(dp(1),0xFF45D6E8);
  b.setBackground(shape);
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(55));p.topMargin=dp(11);
  root.addView(b,p);b.setOnClickListener(click);
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  getWindow().setStatusBarColor(0xFF071019);
  getWindow().setNavigationBarColor(0xFF071019);
  ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(0xFF071019);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);
  root.setPadding(dp(22),dp(18),dp(22),dp(26));sc.addView(root);
  root.addView(tv("SKYRIM // CHEAT OVERLAY",24,0xFF53E7FF));
  root.addView(tv("Версия 0.3 · Отдельное меню поверх Skyrim · оригинальный APK не меняется",14,0xFFBBCEDC));
  info=tv("",15,0xFFFFC88D);root.addView(info);
  root.addView(tv("Для системных нажатий нужен Shizuku. На Android без root запусти Shizuku через «Беспроводная отладка» в параметрах разработчика. Это отдельный шаг на телефоне.",14,0xFFDBE7F0));
  button(root,"1. Подключить и разрешить Shizuku",v->{
   try{
    if(!ShizukuBridge.running())
     startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/guide/setup/")));
    else if(!ShizukuBridge.permitted()) Shizuku.requestPermission(PERMISSION_CODE);
    else ShizukuBridge.connect(this);
   }catch(Throwable e){info.setText("Ошибка подключения Shizuku: "+e.getClass().getSimpleName());}
   status();
  });
  button(root,"2. Разрешить меню поверх других окон",v->{
   startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
  });
  button(root,"3. ПОКАЗАТЬ МЕНЮ",v->{
   if(!ShizukuBridge.permitted()){info.setText("Сначала предоставь разрешение Shizuku");return;}
   if(!Settings.canDrawOverlays(this)){info.setText("Сначала разреши отображение поверх других приложений");return;}
   try{
    ShizukuBridge.connect(this);
    Intent intent=new Intent(this,OverlayService.class);
    if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);
    info.setText("Меню активировано. Вернись в Skyrim, открой консоль ~ и нажми кнопку SKY.");
   }catch(Exception e){info.setText("Не удалось открыть меню: "+e.getMessage());}
  });
  button(root,"Убрать меню с экрана",v->{stopService(new Intent(this,OverlayService.class));status();});
  root.addView(tv("Команды отправляются через Android shell input по системному каналу Shizuku, а не через экранную клавиатуру. Проверяй сначала кнопку «~» и ввод tgm.",13,0xFF96B2C3));
  setContentView(sc);
  try{Shizuku.addRequestPermissionResultListener(permissionListener);}catch(Throwable ignored){}
  status();
 }
 @Override protected void onResume(){super.onResume();status();}
 private void status(){
  if(info==null)return;
  boolean shizuku=ShizukuBridge.running();
  boolean access=ShizukuBridge.permitted();
  boolean overlay=Settings.canDrawOverlays(this);
  info.setText("Shizuku: "+(shizuku?(access?"разрешён":"нет разрешения"):"не запущен")+
   "\nНаложение: "+(overlay?"разрешено":"нет разрешения"));
  if(access)ShizukuBridge.connect(this);
 }
 @Override protected void onDestroy(){
  try{Shizuku.removeRequestPermissionResultListener(permissionListener);}catch(Throwable ignored){}
  super.onDestroy();
 }
}
