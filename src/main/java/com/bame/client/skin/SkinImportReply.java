package com.bame.client.skin;

import java.util.Locale;

/**
 * Reine Texte und Antwort-Auswertung für „Skin speichern“ (keine Minecraft-Klassen, getestet in
 * {@code src/plannerTest}, Aufgabe {@code skinSaveTest}). Vertrag des Launchers für {@code POST /v1/skins/import}:
 * 201 = neu gespeichert, 200 mit {@code duplicate:true} = schon vorhanden (auch bei voller Bibliothek), Fehler als
 * {@code {error}} mit 400/401/409/413/429 (429 zusätzlich mit {@code Retry-After}).
 */
public final class SkinImportReply {
    private SkinImportReply() {}

    public static final String TOAST_TITLE = "Skin speichern";
    public static final String SAVED_DIR_DISPLAY = "config/bameclient/saved-skins";
    public static final String MSG_UNREACHABLE = "Launcher nicht erreichbar";
    public static final String MSG_FALLBACK = "Launcher nicht verbunden – Skin gespeichert unter " + SAVED_DIR_DISPLAY;
    public static final String MSG_FALLBACK_TOO_LARGE = "Skin zu groß für den Launcher – gespeichert unter " + SAVED_DIR_DISPLAY;

    public static String savedMessage(String name) {
        return "Skin von " + name + " gespeichert – im Launcher unter Skins";
    }

    public static String duplicateMessage(String name) {
        return "Skin von " + name + " ist schon in deiner Bibliothek";
    }

    public static String alreadySavedLocallyMessage(String name) {
        return "Skin von " + name + " liegt schon unter " + SAVED_DIR_DISPLAY;
    }

    public static String noMojangSkinMessage(String name) {
        return name + " hat keinen Mojang-Skin (Offline-Modus oder Standard-Skin)";
    }

    /** Ist 2xx eine Duplikat-Antwort? {@code duplicateField} = Wert aus dem JSON oder {@code null}, wenn er fehlt. */
    public static boolean isDuplicate(int status, Boolean duplicateField) {
        if (duplicateField != null) return duplicateField;
        return status == 200;
    }

    /** {@code Retry-After} in Sekunden (nur die Sekunden-Form; Datum oder Unsinn = -1), begrenzt auf 1..3600. */
    public static int retryAfterSeconds(String header) {
        if (header == null) return -1;
        String h = header.trim();
        if (h.isEmpty() || h.length() > 9) return -1;
        for (int i = 0; i < h.length(); i++) {
            if (h.charAt(i) < '0' || h.charAt(i) > '9') return -1;
        }
        long s = Long.parseLong(h);
        return (int) Math.max(1, Math.min(3600, s));
    }

    /**
     * Meldung für einen Fehler des Launchers: seine Meldung (bereinigt), sonst eine deutsche Meldung je Status. Bei 429
     * mit dem Hinweis, kurz zu warten (mit Sekunden aus {@code Retry-After}, falls vorhanden).
     */
    public static String errorMessage(int status, String launcherMessage, String retryAfter) {
        String msg = sanitize(launcherMessage);
        if (status == 429) {
            String base = msg != null ? msg : "Zu viele Anfragen an den Launcher";
            if (base.toLowerCase(Locale.ROOT).contains("wart")) return base;
            int seconds = retryAfterSeconds(retryAfter);
            return base + " – " + (seconds > 0 ? "bitte " + seconds + " s warten" : "bitte kurz warten");
        }
        if (msg != null) return msg;
        return switch (status) {
            case 400 -> "Der Launcher hat den Skin abgelehnt (ungültige Daten)";
            case 401, 403 -> "Der Launcher hat die Anfrage abgelehnt";
            case 409 -> "Der Skin konnte im Launcher nicht gespeichert werden";
            case 413 -> "Die Skin-Datei ist für den Launcher zu groß";
            default -> MSG_UNREACHABLE;
        };
    }

    /** Steuerzeichen und §-Farbcodes entfernen, auf 200 Zeichen kürzen; leer = {@code null}. */
    public static String sanitize(String msg) {
        if (msg == null) return null;
        StringBuilder sb = new StringBuilder(Math.min(msg.length(), 201));
        for (int i = 0; i < msg.length() && sb.length() <= 200; i++) {
            char c = msg.charAt(i);
            sb.append(Character.isISOControl(c) || c == '§' ? ' ' : c);
        }
        String clean = sb.toString().trim();
        if (clean.isEmpty()) return null;
        return clean.length() > 200 ? clean.substring(0, 200) + "…" : clean;
    }
}
