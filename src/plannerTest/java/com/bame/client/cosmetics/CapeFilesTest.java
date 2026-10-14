package com.bame.client.cosmetics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Prüft ohne Minecraft die Datei- und Formatlogik der Caeser-Capes aus {@link CapeFiles}: Cape-IDs, PNG-Kopf,
 * erlaubte Größen (2:1, 64×32 bis 1024×512, inkl. HD 512×256), 1-MB-Grenze und den Festplatten-Cache mit
 * Begrenzung auf 300 Dateien (älteste werden gelöscht).
 */
public class CapeFilesTest {

    private static int checks = 0;

    public static void main(String[] args) throws Exception {
        ids();
        sizes();
        pngHeader();
        cache();
        System.out.println("CAPE FILES TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static void ids() {
        check("abc123".equals(CapeFiles.normalizeId(" abc123 ")), "trimmed id");
        check(CapeFiles.normalizeId(null) == null, "null id");
        check(CapeFiles.normalizeId("") == null, "empty id");
        check(CapeFiles.normalizeId("../evil") == null, "path traversal rejected");
        check(CapeFiles.normalizeId("a/b") == null, "slash rejected");
        check(CapeFiles.normalizeId("a.png") == null, "dot rejected");
        check(CapeFiles.normalizeId("x".repeat(129)) == null, "too long rejected");
        check(CapeFiles.normalizeId("Ab_-9") != null, "base64url chars allowed");
        String hex = "0123456789abcdef0123456789abcdef";
        check(hex.equals(CapeFiles.storageKey(hex)), "lowercase id kept as key");
        String upper = CapeFiles.storageKey("AbC");
        check(upper != null && upper.matches("^h[0-9a-f]{64}$"), "uppercase id hashed");
        check(!upper.equals(CapeFiles.storageKey("abc")), "case variants do not collide");
        check(upper.equals(CapeFiles.storageKey("AbC")), "hash stable");
    }

    private static void sizes() {
        check(CapeFiles.isAllowedSize(64, 32), "64x32");
        check(CapeFiles.isAllowedSize(128, 64), "128x64");
        check(CapeFiles.isAllowedSize(512, 256), "HD 512x256");
        check(CapeFiles.isAllowedSize(1024, 512), "1024x512");
        check(CapeFiles.isAllowedSize(96, 48), "non power of two 2:1");
        check(!CapeFiles.isAllowedSize(32, 16), "too small");
        check(!CapeFiles.isAllowedSize(2048, 1024), "too large");
        check(!CapeFiles.isAllowedSize(64, 64), "1:1 rejected");
        check(!CapeFiles.isAllowedSize(64, 33), "not exactly 2:1");
    }

    private static void pngHeader() throws IOException {
        byte[] hd = png(512, 256);
        CapeFiles.PngSize s = CapeFiles.readPngSize(hd);
        check(s != null && s.width() == 512 && s.height() == 256, "reads HD PNG size");
        check(CapeFiles.validate(hd) != null, "HD PNG valid");
        check(CapeFiles.validate(png(64, 32)) != null, "classic PNG valid");
        check(CapeFiles.validate(png(64, 64)) == null, "square PNG rejected");
        check(CapeFiles.validate(png(2048, 1024)) == null, "oversized PNG rejected");
        check(CapeFiles.validate(null) == null, "null rejected");
        check(CapeFiles.validate(new byte[0]) == null, "empty rejected");
        byte[] notPng = "GIF89a this is not a png at all, really not".getBytes();
        check(CapeFiles.validate(notPng) == null, "non-PNG rejected");
        byte[] broken = hd.clone();
        broken[12] = 'X'; // IHDR kaputt
        check(CapeFiles.validate(broken) == null, "broken IHDR rejected");
        byte[] truncated = java.util.Arrays.copyOf(hd, 20);
        check(CapeFiles.validate(truncated) == null, "truncated rejected");
        byte[] big = java.util.Arrays.copyOf(png(64, 32), CapeFiles.MAX_BYTES + 1);
        check(CapeFiles.validate(big) == null, "over 1 MB rejected");
    }

    private static void cache() throws IOException {
        Path dir = Files.createTempDirectory("cape-cache-test");
        try {
            byte[] data = png(128, 64);
            check(CapeFiles.readCached(dir, "missing") == null, "miss on empty cache");
            check(CapeFiles.writeCached(dir, "abc", data), "write ok");
            byte[] back = CapeFiles.readCached(dir, "abc");
            check(back != null && java.util.Arrays.equals(back, data), "read back same bytes");
            check(Files.exists(dir.resolve("abc.png")), "file named <id>.png");
            check(!CapeFiles.writeCached(dir, "bad", "nope".getBytes()), "invalid data not cached");
            check(!Files.exists(dir.resolve("bad.png")), "no file for invalid data");

            // Kaputte Datei im Cache wird verworfen und gelöscht
            Files.write(dir.resolve("corrupt.png"), "garbage".getBytes());
            check(CapeFiles.readCached(dir, "corrupt") == null, "corrupt cache entry ignored");
            check(!Files.exists(dir.resolve("corrupt.png")), "corrupt cache entry deleted");

            // Begrenzung: 305 Dateien -> die 5 ältesten werden gelöscht
            for (int i = 0; i < 305; i++) {
                Path f = dir.resolve(String.format("c%03d.png", i));
                Files.write(f, data);
                Files.setLastModifiedTime(f, FileTime.fromMillis(1_000_000L + i * 1000L));
            }
            Files.setLastModifiedTime(dir.resolve("abc.png"), FileTime.fromMillis(9_000_000_000L));
            int deleted = CapeFiles.prune(dir, CapeFiles.MAX_CACHE_FILES);
            long remaining;
            try (var s = Files.list(dir)) {
                remaining = s.filter(p -> p.toString().endsWith(".png")).count();
            }
            check(deleted == 6, "deleted oldest beyond 300 (got " + deleted + ")");
            check(remaining == 300, "300 files remain (got " + remaining + ")");
            for (int i = 0; i < 6; i++) {
                check(!Files.exists(dir.resolve(String.format("c%03d.png", i))), "oldest deleted " + i);
            }
            check(Files.exists(dir.resolve("c006.png")), "newer kept");
            check(Files.exists(dir.resolve("abc.png")), "recently used kept");

            // Lesen erneuert die Änderungszeit (LRU)
            Files.setLastModifiedTime(dir.resolve("c006.png"), FileTime.fromMillis(1L));
            check(CapeFiles.readCached(dir, "c006") != null, "cache hit");
            check(Files.getLastModifiedTime(dir.resolve("c006.png")).toMillis() > 1_000_000L, "hit refreshes mtime");

            // writeCached räumt selbst auf
            check(CapeFiles.writeCached(dir, "fresh", data), "write with prune");
            try (var s = Files.list(dir)) {
                remaining = s.filter(p -> p.toString().endsWith(".png")).count();
            }
            check(remaining == 300, "still 300 after write (got " + remaining + ")");
            check(Files.exists(dir.resolve("fresh.png")), "fresh entry kept");
        } finally {
            try (var s = Files.walk(dir)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    /** Minimales, gültiges RGBA-PNG der Größe w×h (alle Pixel transparent). */
    private static byte[] png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        writeInt(ihdr, w);
        writeInt(ihdr, h);
        ihdr.write(new byte[]{8, 6, 0, 0, 0});
        chunk(out, "IHDR", ihdr.toByteArray());
        byte[] raw = new byte[h * (1 + w * 4)];
        Deflater d = new Deflater();
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!d.finished()) {
            int n = d.deflate(buf);
            z.write(buf, 0, n);
        }
        d.end();
        chunk(out, "IDAT", z.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        writeInt(out, data.length);
        byte[] t = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(t);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }
}
