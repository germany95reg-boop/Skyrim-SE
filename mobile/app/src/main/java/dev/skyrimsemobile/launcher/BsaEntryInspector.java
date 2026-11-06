package dev.skyrimsemobile.launcher;

import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.xxhash.XXHashFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * v0.9: read-only, single-entry BSA 105 NIF header probe.
 * Never writes files, changes game data, or attempts to parse geometry.
 * Important: even for an uncompressed file we read at most 8 MiB into RAM.
 */
public final class BsaEntryInspector {
    private static final int MAX_PACKED = 8 * 1024 * 1024;
    private static final int MAX_DECODED = 16 * 1024 * 1024;
    private BsaEntryInspector() {}

    public static final class Result {
        public final String path;
        public final long recordOffset;
        public final int packedBytes;
        public final int decodedBytes;
        public final boolean compressed;
        public final int embeddedNameBytes;
        public final ResourceHeaderInspector.Result header;

        Result(String path, long offset, int packedBytes, int decodedBytes,
               boolean compressed, int embeddedNameBytes, ResourceHeaderInspector.Result header) {
            this.path = path;
            this.recordOffset = offset;
            this.packedBytes = packedBytes;
            this.decodedBytes = decodedBytes;
            this.compressed = compressed;
            this.embeddedNameBytes = embeddedNameBytes;
            this.header = header;
        }
        public String report() {
            return "Ресурс: " + path + "\nBSA смещение: 0x" + Long.toHexString(recordOffset) +
                "\nРазмер записи: " + packedBytes + " байт" +
                "\nРазмер данных: " + decodedBytes + " байт" +
                "\nСжатие: " + (compressed ? "LZ4 Frame (BSA 105)" : "нет") +
                (embeddedNameBytes > 0 ? "\nПрефикс имени BSA: " + embeddedNameBytes + " байт" : "") +
                "\nРаспознано: " + header.recognized +
                "\n" + header.toString() +
                "\nПрочитана только выбранная запись; остальные файлы BSA не извлекались." +
                "\nДанные находятся только в памяти приложения. Исходный BSA не изменён.";
        }
    }

    private static final class Decoded {
        final byte[] bytes;
        final int storedLength,embeddedNameBytes;
        Decoded(byte[] bytes,int storedLength,int embeddedNameBytes) {
            this.bytes=bytes;this.storedLength=storedLength;this.embeddedNameBytes=embeddedNameBytes;
        }
    }

    public static Result inspect(InputStream bsa, BsaCatalogReader.Entry entry, long archiveFlags)
            throws IOException {
        Decoded d=readDecoded(bsa,entry,archiveFlags);
        ResourceHeaderInspector.Result header=ResourceHeaderInspector.inspect(
                new ByteArrayInputStream(d.bytes),entry.path);
        return new Result(entry.path,entry.offset,d.storedLength,d.bytes.length,
                entry.compressed,d.embeddedNameBytes,header);
    }

    /** Parse the bounded NIF block table without persisting, exporting or rendering content. */
    public static String inspectBlocks(InputStream bsa, BsaCatalogReader.Entry entry, long archiveFlags)
            throws IOException {
        Decoded d=readDecoded(bsa,entry,archiveFlags);
        NifBlockInspector.Result details=NifBlockInspector.inspect(d.bytes);
        return "Ресурс: "+entry.path+"\nДанные: "+d.bytes.length+" байт\n"+details.report();
    }

    /** v1.1: decode one bounded static SSE BSTriShape into a safe in-memory wireframe. */
    public static NifGeometryDecoder.Mesh decodeNifGeometry(InputStream bsa,
            BsaCatalogReader.Entry entry,long archiveFlags) throws IOException {
        Decoded d=readDecoded(bsa,entry,archiveFlags);
        return NifGeometryDecoder.decode(d.bytes);
    }

    /** v2.6: inspect individual static BSTriShape components in one bounded NIF.
     * Kept separate from the existing single-part textured Vulkan path.
     */
    public static NifGeometryDecoder.Parts inspectNifParts(InputStream bsa,
            BsaCatalogReader.Entry entry,long archiveFlags)throws IOException{
        Decoded d=readDecoded(bsa,entry,archiveFlags);
        return NifGeometryDecoder.scan(d.bytes);
    }

    /** v2.10: inspect validated per-BSTriShape material links in a single bounded NIF. */
    public static NifMaterialLinkInspector.Result readPartMaterialLinks(InputStream bsa,
            BsaCatalogReader.Entry entry,long archiveFlags)throws IOException{
        Decoded d=readDecoded(bsa,entry,archiveFlags);
        return NifMaterialLinkInspector.inspect(d.bytes);
    }

    /** v1.5: bounded read-only material and DDS texture path list of one selected NIF. */
    public static String inspectMaterials(InputStream bsa,BsaCatalogReader.Entry entry,long archiveFlags)
            throws IOException {
        Decoded d=readDecoded(bsa,entry,archiveFlags);
        return "Ресурс: "+entry.path+"\nДанные: "+d.bytes.length+" байт\n"+
            NifMaterialInspector.inspect(d.bytes).report();
    }

    /** v1.6: get the trusted, bounded list of BSShaderTextureSet slots from one NIF. */
    public static NifMaterialInspector.Result readMaterialData(InputStream bsa,
            BsaCatalogReader.Entry entry,long archiveFlags) throws IOException {
        Decoded d=readDecoded(bsa,entry,archiveFlags);
        return NifMaterialInspector.inspect(d.bytes);
    }

    private static Decoded readDecoded(InputStream bsa,BsaCatalogReader.Entry entry,long archiveFlags)
            throws IOException {
        if (bsa == null || entry == null) throw new IOException("Архив или запись недоступны");
        if (!entry.nif()) throw new IOException("Разрешено проверять только записи NIF");
        if (entry.offset < 36L) throw new IOException("Некорректное смещение записи BSA");
        if (entry.storedBytes < 1 || entry.storedBytes > MAX_PACKED)
            throw new IOException("Запись превышает безопасный предел 8 МиБ или имеет нулевой размер");
        if ((archiveFlags & 0x40L) != 0) throw new IOException("Неподдерживаемый порядок байтов BSA");
        // ContentResolver.openInputStream may not be seekable. Sequential skip works with SAF providers.
        BufferedInputStream in = new BufferedInputStream(bsa, 65536);
        skipExactly(in, entry.offset);
        byte[] stored = new byte[(int) entry.storedBytes];
        readFully(in, stored, 0, stored.length);

        int p = 0;
        if ((archiveFlags & 0x100L) != 0) {
            int nameLength = stored[0] & 0xff;
            if ((long) nameLength + 1 > stored.length)
                throw new IOException("Обрезано встроенное имя записи BSA");
            p = nameLength + 1;
        }
        byte[] data;
        if (entry.compressed) {
            if (stored.length - p < 8) throw new IOException("Слишком короткий сжатый блок BSA");
            long expected = u32(stored, p);
            p += 4;
            if (expected < 1 || expected > MAX_DECODED)
                throw new IOException("Распакованный NIF превышает предел 16 МиБ или имеет нулевой размер");
            // Use pure Java implementations: no platform-specific JNI / shared libraries needed.
            try (LZ4FrameInputStream lz4 = new LZ4FrameInputStream(
                    new ByteArrayInputStream(stored, p, stored.length - p),
                    LZ4Factory.fastestJavaInstance().safeDecompressor(),
                    XXHashFactory.fastestJavaInstance().hash32(), true)) {
                data = readDecodedExactly(lz4, (int) expected);
            } catch (RuntimeException ex) {
                throw new IOException("Ошибка декодирования LZ4: " + ex.getMessage(), ex);
            }
        } else {
            int n = stored.length - p;
            if (n < 1 || n > MAX_DECODED) throw new IOException("Некорректный размер NIF");
            data = new byte[n];
            System.arraycopy(stored, p, data, 0, n);
        }
        return new Decoded(data, stored.length,
                (archiveFlags & 0x100L) != 0 ? (stored[0] & 0xff) + 1 : 0);
    }

    private static byte[] readDecodedExactly(InputStream in, int expected) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(expected, 16384));
        byte[] chunk = new byte[8192];
        while (true) {
            int n = in.read(chunk);
            if (n < 0) break;
            if (n == 0) continue;
            if (out.size() + n > expected) throw new IOException("LZ4 выдаёт больше заявленного размера");
            out.write(chunk, 0, n);
        }
        if (out.size() != expected)
            throw new IOException("Длина LZ4 не совпадает с заголовком BSA: " + out.size() + " / " + expected);
        return out.toByteArray();
    }

    private static long u32(byte[] b, int p) {
        return ((long)b[p]&255)|(((long)b[p+1]&255)<<8)|(((long)b[p+2]&255)<<16)|(((long)b[p+3]&255)<<24);
    }
    private static void readFully(InputStream in, byte[] dst, int start, int len) throws IOException {
        int p = start;
        while (p < start + len) {
            int n = in.read(dst, p, start + len - p);
            if (n < 0) throw new IOException("BSA оборвался внутри выбранной записи");
            if (n == 0) {
                int b = in.read();
                if (b < 0) throw new IOException("BSA оборвался внутри выбранной записи");
                dst[p++] = (byte)b;
            } else p += n;
        }
    }
    private static void skipExactly(InputStream in, long bytes) throws IOException {
        while (bytes > 0) {
            long n = in.skip(bytes);
            if (n > 0) bytes -= n;
            else if (in.read() < 0) throw new IOException("BSA оборвался до начала выбранной записи");
            else bytes--;
        }
    }
}
