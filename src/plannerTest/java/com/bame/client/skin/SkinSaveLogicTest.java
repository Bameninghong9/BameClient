package com.bame.client.skin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Prüft ohne Minecraft die reine Logik von „Skin speichern“: Dateinamen bereinigen und Kollisionen nummerieren
 * ({@link SkinFileNames}), PNG-/Größenprüfung ({@link SkinPng}), erlaubte Skin-Adressen und Data-URL
 * ({@link SkinUrls}) sowie die Auswertung der Launcher-Antworten ({@link SkinImportReply}).
 * Gradle-Aufgabe {@code skinSaveTest} (läuft mit {@code check}/{@code build}).
 */
public final class SkinSaveLogicTest {
    private static int checks = 0;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        names();
        collisions();
        pngValid();
        pngInvalid();
        pngFuzz();
        urls();
        dataUrl();
        replies();
        System.out.println("SKIN SAVE TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Dateinamen
    // ------------------------------------------------------------------------------------------------------------

    private static void names() {
        check("Notch".equals(SkinFileNames.sanitize("Notch")), "normal name unchanged");
        check("Dinner_bone-2".equals(SkinFileNames.sanitize("Dinner_bone-2")), "underscore and dash kept");
        check("___".equals(SkinFileNames.sanitize("___")), "valid Minecraft name of underscores kept");
        check("A_B".equals(SkinFileNames.sanitize("A B")), "space replaced");
        check("___evil".equals(SkinFileNames.sanitize("../evil")), "path traversal neutralised");
        check("C__x".equals(SkinFileNames.sanitize("C:\\x")), "drive and backslash replaced");
        check("a_b_c".equals(SkinFileNames.sanitize("a/b\\c")), "separators replaced");
        check("Spieler_Name".equals(SkinFileNames.sanitize("Spieler€Name")), "non-ASCII replaced");
        check("skin".equals(SkinFileNames.sanitize("äöü")), "nothing kept -> fallback");
        check("skin".equals(SkinFileNames.sanitize("...")), "only dots -> fallback");
        check("skin".equals(SkinFileNames.sanitize("")), "empty -> fallback");
        check("skin".equals(SkinFileNames.sanitize(null)), "null -> fallback");
        check("_CON".equals(SkinFileNames.sanitize("CON")) && "_con".equals(SkinFileNames.sanitize("con")), "reserved Windows names prefixed");
        check("_Com1".equals(SkinFileNames.sanitize("Com1")) && "_LPT9".equals(SkinFileNames.sanitize("LPT9")), "reserved device names prefixed");
        check("CONSOLE".equals(SkinFileNames.sanitize("CONSOLE")), "longer names with a reserved prefix are fine");
        String longName = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
        check(SkinFileNames.sanitize(longName).length() == SkinFileNames.MAX_BASE_LENGTH, "length capped");
        Random random = new Random(5);
        for (int i = 0; i < 3000; i++) {
            StringBuilder sb = new StringBuilder();
            int len = random.nextInt(40);
            for (int k = 0; k < len; k++) sb.append((char) random.nextInt(0x3000));
            String s = SkinFileNames.sanitize(sb.toString());
            check(s.matches("[A-Za-z0-9_-]{1,33}"), "only safe characters: " + s);
            check(!s.contains("..") && !s.contains("/") && !s.contains("\\") && !s.contains(":"), "no path parts");
            check(SkinFileNames.sanitize(s).equals(s), "sanitize is idempotent");
        }
    }

    private static void collisions() {
        check("x.png".equals(SkinFileNames.fileName("x", 1)) && "x_2.png".equals(SkinFileNames.fileName("x", 2)), "file name numbering");
        Set<String> existing = new HashSet<>();
        java.util.function.Predicate<String> exists = n -> existing.contains(n.toLowerCase(Locale.ROOT)); // Windows: Groß/klein egal
        check("Notch.png".equals(SkinFileNames.firstFree("Notch", exists)), "free name");
        existing.add("notch.png");
        check("Notch_2.png".equals(SkinFileNames.firstFree("Notch", exists)), "collision -> _2 (case-insensitive)");
        existing.add("notch_2.png");
        check("Notch_3.png".equals(SkinFileNames.firstFree("Notch", exists)), "second collision -> _3");
        existing.remove("notch.png");
        check("Notch.png".equals(SkinFileNames.firstFree("Notch", exists)), "gap is reused");
        check(SkinFileNames.firstFree("Full", n -> true) == null, "all numbers taken -> null");
        Set<String> seen = new HashSet<>();
        for (int n = 1; n <= SkinFileNames.MAX_NUMBER; n++) {
            check(seen.add(SkinFileNames.fileName("Steve", n).toLowerCase(Locale.ROOT)), "numbered names are unique");
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // PNG
    // ------------------------------------------------------------------------------------------------------------

    private static byte[] chunk(String type, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len = data.length;
        out.write(len >>> 24);
        out.write(len >>> 16);
        out.write(len >>> 8);
        out.write(len);
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(t);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        long v = crc.getValue();
        out.write((int) (v >>> 24));
        out.write((int) (v >>> 16));
        out.write((int) (v >>> 8));
        out.write((int) v);
        return out.toByteArray();
    }

    private static byte[] ihdr(int w, int h, int bitDepth, int colorType) {
        return new byte[]{
                (byte) (w >>> 24), (byte) (w >>> 16), (byte) (w >>> 8), (byte) w,
                (byte) (h >>> 24), (byte) (h >>> 16), (byte) (h >>> 8), (byte) h,
                (byte) bitDepth, (byte) colorType, 0, 0, 0};
    }

    private static byte[] idat(int w, int h, int bytesPerPixel) {
        byte[] raw = new byte[h * (1 + w * bytesPerPixel)];
        for (int i = 0; i < raw.length; i++) raw[i] = (byte) (i % (1 + w * bytesPerPixel) == 0 ? 0 : (i * 31));
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        byte[] buf = new byte[raw.length + 64];
        int n = deflater.deflate(buf);
        deflater.end();
        return java.util.Arrays.copyOf(buf, n);
    }

    private static final byte[] SIG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private static byte[] png(byte[]... chunks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(SIG);
        for (byte[] c : chunks) out.writeBytes(c);
        return out.toByteArray();
    }

    private static byte[] simplePng(int w, int h) {
        return png(chunk("IHDR", ihdr(w, h, 8, 6)), chunk("IDAT", idat(w, h, 4)), chunk("IEND", new byte[0]));
    }

    private static byte[] imageIo(int w, int h) throws Exception {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setRGB(x, y, (x * 4) << 16 | (y * 4) << 8 | 0xFF000000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static void pngValid() throws Exception {
        SkinPng.Result r = SkinPng.check(simplePng(64, 64));
        check(r.ok() && r.width() == 64 && r.height() == 64 && r.reason() == null, "64x64 RGBA is valid");
        r = SkinPng.check(simplePng(64, 32));
        check(r.ok() && r.height() == 32, "64x32 (old skin format) is valid");
        byte[] rgb = png(chunk("IHDR", ihdr(64, 64, 8, 2)), chunk("IDAT", idat(64, 64, 3)), chunk("IEND", new byte[0]));
        check(SkinPng.check(rgb).ok(), "RGB is valid");
        byte[] palette = png(chunk("IHDR", ihdr(64, 64, 8, 3)), chunk("PLTE", new byte[]{0, 0, 0, (byte) 255, (byte) 255, (byte) 255}),
                chunk("IDAT", idat(64, 64, 1)), chunk("IEND", new byte[0]));
        check(SkinPng.check(palette).ok(), "palette PNG is valid");
        byte[] withText = png(chunk("IHDR", ihdr(64, 64, 8, 6)), chunk("tEXt", "Comment\0skin".getBytes(StandardCharsets.ISO_8859_1)),
                chunk("IDAT", idat(64, 64, 4)), chunk("IDAT", new byte[0]), chunk("IEND", new byte[0]));
        check(SkinPng.check(withText).ok(), "ancillary chunks and split IDAT are fine");
        check(SkinPng.check(imageIo(64, 64)).ok(), "real encoder output 64x64 is valid");
        check(SkinPng.check(imageIo(64, 32)).ok(), "real encoder output 64x32 is valid");
        // Genau 1 MB (mit großem Zusatz-Chunk) ist noch erlaubt
        byte[] base = simplePng(64, 64);
        int filler = SkinPng.MAX_BYTES - base.length - 12;
        byte[] exact = png(chunk("IHDR", ihdr(64, 64, 8, 6)), chunk("zTXt", new byte[filler]), chunk("IDAT", idat(64, 64, 4)), chunk("IEND", new byte[0]));
        check(exact.length == SkinPng.MAX_BYTES, "test file is exactly 1 MB");
        check(SkinPng.check(exact).ok(), "exactly 1 MB is allowed");
    }

    private static void pngInvalid() throws Exception {
        int[][] sizes = {{32, 32}, {64, 48}, {128, 128}, {128, 64}, {64, 65}, {63, 64}, {0, 0}};
        for (int[] s : sizes) {
            SkinPng.Result r = SkinPng.check(simplePng(s[0], s[1]));
            check(!r.ok() && r.reason().contains("Größe"), "wrong size rejected: " + s[0] + "x" + s[1]);
        }
        check(!SkinPng.check(imageIo(64, 128)).ok(), "real 64x128 rejected");
        check(!SkinPng.check(null).ok() && !SkinPng.check(new byte[0]).ok(), "empty rejected");
        byte[] jpeg = new byte[200];
        jpeg[0] = (byte) 0xFF;
        jpeg[1] = (byte) 0xD8;
        jpeg[2] = (byte) 0xFF;
        check(!SkinPng.check(jpeg).ok(), "JPEG rejected");
        byte[] gif = "GIF89a....................................................".getBytes(StandardCharsets.US_ASCII);
        check(!SkinPng.check(gif).ok(), "GIF rejected");
        check(!SkinPng.check("<html>not a png</html> ......................".getBytes(StandardCharsets.US_ASCII)).ok(), "HTML rejected");

        byte[] good = simplePng(64, 64);
        for (int cut = 0; cut < good.length; cut++) {
            check(!SkinPng.check(java.util.Arrays.copyOf(good, cut)).ok(), "truncated file rejected (" + cut + " bytes)");
        }
        byte[] badCrc = good.clone();
        badCrc[8 + 8 + 13] ^= 0x55; // CRC des IHDR
        check(!SkinPng.check(badCrc).ok(), "broken CRC rejected");
        byte[] flipped = good.clone();
        flipped[8 + 8 + 2] ^= 0x01; // Breite im IHDR geändert, CRC passt nicht mehr
        check(!SkinPng.check(flipped).ok(), "changed header without CRC rejected");
        check(!SkinPng.check(png(chunk("IHDR", ihdr(64, 64, 8, 6)), chunk("IDAT", idat(64, 64, 4)))).ok(), "missing IEND rejected");
        check(!SkinPng.check(png(chunk("IHDR", ihdr(64, 64, 8, 6)), chunk("IEND", new byte[0]))).ok(), "missing IDAT rejected");
        check(!SkinPng.check(png(chunk("tEXt", "a\0b".getBytes(StandardCharsets.ISO_8859_1)), chunk("IHDR", ihdr(64, 64, 8, 6)),
                chunk("IDAT", idat(64, 64, 4)), chunk("IEND", new byte[0]))).ok(), "IHDR not first rejected");
        check(!SkinPng.check(png(chunk("IHDR", ihdr(64, 64, 8, 6)), chunk("IDAT", idat(64, 64, 4)), chunk("IEND", new byte[]{1}))).ok(),
                "IEND with data rejected");
        check(!SkinPng.check(png(chunk("IHDR", ihdr(64, 64, 8, 6)), chunk("IHDR", ihdr(64, 64, 8, 6)), chunk("IDAT", idat(64, 64, 4)),
                chunk("IEND", new byte[0]))).ok(), "second IHDR rejected");
        check(!SkinPng.check(png(chunk("IHDR", ihdr(64, 64, 7, 6)), chunk("IDAT", idat(64, 64, 4)), chunk("IEND", new byte[0]))).ok(),
                "invalid bit depth rejected");
        check(!SkinPng.check(png(chunk("IHDR", ihdr(64, 64, 8, 5)), chunk("IDAT", idat(64, 64, 4)), chunk("IEND", new byte[0]))).ok(),
                "invalid colour type rejected");
        byte[] hugeLength = good.clone();
        hugeLength[8] = (byte) 0xFF;
        hugeLength[9] = (byte) 0xFF;
        hugeLength[10] = (byte) 0xFF;
        hugeLength[11] = (byte) 0xFF;
        check(!SkinPng.check(hugeLength).ok(), "absurd chunk length rejected without exception");
        byte[] base = simplePng(64, 64);
        byte[] tooBig = png(chunk("IHDR", ihdr(64, 64, 8, 6)), chunk("zTXt", new byte[SkinPng.MAX_BYTES - base.length - 11]),
                chunk("IDAT", idat(64, 64, 4)), chunk("IEND", new byte[0]));
        check(tooBig.length == SkinPng.MAX_BYTES + 1, "test file is 1 MB + 1 byte");
        SkinPng.Result r = SkinPng.check(tooBig);
        check(!r.ok() && r.reason().contains("1 MB"), "more than 1 MB rejected");
    }

    /** Kaputte und zufällige Daten: nie eine Ausnahme, nie fälschlich gültig mit falscher Größe. */
    private static void pngFuzz() {
        Random random = new Random(77);
        byte[] good = simplePng(64, 64);
        for (int i = 0; i < 20000; i++) {
            byte[] data;
            if (i % 3 == 0) {
                data = new byte[random.nextInt(300)];
                random.nextBytes(data);
                if (data.length >= 8 && i % 2 == 0) System.arraycopy(SIG, 0, data, 0, 8);
            } else {
                data = good.clone();
                int flips = 1 + random.nextInt(4);
                for (int k = 0; k < flips; k++) data[random.nextInt(data.length)] ^= (byte) (1 << random.nextInt(8));
            }
            SkinPng.Result r;
            try {
                r = SkinPng.check(data);
            } catch (Throwable t) {
                throw new AssertionError("check threw " + t, t);
            }
            checks++;
            if (r.ok()) check(r.width() == 64 && (r.height() == 64 || r.height() == 32), "valid result always has a skin size");
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Adressen und Data-URL
    // ------------------------------------------------------------------------------------------------------------

    private static final String HASH = "2a0b5f1b2b1a2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef01234567";

    private static void urls() {
        String expected = "https://textures.minecraft.net/texture/" + HASH;
        check(expected.equals(SkinUrls.normalize("http://textures.minecraft.net/texture/" + HASH)), "http from the profile -> https");
        check(expected.equals(SkinUrls.normalize(expected)), "https kept");
        check(expected.equals(SkinUrls.normalize("https://textures.minecraft.net/texture/" + HASH.toUpperCase(Locale.ROOT))), "hash lower-cased");
        check(expected.equals(SkinUrls.normalize("  " + expected + "\n")), "surrounding whitespace trimmed");
        String[] bad = {
                null, "", "   ",
                "https://textures.minecraft.net.evil.com/texture/" + HASH,
                "https://evil.com/textures.minecraft.net/texture/" + HASH,
                "https://evil.com/?u=https://textures.minecraft.net/texture/" + HASH,
                "https://user@textures.minecraft.net/texture/" + HASH,
                "https://textures.minecraft.net:443/texture/" + HASH,
                "https://textures.minecraft.net/texture/" + HASH + "?x=1",
                "https://textures.minecraft.net/texture/" + HASH + "#frag",
                "https://textures.minecraft.net/texture/" + HASH + "/",
                "https://textures.minecraft.net/texture/" + HASH + "/extra",
                "https://textures.minecraft.net/texture/../" + HASH,
                "https://textures.minecraft.net/skin/" + HASH,
                "https://textures.minecraft.net/texture/xyz" + HASH.substring(3),
                "https://textures.minecraft.net/texture/abc123",
                "https://textures.minecraft.net/texture/" + HASH + HASH + HASH,
                "https://textures.minecraft.net/texture/" + HASH.substring(0, 20) + " " + HASH.substring(20),
                "ftp://textures.minecraft.net/texture/" + HASH,
                "file:///C:/texture/" + HASH,
                "HTTPS://TEXTURES.MINECRAFT.NET/texture/" + HASH,
                "https://textures.minecraft.net\\texture\\" + HASH,
                "https://textures%2Eminecraft.net/texture/" + HASH,
        };
        for (String u : bad) check(SkinUrls.normalize(u) == null, "rejected: " + u);
    }

    private static void dataUrl() {
        byte[] png = simplePng(64, 64);
        String url = SkinUrls.pngDataUrl(png);
        check(url.startsWith("data:image/png;base64,"), "data URL prefix");
        check(java.util.Arrays.equals(Base64.getDecoder().decode(url.substring("data:image/png;base64,".length())), png), "data URL decodes back");
        for (int n = 0; n <= 300; n++) {
            check(SkinUrls.pngDataUrlLength(n) == SkinUrls.pngDataUrl(new byte[n]).length(), "length formula " + n);
        }
        check(SkinUrls.pngDataUrlLength(SkinPng.MAX_BYTES) == SkinUrls.pngDataUrl(new byte[SkinPng.MAX_BYTES]).length(), "length formula 1 MB");
        // Bridge-Limit 512 KB: ein normaler Skin passt locker, eine 1-MB-Datei nicht (wird dann lokal gesichert)
        check(SkinUrls.pngDataUrlLength(png.length) < 512 * 1024, "normal skin fits the bridge body limit");
        check(SkinUrls.pngDataUrlLength(SkinPng.MAX_BYTES) > 512 * 1024, "1 MB skin exceeds the bridge body limit");
        check("slim".equals(SkinUrls.variant("slim")) && "slim".equals(SkinUrls.variant("SLIM")) && "slim".equals(SkinUrls.variant(" slim ")),
                "slim model");
        check("classic".equals(SkinUrls.variant(null)) && "classic".equals(SkinUrls.variant("")) && "classic".equals(SkinUrls.variant("default")),
                "everything else is classic");
    }

    // ------------------------------------------------------------------------------------------------------------
    // Launcher-Antworten
    // ------------------------------------------------------------------------------------------------------------

    private static void replies() {
        check("Skin von Notch gespeichert – im Launcher unter Skins".equals(SkinImportReply.savedMessage("Notch")), "success text");
        check("Skin von Notch ist schon in deiner Bibliothek".equals(SkinImportReply.duplicateMessage("Notch")), "duplicate text");
        check("Launcher nicht verbunden – Skin gespeichert unter config/bameclient/saved-skins".equals(SkinImportReply.MSG_FALLBACK), "fallback text");
        check("Launcher nicht erreichbar".equals(SkinImportReply.MSG_UNREACHABLE), "unreachable text");
        check(SkinImportReply.noMojangSkinMessage("Steve").contains("keinen Mojang-Skin"), "no Mojang skin text");

        check(!SkinImportReply.isDuplicate(201, null) && SkinImportReply.isDuplicate(200, null), "status decides without the field");
        check(SkinImportReply.isDuplicate(201, true) && !SkinImportReply.isDuplicate(200, false), "field wins over the status");

        check(SkinImportReply.retryAfterSeconds("30") == 30 && SkinImportReply.retryAfterSeconds(" 7 ") == 7, "retry-after seconds");
        check(SkinImportReply.retryAfterSeconds("0") == 1 && SkinImportReply.retryAfterSeconds("99999") == 3600, "retry-after clamped");
        check(SkinImportReply.retryAfterSeconds(null) == -1 && SkinImportReply.retryAfterSeconds("-5") == -1
                && SkinImportReply.retryAfterSeconds("12a") == -1 && SkinImportReply.retryAfterSeconds("Wed, 21 Oct 2015 07:28:00 GMT") == -1,
                "retry-after date or garbage ignored");

        check("Ungültiges Bild".equals(SkinImportReply.errorMessage(400, "Ungültiges Bild", null)), "launcher message shown");
        check("Der Launcher hat den Skin abgelehnt (ungültige Daten)".equals(SkinImportReply.errorMessage(400, null, null)), "400 fallback");
        check("Der Launcher hat die Anfrage abgelehnt".equals(SkinImportReply.errorMessage(401, null, null)), "401 fallback");
        check("Der Skin konnte im Launcher nicht gespeichert werden".equals(SkinImportReply.errorMessage(409, "   ", null)), "409 fallback (blank message)");
        check("Die Skin-Datei ist für den Launcher zu groß".equals(SkinImportReply.errorMessage(413, null, null)), "413 fallback");
        check("Zu viele Anfragen an den Launcher – bitte 20 s warten".equals(SkinImportReply.errorMessage(429, null, "20")), "429 with Retry-After");
        check("Zu viele Anfragen an den Launcher – bitte kurz warten".equals(SkinImportReply.errorMessage(429, null, null)), "429 without Retry-After");
        check("Limit erreicht – bitte kurz warten".equals(SkinImportReply.errorMessage(429, "Limit erreicht", "abc")), "429 launcher text plus hint");
        check("Bitte warte kurz".equals(SkinImportReply.errorMessage(429, "Bitte warte kurz", "5")), "429 text that already asks to wait");
        check("Launcher nicht erreichbar".equals(SkinImportReply.errorMessage(500, null, null)), "other status: unreachable");
        String dirty = SkinImportReply.errorMessage(400, "§cRot\u0007er\nText", null);
        check(dirty.indexOf('§') < 0 && dirty.chars().noneMatch(Character::isISOControl), "launcher message sanitised");
        String longMsg = SkinImportReply.errorMessage(400, "x".repeat(500), null);
        check(longMsg.length() == 201 && longMsg.endsWith("…"), "long launcher message shortened");
        check(SkinImportReply.sanitize("  ") == null && SkinImportReply.sanitize(null) == null, "blank -> null");
    }
}
