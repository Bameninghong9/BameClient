package com.bame.client.gui.tab;

import com.bame.client.BameClientConfig;
import com.bame.client.gui.BameClientScreen;
import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.gui.GuiTheme;
import com.bame.client.module.FreelookModule;
import com.bame.client.module.InvMoveModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class MovementTab implements ClientTab {
    private boolean listeningInvMove = false;
    private boolean listeningFreelook = false;
    private boolean listeningEmote = false;
    private boolean draggingFreelookSensitivity = false;

    @Override
    public String getCategory() {
        return "Movement";
    }

    public boolean invMoveVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "invmove".contains(q) || "inv move".contains(q) || "inventory walk".contains(q) || "inventory".contains(q) || "movement".contains(q);
        return true;
    }

    public boolean freelookVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "freelook".contains(q) || "free look".contains(q) || "perspective".contains(q) || "360".contains(q) || "kamera".contains(q);
        return true;
    }

    public boolean emoteVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "emote".contains(q) || "emotes".contains(q) || "animation".contains(q) || "animations".contains(q) || "wave".contains(q) || "sit".contains(q) || "basketball".contains(q) || "wheel".contains(q) || "animator".contains(q);
        return true;
    }

    @Override
    public boolean isVisible(String search) {
        if (search.isEmpty()) return true;
        return invMoveVisible(search) || freelookVisible(search) || emoteVisible(search);
    }

    public int getInvMoveHeight() {
        return InvMoveModule.expanded ? 206 : 50;
    }

    public int getFreelookHeight() {
        return FreelookModule.expanded ? 180 : 50;
    }

    public int getEmoteHeight() {
        return com.bame.client.module.EmoteModule.expanded ? 158 : 50;
    }

    @Override
    public void layout(BameClientScreen screen, int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
    }

    @Override
    public void calculateHeight(BameClientScreen screen, int columnWidth, ColumnTracker tracker, String search) {
        if (invMoveVisible(search)) tracker.leftY += getInvMoveHeight() + 12;
        if (freelookVisible(search)) tracker.rightY += getFreelookHeight() + 12;
        if (emoteVisible(search)) {
            if (tracker.leftY <= tracker.rightY) {
                tracker.leftY += getEmoteHeight() + 12;
            } else {
                tracker.rightY += getEmoteHeight() + 12;
            }
        }
    }

    @Override
    public void render(BameClientScreen screen, DrawContext c, int mx, int my, float delta,
                       int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        if (invMoveVisible(search)) {
            renderInvMove(screen, c, mx, my, delta, leftX, tracker.leftY, columnWidth);
            tracker.leftY += getInvMoveHeight() + 12;
        }
        if (freelookVisible(search)) {
            renderFreelook(screen, c, mx, my, delta, rightX, tracker.rightY, columnWidth);
            tracker.rightY += getFreelookHeight() + 12;
        }
        if (emoteVisible(search)) {
            int targetX = (tracker.leftY <= tracker.rightY) ? leftX : rightX;
            int targetY = (tracker.leftY <= tracker.rightY) ? tracker.leftY : tracker.rightY;
            renderEmote(screen, c, mx, my, delta, targetX, targetY, columnWidth);
            if (targetX == leftX) {
                tracker.leftY += getEmoteHeight() + 12;
            } else {
                tracker.rightY += getEmoteHeight() + 12;
            }
        }
    }

    private void renderInvMove(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getInvMoveHeight();
        screen.moduleCard(c, x, y, w, h, InvMoveModule.enabled, InvMoveModule.expanded,
                "InvMove", "Move freely while inside inventories", "NEW", "movement", mx, my);

        if (!InvMoveModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningInvMove ? "..." : screen.formatKey(InvMoveModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.text(c, "Jump", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, InvMoveModule.jump, mx, my, delta);
        curY += 26;

        screen.text(c, "Sprint", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, InvMoveModule.sprint, mx, my, delta);
        curY += 26;

        screen.text(c, "Sneak", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, InvMoveModule.sneak, mx, my, delta);
        curY += 26;

        screen.text(c, "Arrow Key Rotation", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, InvMoveModule.rotateWithArrows, mx, my, delta);
        curY += 26;

        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderFreelook(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getFreelookHeight();
        screen.moduleCard(c, x, y, w, h, FreelookModule.enabled, FreelookModule.expanded,
                "Freelook", "Look around 360 without turning player", "NEW", "movement", mx, my);

        if (!FreelookModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningFreelook ? "..." : screen.formatKey(FreelookModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.text(c, "Mode", x + 14, curY + 4, 0xFFD4D8E0);
        screen.button(c, FreelookModule.toggleMode ? "Toggle" : "Hold", x + w - 58, curY, 46, 16, mx, my);
        curY += 26;

        screen.text(c, "Invert Pitch", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, FreelookModule.invertPitch, mx, my, delta);
        curY += 26;

        int sensPct = Math.round(FreelookModule.sensitivity * 100);
        screen.text(c, "Sens: " + sensPct + "%", x + 14, curY + 4, 0xFFD4D8E0);
        int sx = x + 84;
        int sw = w - 98;
        CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
        float sensNorm = (FreelookModule.sensitivity - 0.5f) / 1.5f;
        int sfill = Math.round(sw * Math.clamp(sensNorm, 0f, 1f));
        if (sfill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sfill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sx + sfill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderEmote(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getEmoteHeight();
        screen.moduleCard(c, x, y, w, h, com.bame.client.module.EmoteModule.enabled, com.bame.client.module.EmoteModule.expanded,
                "Animations & Emotes", "Emote Wheel radial menu & modern animations", "NEW", "movement", mx, my);

        if (!com.bame.client.module.EmoteModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningEmote ? "..." : screen.formatKey(com.bame.client.module.EmoteModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.text(c, "Smooth Animations", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, com.bame.client.module.EmoteModule.smoothAnimations, mx, my, delta);
        curY += 26;

        screen.text(c, "Radial Wheel", x + 14, curY + 4, 0xFFD4D8E0);
        screen.button(c, "Open Wheel", x + w - 74, curY, 62, 16, mx, my);
        curY += 26;

        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    @Override
    public boolean mouseClicked(BameClientScreen screen, Click click, boolean twice,
                                int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        double mx = click.x();
        double my = click.y();

        if (invMoveVisible(search)) {
            int y = tracker.leftY;
            int h = getInvMoveHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, leftX, y, columnWidth,
                    () -> {
                        InvMoveModule.enabled = !InvMoveModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("InvMove", InvMoveModule.enabled);
                    },
                    () -> InvMoveModule.expanded = !InvMoveModule.expanded)) {
                return true;
            }
            if (InvMoveModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, leftX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningInvMove = true;
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 2, 26, 14)) { InvMoveModule.jump = !InvMoveModule.jump; BameClientConfig.save(); return true; }
                curY += 26;
                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 2, 26, 14)) { InvMoveModule.sprint = !InvMoveModule.sprint; BameClientConfig.save(); return true; }
                curY += 26;
                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 2, 26, 14)) { InvMoveModule.sneak = !InvMoveModule.sneak; BameClientConfig.save(); return true; }
                curY += 26;
                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 2, 26, 14)) { InvMoveModule.rotateWithArrows = !InvMoveModule.rotateWithArrows; BameClientConfig.save(); return true; }
                curY += 26;
                if (screen.inside(mx, my, leftX + columnWidth - 58, curY, 46, 16)) {
                    InvMoveModule.resetToDefault();
                    BameClientConfig.save();
                    return true;
                }
            }
            tracker.leftY += h + 12;
        }

        if (freelookVisible(search)) {
            int y = tracker.rightY;
            int h = getFreelookHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, rightX, y, columnWidth,
                    () -> {
                        FreelookModule.enabled = !FreelookModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Freelook", FreelookModule.enabled);
                    },
                    () -> FreelookModule.expanded = !FreelookModule.expanded)) {
                return true;
            }
            if (FreelookModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, rightX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningFreelook = true;
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 58, curY, 46, 16)) {
                    FreelookModule.toggleMode = !FreelookModule.toggleMode;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 38, curY + 2, 26, 14)) {
                    FreelookModule.invertPitch = !FreelookModule.invertPitch;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                int sx = rightX + 84;
                int sw = columnWidth - 98;
                if (screen.inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                    draggingFreelookSensitivity = true;
                    float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                    FreelookModule.sensitivity = Math.round((0.5f + fval * 1.5f) * 100.0f) / 100.0f;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 58, curY, 46, 16)) {
                    FreelookModule.toggleMode = false;
                    FreelookModule.invertPitch = false;
                    FreelookModule.sensitivity = 1.0f;
                    FreelookModule.keyBind = GLFW.GLFW_KEY_V;
                    BameClientConfig.save();
                    return true;
                }
            }
            tracker.rightY += h + 12;
        }

        if (emoteVisible(search)) {
            int targetX = (tracker.leftY <= tracker.rightY) ? leftX : rightX;
            int y = (tracker.leftY <= tracker.rightY) ? tracker.leftY : tracker.rightY;
            int h = getEmoteHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, targetX, y, columnWidth,
                    () -> {
                        com.bame.client.module.EmoteModule.enabled = !com.bame.client.module.EmoteModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Emotes", com.bame.client.module.EmoteModule.enabled);
                    },
                    () -> com.bame.client.module.EmoteModule.expanded = !com.bame.client.module.EmoteModule.expanded)) {
                return true;
            }
            if (com.bame.client.module.EmoteModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, targetX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningEmote = true;
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, targetX + columnWidth - 38, curY + 2, 26, 14)) {
                    com.bame.client.module.EmoteModule.smoothAnimations = !com.bame.client.module.EmoteModule.smoothAnimations;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, targetX + columnWidth - 74, curY, 62, 16)) {
                    net.minecraft.client.MinecraftClient.getInstance().setScreen(new com.bame.client.gui.EmoteWheelScreen());
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, targetX + columnWidth - 58, curY, 46, 16)) {
                    com.bame.client.module.EmoteModule.keyBind = GLFW.GLFW_KEY_B;
                    com.bame.client.module.EmoteModule.smoothAnimations = true;
                    BameClientConfig.save();
                    return true;
                }
            }
            if (targetX == leftX) {
                tracker.leftY += h + 12;
            } else {
                tracker.rightY += h + 12;
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(BameClientScreen screen, Click click, double deltaX, double deltaY) {
        double mx = click.x();
        int columnWidth = screen.getColumnWidth();
        int rightX = screen.getContentRightX();

        if (draggingFreelookSensitivity) {
            int sx = rightX + 84;
            int sw = columnWidth - 98;
            float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
            FreelookModule.sensitivity = Math.round((0.5f + fval * 1.5f) * 100.0f) / 100.0f;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(BameClientScreen screen, Click click) {
        boolean released = draggingFreelookSensitivity;
        draggingFreelookSensitivity = false;
        if (released) {
            BameClientConfig.save();
        }
        return released;
    }

    @Override
    public boolean keyPressed(BameClientScreen screen, KeyInput input) {
        int key = input.key();
        if (listeningInvMove) {
            InvMoveModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningInvMove = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningFreelook) {
            FreelookModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningFreelook = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningEmote) {
            com.bame.client.module.EmoteModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningEmote = false;
            BameClientConfig.save();
            return true;
        }
        return false;
    }

    private void clearKeyListeners() {
        listeningInvMove = false;
        listeningFreelook = false;
        listeningEmote = false;
    }
}
