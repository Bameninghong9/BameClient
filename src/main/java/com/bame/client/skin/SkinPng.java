package com.bame.client.skin;

import java.util.zip.CRC32;

/**
 * Reine PNG-Prüfung für heruntergeladene Skins (keine Minecraft-Klassen, getestet in {@code src/plannerTest}, Aufgabe
 * {@code skinSaveTest}): Signatur, Chunk-Aufbau mit CRC, IHDR zuerst, mindestens ein IDAT, IEND am Ende, Größe
 * 64x64 oder 64x32 und höchstens {@link #MAX_BYTES}. Wirft nie (auch nicht bei absichtlich kaputten Daten).
 */
public final class SkinPng {
    private SkinPng() {}

    /** Höchstgröße eines Skins (Download und Prüfung). */
    public static final int MAX_BYTES = 1024 * 1024;

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final int IHDR = chunkType("IHDR");
    private static final int IDAT = chunkType("IDAT");
    private static final int IEND = chunkType("IEND");

    /** Ergebnis der Prüfung; {@code reason} ist bei Fehlern eine kurze deutsche Begründung. */
    public record Result(boolean ok, int width, int height, String reason) {
        static Result fail(String reason) {
            return new Result(false, 0, 0, reason);
        }
    }

    public static Result check(byte[] data) {
        if (data == null || data.length == 0) return Result.fail("leere Datei");
        if (data.length > MAX_BYTES) return Result.fail("Datei größer als 1 MB");
        if (data.length < SIGNATURE.length + 12 + 13) return Result.fail("kein PNG");
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (data[i] != SIGNATURE[i]) return Result.fail("kein PNG");
        }
        int pos = SIGNATURE.length;
        int width = -1, height = -1;
        boolean first = true;
        boolean sawData = false;
        CRC32 crc = new CRC32();
        while (true) {
            if (data.length - pos < 12) return Result.fail("PNG unvollständig");
            long length = readUInt(data, pos);
            int type = readInt(data, pos + 4);
            if (!isChunkType(data, pos + 4)) return Result.fail("PNG beschädigt");
            if (length > data.length - pos - 12) return Result.fail("PNG unvollständig");
            int dataStart = pos + 8;
            int len = (int) length;
            crc.reset();
            crc.update(data, pos + 4, 4 + len);
            if (crc.getValue() != readUInt(data, dataStart + len)) return Result.fail("PNG beschädigt (Prüfsumme)");
            if (first) {
                if (type != IHDR || len != 13) return Result.fail("PNG ohne Kopfdaten");
                long w = readUInt(data, dataStart);
                long h = readUInt(data, dataStart + 4);
                int bitDepth = data[dataStart + 8] & 0xFF;
                int colorType = data[dataStart + 9] & 0xFF;
                int compression = data[dataStart + 10] & 0xFF;
                int filter = data[dataStart + 11] & 0xFF;
                int interlace = data[dataStart + 12] & 0xFF;
                if (!validDepth(colorType, bitDepth) || compression != 0 || filter != 0 || interlace > 1) {
                    return Result.fail("PNG-Format nicht unterstützt");
                }
                if (w != 64 || (h != 64 && h != 32)) {
                    return Result.fail("falsche Größe " + w + "x" + h + " (erlaubt 64x64 oder 64x32)");
                }
                width = (int) w;
                height = (int) h;
                first = false;
            } else if (type == IHDR) {
                return Result.fail("PNG beschädigt");
            } else if (type == IDAT) {
                sawData = true;
            } else if (type == IEND) {
                if (len != 0) return Result.fail("PNG beschädigt");
                if (!sawData) return Result.fail("PNG ohne Bilddaten");
                return new Result(true, width, height, null);
            }
            pos = dataStart + len + 4;
        }
    }

    private static boolean validDepth(int colorType, int bitDepth) {
        return switch (colorType) {
            case 0 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 || bitDepth == 16;
            case 3 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8;
            case 2, 4, 6 -> bitDepth == 8 || bitDepth == 16;
            default -> false;
        };
    }

    /** Chunk-Typen bestehen aus vier ASCII-Buchstaben. */
    private static boolean isChunkType(byte[] data, int at) {
        for (int i = 0; i < 4; i++) {
            int c = data[at + i] & 0xFF;
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) return false;
        }
        return true;
    }

    private static int chunkType(String s) {
        return (s.charAt(0) << 24) | (s.charAt(1) << 16) | (s.charAt(2) << 8) | s.charAt(3);
    }

    private static int readInt(byte[] b, int at) {
        return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }

    private static long readUInt(byte[] b, int at) {
        return readInt(b, at) & 0xFFFFFFFFL;
    }
}
