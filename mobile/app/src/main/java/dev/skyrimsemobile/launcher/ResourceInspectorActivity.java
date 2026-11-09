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
    private static final int PICK_DATA=707, PICK_RESOURCE=708, PICK_PLUGIN=709, PICK_GROUPS=710, PICK_NESTED_CELL=711, PICK_NESTED_WRLD=712, PICK_MODEL_BSA=713, PICK_DATA_MODELS=714;
    public static final String PREF_REPORT="resource_inspector_report";
    private SharedPreferences prefs;
    private TextView report;
    private boolean closed=false;
    private Tes4BaseFormResolver.Result lastBaseRecords;
    private long multiArchiveScanSequence=0;
    private String lastNestedReport="";
    private String lastMultiBsaReport="";
    private java.util.List<ModelProbeCandidate> lastNifCandidates=java.util.Collections.emptyList();
    private static final class ModelProbeCandidate {
        final Uri archiveUri;
        final String archiveName;
        final BsaCatalogReader.Entry entry;
        final long flags;
        ModelProbeCandidate(Uri uri,String name,BsaCatalogReader.Entry entry,long flags){
            archiveUri=uri;archiveName=name;this.entry=entry;this.flags=flags;
        }
    }
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
        TextView title=new TextView(this);title.setText("Ресурсы Skyrim SE · " + InstalledAppVersion.label(this));
        title.setTextSize(22);title.setTextColor(0xffdbc5fb);title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView info=new TextView(this);
        info.setText("Чтение заголовков BSA/NIF/TES4, списка Data и каталога файлов внутри BSA 105. Никакой распаковки, изменения файлов или запуска Skyrim SE.");
        info.setTextColor(0xffd5d3dd);info.setTextSize(13);info.setPadding(0,dp(12),0,dp(14));root.addView(info);
        addButton(root,"Проверить папку игры / Data",()->scanCurrent());
        addButton(root,"Выбрать папку Data",()->selectFolder());
        addButton(root,"Проверить файл BSA / NIF",()->selectFile());
        addButton(root,"Проверить ESM / ESP / ESL (заголовок TES4)",()->selectPlugin());
        addButton(root,"Индекс ESM / ESP / ESL · группы GRUP",()->selectGroupFile());
        addButton(root,"Записи CELL · FormID / базовые модели (образец)",()->selectNestedFile(PICK_NESTED_CELL));
        addButton(root,"Записи WRLD · FormID / базовые модели (образец)",()->selectNestedFile(PICK_NESTED_WRLD));
        addButton(root,"Сопоставить модели MODL с BSA",()->matchSelectedBsa(false));
        addButton(root,"Найти MODL во всех BSA папки Data",this::startMultiBsaSearch);
        addButton(root,"Проверить содержимое найденных NIF",this::verifyFoundModels);
        addButton(root,"Выбрать другой BSA для моделей",()->matchSelectedBsa(true));
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
        if(getIntent()!=null && getIntent().getBooleanExtra("open_grup",false))
            scroll.post(this::selectGroupFile);
        else if(getIntent()!=null && getIntent().getBooleanExtra("open_tes4",false))
            scroll.post(this::selectPlugin);
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
    private void selectPlugin(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,PICK_PLUGIN);
    }
    private void selectGroupFile(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,PICK_GROUPS);
    }
    private void selectNestedFile(int requestCode){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,requestCode);
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
        else if(req==PICK_PLUGIN)inspectPlugin(uri);
        else if(req==PICK_GROUPS)inspectPluginGroups(uri);
        else if(req==PICK_NESTED_CELL)inspectNested(uri,"CELL");
        else if(req==PICK_NESTED_WRLD)inspectNested(uri,"WRLD");
        else if(req==PICK_DATA_MODELS){
            BsaSessionStore saved=new BsaSessionStore(this);
            if(saved.retainReadPermission(uri)){
                saved.saveDataTree(uri);
                prefs.edit().putString("resource_data_uri",uri.toString()).apply();
            }
            scanAllModelArchives(uri);
        }
        else if(req==PICK_MODEL_BSA){
            String archiveName=getName(uri);
            BsaSessionStore session=new BsaSessionStore(this);
            if(session.retainReadPermission(uri))session.saveArchive(uri,archiveName);
            inspectModelArchive(uri,archiveName);
        }
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
    private void inspectPlugin(Uri uri){
        update("Читаю заголовок TES4 (только чтение)...");
        new Thread(()->{
            try{
                String name=getName(uri);
                String suffix=name.toLowerCase(Locale.ROOT);
                if(!suffix.endsWith(".esm")&&!suffix.endsWith(".esp")&&!suffix.endsWith(".esl"))
                    throw new java.io.IOException("Выберите файл .esm, .esp или .esl");
                Tes4PluginHeaderReader.Result value;
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    value=Tes4PluginHeaderReader.inspect(in);
                }
                String message="Файл: "+name+"\n"+value+"\nИсходные файлы не изменялись.";
                runOnUiThread(()->update(message));
            }catch(Exception e){
                String message="Ошибка TES4: "+e.getClass().getSimpleName()+": "+e.getMessage();
                runOnUiThread(()->update(message));
            }
        },"tes4-header-reader").start();
    }
    private long getFileSize(Uri uri){
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){
            if(c!=null&&c.moveToFirst()&&!c.isNull(0))return c.getLong(0);
        }catch(Exception ignored){}
        return -1;
    }
    private void inspectPluginGroups(Uri uri){
        update("Проверяю верхние группы GRUP · только чтение...");
        new Thread(()->{
            try{
                String name=getName(uri);
                String suffix=name.toLowerCase(Locale.ROOT);
                if(!suffix.endsWith(".esm")&&!suffix.endsWith(".esp")&&!suffix.endsWith(".esl"))
                    throw new java.io.IOException("Выберите файл .esm, .esp или .esl");
                long fileSize=getFileSize(uri);
                Tes4TopLevelGroupReader.Result index;
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    index=Tes4TopLevelGroupReader.scan(in,fileSize);
                }
                String message="Файл: "+name+"\n"+index+"\nИсходные файлы не изменялись.";
                runOnUiThread(()->update(message));
            }catch(Exception e){
                String message="Ошибка индекса GRUP: "+e.getClass().getSimpleName()+": "+e.getMessage();
                runOnUiThread(()->update(message));
            }
        },"tes4-grup-index").start();
    }
    private Uri persistedDataTree(){
        String saved=prefs.getString("resource_data_uri","");
        if(!saved.isEmpty()){
            try{
                Uri parsed=Uri.parse(saved);
                for(android.content.UriPermission permission:
                        getContentResolver().getPersistedUriPermissions()){
                    if(permission.isReadPermission()&&parsed.equals(permission.getUri()))
                        return parsed;
                }
            }catch(IllegalArgumentException ignored){}
        }
        return new BsaSessionStore(this).lastDataTree();
    }
    private void startMultiBsaSearch(){
        if(lastBaseRecords==null){
            update("Сначала открой Skyrim.esm через «Записи CELL» или «Записи WRLD», "
                  +"чтобы получить базовые FormID и пути MODL.");
            return;
        }
        Uri tree=persistedDataTree();
        if(tree!=null){scanAllModelArchives(tree);return;}
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
                   Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_DATA_MODELS);
    }
    private void verifyFoundModels(){
        java.util.List<ModelProbeCandidate> candidates=new java.util.ArrayList<>(lastNifCandidates);
        final String searchReport=lastMultiBsaReport;
        if(candidates.isEmpty()){
            update("Сначала нажми «Найти MODL во всех BSA папки Data». "
                  +"Нужны найденные записи NIF, а не только имена из Skyrim.esm.");
            return;
        }
        final long sequence=multiArchiveScanSequence;
        update(searchReport+"\n\nЧитаю первые найденные NIF (не более 3 записей)...");
        new Thread(()->{
            StringBuilder sb=new StringBuilder(searchReport);
            sb.append("\n\nПроверка содержимого найденных NIF · только чтение\n");
            int inspected=0,valid=0;
            for(ModelProbeCandidate candidate:candidates){
                if(inspected>=Tes4MatchedNifVerifier.MAX_MODELS)break;
                inspected++;
                try(InputStream in=getContentResolver().openInputStream(candidate.archiveUri)){
                    Tes4MatchedNifVerifier.Verification result=
                        Tes4MatchedNifVerifier.verify(in,candidate.entry,
                            candidate.flags,candidate.archiveName);
                    sb.append(result.report()).append('\n');
                    valid++;
                }catch(Exception e){
                    sb.append("• ").append(candidate.entry.path).append(" · ")
                      .append(candidate.archiveName).append(" · ОШИБКА: ")
                      .append(e.getClass().getSimpleName()).append(": ")
                      .append(e.getMessage()).append('\n');
                }
            }
            sb.append("Проверено: ").append(inspected)
              .append(" · распознано NIF: ").append(valid).append('\n');
            sb.append("Распаковка только в памяти; без записи ресурсов и рендеринга.");
            String finalReport=sb.toString();
            runOnUiThread(()->{
                if(sequence==multiArchiveScanSequence)update(finalReport);
            });
        },"tes4-bsa-nif-verify").start();
    }
    private void scanAllModelArchives(Uri tree){
        final Tes4BaseFormResolver.Result baseRecords=lastBaseRecords;
        final String baseReport=lastNestedReport;
        if(baseRecords==null){update("Нет базовых записей ESM.");return;}
        final long sequence=++multiArchiveScanSequence;
        lastNifCandidates=java.util.Collections.emptyList();
        lastMultiBsaReport="";
        update(baseReport+"\n\nИщу модели в каталогах BSA папки Data...");
        new Thread(()->{
            try{
                String rootId=DocumentsContract.getTreeDocumentId(tree);
                String targetId=rootId;
                if(!"Data".equalsIgnoreCase(nameDocument(tree,rootId))){
                    String child=findFolder(tree,rootId,"Data");
                    if(child!=null)targetId=child;
                }
                ArrayList<Item> listed=readChildren(tree,targetId,4096);
                ArrayList<Item> archives=new ArrayList<>();
                for(Item item:listed){
                    if(!item.directory&&item.name.toLowerCase(Locale.ROOT).endsWith(".bsa"))
                        archives.add(item);
                }
                // Skyrim - Meshes*.bsa contain the most directly relevant NIFs.
                archives.sort((a,b)->{
                    boolean am=a.name.toLowerCase(Locale.ROOT).contains("meshes");
                    boolean bm=b.name.toLowerCase(Locale.ROOT).contains("meshes");
                    if(am!=bm)return am?-1:1;
                    return a.name.compareToIgnoreCase(b.name);
                });
                Tes4MultiBsaModelMatcher.Search search=
                    Tes4MultiBsaModelMatcher.begin(baseRecords.found.values());
                java.util.List<ModelProbeCandidate> verifiedCandidates=new java.util.ArrayList<>();
                for(Item archive:archives){
                    if(search.allFound())break;
                    if(search.archivesChecked()>=Tes4MultiBsaModelMatcher.MAX_ARCHIVES){
                        search.markCapped();break;
                    }
                    try(InputStream in=getContentResolver().openInputStream(
                        DocumentsContract.buildDocumentUriUsingTree(tree,archive.id))){
                        BsaCatalogReader.Catalog catalog=BsaCatalogReader.read(in);
                        search.accept(archive.name,catalog);
                        if(verifiedCandidates.size()<Tes4MatchedNifVerifier.MAX_MODELS){
                            Uri archiveUri=DocumentsContract.buildDocumentUriUsingTree(tree,archive.id);
                            for(Tes4MultiBsaModelMatcher.Hit hit:search.hitsInArchive(archive.name)){
                                if(verifiedCandidates.size()>=Tes4MatchedNifVerifier.MAX_MODELS)break;
                                verifiedCandidates.add(new ModelProbeCandidate(
                                    archiveUri,archive.name,hit.entry,catalog.flags));
                            }
                        }
                    }catch(Exception failure){
                        search.failed(archive.name,failure);
                    }
                }
                String full=baseReport+"\n\n"+
                    "Папка: "+nameDocument(tree,targetId)+"\n"+
                    search.report(archives.size())+
                    (listed.size()>=4096
                      ?"\nВнимание: список Data ограничен 4096 элементами."
                      :"")+
                    "\nИсходные файлы не изменялись.";
                runOnUiThread(()->{
                    if(sequence==multiArchiveScanSequence){
                        lastNifCandidates=java.util.Collections.unmodifiableList(
                            new java.util.ArrayList<>(verifiedCandidates));
                        lastMultiBsaReport=full;
                        update(full);
                    }
                });
            }catch(Exception e){
                String msg=baseReport+"\n\nОшибка поиска BSA: "+
                    e.getClass().getSimpleName()+": "+e.getMessage();
                runOnUiThread(()->{
                    if(sequence==multiArchiveScanSequence)update(msg);
                });
            }
        },"tes4-multi-bsa-index").start();
    }
    private void matchSelectedBsa(boolean forcePicker){
        if(lastBaseRecords==null){
            update("Сначала проверь записи CELL или WRLD в ESM/ESP/ESL, "
                   +"чтобы получить базовые FormID и пути MODL.");
            return;
        }
        if(!forcePicker){
            BsaSessionStore session=new BsaSessionStore(this);
            Uri saved=new BsaSessionStore(this).lastArchive();
            if(saved!=null){
                inspectModelArchive(saved,session.archiveDisplayName());
                return;
            }
        }
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,PICK_MODEL_BSA);
    }
    private void inspectModelArchive(Uri uri,String archiveName){
        final Tes4BaseFormResolver.Result baseRecords=lastBaseRecords;
        final String baseReport=lastNestedReport;
        if(baseRecords==null){update("Нет результатов базовых записей.");return;}
        if(archiveName==null||!archiveName.toLowerCase(Locale.ROOT).endsWith(".bsa")){
            update(baseReport+"\n\nДля проверки выберите архив с расширением .bsa");
            return;
        }
        update(baseReport+"\n\nСопоставляю пути MODL с каталогом "+archiveName+"...");
        new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                BsaCatalogReader.Catalog catalogue=BsaCatalogReader.read(in);
                Tes4BsaModelMatcher.Result matches=Tes4BsaModelMatcher.match(
                    baseRecords.found.values(),catalogue,archiveName);
                String message=baseReport+"\n\n"+matches+
                    "\nИсходные файлы не изменялись.";
                runOnUiThread(()->update(message));
            }catch(Exception e){
                String message=baseReport+"\n\nОшибка проверки BSA: "+
                    e.getClass().getSimpleName()+": "+e.getMessage();
                runOnUiThread(()->update(message));
            }
        },"tes4-bsa-model-match").start();
    }
    private void inspectNested(Uri uri,String section){
        multiArchiveScanSequence++;
        lastNifCandidates=java.util.Collections.emptyList();
        lastMultiBsaReport="";
        lastBaseRecords=null;
        lastNestedReport="";
        update("Читаю вложенные заголовки "+section+" · ограниченный образец...");
        new Thread(()->{
            try{
                String name=getName(uri);
                String suffix=name.toLowerCase(Locale.ROOT);
                if(!suffix.endsWith(".esm")&&!suffix.endsWith(".esp")&&!suffix.endsWith(".esl"))
                    throw new java.io.IOException("Выберите файл .esm, .esp или .esl");
                long fileSize=getFileSize(uri);
                Tes4NestedRecordSampler.Result result;
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    result=Tes4NestedRecordSampler.scan(in,section,fileSize);
                }
                StringBuilder output=new StringBuilder("Файл: ").append(name)
                    .append("\n").append(result);
                Tes4BaseFormResolver.Result bases=null;
                if(!result.placements.isEmpty()){
                    try(InputStream baseIn=getContentResolver().openInputStream(uri)){
                        Tes4BaseFormResolver.Result resolved=
                            Tes4BaseFormResolver.resolve(baseIn,result.placements,fileSize);
                        output.append("\n\n").append(resolved.report(result.placements));
                        bases=resolved;
                    }catch(Exception lookupError){
                        output.append("\n\nБазовые записи: чтение не удалось: ")
                            .append(lookupError.getClass().getSimpleName())
                            .append(": ").append(lookupError.getMessage());
                    }
                }
                output.append("\nИсходные файлы не изменялись.");
                String message=output.toString();
                final Tes4BaseFormResolver.Result readyBases=bases;
                runOnUiThread(()->{
                    lastBaseRecords=readyBases;
                    lastNestedReport=message;
                    update(message);
                });
            }catch(Exception e){
                String message="Ошибка вложенных записей "+section+": "+e.getClass().getSimpleName()+": "+e.getMessage();
                runOnUiThread(()->update(message));
            }
        },"tes4-nested-sample").start();
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
