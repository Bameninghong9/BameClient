package com.bame.client.render;

import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.module.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class StatusHudRenderer implements HudRenderCallback {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final ItemStack[] PREVIEW_ARMOR = new ItemStack[] {
        new ItemStack(Items.NETHERITE_HELMET),
        new ItemStack(Items.NETHERITE_CHESTPLATE),
        new ItemStack(Items.NETHERITE_LEGGINGS),
        new ItemStack(Items.NETHERITE_BOOTS),
        new ItemStack(Items.TOTEM_OF_UNDYING)
    };

    private static final ItemStack[] PREVIEW_INV = new ItemStack[36];
    static {
        PREVIEW_INV[9] = new ItemStack(Items.ENDER_PEARL, 16);
        PREVIEW_INV[10] = new ItemStack(Items.GOLDEN_APPLE, 16);
        PREVIEW_INV[11] = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 3);
        PREVIEW_INV[12] = new ItemStack(Items.SPLASH_POTION);
        PREVIEW_INV[13] = new ItemStack(Items.SPLASH_POTION);
        PREVIEW_INV[14] = new ItemStack(Items.POTION);
        PREVIEW_INV[15] = new ItemStack(Items.POTION);
        PREVIEW_INV[16] = new ItemStack(Items.NETHERITE_PICKAXE);
        PREVIEW_INV[17] = new ItemStack(Items.NETHERITE_AXE);

        PREVIEW_INV[18] = new ItemStack(Items.NETHERITE_SHOVEL);
        PREVIEW_INV[19] = new ItemStack(Items.ARROW, 64);
        PREVIEW_INV[20] = new ItemStack(Items.WIND_CHARGE, 8);
        PREVIEW_INV[21] = new ItemStack(Items.COBWEB, 16);
        PREVIEW_INV[22] = new ItemStack(Items.OBSIDIAN, 32);
        PREVIEW_INV[23] = new ItemStack(Items.WATER_BUCKET);
        PREVIEW_INV[24] = new ItemStack(Items.TOTEM_OF_UNDYING);
        PREVIEW_INV[25] = new ItemStack(Items.EXPERIENCE_BOTTLE, 64);
        PREVIEW_INV[26] = new ItemStack(Items.GOLDEN_CARROT, 64);

        PREVIEW_INV[27] = new ItemStack(Items.FIREWORK_ROCKET, 32);
        PREVIEW_INV[28] = new ItemStack(Items.RESPAWN_ANCHOR, 4);
        PREVIEW_INV[29] = new ItemStack(Items.GLOWSTONE, 32);
        PREVIEW_INV[30] = new ItemStack(Items.SPLASH_POTION);
        PREVIEW_INV[31] = new ItemStack(Items.ANVIL);
        PREVIEW_INV[32] = new ItemStack(Items.CRAFTING_TABLE);
        PREVIEW_INV[33] = new ItemStack(Items.TOTEM_OF_UNDYING);
        PREVIEW_INV[34] = new ItemStack(Items.CROSSBOW);
        PREVIEW_INV[35] = new ItemStack(Items.TOTEM_OF_UNDYING);

        PREVIEW_INV[0] = new ItemStack(Items.NETHERITE_SWORD);
        PREVIEW_INV[1] = new ItemStack(Items.GOLDEN_APPLE, 16);
        PREVIEW_INV[2] = new ItemStack(Items.ENDER_PEARL, 16);
        PREVIEW_INV[3] = new ItemStack(Items.SPLASH_POTION);
        PREVIEW_INV[4] = new ItemStack(Items.BOW);
        PREVIEW_INV[5] = new ItemStack(Items.WIND_CHARGE, 4);
        PREVIEW_INV[6] = new ItemStack(Items.COBWEB, 8);
        PREVIEW_INV[7] = new ItemStack(Items.SHIELD);
        PREVIEW_INV[8] = new ItemStack(Items.TOTEM_OF_UNDYING);
    }

    private static void drawFastSlot(DrawContext c, int x, int y, int size) {
        c.fill(x, y, x + size, y + size, 0x2A121622);
        c.fill(x, y, x + size, y + 1, 0x1EFFFFFF);
        c.fill(x, y + size - 1, x + size, y + size, 0x1EFFFFFF);
        c.fill(x, y + 1, x + 1, y + size - 1, 0x1EFFFFFF);
        c.fill(x + size - 1, y + 1, x + size, y + size - 1, 0x1EFFFFFF);
    }

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        if (com.bame.client.module.SpotifyHudModule.enabled) {
            SpotifyHudRenderer.render(context, com.bame.client.module.SpotifyHudModule.hudX, com.bame.client.module.SpotifyHudModule.hudY, com.bame.client.module.SpotifyHudModule.scale, false);
        }

        if (!com.bame.client.module.ShowHudModule.enabled) return;

        if (FpsModule.enabled && (!ServerInfoModule.enabled || !ServerInfoModule.isDocked("fps"))) {
            renderFps(context, FpsModule.hudX, FpsModule.hudY, FpsModule.scale);
        }

        if (PingModule.enabled && (!ServerInfoModule.enabled || !ServerInfoModule.isDocked("ping"))) {
            renderPing(context, PingModule.hudX, PingModule.hudY, PingModule.scale);
        }

        if (CpsModule.enabled) {
            renderCps(context, CpsModule.hudX, CpsModule.hudY, CpsModule.scale);
        }

        if (ServerInfoModule.enabled) {
            if (!ServerInfoModule.getActiveDockedElements().isEmpty()) {
                int w = getServerInfoWidth(client);
                int x = ServerInfoModule.hudX;
                if (x == -1) {
                    x = client.getWindow().getScaledWidth() - (int)(w * ServerInfoModule.scale) - 10;
                    ServerInfoModule.hudX = x;
                }
                renderServerInfo(context, x, ServerInfoModule.hudY, ServerInfoModule.scale);
            }
            if (ServerInfoModule.showName && !ServerInfoModule.isDocked("name")) {
                renderStandaloneName(context, ServerInfoModule.nameX, ServerInfoModule.nameY, ServerInfoModule.scale);
            }
            if (ServerInfoModule.showServer && !ServerInfoModule.isDocked("server")) {
                renderStandaloneServer(context, ServerInfoModule.serverX, ServerInfoModule.serverY, ServerInfoModule.scale);
            }
            if (ServerInfoModule.showTime && !ServerInfoModule.isDocked("time")) {
                renderStandaloneTime(context, ServerInfoModule.timeX, ServerInfoModule.timeY, ServerInfoModule.scale);
            }
        }

        if (ClockModule.enabled) {
            renderClock(context, ClockModule.hudX, ClockModule.hudY, ClockModule.scale);
        }

        if (CoordinatesModule.enabled) {
            renderCoordinates(context, CoordinatesModule.hudX, CoordinatesModule.hudY, CoordinatesModule.scale);
        }

        if (PotionsModule.enabled) {
            renderPotions(context, PotionsModule.hudX, PotionsModule.hudY, PotionsModule.scale, false);
        }

        if (TargetHudModule.enabled) {
            int w = getTargetHudWidth();
            int x = TargetHudModule.hudX;
            if (x == -1) {
                x = (client.getWindow().getScaledWidth() - (int)(w * TargetHudModule.scale)) / 2;
            }
            int y = TargetHudModule.hudY;
            if (y == -1) {
                y = client.getWindow().getScaledHeight() - 120;
            }
            renderTargetHud(context, x, y, TargetHudModule.scale, false);
        }

        if (ArmorHudModule.enabled) {
            int w = getArmorHudWidth(client);
            int x = ArmorHudModule.hudX;
            if (x == -1) {
                x = (client.getWindow().getScaledWidth() - (int)(w * ArmorHudModule.scale)) / 2;
            }
            int y = ArmorHudModule.hudY;
            if (y == -1) {
                y = client.getWindow().getScaledHeight() - 56;
            }
            renderArmorHud(context, x, y, ArmorHudModule.scale, false);
        }
    }

    public static int getFpsWidth(MinecraftClient client) {
        String text = client.getCurrentFps() + " FPS";
        return client.textRenderer.getWidth(CustomGuiUtils.getFontText(text)) + 16;
    }

    public static int getPingWidth(MinecraftClient client) {
        int ping = getPingValue(client);
        String text = ping + " ms";
        return client.textRenderer.getWidth(CustomGuiUtils.getFontText(text)) + 16;
    }

    public static int getCpsWidth(MinecraftClient client) {
        int l = CpsModule.getLeftCps();
        int r = CpsModule.getRightCps();
        String text = l + " | " + r + " CPS";
        return client.textRenderer.getWidth(CustomGuiUtils.getFontText(text)) + 16;
    }

    public static int getClockWidth(MinecraftClient client) {
        String text = LocalTime.now().format(TIME_FORMATTER);
        return client.textRenderer.getWidth(CustomGuiUtils.getFontText(text)) + 16;
    }

    public static String getCoordinatesString(MinecraftClient client) {
        if (client.player == null) return "XYZ: 0 64 0";
        return String.format(java.util.Locale.ROOT, "XYZ: %d %d %d", client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ());
    }

    public static int getCoordinatesWidth(MinecraftClient client) {
        String text = getCoordinatesString(client);
        return client.textRenderer.getWidth(CustomGuiUtils.getFontText(text)) + 16;
    }

    private static String getAmplifierString(int amplifier) {
        return switch (amplifier) {
            case 0 -> "";
            case 1 -> " II";
            case 2 -> " III";
            case 3 -> " IV";
            case 4 -> " V";
            default -> " " + (amplifier + 1);
        };
    }

    private static String formatDuration(int ticks, boolean infinite) {
        if (infinite) return "**:**";
        int totalSeconds = ticks / 20;
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", mins, secs);
    }

    private static String getEffectName(StatusEffectInstance inst) {
        String base = inst.getEffectType().value().getName().getString();
        return base + getAmplifierString(inst.getAmplifier());
    }

    public static int getPotionsWidth(MinecraftClient client, boolean isPreview) {
        if (client == null) client = MinecraftClient.getInstance();
        int maxW = 120;
        if (client.player != null && !client.player.getStatusEffects().isEmpty()) {
            for (StatusEffectInstance inst : client.player.getStatusEffects()) {
                String name = getEffectName(inst);
                String dur = formatDuration(inst.getDuration(), inst.isInfinite());
                int nw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(name));
                int dw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(dur));
                maxW = Math.max(maxW, 26 + nw + 14 + dw + 8);
            }
        } else if (isPreview) {
            int nw = client.textRenderer.getWidth(CustomGuiUtils.getFontText("Absorption"));
            int dw = client.textRenderer.getWidth(CustomGuiUtils.getFontText("1:18"));
            maxW = Math.max(maxW, 26 + nw + 14 + dw + 8);
        }
        return maxW;
    }

    public static int getPotionsHeight(MinecraftClient client, boolean isPreview) {
        if (client == null) client = MinecraftClient.getInstance();
        int count = 0;
        if (client.player != null && !client.player.getStatusEffects().isEmpty()) {
            count = client.player.getStatusEffects().size();
        } else if (isPreview) {
            count = 1;
        }
        if (count == 0) return 0;
        return 19 + count * 20 + 3;
    }

    public static int getTargetHudWidth() {
        return 144;
    }

    public static int getTargetHudHeight() {
        int h = 40;
        if (TargetHudModule.showArmor) h += 24;
        return h;
    }

    public static int getArmorHudWidth(MinecraftClient client) { return 56; }
    public static int getArmorHudHeight() {
        return getArmorHudHeight(MinecraftClient.getInstance(), true);
    }
    public static int getArmorHudHeight(MinecraftClient client, boolean isPreview) {
        if (client == null) client = MinecraftClient.getInstance();
        int count = 0;
        if (client.player != null) {
            if (!client.player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) count++;
            if (!client.player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) count++;
            if (!client.player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()) count++;
            if (!client.player.getEquippedStack(EquipmentSlot.FEET).isEmpty()) count++;
        }
        if (count == 0 && isPreview) count = 4;
        return Math.max(22, count * 22 + 4);
    }

    private static int getPingValue(MinecraftClient client) {
        if (client.getNetworkHandler() != null && client.player != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
            if (entry != null) {
                return entry.getLatency();
            }
        }
        return 0;
    }

    public static void renderFps(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = client.getCurrentFps() + " FPS";
        renderPill(c, x, y, scale, text, FpsModule.bgMode, FpsModule.bgColor);
    }

    public static void renderPing(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        int ping = getPingValue(client);
        String text = ping + " ms";
        renderPill(c, x, y, scale, text, PingModule.bgMode, PingModule.bgColor);
    }

    public static void renderCps(DrawContext c, int x, int y, float scale) {
        int l = CpsModule.getLeftCps();
        int r = CpsModule.getRightCps();
        String text = l + " | " + r + " CPS";
        renderPill(c, x, y, scale, text, CpsModule.bgMode, CpsModule.bgColor);
    }

    public static void renderClock(DrawContext c, int x, int y, float scale) {
        String text = LocalTime.now().format(TIME_FORMATTER);
        renderPill(c, x, y, scale, text, ClockModule.bgMode, ClockModule.bgColor);
    }

    public static void renderCoordinates(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = getCoordinatesString(client);
        renderPill(c, x, y, scale, text, CoordinatesModule.bgMode, CoordinatesModule.bgColor);
    }

    public static void drawSparkle(DrawContext c, int x, int y, int color) {
        c.fill(x + 3, y + 2, x + 6, y + 5, color);
        c.fill(x + 4, y, x + 5, y + 2, color);
        c.fill(x + 4, y + 5, x + 5, y + 7, color);
        c.fill(x + 1, y + 3, x + 3, y + 4, color);
        c.fill(x + 6, y + 3, x + 8, y + 4, color);
        c.fill(x + 8, y, x + 9, y + 1, color);
    }

    public static void renderPotions(DrawContext c, int x, int y, float scale, boolean isPreview) {
        MinecraftClient client = MinecraftClient.getInstance();
        List<StatusEffectInstance> effects = new ArrayList<>();
        if (client.player != null && !client.player.getStatusEffects().isEmpty()) {
            effects.addAll(client.player.getStatusEffects());
        }
        if (effects.isEmpty() && !isPreview) return;

        int w = getPotionsWidth(client, isPreview);
        int h = getPotionsHeight(client, isPreview);
        if (h <= 0) return;

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);

        drawBoxBg(c, 0, 0, w, h, PotionsModule.bgMode);

        drawSparkle(c, 6, 5, 0xFFFFFFFF);
        c.drawText(client.textRenderer, CustomGuiUtils.getFontText("Potions"), 20, 5, 0xFFFFFFFF, PotionsModule.bgMode == 1);

        c.fill(4, 17, w - 4, 18, 0x25FFFFFF);

        if (!effects.isEmpty()) {
            for (int i = 0; i < effects.size(); i++) {
                StatusEffectInstance inst = effects.get(i);
                int rowY = 19 + i * 20;

                Identifier sprite = InGameHud.getEffectTexture(inst.getEffectType());
                c.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, 6, rowY + 2, 16, 16);

                String name = getEffectName(inst);
                c.drawText(client.textRenderer, CustomGuiUtils.getFontText(name), 26, rowY + 6, 0xFFFFFFFF, PotionsModule.bgMode == 1);

                String dur = formatDuration(inst.getDuration(), inst.isInfinite());
                int dw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(dur));
                c.drawText(client.textRenderer, CustomGuiUtils.getFontText(dur), w - 8 - dw, rowY + 6, 0xFFE0E0E0, PotionsModule.bgMode == 1);
            }
        } else if (isPreview) {
            int rowY = 19;
            Identifier sprite = InGameHud.getEffectTexture(StatusEffects.ABSORPTION);
            c.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, 6, rowY + 2, 16, 16);

            String name = "Absorption";
            c.drawText(client.textRenderer, CustomGuiUtils.getFontText(name), 26, rowY + 6, 0xFFFFFFFF, PotionsModule.bgMode == 1);

            String dur = "1:18";
            int dw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(dur));
            c.drawText(client.textRenderer, CustomGuiUtils.getFontText(dur), w - 8 - dw, rowY + 6, 0xFFE0E0E0, PotionsModule.bgMode == 1);
        }

        c.getMatrices().popMatrix();
    }

    public static void renderTargetHud(DrawContext c, int x, int y, float scale, boolean isPreview) {
        if (!TargetHudModule.enabled || (!TargetHudModule.showHearts && !TargetHudModule.showArmor)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        LivingEntity target = TargetHudModule.currentTarget;
        if (!isPreview) {
            if (target == null || target.isDead() || target.isRemoved()) return;
            if (System.currentTimeMillis() - TargetHudModule.lastTargetTime > 4000) return;
            if (TargetHudModule.playersOnly && !(target instanceof PlayerEntity)) return;
        }

        int w = getTargetHudWidth();
        int h = getTargetHudHeight();

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);

        drawBoxBg(c, 0, 0, w, h, TargetHudModule.bgMode);

        int headX = 5, headY = 5, headSize = 28;
        if (target instanceof AbstractClientPlayerEntity acpe) {
            PlayerSkinDrawer.draw(c, acpe.getSkin(), headX, headY, headSize);
        } else if (target != null) {
            CustomGuiUtils.fillUltraRounded(c, headX, headY, headSize, headSize, 0xFF2A2E39, 4);
        } else if (client.player != null) {
            PlayerSkinDrawer.draw(c, client.player.getSkin(), headX, headY, headSize);
        } else {
            CustomGuiUtils.fillUltraRounded(c, headX, headY, headSize, headSize, 0xFF2A2E39, 4);
        }

        int tx = headX + headSize + 6;
        int maxW = w - tx - 6;

        String name;
        if (target != null) {
            name = target.getName().getString();
            if (NameProtectModule.enabled) name = NameProtectModule.getProtectedName(name);
        } else {
            name = "Target";
        }

        if (TargetHudModule.showHearts) {
            c.drawText(client.textRenderer, CustomGuiUtils.getFontText(name), tx, 6, 0xFFFFFFFF, TargetHudModule.bgMode == 1);

            float hp = target != null ? target.getHealth() : 20.0f;
            float maxHp = target != null ? target.getMaxHealth() : 20.0f;
            int totalHearts = Math.min(10, Math.max(1, (int) Math.ceil(maxHp / 2.0f)));
            int heartY = 17;

            for (int i = 0; i < totalHearts; i++) {
                int hx = tx + i * 8;
                c.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla("hud/heart/container"), hx, heartY, 9, 9);

                float heartHealth = hp - (i * 2);
                if (heartHealth >= 2.0f) {
                    c.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla("hud/heart/full"), hx, heartY, 9, 9);
                } else if (heartHealth >= 1.0f) {
                    c.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla("hud/heart/half"), hx, heartY, 9, 9);
                }
            }

            String hpStr = String.format(java.util.Locale.ROOT, "%.1f HP", hp);
            String distStr = (target != null && client.player != null) ? String.format(java.util.Locale.ROOT, "%.1fm", client.player.distanceTo(target)) : "1.9m";
            c.drawText(client.textRenderer, CustomGuiUtils.getFontText(hpStr), tx, 28, 0xFFB0B8C5, TargetHudModule.bgMode == 1);
            int dw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(distStr));
            c.drawText(client.textRenderer, CustomGuiUtils.getFontText(distStr), w - dw - 6, 28, 0xFF8E95A4, TargetHudModule.bgMode == 1);
        } else {
            int nameY = 15;
            c.drawText(client.textRenderer, CustomGuiUtils.getFontText(name), tx, nameY, 0xFFFFFFFF, TargetHudModule.bgMode == 1);
            String distStr = (target != null && client.player != null) ? String.format(java.util.Locale.ROOT, "%.1fm", client.player.distanceTo(target)) : "1.9m";
            int dw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(distStr));
            c.drawText(client.textRenderer, CustomGuiUtils.getFontText(distStr), w - dw - 6, nameY, 0xFF8E95A4, TargetHudModule.bgMode == 1);
        }

        int curSectionY = 40;

        // Show Armor (+ Offhand)
        if (TargetHudModule.showArmor) {
            ItemStack helmet = target != null ? target.getEquippedStack(EquipmentSlot.HEAD) : ItemStack.EMPTY;
            ItemStack chest = target != null ? target.getEquippedStack(EquipmentSlot.CHEST) : ItemStack.EMPTY;
            ItemStack legs = target != null ? target.getEquippedStack(EquipmentSlot.LEGS) : ItemStack.EMPTY;
            ItemStack boots = target != null ? target.getEquippedStack(EquipmentSlot.FEET) : ItemStack.EMPTY;
            ItemStack offhand = target != null ? target.getEquippedStack(EquipmentSlot.OFFHAND) : ItemStack.EMPTY;

            ItemStack[] pieces = (isPreview && target == null) ? PREVIEW_ARMOR : new ItemStack[] { helmet, chest, legs, boots, offhand };
            int slotSize = 18;
            int gap = 3;
            int totalArmorW = 5 * slotSize + 4 * gap;
            int startX = (w - totalArmorW) / 2;

            for (int i = 0; i < 5; i++) {
                int sx = startX + i * (slotSize + gap);
                int sy = curSectionY + 2;
                drawFastSlot(c, sx, sy, slotSize);

                ItemStack stack = pieces[i];
                if (stack != null && !stack.isEmpty()) {
                    c.drawItem(stack, sx + 1, sy + 1);
                    c.drawStackOverlay(client.textRenderer, stack, sx + 1, sy + 1);
                }
            }
            curSectionY += 24;
        }

        c.getMatrices().popMatrix();
    }

    public static void renderArmorHud(DrawContext c, int x, int y, float scale, boolean isPreview) {
        MinecraftClient client = MinecraftClient.getInstance();
        List<ItemStack> pieces = new ArrayList<>();
        if (client.player != null) {
            ItemStack head = client.player.getEquippedStack(EquipmentSlot.HEAD);
            ItemStack chest = client.player.getEquippedStack(EquipmentSlot.CHEST);
            ItemStack legs = client.player.getEquippedStack(EquipmentSlot.LEGS);
            ItemStack feet = client.player.getEquippedStack(EquipmentSlot.FEET);

            if (!head.isEmpty()) pieces.add(head);
            if (!chest.isEmpty()) pieces.add(chest);
            if (!legs.isEmpty()) pieces.add(legs);
            if (!feet.isEmpty()) pieces.add(feet);
        }
        if (pieces.isEmpty() && isPreview) {
            pieces.add(new ItemStack(Items.NETHERITE_HELMET));
            pieces.add(new ItemStack(Items.NETHERITE_CHESTPLATE));
            pieces.add(new ItemStack(Items.NETHERITE_LEGGINGS));
            pieces.add(new ItemStack(Items.NETHERITE_BOOTS));
        }
        if (pieces.isEmpty()) return;

        int w = getArmorHudWidth(client);
        int h = Math.max(22, pieces.size() * 22 + 4);

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);

        drawBoxBg(c, 0, 0, w, h, ArmorHudModule.bgMode);

        for (int i = 0; i < pieces.size(); i++) {
            ItemStack stack = pieces.get(i);
            int rowY = 2 + i * 22;

            c.drawItem(stack, 6, rowY + 3);

            int pct = 100;
            if (stack.isDamageable()) {
                int max = stack.getMaxDamage();
                int dmg = stack.getDamage();
                pct = Math.max(0, Math.min(100, Math.round(((float) (max - dmg) / (float) max) * 100.0f)));
            }
            String pctStr = pct + "%";
            int pctColor = pct > 50 ? 0xFF55FF55 : (pct > 25 ? 0xFFFFAA00 : 0xFFFF5555);
            c.drawText(client.textRenderer, CustomGuiUtils.getFontText(pctStr), 27, rowY + 7, pctColor, ArmorHudModule.bgMode == 1);

            if (i < pieces.size() - 1) {
                c.fill(3, rowY + 22, w - 3, rowY + 23, 0x25FFFFFF);
            }
        }

        c.getMatrices().popMatrix();
    }

    public static void drawBoxBg(DrawContext c, int x, int y, int w, int h, int bgMode) {
        int fill = 0xD012161E;
        int outline = 0xFF292D36;

        if (bgMode == 1) { // Transparent
            return;
        } else if (bgMode == 2) { // Rainbow
            long time = System.currentTimeMillis();
            float hue = (time % 3000L) / 3000.0f;
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.75f, 0.9f);
            fill = 0xD0000000 | (rgb & 0xFFFFFF);
            outline = 0xFF000000 | (rgb & 0xFFFFFF);
        } else if (bgMode == 3) { // Theme
            int accent = com.bame.client.gui.GuiTheme.accent();
            fill = 0xB0000000 | (accent & 0xFFFFFF);
            outline = accent;
        }

        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, fill, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, outline, 4);
    }

    private static void renderPill(DrawContext c, int x, int y, float scale, String text, int bgMode, int bgColor) {
        MinecraftClient client = MinecraftClient.getInstance();
        int textWidth = client.textRenderer.getWidth(CustomGuiUtils.getFontText(text));
        int w = textWidth + 16;
        int h = 18;

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);

        int fill = 0xD012161E;
        int outline = 0xFF292D36;

        if (bgMode == 1) { // Transparent
            fill = 0;
            outline = 0;
        } else if (bgMode == 2) { // Rainbow
            long time = System.currentTimeMillis();
            float hue = (time % 3000L) / 3000.0f;
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.75f, 0.9f);
            fill = 0xD0000000 | (rgb & 0xFFFFFF);
            outline = 0xFF000000 | (rgb & 0xFFFFFF);
        } else if (bgMode == 3) { // Theme
            int accent = com.bame.client.gui.GuiTheme.accent();
            fill = 0xB0000000 | (accent & 0xFFFFFF);
            outline = accent;
        }

        if (fill != 0) {
            CustomGuiUtils.fillUltraRounded(c, 0, 0, w, h, fill, 4);
        }
        if (outline != 0) {
            CustomGuiUtils.drawUltraRoundedOutline(c, 0, 0, w, h, outline, 4);
        }
        c.drawText(client.textRenderer, CustomGuiUtils.getFontText(text), 8, (h - 8) / 2, 0xFFFFFFFF, bgMode == 1);

        c.getMatrices().popMatrix();
    }

    public static int getServerInfoWidth(MinecraftClient client) {
        java.util.List<String> active = ServerInfoModule.getActiveDockedElements();
        if (active.isEmpty()) {
            return 40;
        }
        int curX = 8;
        for (int i = 0; i < active.size(); i++) {
            String elem = active.get(i);
            curX += getElementWidth(client, elem);
            if (i < active.size() - 1) {
                curX += 16;
            }
        }
        return curX + 8;
    }

    public static int getElementWidth(MinecraftClient client, String elem) {
        return switch (elem) {
            case "name" -> {
                String name = client.player != null ? client.player.getName().getString() : (client.getSession() != null ? client.getSession().getUsername() : "Player");
                if (NameProtectModule.enabled) name = NameProtectModule.getProtectedName(name);
                yield 8 + 5 + client.textRenderer.getWidth(CustomGuiUtils.getFontText(name));
            }
            case "server" -> {
                String server = (client.getCurrentServerEntry() != null) ? client.getCurrentServerEntry().address : "Singleplayer";
                yield 9 + 5 + client.textRenderer.getWidth(CustomGuiUtils.getFontText(server));
            }
            case "time" -> {
                String time = LocalTime.now().format(TIME_FORMATTER);
                yield 8 + 5 + client.textRenderer.getWidth(CustomGuiUtils.getFontText(time));
            }
            case "fps" -> client.textRenderer.getWidth(CustomGuiUtils.getFontText(client.getCurrentFps() + " FPS"));
            case "ping" -> client.textRenderer.getWidth(CustomGuiUtils.getFontText(getPingValue(client) + " ms"));
            default -> 20;
        };
    }

    public static void drawUserIcon(DrawContext c, int x, int y, int color) {
        c.fill(x + 2, y, x + 6, y + 1, color);
        c.fill(x + 1, y + 1, x + 7, y + 3, color);
        c.fill(x + 2, y + 3, x + 6, y + 4, color);
        c.fill(x + 2, y + 5, x + 6, y + 6, color);
        c.fill(x + 1, y + 6, x + 7, y + 7, color);
        c.fill(x, y + 7, x + 8, y + 8, color);
    }

    public static void drawCloudIcon(DrawContext c, int x, int y, int color) {
        c.fill(x + 4, y, x + 6, y + 1, color);
        c.fill(x + 3, y + 1, x + 7, y + 2, color);
        c.fill(x + 2, y + 2, x + 8, y + 3, color);
        c.fill(x + 1, y + 3, x + 8, y + 4, color);
        c.fill(x, y + 4, x + 9, y + 5, color);
        c.fill(x, y + 5, x + 9, y + 6, color);
        c.fill(x + 1, y + 6, x + 8, y + 7, color);
    }

    public static void drawClockIcon(DrawContext c, int x, int y, int color) {
        c.fill(x + 2, y, x + 6, y + 1, color);
        c.fill(x + 1, y + 1, x + 2, y + 2, color);
        c.fill(x + 6, y + 1, x + 7, y + 2, color);
        c.fill(x, y + 2, x + 1, y + 6, color);
        c.fill(x + 7, y + 2, x + 8, y + 6, color);
        c.fill(x + 1, y + 6, x + 2, y + 7, color);
        c.fill(x + 6, y + 6, x + 7, y + 7, color);
        c.fill(x + 2, y + 7, x + 6, y + 8, color);
        c.fill(x + 3, y + 2, x + 4, y + 4, color);
        c.fill(x + 4, y + 3, x + 6, y + 4, color);
    }

    public static void renderServerInfo(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        java.util.List<String> active = ServerInfoModule.getActiveDockedElements();
        if (active.isEmpty()) return;

        int w = getServerInfoWidth(client);
        int h = 18;

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);

        drawBoxBg(c, 0, 0, w, h, ServerInfoModule.bgMode);

        int textY = (h - 8) / 2;
        boolean shadow = ServerInfoModule.bgMode == 1;
        int curX = 8;

        for (int i = 0; i < active.size(); i++) {
            String elem = active.get(i);
            int elemW = getElementWidth(client, elem);

            switch (elem) {
                case "name" -> {
                    String name = client.player != null ? client.player.getName().getString() : (client.getSession() != null ? client.getSession().getUsername() : "Player");
                    if (NameProtectModule.enabled) name = NameProtectModule.getProtectedName(name);
                    drawUserIcon(c, curX, 5, 0xFFFFFFFF);
                    c.drawText(client.textRenderer, CustomGuiUtils.getFontText(name), curX + 8 + 5, textY, 0xFFFFFFFF, shadow);
                }
                case "server" -> {
                    String server = (client.getCurrentServerEntry() != null) ? client.getCurrentServerEntry().address : "Singleplayer";
                    drawCloudIcon(c, curX, 5, 0xFFFFFFFF);
                    c.drawText(client.textRenderer, CustomGuiUtils.getFontText(server), curX + 9 + 5, textY, 0xFFFFFFFF, shadow);
                }
                case "time" -> {
                    String time = LocalTime.now().format(TIME_FORMATTER);
                    drawClockIcon(c, curX, 5, 0xFFFFFFFF);
                    c.drawText(client.textRenderer, CustomGuiUtils.getFontText(time), curX + 8 + 5, textY, 0xFFFFFFFF, shadow);
                }
                case "fps" -> {
                    String text = client.getCurrentFps() + " FPS";
                    c.drawText(client.textRenderer, CustomGuiUtils.getFontText(text), curX, textY, 0xFFFFFFFF, shadow);
                }
                case "ping" -> {
                    String text = getPingValue(client) + " ms";
                    c.drawText(client.textRenderer, CustomGuiUtils.getFontText(text), curX, textY, 0xFFFFFFFF, shadow);
                }
            }

            curX += elemW;

            if (i < active.size() - 1) {
                c.fill(curX + 7, 3, curX + 8, h - 3, 0x338E95A4);
                curX += 16;
            }
        }

        c.getMatrices().popMatrix();
    }

    public static int getNameWidth(MinecraftClient client) {
        String name = client.player != null ? client.player.getName().getString() : (client.getSession() != null ? client.getSession().getUsername() : "Player");
        if (NameProtectModule.enabled) name = NameProtectModule.getProtectedName(name);
        return 8 + 8 + 5 + client.textRenderer.getWidth(CustomGuiUtils.getFontText(name)) + 8;
    }

    public static void renderStandaloneName(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        String name = client.player != null ? client.player.getName().getString() : (client.getSession() != null ? client.getSession().getUsername() : "Player");
        if (NameProtectModule.enabled) name = NameProtectModule.getProtectedName(name);
        int nw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(name));
        int w = 8 + 8 + 5 + nw + 8;
        int h = 18;

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);
        drawBoxBg(c, 0, 0, w, h, ServerInfoModule.bgMode);
        drawUserIcon(c, 8, 5, 0xFFFFFFFF);
        c.drawText(client.textRenderer, CustomGuiUtils.getFontText(name), 8 + 8 + 5, (h - 8) / 2, 0xFFFFFFFF, ServerInfoModule.bgMode == 1);
        c.getMatrices().popMatrix();
    }

    public static int getServerWidth(MinecraftClient client) {
        String server = (client.getCurrentServerEntry() != null) ? client.getCurrentServerEntry().address : "Singleplayer";
        return 8 + 9 + 5 + client.textRenderer.getWidth(CustomGuiUtils.getFontText(server)) + 8;
    }

    public static void renderStandaloneServer(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        String server = (client.getCurrentServerEntry() != null) ? client.getCurrentServerEntry().address : "Singleplayer";
        int sw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(server));
        int w = 8 + 9 + 5 + sw + 8;
        int h = 18;

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);
        drawBoxBg(c, 0, 0, w, h, ServerInfoModule.bgMode);
        drawCloudIcon(c, 8, 5, 0xFFFFFFFF);
        c.drawText(client.textRenderer, CustomGuiUtils.getFontText(server), 8 + 9 + 5, (h - 8) / 2, 0xFFFFFFFF, ServerInfoModule.bgMode == 1);
        c.getMatrices().popMatrix();
    }

    public static int getTimeWidth(MinecraftClient client) {
        String time = LocalTime.now().format(TIME_FORMATTER);
        return 8 + 8 + 5 + client.textRenderer.getWidth(CustomGuiUtils.getFontText(time)) + 8;
    }

    public static void renderStandaloneTime(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        String time = LocalTime.now().format(TIME_FORMATTER);
        int tw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(time));
        int w = 8 + 8 + 5 + tw + 8;
        int h = 18;

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);
        drawBoxBg(c, 0, 0, w, h, ServerInfoModule.bgMode);
        drawClockIcon(c, 8, 5, 0xFFFFFFFF);
        c.drawText(client.textRenderer, CustomGuiUtils.getFontText(time), 8 + 8 + 5, (h - 8) / 2, 0xFFFFFFFF, ServerInfoModule.bgMode == 1);
        c.getMatrices().popMatrix();
    }
}
