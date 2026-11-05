package dev.skyrimsemobile.launcher;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class PeHeaderInspectorTest {
    private static byte[] makeHeader(int machine, int magic) {
        byte[] data = new byte[256];
        data[0] = 'M'; data[1] = 'Z';
        data[0x3c] = (byte) 0x80;
        data[0x80] = 'P'; data[0x81] = 'E';
        data[0x84] = (byte) machine;
        data[0x85] = (byte) (machine >> 8);
        data[0x80 + 24] = (byte) magic;
        data[0x80 + 25] = (byte) (magic >> 8);
        return data;
    }
    private static void check(boolean valid, String name) {
        if (!valid) throw new AssertionError(name);
    }
    public static void main(String[] args) throws Exception {
        check(PeHeaderInspector.inspect(new ByteArrayInputStream(makeHeader(0x8664, 0x20b))).isPe64, "x64 PE32+");
        check(!PeHeaderInspector.inspect(new ByteArrayInputStream(makeHeader(0x14c, 0x10b))).isPe64, "32-bit exe");
        check(!PeHeaderInspector.inspect(new ByteArrayInputStream(makeHeader(0x8664, 0x10b))).isPe64, "invalid optional header");
        byte[] broken = makeHeader(0x8664, 0x20b); broken[0]='!';
        check(!PeHeaderInspector.inspect(new ByteArrayInputStream(broken)).isPe64, "bad DOS magic");
        broken = makeHeader(0x8664, 0x20b); broken[0x3c] = 0;
        check(!PeHeaderInspector.inspect(new ByteArrayInputStream(broken)).isPe64, "invalid PE offset");
        try {
            PeHeaderInspector.inspect(new ByteArrayInputStream(new byte[]{'M','Z'}));
            throw new AssertionError("truncated exe should fail");
        } catch (IOException expected) { }
        System.out.println("PASS: PE header inspector (6 cases)");
    }
}
