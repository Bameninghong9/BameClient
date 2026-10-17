package com.bame.client.playerwheel;

import com.bame.client.BameClientConfig;
import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.gui.GuiTheme;
import com.bame.client.mixin.ChatHudAccessor;
import com.bame.client.render.SpotifyHudRenderer;
import com.bame.client.sound.ClientSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.network.message.ChatVisibility;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Spieler-Auswahlrad bei offenem Chat (eingehängt in {@code ChatScreenMixin}: Zeichnen am Ende von
 * {@code ChatScreen.render}, Klicks und Esc vor dem Chat).
 *
 * <p>Ablauf: Steht der Mauszeiger in der Welt über einem anderen Spieler (nicht über Chat-Text, Eingabezeile,
 * Vorschlagsliste oder den Spotify-Knöpfen), wird er mit Ecken und Namen in Akzentfarbe markiert ({@link PlayerPicker}).
 * Nach kurzem Verweilen – oder sofort per Linksklick – öffnet das Rad um den Mauszeiger (gleiche Optik wie das
 * Emote-Rad: abgerundete Kästchen, Beschriftung in Großbuchstaben, Akzent beim Überfahren; kurzes Aufziehen). In der
 * Mitte stehen Kopf und Name, außen die Aktionen aus {@link PlayerWheelAction#ALL}. Klick auf ein Segment führt die
 * Aktion aus; Klick daneben, Maus weg oder Esc schließen das Rad. Danach öffnet es für denselben Spieler erst wieder,
 * wenn die Maus ihn einmal verlassen hat (oder per Klick).
 *
 * <p>Nur Render-Thread. Picking höchstens alle {@value #PICK_INTERVAL_MS} ms, solange die Maus stillsteht.
 */
public final class PlayerWheel {
    private PlayerWheel() {}

    public static final String PROFILER_SECTION = "caeser_player_wheel";
    /** Verweildauer über einem Spieler, bis das Rad von selbst öffnet. */
    private static final long DWELL_MS = 300L;
    private static final long OPEN_ANIM_MS = 140L;
    private static final long PICK_INTERVAL_MS = 33L;
    /** Chat-Eingabezeile ({@code ChatScreen}: Hintergrund ab Höhe - 14). */
    private static final int CHAT_INPUT_HEIGHT = 14;
    /** Unten frei lassen (Eingabezeile plus Abstand), wenn das Rad positioniert wird. */
    private static final int BOTTOM_RESERVE = 18;
    private static final int HEAD = 20;
    private static final int PLATE_HALF_H = 18;

    // Farben wie im Emote-Rad
    private static final int SLOT_BG = 0x7510131B;
    private static final int SLOT_BG_HOVER = 0x99242938;
    private static final int SLOT_BORDER = 0x554B5568;
    private static final int PLATE_BG = 0x99101319;
    private static final int BACKDROP = 0x5506080E;

    private static final WheelLayout LAYOUT = new WheelLayout();
    private static final double[] RECT = new double[4];

    // Markierung (Rad zu)
    private static AbstractClientPlayerEntity hovered;
    private static long hoverSinceMs;
    /** Entity-ID, für die das Rad nach dem Schließen nicht von selbst wieder öffnet, bis die Maus sie verlässt. */
    private static int suppressedId = Integer.MIN_VALUE;
    private static double lastPickX = Double.NaN;
    private static double lastPickY = Double.NaN;
    private static long lastPickMs;

    // Rad
    private static boolean open;
    private static AbstractClientPlayerEntity target;
    private static String targetName = "";
    private static double centerX;
    private static double centerY;
    private static long openedAtMs;
    private static int plateHalfW;

    public static boolean isOpen() {
        return open;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Einhängepunkte (ChatScreenMixin)
    // ------------------------------------------------------------------------------------------------------------

    /** Ende von {@code ChatScreen.render}: Markierung aktualisieren, ggf. öffnen, zeichnen. */
    public static void render(ChatScreen screen, DrawContext context, int mouseX, int mouseY, boolean suggestionsOpen) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            reset();
            return;
        }
        Profiler profiler = Profilers.get();
        profiler.push(PROFILER_SECTION);
        try {
            long now = Util.getMeasuringTimeMs();
            if (open && !targetStillThere(client)) close(false);
            if (open && LAYOUT.isAway(mouseX, mouseY, centerX, centerY)) close(true);
            if (!open) {
                updateHover(client, overChatUi(client, screen, mouseX, mouseY, suggestionsOpen), now);
                if (hovered != null && hovered.getId() != suppressedId && now - hoverSinceMs >= DWELL_MS) {
                    openFor(client, hovered, mouseX, mouseY, screen.width, screen.height, now);
                }
            }
            if (open) {
                drawHighlight(context, client, target, false);
                drawWheel(context, client, mouseX, mouseY, now);
            } else if (hovered != null) {
                drawHighlight(context, client, hovered, true);
            }
        } finally {
            profiler.pop();
        }
    }

    /**
     * Anfang von {@code ChatScreen.mouseClicked}. {@code true} = Klick verbraucht (geht nicht an den Chat). Ist kein Rad
     * offen und kein Spieler markiert, oder liegt der Klick auf dem Chat, geht er unverändert an den Chat (Links, Text).
     */
    public static boolean mouseClicked(ChatScreen screen, double mx, double my, int button, boolean suggestionsOpen) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return false;
        if (open) {
            int seg = LAYOUT.segmentAt(mx, my, centerX, centerY);
            List<PlayerWheelAction> actions = PlayerWheelAction.ALL;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && seg >= 0 && seg < actions.size()) {
                AbstractClientPlayerEntity chosen = targetStillThere(client) ? target : null;
                close(true);
                ClientSoundManager.playClick();
                if (chosen != null) actions.get(seg).action().accept(chosen);
                return true;
            }
            boolean onWheel = seg >= 0 || LAYOUT.onPlate(mx, my, centerX, centerY);
            close(true);
            // Klick daneben: Rad zu; liegt er auf dem Chat, macht der Chat normal weiter
            return onWheel || !overChatUi(client, screen, mx, my, suggestionsOpen);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hovered != null && PlayerPicker.isPickable(hovered, client.player)
                && !overChatUi(client, screen, mx, my, suggestionsOpen)) {
            openFor(client, hovered, mx, my, screen.width, screen.height, Util.getMeasuringTimeMs());
            return true;
        }
        return false;
    }

    /** Anfang von {@code ChatScreen.keyPressed}: Esc schließt zuerst das Rad (nicht den Chat). */
    public static boolean keyPressed(int key) {
        if (open && key == GLFW.GLFW_KEY_ESCAPE) {
            close(true);
            return true;
        }
        return false;
    }

    /** Chat geschlossen ({@code ChatScreen.removed}) oder Welt weg: alles vergessen (hält keine Spieler fest). */
    public static void reset() {
        open = false;
        target = null;
        hovered = null;
        suppressedId = Integer.MIN_VALUE;
        lastPickX = Double.NaN;
        lastPickY = Double.NaN;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Zustand
    // ------------------------------------------------------------------------------------------------------------

    private static void updateHover(MinecraftClient client, boolean blocked, long now) {
        if (blocked) {
            setHovered(null, now);
            return;
        }
        double mx = client.mouse.getX();
        double my = client.mouse.getY();
        boolean stale = hovered != null && !PlayerPicker.isPickable(hovered, client.player);
        if (!stale && mx == lastPickX && my == lastPickY && now - lastPickMs < PICK_INTERVAL_MS) return;
        lastPickX = mx;
        lastPickY = my;
        lastPickMs = now;
        setHovered(PlayerPicker.pick(client, mx, my), now);
    }

    private static void setHovered(AbstractClientPlayerEntity p, long now) {
        if (p != hovered) {
            hovered = p;
            hoverSinceMs = now;
        }
        if (p == null || p.getId() != suppressedId) suppressedId = Integer.MIN_VALUE;
    }

    private static void openFor(MinecraftClient client, AbstractClientPlayerEntity p, double mx, double my, int screenW, int screenH, long now) {
        target = p;
        targetName = p.getGameProfile().name();
        hovered = null;
        int nameW = client.textRenderer.getWidth(CustomGuiUtils.getFontText(targetName));
        plateHalfW = Math.max(HEAD / 2 + 10, nameW / 2 + 8);
        LAYOUT.compute(PlayerWheelAction.ALL.size(), plateHalfW, PLATE_HALF_H);
        centerX = LAYOUT.clampCenterX(mx, screenW);
        centerY = LAYOUT.clampCenterY(my, screenH, BOTTOM_RESERVE);
        openedAtMs = now;
        open = true;
    }

    private static void close(boolean suppress) {
        if (suppress && target != null) suppressedId = target.getId();
        open = false;
        target = null;
    }

    private static boolean targetStillThere(MinecraftClient client) {
        return target != null && client.world != null && client.world.getEntityById(target.getId()) == target
                && PlayerPicker.isPickable(target, client.player);
    }

    /**
     * Liegt der Punkt (GUI-Pixel) auf einem Element des Chat-Bildschirms? Eingabezeile, sichtbare Chat-Zeilen,
     * offene Vorschlagsliste (dann überall) und die Spotify-Steuerknöpfe.
     */
    static boolean overChatUi(MinecraftClient client, ChatScreen screen, double mx, double my, boolean suggestionsOpen) {
        if (suggestionsOpen) return true;
        if (my >= screen.height - CHAT_INPUT_HEIGHT) return true;
        if (SpotifyHudRenderer.buttonAt(mx, my) >= 0) return true;
        return overChatLines(client, screen.height, mx, my);
    }

    /** Bereich der sichtbaren Chat-Zeilen (gleiche Rechnung wie {@code ChatHud.render} bei offenem Chat). */
    private static boolean overChatLines(MinecraftClient client, int screenH, double mx, double my) {
        GameOptions options = client.options;
        if (options.getChatVisibility().getValue() == ChatVisibility.HIDDEN) return false;
        ChatHud hud = client.inGameHud.getChatHud();
        ChatHudAccessor access = (ChatHudAccessor) hud;
        int total = access.caeser$getVisibleMessages().size();
        if (total <= 0) return false;
        int lines = Math.min(total - access.caeser$getScrolledLines(), hud.getVisibleLineCount());
        if (lines <= 0) return false;
        double scale = options.getChatScale().getValue();
        if (scale <= 0) return false;
        int width = MathHelper.ceil(ChatHud.getWidth(options.getChatWidth().getValue()) / scale);
        int bottom = MathHelper.floor((screenH - 40) / scale);
        int lineH = (int) (9.0 * (options.getChatLineSpacing().getValue() + 1.0));
        double left = 0.0;
        double right = (width + 12) * scale;
        double top = (bottom - lines * lineH) * scale;
        double bottomY = (bottom + 9) * scale; // inklusive Zeile „Nachrichten in der Warteschlange“
        return mx >= left - 2 && mx <= right + 2 && my >= top - 2 && my <= bottomY + 2;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Zeichnen
    // ------------------------------------------------------------------------------------------------------------

    private static int fade(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Ecken um den Spieler (und ohne offenes Rad der Name darunter) in Akzentfarbe. */
    private static void drawHighlight(DrawContext context, MinecraftClient client, AbstractClientPlayerEntity p, boolean withName) {
        if (p == null || !PlayerPicker.screenRect(client, p, RECT)) return;
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();
        int x0 = (int) Math.floor(RECT[0]) - 2;
        int y0 = (int) Math.floor(RECT[1]) - 2;
        int x1 = (int) Math.ceil(RECT[2]) + 2;
        int y1 = (int) Math.ceil(RECT[3]) + 2;
        if (x1 <= 0 || y1 <= 0 || x0 >= sw || y0 >= sh) return;
        int w = x1 - x0;
        int h = y1 - y0;
        if (w < 4 || h < 4 || w > sw * 4 || h > sh * 4) return;
        int accent = 0xFF000000 | GuiTheme.accent();
        int line = (accent & 0x00FFFFFF) | 0xD0000000;
        int len = Math.max(3, Math.min(10, Math.min(w, h) / 4));
        context.fill(x0, y0, x0 + len, y0 + 1, line);
        context.fill(x0, y0 + 1, x0 + 1, y0 + len, line);
        context.fill(x1 - len, y0, x1, y0 + 1, line);
        context.fill(x1 - 1, y0 + 1, x1, y0 + len, line);
        context.fill(x0, y1 - 1, x0 + len, y1, line);
        context.fill(x0, y1 - len, x0 + 1, y1 - 1, line);
        context.fill(x1 - len, y1 - 1, x1, y1, line);
        context.fill(x1 - 1, y1 - len, x1, y1 - 1, line);
        if (!withName) return;

        TextRenderer tr = client.textRenderer;
        Text name = CustomGuiUtils.getFontText(p.getGameProfile().name());
        int pw = tr.getWidth(name) + 10;
        int ph = 13;
        int px = (x0 + x1) / 2 - pw / 2;
        int py = y1 + 3; // unter den Füßen: das Vanilla-Namensschild über dem Kopf bleibt frei
        if (py + ph > sh - BOTTOM_RESERVE) py = y0 - ph - 3;
        px = Math.max(2, Math.min(sw - pw - 2, px));
        py = Math.max(2, Math.min(sh - BOTTOM_RESERVE - ph, py));
        CustomGuiUtils.fillUltraRounded(context, px, py, pw, ph, 0xC80D1117, 4);
        CustomGuiUtils.drawUltraRoundedOutline(context, px, py, pw, ph, line, 4);
        context.drawText(tr, name, px + 5, py + 3, accent, false);
    }

    private static void drawWheel(DrawContext context, MinecraftClient client, int mouseX, int mouseY, long now) {
        if (target == null) return;
        float t = Math.min(1f, Math.max(0f, (now - openedAtMs) / (float) OPEN_ANIM_MS));
        float eased = 1f - (1f - t) * (1f - t) * (1f - t);
        float scale = 0.85f + 0.15f * eased;
        int cx = (int) Math.round(centerX);
        int cy = (int) Math.round(centerY);
        int accent = 0xFF000000 | (BameClientConfig.customMainColor ? BameClientConfig.mainColor : GuiTheme.accent());
        TextRenderer tr = client.textRenderer;
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate((float) cx, (float) cy);
            matrices.scale(scale, scale);
            matrices.translate((float) -cx, (float) -cy);

            // Dezenter Hintergrund nur um das Rad (das Emote-Rad dunkelt den ganzen Bildschirm ab)
            int bx = cx - LAYOUT.extentLeft() - 8;
            int by = cy - LAYOUT.extentTop() - 8;
            int bw = LAYOUT.extentLeft() + LAYOUT.extentRight() + 16;
            int bh = LAYOUT.extentTop() + LAYOUT.extentBottom() + 16;
            CustomGuiUtils.fillUltraRounded(context, bx, by, bw, bh, fade(BACKDROP, eased), 10f);

            // Mitte: Kopf und Name
            int plateW = plateHalfW * 2;
            int plateH = PLATE_HALF_H * 2;
            CustomGuiUtils.fillUltraRounded(context, cx - plateHalfW, cy - PLATE_HALF_H, plateW, plateH, fade(PLATE_BG, eased), 6f);
            CustomGuiUtils.drawUltraRoundedOutline(context, cx - plateHalfW, cy - PLATE_HALF_H, plateW, plateH, fade(SLOT_BORDER, eased), 6f);
            PlayerSkinDrawer.draw(context, target.getSkin(), cx - HEAD / 2, cy - PLATE_HALF_H + 3, HEAD, fade(0xFFFFFFFF, eased));
            Text name = CustomGuiUtils.getFontText(targetName);
            context.drawTextWithShadow(tr, name, cx - tr.getWidth(name) / 2, cy + PLATE_HALF_H - 11, fade(0xFFFFFFFF, eased));

            // Segmente
            int hoveredSeg = LAYOUT.segmentAt(mouseX, mouseY, centerX, centerY);
            List<PlayerWheelAction> actions = PlayerWheelAction.ALL;
            for (int i = 0; i < actions.size(); i++) {
                PlayerWheelAction action = actions.get(i);
                int x = LAYOUT.slotX(centerX, i);
                int y = LAYOUT.slotY(centerY, i);
                boolean hover = i == hoveredSeg;
                CustomGuiUtils.fillUltraRounded(context, x, y, WheelLayout.SLOT, WheelLayout.SLOT, fade(hover ? SLOT_BG_HOVER : SLOT_BG, eased), 4f);
                CustomGuiUtils.drawUltraRoundedOutline(context, x, y, WheelLayout.SLOT, WheelLayout.SLOT, fade(hover ? accent : SLOT_BORDER, eased), 4f);
                int iconColor = fade(hover ? accent : 0xFFFFFFFF, eased);
                action.icon().draw(context, x + WheelLayout.SLOT / 2, y + WheelLayout.SLOT / 2, iconColor);
                Text label = CustomGuiUtils.getFontText(action.label().toUpperCase(Locale.ROOT));
                int lw = tr.getWidth(label);
                context.drawTextWithShadow(tr, label, x + WheelLayout.SLOT / 2 - lw / 2, y + WheelLayout.SLOT + WheelLayout.LABEL_GAP,
                        fade(hover ? accent : 0xFFFFFFFF, eased));
            }
        } finally {
            matrices.popMatrix();
        }
    }
}
