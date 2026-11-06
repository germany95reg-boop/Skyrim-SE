package dev.skyrimsemobile.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.widget.ImageView;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Read-only BSA metadata and bounded single NIF header probe; never modifies files. */
public final class BsaCatalogActivity extends Activity {
    private static final int PICK_BSA=801;
    private static final int PICK_DATA=802;
    private static final int MAX_BSA_ARCHIVES=48, MAX_DATA_CHILDREN=4096;
    private List<String> materialDdsPaths=Collections.emptyList();
    private String materialNifPath="";
    private Uri materialBsaUri;
    private List<LocatedDds> foundDds=Collections.emptyList();
    private String lastDdsSearchReport="";
    /** True per-shape links retained for an optional indexed DDS search. */
    private NifMaterialLinkInspector.Result partMaterialLinks;
    private String partMaterialNifPath="";
    private BsaCatalogReader.Catalog catalog;
    private TextView summary,results,inspectionOutput,selectedLabel;
    private String selectedNifPath="";
    private Uri selectedNifArchive;
    private Uri currentUri;
    private EditText filter;
    private CheckBox nifOnly;
    private String currentName="";
    private BsaSessionStore savedSession;
    private long archiveLoadSequence=0;
    private volatile boolean destroyed=false;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        savedSession=new BsaSessionStore(this);
        getWindow().setStatusBarColor(0xff101115);
        getWindow().setNavigationBarColor(0xff101115);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff101116);scroll.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(14),dp(16),dp(18));scroll.addView(root);
        TextView title=text("Skyrim SE · NIF v2.18",22,0xffdbc5fb);title.setGravity(Gravity.CENTER);root.addView(title);
        root.addView(text("BSA 105: каталог, NIF и Vulkan. Последний разрешённый архив, модель и папка Data восстанавливаются автоматически. Содержимое файлов Skyrim не копируется и не изменяется.",13,0xffd5d3dd));
        button(root,"Запустить полный автотест на телефоне · v2.15",()->
            startActivity(new Intent(this,DeviceSelfTestActivity.class)));
        button(root,"Выбрать архив BSA",this::pick);
        button(root,"Забыть сохранённые пути · v2.11",this::forgetSavedPaths);
        filter=new EditText(this);filter.setSingleLine(true);filter.setHint("Поиск: meshes, .nif, fish...");
        filter.setTextColor(0xffeeeeee);filter.setHintTextColor(0xffaaa4b8);root.addView(filter);
        nifOnly=new CheckBox(this);nifOnly.setText("Только модели .nif");nifOnly.setTextColor(0xffeeeeee);
        root.addView(nifOnly);
        selectedLabel=text("NIF не выбрана. Можно нажать имя в результатах или открыть список.",13,0xffd9c1ff);
        selectedLabel.setTextIsSelectable(true);
        root.addView(selectedLabel);
        button(root,"Выбрать NIF из списка · v2.11",this::showNifChooser);
        button(root,"Недавние модели NIF · v2.11",this::showRecentNif);
        filter.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int cnt,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int count){
                // Manual edits invalidate the explicit choice and DDS cache.
                if(!selectedNifPath.isEmpty()&&!selectedNifPath.equals(s.toString())){
                    selectedNifPath="";selectedNifArchive=null;
                    clearMaterialSelection();
                    selectedLabel.setText("NIF не выбрана. Выбери модель в списке.");
                }
                renderResults();
            }
            public void afterTextChanged(Editable e){}
        });
        nifOnly.setOnCheckedChangeListener((b,value)->renderResults());
        button(root,"Прочитать первый найденный NIF (только заголовок)",this::inspectFirstNif);
        button(root,"Исследовать блоки выбранного NIF · v1.0",this::inspectNifBlocks);
        button(root,"Показать BSTriShape в Vulkan · v2.11",this::previewNifGeometry);
        button(root,"Части NIF · выбрать BSTriShape · v2.11",this::selectNifPart);
        button(root,"Показать все части NIF в Vulkan · v2.11",this::previewCombinedNif);
        button(root,"Связи материалов частей NIF · v2.11",this::inspectPartMaterialLinks);
        button(root,"Найти DDS каждой части NIF · v2.11",this::findPartDds);
        button(root,"Показать выбранную часть NIF с её DDS · v2.12",this::previewTexturedPart);
        button(root,"Показать ВСЕ части NIF с DDS · v2.18",this::previewCompositeDds);
        button(root,"Пути текстур NIF · v2.11",this::inspectNifMaterials);
        button(root,"Найти DDS в папке Data · v2.11",this::pickDataForDds);
        button(root,"Изменить папку Data · v2.11",this::chooseDataFolder);
        button(root,"Заголовки найденных DDS · v2.11",this::inspectFoundDdsHeaders);
        button(root,"Показать текстуры DDS · BC1/2/3/4/5 · v2.11",this::previewFoundDds);
        button(root,"Показать NIF с DDS в Vulkan · v2.11",this::previewTexturedNif);
        button(root,"Скопировать список и отчёт",this::copyReport);
        button(root,"Назад",this::finish);
        summary=text("Выбери BSA-файл из памяти телефона. Архив будет открыт только на чтение.",14,0xffeeeeee);
        summary.setTextIsSelectable(true);summary.setPadding(dp(8),dp(10),dp(8),dp(10));
        root.addView(summary);
        results=text("",12,0xffe6e2ed);
        results.setPadding(dp(8),dp(10),dp(8),dp(10));
        results.setTextIsSelectable(false);
        results.setLinksClickable(true);
        results.setMovementMethod(LinkMovementMethod.getInstance());
        root.addView(results);
        inspectionOutput=text("Выбранный NIF ещё не проверялся.",13,0xffd9c1ff);
        inspectionOutput.setTextIsSelectable(true);root.addView(inspectionOutput);
        setContentView(scroll);
        restoreSavedSession();
    }
    private int dp(float a){return (int)(a*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(sp);t.setPadding(0,dp(9),0,dp(9));return t;}
    private void button(LinearLayout root,String caption,Runnable action){
        Button b=new Button(this);b.setText(caption);b.setAllCaps(false);b.setOnClickListener(v->action.run());root.addView(b);
    }
    /** Clear only the derived DDS lookup state; never touch the game archive. */
    private void clearMaterialSelection(){
        materialDdsPaths=Collections.emptyList();materialNifPath="";materialBsaUri=null;
        foundDds=Collections.emptyList();lastDdsSearchReport="";
        partMaterialLinks=null;partMaterialNifPath="";
    }
    /** Explicitly selected entry wins over a substring search; the exact path
     * is retained so identically named models in different folders stay distinct. */
    private List<BsaCatalogReader.Entry> selectedNifMatches(){
        String path=selectedNifArchive!=null&&selectedNifArchive.equals(currentUri)
            ?selectedNifPath:"";
        return NifCatalogSelection.matches(catalog,path,filter.getText().toString());
    }
    private boolean isCurrentNif(Uri uri,BsaCatalogReader.Entry entry){
        if(destroyed||!uri.equals(currentUri))return false;
        List<BsaCatalogReader.Entry> chosen=selectedNifMatches();
        return !chosen.isEmpty()&&chosen.get(0).path.equals(entry.path);
    }
    private void chooseNif(BsaCatalogReader.Entry entry){
        if(entry==null||!entry.nif()||catalog==null||currentUri==null||
            !catalog.entries.contains(entry))return;
        clearMaterialSelection();
        selectedNifPath=entry.path;selectedNifArchive=currentUri;
        if(!nifOnly.isChecked())nifOnly.setChecked(true);
        filter.setText(entry.path);
        filter.setSelection(filter.length());
        savedSession.saveModel(currentUri,entry.path);
        selectedLabel.setText("Выбрана NIF: "+entry.path+
            "\nТеперь кнопки частей, общей сцены и DDS используют именно этот файл.");
        inspectionOutput.setText("Выбрана модель: "+entry.path+
            "\nДля составной геометрии нажми «Показать все части NIF в Vulkan». "+
            "Для DDS сначала прочитай «Пути текстур NIF».");
        renderResults();
    }
    private void showNifChooser(){
        if(catalog==null||currentUri==null){
            inspectionOutput.setText("Сначала выбери BSA и дождись чтения каталога.");return;
        }
        List<BsaCatalogReader.Entry> options=catalog.find(
            filter.getText().toString(),true,60);
        if(options.isEmpty()){
            inspectionOutput.setText("По этому запросу NIF не найдены. "+
                "Очисти поиск или введи часть имени файла.");return;
        }
        String[] labels=new String[options.size()];
        for(int i=0;i<labels.length;i++)labels[i]=options.get(i).path;
        new AlertDialog.Builder(this)
            .setTitle("Выбери NIF · "+options.size()+" совпадений")
            .setItems(labels,(dialog,index)->chooseNif(options.get(index)))
            .setNegativeButton("Отмена",null).show();
    }
    /** Last eight exact NIF paths within the currently open BSA. */
    private void showRecentNif(){
        if(catalog==null||currentUri==null){
            inspectionOutput.setText("Сначала открой архив BSA.");return;
        }
        List<String> recent=savedSession.recentModels(currentUri);
        if(recent.isEmpty()){
            inspectionOutput.setText("Недавних моделей пока нет. Выбери NIF из каталога.");
            return;
        }
        String[] names=recent.toArray(new String[0]);
        new AlertDialog.Builder(this)
            .setTitle("Недавние NIF · до 8 моделей")
            .setItems(names,(dialog,index)->{
                List<BsaCatalogReader.Entry> found=NifCatalogSelection.matches(
                    catalog,names[index],"");
                if(found.isEmpty()){
                    inspectionOutput.setText("Модель больше не найдена в BSA: "+names[index]);
                    return;
                }
                chooseNif(found.get(0));
            })
            .setNegativeButton("Закрыть",null).show();
    }

    /** Auto-reopen the last archive only when the Android SAF read grant survives
     * a process restart. Earlier (v2.8) selections need to be chosen once again. */
    private void restoreSavedSession(){
        Uri savedArchive=savedSession.lastArchive();
        Uri savedTree=savedSession.lastDataTree();
        if(savedArchive==null){
            inspectionOutput.setText("Сохранённого BSA с постоянным доступом пока нет. "+
                "Выбери архив один раз в v2.11; затем он будет открываться автоматически."+
                (savedTree!=null?"\nРазрешение на папку Data сохранено.":""));
            return;
        }
        openCatalog(savedArchive,savedSession.archiveDisplayName(),true);
    }

    /** One async loader for manual and automatic selection; prevents a stale
     * worker from replacing a more recently selected archive. */
    private void openCatalog(Uri uri,String displayName,boolean restored){
        final long request=++archiveLoadSequence;
        summary.setText((restored?"Восстанавливаю последний BSA: ":"Читаю BSA: ")+
            displayName+"…");
        results.setText("");
        catalog=null;currentUri=null;
        selectedNifPath="";selectedNifArchive=null;
        clearMaterialSelection();
        selectedLabel.setText("NIF не выбрана. Проверяю содержимое архива…");
        inspectionOutput.setText("Чтение каталога BSA только для просмотра.");
        new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("Нет доступа к сохранённому архиву");
                BsaCatalogReader.Catalog found=BsaCatalogReader.read(in);
                runOnUiThread(()->{
                    if(destroyed||request!=archiveLoadSequence)return;
                    currentName=displayName;catalog=found;currentUri=uri;
                    // Persist archive only if the SAF grant is durable.
                    if(savedSession.hasReadPermission(uri))
                        savedSession.saveArchive(uri,displayName);
                    String priorNif=savedSession.lastModel(uri);
                    List<BsaCatalogReader.Entry> exact=
                        NifCatalogSelection.matches(found,priorNif,"");
                    boolean valid=!priorNif.isEmpty()&&!exact.isEmpty()&&
                        priorNif.equals(exact.get(0).path);
                    if(valid){
                        // Set the EditText first so its watcher cannot clear the
                        // remembered explicit model during recovery.
                        filter.setText(priorNif);
                        nifOnly.setChecked(true);
                        selectedNifPath=priorNif;selectedNifArchive=uri;
                        selectedLabel.setText("Автовосстановление NIF: "+priorNif);
                        inspectionOutput.setText("Архив и NIF восстановлены. "+
                            "Для DDS нажми «Найти DDS» — папка Data тоже запоминается.");
                    }else{
                        if(!priorNif.isEmpty())savedSession.clearModel();
                        filter.setText("");
                        selectedLabel.setText("NIF не выбрана. Нажми имя в каталоге.");
                        inspectionOutput.setText(restored?
                            "Архив восстановлен. Предыдущая NIF больше не найдена — выбери другую.":
                            "Архив открыт. Выбери нужную NIF нажатием на имя.");
                    }
                    renderResults();
                    // Reading only one selected NIF is bounded; DDS discovery is
                    // deliberately not started until the user requests it.
                    if(valid)inspectNifMaterials();
                });
            }catch(Exception ex){
                runOnUiThread(()->{
                    if(destroyed||request!=archiveLoadSequence)return;
                    if(restored)savedSession.clearArchive();
                    summary.setText("Ошибка чтения "+
                        (restored?"сохранённого ":"")+"BSA: "+ex.getMessage());
                    results.setText("Выбери архив BSA вручную. "+
                        "Файлы Skyrim не изменены.");
                    selectedLabel.setText("Автоматическое восстановление недоступно.");
                    inspectionOutput.setText("Проверь, не был ли архив перемещён "+
                        "и не отозван ли доступ Android к нему.");
                });
            }
        },"bsa-v29-restore-or-load").start();
    }

    /** Clear remembered paths; does not delete the BSA, NIF or DDS files. */
    private void forgetSavedPaths(){
        savedSession.forgetAll();
        ++archiveLoadSequence;
        currentUri=null;catalog=null;currentName="";
        selectedNifPath="";selectedNifArchive=null;
        clearMaterialSelection();
        filter.setText("");
        results.setText("");
        summary.setText("Сохранённые пути и разрешения очищены. Выбери BSA снова.");
        selectedLabel.setText("NIF не выбрана.");
        inspectionOutput.setText("Автовосстановление очищено. "+
            "Файлы Skyrim не изменены.");
    }

    private void pick(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_BSA);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==PICK_DATA){
            Uri tree=data.getData();
            if(savedSession.retainReadPermission(tree)){
                savedSession.saveDataTree(tree);
                if(materialBsaUri!=null&&!materialDdsPaths.isEmpty())
                    scanDdsInTree(tree);
                else inspectionOutput.setText("Папка Data запомнена. "+
                    "Теперь выбери NIF и прочитай пути DDS.");
            }else{
                savedSession.clearDataTree();
                if(materialBsaUri!=null&&!materialDdsPaths.isEmpty())
                    scanDdsInTree(tree);
                else inspectionOutput.setText("Папка Data доступна временно, "+
                    "но Android не дал разрешение сохранить доступ.");
            }
            return;
        }
        if(request!=PICK_BSA)return;
        final Uri uri=data.getData();
        boolean canKeep=savedSession.retainReadPermission(uri);
        if(!canKeep)savedSession.clearArchive();
        String name="Выбранный BSA";
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())name=c.getString(0);
        }catch(Exception ignored){}
        openCatalog(uri,name,false);
        if(!canKeep)selectedLabel.setText("Android разрешил BSA только на эту сессию; "+
            "для автозагрузки нужен постоянный доступ к документу.");
    }
    private void renderResults(){
        if(catalog==null||summary==null||results==null)return;
        BsaCatalogReader.Catalog c=catalog;
        String info="Архив: "+currentName+"\nBSA версия 105; папок: "+c.folderCount+"; файлов: "+c.fileCount+
            "; записей NIF: "+c.nifCount+"\nФлаги: 0x"+Long.toHexString(c.flags)+
            "\nИндекс поиска: "+c.indexedCount+" / "+c.fileCount+
            (c.partialIndex?" (ограничение объёма; поиск неполный)":" (полный)")+
            "\nЧтение только каталога; данные файлов не распакованы и не изменены.";
        if(currentUri!=null&&!savedSession.hasReadPermission(currentUri))
            info+="\nВнимание: Android предоставил только временный доступ к BSA. "+
                "При следующем открытии каталога его понадобится выбрать заново.";
        summary.setText(info);
        List<BsaCatalogReader.Entry> found=c.find(filter.getText().toString(),nifOnly.isChecked(),60);
        SpannableStringBuilder b=new SpannableStringBuilder(
            "Совпадения (до 60): "+found.size()+
            "\nНажми на название NIF, чтобы выбрать её.\n\n");
        for(BsaCatalogReader.Entry entry:found){
            b.append("• ");
            int first=b.length();
            b.append(entry.path);
            int last=b.length();
            if(entry.nif()){
                final boolean active=entry.path.equals(selectedNifPath);
                b.setSpan(new ClickableSpan(){
                    @Override public void onClick(View widget){chooseNif(entry);}
                    @Override public void updateDrawState(TextPaint paint){
                        paint.setColor(active?0xffb9f6ca:0xffd5bbff);
                        paint.setUnderlineText(!active);
                    }
                },first,last,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            b.append("\n  ").append(String.valueOf(entry.storedBytes))
             .append(" байт · 0x").append(Long.toHexString(entry.offset))
             .append(entry.compressed?" · сжат\n":" · без сжатия\n");
        }
        if(found.isEmpty())b.append("В индексированной части архива совпадений нет.");
        results.setText(b,TextView.BufferType.SPANNABLE);
    }
    private void inspectFirstNif(){
        if(catalog==null || currentUri==null){
            inspectionOutput.setText("Сначала выбери BSA и дождись загрузки каталога.");return;
        }
        List<BsaCatalogReader.Entry> found=selectedNifMatches();
        if(found.isEmpty()){
            inspectionOutput.setText("NIF по текущему фильтру не найден. Введи, например, lureargonian01.nif");return;
        }
        BsaCatalogReader.Entry selected=found.get(0);
        final Uri uri=currentUri;
        final long flags=catalog.flags;
        inspectionOutput.setText("Проверяю только одну запись:\n"+selected.path+"\nДанные не сохраняются…");
        new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                BsaEntryInspector.Result result=BsaEntryInspector.inspect(in,selected,flags);
                runOnUiThread(()->{if(!destroyed && uri.equals(currentUri))inspectionOutput.setText(result.report());});
            }catch(Exception e){
                runOnUiThread(()->{if(!destroyed && uri.equals(currentUri))
                    inspectionOutput.setText("Проверка NIF остановлена: "+e.getMessage()+"\nИсходный архив не изменён.");});
            }
        },"bsa-single-nif-reader").start();
    }
    /** v1.0 reads and validates the NIF block table from only one archive entry. */
    private void inspectNifBlocks(){
        if(catalog==null || currentUri==null){
            inspectionOutput.setText("Сначала выбери BSA и дождись загрузки каталога.");return;
        }
        List<BsaCatalogReader.Entry> found=selectedNifMatches();
        if(found.isEmpty()){
            inspectionOutput.setText("NIF не найден. Введи точное имя: lureargonian01.nif");return;
        }
        final BsaCatalogReader.Entry entry=found.get(0);
        final Uri uri=currentUri;
        final long flags=catalog.flags;
        inspectionOutput.setText("Проверяю типы и размеры блоков:\n"+entry.path+"\nВ файл ничего не записывается…");
        new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                String result=BsaEntryInspector.inspectBlocks(in,entry,flags);
                runOnUiThread(()->{if(!destroyed && uri.equals(currentUri))inspectionOutput.setText(result);});
            }catch(Exception e){
                runOnUiThread(()->{if(!destroyed && uri.equals(currentUri))
                    inspectionOutput.setText("Разбор блоков остановлен: "+e.getMessage()+"\nИсходный BSA не изменён.");});
            }
        },"nif-v10-block-table").start();
    }

    /** v1.1: bounded, read-only NIF extraction and static wireframe preview. */
    private void previewNifGeometry(){
        if(catalog==null||currentUri==null){
            inspectionOutput.setText("Сначала выбери архив BSA.");return;
        }
        List<BsaCatalogReader.Entry> found=selectedNifMatches();
        if(found.isEmpty()){
            inspectionOutput.setText("NIF не найден. Введи lureargonian01.nif");return;
        }
        final BsaCatalogReader.Entry entry=found.get(0);
        final Uri uri=currentUri;
        final long flags=catalog.flags;
        inspectionOutput.setText("Декодирую одну модель BSTriShape в памяти: "+entry.path);
        new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                NifGeometryDecoder.Mesh mesh=BsaEntryInspector.decodeNifGeometry(in,entry,flags);
                runOnUiThread(()->{
                    if(!isCurrentNif(uri,entry))return;
                    inspectionOutput.setText("Ресурс: "+entry.path+"\n"+mesh.report());
                    NifGeometryTransfer.offer(mesh.lines,mesh.surfaceTriangles,"NIF: "+entry.path.substring(entry.path.lastIndexOf('/')+1),mesh.compactInfo());
                    startActivity(new Intent(this,VulkanSceneActivity.class));
                });
            }catch(Exception e){
                runOnUiThread(()->{if(!destroyed&&uri.equals(currentUri))
                    inspectionOutput.setText("3D-предпросмотр NIF остановлен: "+e.getMessage()+
                        "\nИсходный BSA не изменён. Попробуй другую статическую модель BSTriShape.");});
            }
        },"nif-v13-geometry").start();
    }

    /** v2.11: choose among static BSTriShape blocks without assuming material links.
     * Reads one bounded NIF entry on a worker thread. Mesh bytes stay in memory.
     */
    private void selectNifPart(){
        if(catalog==null||currentUri==null){
            inspectionOutput.setText("Сначала выбери архив BSA.");return;
        }
        List<BsaCatalogReader.Entry> found=selectedNifMatches();
        if(found.isEmpty()){
            inspectionOutput.setText("NIF не найден. Введи имя модели в поиск.");return;
        }
        final BsaCatalogReader.Entry entry=found.get(0);
        final Uri uri=currentUri;
        final long flags=catalog.flags;
        inspectionOutput.setText("Проверяю отдельные части NIF (только чтение): "+entry.path);
        new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("Нет доступа к архиву BSA");
                NifGeometryDecoder.Parts parts=BsaEntryInspector.inspectNifParts(in,entry,flags);
                runOnUiThread(()->{
                    if(!isCurrentNif(uri,entry))return;
                    inspectionOutput.setText("Ресурс: "+entry.path+"\n"+parts.report());
                    String[] options=new String[parts.items.size()];
                    for(int i=0;i<options.length;i++)options[i]=parts.items.get(i).label();
                    new AlertDialog.Builder(this)
                        .setTitle("Части NIF · "+parts.available+" доступны")
                        .setItems(options,(dialog,index)->{
                            if(!isCurrentNif(uri,entry))return;
                            NifGeometryDecoder.Part part=parts.items.get(index);
                            if(part.mesh==null){
                                inspectionOutput.setText("BSTriShape #"+part.blockIndex+
                                    " не открывается: "+part.problem+
                                    "\nДругие части NIF не затронуты.");
                                return;
                            }
                            NifGeometryDecoder.Mesh mesh=part.mesh;
                            inspectionOutput.setText("Ресурс: "+entry.path+
                                "\nВыбрана часть #"+part.blockIndex+"\n"+mesh.report());
                            NifGeometryTransfer.offer(mesh.lines,mesh.surfaceTriangles,
                                "NIF: "+entry.path.substring(entry.path.lastIndexOf('/')+1)+
                                    " · часть #"+part.blockIndex,
                                mesh.compactInfo()+" · часть #"+part.blockIndex+
                                    " (без DDS)");
                            startActivity(new Intent(this,VulkanSceneActivity.class));
                        })
                        .setNegativeButton("Закрыть",null).show();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    if(!destroyed&&uri.equals(currentUri))
                        inspectionOutput.setText("Список частей NIF недоступен: "+e.getMessage()+
                            "\nИсходный BSA не изменён.");
                });
            }
        },"nif-v26-part-selector").start();
    }

    /** v2.11: read a single BSA NIF once and preview all VALID static BSTriShape
     * blocks together with a common normalization. Deliberately no DDS.
     */
    private void previewCombinedNif(){
        if(catalog==null||currentUri==null){
            inspectionOutput.setText("Сначала выбери BSA.");return;
        }
        List<BsaCatalogReader.Entry> candidates=selectedNifMatches();
        if(candidates.isEmpty()){
            inspectionOutput.setText("NIF не найден. Введи имя файла в поиск.");return;
        }
        final BsaCatalogReader.Entry entry=candidates.get(0);
        final Uri uri=currentUri;
        final long flags=catalog.flags;
        inspectionOutput.setText("Собираю поддерживаемые части NIF в памяти: "+
            entry.path+"\nОригинал не меняется.");
        new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("Архив BSA недоступен для чтения");
                NifGeometryDecoder.Parts parts=BsaEntryInspector.inspectNifParts(
                    in,entry,flags);
                NifSceneComposer.Combined scene=NifSceneComposer.combine(parts);
                runOnUiThread(()->{
                    if(!isCurrentNif(uri,entry))return;
                    inspectionOutput.setText("Ресурс: "+entry.path+"\n"+scene.info);
                    NifGeometryTransfer.offer(scene.lines,scene.triangles,
                        "NIF: "+entry.path.substring(entry.path.lastIndexOf('/')+1)+
                            " · "+scene.included+" части",
                        "Составная NIF · "+scene.included+" части · "+
                            scene.triangleCount+" треугольников · без DDS");
                    startActivity(new Intent(this,VulkanSceneActivity.class));
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    if(!destroyed&&uri.equals(currentUri))
                        inspectionOutput.setText("Составная NIF не открыта: "+
                            e.getMessage()+"\nBSA не изменён. "+
                            "Отдельные части можно проверить кнопкой «Части NIF».");
                });
            }
        },"nif-v27-composite").start();
    }


    /**
     * v2.12: textured preview of a chosen part from a MULTI-shape NIF.
     * The shader and DDS are selected by the shape's actual NIF block index.
     * Composite geometry and the proven single-shape preview are unchanged.
     */
    private void previewTexturedPart(){
        final Uri uri=currentUri;
        if(uri==null||catalog==null||materialBsaUri==null||
           !uri.equals(materialBsaUri)||foundDds.isEmpty()){
            inspectionOutput.setText("Сначала выбери NIF и нажми «Найти DDS каждой части NIF».");
            return;
        }
        List<BsaCatalogReader.Entry> entries=selectedNifMatches();
        if(entries.isEmpty()||!entries.get(0).path.equals(materialNifPath)){
            inspectionOutput.setText("NIF изменился. Повтори поиск DDS каждой части.");
            return;
        }
        final BsaCatalogReader.Entry entry=entries.get(0);
        final long flags=catalog.flags;
        final List<LocatedDds> locations=new ArrayList<>(foundDds);
        inspectionOutput.setText("Проверяю геометрию и точные связи каждой части NIF…");
        new Thread(()->{
            try{
                NifGeometryDecoder.Parts parts;
                NifMaterialLinkInspector.Result links;
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    if(in==null)throw new IOException("Нет доступа к NIF BSA");
                    parts=BsaEntryInspector.inspectNifParts(in,entry,flags);
                }
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    if(in==null)throw new IOException("Нет доступа к NIF BSA");
                    links=BsaEntryInspector.readPartMaterialLinks(in,entry,flags);
                }
                String[] choices=new String[parts.items.size()];
                for(int i=0;i<choices.length;i++){
                    NifGeometryDecoder.Part part=parts.items.get(i);
                    String state;
                    try{
                        if(part.mesh==null)throw new IOException(part.problem);
                        if(part.mesh.surfaceUVs.length!=part.mesh.triangles*6)
                            throw new IOException("Нет корректных UV");
                        NifMaterialLinkInspector.Link link=
                            NifPartTextureSelection.require(links,part.blockIndex);
                        if(findLocatedDds(locations,link.diffuse)==null)
                            throw new IOException("Диффузная DDS не найдена");
                        state="DDS: "+link.diffuse;
                    }catch(Exception error){state="Недоступно: "+error.getMessage();}
                    choices[i]="#"+part.blockIndex+" · "+state;
                }
                runOnUiThread(()->{
                    if(!isCurrentNif(uri,entry)||!entry.path.equals(materialNifPath))return;
                    new AlertDialog.Builder(this)
                        .setTitle("Vulkan · DDS отдельной части NIF")
                        .setItems(choices,(dialog,index)->{
                            if(!isCurrentNif(uri,entry)||!entry.path.equals(materialNifPath))return;
                            NifGeometryDecoder.Part selected=parts.items.get(index);
                            if(selected.mesh==null){
                                inspectionOutput.setText("Часть #"+selected.blockIndex+
                                    " не поддерживается: "+selected.problem);
                                return;
                            }
                            try{
                                NifMaterialLinkInspector.Link link=
                                    NifPartTextureSelection.require(links,selected.blockIndex);
                                if(selected.mesh.surfaceUVs.length!=selected.mesh.triangles*6)
                                    throw new IOException("У части #"+selected.blockIndex+" отсутствуют UV");
                                previewOnePartDds(uri,entry,selected.mesh,link,locations);
                            }catch(IOException invalid){
                                inspectionOutput.setText("Материал части #"+selected.blockIndex+
                                    ": "+invalid.getMessage());
                            }
                        })
                        .setNegativeButton("Закрыть",null).show();
                });
            }catch(Exception error){
                runOnUiThread(()->{
                    if(isCurrentNif(uri,entry))
                        inspectionOutput.setText("Невозможно выбрать часть NIF: "+
                            error.getMessage()+"\nИсходные BSA не изменены.");
                });
            }
        },"nif-v212-part-chooser").start();
    }

    /** Resolve a matched BSA entry by canonical DDS path. No guessing by filename. */
    private static LocatedDds findLocatedDds(List<LocatedDds> matches,String path)
            throws IOException{
        String key=DdsArchiveFinder.normalizeDds(path);
        for(LocatedDds item:matches){
            try{
                if(key.equals(DdsArchiveFinder.normalizeDds(item.hit.declaredPath)))
                    return item;
            }catch(IOException ignored){}
        }
        return null;
    }

    private DdsBcDecoder.Image readLocatedDds(LocatedDds hit)throws IOException{
        try(InputStream in=getContentResolver().openInputStream(hit.uri)){
            if(in==null)throw new IOException("Нет доступа к DDS BSA: "+hit.archive);
            BsaCatalogReader.Entry entry=new BsaCatalogReader.Entry(
                hit.hit.entryPath,hit.hit.storedBytes,hit.hit.offset,hit.hit.compressed);
            return BsaDdsPixelReader.preview(in,entry,hit.flags);
        }
    }

    /** Decode only the selected diffuse and its optional normal, in a worker. */
    private void previewOnePartDds(Uri uri,BsaCatalogReader.Entry entry,
            NifGeometryDecoder.Mesh mesh,NifMaterialLinkInspector.Link link,
            List<LocatedDds> matches){
        inspectionOutput.setText("Декодирую DDS для BSTriShape #"+link.shape+"…");
        new Thread(()->{
            try{
                LocatedDds diffuse=findLocatedDds(matches,link.diffuse);
                if(diffuse==null)throw new IOException("DDS [0] не найдена: "+link.diffuse);
                DdsBcDecoder.Image image=readLocatedDds(diffuse);
                DdsBcDecoder.Image normal=null;
                String normalInfo="Без карты нормалей";
                if(link.normal!=null&&!link.normal.isEmpty()){
                    try{
                        LocatedDds normalHit=findLocatedDds(matches,link.normal);
                        if(normalHit==null)throw new IOException("не найдена в BSA");
                        DdsBcDecoder.Image candidate=readLocatedDds(normalHit);
                        if(!candidate.format.startsWith("BC3")&&
                           !candidate.format.startsWith("BC5")&&
                           !"RGBA8".equals(candidate.format)&&
                           !"BGRA8".equals(candidate.format))
                            throw new IOException("неподдерживаемый формат "+candidate.format);
                        boolean resized=candidate.width!=image.width||candidate.height!=image.height;
                        normal=DdsNormalResampler.match(candidate,image.width,image.height);
                        normalInfo="Нормали: "+normal.width+"×"+normal.height+
                            " · "+normal.format+(resized?" · масштабирована для просмотра":"");
                    }catch(Exception optional){
                        normalInfo="Карта нормалей пропущена: "+optional.getMessage();
                    }
                }
                final DdsBcDecoder.Image normalImage=normal;
                final String detail=normalInfo;
                runOnUiThread(()->{
                    if(!isCurrentNif(uri,entry)||!entry.path.equals(materialNifPath))return;
                    NifGeometryTransfer.offerTextured(mesh.lines,mesh.surfaceTriangles,
                        mesh.surfaceUVs,image,normalImage,
                        "NIF: "+entry.path.substring(entry.path.lastIndexOf('/')+1)+
                            " · #"+link.shape,
                        mesh.compactInfo()+"\nBSTriShape #"+link.shape+
                            " → shader #"+link.shader+" → texture set #"+link.textureSet+
                            "\nDDS: "+link.diffuse+" · "+image.width+"×"+image.height+
                            "\n"+detail+"\nДругие части этой NIF не отображаются.");
                    inspectionOutput.setText("Текстура BSTriShape #"+link.shape+
                        " готова для Vulkan.\n"+detail+
                        "\nПолная составная модель пока открывается отдельно без DDS.");
                    startActivity(new Intent(this,VulkanSceneActivity.class));
                });
            }catch(Exception error){
                runOnUiThread(()->{
                    if(isCurrentNif(uri,entry))
                        inspectionOutput.setText("DDS части #"+link.shape+" не открыта: "+
                            error.getMessage()+"\nИгровые файлы не изменены.");
                });
            }
        },"nif-v212-part-dds").start();
    }


    /**
     * v2.13: true textured COMPOSITE scene, all decodable BSTriShape in one
     * global coordinate system, with individual diffuse DDS per NIF block.
     * Missing DDS uses neutral grey but does not remove that part's geometry.
     * All reads are via the user's read-only SAF grants, no game file writes.
     */
    private void previewCompositeDds(){
        final Uri uri=currentUri;
        if(uri==null||catalog==null||materialBsaUri==null||
           !uri.equals(materialBsaUri)||foundDds.isEmpty()){
            inspectionOutput.setText("Сначала выбери NIF и нажми «Найти DDS каждой части NIF».");
            return;
        }
        List<BsaCatalogReader.Entry> candidates=selectedNifMatches();
        if(candidates.isEmpty()||!candidates.get(0).path.equals(materialNifPath)){
            inspectionOutput.setText("Выбрана другая NIF. Повтори «Найти DDS каждой части NIF».");
            return;
        }
        final BsaCatalogReader.Entry entry=candidates.get(0);
        final long flags=catalog.flags;
        final List<LocatedDds> matches=new ArrayList<>(foundDds);
        inspectionOutput.setText("Собираю части NIF с diffuse + normal DDS в общий Vulkan-атлас v2.18…");
        new Thread(()->{
            try{
                NifGeometryDecoder.Parts parts;
                NifMaterialLinkInspector.Result links;
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    if(in==null)throw new IOException("NIF BSA недоступен");
                    parts=BsaEntryInspector.inspectNifParts(in,entry,flags);
                }
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    if(in==null)throw new IOException("NIF BSA недоступен");
                    links=BsaEntryInspector.readPartMaterialLinks(in,entry,flags);
                }
                NifSceneComposer.Combined scene=NifSceneComposer.combine(parts);
                Map<String,DdsBcDecoder.Image> cache=new LinkedHashMap<>();
                Map<Integer,DdsBcDecoder.Image> perShape=new LinkedHashMap<>();
                Map<Integer,DdsBcDecoder.Image> perNormal=new LinkedHashMap<>();
                StringBuilder errors=new StringBuilder();
                long sourcePixels=0;
                for(NifGeometryDecoder.Part part:parts.items){
                    if(destroyed||!uri.equals(currentUri))return;
                    if(part.mesh==null)continue;
                    try{
                        if(part.mesh.surfaceUVs.length!=part.mesh.triangles*6)
                            throw new IOException("UV отсутствуют");
                        NifMaterialLinkInspector.Link link=
                            NifPartTextureSelection.require(links,part.blockIndex);
                        LocatedDds hit=findLocatedDds(matches,link.diffuse);
                        if(hit==null)throw new IOException("DDS не найдена: "+link.diffuse);
                        String key=DdsArchiveFinder.normalizeDds(link.diffuse);
                        DdsBcDecoder.Image image=cache.get(key);
                        if(image==null){
                            if(cache.size()>=NifCompositeDdsAtlas.MAX_TEXTURES)
                                throw new IOException("Лимит уникальных DDS: "+
                                    NifCompositeDdsAtlas.MAX_TEXTURES);
                            image=readLocatedDds(hit);
                            long pixels=(long)image.width*image.height;
                            if(sourcePixels+pixels>8L*1024*1024)
                                throw new IOException("Лимит декодированных DDS: 8 млн пикселей");
                            sourcePixels+=pixels;
                            cache.put(key,image);
                        }
                        perShape.put(part.blockIndex,image);
                        if(link.normal!=null&&!link.normal.isEmpty()){
                            try{
                                LocatedDds normalHit=findLocatedDds(matches,link.normal);
                                if(normalHit!=null){
                                    String nk=DdsArchiveFinder.normalizeDds(link.normal);
                                    DdsBcDecoder.Image normal=cache.get(nk);
                                    if(normal==null){
                                        if(cache.size()>=NifCompositeDdsAtlas.MAX_TEXTURES)
                                            throw new IOException("Лимит DDS cache");
                                        normal=readLocatedDds(normalHit);
                                        if(!normal.format.startsWith("BC3")&&
                                           !normal.format.startsWith("BC5")&&
                                           !"RGBA8".equals(normal.format)&&
                                           !"BGRA8".equals(normal.format))
                                            throw new IOException("normal DDS format "+normal.format);
                                        long nPixels=(long)normal.width*normal.height;
                                        if(sourcePixels+nPixels>8L*1024*1024)
                                            throw new IOException("Лимит декодированных DDS");
                                        sourcePixels+=nPixels;
                                        cache.put(nk,normal);
                                    }
                                    perNormal.put(part.blockIndex,normal);
                                }
                            }catch(Exception optionalNormal){
                                errors.append("\\n• normal #").append(part.blockIndex)
                                    .append(": ").append(optionalNormal.getMessage())
                                    .append(" → плоская normal map");
                            }
                        }
                    }catch(Exception partError){
                        errors.append("\n• #").append(part.blockIndex).append(": ")
                            .append(partError.getMessage()).append(" → нейтральный материал");
                    }
                }
                NifCompositeDdsAtlas.Result atlas=
                    NifCompositeDdsAtlas.build(parts,scene,perShape);
                NifCompositeNormalAtlas.Result stacked=null;
                String normalNote="Normal DDS: недоступны; сохранён diffuse atlas";
                if(!perNormal.isEmpty()){
                    try{
                        stacked=NifCompositeNormalAtlas.build(parts,atlas,perNormal);
                        normalNote="Normal DDS: "+stacked.normalParts+
                            " частей, уникальных карт "+stacked.uniqueNormals+
                            " · нейтральных "+stacked.neutralNormalParts;
                    }catch(Exception unsupported){
                        normalNote="Normal DDS fallback: "+unsupported.getMessage()+
                            " · сохранён diffuse atlas";
                    }
                }
                final NifCompositeNormalAtlas.Result combinedNormal=stacked;
                final String details="Составная NIF · "+scene.included+" частей · "+
                    scene.triangleCount+" треугольников\\n"+
                    "Diffuse DDS: "+atlas.textureCount+" · без DDS: "+
                    atlas.neutralParts+" частей\\n"+
                    "DDS-атлас: "+atlas.width+"×"+atlas.height+" · сетка "+
                    atlas.grid+"×"+atlas.grid+"\\n"+
                    normalNote+"\\n"+
                    parts.hierarchyNote+
                    errors;
                runOnUiThread(()->{
                    if(!isCurrentNif(uri,entry)||!entry.path.equals(materialNifPath))return;
                    String title="NIF: "+entry.path.substring(entry.path.lastIndexOf('/')+1)+
                        " · все "+scene.included+" части";
                    if(combinedNormal!=null)
                        NifGeometryTransfer.offerAtlasNormal(scene.lines,scene.triangles,
                            atlas,combinedNormal,title,details);
                    else
                        NifGeometryTransfer.offerAtlas(scene.lines,scene.triangles,
                            atlas,title,details);
                    inspectionOutput.setText("Составная Vulkan DDS v2.18 подготовлена.\\n"+
                        details+"\\nИгровые BSA не изменены.");
                    startActivity(new Intent(this,VulkanSceneActivity.class));
                });
            }catch(Exception error){
                runOnUiThread(()->{
                    if(isCurrentNif(uri,entry))
                        inspectionOutput.setText("Составная модель с DDS не открыта: "+
                            error.getMessage()+"\nФайлы Skyrim не изменены. "+
                            "Можно открыть части отдельно.");
                });
            }
        },"nif-v213-composite-dds").start();
    }

    /** Diagnostic: resolve exact BSTriShape -> shader -> texture-set links.
     * Never assume that the first shader belongs to the first geometry block.
     */
    private void inspectPartMaterialLinks(){
        if(catalog==null||currentUri==null){
            inspectionOutput.setText("Сначала выбери архив BSA.");return;
        }
        List<BsaCatalogReader.Entry> entries=selectedNifMatches();
        if(entries.isEmpty()){
            inspectionOutput.setText("Сначала выбери модель NIF.");return;
        }
        final BsaCatalogReader.Entry entry=entries.get(0);
        final Uri uri=currentUri;
        final long flags=catalog.flags;
        inspectionOutput.setText("Сверяю связи частей NIF и шейдеров: "+entry.path+
            "\nЧтение только одной записи BSA.");
        new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("Нет доступа к архиву BSA");
                NifMaterialLinkInspector.Result info=
                    BsaEntryInspector.readPartMaterialLinks(in,entry,flags);
                runOnUiThread(()->{
                    if(!isCurrentNif(uri,entry))return;
                    partMaterialLinks=info;partMaterialNifPath=entry.path;
                    inspectionOutput.setText("Ресурс: "+entry.path+"\n"+info.report()+
                        "\n\nНажми «Найти DDS каждой части NIF» для проверки в Data.");
                });
            }catch(Exception error){
                runOnUiThread(()->{
                    if(isCurrentNif(uri,entry))
                        inspectionOutput.setText("Разбор связей материалов остановлен: "+
                            error.getMessage()+"\nNIF и BSA не изменены.");
                });
            }
        },"nif-v210-material-links").start();
    }

    /** One-tap per-part material lookup: validate references, then scan the
     * previously approved Data directory using the existing read-only BSA index.
     * Distinct shapes can share DDS paths, but are reported separately.
     */
    private void findPartDds(){
        if(catalog==null||currentUri==null){
            inspectionOutput.setText("Сначала выбери архив BSA.");return;
        }
        List<BsaCatalogReader.Entry> candidates=selectedNifMatches();
        if(candidates.isEmpty()){
            inspectionOutput.setText("Сначала выбери модель NIF.");return;
        }
        final BsaCatalogReader.Entry entry=candidates.get(0);
        final Uri uri=currentUri;
        final long flags=catalog.flags;
        clearMaterialSelection();
        inspectionOutput.setText("Читаю точные ссылки DDS каждой части: "+entry.path+
            "\nЗатем проверю имена в индексах BSA каталога Data.");
        new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("Нет доступа к архиву BSA");
                NifMaterialLinkInspector.Result links=
                    BsaEntryInspector.readPartMaterialLinks(in,entry,flags);
                List<String> requested=NifPartDdsAvailability.targets(links);
                runOnUiThread(()->{
                    if(!isCurrentNif(uri,entry))return;
                    partMaterialLinks=links;partMaterialNifPath=entry.path;
                    materialDdsPaths=requested;materialNifPath=entry.path;
                    materialBsaUri=uri;
                    foundDds=Collections.emptyList();lastDdsSearchReport="";
                    Uri tree=savedSession.lastDataTree();
                    if(tree!=null)scanDdsInTree(tree);
                    else{
                        inspectionOutput.setText("Пути DDS по частям определены: "+
                            requested.size()+". Выбери папку Data один раз.");
                        chooseDataFolder();
                    }
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    if(isCurrentNif(uri,entry))
                        inspectionOutput.setText("Поиск DDS по частям недоступен: "+
                            e.getMessage()+"\nИгровые файлы не изменены.");
                });
            }
        },"nif-v211-part-dds-lookup").start();
    }

    /** Read only one NIF and remember its declared DDS paths for a separate Data scan. */
    private void inspectNifMaterials(){
        if(catalog==null||currentUri==null){inspectionOutput.setText("Сначала выбери архив BSA.");return;}
        List<BsaCatalogReader.Entry> found=selectedNifMatches();
        if(found.isEmpty()){inspectionOutput.setText("NIF не найден. Введи lureargonian01.nif");return;}
        final BsaCatalogReader.Entry entry=found.get(0);
        final Uri uri=currentUri;
        final long flags=catalog.flags;
        clearMaterialSelection();
        inspectionOutput.setText("Читаю ссылки на текстуры из одной NIF-записи:\n"+entry.path);
        new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                NifMaterialInspector.Result m=BsaEntryInspector.readMaterialData(in,entry,flags);
                ArrayList<String> declared=new ArrayList<>();
                for(NifMaterialInspector.TextureSet set:m.textureSets)
                    for(NifMaterialInspector.Slot slot:set.slots)
                        if(!slot.path.isEmpty())declared.add(slot.path);
                ArrayList<String> candidates=new ArrayList<>();
                int unsupported=0;
                for(String path:declared){
                    try {
                        DdsArchiveFinder.normalizeDds(path);
                        if(candidates.size()<DdsArchiveFinder.MAX_TARGETS)candidates.add(path);
                        else unsupported++;
                    }catch(IOException ignored){unsupported++;}
                }
                final List<String> safeTargets=candidates.isEmpty()?Collections.emptyList():
                    new ArrayList<>(DdsArchiveFinder.targets(candidates).values());
                final String report="Ресурс: "+entry.path+"\n"+m.report()+
                    "\n\nПоиск v2.11: "+safeTargets.size()+" подходящих путей DDS"+
                    (unsupported>0?" · неподдерживаемых или сверх лимита: "+unsupported:"")+". "+
                    "Нажми «Найти DDS в папке Data» и выбери каталог игры Data.";
                runOnUiThread(()->{
                    if(!isCurrentNif(uri,entry))return;
                    materialDdsPaths=safeTargets;materialNifPath=entry.path;materialBsaUri=uri;
                    inspectionOutput.setText(report);
                });
            }catch(Exception e){
                runOnUiThread(()->{if(!destroyed&&uri.equals(currentUri))
                    inspectionOutput.setText("Пути текстур не прочитаны: "+e.getMessage()+
                        "\nИсходный BSA не изменён.");});
            }
        },"nif-v16-texture-paths").start();
    }

    /** User grants read-only access to the containing Data directory via SAF. */
    private void pickDataForDds(){
        if(currentUri==null||materialBsaUri==null||!currentUri.equals(materialBsaUri)||materialDdsPaths.isEmpty()){
            inspectionOutput.setText("Сначала выбери NIF и нажми «Пути текстур NIF · v2.11». "+
                "После получения путей DDS можно искать их в папке Data.");return;
        }
        List<BsaCatalogReader.Entry> found=catalog==null?Collections.emptyList():
            selectedNifMatches();
        if(found.isEmpty()||!found.get(0).path.equals(materialNifPath)){
            inspectionOutput.setText("Выбран другой NIF. Повтори «Пути текстур NIF», затем поиск DDS.");return;
        }
        Uri remembered=savedSession.lastDataTree();
        if(remembered!=null){
            scanDdsInTree(remembered);
            return;
        }
        chooseDataFolder();
    }

    /** Explicit replacement is always available, even with a saved Data URI. */
    private void chooseDataFolder(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,PICK_DATA);
    }

    private static final class LocatedDds {
        final Uri uri;
        final String archive;
        final DdsArchiveFinder.Hit hit;
        final long flags;
        LocatedDds(Uri uri,String archive,DdsArchiveFinder.Hit hit,long flags) {
            this.uri=uri;this.archive=archive;this.hit=hit;this.flags=flags;
        }
    }

    private static final class ArchiveDoc {
        final Uri uri;final String name;
        ArchiveDoc(Uri uri,String name){this.uri=uri;this.name=name;}
    }

    /** Only direct BSA files within selected Data tree; no recursive traversal. */
    private List<ArchiveDoc> listBsaInData(Uri tree) throws IOException {
        String treeId=DocumentsContract.getTreeDocumentId(tree);
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,treeId);
        String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE};
        List<ArchiveDoc> out=new ArrayList<>();
        int count=0;
        try(Cursor c=getContentResolver().query(children,columns,null,null,null)){
            if(c==null)throw new IOException("Android не предоставил список файлов Data");
            final int idCol=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            final int nameCol=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            final int mimeCol=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while(c.moveToNext()){
                if(++count>MAX_DATA_CHILDREN)throw new IOException("В папке более 4096 элементов; поиск остановлен");
                String filename=c.getString(nameCol);
                String mime=c.getString(mimeCol);
                String id=c.getString(idCol);
                if(filename==null||id==null||DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)||
                    !filename.toLowerCase(Locale.ROOT).endsWith(".bsa"))continue;
                out.add(new ArchiveDoc(DocumentsContract.buildDocumentUriUsingTree(tree,id),filename));
                if(out.size()>MAX_BSA_ARCHIVES)throw new IOException("Более 48 BSA в Data; поиск остановлен");
            }
        }catch(SecurityException e){throw new IOException("Нет разрешения на чтение Data",e);}
        out.sort((a,b)->a.name.compareToIgnoreCase(b.name));
        return out;
    }

    private void scanDdsInTree(Uri tree){
        if(materialBsaUri==null||materialDdsPaths.isEmpty()){
            inspectionOutput.setText("Пути DDS больше недоступны. Сначала повтори анализ NIF.");return;
        }
        final Uri nifUri=materialBsaUri;
        final String nifName=materialNifPath;
        final List<String> requested=new ArrayList<>(materialDdsPaths);
        final NifMaterialLinkInspector.Result activePartLinks=
            nifName.equals(partMaterialNifPath)?partMaterialLinks:null;
        inspectionOutput.setText("Ищу "+requested.size()+" DDS по именам в BSA каталога Data…\n"+
            "Содержимое DDS не читается, архивы не изменяются.");
        new Thread(()->{
            try{
                final Map<String,String> targets=DdsArchiveFinder.targets(requested);
                final List<ArchiveDoc> docs=listBsaInData(tree);
                if(docs.isEmpty())throw new IOException("В выбранной папке нет архивов BSA. Выбери именно Data.");
                List<DdsArchiveFinder.Hit> matches=new ArrayList<>();
                List<LocatedDds> located=new ArrayList<>();
                List<String> failures=new ArrayList<>();
                int read=0,partial=0;
                for(ArchiveDoc doc:docs){
                    if(destroyed||!nifUri.equals(currentUri))return;
                    final int checked=read;
                    runOnUiThread(()->{if(!destroyed&&nifUri.equals(currentUri))
                        inspectionOutput.setText("Поиск DDS v2.11: проверено "+checked+" из "+docs.size()+
                            " BSA…\nТолько индексы имён, без распаковки.");});
                    try(InputStream in=getContentResolver().openInputStream(doc.uri)){
                        if(in==null)throw new IOException("Невозможно открыть архив");
                        BsaCatalogReader.Catalog c=BsaCatalogReader.read(in);
                        if(c.partialIndex)partial++;
                        for(DdsArchiveFinder.Hit hit:DdsArchiveFinder.find(c,targets,doc.name)){
                            matches.add(hit);
                            if(located.size()<64)located.add(new LocatedDds(doc.uri,doc.name,hit,c.flags));
                        }
                        read++;
                    }catch(Exception ex){
                        if(failures.size()<10)failures.add(doc.name+": "+ex.getMessage());
                    }
                }
                StringBuilder out=new StringBuilder("Поиск DDS v2.11 · только каталоги BSA\n");
                out.append("Исходный NIF: ").append(nifName).append('\n');
                out.append("BSA в Data: ").append(docs.size()).append(" · прочитано: ").append(read)
                   .append(" · ошибок: ").append(docs.size()-read).append(" · частичных индексов: ")
                   .append(partial).append('\n');
                out.append("\nИскомые DDS: ").append(targets.size()).append('\n');
                for(String path:targets.values()){
                    out.append("• ").append(path).append('\n');
                    int found=0;
                    for(DdsArchiveFinder.Hit h:matches)if(path.equals(h.declaredPath)){
                        found++;
                        out.append("  НАЙДЕНО: ").append(h.archive).append(" → ").append(h.entryPath)
                           .append("\n  ").append(h.storedBytes).append(" байт · 0x")
                           .append(Long.toHexString(h.offset)).append(h.compressed?" · сжат":" · без сжатия")
                           .append('\n');
                    }
                    if(found==0)out.append("  Не найдено в проверенных индексах; это не подтверждает отсутствие файла.\n");
                }
                if(!failures.isEmpty()){
                    out.append("\nОшибки чтения (до 10):\n");
                    for(String err:failures)out.append("• ").append(err).append('\n');
                }
                if(partial>0||read<docs.size())out.append("\nВНИМАНИЕ: поиск неполный из-за ограничений индекса или ошибок доступа.\n");
                if(activePartLinks!=null)
                    out.append("\n\n").append(NifPartDdsAvailability.report(
                        activePartLinks,matches,partial==0&&read==docs.size()));
                out.append("\nDDS не декодированы и не распакованы. Файлы игры не изменены.");
                final String report=out.toString();
                runOnUiThread(()->{
                    if(!destroyed&&nifUri.equals(currentUri)&&nifName.equals(materialNifPath)){
                        foundDds=Collections.unmodifiableList(new ArrayList<>(located));
                        lastDdsSearchReport=report;
                        inspectionOutput.setText(report+"\n\nНажми «Заголовки найденных DDS · v2.11» для проверки формата изображений.");
                    }
                });
            }catch(Exception e){
                runOnUiThread(()->{if(!destroyed&&nifUri.equals(currentUri))
                    inspectionOutput.setText("Поиск DDS остановлен: "+e.getMessage()+
                        "\nНичего не изменено в каталоге игры.");});
            }
        },"nif-v16-dds-index-search").start();
    }

    /** Read the two requested DDS headers from their BSA entries, never writing assets. */
    private void inspectFoundDdsHeaders(){
        final Uri nifUri=currentUri;
        if(nifUri==null||materialBsaUri==null||!nifUri.equals(materialBsaUri)||foundDds.isEmpty()){
            inspectionOutput.setText("Сначала выбери NIF, найди его DDS в папке Data и дождись отчёта.");
            return;
        }
        final List<LocatedDds> entries=new ArrayList<>(foundDds);
        final String searchReport=lastDdsSearchReport;
        inspectionOutput.setText("Проверяю заголовки "+entries.size()+" найденных DDS…\nТекстуры не декодируются и не записываются.");
        new Thread(()->{
            StringBuilder report=new StringBuilder(searchReport);
            report.append("\n\nЗаголовки DDS v2.11 · только чтение выбранных записей BSA\n");
            int checked=0,failed=0;
            // Prefer the first matching BSA entry for each canonical texture path.
            java.util.HashSet<String> seen=new java.util.HashSet<>();
            for(LocatedDds item:entries){
                if(destroyed||!nifUri.equals(currentUri))return;
                String canonical;
                try{canonical=DdsArchiveFinder.normalizeDds(item.hit.declaredPath);}
                catch(IOException ex){failed++;continue;}
                if(!seen.add(canonical))continue;
                try(InputStream in=getContentResolver().openInputStream(item.uri)){
                    if(in==null)throw new IOException("Файл BSA недоступен");
                    BsaCatalogReader.Entry entry=new BsaCatalogReader.Entry(item.hit.entryPath,
                        item.hit.storedBytes,item.hit.offset,item.hit.compressed);
                    BsaDdsInspector.Result info=BsaDdsInspector.inspect(in,entry,item.flags);
                    report.append("\n• Архив: ").append(item.archive).append('\n')
                        .append(info.report()).append('\n');
                    checked++;
                }catch(Exception ex){
                    failed++;
                    report.append("\n• ").append(item.archive).append(" → ")
                        .append(item.hit.entryPath).append("\n  Ошибка заголовка: ")
                        .append(ex.getMessage()).append('\n');
                }
            }
            report.append("\nПроверено DDS: ").append(checked).append(" · ошибок: ").append(failed)
                .append(". Файлы игры не изменены; изображения не наложены на модель.");
            final String result=report.toString();
            runOnUiThread(()->{if(!destroyed&&nifUri.equals(currentUri)&&
                materialBsaUri!=null&&materialBsaUri.equals(nifUri))inspectionOutput.setText(result);});
        },"dds-v17-single-header-reader").start();
    }

    /** v2.11: CPU decode only the first mip of up to four explicitly matched DDS records. */
    private void previewFoundDds(){
        final Uri nifUri=currentUri;
        if(nifUri==null||materialBsaUri==null||!nifUri.equals(materialBsaUri)||foundDds.isEmpty()){
            inspectionOutput.setText("Сначала открой NIF, найди DDS в папке Data и дождись результатов поиска.");
            return;
        }
        final List<LocatedDds> matches=new ArrayList<>(foundDds);
        inspectionOutput.setText("Декодирую первые mip-уровни найденных DDS в памяти…\n"+
            "BC1/BC2/BC3/BC4/BC5 и RGBA8/BGRA8, до 4 изображений. Файлы игры не изменяются.");
        new Thread(()->{
            ArrayList<DdsBcDecoder.Image> images=new ArrayList<>();
            ArrayList<String> names=new ArrayList<>();
            java.util.HashSet<String> used=new java.util.HashSet<>();
            StringBuilder report=new StringBuilder("Предпросмотр DDS v2.11 · BC1/2/3/4/5\n");
            for(LocatedDds item:matches){
                if(destroyed||!nifUri.equals(currentUri))return;
                String key;
                try{key=DdsArchiveFinder.normalizeDds(item.hit.declaredPath);}
                catch(IOException ex){continue;}
                if(!used.add(key))continue;
                if(images.size()>=4){report.append("Остальные DDS не декодируются (лимит 4).\n");break;}
                try(InputStream in=getContentResolver().openInputStream(item.uri)){
                    if(in==null)throw new IOException("Нет доступа к BSA");
                    BsaCatalogReader.Entry entry=new BsaCatalogReader.Entry(item.hit.entryPath,
                        item.hit.storedBytes,item.hit.offset,item.hit.compressed);
                    DdsBcDecoder.Image d=BsaDdsPixelReader.preview(in,entry,item.flags);
                    images.add(d);names.add(item.hit.entryPath);
                    report.append("• ").append(item.hit.entryPath).append("\n  ")
                        .append(d.width).append(" × ").append(d.height).append(" · ")
                        .append(d.format).append(" · готово\n");
                }catch(Exception ex){
                    report.append("• ").append(item.hit.entryPath).append("\n  Ошибка: ")
                        .append(ex.getMessage()).append("\n");
                }
            }
            report.append("\nДекодированы только изображения для просмотра, без применения к Vulkan. " +
                "Игра и файлы BSA не изменены.");
            final String text=report.toString();
            runOnUiThread(()->{
                if(destroyed||!nifUri.equals(currentUri))return;
                inspectionOutput.setText(text);
                if(images.isEmpty())return;
                ScrollView scroller=new ScrollView(this);
                LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
                panel.setPadding(dp(12),dp(8),dp(12),dp(12));
                panel.setBackgroundColor(0xff151923);scroller.addView(panel);
                for(int i=0;i<images.size();i++){
                    DdsBcDecoder.Image d=images.get(i);
                    panel.addView(text(names.get(i)+" · "+d.width+"×"+d.height+" · "+d.format,12,0xffeee8fa));
                    Bitmap bmp=Bitmap.createBitmap(d.argb,d.width,d.height,Bitmap.Config.ARGB_8888);
                    ImageView preview=new ImageView(this);
                    preview.setImageBitmap(bmp);preview.setAdjustViewBounds(true);
                    preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    preview.setBackgroundColor(0xff282e3b);
                    panel.addView(preview,new LinearLayout.LayoutParams(-1,dp(240)));
                }
                new AlertDialog.Builder(this).setTitle("Текстуры Skyrim SE · DDS")
                    .setView(scroller).setPositiveButton("Закрыть",(d,which)->{})
                    .show();
            });
        },"dds-v19-preview").start();
    }

    /**
     * v2.11 opt-in diffuse-only preview. Requires an explicitly selected NIF,
     * a single BSShaderTextureSet and a matched diffuse DDS in the granted Data tree.
     * All BSA reads are bounded and read-only; multi-material NIFs are refused.
     */
    private void previewTexturedNif(){
        final Uri selectedUri=currentUri;
        if(selectedUri==null||catalog==null||materialBsaUri==null||
           !selectedUri.equals(materialBsaUri)||foundDds.isEmpty()){
            inspectionOutput.setText("Сначала выбери NIF, прочитай пути текстур и найди DDS в Data.");
            return;
        }
        List<BsaCatalogReader.Entry> candidates=selectedNifMatches();
        if(candidates.isEmpty()||!candidates.get(0).path.equals(materialNifPath)){
            inspectionOutput.setText("NIF изменился. Повтори анализ путей текстур и поиск DDS.");
            return;
        }
        final BsaCatalogReader.Entry entry=candidates.get(0);
        final long flags=catalog.flags;
        final List<LocatedDds> matches=new ArrayList<>(foundDds);
        inspectionOutput.setText("Подготавливаю UV и первую диффузную DDS для Vulkan…");
        new Thread(()->{
            try {
                NifGeometryDecoder.Mesh mesh;
                NifMaterialInspector.Result materials;
                try(InputStream input=getContentResolver().openInputStream(selectedUri)){
                    if(input==null)throw new IOException("NIF BSA больше недоступен");
                    mesh=BsaEntryInspector.decodeNifGeometry(input,entry,flags);
                }
                if(mesh.surfaceUVs.length==0)
                    throw new IOException("У этой BSTriShape нет UV-координат");
                try(InputStream input=getContentResolver().openInputStream(selectedUri)){
                    if(input==null)throw new IOException("NIF BSA больше недоступен");
                    materials=BsaEntryInspector.readMaterialData(input,entry,flags);
                }
                if(materials.textureSets.size()!=1||materials.shapeCount!=1)
                    throw new IOException("Пока поддерживаются только NIF с одной BSTriShape и одним набором текстур");
                String diffuse=null,normalPath=null;
                for(NifMaterialInspector.Slot slot:materials.textureSets.get(0).slots){
                    if(slot.index==0)diffuse=slot.path;
                    if(slot.index==1)normalPath=slot.path;
                }
                if(diffuse==null||diffuse.isEmpty())
                    throw new IOException("В NIF не указан слот 0 (диффузная DDS)");
                String target=DdsArchiveFinder.normalizeDds(diffuse);
                LocatedDds chosen=null;
                for(LocatedDds item:matches){
                    try {
                        if(target.equals(DdsArchiveFinder.normalizeDds(item.hit.declaredPath))){
                            chosen=item;break;
                        }
                    }catch(IOException ignored){}
                }
                if(chosen==null)throw new IOException("Диффузная DDS не найдена среди проверенных архивов Data");
                DdsBcDecoder.Image pixels;
                try(InputStream input=getContentResolver().openInputStream(chosen.uri)){
                    if(input==null)throw new IOException("Архив DDS больше недоступен");
                    BsaCatalogReader.Entry asset=new BsaCatalogReader.Entry(chosen.hit.entryPath,
                            chosen.hit.storedBytes,chosen.hit.offset,chosen.hit.compressed);
                    pixels=BsaDdsPixelReader.preview(input,asset,chosen.flags);
                }
                // The second map is optional. Never fail the working diffuse renderer
                // merely because a normal DDS is absent, unsupported or malformed.
                DdsBcDecoder.Image normalImage=null;
                String normalStatus="без карты нормалей";
                if(normalPath!=null&&!normalPath.isEmpty()){
                    try {
                        String normalKey=DdsArchiveFinder.normalizeDds(normalPath);
                        LocatedDds normalHit=null;
                        for(LocatedDds item:matches){
                            if(normalKey.equals(DdsArchiveFinder.normalizeDds(item.hit.declaredPath))){
                                normalHit=item;break;
                            }
                        }
                        if(normalHit!=null){
                            try(InputStream normalInput=getContentResolver().openInputStream(normalHit.uri)){
                                if(normalInput==null)throw new IOException("Нет доступа к normal map BSA");
                                BsaCatalogReader.Entry normalEntry=new BsaCatalogReader.Entry(
                                    normalHit.hit.entryPath,normalHit.hit.storedBytes,
                                    normalHit.hit.offset,normalHit.hit.compressed);
                                DdsBcDecoder.Image candidate=BsaDdsPixelReader.preview(
                                    normalInput,normalEntry,normalHit.flags);
                                if(!candidate.format.startsWith("BC3")&&
                                   !candidate.format.startsWith("BC5")&&
                                   !"RGBA8".equals(candidate.format)&&
                                   !"BGRA8".equals(candidate.format))
                                    throw new IOException("Неподдерживаемый normal DDS: "+candidate.format);
                                boolean resized=candidate.width!=pixels.width||candidate.height!=pixels.height;
                                normalImage=DdsNormalResampler.match(candidate,pixels.width,pixels.height);
                                normalStatus="нормали: "+normalImage.width+"×"+normalImage.height+
                                    " · "+normalImage.format+(resized?" · масштабирована для просмотра":"");
                            }
                        }else normalStatus="normal DDS не найдена в Data";
                    }catch(Exception normalError){
                        normalStatus="normal DDS пропущена: "+normalError.getMessage();
                    }
                }
                final DdsBcDecoder.Image image=pixels;
                final DdsBcDecoder.Image selectedNormal=normalImage;
                final String normalInfo=normalStatus;
                final String name="NIF: "+entry.path.substring(entry.path.lastIndexOf('/')+1);
                runOnUiThread(()->{
                    if(destroyed||!selectedUri.equals(currentUri)||
                       !entry.path.equals(materialNifPath))return;
                    NifGeometryTransfer.offerTextured(mesh.lines,mesh.surfaceTriangles,
                          mesh.surfaceUVs,image,selectedNormal,name,
                          mesh.compactInfo()+" · DDS: "+image.width+"×"+image.height+" "+image.format+
                          "\n"+normalInfo);
                    inspectionOutput.setText("UV и DDS подготовлены. "+normalInfo+
                        ". Режимы поверхности, DDS и нормалей доступны в Vulkan.");
                    startActivity(new Intent(this,VulkanSceneActivity.class));
                });
            }catch(Exception e){
                runOnUiThread(()->{if(!destroyed&&selectedUri.equals(currentUri))
                    inspectionOutput.setText("Vulkan DDS-предпросмотр остановлен: "+e.getMessage()+
                        "\nАрхивы и игровые файлы не изменены.");});
            }
        },"nif-v21-vulkan-dds").start();
    }

    private void copyReport(){
        ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("Skyrim SE BSA v2.11",summary.getText()+"\n\n"+results.getText()+"\n\n"+inspectionOutput.getText()));
    }
    @Override protected void onDestroy(){destroyed=true;super.onDestroy();}
}
