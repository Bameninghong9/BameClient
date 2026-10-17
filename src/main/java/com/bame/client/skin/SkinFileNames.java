package com.bame.client.skin;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Reine Dateinamen-Logik für den Rückfall „Skin lokal speichern“ (keine Minecraft-Klassen, getestet in
 * {@code src/plannerTest}, Aufgabe {@code skinSaveTest}): Spielername bereinigen und bei Kollisionen nummerieren
 * ({@code Name.png}, {@code Name_2.png}, {@code Name_3.png}, ...).
 */
public final class SkinFileNames {
    private SkinFileNames() {}

    public static final int MAX_BASE_LENGTH = 32;
    /** Höchstens so viele Nummern ausprobieren. */
    public static final int MAX_NUMBER = 999;
    public static final String FALLBACK_BASE = "skin";

    private static final String[] RESERVED = {"CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};

    /**
     * Sicherer Dateiname ohne Endung: nur Buchstaben A-Z/a-z, Ziffern, {@code _} und {@code -} (alles andere wird
     * {@code _}), höchstens {@link #MAX_BASE_LENGTH} Zeichen, keine reservierten Windows-Namen. Bleibt vom Namen kein
     * einziges Zeichen unverändert übrig, wird es {@link #FALLBACK_BASE}.
     */
    public static String sanitize(String name) {
        if (name == null) return FALLBACK_BASE;
        StringBuilder sb = new StringBuilder(Math.min(name.length(), MAX_BASE_LENGTH));
        boolean kept = false;
        for (int i = 0; i < name.length() && sb.length() < MAX_BASE_LENGTH; i++) {
            char c = name.charAt(i);
            boolean allowed = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            sb.append(allowed ? c : '_');
            kept |= allowed;
        }
        if (!kept) return FALLBACK_BASE;
        String base = sb.toString();
        String upper = base.toUpperCase(Locale.ROOT);
        for (String reserved : RESERVED) {
            if (upper.equals(reserved)) return "_" + base;
        }
        return base;
    }

    /** Dateiname für Versuch {@code n} (1 = ohne Nummer). */
    public static String fileName(String base, int n) {
        return n <= 1 ? base + ".png" : base + "_" + n + ".png";
    }

    /**
     * Erster freier Dateiname (für Tests und Vorabprüfung; das Speichern selbst nutzt {@code CREATE_NEW} und zählt bei
     * „existiert schon“ weiter, damit es keine Lücke zwischen Prüfen und Schreiben gibt). {@code null}, wenn alle
     * Nummern belegt sind.
     */
    public static String firstFree(String base, Predicate<String> exists) {
        for (int n = 1; n <= MAX_NUMBER; n++) {
            String candidate = fileName(base, n);
            if (!exists.test(candidate)) return candidate;
        }
        return null;
    }
}
