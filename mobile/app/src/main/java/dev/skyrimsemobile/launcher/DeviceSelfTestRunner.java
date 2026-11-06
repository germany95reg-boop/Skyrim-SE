package dev.skyrimsemobile.launcher;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.content.pm.PackageInfo;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * One-tap, on-device read-only test using ONLY previously approved SAF grants.
 * Bounded BSA/NIF/DDS operations run off the main thread. No archive writes,
 * network calls, asset uploads, shell commands or Windows/Android emulation.
 */
final class DeviceSelfTestRunner {
    interface Progress { void update(String message); }
    private static final int MAX_DATA_CHILDREN=4096;
    private static final int MAX_ARCHIVES=48;
    private static final int MAX_UNIQUE_DIFFUSE=12;
    private static final long MAX_SOURCE_PIXELS=8L*1024*1024;
    private final Context context;
    private final ContentResolver resolver;
    private final DeviceSelfTestReport report;
    private final Progress progress;
    private volatile boolean cancelled;

    DeviceSelfTestRunner(Context context,DeviceSelfTestReport report,Progress progress){
        this.context=context.getApplicationContext();
        this.resolver=context.getContentResolver();
        this.report=report;
        this.progress=progress;
    }
    void cancel(){cancelled=true;}
    private void checkCancelled()throws IOException{
        if(cancelled||Thread.currentThread().isInterrupted())
            throw new IOException("Проверка остановлена");
    }
    private void step(String text){progress.update(text);}
    private InputStream open(Uri uri)throws IOException{
        checkCancelled();
        InputStream in=resolver.openInputStream(uri);
        if(in==null)throw new IOException("Android не предоставил поток BSA");
        return in;
    }
    private static String shortError(Throwable t){
        String message=t.getMessage();
        if(message==null||message.isEmpty())return t.getClass().getSimpleName();
        return message.length()>160?message.substring(0,160)+"…":message;
    }

    void run(){
        checkPackageAndDevice();
        checkVulkanProbe();
        checkGameAssets();
    }
    private void checkPackageAndDevice(){
        step("1/5 · Проверяю установленный APK и процессор…");
        try{
            PackageInfo packageInfo=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            boolean abi=false;
            for(String name:Build.SUPPORTED_ABIS)
                if("arm64-v8a".equals(name))abi=true;
            if(abi)report.pass("ARM64",Build.MANUFACTURER+" "+Build.MODEL+
                " · Android "+Build.VERSION.RELEASE);
            else report.fail("ARM64","Устройство не объявляет arm64-v8a");
            String version=packageInfo.versionName==null?"неизвестно":packageInfo.versionName;
            if("dev.skyrimsemobile.launcher".equals(context.getPackageName()))
                report.pass("Установленный APK","Skyrim SE Mobile "+version+
                    " · код версии "+packageInfo.versionCode);
            else report.fail("Пакет APK","Неожиданный Android package name");
        }catch(Exception ex){report.fail("APK / устройство",shortError(ex));}
    }
    private void checkVulkanProbe(){
        step("2/5 · Определяю настоящий Vulkan-драйвер устройства…");
        try{
            RuntimeDiagnostics diagnostic=new RuntimeDiagnostics();
            CountDownLatch finished=new CountDownLatch(1);
            diagnostic.run(finished::countDown);
            if(!finished.await(12,TimeUnit.SECONDS)){
                report.warn("Vulkan-драйвер","Проверка возможностей GPU превысила 12 секунд");
                return;
            }
            JSONObject info=diagnostic.snapshot();
            boolean available=info.optBoolean("vulkanLoader",false)&&
                    info.optInt("gpuCount",0)>0;
            String description=info.optString("gpuName","")+ " · Vulkan "+
                    info.optString("apiVersion","нет данных");
            if(available)report.pass("Vulkan-драйвер",description);
            else report.warn("Vulkan-драйвер",info.optString("message","GPU недоступен"));
        }catch(InterruptedException ex){
            Thread.currentThread().interrupt();
            report.warn("Vulkan-драйвер","Диагностика прервана");
        }catch(Exception ex){report.warn("Vulkan-драйвер",shortError(ex));}
    }

    private static final class Sample {
        final BsaCatalogReader.Entry nif;
        final NifGeometryDecoder.Parts parts;
        final NifSceneComposer.Combined scene;
        final NifMaterialLinkInspector.Result links;
        Sample(BsaCatalogReader.Entry n,NifGeometryDecoder.Parts p,
               NifSceneComposer.Combined s,NifMaterialLinkInspector.Result l){
            nif=n;parts=p;scene=s;links=l;
        }
    }
    private static final class Archive {
        final Uri uri;
        final String name;
        Archive(Uri u,String n){uri=u;name=n;}
    }
    private static final class Location {
        final Uri uri;
        final BsaCatalogReader.Entry entry;
        final long flags;
        Location(Uri u,BsaCatalogReader.Entry e,long f){uri=u;entry=e;flags=f;}
    }

    private void checkGameAssets(){
        step("3/5 · Ищу сохранённые архивы и проверяю BSA 105…");
        BsaSessionStore session=new BsaSessionStore(context);
        Uri archive=session.lastArchive();
        if(archive==null){
            report.skip("Доступ к BSA","Архив ещё не выбран или разрешение отозвано. "+
                "Открой просмотрщик BSA и выбери архив один раз.");
            report.skip("NIF / DDS","Требуется сохранённый доступ к BSA");
            return;
        }
        BsaCatalogReader.Catalog catalog;
        try(InputStream in=open(archive)){
            catalog=BsaCatalogReader.read(in);
            String details=session.archiveDisplayName()+" · "+catalog.folderCount+
                " папок · "+catalog.fileCount+" файлов · "+catalog.nifCount+" NIF";
            if(catalog.partialIndex)report.warn("BSA 105",details+
                    " · частичный индекс "+catalog.indexedCount);
            else report.pass("BSA 105",details);
        }catch(Exception ex){
            report.fail("BSA 105",shortError(ex));
            return;
        }
        checkNifAndDds(session,archive,catalog);
    }

    private Sample chooseSample(BsaSessionStore session,Uri archive,
                                BsaCatalogReader.Catalog catalog)throws IOException{
        List<BsaCatalogReader.Entry> options=new ArrayList<>();
        String saved=session.lastModel(archive);
        // Previously selected NIF is always preferred, then two known-safe
        // example names, then a bounded number of other indexed static NIFs.
        if(!saved.isEmpty())for(BsaCatalogReader.Entry e:catalog.entries)
            if(e.nif()&&e.path.equals(saved)){options.add(e);break;}
        for(String target:new String[]{"fishinggear_cluster01.nif","lureargonian01.nif"}){
            for(BsaCatalogReader.Entry e:catalog.entries){
                if(!e.nif()||options.contains(e))continue;
                if(e.path.toLowerCase(Locale.ROOT).endsWith("/"+target)){options.add(e);break;}
            }
        }
        for(BsaCatalogReader.Entry e:catalog.entries){
            if(options.size()>=8)break;
            if(e.nif()&&!options.contains(e))options.add(e);
        }
        if(options.isEmpty())throw new IOException("Архив не содержит NIF");
        IOException last=null;
        for(BsaCatalogReader.Entry entry:options){
            checkCancelled();
            try{
                NifGeometryDecoder.Parts parts;
                NifMaterialLinkInspector.Result links;
                try(InputStream in=open(archive)){
                    parts=BsaEntryInspector.inspectNifParts(in,entry,catalog.flags);
                }
                if(parts.available==0)throw new IOException("Нет поддерживаемых BSTriShape");
                try(InputStream in=open(archive)){
                    links=BsaEntryInspector.readPartMaterialLinks(in,entry,catalog.flags);
                }
                NifSceneComposer.Combined scene=NifSceneComposer.combine(parts);
                return new Sample(entry,parts,scene,links);
            }catch(IOException ex){last=ex;}
        }
        throw new IOException("Проверено до "+options.size()+
            " NIF, поддерживаемая составная модель не найдена"+
            (last==null?"":": "+shortError(last)));
    }

    private List<Archive> listDataBsa(Uri tree)throws IOException{
        String treeId=DocumentsContract.getTreeDocumentId(tree);
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,treeId);
        String[] columns={
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        List<Archive> found=new ArrayList<>();
        try(Cursor cursor=resolver.query(children,columns,null,null,null)){
            if(cursor==null)throw new IOException("Android не предоставил список Data");
            int id=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int name=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mime=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int visited=0;
            while(cursor.moveToNext()){
                checkCancelled();
                if(++visited>MAX_DATA_CHILDREN)
                    throw new IOException("Data содержит более 4096 объектов");
                String display=cursor.getString(name),docId=cursor.getString(id);
                if(docId==null||display==null||
                   DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(mime))||
                   !display.toLowerCase(Locale.ROOT).endsWith(".bsa"))continue;
                if(found.size()>=MAX_ARCHIVES)
                    throw new IOException("Data содержит более 48 BSA");
                found.add(new Archive(DocumentsContract.buildDocumentUriUsingTree(tree,docId),display));
            }
        }catch(SecurityException ex){throw new IOException("Нет разрешения на чтение Data",ex);}
        Collections.sort(found,(a,b)->a.name.compareToIgnoreCase(b.name));
        return found;
    }

    private Map<String,Location> findTextures(Uri archive,BsaCatalogReader.Catalog source,
                                               Uri dataTree,Map<String,String> targets)
            throws IOException {
        Map<String,Location> found=new LinkedHashMap<>();
        searchCatalog(archive,source,targets,found);
        if(dataTree==null||found.size()==targets.size())return found;
        List<Archive> docs=listDataBsa(dataTree);
        int scanned=0,errors=0,partial=0;
        for(Archive doc:docs){
            checkCancelled();
            if(found.size()>=targets.size())break;
            try(InputStream in=open(doc.uri)){
                BsaCatalogReader.Catalog cat=BsaCatalogReader.read(in);
                searchCatalog(doc.uri,cat,targets,found);
                if(cat.partialIndex)partial++;
                scanned++;
            }catch(IOException | SecurityException ex){errors++;}
        }
        report.pass("Поиск Data BSA","Индексы "+scanned+" BSA · совпадения "+
            found.size()+"/"+targets.size());
        if(errors>0||partial>0)
            report.warn("Индексы Data","Ошибок чтения "+errors+
                " · частичных индексов "+partial+
                " (отсутствие DDS не всегда подтверждено)");
        return found;
    }
    private void searchCatalog(Uri uri,BsaCatalogReader.Catalog catalog,
                               Map<String,String> targets,Map<String,Location> found){
        for(BsaCatalogReader.Entry entry:catalog.entries){
            String key;
            try{key=DdsArchiveFinder.normalizeDds(entry.path);}
            catch(IOException ignored){continue;}
            if(targets.containsKey(key)&&!found.containsKey(key))
                found.put(key,new Location(uri,entry,catalog.flags));
        }
    }

    private void checkNifAndDds(BsaSessionStore session,Uri archive,
                                BsaCatalogReader.Catalog catalog){
        step("4/5 · Проверяю геометрию NIF, материал и составную модель…");
        Sample sample;
        try{
            sample=chooseSample(session,archive,catalog);
            report.pass("NIF / геометрия",sample.nif.path+" · "+
                sample.parts.available+" частей · "+
                sample.scene.triangleCount+" треугольников");
            if(sample.parts.hierarchyNote.contains("ПРЕДУПРЕЖДЕНИЕ")||
               (sample.parts.linkedParentShapes>0&&
                sample.parts.linkedParentShapes<sample.parts.available)){
                report.warn("Иерархия NIF",sample.parts.hierarchyNote);
            }else if(sample.parts.linkedParentShapes>0){
                report.pass("NiNode/BSFadeNode",
                    sample.parts.linkedParentShapes+" BSTriShape получили родительские "+
                    "повороты, масштабы и смещения");
            }else{
                report.warn("Иерархия NIF",sample.parts.hierarchyNote);
            }
            int resolved=0;
            for(NifMaterialLinkInspector.Link link:sample.links.links)
                if(link.resolved())resolved++;
            if(resolved>0)report.pass("Связи материалов",resolved+
                " BSTriShape с проверенной ссылкой shader → texture set");
            else report.warn("Связи материалов","Нет разрешённых шейдерных связей");
        }catch(Exception ex){
            report.warn("NIF / геометрия",shortError(ex));
            return;
        }
        Map<Integer,DdsBcDecoder.Image> perShape=new HashMap<>();
        Map<Integer,DdsBcDecoder.Image> perNormal=new HashMap<>();
        boolean prepared=false;
        try {
            step("5/5 · Ищу DDS, декодирую и собираю безопасный Vulkan-атлас…");
            List<String> wanted=NifPartDdsAvailability.targets(sample.links);
            Map<String,String> targets=DdsArchiveFinder.targets(wanted);
            Uri dataTree=session.lastDataTree();
            if(dataTree==null)report.warn("Папка Data","Доступ не сохранён; ищу DDS только в выбранном BSA");
            Map<String,Location> found=findTextures(archive,catalog,dataTree,targets);
            if(found.size()==targets.size())report.pass("Поиск DDS",
                "Найдены все "+found.size()+" объявленных текстуры");
            else report.warn("Поиск DDS","Найдено "+found.size()+" из "+targets.size()+
                " (недостающие материалы будут показаны серым)");

            Map<String,DdsBcDecoder.Image> cache=new HashMap<>();
            long decodedPixels=0;
            int failures=0;
            for(NifGeometryDecoder.Part part:sample.parts.items){
                checkCancelled();
                if(part.mesh==null||part.mesh.surfaceUVs.length!=part.mesh.triangles*6)continue;
                try{
                    NifMaterialLinkInspector.Link link=
                        NifPartTextureSelection.require(sample.links,part.blockIndex);
                    String key=DdsArchiveFinder.normalizeDds(link.diffuse);
                    Location located=found.get(key);
                    if(located==null)continue;
                    DdsBcDecoder.Image image=cache.get(key);
                    if(image==null){
                        if(cache.size()>=MAX_UNIQUE_DIFFUSE)
                            throw new IOException("Лимит уникальных DDS ("+MAX_UNIQUE_DIFFUSE+")");
                        try(InputStream in=open(located.uri)){
                            image=BsaDdsPixelReader.preview(in,located.entry,located.flags);
                        }
                        long pixels=(long)image.width*image.height;
                        if(decodedPixels+pixels>MAX_SOURCE_PIXELS)
                            throw new IOException("Превышен предел 8 млн декодированных пикселей");
                        cache.put(key,image);
                        decodedPixels+=pixels;
                    }
                    perShape.put(part.blockIndex,image);
                    // Normal DDS is optional; never reject a valid diffuse
                    // simply because a normal mip is missing or unsupported.
                    if(link.normal!=null&&!link.normal.isEmpty()){
                        try{
                            String normalKey=DdsArchiveFinder.normalizeDds(link.normal);
                            Location normalLocation=found.get(normalKey);
                            if(normalLocation!=null){
                                DdsBcDecoder.Image normalImage=cache.get(normalKey);
                                if(normalImage==null){
                                    if(cache.size()>=MAX_UNIQUE_DIFFUSE)
                                        throw new IOException("Лимит DDS cache");
                                    try(InputStream in=open(normalLocation.uri)){
                                        normalImage=BsaDdsPixelReader.preview(in,
                                            normalLocation.entry,normalLocation.flags);
                                    }
                                    long nPixels=(long)normalImage.width*normalImage.height;
                                    if(decodedPixels+nPixels>MAX_SOURCE_PIXELS)
                                        throw new IOException("Превышен предел декодированных пикселей");
                                    cache.put(normalKey,normalImage);
                                    decodedPixels+=nPixels;
                                }
                                perNormal.put(part.blockIndex,normalImage);
                            }
                        }catch(Exception badNormal){
                            report.warn("Normal DDS #"+part.blockIndex,
                                "Не удалось декодировать normal mip: "+shortError(badNormal));
                        }
                    }
                }catch(Exception partError){failures++;}
            }
            if(!perShape.isEmpty()){
                report.pass("Декодирование DDS","Форматы BCn/RGBA · успешно "+
                    cache.size()+" уникальных DDS (diffuse и normal) · "+
                    perShape.size()+" частей NIF");
                if(failures>0)report.warn("Материалы","Пропущено частей: "+failures+
                    " (невалидная или неподдерживаемая DDS)");
                try{
                    NifCompositeDdsAtlas.Result atlas=perNormal.isEmpty()?
                        NifCompositeDdsAtlas.build(sample.parts,sample.scene,perShape):
                        NifCompositeDdsAtlas.buildPaired(
                            sample.parts,sample.scene,perShape,perNormal);
                    report.pass("Vulkan DDS-атлас",atlas.width+"×"+atlas.height+
                        " · слотов (diffuse/normal) "+atlas.textureCount+" · серых частей "+atlas.neutralParts);
                    if(!perNormal.isEmpty()){
                        try {
                            NifCompositeNormalAtlas.Result combined=
                                NifCompositeNormalAtlas.build(sample.parts,atlas,perNormal);
                            NifGeometryTransfer.offerAtlasNormal(sample.scene.lines,
                                sample.scene.triangles,atlas,combined,"NIF: "+sample.nif.path,
                                "АВТОТЕСТ · составная NIF с diffuse + normal DDS");
                            report.pass("Normal DDS-атлас",
                                combined.normalParts+" частей · "+
                                combined.uniqueNormals+" уникальных normal DDS · "+
                                combined.neutralNormalParts+" с нейтральными нормалями");
                        }catch(Exception normalError){
                            report.warn("Normal DDS-атлас",
                                shortError(normalError)+" · сохранён прежний diffuse atlas");
                            NifGeometryTransfer.offerAtlas(sample.scene.lines,
                                sample.scene.triangles,atlas,"NIF: "+sample.nif.path,
                                "АВТОТЕСТ · составная NIF с diffuse DDS");
                        }
                    }else{
                        report.warn("Normal DDS-атлас",
                            "Normal DDS для выбранных частей не декодированы, рельефный шейдер не проверялся");
                        NifGeometryTransfer.offerAtlas(sample.scene.lines,
                            sample.scene.triangles,atlas,"NIF: "+sample.nif.path,
                            "АВТОТЕСТ · составная NIF с diffuse DDS");
                    }
                    prepared=true;
                }catch(Exception ex){
                    report.warn("Vulkan DDS-атлас",shortError(ex)+
                        "; геометрия сохранена для GPU-проверки");
                }
            }else report.warn("Декодирование DDS",
                "Для выбранной NIF не удалось декодировать подходящую diffuse DDS");
        }catch(Exception ex){
            report.warn("DDS / Data",shortError(ex));
        }
        if(!prepared){
            // Even without textures, render the actual normalized NIF geometry.
            NifGeometryTransfer.offer(sample.scene.lines,sample.scene.triangles,
                "NIF: "+sample.nif.path,"АВТОТЕСТ · составная геометрия");
            report.pass("Вход GPU-проверки","Подготовлена составная NIF без текстур");
        }else report.pass("Вход GPU-проверки",
            "Подготовлена составная NIF с несколькими diffuse DDS");
    }
}
