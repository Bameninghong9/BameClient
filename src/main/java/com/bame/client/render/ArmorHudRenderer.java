package com.bame.client.render;

import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.gui.GuiTheme;
import com.bame.client.module.ArmorHudModule;
import com.bame.client.module.HotbarModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Armor HUD: Rüstung (einzeln abwählbar), optional Haupthand, Offhand und Pfeilanzahl, in vier Stilen
 * ({@link ArmorHudLayout#STYLE_SLOTS} … {@link ArmorHudLayout#STYLE_MINIMAL}) mit wählbarer Haltbarkeitsanzeige.
 * Position frei (HUD-Editor) oder links/rechts an der verschiebbaren Hotbar ({@link HotbarModule}).
 *
 * <p>Leistung: keine Allokation pro Bild im eigenen Code. Einträge liegen in festen Arrays, die Texte kommen aus
 * Caches (Prozent fest, Zahlen bis 9999 einmal erzeugt, „Zahlen/Max“ und die Textbreiten pro Eintragsart nur bei
 * geänderter Haltbarkeit neu), die Platzhalter-Rüstung der Editor-Vorschau wird einmal angelegt. Nur Render-Thread.
 */
public final class ArmorHudRenderer {
    private ArmorHudRenderer() {}

    // Eintragsarten (zugleich Index der Text-Caches)
    private static final int KIND_HEAD = 0;
    private static final int KIND_CHEST = 1;
    private static final int KIND_LEGS = 2;
    private static final int KIND_FEET = 3;
    private static final int KIND_MAIN = 4;
    private static final int KIND_OFF = 5;
    private static final int KIND_ARROWS = 6;
    private static final int KINDS = 7;

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Vanilla-Platzhalter leerer Felder (wie im Inventar), halb durchsichtig gezeichnet. */
    private static final Identifier[] EMPTY_SPRITES = {
            PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE,
            PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE,
            PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE,
            PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE,
            Identifier.ofVanilla("container/slot/sword"),
            PlayerScreenHandler.EMPTY_OFF_HAND_SLOT_TEXTURE,
            null
    };
    private static final int EMPTY_SPRITE_TINT = 0x70FFFFFF;

    // Hotbar-Look der Slot-Leiste (eigene Zeichnung mit flachen Farben, angelehnt an die Vanilla-Hotbar)
    private static final int SLOT_OUTER = 0xFF000000;
    private static final int SLOT_BEVEL = 0xFF9A9A9A;
    private static final int SLOT_FRAME = 0xFF5B5B5B;
    private static final int SLOT_EDGE_DARK = 0xC0050505;
    private static final int SLOT_EDGE_LIGHT = 0xFF7E7E7E;
    private static final int SLOT_FILL = 0xB0242424;

    private static final int BAR_TRACK = 0xA0000000;
    private static final int SEPARATOR = 0x25FFFFFF;
    private static final int TEXT_PLAIN = 0xFFFFFFFF;
    private static final int COUNT_ZERO = 0xFFFF5555;

    /** Leere Vorschau im HUD-Editor (alle Einträge abgewählt): greifbares Feld mit Hinweis. */
    private static final int EMPTY_PREVIEW_W = 64;
    private static final int EMPTY_PREVIEW_H = 20;

    /** Platzhalter für die Vorschau im HUD-Editor, falls der Spieler keine Rüstung trägt (einmal angelegt). */
    private static final class Preview {
        static final ItemStack[] ARMOR = {
                worn(Items.NETHERITE_HELMET, 0.87f),
                worn(Items.NETHERITE_CHESTPLATE, 0.64f),
                worn(Items.NETHERITE_LEGGINGS, 0.41f),
                worn(Items.NETHERITE_BOOTS, 0.12f)
        };
        static final ItemStack MAIN_HAND = worn(Items.NETHERITE_SWORD, 0.93f);
        static final ItemStack OFF_HAND = new ItemStack(Items.TOTEM_OF_UNDYING);
        static final ItemStack ARROW = new ItemStack(Items.ARROW);
        static final int ARROW_COUNT = 64;

        private static ItemStack worn(Item item, float left) {
            ItemStack stack = new ItemStack(item);
            if (stack.isDamageable()) stack.setDamage(Math.round(stack.getMaxDamage() * (1.0f - left)));
            return stack;
        }
    }

    /** Text mit gemerkter Breite; neu gebaut nur, wenn sich Modus oder Werte ändern. */
    private static final class Label {
        int mode = Integer.MIN_VALUE;
        int a = Integer.MIN_VALUE;
        int b = Integer.MIN_VALUE;
        String text;
        int width;
    }

    private static final Label[] DURABILITY_LABELS = new Label[KINDS];
    /** Längster möglicher Text je Eintragsart (bestimmt die Breite, damit das HUD beim Abnutzen nicht springt). */
    private static final Label[] WIDEST_LABELS = new Label[KINDS];
    private static final Label[] COUNT_LABELS = new Label[KINDS];
    static {
        for (int i = 0; i < KINDS; i++) {
            DURABILITY_LABELS[i] = new Label();
            WIDEST_LABELS[i] = new Label();
            COUNT_LABELS[i] = new Label();
        }
    }

    // Einträge des aktuellen Bilds (feste Arrays, Index 0..count-1)
    private static final ItemStack[] stacks = new ItemStack[KINDS];
    private static final int[] kinds = new int[KINDS];
    private static final boolean[] damageable = new boolean[KINDS];
    private static final int[] damage = new int[KINDS];
    private static final int[] maxDamage = new int[KINDS];
    private static final int[] remaining = new int[KINDS];
    private static int count;
    private static int arrowTotal;
    private static boolean emptyPreview;

    private static final ArmorHudLayout.Metrics METRICS = new ArmorHudLayout.Metrics();
    private static int boxW;
    private static int boxH;
    private static int contentDX;
    private static int contentDY;

    // Ergebnis von place()
    private static int placedX;
    private static int placedY;
    private static float placedScale = 1.0f;

    // ------------------------------------------------------------------------------------------------------------
    // Öffentliche Schnittstelle
    // ------------------------------------------------------------------------------------------------------------

    /** Im Spiel (StatusHudRenderer, Profiler-Abschnitt {@code caeser_hud_armor}): Position berechnen und zeichnen. */
    public static void renderInGame(DrawContext context) {
        place(context.getScaledWindowWidth(), context.getScaledWindowHeight(), false);
        if (count == 0) return;
        draw(context, placedX, placedY, placedScale);
    }

    /** Zeichnet an einer vorgegebenen Stelle (HUD-Editor). {@code preview}: Platzhalter, falls keine Rüstung getragen wird. */
    public static void render(DrawContext context, int x, int y, float scale, boolean preview) {
        prepare(preview);
        draw(context, x, y, ArmorHudLayout.sanitizeScale(scale));
    }

    /**
     * Berechnet Einträge, Größe und Position für diesen Bildschirm: frei (gespeicherte Position oder automatisch
     * mittig unten) oder am Hotbar-Anker (beachtet Versatz und Größe der verschiebbaren Hotbar und die Offhand-Seite).
     * Ergebnis über {@link #placedX()}, {@link #placedY()}, {@link #placedScale()}, {@link #boxWidth()},
     * {@link #boxHeight()}.
     */
    public static void place(int screenW, int screenH, boolean preview) {
        prepare(preview);
        if (ArmorHudModule.isAnchored()) {
            // Im HUD-Editor zeigt die Hotbar-Vorschau immer die eingestellte Lage; im Spiel gilt sie nur, wenn Vanilla
            // die Hotbar wirklich verschoben zeichnet (nicht im Zuschauermodus)
            boolean moved = preview || HotbarHud.isActive(false);
            float hotbarScale = moved ? HotbarLayout.clampScale(HotbarModule.scale) : 1.0f;
            int offX = moved ? HotbarHud.effectiveOffsetX(screenW) : 0;
            int offY = moved ? HotbarHud.effectiveOffsetY(screenH) : 0;
            float s = ArmorHudLayout.anchoredScale(ArmorHudModule.scale, hotbarScale);
            int sw = ArmorHudLayout.scaledSize(boxW, s);
            int sh = ArmorHudLayout.scaledSize(boxH, s);
            int left = ArmorHudLayout.hotbarLeftEdge(screenW, offX, hotbarScale, HotbarHud.groupLeft());
            int right = ArmorHudLayout.hotbarRightEdge(screenW, offX, hotbarScale, HotbarHud.groupRight());
            int bottom = ArmorHudLayout.hotbarBottomEdge(screenH, offY, hotbarScale);
            placedX = ArmorHudLayout.anchoredX(ArmorHudLayout.sanitizeAnchor(ArmorHudModule.anchor), left, right, sw,
                    screenW, ArmorHudLayout.anchorGap(hotbarScale));
            placedY = ArmorHudLayout.anchoredY(bottom, sh, screenH);
            placedScale = s;
        } else {
            float s = ArmorHudLayout.sanitizeScale(ArmorHudModule.scale);
            placedX = ArmorHudLayout.freeX(ArmorHudModule.hudX, screenW, ArmorHudLayout.scaledSize(boxW, s));
            placedY = ArmorHudLayout.freeY(ArmorHudModule.hudY, screenH, ArmorHudLayout.scaledSize(boxH, s));
            placedScale = s;
        }
    }

    public static int placedX() {
        return placedX;
    }

    public static int placedY() {
        return placedY;
    }

    public static float placedScale() {
        return placedScale;
    }

    /** Breite des Elements (unskaliert, mindestens die eigene Breite aus dem HUD-Editor). */
    public static int boxWidth() {
        return boxW;
    }

    public static int boxHeight() {
        return boxH;
    }

    public static int width(boolean preview) {
        prepare(preview);
        return boxW;
    }

    public static int height(boolean preview) {
        prepare(preview);
        return boxH;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Einträge sammeln und messen
    // ------------------------------------------------------------------------------------------------------------

    private static void prepare(boolean preview) {
        MinecraftClient client = MinecraftClient.getInstance();
        collect(client.player, preview);
        measure(client.textRenderer, preview);
    }

    private static void collect(PlayerEntity player, boolean preview) {
        count = 0;
        arrowTotal = 0;
        if (player == null && !preview) return;
        boolean placeholders = preview && (player == null || !wearsArmor(player));
        boolean helmetFirst = ArmorHudLayout.sanitizeOrder(ArmorHudModule.order) == ArmorHudLayout.ORDER_HELMET_FIRST;
        for (int j = 0; j < 4; j++) {
            int kind = helmetFirst ? j : 3 - j;
            if (!showArmorKind(kind)) continue;
            add(kind, placeholders ? Preview.ARMOR[kind] : player.getEquippedStack(ARMOR_SLOTS[kind]));
        }
        if (ArmorHudModule.showMainHand) add(KIND_MAIN, placeholders ? Preview.MAIN_HAND : player.getMainHandStack());
        if (ArmorHudModule.showOffHand) add(KIND_OFF, placeholders ? Preview.OFF_HAND : player.getOffHandStack());
        if (ArmorHudModule.showArrows) {
            if (placeholders) {
                addArrows(Preview.ARROW, Preview.ARROW_COUNT);
            } else if (preview || holdsRangedWeapon(player)) {
                // Im HUD-Editor immer sichtbar (damit man die Option sieht), im Spiel nur mit Bogen/Armbrust in der Hand
                addArrowsFromInventory(player);
            }
        }
    }

    private static boolean showArmorKind(int kind) {
        return switch (kind) {
            case KIND_HEAD -> ArmorHudModule.showHelmet;
            case KIND_CHEST -> ArmorHudModule.showChestplate;
            case KIND_LEGS -> ArmorHudModule.showLeggings;
            default -> ArmorHudModule.showBoots;
        };
    }

    private static boolean wearsArmor(PlayerEntity player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!player.getEquippedStack(slot).isEmpty()) return true;
        }
        return false;
    }

    private static boolean holdsRangedWeapon(PlayerEntity player) {
        return player.getMainHandStack().getItem() instanceof RangedWeaponItem
                || player.getOffHandStack().getItem() instanceof RangedWeaponItem;
    }

    private static void add(int kind, ItemStack stack) {
        ItemStack s = stack != null ? stack : ItemStack.EMPTY;
        if (s.isEmpty() && ArmorHudModule.hideEmpty) return;
        int i = count++;
        stacks[i] = s;
        kinds[i] = kind;
        // Unzerstörbare Items und Items ohne Haltbarkeit (z. B. Totem, Pfeile) bekommen keine Haltbarkeits-Anzeige
        boolean dmg = !s.isEmpty() && s.isDamageable() && s.getMaxDamage() > 0;
        damageable[i] = dmg;
        if (dmg) {
            int max = s.getMaxDamage();
            int d = s.getDamage();
            maxDamage[i] = max;
            damage[i] = d;
            remaining[i] = ArmorHudLayout.remaining(d, max);
        } else {
            maxDamage[i] = 0;
            damage[i] = 0;
            remaining[i] = 0;
        }
    }

    /** Pfeile (Pfeil, Spektral-, Trankpfeil) in Offhand und Inventar zählen; Symbol wie Vanilla: zuerst die Hände. */
    private static void addArrowsFromInventory(PlayerEntity player) {
        ItemStack icon = null;
        int total = 0;
        ItemStack off = player.getOffHandStack();
        if (!off.isEmpty() && off.isIn(ItemTags.ARROWS)) {
            icon = off;
            total += off.getCount();
        }
        ItemStack main = player.getMainHandStack();
        if (icon == null && !main.isEmpty() && main.isIn(ItemTags.ARROWS)) icon = main;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < PlayerInventory.MAIN_SIZE; slot++) {
            ItemStack s = inventory.getStack(slot);
            if (s.isEmpty() || !s.isIn(ItemTags.ARROWS)) continue;
            if (icon == null) icon = s;
            total += s.getCount();
        }
        addArrows(icon != null ? icon : Preview.ARROW, total);
    }

    private static void addArrows(ItemStack icon, int total) {
        int i = count++;
        stacks[i] = icon;
        kinds[i] = KIND_ARROWS;
        damageable[i] = false;
        damage[i] = 0;
        maxDamage[i] = 0;
        remaining[i] = 0;
        arrowTotal = Math.max(0, total);
    }

    private static void measure(TextRenderer textRenderer, boolean preview) {
        int mode = ArmorHudLayout.sanitizeDurability(ArmorHudModule.durabilityMode);
        int style = ArmorHudLayout.sanitizeStyle(ArmorHudModule.style);
        int maxTextW = 0;
        if (ArmorHudLayout.showsText(mode)) {
            for (int i = 0; i < count; i++) {
                if (!damageable[i]) continue;
                int w = Math.max(durabilityLabel(i, mode, textRenderer).width, widestLabel(i, mode, textRenderer).width);
                maxTextW = Math.max(maxTextW, w);
            }
        }
        boolean bgVisible = style != ArmorHudLayout.STYLE_MINIMAL && ArmorHudModule.bgMode != 0;
        METRICS.compute(style, mode, count, maxTextW, bgVisible);
        emptyPreview = count == 0 && preview;
        int naturalW = emptyPreview ? EMPTY_PREVIEW_W : (count == 0 ? 0 : METRICS.width);
        int naturalH = emptyPreview ? EMPTY_PREVIEW_H : (count == 0 ? 0 : METRICS.height);
        boxW = ArmorHudModule.customWidth > 0 && naturalW > 0 ? Math.max(naturalW, ArmorHudModule.customWidth) : naturalW;
        boxH = ArmorHudModule.customHeight > 0 && naturalH > 0 ? Math.max(naturalH, ArmorHudModule.customHeight) : naturalH;
        contentDX = (boxW - naturalW) / 2;
        contentDY = (boxH - naturalH) / 2;
    }

    private static Label durabilityLabel(int i, int mode, TextRenderer textRenderer) {
        Label l = DURABILITY_LABELS[kinds[i]];
        if (l.mode != mode || l.a != remaining[i] || l.b != maxDamage[i]) {
            l.mode = mode;
            l.a = remaining[i];
            l.b = maxDamage[i];
            l.text = ArmorHudLayout.durabilityText(mode, remaining[i], maxDamage[i]);
            l.width = l.text == null ? 0 : textRenderer.getWidth(CustomGuiUtils.getFontText(l.text));
        }
        return l;
    }

    private static Label widestLabel(int i, int mode, TextRenderer textRenderer) {
        Label l = WIDEST_LABELS[kinds[i]];
        if (l.mode != mode || l.b != maxDamage[i]) {
            l.mode = mode;
            l.b = maxDamage[i];
            l.text = ArmorHudLayout.widestDurabilityText(mode, maxDamage[i]);
            l.width = l.text == null ? 0 : textRenderer.getWidth(CustomGuiUtils.getFontText(l.text));
        }
        return l;
    }

    private static Label countLabel(int kind, int n, TextRenderer textRenderer) {
        Label l = COUNT_LABELS[kind];
        if (l.a != n || l.text == null) {
            l.a = n;
            l.text = ArmorHudLayout.numberText(n);
            l.width = textRenderer.getWidth(l.text);
        }
        return l;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Zeichnen
    // ------------------------------------------------------------------------------------------------------------

    private static void draw(DrawContext c, int x, int y, float scale) {
        if (count == 0 && !emptyPreview) return;
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        ArmorHudLayout.Metrics m = METRICS;
        int bgMode = ArmorHudModule.bgMode;
        Matrix3x2fStack matrices = c.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate((float) x, (float) y);
            matrices.scale(scale, scale);
            if (m.style != ArmorHudLayout.STYLE_MINIMAL) {
                StatusHudRenderer.drawBoxBg(c, 0, 0, boxW, boxH, bgMode, ArmorHudModule.outlineColor);
            }
            if (emptyPreview) {
                String hint = "Armor HUD";
                int tw = textRenderer.getWidth(CustomGuiUtils.getFontText(hint));
                c.drawStrokedRectangle(0, 0, boxW, boxH, 0x55FFFFFF);
                c.drawText(textRenderer, CustomGuiUtils.getFontText(hint), (boxW - tw) / 2, (boxH - 8) / 2, 0xFF8E95A4, true);
                return;
            }
            if (contentDX != 0 || contentDY != 0) matrices.translate((float) contentDX, (float) contentDY);

            // Schatten auf durchsichtigem/kontur-/Tooltip-Hintergrund (lesbar über der Welt), nicht auf „Dark“
            boolean shadow = m.style == ArmorHudLayout.STYLE_MINIMAL || bgMode != 2;
            float pulse = ArmorHudLayout.warnPulse(System.currentTimeMillis());
            int accent = 0xFF000000 | GuiTheme.accent();

            if (m.style == ArmorHudLayout.STYLE_SLOTS) {
                drawSlotBar(c, m);
            } else if (m.style == ArmorHudLayout.STYLE_VERTICAL && bgMode != 0) {
                for (int i = 0; i < count - 1; i++) {
                    int sy = m.rowY(i) + ArmorHudLayout.V_ROW_H;
                    c.fill(3, sy, m.width - 3, sy + 1, SEPARATOR);
                }
            }
            int mode = m.mode;
            boolean warnEnabled = ArmorHudModule.lowWarning;
            int threshold = ArmorHudModule.lowThreshold;
            for (int i = 0; i < count; i++) {
                boolean low = warnEnabled && damageable[i] && ArmorHudLayout.isLow(remaining[i], maxDamage[i], threshold);
                drawEntry(c, textRenderer, m, i, mode, low, pulse, shadow, accent);
            }
        } finally {
            matrices.popMatrix();
        }
    }

    /** Slot-Leiste im Hotbar-Look: 1 px schwarzer Rand, heller Rand oben/links je Feld, durchscheinende Slot-Felder. */
    private static void drawSlotBar(DrawContext c, ArmorHudLayout.Metrics m) {
        int fx = m.frameX;
        int fy = m.frameY;
        int fw = m.frameW;
        int h = ArmorHudLayout.S_H;
        // Außenrand
        c.fill(fx, fy, fx + fw, fy + 1, SLOT_OUTER);
        c.fill(fx, fy + h - 1, fx + fw, fy + h, SLOT_OUTER);
        c.fill(fx, fy + 1, fx + 1, fy + h - 1, SLOT_OUTER);
        c.fill(fx + fw - 1, fy + 1, fx + fw, fy + h - 1, SLOT_OUTER);
        // Rahmen oben (Lichtkante + Rahmenzeile) und unten
        c.fill(fx + 1, fy + 1, fx + fw - 1, fy + 2, SLOT_BEVEL);
        c.fill(fx + 1, fy + 2, fx + fw - 1, fy + 3, SLOT_FRAME);
        c.fill(fx + 1, fy + h - 2, fx + fw - 1, fy + h - 1, SLOT_FRAME);
        int top = fy + 3;
        int bottom = fy + h - 2;
        for (int i = 0; i < count; i++) {
            int cx = m.cellX(i);
            int sx = m.iconX(i);
            // Lichtkante am Feldanfang, Rahmen links und rechts vom Slot (der Slot selbst bleibt durchscheinend)
            c.fill(cx, fy + 2, cx + 1, bottom, SLOT_BEVEL);
            if (sx > cx + 1) c.fill(cx + 1, top, sx, bottom, SLOT_FRAME);
            if (cx + m.pitch > sx + 17) c.fill(sx + 17, top, cx + m.pitch, bottom, SLOT_FRAME);
            // Slot 17x17: dunkle Kante oben/links, helle Kante unten/rechts, Inneres 15x15
            c.fill(sx, top, sx + 16, top + 1, SLOT_EDGE_DARK);
            c.fill(sx, top + 1, sx + 1, top + 16, SLOT_EDGE_DARK);
            c.fill(sx + 1, top + 1, sx + 16, top + 16, SLOT_FILL);
            c.fill(sx + 16, top, sx + 17, top + 17, SLOT_EDGE_LIGHT);
            c.fill(sx, top + 16, sx + 16, top + 17, SLOT_EDGE_LIGHT);
        }
    }

    private static void drawEntry(DrawContext c, TextRenderer textRenderer, ArmorHudLayout.Metrics m, int i, int mode,
                                  boolean low, float pulse, boolean shadow, int accent) {
        ItemStack stack = stacks[i];
        int kind = kinds[i];
        int ix = m.iconX(i);
        int iy = m.iconY(i);

        if (low) drawWarnFill(c, m, i, pulse);

        if (stack.isEmpty()) {
            Identifier sprite = EMPTY_SPRITES[kind];
            if (sprite != null) c.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, ix, iy, 16, 16, EMPTY_SPRITE_TINT);
        } else {
            c.drawItem(stack, ix, iy);
        }

        if (damageable[i]) {
            int rem = remaining[i];
            int max = maxDamage[i];
            boolean colored = ArmorHudModule.colorByDurability;
            if (m.bar) {
                int bx = m.barX(i);
                int by = m.barY(i);
                int barColor = colored ? 0xFF000000 | ArmorHudLayout.vanillaBarColor(damage[i], max) : accent;
                if (m.vanillaBar) {
                    // Wie DrawContext.drawItemBar: schwarzer 13x2-Streifen, farbige obere Zeile (hier auch bei voller Haltbarkeit)
                    c.fill(bx, by, bx + 13, by + 2, 0xFF000000);
                    int step = ArmorHudLayout.vanillaBarStep(damage[i], max);
                    if (step > 0) c.fill(bx, by, bx + step, by + 1, barColor);
                } else {
                    int bw = m.barW();
                    int bh = m.barH();
                    c.fill(bx, by, bx + bw, by + bh, BAR_TRACK);
                    int fill = ArmorHudLayout.barFill(bw, rem, max);
                    if (fill > 0) c.fill(bx, by, bx + fill, by + bh, barColor);
                }
            }
            if (m.text) {
                Label label = durabilityLabel(i, mode, textRenderer);
                if (label.text != null) {
                    int color = colored ? ArmorHudLayout.durabilityColor(rem, max) : TEXT_PLAIN;
                    if (m.style == ArmorHudLayout.STYLE_VERTICAL) {
                        c.drawText(textRenderer, CustomGuiUtils.getFontText(label.text), m.textX(i, 0), m.textY(i), color, shadow);
                    } else {
                        int lw = ArmorHudLayout.labelWidth(label.width);
                        Matrix3x2fStack matrices = c.getMatrices();
                        matrices.pushMatrix();
                        matrices.translate((float) m.textX(i, lw), (float) m.textY(i));
                        matrices.scale(ArmorHudLayout.LABEL_SCALE, ArmorHudLayout.LABEL_SCALE);
                        c.drawText(textRenderer, CustomGuiUtils.getFontText(label.text), 0, 0, color, shadow);
                        matrices.popMatrix();
                    }
                }
            }
        }

        // Anzahl wie im Inventar unten rechts am Item (Vanilla-Schrift): Pfeile im Inventar bzw. Stapelgröße
        if (kind == KIND_ARROWS) {
            Label l = countLabel(kind, arrowTotal, textRenderer);
            c.drawText(textRenderer, l.text, ix + 17 - l.width, iy + 9, arrowTotal == 0 ? COUNT_ZERO : TEXT_PLAIN, true);
        } else if (!stack.isEmpty() && stack.getCount() > 1) {
            Label l = countLabel(kind, stack.getCount(), textRenderer);
            c.drawText(textRenderer, l.text, ix + 17 - l.width, iy + 9, TEXT_PLAIN, true);
        }

        if (low) drawWarnOutline(c, m, i, pulse);
    }

    private static void drawWarnFill(DrawContext c, ArmorHudLayout.Metrics m, int i, float pulse) {
        int color = ArmorHudLayout.warnFillColor(pulse);
        int x = m.markX(i);
        int y = m.markY(i);
        if (m.style == ArmorHudLayout.STYLE_SLOTS) {
            c.fill(x + 1, y + 1, x + m.markW() - 1, y + m.markH() - 1, color);
        } else {
            CustomGuiUtils.fillUltraRounded(c, x, y, m.markW(), m.markH(), color, 3);
        }
    }

    private static void drawWarnOutline(DrawContext c, ArmorHudLayout.Metrics m, int i, float pulse) {
        int color = ArmorHudLayout.warnOutlineColor(pulse);
        int x = m.markX(i);
        int y = m.markY(i);
        if (m.style == ArmorHudLayout.STYLE_SLOTS) {
            c.drawStrokedRectangle(x, y, m.markW(), m.markH(), color);
        } else {
            CustomGuiUtils.drawUltraRoundedOutline(c, x, y, m.markW(), m.markH(), color, 3);
        }
    }
}
