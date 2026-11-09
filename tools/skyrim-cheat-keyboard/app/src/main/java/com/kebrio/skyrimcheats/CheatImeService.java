package com.kebrio.skyrimcheats;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class CheatImeService extends InputMethodService {
    private static final int BG=0xFF071019, PANEL=0xFF10212D, CYAN=0xFF4DE4FE,
        WHITE=0xFFE8F4FC, GRAY=0xFF95ACBE, WARN=0xFFFFB47E;
    private boolean keyMode=true;
    private TextView status, mode;
    private long lastDangerTime=0;
    private String lastDanger="";
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int sp,int color,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);
        v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    private TextView button(String s,int border,Runnable callback){
        TextView b=text(s,13,WHITE,true);b.setGravity(Gravity.CENTER);
        b.setPadding(dp(5),dp(9),dp(5),dp(9));b.setMinHeight(dp(45));
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(PANEL);shape.setCornerRadius(dp(10));
        shape.setStroke(dp(1),border);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x444DE4FE),shape,null));
        b.setOnClickListener(v->callback.run());return b;
    }
    private void section(LinearLayout p,String s){
        TextView t=text(s,14,CYAN,true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(14);p.addView(t,lp);
    }
    private void row(LinearLayout parent,String... values){
        // alternating button titles and console commands
        LinearLayout line=new LinearLayout(this);line.setOrientation(0);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);
        rp.topMargin=dp(7);parent.addView(line,rp);
        for(int i=0;i+1<values.length;i+=2){
            final String command=values[i+1];
            TextView b=button(values[i],command.equals("psb")?WARN:0xFF2B6B7F,()->dispatch(command));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);
            if(i>0)lp.leftMargin=dp(5);
            line.addView(b,lp);
        }
    }
    @Override public View onCreateInputView(){
        int width=getResources().getConfiguration().screenWidthDp;
        int available=getResources().getConfiguration().screenHeightDp;
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        LinearLayout content=new LinearLayout(this);content.setOrientation(1);
        content.setPadding(dp(10),dp(8),dp(10),dp(13));
        scroll.addView(content);
        LinearLayout top=new LinearLayout(this);top.setOrientation(0);
        top.setGravity(Gravity.CENTER_VERTICAL);content.addView(top);
        TextView title=text("SKYRIM / CHEATS",16,CYAN,true);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(43),1));
        mode=button("КЛАВИШИ",CYAN,()->{
            keyMode=!keyMode;mode.setText(keyMode?"КЛАВИШИ":"ТЕКСТ");
            setStatus(keyMode?"Ввод событиями клавиш":"Ввод через commitText");
        });
        top.addView(mode,new LinearLayout.LayoutParams(dp(width>=600?185:120),dp(42)));
        status=text("Открой консоль Skyrim (~), затем выбери чит",12,GRAY,false);
        status.setSingleLine(true);status.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(status,new LinearLayout.LayoutParams(-1,dp(31)));
        section(content,"РЕЖИМЫ");
        row(content,"Бессмертие ↕","tgm","Скорость 150%","player.setav speedmult 150","Скорость 100%","player.setav speedmult 100");
        row(content,"Вес +10 000","player.modav carryweight 10000","Вес −10 000","player.modav carryweight -10000");
        row(content,"Без отката криков","player.forceav shoutrecoverymult 0","Вернуть откат","player.forceav shoutrecoverymult 1");
        section(content,"ПРЕДМЕТЫ И СПОСОБНОСТИ");
        row(content,"100 000 золота","player.additem 0000000F 100000","1000 отмычек","player.additem 0000000A 1000");
        row(content,"100 душ","player.modav dragonsouls 100","Открыть объект","unlock","Все заклинания*","psb");
        section(content,"НАВЫКИ = 100");
        row(content,"Алхимия","player.setav alchemy 100","Кузнечное дело","player.setav smithing 100");
        row(content,"Зачарование","player.setav enchanting 100","Изменение","player.setav alteration 100");
        row(content,"Колдовство","player.setav conjuration 100","Разрушение","player.setav destruction 100");
        row(content,"Иллюзия","player.setav illusion 100","Восстановление","player.setav restoration 100");
        section(content,"УПРАВЛЕНИЕ");
        row(content,"Enter","!ENTER","Backspace","!DEL","Консоль ~","!GRAVE");
        TextView help=text("* psb: двойное нажатие за 4 секунды.\nПеред использованием сделай отдельное сохранение.\nЭто клавиатура, а не редактор памяти игры: состояние читов неизвестно. Нельзя автоматически отменить выдачу предметов и навыки.",12,WARN,false);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(12);
        content.addView(help,hp);
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(1);
        outer.setBackgroundColor(BG);
        int height=Math.max(180,Math.min(400,(int)(available*.48f)));
        outer.addView(scroll,new LinearLayout.LayoutParams(-1,dp(height)));
        return outer;
    }
    private void setStatus(String s){if(status!=null)status.setText(s);}
    private void dispatch(String cmd){
        if("psb".equals(cmd)){
            long now=SystemClock.elapsedRealtime();
            if(!"psb".equals(lastDanger)||now-lastDangerTime>4000){
                lastDanger="psb";lastDangerTime=now;
                setStatus("Для psb нажми повторно в течение 4 сек");return;
            }
            lastDanger="";
        }
        boolean ok;
        switch(cmd){
            case "!ENTER":ok=sendKey(KeyEvent.KEYCODE_ENTER);break;
            case "!DEL":ok=sendKey(KeyEvent.KEYCODE_DEL);break;
            case "!GRAVE":ok=sendKey(KeyEvent.KEYCODE_GRAVE);break;
            default:ok=sendCommand(cmd);
        }
        setStatus(ok?"Отправлено: "+cmd:"Нет ввода или приложение отклонило команду");
        if(!ok)Toast.makeText(this,"Открой игровую консоль и вызови экранную клавиатуру",Toast.LENGTH_SHORT).show();
    }
    private boolean sendCommand(String s){
        InputConnection ic=getCurrentInputConnection();if(ic==null)return false;
        boolean ok=false;
        if(keyMode){
            try{
                KeyEvent[] events=KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(s.toCharArray());
                if(events!=null && events.length>0){
                    ok=true;
                    for(KeyEvent e:events)if(!ic.sendKeyEvent(e))ok=false;
                }
            }catch(Exception ignored){ok=false;}
        }else{ok=ic.commitText(s,1);}
        if(!ok)return false;
        return sendKey(KeyEvent.KEYCODE_ENTER);
    }
    private boolean sendKey(int code){
        InputConnection ic=getCurrentInputConnection();if(ic==null)return false;
        long now=SystemClock.uptimeMillis();
        KeyEvent down=new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0);
        KeyEvent up=new KeyEvent(now,now,KeyEvent.ACTION_UP,code,0);
        return ic.sendKeyEvent(down) & ic.sendKeyEvent(up);
    }
}
