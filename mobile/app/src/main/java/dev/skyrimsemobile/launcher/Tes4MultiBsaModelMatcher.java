package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read-only search for previously resolved TES4 MODL paths across a bounded
 * number of user-authorized BSA catalogues. Only metadata is scanned.
 */
public final class Tes4MultiBsaModelMatcher {
    public static final int MAX_ARCHIVES=48;
    private static final int MAX_BASES=32;
    private Tes4MultiBsaModelMatcher(){}

    public static final class Hit {
        public final String archive, entryPath;
        public final long storedBytes;
        public final boolean compressed;
        Hit(String archive,BsaCatalogReader.Entry entry){
            this.archive=archive;
            this.entryPath=entry.path;
            storedBytes=entry.storedBytes;
            compressed=entry.compressed;
        }
    }

    public static final class Search {
        private final List<Tes4BaseFormResolver.Base> bases;
        private final Map<Long,String> canonical;
        private final Set<String> wanted;
        private final Map<String,Hit> hits=new LinkedHashMap<>();
        private final List<String> failures=new ArrayList<>();
        public final int missingModl,invalidModl;
        private int attempted,successful,partialIndexes;
        private boolean capped;
        private Search(List<Tes4BaseFormResolver.Base> bases,Map<Long,String> canonical,
                       Set<String> wanted,int without,int invalid){
            this.bases=bases;this.canonical=canonical;this.wanted=wanted;
            missingModl=without;invalidModl=invalid;
        }

        public boolean allFound(){return hits.keySet().containsAll(wanted);}
        public int neededPaths(){return wanted.size();}
        public int foundPaths(){return hits.size();}
        public int archivesChecked(){return attempted;}
        public void markCapped(){capped=true;}

        public void accept(String archive,BsaCatalogReader.Catalog catalog)throws IOException {
            if(catalog==null)throw new IOException("Нет каталога BSA");
            if(attempted>=MAX_ARCHIVES)throw new IOException("Превышен лимит BSA");
            attempted++;
            successful++;
            if(catalog.partialIndex)partialIndexes++;
            if(wanted.isEmpty()||allFound())return;
            for(BsaCatalogReader.Entry entry:catalog.entries){
                if(!entry.nif())continue;
                String path;
                try{path=Tes4BsaModelMatcher.canonicalNif(entry.path);}
                catch(IOException ignored){continue;}
                if(wanted.contains(path)&&!hits.containsKey(path))
                    hits.put(path,new Hit(archive,entry));
                if(allFound())break;
            }
        }

        public void failed(String archive,Exception exception){
            if(attempted>=MAX_ARCHIVES)return;
            attempted++;
            if(failures.size()<8)
                failures.add(archive+" · "+exception.getClass().getSimpleName()+
                    ": "+exception.getMessage());
        }

        public String report(int totalArchives){
            StringBuilder b=new StringBuilder("Поиск MODL во всех доступных BSA папки Data (индексный)\n");
            b.append("Проверено BSA: ").append(attempted)
             .append(" / ").append(totalArchives)
             .append(" · прочитано каталогов: ").append(successful)
             .append(" · неполных каталогов: ").append(partialIndexes)
             .append(" · ошибок: ").append(attempted-successful).append('\n');
            b.append("Различных путей NIF найдено: ").append(hits.size())
             .append(" из ").append(wanted.size()).append('\n');
            if(capped)b.append("Достигнут лимит 48 BSA: возможны непроверенные архивы.\n");
            for(Tes4BaseFormResolver.Base base:bases){
                if(base==null)continue;
                String normalized=canonical.get(base.formId);
                if(normalized==null)continue;
                b.append("• 0x").append(hex(base.formId)).append(" · ").append(normalized);
                Hit hit=hits.get(normalized);
                if(hit==null){
                    if(partialIndexes>0||attempted<totalArchives||attempted>successful)
                        b.append(" · НЕ ПОДТВЕРЖДЕНО (не все каталоги доступны/полны)");
                    else b.append(" · нет в проверенных BSA");
                }else{
                    b.append(" · НАЙДЕНО в ").append(hit.archive)
                     .append(" · ").append(hit.storedBytes).append(" байт");
                    if(hit.compressed)b.append(" · сжатый ресурс");
                }
                b.append('\n');
            }
            if(missingModl>0||invalidModl>0)
                b.append("Без MODL: ").append(missingModl)
                 .append(" · некорректных MODL: ").append(invalidModl).append('\n');
            for(String failure:failures)b.append("Ошибка: ").append(failure).append('\n');
            b.append("Проверены только имена NIF в выбранных BSA, без распаковки.");
            b.append(" Loose Data, порядок загрузки модов и отображение моделей не реализованы.");
            return b.toString();
        }
    }

    public static Search begin(Collection<Tes4BaseFormResolver.Base> raw)throws IOException {
        if(raw==null||raw.size()>MAX_BASES)
            throw new IOException("Неверное количество базовых записей");
        List<Tes4BaseFormResolver.Base> bases=new ArrayList<>(raw);
        Map<Long,String> paths=new LinkedHashMap<>();
        Set<String> wanted=new LinkedHashSet<>();
        int missing=0,invalid=0;
        for(Tes4BaseFormResolver.Base base:bases){
            if(base==null||base.modelPath==null||base.modelPath.isEmpty()){
                missing++;continue;
            }
            try{
                String canonical=Tes4BsaModelMatcher.canonicalNif(base.modelPath);
                paths.put(base.formId,canonical);
                wanted.add(canonical);
            }catch(IOException e){invalid++;}
        }
        return new Search(Collections.unmodifiableList(bases),paths,wanted,missing,invalid);
    }
    private static String hex(long id){return String.format(Locale.ROOT,"%08X",id);}
}
