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
        // Page 1: Active Emotes (8 unique emotes)
        PAGES[0][0] = EmoteType.T_POSE;      // Top (T-Pose)
        PAGES[0][1] = EmoteType.GUITAR;      // Top-Right (🎸 E-Gitarre Solo)
        PAGES[0][2] = EmoteType.DANCE;       // Right (💃 Tanz)
        PAGES[0][3] = EmoteType.WHEELCHAIR;  // Bottom-Right (♿ Rollstuhl)
        PAGES[0][4] = EmoteType.SIT;         // Bottom (Sitzen)
        PAGES[0][5] = EmoteType.JUGGLE;      // Bottom-Left (⚽ Freestyle Juggle)
        PAGES[0][6] = EmoteType.BASKETBALL;  // Left (🏀 Basketball Dribble & Spin)
        PAGES[0][7] = EmoteType.COIN_FLIP;   // Top-Left (🪙 Münzwurf)

        // Other pages empty (no duplicates, no wave, no zombie)
        for (int p = 1; p < PAGE_COUNT; p++) {
            for (int s = 0; s < SLOTS_PER_PAGE; s++) {
                PAGES[p][s] = null;
            }
        }
    }

    public static int getActivePageCount() {
        int count = 1;
        for (int p = 0; p < PAGES.length; p++) {
            for (int s = 0; s < SLOTS_PER_PAGE; s++) {
                if (PAGES[p][s] != null) {
                    count = Math.max(count, p + 1);
                    break;
                }
            }
        }
        return count;
    }

    private final long openTime;
    private int hoveredSlot = -1;

    public EmoteWheelScreen() {
        super(Text.literal("Caeser Client Emote Wheel"));
        this.openTime = System.currentTimeMillis();
        com.bame.client.emotes.EmoteUnlockManager.checkRefresh();
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

        // Center Page Indicator below player: "< 1 / 1 >"
        int totalPages = getActivePageCount();
        String pageStr = totalPages > 1 ? ("<  " + (currentPage + 1) + " / " + totalPages + "  >") : "1 / 1";
        int pageW = textRenderer.getWidth(CustomGuiUtils.getFontText(pageStr));
        boolean pageHovered = totalPages > 1 && mouseX >= cx - pageW / 2 - 4 && mouseX <= cx + pageW / 2 + 4 && mouseY >= cy + 30 && mouseY <= cy + 42;
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
            boolean isUnlocked = (emote == null || com.bame.client.emotes.EmoteUnlockManager.isEmoteUnlocked(emote));

            // Slot Box
            int bgCol;
            int borderCol;
            if (!isUnlocked) {
                bgCol = isHovered ? 0x992B151C : 0x75160E14;
                borderCol = isHovered ? 0xFFEF4444 : 0x55EF4444;
            } else {
                bgCol = isHovered ? 0x99222736 : 0x750E1018;
                borderCol = isHovered ? (accent | 0xFF000000) : 0x55FFFFFF;
            }

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

                // If locked, render sleek lock badge overlay on the top right
                if (!isUnlocked) {
                    CustomGuiUtils.fillUltraRounded(context, x2 - 16, y1 + 3, 13, 13, 0xEE111827, 3.0F);
                    CustomGuiUtils.drawUltraRoundedOutline(context, x2 - 16, y1 + 3, 13, 13, 0xAAEF4444, 3.0F);
                    context.drawTextWithShadow(textRenderer, Text.literal("🔒"), x2 - 14, y1 + 5, 0xFFFFFFFF);
                }

                // Render emote label in uppercase centered below the slot box
                String label = isUnlocked ? emote.getNameEn().toUpperCase(Locale.ROOT) : ("🔒 " + emote.getNameEn().toUpperCase(Locale.ROOT));
                int labelW = textRenderer.getWidth(CustomGuiUtils.getFontText(label));
                int labelCol;
                if (!isUnlocked) {
                    labelCol = isHovered ? 0xFFF87171 : 0xFFA1A1AA;
                } else if (emote == EmoteType.STOP && isHovered) {
                    labelCol = 0xFFF87171;
                } else {
                    labelCol = isHovered ? accent : 0xFFFFFFFF;
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

        // Hovered locked warning hint in center
        if (hoveredEmote != null && !com.bame.client.emotes.EmoteUnlockManager.isEmoteUnlocked(hoveredEmote)) {
            String lockedBanner = "§c🔒 " + hoveredEmote.getNameEn() + " ist gesperrt • Freischaltung erforderlich";
            int bannerW = textRenderer.getWidth(CustomGuiUtils.getFontText(lockedBanner));
            context.drawTextWithShadow(textRenderer, CustomGuiUtils.getFontText(lockedBanner), cx - bannerW / 2, cy + 48, 0xFFF87171);
        }

        // Subtitle / Controls guide at the bottom
        String hint = totalPages > 1
                ? "Taste 1-8 • Klick zum Ausführen • Mausrad für Seiten (" + (currentPage + 1) + "/" + totalPages + ") • ESC zum Schließen"
                : "Taste 1-8 • Klick zum Ausführen • ESC zum Schließen";
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
        int totalPages = getActivePageCount();
        if (totalPages > 1) {
            String pageStr = "<  " + (currentPage + 1) + " / " + totalPages + "  >";
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
        int max = getActivePageCount();
        if (max <= 1) return;
        currentPage = (currentPage - 1 + max) % max;
        ClientSoundManager.playClick();
    }

    private void nextPage() {
        int max = getActivePageCount();
        if (max <= 1) return;
        currentPage = (currentPage + 1) % max;
        ClientSoundManager.playClick();
    }

    private void executeSelectedEmote(EmoteType emote) {
        if (!com.bame.client.emotes.EmoteUnlockManager.isEmoteUnlocked(emote)) {
            if (client != null) {
                try {
                    client.getSoundManager().play(
                        net.minecraft.client.sound.PositionedSoundInstance.master(
                            net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 1.0f
                        )
                    );
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("§c🔒 Dieses Emote ist nicht freigeschaltet!"), true);
                    }
                } catch (Throwable ignored) {}
            }
            return;
        }

        ClientSoundManager.playClick();
        if (emote == EmoteType.STOP || emote == EmoteModule.getCurrentEmote()) {
            EmoteModule.stopEmote();
        } else {
            EmoteModule.playEmote(emote);
        }
        close();
    }
}
