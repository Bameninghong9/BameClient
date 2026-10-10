package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import com.bame.client.emotes.EmotePreviewRenderer;
import com.bame.client.emotes.EmoteType;
import com.bame.client.module.EmoteModule;
import com.bame.client.sound.ClientSoundManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class EmoteWheelScreen extends Screen {

    private static final int PAGE_COUNT = 5;
    private static final int SLOTS_PER_PAGE = 8;
    private static int currentPage = 0;

    // 5 pages x 8 slots
    private static final EmoteType[][] PAGES = new EmoteType[PAGE_COUNT][SLOTS_PER_PAGE];

    static {
        // Page 1: Active Emotes (Slot 4 at the bottom is T-Pose, Slot 5 is JUGGLE)
        PAGES[0][0] = EmoteType.WAVE;      // Top
        PAGES[0][1] = EmoteType.CLAP;      // Top-Right
        PAGES[0][2] = EmoteType.DANCE;     // Right
        PAGES[0][3] = EmoteType.SIT;       // Bottom-Right
        PAGES[0][4] = EmoteType.T_POSE;    // Bottom (TPOSE)
        PAGES[0][5] = EmoteType.JUGGLE;    // Bottom-Left (JUGGLE ⚽)
        PAGES[0][6] = EmoteType.HEADSPIN;  // Left
        PAGES[0][7] = EmoteType.STOP;      // Top-Left

        // Page 2:
        PAGES[1][0] = EmoteType.ZOMBIE;
        PAGES[1][1] = null;
        PAGES[1][2] = null;
        PAGES[1][3] = null;
        PAGES[1][4] = null;
        PAGES[1][5] = null;
        PAGES[1][6] = null;
        PAGES[1][7] = null;
    }

    private final long openTime;
    private int hoveredSlot = -1;

    public EmoteWheelScreen() {
        super(Text.literal("Caeser Client Emote Wheel"));
        this.openTime = System.currentTimeMillis();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Semi-transparent ambient background
        context.fill(0, 0, width, height, 0x6606080E);

        int cx = width / 2;
        int cy = height / 2;
        int accent = BameClientConfig.customMainColor ? BameClientConfig.mainColor : GuiTheme.accent();
        float animTime = (System.currentTimeMillis() - openTime) / 1000.0F;

        float radius = 96.0F;
        int slotSize = 52;
        hoveredSlot = -1;

        // Determine angle-based or coordinate-based hovered slot
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double distFromCenter = Math.sqrt(dx * dx + dy * dy);

        // Pre-check direct box hover
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            double angle = -Math.PI / 2.0 + (i * 2.0 * Math.PI / SLOTS_PER_PAGE);
            int sx = (int) Math.round(cx + Math.cos(angle) * radius);
            int sy = (int) Math.round(cy + Math.sin(angle) * radius);
            int x1 = sx - slotSize / 2;
            int y1 = sy - slotSize / 2;
            int x2 = x1 + slotSize;
            int y2 = y1 + slotSize;

            if (mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2) {
                hoveredSlot = i;
                break;
            }
        }

        // Angle-based hover fallback if moving outward from center
        if (hoveredSlot == -1 && distFromCenter > 38.0 && distFromCenter < 145.0) {
            double angle = Math.atan2(dy, dx) + Math.PI / 2.0;
            if (angle < 0) angle += 2.0 * Math.PI;
            double seg = (2.0 * Math.PI) / SLOTS_PER_PAGE;
            hoveredSlot = (int) Math.round(angle / seg) % SLOTS_PER_PAGE;
        }

        EmoteType hoveredEmote = (hoveredSlot >= 0 && hoveredSlot < SLOTS_PER_PAGE) ? PAGES[currentPage][hoveredSlot] : null;

        // Render Center 3D Player Preview
        if (client != null && client.player != null) {
            EmoteType centerEmote = hoveredEmote != null ? hoveredEmote : (EmoteModule.isEmoting() ? EmoteModule.getCurrentEmote() : null);
            EmotePreviewRenderer.drawPlayerPreview(
                    context,
                    cx - 24, cy - 35,
                    cx + 24, cy + 25,
                    32,
                    mouseX, mouseY,
                    client.player,
                    centerEmote,
                    animTime
            );
        }

        // Center Page Indicator below player: "< 1 / 5 >"
        String pageStr = "<  " + (currentPage + 1) + " / " + PAGE_COUNT + "  >";
        int pageW = textRenderer.getWidth(CustomGuiUtils.getFontText(pageStr));
        boolean pageHovered = mouseX >= cx - pageW / 2 - 4 && mouseX <= cx + pageW / 2 + 4 && mouseY >= cy + 30 && mouseY <= cy + 42;
        context.drawTextWithShadow(
                textRenderer,
                CustomGuiUtils.getFontText(pageStr),
                cx - pageW / 2,
                cy + 32,
                pageHovered ? accent : 0xFFFFFFFF
        );

        // Render the 8 Circular Slots
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            double angle = -Math.PI / 2.0 + (i * 2.0 * Math.PI / SLOTS_PER_PAGE);
            int sx = (int) Math.round(cx + Math.cos(angle) * radius);
            int sy = (int) Math.round(cy + Math.sin(angle) * radius);
            int x1 = sx - slotSize / 2;
            int y1 = sy - slotSize / 2;
            int x2 = x1 + slotSize;
            int y2 = y1 + slotSize;

            boolean isHovered = (i == hoveredSlot);
            EmoteType emote = PAGES[currentPage][i];

            // Slot Box
            int bgCol = isHovered ? 0x99222736 : 0x750E1018;
            int borderCol = isHovered ? (accent | 0xFF000000) : 0x55FFFFFF;

            CustomGuiUtils.fillUltraRounded(context, x1, y1, slotSize, slotSize, bgCol, 6.0F);
            CustomGuiUtils.drawUltraRoundedOutline(context, x1, y1, slotSize, slotSize, borderCol, 6.0F);

            // Render live 3D player preview inside slot
            if (emote != null && client != null && client.player != null) {
                EmotePreviewRenderer.drawPlayerPreview(
                        context,
                        x1 + 3, y1 + 3,
                        x2 - 3, y2 - 3,
                        21,
                        sx, sy, // Look forward
                        client.player,
                        emote,
                        animTime
                );

                // Render emote label in uppercase centered below the slot box
                String label = emote.getNameEn().toUpperCase(Locale.ROOT);
                int labelW = textRenderer.getWidth(CustomGuiUtils.getFontText(label));
                int labelCol = isHovered ? accent : 0xFFFFFFFF;
                if (emote == EmoteType.STOP && isHovered) {
                    labelCol = 0xFFF87171;
                }
                context.drawTextWithShadow(
                        textRenderer,
                        CustomGuiUtils.getFontText(label),
                        sx - labelW / 2,
                        y2 + 4,
                        labelCol
                );
            }
        }

        // Subtitle / Controls guide at the bottom
        String hint = "Taste 1-8 • Klick zum Ausführen • Mausrad für Seiten (1/5) • ESC zum Schließen";
        int hintW = textRenderer.getWidth(CustomGuiUtils.getFontText(hint));
        context.drawTextWithShadow(textRenderer, CustomGuiUtils.getFontText(hint), cx - hintW / 2, height - 24, 0x889CA3AF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount > 0) {
            prevPage();
            return true;
        } else if (verticalAmount < 0) {
            nextPage();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        int cx = width / 2;
        int cy = height / 2;

        // Check page indicator click
        String pageStr = "<  " + (currentPage + 1) + " / " + PAGE_COUNT + "  >";
        int pageW = textRenderer.getWidth(CustomGuiUtils.getFontText(pageStr));
        if (click.y() >= cy + 28 && click.y() <= cy + 44) {
            if (click.x() >= cx - pageW / 2 - 8 && click.x() <= cx - 10) {
                prevPage();
                return true;
            } else if (click.x() >= cx + 10 && click.x() <= cx + pageW / 2 + 8) {
                nextPage();
                return true;
            }
        }

        // Check slot click
        if (hoveredSlot >= 0 && hoveredSlot < SLOTS_PER_PAGE) {
            EmoteType emote = PAGES[currentPage][hoveredSlot];
            if (emote != null) {
                executeSelectedEmote(emote);
                return true;
            }
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        int key = input.key();
        if (key == EmoteModule.keyBind || key == GLFW.GLFW_KEY_B) {
            if (hoveredSlot >= 0 && hoveredSlot < SLOTS_PER_PAGE) {
                EmoteType emote = PAGES[currentPage][hoveredSlot];
                if (emote != null) {
                    executeSelectedEmote(emote);
                    return true;
                }
            }
            close();
            return true;
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        // Number keys 1-8 for instant slot trigger
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_8) {
            int slot = key - GLFW.GLFW_KEY_1;
            EmoteType emote = PAGES[currentPage][slot];
            if (emote != null) {
                executeSelectedEmote(emote);
                return true;
            }
        }

        // Left / Right arrow keys for page turning
        if (key == GLFW.GLFW_KEY_LEFT) {
            prevPage();
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            nextPage();
            return true;
        }

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            if (hoveredSlot >= 0 && hoveredSlot < SLOTS_PER_PAGE) {
                EmoteType emote = PAGES[currentPage][hoveredSlot];
                if (emote != null) {
                    executeSelectedEmote(emote);
                    return true;
                }
            }
        }
        return super.keyPressed(input);
    }

    private void prevPage() {
        currentPage = (currentPage - 1 + PAGE_COUNT) % PAGE_COUNT;
        ClientSoundManager.playClick();
    }

    private void nextPage() {
        currentPage = (currentPage + 1) % PAGE_COUNT;
        ClientSoundManager.playClick();
    }

    private void executeSelectedEmote(EmoteType emote) {
        ClientSoundManager.playClick();
        if (emote == EmoteType.STOP) {
            EmoteModule.stopEmote();
        } else {
            EmoteModule.playEmote(emote);
        }
        close();
    }
}
