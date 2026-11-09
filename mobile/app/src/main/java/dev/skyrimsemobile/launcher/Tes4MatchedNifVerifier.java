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
        Verification(String archive,BsaEntryInspector.Result details){
            this.archive=archive;
            path=details.path;
            format=details.header.format;
            storedBytes=details.packedBytes;
            decodedBytes=details.decodedBytes;
            compressed=details.compressed;
            offset=details.recordOffset;
        }
        public String report(){
            return String.format(Locale.ROOT,
                "• %s\n  BSA: %s · offset %d · %d → %d байт · %s\n  NIF-заголовок: РАСПОЗНАН (%s)",
                path,archive,offset,storedBytes,decodedBytes,
                compressed?"LZ4":"без сжатия",format);
        }
    }

    public static Verification verify(InputStream in,BsaCatalogReader.Entry entry,
                                      long archiveFlags,String archive)throws IOException{
        if(archive==null||archive.trim().isEmpty())throw new IOException("Имя архива не задано");
        BsaEntryInspector.Result parsed=BsaEntryInspector.inspect(in,entry,archiveFlags);
        if(!parsed.header.recognized||!"NIF".equals(parsed.header.format))
            throw new IOException("Данные BSA не содержат корректный заголовок NIF");
        return new Verification(archive,parsed);
    }
}
