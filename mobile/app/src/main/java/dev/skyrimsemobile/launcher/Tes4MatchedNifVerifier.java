package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Read-only single-NIF payload verification for a NIF already located by MODL.
 * Reuses BsaEntryInspector's 8-MiB stored / 16-MiB decoded safeguards and
 * LZ4-frame handling. No extracted data is written to device storage.
 */
public final class Tes4MatchedNifVerifier {
    public static final int MAX_MODELS=3;
    private Tes4MatchedNifVerifier(){}

    public static final class Verification {
        public final String archive, path, format;
        public final int storedBytes,decodedBytes;
        public final boolean compressed;
        public final long offset;
        public final int totalBlocks,rootBlocks,meshCandidates,staticParts;
        public final String structureStatus,geometryStatus;
        Verification(String archive,BsaEntryInspector.Profile profile){
            BsaEntryInspector.Result details=profile.header;
            totalBlocks=profile.blocks==null?-1:profile.blocks.blockCount;
            rootBlocks=profile.blocks==null?-1:profile.blocks.rootCount;
            meshCandidates=profile.blocks==null?-1:profile.blocks.meshCandidateCount;
            staticParts=profile.staticParts==null?0:profile.staticParts.available;
            structureStatus=profile.blockIssue;
            geometryStatus=profile.geometryIssue;
            this.archive=archive;
            path=details.path;
            format=details.header.format;
            storedBytes=details.packedBytes;
            decodedBytes=details.decodedBytes;
            compressed=details.compressed;
            offset=details.recordOffset;
        }
        public String report(){
            StringBuilder s=new StringBuilder(String.format(Locale.ROOT,
                "• %s\n  BSA: %s · offset %d · %d → %d байт · %s\n  NIF-заголовок: РАСПОЗНАН (%s)",
                path,archive,offset,storedBytes,decodedBytes,
                compressed?"LZ4":"без сжатия",format));
            if(totalBlocks<0)
                s.append("\n  Таблица NIF: не разобрана (")
                 .append(structureStatus).append(")");
            else{
                s.append("\n  Таблица NIF: ").append(totalBlocks)
                 .append(" блоков, ").append(rootBlocks)
                 .append(" корней, ").append(meshCandidates)
                 .append(" потенциальных геометрических блоков");
                if(meshCandidates>0){
                    s.append("\n  Статическая геометрия BSTriShape: ")
                     .append(staticParts).append(" частей разобрано");
                    if(geometryStatus!=null)
                        s.append(" · ограничение: ").append(geometryStatus);
                }else s.append("\n  Статическая геометрия BSTriShape: не обнаружена");
            }
            s.append("\n  Структура NIF прочитана; факт работы Vulkan проверяется отдельно.");
            return s.toString();
        }
    }

    public static Verification verify(InputStream in,BsaCatalogReader.Entry entry,
                                      long archiveFlags,String archive)throws IOException{
        if(archive==null||archive.trim().isEmpty())throw new IOException("Имя архива не задано");
        BsaEntryInspector.Profile parsed=BsaEntryInspector.inspectProfile(in,entry,archiveFlags);
        return new Verification(archive,parsed);
    }
}
