package com.bame.client.account;

import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.gui.GuiTheme;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.session.Session;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

/**
 * Knopf oben links im Multiplayer-Menü: Kopf + Name des aktuellen Accounts und ein Status-Punkt für die
 * Launcher-Bridge. Ein Klick öffnet den {@link AccountSwitcherScreen}.
 */
public class AccountButtonWidget extends ClickableWidget {
    public static final int X = 6;
    public static final int Y = 6;
    public static final int HEIGHT = 20;
    public static final int MAX_WIDTH = 150;
    private static final int MIN_WIDTH = 30;

    private final Screen screen;
    private final int maxWidth;
    private float hoverProgress = 0.0f;
    private long lastFrame = 0L;

    public AccountButtonWidget(Screen screen, int maxWidth) {
        super(X, Y, MIN_WIDTH, HEIGHT, Text.literal("Account"));
        this.screen = screen;
        this.maxWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, maxWidth));
    }

    /** Fügt den Knopf (genau einmal) zu einem Multiplayer-Menü hinzu. */
    public static void attach(Screen screen) {
        List<ClickableWidget> buttons = Screens.getButtons(screen);
        buttons.removeIf(b -> b instanceof AccountButtonWidget);

        // Der Titel steht mittig in der Kopfzeile: links davon bleibt (Breite - Titelbreite) / 2 Platz.
        int titleWidth = screen.getTextRenderer().getWidth(screen.getTitle());
        int free = (screen.width - titleWidth) / 2 - X - 6;
        buttons.add(new AccountButtonWidget(screen, Math.min(MAX_WIDTH, free)));

        LauncherBridge.refreshReachability();
    }

    private static String currentName() {
        Session s = MinecraftClient.getInstance().getSession();
        return s != null && s.getUsername() != null ? s.getUsername() : "Spieler";
    }

    private static UUID currentUuid() {
        Session s = MinecraftClient.getInstance().getSession();
        return s != null ? s.getUuidOrNull() : null;
    }

    @Override
    protected void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;

        long now = System.currentTimeMillis();
        float dt = lastFrame == 0L ? 0.0f : Math.min(0.1f, (now - lastFrame) / 1000.0f);
        lastFrame = now;
        float target = isHovered() ? 1.0f : 0.0f;
        hoverProgress += (target - hoverProgress) * Math.min(1.0f, dt * 14.0f);

        // Breite nach Inhalt: 4 Rand + 12 Kopf + 5 + Name + 6 + 4 Punkt + 5 Rand
        String name = currentName();
        int fixed = 4 + 12 + 5 + 6 + 4 + 5;
        int textSpace = maxWidth - fixed;
        String shown = name;
        if (tr.getWidth(CustomGuiUtils.getFontText(shown)) > textSpace) {
            shown = textSpace > 8
                    ? tr.trimToWidth(CustomGuiUtils.getFontText(name), Math.max(0, textSpace - tr.getWidth(CustomGuiUtils.getFontText("…")))).getString() + "…"
                    : "";
        }
        Text label = CustomGuiUtils.getFontText(shown);
        int textW = shown.isEmpty() ? 0 : tr.getWidth(label);
        setWidth(Math.max(MIN_WIDTH, Math.min(maxWidth, fixed + textW)));

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int accent = GuiTheme.accent();

        int base = GuiTheme.alpha(GuiTheme.surface(), 0xD9);
        int bg = GuiTheme.blend(base, 0xFF2A2E3A, hoverProgress);
        int border = GuiTheme.alpha(accent, (int) (0x66 + (0xFF - 0x66) * hoverProgress));
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, bg, 5.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, border, 5.0f);

        AccountHeads.draw(c, currentUuid(), x + 4, y + 4, 12, 0xFFFFFFFF);

        if (textW > 0) {
            int textColor = GuiTheme.blend(0xFFD4D8E0, 0xFFFFFFFF, hoverProgress);
            c.drawText(tr, label, x + 21, y + (h - 8) / 2, textColor, false);
        }

        Boolean reachable = LauncherBridge.getLastReachable();
        int dotColor = Boolean.TRUE.equals(reachable) ? 0xFF4ADE80 : 0xFF6B7280;
        CustomGuiUtils.fillUltraRounded(c, x + w - 9, y + (h - 4) / 2, 4, 4, dotColor, 2.0f);

        if (isHovered()) {
            setMessage(Text.literal("Account: " + name));
        }
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new AccountSwitcherScreen(screen, getX(), getY(), getWidth(), getHeight()));
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        try {
            com.bame.client.sound.ClientSoundManager.playClick();
        } catch (Throwable t) {
            super.playDownSound(soundManager);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        setMessage(Text.literal("Account: " + currentName()));
        appendDefaultNarrations(builder);
    }
}
