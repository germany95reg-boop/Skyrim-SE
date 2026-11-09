package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Safe, read-only exact NIF-path comparison between TES4 base records and
 * the INDEXED part of ONE user-selected BSA 105 catalogue.
 * It does not load models, decompress assets, or resolve Skyrim load order.
 */
public final class Tes4BsaModelMatcher {
    private static final int MAX_BASES=32;
    private Tes4BsaModelMatcher(){}

    public static final class Item {
        public final Tes4BaseFormResolver.Base base;
        public final String canonicalPath;
        public final BsaCatalogReader.Entry match;
        Item(Tes4BaseFormResolver.Base base,String path,BsaCatalogReader.Entry match){
            this.base=base;this.canonicalPath=path;this.match=match;
        }
    }
    public static final class Result {
        public final String archive;
        public final boolean partialIndex;
        public final int indexedFiles,totalFiles,foundCount,missingCount,invalidCount,noModelCount;
        public final List<Item> items;
        private Result(String archive,BsaCatalogReader.Catalog catalogue,List<Item> items,
                       int found,int missing,int invalid,int noModel){
            this.archive=archive;this.partialIndex=catalogue.partialIndex;
            this.indexedFiles=catalogue.indexedCount;this.totalFiles=catalogue.fileCount;
            this.items=Collections.unmodifiableList(new ArrayList<>(items));
            this.foundCount=found;this.missingCount=missing;
            this.invalidCount=invalid;this.noModelCount=noModel;
        }
        @Override public String toString(){
            StringBuilder b=new StringBuilder("Проверка путей MODL в выбранном BSA (только индекс)\n");
            b.append("Архив: ").append(archive).append('\n');
            b.append("Каталог BSA: ").append(indexedFiles).append(" / ").append(totalFiles)
             .append(partialIndex?" файлов · индекс НЕПОЛНЫЙ\n":" файлов · индекс полный\n");
            b.append("Совпадений NIF: ").append(foundCount)
             .append(" · без совпадения: ").append(missingCount)
             .append(" · недопустимых путей: ").append(invalidCount)
             .append(" · без MODL: ").append(noModelCount).append('\n');
            for(Item item:items){
                b.append("• База 0x").append(hex(item.base.formId));
                if(!item.base.editorId.isEmpty())b.append(" · ").append(item.base.editorId);
                b.append(" · ").append(item.canonicalPath);
                if(item.match!=null){
                    b.append(" · НАЙДЕНО в каталоге · ")
                     .append(item.match.storedBytes).append(" байт")
                     .append(item.match.compressed?" · сжатый ресурс":"");
                }else if(partialIndex){
                    b.append(" · НЕ ПОДТВЕРЖДЕНО (проверена лишь часть каталога)");
                }else b.append(" · нет в ЭТОМ BSA");
                b.append('\n');
            }
            b.append("Проверен только один BSA; файлы в других архивах или Data не исключены.");
            b.append(" Совпадение имени — ещё не загрузка геометрии и не работа SkyrimSE.exe.");
            return b.toString();
        }
    }

    /** Convert Skyrim MODL relative paths to canonical BSA paths under meshes/. */
    public static String canonicalNif(String raw)throws IOException{
        if(raw==null)throw new IOException("Нет пути NIF");
        String p=raw.trim().replace('\\','/').toLowerCase(Locale.ROOT);
        while(p.startsWith("./"))p=p.substring(2);
        if(p.length()<5||p.length()>1024||!p.endsWith(".nif")||p.startsWith("/"))
            throw new IOException("Недопустимый путь NIF");
        if(p.indexOf(':')>=0||p.indexOf('\0')>=0)
            throw new IOException("Недопустимый путь NIF");
        String[] parts=p.split("/",-1);
        for(String part:parts){
            if(part.isEmpty()||part.equals(".")||part.equals(".."))
                throw new IOException("Небезопасный путь NIF");
            for(int i=0;i<part.length();i++)
                if(part.charAt(i)<32||part.charAt(i)==127)
                    throw new IOException("Управляющий символ в имени NIF");
        }
        return p.startsWith("meshes/")?p:"meshes/"+p;
    }

    public static Result match(Collection<Tes4BaseFormResolver.Base> bases,
                              BsaCatalogReader.Catalog catalogue,String archive)throws IOException{
        if(catalogue==null)throw new IOException("BSA ещё не прочитан");
        if(bases==null)throw new IOException("Базовые записи не выбраны");
        if(bases.size()>MAX_BASES)throw new IOException("Слишком много базовых записей");
        Map<String,BsaCatalogReader.Entry> nifIndex=new LinkedHashMap<>();
        for(BsaCatalogReader.Entry entry:catalogue.entries){
            if(!entry.nif())continue;
            try{
                String canonical=canonicalNif(entry.path);
                if(!nifIndex.containsKey(canonical))nifIndex.put(canonical,entry);
            }catch(IOException ignored){
                // Invalid archive names are never converted into trusted matches.
            }
        }
        List<Item> items=new ArrayList<>();
        int found=0,missing=0,invalid=0,noModel=0;
        for(Tes4BaseFormResolver.Base base:bases){
            if(base==null||base.modelPath==null||base.modelPath.isEmpty()){
                noModel++;continue;
            }
            final String canonical;
            try{canonical=canonicalNif(base.modelPath);}
            catch(IOException ignored){invalid++;continue;}
            BsaCatalogReader.Entry entry=nifIndex.get(canonical);
            if(entry!=null)found++;else missing++;
            items.add(new Item(base,canonical,entry));
        }
        return new Result(archive==null?"Выбранный BSA":archive,catalogue,
                          items,found,missing,invalid,noModel);
    }
    private static String hex(long formId){
        return String.format(Locale.ROOT,"%08X",formId);
    }
}
