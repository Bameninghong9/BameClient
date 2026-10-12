package com.bame.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class CustomGuiUtils {
    public static void fillRounded(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void drawRoundedOutline(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + 1, color);
        context.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - ir(1), color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private static int ir(float r) { return (int) Math.ceil(r); }

    public static void fillSuperRounded(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 2, y, x + width - 2, y + height, color);
        context.fill(x, y + 2, x + 2, y + height - 2, color);
        context.fill(x + width - 2, y + 2, x + width, y + height - 2, color);
        context.fill(x + 1, y + 1, x + 2, y + 2, color);
        context.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
        context.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        context.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
    }

    // Anti-aliased rounded rectangles: geometry lives in RoundedRectGeometry, the corners are drawn as small
    // tinted alpha-mask textures (one per radius, for fill and outline) instead of one fill per edge pixel.
    private static final AlphaMaskTexture[] CORNER_FILL_TEXTURES = new AlphaMaskTexture[RoundedRectGeometry.MAX_RADIUS + 1];
    private static final AlphaMaskTexture[] CORNER_OUTLINE_TEXTURES = new AlphaMaskTexture[RoundedRectGeometry.MAX_RADIUS + 1];
    static {
        for (int i = 1; i <= RoundedRectGeometry.MAX_RADIUS; i++) {
            final int r = i;
            CORNER_FILL_TEXTURES[i] = new AlphaMaskTexture(Identifier.of("bameclient", "dynamic/corner_fill_" + r),
                2 * r, 2 * r, () -> RoundedRectGeometry.buildCornerAlpha(r, false));
            CORNER_OUTLINE_TEXTURES[i] = new AlphaMaskTexture(Identifier.of("bameclient", "dynamic/corner_outline_" + r),
                2 * r, 2 * r, () -> RoundedRectGeometry.buildCornerAlpha(r, true));
        }
    }

    private static final RoundedRectGeometry.Target<DrawContext> DRAW_TARGET = new RoundedRectGeometry.Target<>() {
        @Override
        public void fill(DrawContext context, int x1, int y1, int x2, int y2, int color) {
            context.fill(x1, y1, x2, y2, color);
        }

        @Override
        public void corner(DrawContext context, boolean outline, int ir, int x, int y, int u, int v, int w, int h, int color) {
            Identifier id = (outline ? CORNER_OUTLINE_TEXTURES : CORNER_FILL_TEXTURES)[ir].id();
            context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                id, x, y, u, v, w, h, 2 * ir, 2 * ir, color);
        }
    };

    public static void fillUltraRounded(DrawContext context, int x, int y, int width, int height, int color) {
        fillUltraRounded(context, x, y, width, height, color, 8.0f);
    }

    public static void fillUltraRounded(DrawContext context, int x, int y, int width, int height, int color, float radius) {
        RoundedRectGeometry.fill(DRAW_TARGET, context, x, y, width, height, color, radius);
    }

    public static void drawUltraRoundedOutline(DrawContext context, int x, int y, int width, int height, int color) {
        drawUltraRoundedOutline(context, x, y, width, height, color, 8.0f);
    }

    public static void drawUltraRoundedOutline(DrawContext context, int x, int y, int width, int height, int color, float radius) {
        RoundedRectGeometry.outline(DRAW_TARGET, context, x, y, width, height, color, radius);
    }

    public static void drawSmoothRing(DrawContext context, int cx, int cy, float r, float thickness, int color) {
        int ir = (int) Math.ceil(r + thickness);
        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        int rgb = color & 0x00FFFFFF;

        for (int y = -ir; y <= ir; y++) {
            for (int x = -ir; x <= ir; x++) {
                float dist = (float) Math.hypot(x, y);
                float distToEdge = Math.abs(dist - r);
                float alphaMult = Math.max(0.0f, Math.min(1.0f, 0.5f + (thickness / 2.0f) - distToEdge));
                if (alphaMult > 0.0f) {
                    int newA = (int) (a * alphaMult);
                    int newColor = (newA << 24) | rgb;
                    context.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, newColor);
                }
            }
        }
    }

    public static void drawSmoothLine(DrawContext context, float x1, float y1, float x2, float y2, float thickness, int color) {
        int minX = (int) Math.floor(Math.min(x1, x2) - thickness - 1);
        int maxX = (int) Math.ceil(Math.max(x1, x2) + thickness + 1);
        int minY = (int) Math.floor(Math.min(y1, y2) - thickness - 1);
        int maxY = (int) Math.ceil(Math.max(y1, y2) + thickness + 1);

        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        int rgb = color & 0x00FFFFFF;

        float l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
        if (l2 == 0) return;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float t = Math.max(0, Math.min(1, ((x - x1) * (x2 - x1) + (y - y1) * (y2 - y1)) / l2));
                float projX = x1 + t * (x2 - x1);
                float projY = y1 + t * (y2 - y1);
                float dist = (float) Math.hypot(x - projX, y - projY);

                float alphaMult = Math.max(0.0f, Math.min(1.0f, 0.5f + (thickness / 2.0f) - dist));
                if (alphaMult > 0.0f) {
                    int newA = (int) (a * alphaMult);
                    int newColor = (newA << 24) | rgb;
                    context.fill(x, y, x + 1, y + 1, newColor);
                }
            }
        }
    }

    private static final Map<String, Identifier> ICONS = Map.ofEntries(
        Map.entry("search", Identifier.of("bameclient", "textures/gui/icons/search.png")),
        Map.entry("combat", Identifier.of("bameclient", "textures/gui/icons/combat.png")),
        Map.entry("movement", Identifier.of("bameclient", "textures/gui/icons/movement.png")),
        Map.entry("visuals", Identifier.of("bameclient", "textures/gui/icons/visuals.png")),
        Map.entry("misc", Identifier.of("bameclient", "textures/gui/icons/misc.png")),
        Map.entry("world", Identifier.of("bameclient", "textures/gui/icons/world.png")),
        Map.entry("settings", Identifier.of("bameclient", "textures/gui/icons/settings.png")),
        Map.entry("reset", Identifier.of("bameclient", "textures/gui/icons/reset.png")),
        Map.entry("shirt", Identifier.of("bameclient", "textures/gui/icons/shirt.png")),
        Map.entry("bolt", Identifier.of("bameclient", "textures/gui/icons/bolt.png")),
        Map.entry("camera", Identifier.of("bameclient", "textures/gui/icons/camera.png")),
        Map.entry("hud", Identifier.of("bameclient", "textures/gui/icons/hud.png")),
        Map.entry("crown", Identifier.of("bameclient", "textures/gui/icons/crown.png")),
        Map.entry("music", Identifier.of("bameclient", "textures/gui/icons/music.png")),
        Map.entry("trash", Identifier.of("bameclient", "textures/gui/icons/trash.png")),
        Map.entry("c_logo", Identifier.of("bameclient", "textures/gui/icons/c_logo.png"))
    );

    public static void drawIcon(DrawContext context, String name, int x, int y, int size, int color) {
        drawIcon(context, name, x, y, size, size, color);
    }

    public static void drawIcon(DrawContext context, String name, int x, int y, int w, int h, int color) {
        Identifier id = ICONS.get(name);
        if (id == null) return;
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
            id, x, y, 0.0f, 0.0f, w, h, 96, 96, 96, 96, color);
    }

    public static void drawSearchIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "search", x, y, 14, 14, color); }
    public static void drawCombatIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "combat", x, y, 18, 18, color); }
    public static void drawMovementIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "movement", x, y, 18, 18, color); }
    public static void drawVisualsIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "visuals", x, y, 18, 18, color); }
    public static void drawMiscIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "misc", x, y, 18, 18, color); }
    public static void drawGlobeIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "world", x, y, 18, 18, color); }
    public static void drawGearIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "settings", x, y, 18, 18, color); }
    public static void drawResetIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "reset", x, y, 14, 14, color); }
    public static void drawShirtIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "shirt", x, y, 18, 18, color); }
    public static void drawSkinIcon(DrawContext c, int x, int y, int color) {
        fillUltraRounded(c, x + 5, y + 1, 8, 8, color, 4.0f);
        fillUltraRounded(c, x + 2, y + 10, 14, 7, color, 3.5f);
    }
    public static void drawCameraIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "camera", x, y, 18, 18, color); }
    public static void drawEmoteIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "hud", x, y, 18, 18, color); }
    public static void drawCrownIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "crown", x, y, 18, 18, color); }
    public static void drawMusicIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "music", x, y, 18, 18, color); }
    public static void drawTrashIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "trash", x, y, 14, 14, color); }

    public static void drawLockIcon(DrawContext c, int x, int y, boolean locked, int color) {
        if (locked) {
            // Closed lock shackle
            c.fill(x + 2, y, x + 7, y + 1, color);
            c.fill(x + 1, y + 1, x + 3, y + 4, color);
            c.fill(x + 6, y + 1, x + 8, y + 4, color);
            // Body
            fillUltraRounded(c, x, y + 4, 9, 7, color, 1.5f);
            // Keyhole
            c.fill(x + 4, y + 6, x + 5, y + 9, 0xFF0E111A);
        } else {
            // Open lock shackle (shackle open on right)
            c.fill(x + 2, y - 2, x + 7, y - 1, color);
            c.fill(x + 1, y - 1, x + 3, y + 4, color);
            c.fill(x + 6, y - 1, x + 8, y + 2, color);
            // Body
            fillUltraRounded(c, x, y + 4, 9, 7, color, 1.5f);
            // Keyhole
            c.fill(x + 4, y + 6, x + 5, y + 9, 0xFF0E111A);
        }
    }

    public static void drawLightningBolt(DrawContext c, int x, int y, int w, int h, int color) {
        drawIcon(c, "bolt", x, y, w, h, color);
    }

    public static void drawCheckmark(DrawContext c, int x, int y, int color) {
        drawIcon(c, "search", x, y, 12, 12, color);
    }

    public static void drawDotsIcon(DrawContext c, int x, int y, int color) {
        c.fill(x, y + 1, x + 2, y + 3, color);
        c.fill(x + 4, y + 1, x + 6, y + 3, color);
        c.fill(x + 8, y + 1, x + 10, y + 3, color);
    }

    public static void drawCLogo(DrawContext context, int x, int y, int size) {
        drawCLogo(context, x, y, size, 0xFFFFFFFF);
    }

    public static void drawCLogo(DrawContext context, int x, int y, int size, int color) {
        drawIcon(context, "c_logo", x, y, size, size, color);
    }

    public static final Style SANS_STYLE = Style.EMPTY
        .withFont(new StyleSpriteSource.Font(Identifier.of("bameclient", "sans")));
    private static final int TEXT_CACHE_SIZE = 1024;
    // Small LRU cache: frequently used labels stay, rarely used strings (e.g. changing numbers) are evicted one by one
    private static final Map<String, Text> TEXT_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Text> eldest) {
            return size() > TEXT_CACHE_SIZE;
        }
    });

    public static Text getFontText(String text) {
        if (text == null) return Text.empty();
        return TEXT_CACHE.computeIfAbsent(text, t -> Text.literal(normalizeFancyText(t)).setStyle(SANS_STYLE));
    }

    public static String normalizeFancyText(String input) {
        if (input == null || input.isEmpty()) return input;
        String normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKC);

        boolean hasSmallCaps = false;
        for (int i = 0; i < normalized.length(); i++) {
            if (isSmallCap(normalized.charAt(i))) {
                hasSmallCaps = true;
                break;
            }
        }
        if (!hasSmallCaps) return normalized;

        StringBuilder sb = new StringBuilder(normalized.length());
        int start = 0;
        int len = normalized.length();
        while (start < len) {
            int end = start;
            while (end < len && !Character.isWhitespace(normalized.charAt(end))) {
                end++;
            }
            String word = normalized.substring(start, end);
            boolean wordHasSmallCap = false;
            for (int i = 0; i < word.length(); i++) {
                if (isSmallCap(word.charAt(i))) {
                    wordHasSmallCap = true;
                    break;
                }
            }
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (isSmallCap(c)) {
                    sb.append(mapSmallCap(c));
                } else if (wordHasSmallCap && Character.isLowerCase(c)) {
                    sb.append(Character.toUpperCase(c));
                } else {
                    sb.append(c);
                }
            }
            while (end < len && Character.isWhitespace(normalized.charAt(end))) {
                sb.append(normalized.charAt(end));
                end++;
            }
            start = end;
        }
        return sb.toString();
    }

    private static boolean isSmallCap(char c) {
        return switch (c) {
            case '\u1D00', '\u1D01', '\u1D02', '\u1D03', '\u0299', '\u1D04', '\u1D05', '\u1D06',
                 '\u1D07', '\u1D08', '\uA730', '\u0262', '\u029B', '\u029C', '\u026A', '\u1D0A',
                 '\u1D0B', '\u029F', '\u1D0C', '\u1D0D', '\u0274', '\u1D0E', '\u1D0F', '\u1D10',
                 '\u1D11', '\u1D12', '\u1D13', '\u1D14', '\u0276', '\u1D18', '\uA7AF', '\u0280',
                 '\u0281', '\u1D19', '\u1D1A', '\uA731', '\u0455', '\u1D1B', '\u1D1C', '\u1D1D',
                 '\u1D1E', '\u1D20', '\u1D21', '\u1D22', '\u028F', '\u0445',
                 '\u1D26', '\u1D27', '\u1D28', '\u1D29', '\u1D2B' -> true;
            default -> false;
        };
    }

    private static String mapSmallCap(char c) {
        return switch (c) {
            case '\u1D00' -> "A";
            case '\u1D01', '\u1D02' -> "AE";
            case '\u1D03', '\u0299' -> "B";
            case '\u1D04' -> "C";
            case '\u1D05', '\u1D06' -> "D";
            case '\u1D07', '\u1D08' -> "E";
            case '\uA730' -> "F";
            case '\u0262', '\u029B', '\u1D26' -> "G";
            case '\u029C' -> "H";
            case '\u026A' -> "I";
            case '\u1D0A' -> "J";
            case '\u1D0B' -> "K";
            case '\u029F', '\u1D0C', '\u1D27' -> "L";
            case '\u1D0D' -> "M";
            case '\u0274', '\u1D0E' -> "N";
            case '\u1D0F', '\u1D10', '\u1D11', '\u1D12', '\u1D13' -> "O";
            case '\u1D14', '\u0276' -> "OE";
            case '\u1D18', '\u1D28', '\u1D29' -> "P";
            case '\uA7AF' -> "Q";
            case '\u0280', '\u0281', '\u1D19', '\u1D1A', '\u1D2B' -> "R";
            case '\uA731', '\u0455' -> "S";
            case '\u1D1B' -> "T";
            case '\u1D1C', '\u1D1D', '\u1D1E' -> "U";
            case '\u1D20' -> "V";
            case '\u1D21' -> "W";
            case '\u0445' -> "X";
            case '\u028F' -> "Y";
            case '\u1D22' -> "Z";
            default -> String.valueOf(c);
        };
    }
}
