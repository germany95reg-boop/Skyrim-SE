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
import org.json.JSONObject;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

/** Read-only Skyrim SE file inspector via Android Storage Access Framework. */
public final class ResourceInspectorActivity extends Activity {
    private static final int PICK_DATA=707, PICK_RESOURCE=708, PICK_PLUGIN=709, PICK_GROUPS=710, PICK_NESTED_CELL=711, PICK_NESTED_WRLD=712, PICK_MODEL_BSA=713, PICK_DATA_MODELS=714, PICK_VULKAN_PREVIEW=715;
    public static final String PREF_REPORT="resource_inspector_report";
    private SharedPreferences prefs;
    private TextView report,folderStatus;
    private Button previewButton;
    private Tes4PlacedNifScene.Preview lastPreview;
    private NifCompositeDdsAtlas.Result lastSceneDiffuseAtlas;
    private int lastSceneRefCount,lastSceneRequestedDds,lastSceneDecodedDds,
        lastSceneTexturedFaces;
    private String placedPreviewToken,placedPreviewExpected;
    private boolean automaticScenePending=false,automaticPlacedPreviewActive=false;
    private java.util.List<Tes4PlacementSubrecordReader.Placement> lastPlacements=java.util.Collections.emptyList();
    private volatile boolean closed=false;
    private volatile long automaticAnalysisSequence=0;
    private Tes4BaseFormResolver.Result lastBaseRecords;
    private volatile long multiArchiveScanSequence=0;
    private volatile long nifVerificationSequence=0;
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
    private String text="Подключи папку Skyrim SE один раз. При следующем входе приложение само проверит файлы, откроет локальную сцену в Vulkan и вернёт аппаратный отчёт.\nИсходные файлы игры не изменяются.";
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
        info.setText("Один раз разреши доступ к папке Skyrim SE. Дальше приложение само найдёт Data, Skyrim.esm, BSA и NIF и проверит их без дополнительных выборов.");
        info.setTextColor(0xffd5d3dd);info.setTextSize(14);info.setPadding(0,dp(12),0,dp(14));root.addView(info);
        folderStatus=new TextView(this);
        folderStatus.setTextSize(13);folderStatus.setTextColor(0xffb1dda9);
        folderStatus.setPadding(dp(6),dp(5),dp(6),dp(14));
        root.addView(folderStatus);
        addPrimaryButton(root,"Проверить Skyrim автоматически",this::startAutomaticAnalysis);
        previewButton=addButton(root,"Просмотреть найденный объект в 3D",this::openPlacedPreview);
        previewButton.setVisibility(View.GONE);
        addButton(root,"Выбрать папку Skyrim",this::selectFolder);
        addButton(root,"Скопировать отчёт",()->{
            ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("Skyrim SE Resource Report",text));
        });
        report=new TextView(this);report.setTextColor(0xffeeeeee);report.setTextSize(14);
        report.setTextIsSelectable(true);report.setPadding(dp(12),dp(14),dp(12),dp(14));
        report.setBackgroundColor(0xff22232e);root.addView(report);
        LinearLayout advanced=new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);
        addButton(root,"Для разработчика · дополнительные проверки",()->{
            advanced.setVisibility(advanced.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
        });
        root.addView(advanced);
        addButton(advanced,"Проверить папку игры / Data",this::scanCurrent);
        addButton(advanced,"Проверить файл BSA / NIF",this::selectFile);
        addButton(advanced,"Заголовок ESM / ESP / ESL",this::selectPlugin);
        addButton(advanced,"Индекс ESM / ESP / ESL · GRUP",this::selectGroupFile);
        addButton(advanced,"Записи CELL (образец)",()->selectNestedFile(PICK_NESTED_CELL));
        addButton(advanced,"Записи WRLD (образец)",()->selectNestedFile(PICK_NESTED_WRLD));
        addButton(advanced,"Сопоставить MODL с одним BSA",()->matchSelectedBsa(false));
        addButton(advanced,"Найти MODL во всех BSA",this::startMultiBsaSearch);
        addButton(advanced,"Повторно проверить содержимое NIF",this::verifyFoundModels);
        addButton(advanced,"Выбрать другой BSA",()->matchSelectedBsa(true));
        addButton(advanced,"Ручная проверка NIF/DDS",()->startActivity(new Intent(this,BsaCatalogActivity.class)));
        addButton(root,"Вернуться в лаунчер",()->finish());
        setContentView(scroll);
        update(text);
        refreshFolderStatus();
        if(getIntent()!=null && getIntent().getBooleanExtra("open_grup",false))
            scroll.post(this::selectGroupFile);
        else if(getIntent()!=null && getIntent().getBooleanExtra("open_tes4",false))
            scroll.post(this::selectPlugin);
        else if(saved==null&&persistedDataTree()!=null)
            scroll.post(this::startAutomaticAnalysis);
    }
    private int dp(float d){return (int)(getResources().getDisplayMetrics().density*d+.5f);}
    private Button addButton(LinearLayout p,String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(47));lp.bottomMargin=dp(6);p.addView(b,lp);
        return b;
    }
    private void addPrimaryButton(LinearLayout p,String title,Runnable action){
        Button b=new Button(this);
        b.setText(title);b.setTextSize(16);b.setAllCaps(false);
        b.setTextColor(0xffffffff);
        GradientDrawable background=new GradientDrawable();
        background.setColor(0xff7259d7);
        background.setCornerRadius(dp(16));
        b.setBackground(background);
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(60));
        lp.bottomMargin=dp(12);p.addView(b,lp);
    }
    private void clearPlacedPreview(){
        lastPreview=null;
        lastSceneDiffuseAtlas=null;
        lastSceneRefCount=0;lastSceneRequestedDds=0;
        lastSceneDecodedDds=0;lastSceneTexturedFaces=0;
        if(previewButton!=null){
            previewButton.setVisibility(View.GONE);
            previewButton.setText("Просмотреть найденный объект в 3D");
        }
    }
    private void openPlacedPreview(){
        openPlacedPreview(false);
    }
    private void openPlacedPreview(boolean automatic){
        Tes4PlacedNifScene.Preview preview=lastPreview;
        if(preview==null){
            update(text+"\n\n3D-предпросмотр пока не готов. Проверь Skyrim автоматически.");
            return;
        }
        if(lastSceneDiffuseAtlas!=null)
            NifGeometryTransfer.offerAtlas(preview.lines,preview.triangles,
                lastSceneDiffuseAtlas,preview.label,preview.details);
        else
            NifGeometryTransfer.offer(preview.lines,preview.triangles,
                preview.label,preview.details);
        automaticPlacedPreviewActive=automatic;
        placedPreviewToken=Long.toHexString(System.nanoTime());
        placedPreviewExpected=preview.label;
        prefs.edit().remove(VulkanSceneActivity.PREF_PLACED_RESULT).apply();
        Intent i=new Intent(this,VulkanSceneActivity.class);
        i.putExtra(VulkanSceneActivity.EXTRA_PLACED_PREVIEW_TOKEN,placedPreviewToken);
        i.putExtra(VulkanSceneActivity.EXTRA_PLACED_PREVIEW_LABEL,placedPreviewExpected);
        if(automatic)
            i.putExtra(VulkanSceneActivity.EXTRA_AUTOMATIC_PLACED_TEST,true);
        startActivityForResult(i,PICK_VULKAN_PREVIEW);
    }
    private void appendVulkanPreviewReceipt(){
        final String token=placedPreviewToken,expected=placedPreviewExpected;
        placedPreviewToken=null;
        placedPreviewExpected=null;
        if(token==null||expected==null)return;
        String report;
        try{
            String raw=prefs.getString(VulkanSceneActivity.PREF_PLACED_RESULT,"");
            JSONObject payload=new JSONObject(raw);
            if(!token.equals(payload.optString("token","")))
                report="3D-предпросмотр Vulkan: нет данных от текущего сеанса.\n"+
                       "Проверь, открывался ли 3D-экран и были ли кадры.";
            else{
                VulkanPlacedPreviewReceipt.Result checked=
                    VulkanPlacedPreviewReceipt.assessWithPixelCopy(expected,
                        payload.optString("geometry",""),
                        payload.optBoolean("running"),
                        payload.optBoolean("error"),
                        payload.optLong("frames"),
                        payload.optInt("edges"),
                        payload.optInt("triangles"),
                        payload.optInt("width"),
                        payload.optInt("height"),
                        payload.optDouble("fps",0),
                        payload.optString("frameCaptureStatus","not-requested"),
                        payload.optString("frameCaptureNote",""),
                        payload.optInt("frameNonBackground",0),
                        payload.optInt("frameSamplePixels",0),
                        payload.optInt("frameForegroundWidth",0),
                        payload.optInt("frameForegroundHeight",0));
                report=checked.report;
            }
        }catch(Exception missing){
            report="3D-предпросмотр Vulkan: кадры не подтверждены. "+
                "Открой 3D-просмотр повторно, либо пришли скриншот ошибки.";
        }
        prefs.edit().remove(VulkanSceneActivity.PREF_PLACED_RESULT).apply();
        update(text+"\n\n"+report);
    }
    /**
     * On-device automatic REFR check returns real native GPU statistics, all
     * three FPS modes and one PixelCopy sample. Distinguish missing evidence
     * from successful rendering; never call an untested texture "correct".
     */
    private void appendAutomaticSceneReceipt(int resultCode,Intent data){
        String token=placedPreviewToken,expected=placedPreviewExpected;
        placedPreviewToken=null;
        placedPreviewExpected=null;
        if(expected==null||token==null)return;
        try{
            String delivered=data==null?"":data.getStringExtra(
                VulkanSceneActivity.EXTRA_AUTOMATIC_PLACED_TOKEN);
            JSONObject stats=new JSONObject(data==null?"{}":data.getStringExtra(
                VulkanSceneActivity.EXTRA_DEVICE_AUTOTEST_RESULT));
            if(!token.equals(delivered))throw new java.io.IOException(
                "Нет подписанного результатом токена текущей сцены");
            org.json.JSONArray stages=stats.optJSONArray("fpsStages");
            int count=stages==null?0:stages.length();
            int[] caps=new int[count];boolean[] passed=new boolean[count];
            for(int i=0;i<count;i++){
                JSONObject stage=stages.optJSONObject(i);
                caps[i]=stage==null?-1:stage.optInt("cap",-1);
                passed[i]=stage!=null&&stage.optBoolean("passed",false)&&
                    stage.optBoolean("capConfirmed",false)&&
                    stage.optLong("frames",0)>0&&stage.optLong("sampleMillis",0)>=1800;
            }
            Tes4PlacedNifScene.Preview preview=lastPreview;
            Tes4AutomaticSceneReceipt.Result checked=Tes4AutomaticSceneReceipt.assess(
                expected,stats.optString("geometry",""),
                resultCode==RESULT_OK,stats.optBoolean("running",false),
                stats.optBoolean("error",true),stats.optLong("frames",0),
                stats.optInt("edges",0),stats.optInt("triangles",0),
                preview==null?-1:preview.triangleCount,
                stats.optInt("width",0),stats.optInt("height",0),
                stats.optDouble("fps",0),lastSceneDiffuseAtlas!=null,
                stats.optString("textureMode","off"),
                stats.optString("frameCaptureStatus","not-requested"),
                stats.optInt("frameNonBackground",0),
                stats.optInt("frameSamplePixels",0),
                caps,passed,stats.optString("frameCaptureNote",""),
                data==null?"Vulkan был закрыт без отчёта":
                    data.getStringExtra(VulkanSceneActivity.EXTRA_DEVICE_AUTOTEST_ERROR));
            checked=Tes4AutomaticSceneReceipt.withVisualQuality(
                checked,lastSceneRefCount,lastSceneRequestedDds,
                lastSceneDecodedDds,
                lastSceneDiffuseAtlas==null?0:lastSceneTexturedFaces,
                preview==null?0:preview.triangleCount,
                stats.optInt("frameNonBackground",0),
                stats.optInt("frameSamplePixels",0));
            update(text+"\n\n"+checked.report);
        }catch(Exception invalid){
            update(text+"\n\n[FAIL] Автоматический Vulkan-тест локальной сцены: "+
                invalid.getMessage()+"\nПопробуй повторить проверку. "+
                "Приложение не может считать неподтверждённые кадры успешными.");
        }
    }
    private void refreshFolderStatus(){
        if(folderStatus==null)return;
        folderStatus.setText(persistedDataTree()==null
            ?"Папка Skyrim не выбрана · нужен однократный доступ Android"
            :"Папка Skyrim подключена · доступ сохранён");
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
        if(req==PICK_VULKAN_PREVIEW){
            if(automaticPlacedPreviewActive)
                appendAutomaticSceneReceipt(result,data);
            else
                appendVulkanPreviewReceipt();
            automaticPlacedPreviewActive=false;
            return;
        }
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(req==PICK_DATA){
            BsaSessionStore session=new BsaSessionStore(this);
            boolean retained=session.retainReadPermission(uri);
            if(retained){
                prefs.edit().putString("resource_data_uri",uri.toString()).apply();
                session.saveDataTree(uri);
            }else{
                prefs.edit().remove("resource_data_uri").apply();
            }
            refreshFolderStatus();
            analyzeAutomaticFolder(uri);
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
    private void startAutomaticAnalysis(){
        Uri saved=persistedDataTree();
        if(saved==null){
            update("Сначала выбери папку Skyrim SE. Доступ запрашивается Android один раз.");
            selectFolder();
            return;
        }
        analyzeAutomaticFolder(saved);
    }
    private void analyzeAutomaticFolder(Uri tree){
        final long sequence=++automaticAnalysisSequence;
        automaticScenePending=true;
        multiArchiveScanSequence++;
        nifVerificationSequence++;
        lastBaseRecords=null;
        lastNestedReport="";
        lastPlacements=java.util.Collections.emptyList();
        clearPlacedPreview();
        lastNifCandidates=java.util.Collections.emptyList();
        lastMultiBsaReport="";
        update("Автоматическая проверка Skyrim SE\nШаг 1/4: ищу Data и Skyrim.esm...");
        new Thread(()->{
            try{
                String rootId=DocumentsContract.getTreeDocumentId(tree);
                String rootName=nameDocument(tree,rootId);
                String dataId=rootId;
                if(!SkyrimAutoDiscovery.dataDirectory(rootName)){
                    String child=findFolder(tree,rootId,"Data");
                    if(child!=null)dataId=child;
                }
                ArrayList<Item> entries=readChildren(tree,dataId,4096);
                java.util.List<String> names=new java.util.ArrayList<>();
                for(Item item:entries)if(!item.directory)names.add(item.name);
                String plugin=SkyrimAutoDiscovery.mainMaster(names);
                if(plugin==null)throw new java.io.IOException(
                    "Skyrim.esm не найден. Выбери папку Skyrim SE, содержащую Data, "+
                    "или саму папку Data.");
                Item master=null;
                for(Item item:entries)if(!item.directory&&item.name.equals(plugin)){
                    master=item;break;
                }
                if(master==null)throw new java.io.IOException("Не удалось получить Skyrim.esm");
                Uri esmUri=DocumentsContract.buildDocumentUriUsingTree(tree,master.id);
                final String chosenDataId=dataId;
                runOnUiThread(()->{
                    if(closed||sequence!=automaticAnalysisSequence)return;
                    update("Автоматическая проверка Skyrim SE\n"+
                           "Шаг 2/4: анализирую WRLD, объекты и базовые FormID...");
                    inspectNested(esmUri,"WRLD",tree,chosenDataId,sequence);
                });
            }catch(Exception e){
                String error="Автоматическая проверка: "+e.getMessage()+
                    "\nПри необходимости нажми «Выбрать папку Skyrim».";
                runOnUiThread(()->{
                    if(!closed&&sequence==automaticAnalysisSequence){
                        automaticScenePending=false;
                        update(error);
                    }
                });
            }
        },"skyrim-auto-locate-data").start();
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
        final java.util.List<Tes4PlacementSubrecordReader.Placement> linkedPoses=
            new java.util.ArrayList<>(lastPlacements);
        final java.util.Map<Long,Tes4BaseFormResolver.Base> linkedBases=
            lastBaseRecords==null?java.util.Collections.emptyMap():lastBaseRecords.found;
        clearPlacedPreview();
        if(candidates.isEmpty()){
            automaticScenePending=false;
            update("Сначала нажми «Найти MODL во всех BSA папки Data». "
                  +"Нужны найденные записи NIF, а не только имена из Skyrim.esm.");
            return;
        }
        final long sequence=multiArchiveScanSequence;
        final long verification=++nifVerificationSequence;
        update(searchReport+"\n\nЧитаю подходящие NIF (до "+
            Tes4MatchedNifVerifier.MAX_MODELS+" записей)...");
        new Thread(()->{
            StringBuilder sb=new StringBuilder(searchReport);
            sb.append("\n\nПроверка содержимого найденных NIF · только чтение\n");
            int inspected=0,valid=0;
            Tes4PlacedNifScene.Preview preparedPreview=null;
            Tes4LocalWorldScene.Builder localScene=new Tes4LocalWorldScene.Builder();
            Tes4LocalWorldScene.Planner sceneCandidates=new Tes4LocalWorldScene.Planner();
            Tes4PlacedNifScene.Link fallbackLink=null;
            NifGeometryDecoder.Parts fallbackParts=null;
            String previewNote="";
            java.util.Map<String,java.util.Map<Integer,String>> materialByNif=
                new java.util.LinkedHashMap<>();
            NifCompositeDdsAtlas.Result preparedAtlas=null;
            int requestedDds=0,decodedDds=0;
            for(ModelProbeCandidate candidate:candidates){
                if(inspected>=Tes4MatchedNifVerifier.MAX_MODELS)break;
                if(sequence!=multiArchiveScanSequence||
                   verification!=nifVerificationSequence||closed)return;
                inspected++;
                final int progress=inspected;
                runOnUiThread(()->{
                    if(sequence==multiArchiveScanSequence&&
                       verification==nifVerificationSequence)
                        update(searchReport+"\n\nПроверка содержимого NIF: "+
                               progress+" / "+candidates.size()+"...");
                });
                try(InputStream in=getContentResolver().openInputStream(candidate.archiveUri)){
                    BsaEntryInspector.Profile profile=BsaEntryInspector.inspectProfile(
                        in,candidate.entry,candidate.flags);
                    Tes4MatchedNifVerifier.Verification result=
                        new Tes4MatchedNifVerifier.Verification(candidate.archiveName,profile);
                    sb.append(result.report()).append('\n');
                    valid++;
                    if(profile.staticParts!=null&&profile.staticParts.available>0){
                        // Only actual BSTriShape -> shader -> texture-set links.
                        // DDS is loaded separately after the scene centre wins.
                        try(InputStream materials=getContentResolver().openInputStream(
                                candidate.archiveUri)){
                            NifMaterialLinkInspector.Result linksByShape=
                                BsaEntryInspector.readPartMaterialLinks(
                                    materials,candidate.entry,candidate.flags);
                            java.util.Map<Integer,String> diffuse=
                                new java.util.LinkedHashMap<>();
                            for(NifMaterialLinkInspector.Link link:linksByShape.links){
                                if(!link.resolved()||link.diffuse==null||
                                   link.diffuse.isEmpty())continue;
                                try{
                                    diffuse.put(link.shape,
                                        DdsArchiveFinder.normalizeDds(link.diffuse));
                                }catch(java.io.IOException badTexture){}
                            }
                            materialByNif.put(
                                Tes4BsaModelMatcher.canonicalNif(candidate.entry.path),
                                diffuse);
                        }catch(Exception noMaterial){
                            sb.append("DDS-связи ").append(candidate.entry.path)
                                .append(": недоступны; сохранён каркас и поверхность\n");
                        }
                        java.util.List<Tes4PlacedNifScene.Link> links=
                            Tes4PlacedNifScene.findVisibleAll(
                                linkedPoses,linkedBases,candidate.entry.path);
                        if(!links.isEmpty()&&fallbackLink==null){
                            fallbackLink=Tes4PlacedNifScene.findVisible(
                                linkedPoses,linkedBases,candidate.entry.path);
                            fallbackParts=profile.staticParts;
                        }
                        for(Tes4PlacedNifScene.Link link:links){
                            try{
                                sceneCandidates.offer(profile.staticParts,link);
                            }catch(java.io.IOException problem){
                                sb.append("3D: REFR 0x")
                                  .append(Long.toHexString(link.pose.recordFormId).toUpperCase(Locale.ROOT))
                                  .append(" пропущен: ").append(problem.getMessage()).append('\n');
                            }
                        }
                    }
                }catch(Exception e){
                    sb.append("• ").append(candidate.entry.path).append(" · ")
                      .append(candidate.archiveName).append(" · ОШИБКА: ")
                      .append(e.getClass().getSimpleName()).append(": ")
                      .append(e.getMessage()).append('\n');
                }
            }
            try{
                localScene=sceneCandidates.best();
            }catch(java.io.IOException error){
                sb.append("Выбор локальной 3D-сцены: ").append(error.getMessage()).append('\n');
            }
            Tes4LocalWorldScene.Builder textureOwner=localScene;
            if(localScene.instances()>0){
                try{
                    preparedPreview=localScene.build();
                    previewNote="3D-предпросмотр подготовлен: "+localScene.summary()+
                        "\nВыбор группы: "+sceneCandidates.summary();
                }catch(java.io.IOException error){
                    previewNote="Локальная 3D-сцена ограничена: "+error.getMessage();
                }
            }
            if(preparedPreview==null&&fallbackLink!=null&&fallbackParts!=null){
                try{
                    preparedPreview=Tes4PlacedNifScene.compose(fallbackParts,fallbackLink);
                    textureOwner=new Tes4LocalWorldScene.Builder();
                    textureOwner.add(fallbackParts,fallbackLink);
                    previewNote="3D-предпросмотр одного объекта: "+
                        fallbackLink.base.editorId+" · "+
                        preparedPreview.triangleCount+" треугольников.";
                }catch(java.io.IOException error){
                    previewNote="3D-предпросмотр не готов: "+error.getMessage();
                }
            }
            if(preparedPreview!=null){
                // Bounded, read-only search of exact DDS paths in Data BSA
                // archives. A corrupt/missing texture never hides the REFR scene.
                try{
                    java.util.List<String> requested=textureOwner.materialPaths(materialByNif);
                    requestedDds=requested.size();
                    if(!requested.isEmpty()){
                        LocalSceneDds found=loadLocalSceneDds(
                            requested,sequence,verification);
                        preparedAtlas=textureOwner.diffuseAtlas(materialByNif,found.decoded);
                        decodedDds=found.decoded.size();
                        previewNote+="\nDDS: "+decodedDds+" / "+
                            requested.size()+" декодировано, найдено по каталогам "+
                            found.indexed+"; просмотр "+
                            (preparedAtlas==null?"без DDS (геометрия сохранена)":
                              "с диффузным атласом "+
                              preparedAtlas.width+"×"+preparedAtlas.height+
                              ", сетка "+preparedAtlas.grid+"×"+preparedAtlas.grid)+
                            " · BSA проверено: "+found.archivesChecked+"."+
                            found.issues;
                    }else previewNote+="\nDDS: у отобранных NIF нет доступных "+
                        "путей диффузных текстур и UV.";
                }catch(Exception materialError){
                    previewNote+="\nDDS пропущены: "+materialError.getMessage()+
                        "; геометрия сохранена.";
                }
            }
            sb.append("Проверено: ").append(inspected)
              .append(" · распознано NIF: ").append(valid).append('\n');
            if(preparedPreview!=null)sb.append(previewNote).append('\n');
            else sb.append(previewNote.isEmpty()?
                "3D-предпросмотр не готов: нет подходящего видимого REFR/NIF в выборке.":
                previewNote).append('\n');
            sb.append("Распаковка и подготовка только в памяти; "+
                "автоматический Vulkan-тест запускается для найденной сцены, "+
                "исходные файлы не изменены.");
            String finalReport=sb.toString().replace(
                "Содержимое NIF: следующий этап (до 8 моделей)",
                "Содержимое NIF: распознано "+valid+" из "+inspected);
            final Tes4PlacedNifScene.Preview readyPreview=preparedPreview;
            final NifCompositeDdsAtlas.Result readyAtlas=preparedAtlas;
            final int readyRefCount=textureOwner.instances();
            final int readyRequestedDds=requestedDds,readyDecodedDds=decodedDds;
            runOnUiThread(()->{
                if(!closed&&sequence==multiArchiveScanSequence&&
                   verification==nifVerificationSequence){
                    lastPreview=readyPreview;
                    lastSceneDiffuseAtlas=readyAtlas;
                    lastSceneRefCount=readyRefCount;
                    lastSceneRequestedDds=readyRequestedDds;
                    lastSceneDecodedDds=readyDecodedDds;
                    lastSceneTexturedFaces=readyAtlas==null?0:
                        readyAtlas.texturedTriangles;
                    if(previewButton!=null){
                        previewButton.setVisibility(readyPreview==null?View.GONE:View.VISIBLE);
                        if(readyPreview!=null)
                            previewButton.setText(readyPreview.label.contains("локальная 3D-сцена")?
                                (readyAtlas==null?"Просмотреть локальную сцену в 3D":
                                    "Просмотреть локальную сцену с DDS в 3D"):
                                (readyAtlas==null?"Просмотреть найденный объект в 3D":
                                    "Просмотреть найденный объект с DDS в 3D"));
                    }
                    boolean auto=automaticScenePending;
                    automaticScenePending=false;
                    if(auto&&readyPreview!=null){
                        update(finalReport+"\n\nШаг 4/4: автоматически открываю локальную "+
                            "сцену для проверки GPU, DDS, FPS и PixelCopy…");
                        openPlacedPreview(true);
                    }else if(auto){
                        update(finalReport+"\n\n[FAIL] Автотест локальной сцены: "+
                            "пригодный REFR/NIF не найден, GPU не запускался.");
                    }else update(finalReport);
                }
            });
        },"tes4-bsa-nif-verify").start();
    }

    private static final class LocalSceneDds {
        final java.util.Map<String,DdsBcDecoder.Image> decoded;
        final int archivesChecked,indexed;
        final String issues;
        LocalSceneDds(java.util.Map<String,DdsBcDecoder.Image> decoded,
                      int checked,int indexed,String issues){
            this.decoded=decoded;archivesChecked=checked;
            this.indexed=indexed;this.issues=issues;
        }
    }
    private static final class SceneDdsFile {
        final Uri uri;
        final BsaCatalogReader.Entry entry;
        final long flags;
        SceneDdsFile(Uri uri,BsaCatalogReader.Entry entry,long flags){
            this.uri=uri;this.entry=entry;this.flags=flags;
        }
    }

    /**
     * v2.44: resolve exact NIF-declared diffuse DDS names in the same granted
     * Skyrim/Data tree. Index up to 24 BSA (texture archives first), decode at
     * most 15 first mips and 4 MiPixels. No path guessing or file writes.
     * All failures are optional: callers retain their untextured scene.
     */
    private LocalSceneDds loadLocalSceneDds(
            java.util.List<String> requested,long sequence,long verification)
            throws java.io.IOException{
        java.util.Map<String,DdsBcDecoder.Image> decoded=
            new java.util.LinkedHashMap<>();
        if(requested==null||requested.isEmpty())
            return new LocalSceneDds(decoded,0,0,"");
        Uri tree=persistedDataTree();
        if(tree==null)throw new java.io.IOException("нет доступа к папке Skyrim/Data");
        String rootId=DocumentsContract.getTreeDocumentId(tree);
        String dataId=rootId;
        if(!SkyrimAutoDiscovery.dataDirectory(nameDocument(tree,rootId))){
            String child=findFolder(tree,rootId,"Data");
            if(child!=null)dataId=child;
        }
        java.util.ArrayList<Item> entries=readChildren(tree,dataId,4096);
        java.util.ArrayList<Item> archives=new java.util.ArrayList<>();
        for(Item entry:entries)
            if(!entry.directory&&entry.name.toLowerCase(Locale.ROOT).endsWith(".bsa"))
                archives.add(entry);
        archives.sort((a,b)->{
            boolean ta=a.name.toLowerCase(Locale.ROOT).contains("textures");
            boolean tb=b.name.toLowerCase(Locale.ROOT).contains("textures");
            if(ta!=tb)return ta?-1:1;
            return a.name.compareToIgnoreCase(b.name);
        });
        java.util.Map<String,String> wanted=new java.util.LinkedHashMap<>(
            DdsArchiveFinder.targets(requested));
        java.util.Map<String,SceneDdsFile> found=new java.util.LinkedHashMap<>();
        int checked=0;
        for(Item archive:archives){
            if(wanted.isEmpty()||checked>=24)break;
            if(closed||sequence!=multiArchiveScanSequence||
               verification!=nifVerificationSequence)
                throw new java.io.IOException("Поиск DDS отменён новым сканированием");
            checked++;
            Uri uri=DocumentsContract.buildDocumentUriUsingTree(tree,archive.id);
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)continue;
                BsaCatalogReader.Catalog catalog=BsaCatalogReader.read(in);
                for(DdsArchiveFinder.Hit hit:DdsArchiveFinder.find(
                        catalog,wanted,archive.name)){
                    String key=DdsArchiveFinder.normalizeDds(hit.declaredPath);
                    if(found.containsKey(key))continue;
                    found.put(key,new SceneDdsFile(uri,
                        new BsaCatalogReader.Entry(
                            hit.entryPath,hit.storedBytes,hit.offset,hit.compressed),
                        catalog.flags));
                    wanted.remove(key);
                }
            }catch(java.io.IOException unsupported){
                // Missing/corrupt archives are not grounds to discard NIF meshes.
            }
        }
        long totalPixels=0;
        StringBuilder issues=new StringBuilder();
        for(String missing:wanted.keySet()){
            if(issues.length()<1000)issues.append("\n• DDS отсутствует в BSA: ")
                .append(missing);
        }
        for(java.util.Map.Entry<String,SceneDdsFile> match:found.entrySet()){
            if(closed||sequence!=multiArchiveScanSequence||
               verification!=nifVerificationSequence)
                throw new java.io.IOException("Загрузка DDS отменена");
            try(InputStream in=getContentResolver().openInputStream(match.getValue().uri)){
                if(in==null)continue;
                DdsBcDecoder.Image image=BsaDdsPixelReader.preview(
                    in,match.getValue().entry,match.getValue().flags);
                long pixels=(long)image.width*image.height;
                if(pixels<=0||totalPixels+pixels>4L*1024*1024){
                    if(issues.length()<1500)issues.append("\n• DDS ограничение памяти: ")
                        .append(match.getKey()).append(" · ")
                        .append(image.width).append("×").append(image.height);
                    continue;
                }
                totalPixels+=pixels;
                decoded.put(match.getKey(),image);
            }catch(java.io.IOException corrupt){
                if(issues.length()<1800)issues.append("\n• DDS не декодирована: ")
                    .append(match.getKey()).append(" · ")
                    .append(corrupt.getMessage());
                // Neutral atlas for this specific unavailable or unsupported DDS.
            }
        }
        return new LocalSceneDds(decoded,checked,found.size(),issues.toString());
    }

    private void scanAllModelArchives(Uri tree){
        scanAllModelArchives(tree,null);
    }
    private void scanAllModelArchives(Uri tree,String selectedDataDocumentId){
        final Tes4BaseFormResolver.Result baseRecords=lastBaseRecords;
        final String baseReport=lastNestedReport;
        if(baseRecords==null){update("Нет базовых записей ESM.");return;}
        final long sequence=++multiArchiveScanSequence;
        lastNifCandidates=java.util.Collections.emptyList();
        lastMultiBsaReport="";
        clearPlacedPreview();
        update(baseReport+"\n\nИщу модели в каталогах BSA папки Data...");
        new Thread(()->{
            try{
                String rootId=DocumentsContract.getTreeDocumentId(tree);
                String targetId=selectedDataDocumentId==null?rootId:selectedDataDocumentId;
                if(selectedDataDocumentId==null&&
                   !SkyrimAutoDiscovery.dataDirectory(nameDocument(tree,rootId))){
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
                String autoSummary=selectedDataDocumentId==null?"":(
                    "Автоматическая проверка Skyrim SE\n"+
                    "Skyrim.esm: найден · WRLD и базовые FormID: прочитаны\n"+
                    "BSA: "+search.archivesChecked()+" из "+archives.size()+
                    " · пути NIF: "+search.foundPaths()+" из "+search.neededPaths()+
                    "\nСодержимое NIF: следующий этап (до 8 моделей)\n\n");
                String full=autoSummary+baseReport+"\n\n"+
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
                        if(lastNifCandidates.isEmpty()){
                            update(full+"\n\nПроверка содержимого NIF: "+
                                "нечего проверять — совпадений NIF нет.");
                        }else{
                            update(full+"\n\nПроверка содержимого NIF "+
                                "запускается автоматически...");
                            verifyFoundModels();
                        }
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
        automaticAnalysisSequence++;
        inspectNested(uri,section,null,null,-1);
    }
    private void inspectNested(Uri uri,String section,Uri autoTree,
                               String autoDataId,long automaticSequence){
        multiArchiveScanSequence++;
        final long sampleSequence=multiArchiveScanSequence;
        nifVerificationSequence++;
        lastNifCandidates=java.util.Collections.emptyList();
        lastMultiBsaReport="";
        lastBaseRecords=null;
        lastNestedReport="";
        lastPlacements=java.util.Collections.emptyList();
        clearPlacedPreview();
        update("Читаю вложенные заголовки "+section+" · ограниченный образец...");
        new Thread(()->{
            try{
                String name=autoTree==null?getName(uri):"Skyrim.esm";
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
                    if(closed||sampleSequence!=multiArchiveScanSequence||
                       (autoTree!=null&&automaticSequence!=automaticAnalysisSequence))return;
                    lastBaseRecords=readyBases;
                    lastPlacements=new java.util.ArrayList<>(result.placements);
                    lastNestedReport=message;
                    if(autoTree!=null&&readyBases!=null){
                        update("Автоматическая проверка Skyrim SE\n"+
                               "Шаг 3/4: ищу модели во всех BSA...");
                        scanAllModelArchives(autoTree,autoDataId);
                    }else{
                        update(message+(autoTree!=null?
                            "\n\nАвтоматический поиск BSA не запущен: "+
                            "базовые FormID прочитать не удалось.":""));
                    }
                });
            }catch(Exception e){
                String message="Ошибка вложенных записей "+section+": "+e.getClass().getSimpleName()+": "+e.getMessage();
                runOnUiThread(()->{
                    if(!closed&&sampleSequence==multiArchiveScanSequence&&
                       (autoTree==null||automaticSequence==automaticAnalysisSequence))
                        update(message);
                });
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
    @Override protected void onDestroy(){closed=true;automaticAnalysisSequence++;multiArchiveScanSequence++;nifVerificationSequence++;super.onDestroy();}
}
