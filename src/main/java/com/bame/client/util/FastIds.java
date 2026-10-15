package com.bame.client.util;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Schnelle, Minecraft-freie Varianten von Hilfsfunktionen, die pro Frame und Spieler laufen (Tab-Liste, Cosmetics-
 * Renderer, Cape-Lookup). Ergebnisse sind identisch mit den früheren String-Operationen (siehe TabListPerfTest).
 */
public final class FastIds {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final Pattern COLOR_CODES = Pattern.compile("§[0-9a-fk-orA-FK-OR]");

    private FastIds() {}

    /*
     * Ergebnis-Cache: dieselben Spieler-UUIDs werden pro Frame vielfach umgewandelt (Emote-/Cosmetics-Lookups mehrmals
     * pro Spieler, Tab-Liste, Capes). Thread-sicher, ohne Allokation beim Treffer; begrenzt durch Leeren.
     */
    private static final int UUID32_CACHE_MAX = 4096;
    private static final java.util.concurrent.ConcurrentHashMap<UUID, String> UUID32_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * UUID als 32 Hex-Zeichen (klein, ohne Bindestriche), gleich {@code uuid.toString().replace("-", "").toLowerCase()},
     * aber ohne Zwischen-Strings; wiederholte Abfragen derselben UUID kommen aus einem Cache (keine Allokation).
     */
    public static String uuid32(UUID uuid) {
        if (uuid == null) return null;
        String cached = UUID32_CACHE.get(uuid);
        if (cached != null) return cached;
        String s = uuid32Uncached(uuid);
        if (UUID32_CACHE.size() >= UUID32_CACHE_MAX) UUID32_CACHE.clear();
        UUID32_CACHE.put(uuid, s);
        return s;
    }

    /** Umwandlung ohne Cache (eine Allokation). */
    public static String uuid32Uncached(UUID uuid) {
        if (uuid == null) return null;
        char[] out = new char[32];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 15; i >= 0; i--) {
            out[i] = HEX[(int) (msb & 0xF)];
            msb >>>= 4;
        }
        for (int i = 31; i >= 16; i--) {
            out[i] = HEX[(int) (lsb & 0xF)];
            lsb >>>= 4;
        }
        return new String(out);
    }

    /**
     * Name ohne Farbcodes, nur das letzte Wort, klein. Gleiches Ergebnis wie früher
     * ({@code replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim()}, Teil nach dem letzten Leerzeichen, {@code toLowerCase}),
     * aber ohne Regex-Kompilierung pro Aufruf und ohne Regex, wenn kein {@code §} vorkommt.
     */
    public static String cleanUsername(String raw) {
        if (raw == null) return null;
        String clean = raw.indexOf('§') >= 0 ? COLOR_CODES.matcher(raw).replaceAll("") : raw;
        clean = clean.trim();
        int spaceIdx = clean.lastIndexOf(' ');
        if (spaceIdx >= 0) {
            clean = clean.substring(spaceIdx + 1);
        }
        return clean.toLowerCase(Locale.ROOT);
    }
}
