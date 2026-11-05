package dev.skyrimsemobile.launcher;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

/** Read-only Skyrim SE file inspector via Android Storage Access Framework. */
public final class ResourceInspectorActivity extends Activity {
    private static final int PICK_DATA=707, PICK_RESOURCE=708;
    public static final String PREF_REPORT="resource_inspector_report";
    private SharedPreferences prefs;
    private TextView report;
    private boolean closed=false;
    private String text="Готово к проверке. Выберите папку Data или отдельный файл BSA/NIF.\nФайлы не изменяются.";
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        prefs=getSharedPreferences("launcher_state",MODE_PRIVATE);
        getWindow().setStatusBarColor(0xff101115);
        getWindow().setNavigationBarColor(0xff101115);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackgroundColor(0xff101116);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(17),dp(16),dp(20));scroll.addView(root);
        TextView title=new TextView(this);title.setText("Ресурсы Skyrim SE · v2.3");
        title.setTextSize(22);title.setTextColor(0xffdbc5fb);title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView info=new TextView(this);
        info.setText("Чтение заголовков BSA/NIF, списка Data и каталога файлов внутри BSA 105. Никакой распаковки, изменения файлов или запуска Skyrim SE.");
        info.setTextColor(0xffd5d3dd);info.setTextSize(13);info.setPadding(0,dp(12),0,dp(14));root.addView(info);
        addButton(root,"Проверить папку игры / Data",()->scanCurrent());
        addButton(root,"Выбрать папку Data",()->selectFolder());
        addButton(root,"Проверить файл BSA / NIF",()->selectFile());
        addButton(root,"Каталог BSA v105 · выбор и проверка NIF",()->startActivity(new Intent(this,BsaCatalogActivity.class)));
        addButton(root,"Скопировать отчёт",()->{
            ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("Skyrim SE Resource Report",text));
        });
        addButton(root,"Вернуться в лаунчер",()->finish());
        report=new TextView(this);report.setTextColor(0xffeeeeee);report.setTextSize(14);
        report.setTextIsSelectable(true);report.setPadding(dp(12),dp(14),dp(12),dp(14));
        report.setBackgroundColor(0xff22232e);root.addView(report);
        setContentView(scroll);
        update(text);
    }
    private int dp(float d){return (int)(getResources().getDisplayMetrics().density*d+.5f);}
    private void addButton(LinearLayout p,String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(47));lp.bottomMargin=dp(6);p.addView(b,lp);
    }
    private void update(String s){text=s;if(closed)return;
        if(report!=null)report.setText(s);
        if(prefs!=null)prefs.edit().putString(PREF_REPORT,s.substring(0,Math.min(s.length(),10000))).apply();
    }
    private void selectFolder(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_DATA);
    }
    private void selectFile(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,PICK_RESOURCE);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(req==PICK_DATA){
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
            prefs.edit().putString("resource_data_uri",uri.toString()).apply();
            inspectTree(uri);
        } else if(req==PICK_RESOURCE)inspectDocument(uri);
    }
    private void scanCurrent(){
        String saved=prefs.getString("resource_data_uri","");
        if(saved.isEmpty())saved=prefs.getString("folder_uri","");
        if(saved.isEmpty()){update("Папка не выбрана. Выбери папку Skyrim SE или Data.");return;}
        inspectTree(Uri.parse(saved));
    }
    private void inspectDocument(Uri uri){
        update("Читаю заголовок выбранного файла…");
        new Thread(()->{
            try {
                String name=getName(uri);
                ResourceHeaderInspector.Result result;
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    result=ResourceHeaderInspector.inspect(in,name);
                }
                String text="Файл: "+name+"\n"+result.toString()+"\n\nТолько чтение первых 256 байт. Файл не изменён.";
                runOnUiThread(()->update(text));
            }catch(Exception e){runOnUiThread(()->update("Ошибка доступа: "+e.getClass().getSimpleName()+": "+e.getMessage()));}
        },"resource-header-reader").start();
    }
    private String getName(Uri uri){
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}
        return "Выбранный файл";
    }
    private void inspectTree(Uri tree){
        update("Сканирование Data (без изменения файлов)…");
        new Thread(()->{
            try{
                String rootId=DocumentsContract.getTreeDocumentId(tree);
                String rootName=nameDocument(tree,rootId);
                String targetId=rootId;
                if(!"data".equalsIgnoreCase(rootName)){
                    String found=findFolder(tree,rootId,"Data");
                    if(found!=null)targetId=found;
                }
                final String folder=targetId;
                ArrayList<Item> found=readChildren(tree,folder,3000);
                int bsas=0,ba2s=0,nifs=0,plugins=0,dirs=0,other=0;
                StringBuilder items=new StringBuilder();
                int lines=0,checked=0;
                for(Item it:found){
                    String n=it.name.toLowerCase(Locale.ROOT);
                    if(it.directory){dirs++;continue;}
                    boolean isBsa=n.endsWith(".bsa"),isBa2=n.endsWith(".ba2"),isNif=n.endsWith(".nif"),isPlugin=n.endsWith(".esm")||n.endsWith(".esp")||n.endsWith(".esl");
                    if(isBsa)bsas++;else if(isBa2)ba2s++;else if(isNif)nifs++;else if(isPlugin)plugins++;else other++;
                    if(isBsa && checked<4){
                        checked++;
                        try(InputStream in=getContentResolver().openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree,it.id))){
                            ResourceHeaderInspector.Result h=ResourceHeaderInspector.inspect(in,it.name);
                            if(lines++<20)items.append("• ").append(it.name).append(" — ").append(h.details).append('\n');
                        }catch(Exception e){if(lines++<20)items.append("• ").append(it.name).append(" — ошибка чтения: ").append(e.getClass().getSimpleName()).append('\n');}
                    } else if((isBsa||isBa2||isPlugin||isNif)&&lines++<20){
                        items.append("• ").append(it.name).append('\n');
                    }
                }
                String name=nameDocument(tree,folder);
                String msg="Папка: "+name+"\n"+
                    "Элементов верхнего уровня: "+found.size()+" (ограничение 3000)\n"+
                    "BSA: "+bsas+" · BA2: "+ba2s+" · ESM/ESP/ESL: "+plugins+"\n"+
                    "NIF непосредственно в папке: "+nifs+" · Подпапок: "+dirs+" · Остальных файлов: "+other+"\n\n"+
                    items+"\nNIF обычно лежат внутри BSA или вложенной папки meshes; рекурсивного поиска в v0.7 нет.\n"+
                    "Это только проверка ресурсов. Движок Skyrim SE не подключён.";
                runOnUiThread(()->update(msg));
            }catch(Exception e){runOnUiThread(()->update("Не удалось прочитать папку: "+e.getClass().getSimpleName()+" — "+e.getMessage()));}
        },"resource-tree-reader").start();
    }
    private String findFolder(Uri tree,String rootId,String name){
        for(Item i:readChildren(tree,rootId,3000))if(i.directory&&name.equalsIgnoreCase(i.name))return i.id;
        return null;
    }
    private String nameDocument(Uri tree,String id){
        Uri doc=DocumentsContract.buildDocumentUriUsingTree(tree,id);
        try(Cursor c=getContentResolver().query(doc,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}
        return "Data / выбранная папка";
    }
    private static final class Item {
        final String name,id;final boolean directory;
        Item(String name,String id,boolean directory){this.name=name;this.id=id;this.directory=directory;}
    }
    private ArrayList<Item> readChildren(Uri tree,String id,int limit){
        ArrayList<Item> items=new ArrayList<>();
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        try(Cursor c=getContentResolver().query(children,new String[]{
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        },null,null,null)){
            if(c==null)return items;
            while(c.moveToNext()&&items.size()<limit){
                String name=c.getString(0),itemId=c.getString(1),mime=c.getString(2);
                if(name!=null&&itemId!=null)items.add(new Item(name,itemId,DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)));
            }
        }
        return items;
    }
    @Override protected void onDestroy(){closed=true;super.onDestroy();}
}
