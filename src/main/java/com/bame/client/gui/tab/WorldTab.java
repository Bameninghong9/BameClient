package com.bame.client.gui.tab;

import com.bame.client.BameClientConfig;
import com.bame.client.gui.*;
import com.bame.client.module.AutoAreaMinerModule;
import com.bame.client.module.NoFogModule;
import com.bame.client.module.TimeChangerModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class WorldTab implements ClientTab {
    private boolean expanded = false;
    private boolean listening = false;
    private boolean draggingWidth = false;
    private boolean listeningNoFog = false;
    private boolean listeningTimeChanger = false;

    private CustomTextFieldWidget corner1;
    private CustomTextFieldWidget corner2;
    private final OutlineColorPicker picker = new OutlineColorPicker();

    public WorldTab() {
        corner1 = new CustomTextFieldWidget(0, 0, 100, 20, Text.literal("Corner 1"));
        corner2 = new CustomTextFieldWidget(0, 0, 100, 20, Text.literal("Corner 2"));
        corner1.setPlaceholder("X Y Z");
        corner2.setPlaceholder("X Y Z");
        corner1.setText(format(AutoAreaMinerModule.corner1));
        corner2.setText(format(AutoAreaMinerModule.corner2));
        corner1.setChangedListener(s -> {
            AutoAreaMinerModule.corner1 = parse(s);
            BameClientConfig.save();
        });
        corner2.setChangedListener(s -> {
            AutoAreaMinerModule.corner2 = parse(s);
            BameClientConfig.save();
        });
    }

    private static String format(BlockPos p) {
        return p == null ? "" : p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static BlockPos parse(String s) {
        try {
            var a = s.trim().split("\\s+");
            if (a.length == 3) return new BlockPos(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
        } catch (NumberFormatException ignored) {}
        return null;
    }

    @Override
    public String getCategory() {
        return "World";
    }

    public boolean minerVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "auto area miner".contains(q) || "miner".contains(q) || "quarry".contains(q) || "world".contains(q);
        return true;
    }

    public boolean noFogVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "nofog".contains(q) || "no fog".contains(q) || "fog".contains(q) || "nebel".contains(q);
        return true;
    }

    public boolean timeChangerVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "timechanger".contains(q) || "time changer".contains(q) || "time".contains(q) || "zeit".contains(q) || "day".contains(q) || "night".contains(q);
        return true;
    }

    @Override
    public boolean isVisible(String search) {
        if (search.isEmpty()) return true;
        return minerVisible(search) || noFogVisible(search) || timeChangerVisible(search);
    }

    public int getMinerHeight() {
        return expanded ? 412 : 50;
    }

    public int getNoFogHeight() {
        return NoFogModule.expanded ? 208 : 50;
    }

    public int getTimeChangerHeight() {
        return TimeChangerModule.expanded ? 130 : 50;
    }

    @Override
    public void layout(BameClientScreen screen, int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        int cy = screen.getCy();
        int ch = screen.getCh();
        if (minerVisible(search)) {
            corner1.setX(leftX + 12);
            corner1.setY(tracker.leftY + 90);
            corner1.setWidth(columnWidth - 24);

            corner2.setX(leftX + 12);
            corner2.setY(tracker.leftY + 130);
            corner2.setWidth(columnWidth - 24);

            corner1.visible = corner2.visible = expanded;
            corner1.active = corner1.visible && corner1.getY() + 22 > cy && corner1.getY() < cy + ch;
            corner2.active = corner2.visible && corner2.getY() + 22 > cy && corner2.getY() < cy + ch;

            if (!corner1.active) corner1.setFocused(false);
            if (!corner2.active) corner2.setFocused(false);
            if (!corner1.visible) { corner1.setFocused(false); corner2.setFocused(false); }

            picker.layout(leftX + 12, tracker.leftY + 308, columnWidth - 24);
            tracker.leftY += getMinerHeight() + 12;
        } else {
            corner1.visible = corner2.visible = false;
            corner1.active = corner2.active = false;
            corner1.setFocused(false);
            corner2.setFocused(false);
        }
        if (timeChangerVisible(search)) {
            tracker.leftY += getTimeChangerHeight() + 12;
        }
        if (noFogVisible(search)) {
            tracker.rightY += getNoFogHeight() + 12;
        }
    }

    @Override
    public void calculateHeight(BameClientScreen screen, int columnWidth, ColumnTracker tracker, String search) {
        if (minerVisible(search)) tracker.leftY += getMinerHeight() + 12;
        if (timeChangerVisible(search)) tracker.leftY += getTimeChangerHeight() + 12;
        if (noFogVisible(search)) tracker.rightY += getNoFogHeight() + 12;
    }

    @Override
    public void render(BameClientScreen screen, DrawContext c, int mx, int my, float delta,
                       int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        if (minerVisible(search)) {
            renderMiner(screen, c, mx, my, delta, leftX, tracker.leftY, columnWidth);
            tracker.leftY += getMinerHeight() + 12;
        }
        if (timeChangerVisible(search)) {
            renderTimeChanger(screen, c, mx, my, delta, leftX, tracker.leftY, columnWidth);
            tracker.leftY += getTimeChangerHeight() + 12;
        }
        if (noFogVisible(search)) {
            renderNoFog(screen, c, mx, my, delta, rightX, tracker.rightY, columnWidth);
            tracker.rightY += getNoFogHeight() + 12;
        }
    }

    private void renderMiner(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getMinerHeight();
        screen.moduleCard(c, x, y, w, h, AutoAreaMinerModule.enabled, expanded,
                "Area Miner", "Automated area block mining", "NEW", "world", mx, my);

        if (!expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 12, curY + 4, 0xFF8E95A4);
        String kb = listening ? "..." : (AutoAreaMinerModule.keyBind < 0 ? "None" : GLFW.glfwGetKeyName(AutoAreaMinerModule.keyBind, 0) != null ? GLFW.glfwGetKeyName(AutoAreaMinerModule.keyBind, 0).toUpperCase(Locale.ROOT) : "Key " + AutoAreaMinerModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);

        screen.text(c, "Corner 1", x + 12, y + 78, 0xFFABB1BE);
        corner1.render(c, mx, my, delta);

        screen.text(c, "Corner 2", x + 12, y + 118, 0xFFABB1BE);
        corner2.render(c, mx, my, delta);

        screen.text(c, "Corner 1 - Looking", x + 12, y + 164, 0xFFABB1BE);
        screen.button(c, "Set", x + w - 46, y + 160, 34, 18, mx, my);

        screen.text(c, "Corner 2 - Looking", x + 12, y + 184, 0xFFABB1BE);
        screen.button(c, "Set", x + w - 46, y + 180, 34, 18, mx, my);

        screen.button(c, AutoAreaMinerModule.mode3x3 ? "Mode: 3x3 Pickaxe" : "Mode: Normal Pickaxe", x + 12, y + 204, w - 24, 20, mx, my);

        screen.text(c, "Style", x + 12, y + 230, 0xFFD4D8E0);
        int mode = BameClientConfig.renderMode;
        int btnW = (w - 24 - 6) / 2;
        screen.modeButton(c, "Clean", x + 12, y + 244, btnW, 18, mx, my, mode == 0);
        screen.modeButton(c, "Outline", x + 12 + btnW + 6, y + 244, btnW, 18, mx, my, mode == 1);
        screen.modeButton(c, "Corners", x + 12, y + 266, btnW, 18, mx, my, mode == 2);
        screen.modeButton(c, "Pulse", x + 12 + btnW + 6, y + 266, btnW, 18, mx, my, mode == 3);

        screen.text(c, "Outline Color", x + 12, y + 292, 0xFFD4D8E0);
        int swatchX = x + w - 28;
        screen.box(c, swatchX, y + 290, 16, 12, 0xFF777777);
        screen.box(c, swatchX, y + 290, 16, 12, BameClientConfig.outlineColor);

        picker.render(c);

        int sliderY = y + 378;
        String widthStr = String.format(Locale.US, "%.1f", BameClientConfig.outlineWidth);
        screen.text(c, "Width: " + widthStr, x + 12, sliderY, 0xFFD4D8E0);
        int sw = w - 24;
        int sx = x + 12;
        CustomGuiUtils.fillUltraRounded(c, sx, sliderY + 14, sw, 4, 0xFF303442, 2);
        float widthVal = (BameClientConfig.outlineWidth - 1.0f) / 4.0f;
        int fill = Math.round(sw * Math.clamp(widthVal, 0f, 1f));
        if (fill > 0) CustomGuiUtils.fillUltraRounded(c, sx, sliderY + 14, fill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sx + fill - 3, sliderY + 12, 7, 8, 0xFFFFFFFF, 4);
    }

    private void renderTimeChanger(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getTimeChangerHeight();
        screen.moduleCard(c, x, y, w, h, TimeChangerModule.enabled, TimeChangerModule.expanded,
                "Time Changer", "Changes client-side visual world time", "NEW", "world", mx, my);

        if (!TimeChangerModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningTimeChanger ? "..." : screen.formatKey(TimeChangerModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.text(c, "Time", x + 14, curY + 4, 0xFFD4D8E0);
        screen.button(c, TimeChangerModule.getCurrentModeName(), x + w - 74, curY, 60, 16, mx, my);
        curY += 26;

        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderNoFog(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getNoFogHeight();
        screen.moduleCard(c, x, y, w, h, NoFogModule.enabled, NoFogModule.expanded,
                "No Fog", "Disables thick environmental fog", "NEW", "world", mx, my);

        if (!NoFogModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningNoFog ? "..." : screen.formatKey(NoFogModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.text(c, "All Fog (Max View)", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, NoFogModule.allFog, mx, my, delta);
        curY += 26;

        screen.text(c, "Nether Fog", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, NoFogModule.netherFog, mx, my, delta);
        curY += 26;

        screen.text(c, "Water Fog", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, NoFogModule.waterFog, mx, my, delta);
        curY += 26;

        screen.text(c, "Lava Fog", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, NoFogModule.lavaFog, mx, my, delta);
        curY += 26;

        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    @Override
    public boolean mouseClicked(BameClientScreen screen, Click click, boolean twice,
                                int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        double mx = click.x();
        double my = click.y();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (minerVisible(search)) {
            int y = tracker.leftY;
            int h = getMinerHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, leftX, y, columnWidth,
                    () -> {
                        AutoAreaMinerModule.enabled = !AutoAreaMinerModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Area Miner", AutoAreaMinerModule.enabled);
                        if (AutoAreaMinerModule.enabled && (AutoAreaMinerModule.corner1 == null || AutoAreaMinerModule.corner2 == null)) {
                            if (mc.player != null) mc.player.sendMessage(Text.literal("§c[AutoAreaMiner] Bitte zuerst Corner 1 und Corner 2 festlegen!"), false);
                            AutoAreaMinerModule.enabled = false;
                        }
                    },
                    () -> expanded = !expanded)) {
                return true;
            }
            if (expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, leftX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listening = true;
                    return true;
                }

                if (corner1.mouseClicked(click, twice)) { corner1.setFocused(true); corner2.setFocused(false); return true; }
                if (corner2.mouseClicked(click, twice)) { corner2.setFocused(true); corner1.setFocused(false); return true; }

                if (screen.inside(mx, my, leftX + columnWidth - 46, y + 160, 34, 18)) {
                    if (mc.player != null && mc.world != null) {
                        Vec3d eye = mc.player.getEyePos();
                        Vec3d look = mc.player.getRotationVec(1.0f);
                        Vec3d end = eye.add(look.multiply(mc.player.getBlockInteractionRange()));
                        var hit = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
                        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bh) {
                            AutoAreaMinerModule.corner1 = bh.getBlockPos();
                            corner1.setText(format(bh.getBlockPos()));
                            BameClientConfig.save();
                        }
                    }
                    return true;
                }

                if (screen.inside(mx, my, leftX + columnWidth - 46, y + 180, 34, 18)) {
                    if (mc.player != null && mc.world != null) {
                        Vec3d eye = mc.player.getEyePos();
                        Vec3d look = mc.player.getRotationVec(1.0f);
                        Vec3d end = eye.add(look.multiply(mc.player.getBlockInteractionRange()));
                        var hit = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
                        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bh) {
                            AutoAreaMinerModule.corner2 = bh.getBlockPos();
                            corner2.setText(format(bh.getBlockPos()));
                            BameClientConfig.save();
                        }
                    }
                    return true;
                }

                if (screen.inside(mx, my, leftX + 12, y + 204, columnWidth - 24, 20)) {
                    AutoAreaMinerModule.mode3x3 = !AutoAreaMinerModule.mode3x3;
                    BameClientConfig.save();
                    return true;
                }

                int btnW = (columnWidth - 24 - 6) / 2;
                if (screen.inside(mx, my, leftX + 12, y + 244, btnW, 18)) { BameClientConfig.renderMode = 0; BameClientConfig.save(); return true; }
                if (screen.inside(mx, my, leftX + 12 + btnW + 6, y + 244, btnW, 18)) { BameClientConfig.renderMode = 1; BameClientConfig.save(); return true; }
                if (screen.inside(mx, my, leftX + 12, y + 266, btnW, 18)) { BameClientConfig.renderMode = 2; BameClientConfig.save(); return true; }
                if (screen.inside(mx, my, leftX + 12 + btnW + 6, y + 266, btnW, 18)) { BameClientConfig.renderMode = 3; BameClientConfig.save(); return true; }

                if (picker.click(mx, my)) {
                    return true;
                }

                int sliderY = y + 378;
                int sx = leftX + 12;
                int sw = columnWidth - 24;
                if (screen.inside(mx, my, sx - 4, sliderY + 12, sw + 8, 14)) {
                    draggingWidth = true;
                    float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                    BameClientConfig.outlineWidth = Math.round((1.0f + fval * 4.0f) * 10.0f) / 10.0f;
                    BameClientConfig.save();
                    return true;
                }
            }
            tracker.leftY += h + 12;
        }

        if (timeChangerVisible(search)) {
            int y = tracker.leftY;
            int h = getTimeChangerHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, leftX, y, columnWidth,
                    () -> {
                        TimeChangerModule.enabled = !TimeChangerModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Time Changer", TimeChangerModule.enabled);
                    },
                    () -> TimeChangerModule.expanded = !TimeChangerModule.expanded)) {
                return true;
            }
            if (TimeChangerModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, leftX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningTimeChanger = true;
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 74, curY, 60, 16)) {
                    TimeChangerModule.nextMode();
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;
                if (screen.inside(mx, my, leftX + columnWidth - 58, curY, 46, 16)) {
                    TimeChangerModule.reset();
                    BameClientConfig.save();
                    return true;
                }
            }
            tracker.leftY += h + 12;
        }

        if (noFogVisible(search)) {
            int y = tracker.rightY;
            int h = getNoFogHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, rightX, y, columnWidth,
                    () -> {
                        NoFogModule.enabled = !NoFogModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("No Fog", NoFogModule.enabled);
                    },
                    () -> NoFogModule.expanded = !NoFogModule.expanded)) {
                return true;
            }
            if (NoFogModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, rightX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningNoFog = true;
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 38, curY + 2, 26, 14)) { NoFogModule.allFog = !NoFogModule.allFog; BameClientConfig.save(); return true; }
                curY += 26;
                if (screen.inside(mx, my, rightX + columnWidth - 38, curY + 2, 26, 14)) { NoFogModule.netherFog = !NoFogModule.netherFog; BameClientConfig.save(); return true; }
                curY += 26;
                if (screen.inside(mx, my, rightX + columnWidth - 38, curY + 2, 26, 14)) { NoFogModule.waterFog = !NoFogModule.waterFog; BameClientConfig.save(); return true; }
                curY += 26;
                if (screen.inside(mx, my, rightX + columnWidth - 38, curY + 2, 26, 14)) { NoFogModule.lavaFog = !NoFogModule.lavaFog; BameClientConfig.save(); return true; }
                curY += 26;
                if (screen.inside(mx, my, rightX + columnWidth - 58, curY, 46, 16)) {
                    NoFogModule.resetToDefault();
                    BameClientConfig.save();
                    return true;
                }
            }
            tracker.rightY += h + 12;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(BameClientScreen screen, Click click, double deltaX, double deltaY) {
        double mx = click.x();
        double my = click.y();
        if (picker.dragging()) {
            picker.update(mx, my);
            return true;
        }
        if (draggingWidth) {
            int columnWidth = screen.getColumnWidth();
            int leftX = screen.getContentLeftX();
            int sx = leftX + 12;
            int sw = columnWidth - 24;
            float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
            BameClientConfig.outlineWidth = Math.round((1.0f + fval * 4.0f) * 10.0f) / 10.0f;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(BameClientScreen screen, Click click) {
        picker.release();
        if (draggingWidth) {
            draggingWidth = false;
            BameClientConfig.save();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(BameClientScreen screen, KeyInput input) {
        int key = input.key();
        if (listening) {
            AutoAreaMinerModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listening = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningNoFog) {
            NoFogModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningNoFog = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningTimeChanger) {
            TimeChangerModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningTimeChanger = false;
            BameClientConfig.save();
            return true;
        }
        if (corner1.isFocused() && corner1.keyPressed(input)) return true;
        if (corner2.isFocused() && corner2.keyPressed(input)) return true;
        return false;
    }

    @Override
    public boolean charTyped(BameClientScreen screen, CharInput input) {
        if (corner1.isFocused() && corner1.charTyped(input)) return true;
        if (corner2.isFocused() && corner2.charTyped(input)) return true;
        return false;
    }

    private void clearKeyListeners() {
        listening = false;
        listeningNoFog = false;
        listeningTimeChanger = false;
    }
}
