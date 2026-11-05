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
    private BsaCatalogReader.Catalog catalog;
    private TextView summary,results,inspectionOutput;
    private Uri currentUri;
    private EditText filter;
    private CheckBox nifOnly;
    private String currentName="";
    private volatile boolean destroyed=false;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xff101115);
        getWindow().setNavigationBarColor(0xff101115);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff101116);scroll.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(14),dp(16),dp(18));scroll.addView(root);
        TextView title=text("Skyrim SE · NIF v2.4",22,0xffdbc5fb);title.setGravity(Gravity.CENTER);root.addView(title);
        root.addView(text("BSA 105: каталог, заголовок NIF и таблица блоков. Выбирай конкретный файл через строку поиска; читаться будет только первая найденная NIF-запись. Без сохранения на диск и изменения игры.",13,0xffd5d3dd));
        button(root,"Выбрать архив BSA",this::pick);
        filter=new EditText(this);filter.setSingleLine(true);filter.setHint("Поиск: meshes, .nif, fish...");
        filter.setTextColor(0xffeeeeee);filter.setHintTextColor(0xffaaa4b8);root.addView(filter);
        nifOnly=new CheckBox(this);nifOnly.setText("Только модели .nif");nifOnly.setTextColor(0xffeeeeee);
        root.addView(nifOnly);
        filter.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int cnt,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int count){renderResults();}
            public void afterTextChanged(Editable e){}
        });
        nifOnly.setOnCheckedChangeListener((b,value)->renderResults());
        button(root,"Прочитать первый найденный NIF (только заголовок)",this::inspectFirstNif);
        button(root,"Исследовать блоки выбранного NIF · v1.0",this::inspectNifBlocks);
        button(root,"Показать BSTriShape в Vulkan · v2.4",this::previewNifGeometry);
        button(root,"Пути текстур NIF · v2.4",this::inspectNifMaterials);
        button(root,"Найти DDS в папке Data · v2.4",this::pickDataForDds);
        button(root,"Заголовки найденных DDS · v2.4",this::inspectFoundDdsHeaders);
        button(root,"Показать текстуры DDS · BC1/2/3/4/5 · v2.4",this::previewFoundDds);
        button(root,"Показать NIF с DDS в Vulkan · v2.4",this::previewTexturedNif);
        button(root,"Скопировать список и отчёт",this::copyReport);
        button(root,"Назад",this::finish);
        summary=text("Выбери BSA-файл из памяти телефона. Архив будет открыт только на чтение.",14,0xffeeeeee);
        summary.setTextIsSelectable(true);summary.setPadding(dp(8),dp(10),dp(8),dp(10));
        root.addView(summary);
        results=text("",12,0xffe6e2ed);results.setTextIsSelectable(true);
        results.setPadding(dp(8),dp(10),dp(8),dp(10));root.addView(results);
        inspectionOutput=text("Выбранный NIF ещё не проверялся.",13,0xffd9c1ff);
        inspectionOutput.setTextIsSelectable(true);root.addView(inspectionOutput);
        setContentView(scroll);
    }
    private int dp(float a){return (int)(a*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(sp);t.setPadding(0,dp(9),0,dp(9));return t;}
    private void button(LinearLayout root,String caption,Runnable action){
        Button b=new Button(this);b.setText(caption);b.setAllCaps(false);b.setOnClickListener(v->action.run());root.addView(b);
    }
    private void pick(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,PICK_BSA);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==PICK_DATA){scanDdsInTree(data.getData());return;}
        if(request!=PICK_BSA)return;
        final Uri uri=data.getData();
        String name="Выбранный BSA";
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())name=c.getString(0);
        }catch(Exception ignored){}
        final String chosen=name;
        summary.setText("Читаю метаданные: "+chosen+"…");results.setText("");catalog=null;currentUri=null;
        materialDdsPaths=Collections.emptyList();materialNifPath="";materialBsaUri=null;
        foundDds=Collections.emptyList();lastDdsSearchReport="";
        inspectionOutput.setText("Выбранный NIF ещё не проверялся.");
        new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                BsaCatalogReader.Catalog found=BsaCatalogReader.read(in);
                runOnUiThread(()->{if(destroyed)return;currentName=chosen;catalog=found;currentUri=uri;renderResults();});
            }catch(Exception e){
                runOnUiThread(()->{if(destroyed)return;summary.setText("Ошибка чтения каталога: "+e.getMessage());results.setText("Файл не изменён.");});
            }
        },"bsa-v105-catalog").start();
    }
    private void renderResults(){
        if(catalog==null||summary==null||results==null)return;
        BsaCatalogReader.Catalog c=catalog;
        String info="Архив: "+currentName+"\nBSA версия 105; папок: "+c.folderCount+"; файлов: "+c.fileCount+
            "; записей NIF: "+c.nifCount+"\nФлаги: 0x"+Long.toHexString(c.flags)+
            "\nИндекс поиска: "+c.indexedCount+" / "+c.fileCount+
            (c.partialIndex?" (ограничение объёма; поиск неполный)":" (полный)")+
            "\nЧтение только каталога; данные файлов не распакованы и не изменены.";
        summary.setText(info);
        List<BsaCatalogReader.Entry> found=c.find(filter.getText().toString(),nifOnly.isChecked(),60);
        StringBuilder b=new StringBuilder("Совпадения (до 60): "+found.size()+"\n\n");
        for(BsaCatalogReader.Entry entry:found){
            b.append("• ").append(entry.path).append("\n  ")
             .append(entry.storedBytes).append(" байт · 0x")
             .append(Long.toHexString(entry.offset)).append(" · ")
             .append(entry.compressed?"сжат":"без сжатия").append("\n");
        }
        if(found.isEmpty())b.append("В индексированной части архива совпадений нет.");
        results.setText(b.toString());
    }
    private void inspectFirstNif(){
        if(catalog==null || currentUri==null){
            inspectionOutput.setText("Сначала выбери BSA и дождись загрузки каталога.");return;
        }
        List<BsaCatalogReader.Entry> found=catalog.find(filter.getText().toString(),true,1);
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
        List<BsaCatalogReader.Entry> found=catalog.find(filter.getText().toString(),true,1);
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
        List<BsaCatalogReader.Entry> found=catalog.find(filter.getText().toString(),true,1);
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
                    if(destroyed||!uri.equals(currentUri))return;
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

    /** Read only one NIF and remember its declared DDS paths for a separate Data scan. */
    private void inspectNifMaterials(){
        if(catalog==null||currentUri==null){inspectionOutput.setText("Сначала выбери архив BSA.");return;}
        List<BsaCatalogReader.Entry> found=catalog.find(filter.getText().toString(),true,1);
        if(found.isEmpty()){inspectionOutput.setText("NIF не найден. Введи lureargonian01.nif");return;}
        final BsaCatalogReader.Entry entry=found.get(0);
        final Uri uri=currentUri;
        final long flags=catalog.flags;
        materialDdsPaths=Collections.emptyList();materialNifPath="";materialBsaUri=null;
        foundDds=Collections.emptyList();lastDdsSearchReport="";
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
                    "\n\nПоиск v2.4: "+safeTargets.size()+" подходящих путей DDS"+
                    (unsupported>0?" · неподдерживаемых или сверх лимита: "+unsupported:"")+". "+
                    "Нажми «Найти DDS в папке Data» и выбери каталог игры Data.";
                runOnUiThread(()->{
                    if(destroyed||!uri.equals(currentUri))return;
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
            inspectionOutput.setText("Сначала выбери NIF и нажми «Пути текстур NIF · v2.4». "+
                "После получения путей DDS можно искать их в папке Data.");return;
        }
        List<BsaCatalogReader.Entry> found=catalog==null?Collections.emptyList():
            catalog.find(filter.getText().toString(),true,1);
        if(found.isEmpty()||!found.get(0).path.equals(materialNifPath)){
            inspectionOutput.setText("Выбран другой NIF. Повтори «Пути текстур NIF», затем поиск DDS.");return;
        }
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
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
                        inspectionOutput.setText("Поиск DDS v2.4: проверено "+checked+" из "+docs.size()+
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
                StringBuilder out=new StringBuilder("Поиск DDS v2.4 · только каталоги BSA\n");
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
                out.append("\nDDS не декодированы и не распакованы. Файлы игры не изменены.");
                final String report=out.toString();
                runOnUiThread(()->{
                    if(!destroyed&&nifUri.equals(currentUri)&&nifName.equals(materialNifPath)){
                        foundDds=Collections.unmodifiableList(new ArrayList<>(located));
                        lastDdsSearchReport=report;
                        inspectionOutput.setText(report+"\n\nНажми «Заголовки найденных DDS · v2.4» для проверки формата изображений.");
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
            report.append("\n\nЗаголовки DDS v2.4 · только чтение выбранных записей BSA\n");
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

    /** v2.4: CPU decode only the first mip of up to four explicitly matched DDS records. */
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
            StringBuilder report=new StringBuilder("Предпросмотр DDS v2.4 · BC1/2/3/4/5\n");
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
     * v2.4 opt-in diffuse-only preview. Requires an explicitly selected NIF,
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
        List<BsaCatalogReader.Entry> candidates=catalog.find(filter.getText().toString(),true,1);
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
                                if(candidate.width!=pixels.width||candidate.height!=pixels.height)
                                    throw new IOException("normal DDS отличается по размеру");
                                normalImage=candidate;
                                normalStatus="нормали: "+candidate.width+"×"+candidate.height+
                                    " · "+candidate.format;
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
        if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("Skyrim SE BSA v2.4",summary.getText()+"\n\n"+results.getText()+"\n\n"+inspectionOutput.getText()));
    }
    @Override protected void onDestroy(){destroyed=true;super.onDestroy();}
}
