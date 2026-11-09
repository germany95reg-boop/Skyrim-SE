package com.kebrio.skyrimcheats;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private int dp(float v){return (int)(v * getResources().getDisplayMetrics().density + .5f);}
    private TextView text(String s,int sp,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);t.setTextColor(color);t.setTextSize(sp);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(dp(0),dp(10),dp(0),dp(10));return t;
    }
    private void button(LinearLayout root,String s,View.OnClickListener action){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);
        b.setTextSize(15);b.setTextColor(0xFFEFFFFF);
        GradientDrawable d=new GradientDrawable();d.setColor(0xFF102B39);
        d.setCornerRadius(dp(12));d.setStroke(dp(1),0xFF31C9DD);b.setBackground(d);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(53));
        p.topMargin=dp(10);root.addView(b,p);b.setOnClickListener(action);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF071019);
        getWindow().setNavigationBarColor(0xFF071019);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);
        sc.setBackgroundColor(0xFF071019);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(dp(20),dp(25),dp(20),dp(25));
        sc.addView(root);
        root.addView(text("SKYRIM // CHEAT KEYBOARD",22,0xFF4DE4FE,true));
        root.addView(text("Отдельное Android-приложение. APK Skyrim и его подпись не изменяются.",15,0xFFCCDCE8,false));
        root.addView(text("1. Разреши клавиатуру в настройках Android.\n2. Выбери её как текущую.\n3. Открой консоль ~ в Skyrim и вызови экранную клавиатуру.\n4. Нажимай команды на панели читов.",15,0xFFBBD0DF,false));
        button(root,"1  Включить чит-клавиатуру",v->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button(root,"2  Выбрать чит-клавиатуру",v->{
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm!=null)imm.showInputMethodPicker();
        });
        root.addView(text("Проверка ввода: тапни поле ниже, выбери чит-клавиатуру и нажми кнопку. Это проверит её работу с обычным полем Android, но не гарантирует совместимость с консолью Skyrim.",13,0xFFFFBD8B,false));
        EditText test=new EditText(this);test.setSingleLine(true);
        test.setHint("Здесь появится команда");test.setHintTextColor(0xFF8FABB8);
        test.setTextColor(0xFFFFFFFF);
        test.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        root.addView(test);
        root.addView(text("Работает только при активном поле ввода, которое принимает Android IME. Без root, сети, рекламы и доступа к данным игры. Используй эту клавиатуру только для читов.",13,0xFF9CB1C1,false));
        setContentView(sc);
    }
}
