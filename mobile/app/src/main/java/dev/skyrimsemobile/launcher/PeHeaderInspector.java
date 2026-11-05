package dev.skyrimsemobile.launcher;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/** Small read-only Windows PE header validator. Does not execute, unpack or modify the EXE. */
public final class PeHeaderInspector {
    private static final long MAX_PE_OFFSET = 8L * 1024 * 1024;
    private PeHeaderInspector() { }

    public static final class Result {
        public final boolean isPe64;
        public final String description;
        Result(boolean accepted, String description) {
            this.isPe64 = accepted;
            this.description = description;
        }
    }

    public static Result inspect(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("Не удалось открыть SkyrimSE.exe");
        byte[] dos = new byte[64];
        readFully(stream, dos);
        if (dos[0] != 'M' || dos[1] != 'Z') return new Result(false, "Файл не имеет заголовка MZ");
        long offset = u32(dos, 0x3c);
        if (offset < 64 || offset > MAX_PE_OFFSET)
            return new Result(false, "Некорректное смещение PE-заголовка");
        skipFully(stream, offset - 64);
        byte[] header = new byte[26];
        readFully(stream, header);
        if (header[0] != 'P' || header[1] != 'E' || header[2] != 0 || header[3] != 0)
            return new Result(false, "Не найден PE-заголовок");
        int machine = u16(header, 4);
        int optionalMagic = u16(header, 24);
        if (machine == 0x8664 && optionalMagic == 0x20b)
            return new Result(true, "Windows PE32+ x86-64 (заголовок подтверждён)");
        if (machine == 0x14c) return new Result(false, "Обнаружен 32-битный EXE (x86), нужен Skyrim SE x64");
        return new Result(false, String.format("Неожиданный тип PE: machine=0x%04x, magic=0x%04x", machine, optionalMagic));
    }

    private static int u16(byte[] a, int i) {
        return (a[i] & 255) | ((a[i + 1] & 255) << 8);
    }
    private static long u32(byte[] a, int i) {
        return ((long) (a[i] & 255)) | ((long) (a[i + 1] & 255) << 8)
            | ((long) (a[i + 2] & 255) << 16) | ((long) (a[i + 3] & 255) << 24);
    }
    private static void readFully(InputStream in, byte[] a) throws IOException {
        int offset = 0;
        while (offset < a.length) {
            int n = in.read(a, offset, a.length - offset);
            if (n == -1) throw new EOFException("Усечённый заголовок EXE");
            if (n == 0) {
                int one = in.read();
                if (one < 0) throw new EOFException("Усечённый заголовок EXE");
                a[offset++] = (byte) one;
            } else offset += n;
        }
    }
    private static void skipFully(InputStream in, long bytes) throws IOException {
        while (bytes > 0) {
            long n = in.skip(bytes);
            if (n <= 0) {
                if (in.read() == -1) throw new EOFException("Усечённый PE-заголовок");
                n = 1;
            }
            bytes -= n;
        }
    }
}
