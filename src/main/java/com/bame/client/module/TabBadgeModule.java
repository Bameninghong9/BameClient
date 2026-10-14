package com.bame.client.module;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Caeser-Badge vor dem Namen in der Tab-Liste. Es gibt nur noch ein Design: "Caeser Bold (C)" ({@code c_bold}).
 * Das Modul lässt sich nur ein- und ausschalten; ausgeschaltet wird {@code none} synchronisiert.
 */
public class TabBadgeModule {
    public static boolean enabled = true;
    public static boolean expanded = false;
    public static int keyBind = -1;

    /** Einziger Badge-Wert im Sync ({@code state.tabBadge}). Ältere gespeicherte Werte werden ignoriert. */
    public static final String BADGE_TYPE = "c_bold";
    private static final String BADGE_SYMBOL = "§6§lC §r";

    /** Eigene Schrift mit genau einem Zeichen (U+E000): das Caeser-Logo als Bild (textures/font/caeser_badge.png). */
    private static final Identifier BADGE_FONT = Identifier.of("bameclient", "badge");
    private static final Style BADGE_STYLE = Style.EMPTY.withFont(new StyleSpriteSource.Font(BADGE_FONT)).withColor(0xFFFFFF);

    /** Logo-Icon plus Leerzeichen, wird vor den Namen in der Tab-Liste gesetzt. */
    public static MutableText badgeText() {
        // Leerer Wurzel-Text: Angehängte Teile (Leerzeichen, Name) erben so nicht die Logo-Schrift.
        return Text.empty().append(Text.literal("").setStyle(BADGE_STYLE)).append(Text.literal(" "));
    }

    /** Eigenes Badge in der Tab-Liste, {@code null} wenn ausgeschaltet. */
    public static String getBadgeSymbol() {
        return enabled ? BADGE_SYMBOL : null;
    }

    /** Wert für {@code state.tabBadge}: {@code c_bold} oder {@code none}. */
    public static String syncValue() {
        return enabled ? BADGE_TYPE : "none";
    }

    /**
     * Badge eines anderen Caeser-Spielers. Jeder Wert außer {@code none} (auch alte Designs wie {@code c} oder
     * {@code crown} älterer Clients) wird einheitlich als Caeser Bold gezeigt.
     */
    public static String symbolForRemote(String remoteBadge) {
        if (remoteBadge != null && "none".equalsIgnoreCase(remoteBadge.trim())) return null;
        return BADGE_SYMBOL;
    }
}
