package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import com.bame.client.cosmetics.CosmeticsConfig;
import com.bame.client.emotes.EmotePreviewRenderer;
import com.bame.client.emotes.EmoteType;
import com.bame.client.emotes.EmoteUnlockManager;
import com.bame.client.gui.tab.CosmeticsTab;
import com.bame.client.module.EmoteModule;
import com.bame.client.notification.Notification;
import com.bame.client.notification.NotificationManager;
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

    // 5 pages x 8 slots = 40 slots
    private static final EmoteType[][] PAGES = new EmoteType[PAGE_COUNT][SLOTS_PER_PAGE];

    public static int getActiveEmoteCount() {
        int count = 0;
        for (int p = 0; p < PAGE_COUNT; p++) {
            for (int s = 0; s < SLOTS_PER_PAGE; s++) {
                if (PAGES[p][s] != null) count++;
            }
        }
        return count;
    }

    public static void rebuildPages() {
        for (int p = 0; p < PAGE_COUNT; p++) {
            for (int s = 0; s < SLOTS_PER_PAGE; s++) {
                int idx = p * SLOTS_PER_PAGE + s;
                String id = CosmeticsConfig.getEmoteAtSlot(idx);
                EmoteType type = EmoteType.fromId(id);
                if (type != null && EmoteUnlockManager.isEmoteUnlocked(type)) {
                    PAGES[p][s] = type;
                } else {
                    PAGES[p][s] = null;
                }
            }
        }
    }

    public static int getActivePageCount() {
        return PAGE_COUNT;
    }

    private final long openTime;
    private int hoveredSlot = -1;

    public EmoteWheelScreen() {
        super(Text.literal("Caeser Client Emote Wheel"));
        this.openTime = System.currentTimeMillis();
        CosmeticsConfig.checkReload();
        EmoteUnlockManager.checkRefresh();
        rebuildPages();
        if (currentPage < 0 || currentPage >= PAGE_COUNT) {
            currentPage = 0;
        }
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

        float radius = 105.0F;
        int slotSize = 54;
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

        // Angle-based hover fallback if cursor moved outward
        if (hoveredSlot == -1 && distFromCenter > 38.0 && distFromCenter < 165.0) {
            double angle = Math.atan2(dy, dx) + Math.PI / 2.0;
            if (angle < 0) angle += 2.0 * Math.PI;
            double seg = (2.0 * Math.PI) / SLOTS_PER_PAGE;
            int candidate = (int) Math.round(angle / seg) % SLOTS_PER_PAGE;
            hoveredSlot = candidate;
        }

        // Center Page Indicator matching Bild 3: "1 / 5"
        String pageStr = (currentPage + 1) + " / " + PAGE_COUNT;
        int pageW = textRenderer.getWidth(CustomGuiUtils.getFontText(pageStr));
        boolean pageHovered = mouseX >= cx - pageW / 2 - 8 && mouseX <= cx + pageW / 2 + 8
                && mouseY >= cy - 10 && mouseY <= cy + 10;
        context.drawTextWithShadow(
                textRenderer,
                CustomGuiUtils.getFontText(pageStr),
                cx - pageW / 2,
                cy - textRenderer.fontHeight / 2,
                pageHovered ? accent : 0xFFFFFFFF
        );

        // Render all 8 slots in radial circle
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            EmoteType emote = PAGES[currentPage][i];

            double angle = -Math.PI / 2.0 + (i * 2.0 * Math.PI / SLOTS_PER_PAGE);
            int sx = (int) Math.round(cx + Math.cos(angle) * radius);
            int sy = (int) Math.round(cy + Math.sin(angle) * radius);
            int x1 = sx - slotSize / 2;
            int y1 = sy - slotSize / 2;
            int x2 = x1 + slotSize;
            int y2 = y1 + slotSize;

            boolean isHovered = (i == hoveredSlot);
            int bgCol = isHovered ? 0x99242938 : 0x7510131B;
            int borderCol = isHovered ? (accent | 0xFF000000) : 0x554B5568;

            CustomGuiUtils.fillUltraRounded(context, x1, y1, slotSize, slotSize, bgCol, 4.0F);
            CustomGuiUtils.drawUltraRoundedOutline(context, x1, y1, slotSize, slotSize, borderCol, 4.0F);

            if (emote == null) {
                // Empty slot matching Bild 3 and Bild 5: Crisp white "+" symbol
                int plusCol = isHovered ? (accent | 0xFF000000) : 0xFFFFFFFF;
                int pw = 2;
                int ph = 10;
                context.fill(sx - pw / 2, sy - ph / 2, sx + pw / 2, sy + ph / 2, plusCol);
                context.fill(sx - ph / 2, sy - pw / 2, sx + ph / 2, sy + pw / 2, plusCol);
            } else {
                // Equipped slot: Live 3D player preview inside slot
                if (client != null && client.player != null) {
                    EmotePreviewRenderer.drawPlayerPreview(
                            context,
                            x1 + 3, y1 + 3,
                            x2 - 3, y2 - 3,
                            21,
                            sx, sy,
                            client.player,
                            emote,
                            animTime
                    );

                    // Render emote label in uppercase centered below the slot box
                    String label = emote.getNameEn().toUpperCase(Locale.ROOT);
                    int labelW = textRenderer.getWidth(CustomGuiUtils.getFontText(label));
                    int labelCol = (emote == EmoteType.STOP && isHovered) ? 0xFFF87171 : (isHovered ? accent : 0xFFFFFFFF);

                    context.drawTextWithShadow(
                            textRenderer,
                            CustomGuiUtils.getFontText(label),
                            sx - labelW / 2,
                            y2 + 4,
                            labelCol
                    );
                }
            }
        }

        // Subtitle / Controls guide at the bottom
        String hint = "Klick auf [+] zum Zuweisen • Klick zum Ausführen • Rechtsklick zum Entfernen • Mausrad für Seiten (" + (currentPage + 1) + "/" + PAGE_COUNT + ") • ESC";
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

        // Check center page indicator click
        String pageStr = (currentPage + 1) + " / " + PAGE_COUNT;
        int pageW = textRenderer.getWidth(CustomGuiUtils.getFontText(pageStr));
        if (click.y() >= cy - 12 && click.y() <= cy + 12) {
            if (click.x() >= cx - pageW / 2 - 12 && click.x() <= cx - 2) {
                prevPage();
                return true;
            } else if (click.x() >= cx + 2 && click.x() <= cx + pageW / 2 + 12) {
                nextPage();
                return true;
            }
        }

        // Check slot click
        if (hoveredSlot >= 0 && hoveredSlot < SLOTS_PER_PAGE) {
            int slotIdx = currentPage * SLOTS_PER_PAGE + hoveredSlot;
            EmoteType emote = PAGES[currentPage][hoveredSlot];

            if (emote == null) {
                // Empty slot clicked -> open Cosmetics Screen at Emotes tab targeting this slot
                CosmeticsTab.assigningToWheelSlot = slotIdx;
                CosmeticsTab.subCategory = "EMOTES";
                BameClientScreen.currentView = BameClientScreen.ViewMode.COSMETICS;
                if (client != null) {
                    client.setScreen(new BameClientScreen());
                }
                ClientSoundManager.playClick();
                return true;
            } else {
                // Right click or Shift-click -> Unequip / clear this slot
                if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT || click.hasShift()) {
                    CosmeticsConfig.clearSlot(slotIdx);
                    rebuildPages();
                    ClientSoundManager.playClick();
                    return true;
                }
                // Left click -> Execute emote
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
            } else {
                // Empty slot -> assign
                int slotIdx = currentPage * SLOTS_PER_PAGE + slot;
                CosmeticsTab.assigningToWheelSlot = slotIdx;
                CosmeticsTab.subCategory = "EMOTES";
                BameClientScreen.currentView = BameClientScreen.ViewMode.COSMETICS;
                if (client != null) {
                    client.setScreen(new BameClientScreen());
                }
                ClientSoundManager.playClick();
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
        if (!EmoteUnlockManager.isEmoteUnlocked(emote)) {
            if (client != null) {
                try {
                    client.getSoundManager().play(
                        net.minecraft.client.sound.PositionedSoundInstance.master(
                            net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 1.0f
                        )
                    );
                    NotificationManager.add(
                        new Notification("🔒 Gesperrt", "Du hast dieses Emote noch nicht freigeschaltet!", Notification.Type.DISABLED, 2500)
                    );
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
