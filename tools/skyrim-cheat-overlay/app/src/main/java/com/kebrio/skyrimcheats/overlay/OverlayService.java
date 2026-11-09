package com.kebrio.skyrimcheats.overlay;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OverlayService extends Service {
 private static final int BG=0xF8071119, PANEL=0xFF112C38, CYAN=0xFF52E6FF,
    WHITE=0xFFF0F8FF, MUTED=0xFF95B1BF, AMBER=0xFFFFBE8C;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final AtomicBoolean sending=new AtomicBoolean(false);
 private WindowManager wm;
 private WindowManager.LayoutParams params;
 private FrameLayout frame;
 private TextView status;
 private boolean expanded=false;
 private String lastStatus="Открой консоль Skyrim через ~";
 private String pending="";
 private long pendingAt=0;
 private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
 @Override public void onCreate(){
  super.onCreate();
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  nm.createNotificationChannel(new NotificationChannel("skycheats","Skyrim cheat overlay",NotificationManager.IMPORTANCE_LOW));
  Notification n=new Notification.Builder(this,"skycheats")
    .setContentTitle("Skyrim Cheat Overlay").setContentText("Плавающее меню активно")
    .setSmallIcon(android.R.drawable.ic_menu_manage).setOngoing(true).build();
  if(Build.VERSION.SDK_INT>=34)startForeground(801,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
  else startForeground(801,n);
  ShizukuBridge.setListener(()->handler.post(this::updateStatus));
  ShizukuBridge.connect(this);
  wm=(WindowManager)getSystemService(WINDOW_SERVICE);
  frame=new FrameLayout(this);
  params=new WindowManager.LayoutParams(dp(58),dp(58),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
    PixelFormat.TRANSLUCENT);
  params.gravity=Gravity.TOP|Gravity.START;params.x=dp(12);params.y=dp(135);
  draw();
  try{wm.addView(frame,params);}catch(Exception e){stopSelf();}
 }
 private TextView label(String text,int size,int color,boolean bold){
  TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);
  v.setGravity(Gravity.CENTER_VERTICAL);
  if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
  return v;
 }
 private TextView key(String text,int stroke,Runnable listener){
  TextView v=label(text,13,WHITE,true);v.setGravity(Gravity.CENTER);
  v.setPadding(dp(7),dp(9),dp(7),dp(9));v.setMinHeight(dp(43));
  GradientDrawable shape=new GradientDrawable();shape.setColor(PANEL);
  shape.setCornerRadius(dp(10));shape.setStroke(dp(1),stroke);
  v.setBackground(shape);
  v.setOnClickListener(w->listener.run());return v;
 }
 private void section(LinearLayout col,String text) {
  TextView t=label(text,13,CYAN,true);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(30));lp.topMargin=dp(9);
  col.addView(t,lp);
 }
 private void row(LinearLayout parent,String... pair){
  LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);
  LinearLayout.LayoutParams linep=new LinearLayout.LayoutParams(-1,-2);linep.topMargin=dp(6);
  parent.addView(line,linep);
  for(int i=0;i<pair.length-1;i+=2){
   final String cmd=pair[i+1];
   TextView k=key(pair[i],cmd.equals("psb")?0xFFB27445:0xFF316981,()->send(cmd));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);
   if(i!=0)lp.leftMargin=dp(6);
   line.addView(k,lp);
  }
 }
 private void draw(){
  if(frame==null)return;
  frame.removeAllViews();status=null;
  if(!expanded){
   TextView bubble=key("SKY",CYAN,()->{expanded=true;draw();});
   bubble.setTextSize(15);
   GradientDrawable shape=new GradientDrawable();shape.setShape(GradientDrawable.OVAL);
   shape.setColor(0xFF0E2938);shape.setStroke(dp(2),CYAN);bubble.setBackground(shape);
   bubble.setOnTouchListener(new View.OnTouchListener(){
    float x0,y0;int startX,startY;boolean moved;
    @Override public boolean onTouch(View v,MotionEvent e){
     switch(e.getActionMasked()){
      case MotionEvent.ACTION_DOWN:
       x0=e.getRawX();y0=e.getRawY();startX=params.x;startY=params.y;moved=false;return true;
      case MotionEvent.ACTION_MOVE:
       if(Math.abs(e.getRawX()-x0)>dp(7)||Math.abs(e.getRawY()-y0)>dp(7))moved=true;
       if(moved){params.x=Math.max(0,startX+Math.round(e.getRawX()-x0));
        params.y=Math.max(0,startY+Math.round(e.getRawY()-y0));
        wm.updateViewLayout(frame,params);}
       return true;
      case MotionEvent.ACTION_UP:
       if(!moved){expanded=true;draw();}
       return true;
      default:return false;
     }
    }
   });
   frame.addView(bubble,new FrameLayout.LayoutParams(-1,-1));
  }else{
   ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
   LinearLayout col=new LinearLayout(this);col.setPadding(dp(12),dp(9),dp(12),dp(11));
   col.setOrientation(LinearLayout.VERTICAL);scroll.addView(col);
   LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);
   col.addView(head,new LinearLayout.LayoutParams(-1,dp(40)));
   TextView title=label("SKYRIM // CHEATS",16,CYAN,true);
   head.addView(title,new LinearLayout.LayoutParams(0,-1,1));
   TextView close=key("−",CYAN,()->{expanded=false;draw();});
   close.setTextSize(21);head.addView(close,new LinearLayout.LayoutParams(dp(48),dp(39)));
   status=label("",12,MUTED,false);
   col.addView(status,new LinearLayout.LayoutParams(-1,dp(46)));updateStatus();
   row(col,"~ Консоль","#console","Enter","#enter","⌫","#backspace");
   section(col,"РЕЖИМЫ");
   row(col,"Бессмертие ↕","tgm");
   row(col,"Скорость 150%","player.setav speedmult 150","Скорость 100%","player.setav speedmult 100");
   row(col,"Вес +10 000","player.modav carryweight 10000","Вес −10 000","player.modav carryweight -10000");
   row(col,"Крики без отката","player.forceav shoutrecoverymult 0","Откат обратно","player.forceav shoutrecoverymult 1");
   section(col,"ПРЕДМЕТЫ И КОМАНДЫ");
   row(col,"100 000 золота","player.additem 0000000F 100000","1000 отмычек","player.additem 0000000A 1000");
   row(col,"100 душ драконов","player.modav dragonsouls 100","Открыть объект","unlock");
   row(col,"Все заклинания*","psb");
   section(col,"НАВЫКИ 100");
   row(col,"Алхимия","player.setav alchemy 100","Кузнечное","player.setav smithing 100");
   row(col,"Зачарование","player.setav enchanting 100","Изменение","player.setav alteration 100");
   row(col,"Колдовство","player.setav conjuration 100","Разрушение","player.setav destruction 100");
   row(col,"Иллюзия","player.setav illusion 100","Восстановление","player.setav restoration 100");
   TextView note=label("* psb требует двойного нажатия. Сохрани игру перед использованием. Кнопки не считывают состояние Skyrim.",12,AMBER,false);
   note.setPadding(0,dp(12),0,dp(10));col.addView(note);
   frame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
  }
  if(wm!=null&&params!=null){
   if(expanded){
    android.graphics.Rect bounds=wm.getCurrentWindowMetrics().getBounds();
    params.width=Math.min(dp(364),Math.max(dp(210),bounds.width()-dp(26)));
    params.height=Math.min(dp(620),Math.max(dp(250),(int)(bounds.height()*.67)));
   }else{params.width=dp(58);params.height=dp(58);}
   try{wm.updateViewLayout(frame,params);}catch(Exception ignored){}
  }
 }
 private void updateStatus(){
  if(status==null||!expanded)return;
  String prefix=ShizukuBridge.ready()?"SHIZUKU OK · ":"SHIZUKU НЕ ПОДКЛЮЧЁН · ";
  status.setText(prefix+lastStatus);
 }
 private void send(String cmd){
  if(!ShizukuBridge.ready()){
   ShizukuBridge.connect(this);
   lastStatus="Подключение... нажми ещё раз";updateStatus();return;
  }
  if(cmd.equals("psb")){
   long now=SystemClock.elapsedRealtime();
   if(!pending.equals("psb")||now-pendingAt>4000){
    pending=cmd;pendingAt=now;lastStatus="Повтори psb за 4 секунды";updateStatus();return;
   }
   pending="";
  }
  if(!sending.compareAndSet(false,true)){lastStatus="Предыдущая команда выполняется";updateStatus();return;}
  lastStatus="Отправка: "+cmd;updateStatus();
  ShizukuBridge.send(cmd,result->handler.post(()->{
   sending.set(false);
   lastStatus=result.equals("OK")?"Системное нажатие отправлено: "+cmd:result;
   updateStatus();
  }));
 }
 @Override public int onStartCommand(Intent intent,int flags,int id){return START_NOT_STICKY;}
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public void onDestroy(){
  if(frame!=null&&wm!=null){try{wm.removeView(frame);}catch(Exception ignored){}}
  ShizukuBridge.setListener(null);super.onDestroy();
 }
}
