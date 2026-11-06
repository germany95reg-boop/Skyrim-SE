package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only per-BSTriShape material DDS availability, based on exact validated links.
 * Keeps texture association by NIF block index even when several parts share
 * a texture set or a material cannot be resolved.
 */
public final class NifPartDdsAvailability {
    private NifPartDdsAvailability(){}

    /** Only validated, referenced diffuse/normal paths; ignores unrelated texture sets.
     * Invalid paths are excluded, with a reason printed in the per-part report.
     */
    public static List<String> targets(NifMaterialLinkInspector.Result links)throws IOException{
        if(links==null||links.links.isEmpty())throw new IOException("Нет геометрических частей NIF");
        List<String> paths=new ArrayList<>();
        for(NifMaterialLinkInspector.Link link:links.links){
            if(!link.resolved())continue;
            for(String raw:new String[]{link.diffuse,link.normal}){
                if(raw==null||raw.isEmpty())continue;
                try{DdsArchiveFinder.normalizeDds(raw);paths.add(raw);}
                catch(IOException ignored){/* per-part report labels invalid path */}
            }
        }
        Map<String,String> all=DdsArchiveFinder.targets(paths);
        return Collections.unmodifiableList(new ArrayList<>(all.values()));
    }

    public static String report(NifMaterialLinkInspector.Result refs,
                                List<DdsArchiveFinder.Hit> matches,boolean indexComplete){
        StringBuilder out=new StringBuilder("DDS по частям NIF · v2.11 (проверка BSA)");
        Set<String> found=new HashSet<>();
        if(matches!=null)for(DdsArchiveFinder.Hit hit:matches){
            if(hit==null)continue;
            try{found.add(DdsArchiveFinder.normalizeDds(hit.declaredPath));}
            catch(IOException ignored){}
        }
        int available=0,requested=0,unresolved=0;
        for(NifMaterialLinkInspector.Link ref:refs.links){
            out.append("\n\nBSTriShape #").append(ref.shape);
            if(ref.shader>=0)out.append(" → shader #").append(ref.shader);
            if(ref.textureSet>=0)out.append(" → texture set #").append(ref.textureSet);
            if(!ref.resolved()){
                out.append("\n  Ссылки не разрешены: ").append(ref.problem);
                unresolved++;continue;
            }
            String[] paths={ref.diffuse,ref.normal};
            for(int i=0;i<2;i++){
                String path=paths[i];
                out.append("\n  DDS [").append(i).append("] ");
                if(path==null||path.isEmpty()){out.append("не задана");continue;}
                requested++;
                try{
                    String key=DdsArchiveFinder.normalizeDds(path);
                    if(found.contains(key)){available++;out.append("НАЙДЕНО");}
                    else out.append(indexComplete?"не найдено в проверенных индексах":
                                                   "не подтверждено (поиск неполный)");
                }catch(IOException ex){out.append("неподдерживаемый путь");}
                out.append(": ").append(path);
            }
        }
        out.append("\n\nИтого: ").append(available).append(" найдено из ")
           .append(requested).append(" объявленных DDS; частей с неподдерживаемыми связями: ")
           .append(unresolved).append('.');
        if(!indexComplete)out.append(
            "\nВНИМАНИЕ: не все архивы BSA прочитаны полностью; отсутствие DDS не подтверждено.");
        out.append("\nПроверены только имена в каталогах BSA. "+
            "DDS не распакованы и не применены к составной Vulkan-модели. "+
            "Файлы Skyrim не изменены.");
        return out.toString();
    }
}
