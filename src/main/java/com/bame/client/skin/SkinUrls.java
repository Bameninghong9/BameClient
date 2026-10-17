package com.bame.client.skin;

import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reine Prüfungen für „Skin speichern“ (keine Minecraft-Klassen, getestet in {@code src/plannerTest}, Aufgabe
 * {@code skinSaveTest}): erlaubte Skin-Adressen und die Data-URL für den Launcher.
 */
public final class SkinUrls {
    private SkinUrls() {}

    /** Einzige erlaubte Quelle. Die textures-Property von Mojang nennt oft {@code http://}; geladen wird nur per HTTPS. */
    public static final String ALLOWED_PREFIX = "https://textures.minecraft.net/texture/";

    /**
     * Genau dieser Host, Pfad {@code /texture/<hex>}, nichts sonst (kein Benutzer, kein Port, keine Query/Fragment,
     * keine weiteren Pfadteile). Groß-/Kleinschreibung des Hashs ist egal.
     */
    private static final Pattern TEXTURE_URL = Pattern.compile("^https?://textures\\.minecraft\\.net/texture/([0-9a-fA-F]{16,128})$");

    /**
     * Normalisiert eine Skin-Adresse aus dem Profil auf {@code https://textures.minecraft.net/texture/<hash>}
     * (Hash klein geschrieben) oder liefert {@code null}, wenn sie nicht erlaubt ist.
     */
    public static String normalize(String url) {
        if (url == null) return null;
        String u = url.trim();
        if (u.length() > 300) return null;
        Matcher m = TEXTURE_URL.matcher(u);
        if (!m.matches()) return null;
        return ALLOWED_PREFIX + m.group(1).toLowerCase(Locale.ROOT);
    }

    /** {@code data:image/png;base64,...} für {@code POST /v1/skins/import}. */
    public static String pngDataUrl(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    /** Länge der Data-URL in Zeichen, ohne sie zu bauen (Base64: 4 Zeichen je angefangene 3 Bytes). */
    public static long pngDataUrlLength(int pngBytes) {
        return "data:image/png;base64,".length() + 4L * ((Math.max(0, pngBytes) + 2) / 3);
    }

    /** Skin-Variante für den Launcher: {@code "slim"} nur bei Modell „slim“ in den Profil-Metadaten, sonst classic. */
    public static String variant(String model) {
        return model != null && "slim".equalsIgnoreCase(model.trim()) ? "slim" : "classic";
    }
}
