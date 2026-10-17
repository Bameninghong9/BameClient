package com.bame.client.gui;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Kleiner Knopf oben rechts im Esc-Menü (Weltkugel, Tooltip „Server wechseln“): öffnet die Server-Liste.
 * Der Beitritt zu einem anderen Server trennt die aktuelle Verbindung selbst (ConnectScreen.connect);
 * „Zurück“ führt wieder ins Esc-Menü, ohne die Verbindung zu verlieren.
 */
public class ServerSwitchButtonWidget extends ClickableWidget {
    public static final int SIZE = 20;
    public static final int MARGIN = 6;

    private final Screen screen;
    private float hoverProgress = 0.0f;
    private long lastFrame = 0L;

    public ServerSwitchButtonWidget(Screen screen) {
        super(screen.width - MARGIN - SIZE, MARGIN, SIZE, SIZE, Text.translatable("bameclient.switch_server"));
        this.screen = screen;
        setTooltip(Tooltip.of(getMessage()));
    }

    /** Fügt den Knopf (genau einmal) zu einem Esc-Menü hinzu; nicht im reinen „Spiel pausiert“-Overlay (F3+Esc). */
    public static void attach(Screen screen) {
        List<ClickableWidget> buttons = Screens.getButtons(screen);
        buttons.removeIf(b -> b instanceof ServerSwitchButtonWidget);
        if (buttons.isEmpty()) return;
        buttons.add(new ServerSwitchButtonWidget(screen));
    }

    @Override
    protected void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0L ? 0.0f : Math.min(0.1f, (now - lastFrame) / 1000.0f);
        lastFrame = now;
        float target = isHovered() ? 1.0f : 0.0f;
        hoverProgress += (target - hoverProgress) * Math.min(1.0f, dt * 14.0f);

        int x = getX();
        int y = getY();
        int accent = GuiTheme.accent();
        int base = GuiTheme.alpha(GuiTheme.surface(), 0xD9);
        int bg = GuiTheme.blend(base, 0xFF2A2E3A, hoverProgress);
        int border = GuiTheme.alpha(accent, (int) (0x66 + (0xFF - 0x66) * hoverProgress));
        CustomGuiUtils.fillUltraRounded(c, x, y, SIZE, SIZE, bg, 5.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, SIZE, SIZE, border, 5.0f);

        int iconColor = GuiTheme.blend(0xFFD4D8E0, accent, hoverProgress);
        CustomGuiUtils.drawIcon(c, "world", x + 3, y + 3, 14, iconColor);
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        MinecraftClient.getInstance().setScreen(new MultiplayerScreen(screen));
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
        appendDefaultNarrations(builder);
    }
}
