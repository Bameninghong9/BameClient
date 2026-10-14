package com.bame.client.cosmetics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reine Datei- und Formatlogik der Caeser-Capes (ohne Minecraft-Klassen, daher ohne Spiel testbar):
 * Prüfung der Cape-ID, PNG-Kopf und erlaubte Größe sowie der Festplatten-Cache {@code <id>.png}.
 * <p>
 * Die Cape-ID ist ein Inhalts-Hash, ein Cache-Eintrag gilt daher für immer. Der Cache ist auf
 * {@link #MAX_CACHE_FILES} Dateien begrenzt; die ältesten (nach Änderungszeit, die bei jeder Nutzung erneuert wird)
 * werden gelöscht.
 */
public final class CapeFiles {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_CACHE_FILES = 300;
    public static final int MIN_WIDTH = 64;
    public static final int MIN_HEIGHT = 32;
    public static final int MAX_WIDTH = 1024;
    public static final int MAX_HEIGHT = 512;

    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");
    private static final Pattern SAFE_KEY = Pattern.compile("^[a-z0-9_-]{1,128}$");
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    public record PngSize(int width, int height) {}

    private CapeFiles() {}

    /** Gültige Cape-ID (getrimmt) oder {@code null}. Erlaubt sind nur Buchstaben, Ziffern, {@code _} und {@code -}. */
    public static String normalizeId(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        return ID.matcher(s).matches() ? s : null;
    }

    /**
     * Schlüssel für Dateiname und Textur-Identifier. Kleinbuchstaben-IDs bleiben unverändert; IDs mit Großbuchstaben
     * werden gehasht, weil Identifier nur Kleinbuchstaben erlauben und Windows-Dateinamen Groß/Klein nicht trennen.
     */
    public static String storageKey(String id) {
        if (id == null) return null;
        if (SAFE_KEY.matcher(id).matches()) return id;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("h");
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Breite und Höhe aus dem IHDR-Block, {@code null}, wenn es kein PNG ist. */
    public static PngSize readPngSize(byte[] data) {
        if (data == null || data.length < 33) return null;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) return null;
        }
        // Erster Block muss IHDR mit Länge 13 sein
        if (readInt(data, 8) != 13) return null;
        if (data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') return null;
        int w = readInt(data, 16);
        int h = readInt(data, 20);
        if (w <= 0 || h <= 0) return null;
        return new PngSize(w, h);
    }

    /** Seitenverhältnis 2:1, von 64×32 bis 1024×512. */
    public static boolean isAllowedSize(int width, int height) {
        return width == 2 * height
                && width >= MIN_WIDTH && width <= MAX_WIDTH
                && height >= MIN_HEIGHT && height <= MAX_HEIGHT;
    }

    /** Größe des Capes, wenn die Daten ein PNG mit erlaubter Größe und höchstens {@link #MAX_BYTES} sind, sonst {@code null}. */
    public static PngSize validate(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) return null;
        PngSize size = readPngSize(data);
        if (size == null || !isAllowedSize(size.width(), size.height())) return null;
        return size;
    }

    private static int readInt(byte[] d, int off) {
        return ((d[off] & 0xFF) << 24) | ((d[off + 1] & 0xFF) << 16) | ((d[off + 2] & 0xFF) << 8) | (d[off + 3] & 0xFF);
    }

    // ==========================================
    // Festplatten-Cache
    // ==========================================

    public static Path cacheFile(Path dir, String id) {
        String key = storageKey(id);
        return key == null ? null : dir.resolve(key + ".png");
    }

    /**
     * Liest ein Cape aus dem Cache. Ungültige Dateien werden gelöscht. Bei einem Treffer wird die Änderungszeit erneuert,
     * damit oft genutzte Capes beim Aufräumen bleiben.
     */
    public static byte[] readCached(Path dir, String id) {
        Path file = cacheFile(dir, id);
        if (file == null) return null;
        try {
            if (!Files.isRegularFile(file)) return null;
            if (Files.size(file) > MAX_BYTES) {
                Files.deleteIfExists(file);
                return null;
            }
            byte[] data = Files.readAllBytes(file);
            if (validate(data) == null) {
                Files.deleteIfExists(file);
                return null;
            }
            try {
                Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
            } catch (IOException ignored) {}
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    /** Schreibt ein (bereits geprüftes) Cape atomar in den Cache und räumt danach auf. */
    public static boolean writeCached(Path dir, String id, byte[] data) {
        Path file = cacheFile(dir, id);
        if (file == null || validate(data) == null) return false;
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(file.getFileName() + ".tmp");
            Files.write(tmp, data);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            return false;
        }
        prune(dir, MAX_CACHE_FILES);
        return true;
    }

    /** Löscht die ältesten {@code .png}-Dateien, bis höchstens {@code max} übrig sind. Gibt die Anzahl gelöschter Dateien zurück. */
    public static int prune(Path dir, int max) {
        if (dir == null || !Files.isDirectory(dir)) return 0;
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.png")) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) files.add(p);
            }
        } catch (IOException e) {
            return 0;
        }
        if (files.size() <= max) return 0;
        files.sort(Comparator.comparingLong(CapeFiles::modifiedMillis).thenComparing(Path::toString));
        int toDelete = files.size() - Math.max(0, max);
        int deleted = 0;
        for (int i = 0; i < toDelete; i++) {
            try {
                if (Files.deleteIfExists(files.get(i))) deleted++;
            } catch (IOException ignored) {}
        }
        return deleted;
    }

    private static long modifiedMillis(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
