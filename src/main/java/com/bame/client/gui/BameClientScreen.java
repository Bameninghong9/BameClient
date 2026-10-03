package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import com.bame.client.module.AutoAreaMinerModule;
import com.bame.client.module.FullbrightModule;
import com.bame.client.module.KeyStrokesModule;
import com.bame.client.module.ZoomModule;
import com.bame.client.module.FpsModule;
import com.bame.client.module.PingModule;
import com.bame.client.module.CpsModule;
import com.bame.client.module.NameProtectModule;
import com.bame.client.module.ServerInfoModule;
import com.bame.client.module.ShowHudModule;
import com.bame.client.module.ClockModule;
import com.bame.client.module.CoordinatesModule;
import com.bame.client.module.PotionsModule;
import com.bame.client.module.TargetHudModule;
import com.bame.client.module.ArmorHudModule;
import com.bame.client.module.SpotifyHudModule;
import com.bame.client.module.ScoreboardModule;
import com.bame.client.module.CustomCrosshairModule;
import com.bame.client.module.InvMoveModule;
import com.bame.client.module.AutoClickerModule;
import com.bame.client.module.HitColorModule;
import com.bame.client.module.ReachDisplayModule;
import com.bame.client.module.LowShieldModule;
import com.bame.client.module.CustomHitboxesModule;
import com.bame.client.module.NoFogModule;
import com.bame.client.module.AutoToolModule;
import com.bame.client.module.BlockOutlineModule;
import com.bame.client.module.FreelookModule;
import com.bame.client.module.ItemSizeModule;
import com.bame.client.module.DurabilityGuardModule;
import com.bame.client.module.TimeChangerModule;
import com.bame.client.module.SkinProtectModule;
import com.bame.client.module.HandPositionModule;
import com.bame.client.render.CustomCrosshairRenderer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.PlayerSkinWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BameClientScreen extends Screen {
    private static final String[] CATEGORIES={"Combat","Movement","Visuals","Misc","World"};
    public static String selected="World";
    private boolean expanded,listening,scrollDragging,draggingWidth,fullbrightExpanded,draggingFullbright,listeningFullbright,listeningMenuBind,listeningZoom,listeningShowHud,listeningSpotify,listeningScoreboard,listeningCrosshair,listeningInvMove;
    private boolean listeningAutoClicker,listeningHitColor,listeningReachDisplay,listeningLowShield,listeningHitboxes,listeningNoFog,listeningAutoTool,listeningBlockOutline,listeningFreelook,listeningItemSize,listeningDurabilityGuard,listeningTimeChanger,listeningHandPosition;
    private boolean draggingCps,draggingAutoClickerDelay,draggingHitColorAlpha,draggingLowShieldHeight,draggingLowShieldTotem,draggingHitboxAlpha,draggingHitboxWidth,draggingBlockOutlineWidth,draggingBlockOutlineOpacity;
    private boolean draggingFreelookSensitivity,draggingItemScale,draggingItemYOffset,itemScrollDragging,draggingSoundVolume,draggingHoverVolume,draggingHandX,draggingHandY,draggingHandZ,draggingHandScale,draggingHandPitch,draggingHandYaw,draggingHandRoll;
    private boolean crosshairColorPickerOpen = false, hitColorColorPickerOpen = false, hitboxColorPickerOpen = false, blockOutlineColorPickerOpen = false;
    private float cpHue = 0f, cpSat = 0f, cpVal = 1f;
    private int cpDrag = -1; // 0=sv, 1=hue
    private int cpX, cpY;
    private int gridDragMode = -1;
    private int lastEditedCol = -1, lastEditedRow = -1;

    private boolean itemModalOpen = false;
    private int itemModalType = 0; // 0 = BLOCKS, 1 = TOOLS
    private final Set<String> tempSelectedItems = new LinkedHashSet<>();
    private final java.util.Map<String, Integer> tempToolThresholds = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, CustomTextFieldWidget> toolThresholdFields = new java.util.LinkedHashMap<>();
    private String itemSearch = "";
    private CustomTextFieldWidget itemSearchWidget;
    private double itemModalScroll = 0;
    private List<Item> cachedFilteredItems = null;
    private static List<Item> ALL_ITEMS = null;
    private static List<Item> ALL_TOOLS = null;

    private static List<Item> getAllItems() {
        if (ALL_ITEMS == null) {
            ALL_ITEMS = Registries.ITEM.stream()
                    .filter(i -> i != Items.AIR)
                    .toList();
        }
        return ALL_ITEMS;
    }

    private static List<Item> getAllTools() {
        if (ALL_TOOLS == null) {
            ALL_TOOLS = Registries.ITEM.stream()
                    .filter(i -> i != Items.AIR && new ItemStack(i).isDamageable())
                    .toList();
        }
        return ALL_TOOLS;
    }

    private void updateItemFilter() {
        List<Item> all = (itemModalType == 1) ? getAllTools() : getAllItems();
        if (itemSearch == null || itemSearch.trim().isEmpty()) {
            cachedFilteredItems = all;
        } else {
            String q = itemSearch.trim().toLowerCase(java.util.Locale.ROOT);
            cachedFilteredItems = all.stream().filter(item -> {
                String name = item.getName().getString().toLowerCase(java.util.Locale.ROOT);
                if (name.contains(q)) return true;
                String id = Registries.ITEM.getId(item).toString().toLowerCase(java.util.Locale.ROOT);
                return id.contains(q);
            }).toList();
        }
        itemModalScroll = 0;
    }

    private void openItemModal() {
        itemModalType = 0;
        tempSelectedItems.clear();
        tempSelectedItems.addAll(ItemSizeModule.selectedItems);
        itemSearch = "";
        if (itemSearchWidget != null) {
            itemSearchWidget.setText("");
            itemSearchWidget.setFocused(true);
            setFocused(itemSearchWidget);
        }
        updateItemFilter();
        itemModalOpen = true;
    }

    private void openToolModal() {
        itemModalType = 1;
        tempToolThresholds.clear();
        tempToolThresholds.putAll(DurabilityGuardModule.toolThresholds);
        itemSearch = "";
        if (itemSearchWidget != null) {
            itemSearchWidget.setText("");
            itemSearchWidget.setFocused(true);
            setFocused(itemSearchWidget);
        }
        updateItemFilter();
        itemModalOpen = true;
    }

    private void applyModalSave() {
        if (itemModalType == 1) {
            DurabilityGuardModule.toolThresholds.clear();
            DurabilityGuardModule.toolThresholds.putAll(tempToolThresholds);
            toolThresholdFields.keySet().retainAll(DurabilityGuardModule.toolThresholds.keySet());
            BameClientConfig.save();
            layout();
        } else {
            ItemSizeModule.selectedItems.clear();
            ItemSizeModule.selectedItems.addAll(tempSelectedItems);
            BameClientConfig.save();
            layout();
        }
        itemModalOpen = false;
    }
    private double scroll,scrollGrab;
    private int px,py,pw,ph,sidebar,cx,cy,cw,ch;
    private CustomTextFieldWidget search,corner1,corner2;
    private long openTime=0;
    private final OutlineColorPicker picker=new OutlineColorPicker();
    private final ThemeSettingsPanel themeSettings=new ThemeSettingsPanel();



    private final AmbientLighting ambient=new AmbientLighting();

    public BameClientScreen() { super(Text.literal("Caeser Client")); }
    private void text(DrawContext c,String s,int x,int y,int color) {
        c.drawText(textRenderer,CustomGuiUtils.getFontText(s),x,y,color,false);
    }
    private void box(DrawContext c,int x,int y,int w,int h,int color) {
        CustomGuiUtils.fillUltraRounded(c,x,y,w,h,color,6);
    }
    private boolean inside(double mx,double my,int x,int y,int w,int h) {
        return mx>=x&&mx<x+w&&my>=y&&my<y+h;
    }
    private void geometry() {
        pw = Math.min(width - 40, Math.max(640, (int)(width * 0.72)));
        ph = Math.min(height - 40, Math.max(400, (int)(height * 0.65)));
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        sidebar = 130;
        cx = px + sidebar + 16;
        cy = py + 58;
        cw = pw - sidebar - 32;
        ch = ph - 72;
    }
    @Override protected void init() {
        if (openTime == 0) openTime = System.currentTimeMillis();
        geometry();
        search=new CustomTextFieldWidget(px+pw-192,py+14,174,24,Text.literal("Search"));
        search.setPlaceholder("Search modules..."); search.setDrawsBackground(false);
        addSelectableChild(search);
        corner1=new CustomTextFieldWidget(0,0,cw-24,22,Text.literal("Corner 1"));
        corner2=new CustomTextFieldWidget(0,0,cw-24,22,Text.literal("Corner 2"));
        corner1.setText(format(AutoAreaMinerModule.corner1)); corner2.setText(format(AutoAreaMinerModule.corner2));
        corner1.setPlaceholder("X Y Z"); corner2.setPlaceholder("X Y Z");
        corner1.setChangedListener(s->{ AutoAreaMinerModule.corner1=parse(s); BameClientConfig.save(); });
        corner2.setChangedListener(s->{ AutoAreaMinerModule.corner2=parse(s); BameClientConfig.save(); });
        addSelectableChild(corner1); addSelectableChild(corner2);

        itemSearchWidget = new CustomTextFieldWidget(0, 0, 318, 20, Text.literal("Search"));
        itemSearchWidget.setPlaceholder("Search...");
        itemSearchWidget.setDrawsBackground(true);
        itemSearchWidget.setChangedListener(s -> {
            itemSearch = s;
            updateItemFilter();
        });
        addSelectableChild(itemSearchWidget);

        layout();
    }
    private boolean isVisible(String moduleName, String category) {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return moduleName.toLowerCase(java.util.Locale.ROOT).contains(q);
        return selected.equals(category);
    }
    private boolean fullbrightVisible() { return isVisible("Fullbright", "Visuals"); }
    private boolean minerVisible() { return isVisible("Auto Area Miner", "World"); }
    private boolean zoomVisible() { return isVisible("Zoom", "Visuals"); }
    private boolean showHudVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) {
            return "show hud".contains(q) || "hud".contains(q) || "fps".contains(q) || "ping".contains(q) ||
                   "cps".contains(q) || "keystrokes".contains(q) || "server info".contains(q) || "name protect".contains(q) ||
                   "clock".contains(q) || "coordinates".contains(q) || "potions".contains(q) || "target hud".contains(q) || "armor hud".contains(q);
        }
        return selected.equals("Visuals");
    }
    private boolean scoreboardVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "scoreboard".contains(q);
        return selected.equals("Visuals");
    }
    private boolean spotifyHudVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "spotify hud".contains(q) || "spotify".contains(q) || "music".contains(q) || "song".contains(q);
        return selected.equals("Visuals");
    }
    private boolean customCrosshairVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "custom crosshair".contains(q) || "crosshair".contains(q) || "fadenkreuz".contains(q);
        return selected.equals("Visuals");
    }
    private boolean invMoveVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "invmove".contains(q) || "inv move".contains(q) || "inventory walk".contains(q) || "inventory".contains(q) || "movement".contains(q);
        return selected.equals("Movement");
    }
    private boolean autoClickerVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "autoclicker".contains(q) || "auto clicker".contains(q) || "cps".contains(q);
        return selected.equals("Combat");
    }
    private boolean hitColorVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "hitcolor".contains(q) || "hit color".contains(q) || "color".contains(q) || "hurt".contains(q);
        return selected.equals("Combat");
    }
    private boolean reachDisplayVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "reachdisplay".contains(q) || "reach display".contains(q) || "reach".contains(q);
        return selected.equals("Combat");
    }
    private boolean lowShieldVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "lowshield".contains(q) || "low shield".contains(q) || "shield".contains(q) || "totem".contains(q);
        return selected.equals("Combat");
    }
    private boolean customHitboxesVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "customhitboxes".contains(q) || "custom hitboxes".contains(q) || "hitbox".contains(q) || "hitboxes".contains(q) || "f3+b".contains(q) || "esp".contains(q);
        return selected.equals("Combat");
    }
    private boolean noFogVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "nofog".contains(q) || "no fog".contains(q) || "fog".contains(q) || "nebel".contains(q);
        return selected.equals("World");
    }
    private boolean autoToolVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "autotool".contains(q) || "auto tool".contains(q) || "tool".contains(q) || "werkzeug".contains(q);
        return selected.equals("Misc");
    }
    private boolean blockOutlineVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "blockoutline".contains(q) || "block outline".contains(q) || "outline".contains(q) || "glow".contains(q);
        return selected.equals("Visuals");
    }
    private boolean freelookVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "freelook".contains(q) || "free look".contains(q) || "perspective".contains(q) || "360".contains(q) || "kamera".contains(q);
        return selected.equals("Movement");
    }
    private boolean itemSizeVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "itemsize".contains(q) || "item size".contains(q) || "item scale".contains(q) || "size".contains(q) || "größe".contains(q) || "blocks".contains(q) || "items".contains(q);
        return selected.equals("Visuals");
    }
    private boolean durabilityGuardVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "durabilityguard".contains(q) || "durability guard".contains(q) || "durability".contains(q) || "guard".contains(q) || "haltbarkeit".contains(q);
        return selected.equals("Misc");
    }
    private boolean timeChangerVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "timechanger".contains(q) || "time changer".contains(q) || "time".contains(q) || "zeit".contains(q) || "day".contains(q) || "night".contains(q);
        return selected.equals("World") || selected.equals("Visuals");
    }
    private int getTimeChangerHeight() {
        return TimeChangerModule.expanded ? 104 : 46;
    }
    private boolean handPositionVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "handposition".contains(q) || "hand position".contains(q) || "item position".contains(q) || "hand".contains(q) || "position".contains(q) || "offset".contains(q) || "viewmodel".contains(q) || "doom".contains(q) || "waffe".contains(q) || "schwert".contains(q);
        return selected.equals("Visuals");
    }
    private int getHandPositionHeight() {
        return HandPositionModule.expanded ? 234 : 46;
    }

    private int getFreelookHeight() {
        return FreelookModule.expanded ? 156 : 46;
    }
    private int getZoomHeight() {
        return ZoomModule.expanded ? 180 : 46;
    }
    private int getItemSizeHeight() {
        if (!ItemSizeModule.expanded) return 46;
        int itemRows = (ItemSizeModule.selectedItems.size() + 1) / 2;
        return 174 + (itemRows * 24);
    }
    private int getDurabilityGuardHeight() {
        if (!DurabilityGuardModule.expanded) return 46;
        return 146 + (DurabilityGuardModule.toolThresholds.size() * 24);
    }


    private void drawCustomSlider(DrawContext c, int sx, int sy, int sw, float normVal) {
        CustomGuiUtils.fillUltraRounded(c, sx, sy, sw, 4, 0xFF303442, 2);
        int fill = Math.round(sw * Math.clamp(normVal, 0.0f, 1.0f));
        if (fill > 0) {
            CustomGuiUtils.fillUltraRounded(c, sx, sy, fill, 4, GuiTheme.accent(), 2);
        }
        CustomGuiUtils.fillUltraRounded(c, sx + fill - 3, sy - 2, 7, 8, 0xFFFFFFFF, 4);
    }

    private int columns() { return 4; }
    private int effectsY() { return 44+((GuiTheme.PRESETS.length+columns()-1)/columns())*58+18; }
    private int settingsY() { return effectsY(); }

    private int getShowHudHeight() {
        if (!ShowHudModule.expanded) return 46;
        int extra = (ServerInfoModule.enabled ? 24 : 0) + (TargetHudModule.expanded ? 3 * 26 : 0) + (CoordinatesModule.expanded ? 8 * 26 : 0) + 28 + 10;
        return 46 + 8 + 10 * 26 + extra;
    }
    private int getScoreboardHeight() {
        return ScoreboardModule.expanded ? 78 : 46;
    }
    private int getSpotifyHudHeight() {
        return SpotifyHudModule.expanded ? 104 : 46;
    }
    private int getCustomCrosshairHeight() {
        return CustomCrosshairModule.expanded ? 218 : 46;
    }
    private int getInvMoveHeight() {
        return InvMoveModule.expanded ? 182 : 46;
    }
    private int getAutoClickerHeight() {
        return AutoClickerModule.expanded ? 260 : 46;
    }
    private int getHitColorHeight() {
        return HitColorModule.expanded ? 130 : 46;
    }
    private int getReachDisplayHeight() {
        return ReachDisplayModule.expanded ? 124 : 46;
    }
    private int getLowShieldHeight() {
        return LowShieldModule.expanded ? 130 : 46;
    }
    private int getCustomHitboxesHeight() {
        return CustomHitboxesModule.expanded ? 234 : 46;
    }
    private int getNoFogHeight() {
        return NoFogModule.expanded ? 182 : 46;
    }
    private int getAutoToolHeight() {
        return AutoToolModule.expanded ? 104 : 46;
    }
    private int getBlockOutlineHeight() {
        return BlockOutlineModule.expanded ? 182 : 46;
    }
    private int getMinerHeight() {
        return expanded ? 386 : 46;
    }

    private int contentHeight() { 
        if (selected.equals("Theme")) return settingsY()+themeSettings.height()+8;
        if (selected.equals("Settings")) return 340;
        int leftY = 0;
        int rightY = 0;
        if (minerVisible()) leftY += getMinerHeight() + 12;

        if (showHudVisible()) leftY += getShowHudHeight() + 12;
        if (scoreboardVisible()) leftY += getScoreboardHeight() + 12;
        if (invMoveVisible()) leftY += getInvMoveHeight() + 12;
        if (autoClickerVisible()) leftY += getAutoClickerHeight() + 12;
        if (reachDisplayVisible()) leftY += getReachDisplayHeight() + 12;
        if (autoToolVisible()) leftY += getAutoToolHeight() + 12;
        if (blockOutlineVisible()) leftY += getBlockOutlineHeight() + 12;
        if (timeChangerVisible()) leftY += getTimeChangerHeight() + 12;
        if (handPositionVisible()) leftY += getHandPositionHeight() + 12;

        if (noFogVisible()) rightY += getNoFogHeight() + 12;
        if (fullbrightVisible()) rightY += (fullbrightExpanded?92:46) + 12;
        if (zoomVisible()) rightY += getZoomHeight() + 12;
        if (spotifyHudVisible()) rightY += getSpotifyHudHeight() + 12;
        if (customCrosshairVisible()) rightY += getCustomCrosshairHeight() + 12;
        if (hitColorVisible()) rightY += getHitColorHeight() + 12;
        if (lowShieldVisible()) rightY += getLowShieldHeight() + 12;
        if (customHitboxesVisible()) rightY += getCustomHitboxesHeight() + 12;
        if (freelookVisible()) rightY += getFreelookHeight() + 12;
        if (itemSizeVisible()) rightY += getItemSizeHeight() + 12;
        if (durabilityGuardVisible()) rightY += getDurabilityGuardHeight() + 12;

        return Math.max(leftY, rightY);
    }
    private double maxScroll() { return Math.max(0,contentHeight()-ch); }
    private int baseY() { return cy-(int)scroll; }
    private void layout() {
        scroll=Math.clamp(scroll,0,maxScroll());
        int yOffset = 0;
        int leftY = baseY();
        int gap = 16;
        int halfW = (cw - gap) / 2;
        if(minerVisible()) {
            corner1.setX(cx+12); corner1.setY(leftY+64); corner1.setWidth(halfW-24);
            corner2.setX(cx+12); corner2.setY(leftY+104); corner2.setWidth(halfW-24);
            corner1.visible=corner2.visible=expanded;
            corner1.active=corner1.visible && corner1.getY()+22>cy && corner1.getY()<cy+ch;
            corner2.active=corner2.visible && corner2.getY()+22>cy && corner2.getY()<cy+ch;
            if(!corner1.active) corner1.setFocused(false);
            if(!corner2.active) corner2.setFocused(false);
            if(!corner1.visible) { corner1.setFocused(false); corner2.setFocused(false); }
            picker.layout(cx+12,leftY+282,halfW-24);
            leftY += getMinerHeight() + 12;
        } else {
            corner1.visible=corner2.visible=false;
            corner1.active=corner2.active=false;
            corner1.setFocused(false); corner2.setFocused(false);
        }

        if (showHudVisible()) leftY += getShowHudHeight() + 12;

        themeSettings.layout(cx,baseY()+settingsY(),cw);
    }
    private static String format(BlockPos p) { return p==null?"":p.getX()+" "+p.getY()+" "+p.getZ(); }
    private static BlockPos parse(String s) {
        try { var a=s.trim().split("\\s+"); if(a.length==3) return new BlockPos(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2])); }
        catch(NumberFormatException ignored) {} return null;
    }
    private boolean isShiftDown() {
        if (client == null || client.getWindow() == null) return false;
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
    private boolean isSecretComboPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT ||
            key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL ||
            key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT) {
            return false;
        }
        if (key != com.bame.client.BameClientConfig.secretKey) {
            return false;
        }
        int mods = input.modifiers();
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0 || isShiftDown();
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean alt = (mods & GLFW.GLFW_MOD_ALT) != 0;
        if (client != null && client.getWindow() != null) {
            long handle = client.getWindow().getHandle();
            if (!ctrl) ctrl = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            if (!alt) alt = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
        }

        return shift == com.bame.client.BameClientConfig.secretRequireShift
                && ctrl == com.bame.client.BameClientConfig.secretRequireCtrl
                && alt == com.bame.client.BameClientConfig.secretRequireAlt;
    }
    private String formatKey(int key) {
        if(key<0) return "None";
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) return "RSHIFT";
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) return "LSHIFT";
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) return "RCTRL";
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) return "LCTRL";
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) return "RALT";
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) return "LALT";
        String s = org.lwjgl.glfw.GLFW.glfwGetKeyName(key,0);
        if(s!=null) return s.toUpperCase(java.util.Locale.ROOT);
        return net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString().toUpperCase(java.util.Locale.ROOT);
    }

    
    private String animName() {
        int a = com.bame.client.BameClientConfig.guiAnimation;
        switch (a) {
            case 0: return "Fade In";
            case 1: return "Slide In";
            case 2: return "Elastic Bounce";
            case 3: return "Swirl Twist";
            default: return "None";
        }
    }

    private String keyNameMenuBind() {
        if(listeningMenuBind) return "...";
        return formatKey(com.bame.client.BameClientConfig.menuBind);
    }
    private String keyNameFullbright() {
        if(listeningFullbright) return "...";
        return formatKey(com.bame.client.module.FullbrightModule.keyBind);
    }
    private String keyName() {
        if(listening) return "...";
        if(AutoAreaMinerModule.keyBind<0) return "None";
        String s=GLFW.glfwGetKeyName(AutoAreaMinerModule.keyBind,0);
        return s==null?"Key "+AutoAreaMinerModule.keyBind:s.toUpperCase(java.util.Locale.ROOT);
    }
    private void modeButton(DrawContext c,String label,int x,int y,int w,int h,int mx,int my,boolean selected) {
        boolean hover=inside(mx,my,x,y,w,h);
        CustomGuiUtils.fillUltraRounded(c,x,y,w,h,selected?GuiTheme.alpha(GuiTheme.accent(), 80):(hover?0xFF252A34:0xFF181C24),4);
        CustomGuiUtils.drawUltraRoundedOutline(c,x,y,w,h,selected?GuiTheme.accent():(hover?0xFF606575:0xFF292D36),4);
        int tw=textRenderer.getWidth(CustomGuiUtils.getFontText(label));
        text(c,label,x+(w-tw)/2,y+(h-8)/2,selected?0xFFFFFFFF:0xFFD4D8E0);
    }
    private void button(DrawContext c,String label,int x,int y,int w,int h,int mx,int my) {
        boolean hover=inside(mx,my,x,y,w,h);
        CustomGuiUtils.fillUltraRounded(c,x,y,w,h,hover?0xFF252A34:0xFF181C24,4);
        CustomGuiUtils.drawUltraRoundedOutline(c,x,y,w,h,hover?GuiTheme.accent():0xFF292D36,4);
        int tw=textRenderer.getWidth(CustomGuiUtils.getFontText(label));
        text(c,label,x+(w-tw)/2,y+(h-8)/2,0xFFD4D8E0);
    }
    private void toggle(DrawContext c,int x,int y,boolean on,int mx,int my,float delta) {
        new CustomToggleWidget(x,y,26,14,on).render(c,mx,my,delta);
    }
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta) {
        // This screen draws its own dimming; only the world behind it is blurred.
        if(BameClientConfig.frostedBlur) applyBlur(c);
    }
    @Override public void render(DrawContext c,int mx,int my,float delta) {
        layout();
        float progress = Math.min(1.0f, (System.currentTimeMillis() - openTime) / 400.0f);
        float p = 1.0f - (float)Math.pow(1.0f - progress, 4);
        int anim = com.bame.client.BameClientConfig.guiAnimation;
        if (progress < 1.0f) {
            
        }
        
        int dimAlpha = (int)((BameClientConfig.seeThrough?0x44:0x88) * p);
        c.fill(0,0,width,height, (dimAlpha << 24) | 0x000000);
        
        if (progress < 1.0f && anim != 4) {
            c.getMatrices().pushMatrix();
            float centerX = (float)width / 2.0f;
            float centerY = (float)height / 2.0f;
            if (anim == 0) {
                c.getMatrices().translate(centerX, centerY);
                float s = 0.6f + 0.4f * p;
                c.getMatrices().scale(s, s);
                c.getMatrices().translate(-centerX, -centerY);
            } else if (anim == 1) {
                c.getMatrices().translate(0f, (float)height * (1.0f - p));
            } else if (anim == 2) {
                c.getMatrices().translate(centerX, centerY);
                float s = 0.65f + 0.35f * p + (float)(Math.sin(progress * Math.PI) * 0.12f * (1.0f - progress));
                c.getMatrices().scale(s, s);
                c.getMatrices().translate(-centerX, -centerY);
            } else if (anim == 3) {
                c.getMatrices().translate(centerX, centerY);
                float s = 0.70f + 0.30f * p;
                float rad = (float)Math.toRadians((1.0f - p) * -5.0f);
                c.getMatrices().rotate(rad);
                c.getMatrices().scale(s, s);
                c.getMatrices().translate(-centerX, -centerY);
            }
        }

        int bg=GuiTheme.alpha(GuiTheme.current().background(),BameClientConfig.seeThrough?205:255);
        box(c,px,py,pw,ph,bg);

        if (BameClientConfig.customWallpaper) {
            com.bame.client.wallpaper.WallpaperManager.render(c, px, py, pw, ph, p);
        }

        CustomGuiUtils.drawUltraRoundedOutline(c,px,py,pw,ph,0xFF292D36);
        ambient.render(c,px+sidebar+1,py+1,pw-sidebar-9,ph-9);
        boolean hoverC = inside(mx, my, px + 10, py + 11, 28, 28);
        if (com.bame.client.BameClientConfig.secretUnlocked && hoverC) {
            CustomGuiUtils.fillUltraRounded(c, px + 10, py + 11, 28, 28, GuiTheme.alpha(GuiTheme.accent(), 40), 6);
            CustomGuiUtils.drawUltraRoundedOutline(c, px + 10, py + 11, 28, 28, GuiTheme.accent(), 6);
            com.bame.client.sound.ClientSoundManager.playHover("c_logo_secret");
        }
        CustomGuiUtils.drawCLogo(c, px + 14, py + 15, 20);
        text(c,"CAESER",px+40,py+21,0xFFFFFFFF);
        text(c,"MODULES",px+14,py+56,0xFF7F8694);
        int step=Math.min(26,Math.max(17,(ph-165)/6));
        for(int i=0;i<CATEGORIES.length;i++) {
            int y=py+73+i*step; boolean active=selected.equals(CATEGORIES[i]);
            if(inside(mx, my, px+6, y-3, sidebar-12, 22)) com.bame.client.sound.ClientSoundManager.playHover("cat_" + CATEGORIES[i]);
            if(active) box(c,px+6,y-3,sidebar-12,22,GuiTheme.alpha(GuiTheme.accent(),40));
            int color=active?GuiTheme.accent():0xFFABB1BE;
            text(c,CATEGORIES[i],px+34,y+5,color);
            switch(i) {
                case 0 -> CustomGuiUtils.drawCombatIcon(c,px+15,y+2,color);
                case 1 -> CustomGuiUtils.drawMovementIcon(c,px+15,y+2,color);
                case 2 -> CustomGuiUtils.drawVisualsIcon(c,px+15,y+2,color);
                case 3 -> CustomGuiUtils.drawMiscIcon(c,px+15,y+2,color);
                case 4 -> CustomGuiUtils.drawGlobeIcon(c,px+15,y+2,color);
            }
        }
        int gy=py+73+5*step+9;
        text(c,"GENERAL",px+14,gy,0xFF7F8694);
        if(inside(mx, my, px+6, gy+16, sidebar-12, 23)) com.bame.client.sound.ClientSoundManager.playHover("tab_theme");
        if(selected.equals("Theme")) box(c,px+6,gy+16,sidebar-12,23,GuiTheme.alpha(GuiTheme.accent(),40));
        int tc=selected.equals("Theme")?GuiTheme.accent():0xFFABB1BE;
        CustomGuiUtils.drawSmoothRing(c,px+21,gy+27,5,1,tc);
        for(int i=0;i<3;i++) box(c,px+18+i*3,gy+24,2,2,tc);
        text(c,"Theme",px+34,gy+24,tc);
        
        if(inside(mx, my, px+6, gy+39, sidebar-12, 23)) com.bame.client.sound.ClientSoundManager.playHover("tab_settings");
        if(selected.equals("Settings")) box(c,px+6,gy+39,sidebar-12,23,GuiTheme.alpha(GuiTheme.accent(),40));
        int sc=selected.equals("Settings")?GuiTheme.accent():0xFFABB1BE;
        CustomGuiUtils.drawGearIcon(c,px+15,gy+44,sc);
        text(c,"Settings",px+34,gy+47,sc);
        int profileY=py+ph-40;
        box(c,px+8,profileY,sidebar-16,30,0xFF14181F);
        net.minecraft.entity.player.SkinTextures profSkin = (SkinProtectModule.enabled)
                ? SkinProtectModule.getCurrentSkin()
                : (client.player != null ? client.player.getSkin() : net.minecraft.client.util.DefaultSkinHelper.getSteve());
        net.minecraft.client.gui.PlayerSkinDrawer.draw(c, profSkin, px+13, profileY+5, 20);
        c.enableScissor(px+38,profileY,px+sidebar-12,profileY+30);
        String pName = (client.player != null) ? client.player.getName().getString() : "Player";
        if (NameProtectModule.enabled) pName = NameProtectModule.getProtectedName(pName);
        text(c,pName,px+38,profileY+11,0xFFD4D8E0); c.disableScissor();
        box(c,px+pw-210,py+14,196,24,0xFF12161C);
        CustomGuiUtils.drawUltraRoundedOutline(c,px+pw-210,py+14,196,24,search.isFocused()?GuiTheme.accent():0xFF292D36);
        CustomGuiUtils.drawSearchIcon(c,px+pw-204,py+20,0xFFABB1BE);
        if(cw>260) text(c,java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),cx,py+22,GuiTheme.accent());
        search.render(c,mx,my,delta);
        c.fill(cx,py+48,cx+cw,py+49,GuiTheme.alpha(GuiTheme.accent(),180));
        c.enableScissor(cx,cy,cx+cw,cy+ch);
        if(selected.equals("Theme")) renderTheme(c,mx,my,delta);
        else if(selected.equals("Settings")) renderSettings(c,mx,my,delta);
        else {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int leftY = 0;
            int rightY = 0;
            if (minerVisible()) {
                renderMiner(c, mx, my, delta, cx, leftY, halfW);
                leftY += getMinerHeight() + 12;
            }
            if (showHudVisible()) {
                renderShowHudModule(c, mx, my, delta, cx, leftY, halfW);
                leftY += getShowHudHeight() + 12;
            }
            if (scoreboardVisible()) {
                renderScoreboardModule(c, mx, my, delta, cx, leftY, halfW);
                leftY += getScoreboardHeight() + 12;
            }
            if (invMoveVisible()) {
                renderInvMoveModule(c, mx, my, delta, cx, leftY, halfW);
                leftY += getInvMoveHeight() + 12;
            }
            if (autoClickerVisible()) {
                renderAutoClickerModule(c, mx, my, delta, cx, leftY, halfW);
                leftY += getAutoClickerHeight() + 12;
            }
            if (reachDisplayVisible()) {
                renderReachDisplayModule(c, mx, my, delta, cx, leftY, halfW);
                leftY += getReachDisplayHeight() + 12;
            }
            if (autoToolVisible()) {
                renderAutoToolModule(c, mx, my, delta, cx, leftY, halfW);
                leftY += getAutoToolHeight() + 12;
            }
            if (blockOutlineVisible()) {
                renderBlockOutlineModule(c, mx, my, delta, cx, leftY, halfW);
                leftY += getBlockOutlineHeight() + 12;
            }
            if (timeChangerVisible()) {
                renderTimeChangerModule(c, mx, my, delta, cx, leftY, halfW);
                leftY += getTimeChangerHeight() + 12;
            }
            if (handPositionVisible()) {
                renderHandPositionModule(c, mx, my, delta, cx, leftY, halfW);
                leftY += getHandPositionHeight() + 12;
            }
            if (noFogVisible()) {
                renderNoFogModule(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += getNoFogHeight() + 12;
            }
            if (fullbrightVisible()) {
                renderFullbright(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += (fullbrightExpanded?92:46) + 12;
            }
            if (zoomVisible()) {
                renderZoom(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += getZoomHeight() + 12;
            }
            if (spotifyHudVisible()) {
                renderSpotifyHudModule(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += getSpotifyHudHeight() + 12;
            }
            if (customCrosshairVisible()) {
                renderCustomCrosshairModule(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += getCustomCrosshairHeight() + 12;
            }
            if (hitColorVisible()) {
                renderHitColorModule(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += getHitColorHeight() + 12;
            }
            if (lowShieldVisible()) {
                renderLowShieldModule(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += getLowShieldHeight() + 12;
            }
            if (customHitboxesVisible()) {
                renderCustomHitboxesModule(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += getCustomHitboxesHeight() + 12;
            }
            if (freelookVisible()) {
                renderFreelookModule(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += getFreelookHeight() + 12;
            }
            if (itemSizeVisible()) {
                renderItemSizeModule(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += getItemSizeHeight() + 12;
            }
            if (durabilityGuardVisible()) {
                renderDurabilityGuardModule(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += getDurabilityGuardHeight() + 12;
            }
        }
        c.disableScissor();
        if(maxScroll()>0) {
            box(c,px+pw-10,cy,4,ch,0xFF272C35);
            box(c,px+pw-10,thumbY(),4,thumbHeight(),scrollDragging?0xFFFFFFFF:GuiTheme.accent());
        }
        
        if (progress < 1.0f && anim != 4) {
            c.getMatrices().popMatrix();
        }

        if (crosshairColorPickerOpen || hitColorColorPickerOpen || hitboxColorPickerOpen || blockOutlineColorPickerOpen) {
            renderModalColorPicker(c, mx, my);
        }
        if (itemModalOpen) {
            renderItemModal(c, mx, my, delta);
        }
    }
    private void renderMiner(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getMinerHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "Auto Area Miner", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);
        button(c, keyName(), x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, AutoAreaMinerModule.enabled, mx, my, delta);
        if (!expanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);
        text(c, "Corner 1", x + 12, y + 52, 0xFFABB1BE);
        corner1.render(c, mx, my, delta);
        text(c, "Corner 2", x + 12, y + 92, 0xFFABB1BE);
        corner2.render(c, mx, my, delta);
        text(c, "Corner 1 - Looking", x + 12, y + 138, 0xFFABB1BE);
        button(c, "Set", x + w - 46, y + 134, 34, 18, mx, my);
        text(c, "Corner 2 - Looking", x + 12, y + 158, 0xFFABB1BE);
        button(c, "Set", x + w - 46, y + 154, 34, 18, mx, my);
        button(c, AutoAreaMinerModule.mode3x3 ? "Mode: 3x3 Pickaxe" : "Mode: Normal Pickaxe", x + 12, y + 178, w - 24, 20, mx, my);
        text(c, "Style", x + 12, y + 204, 0xFFD4D8E0);
        int mode = BameClientConfig.renderMode;
        int btnW = (w - 24 - 6) / 2;
        modeButton(c, "Clean", x + 12, y + 218, btnW, 18, mx, my, mode == 0);
        modeButton(c, "Outline", x + 12 + btnW + 6, y + 218, btnW, 18, mx, my, mode == 1);
        modeButton(c, "Corners", x + 12, y + 240, btnW, 18, mx, my, mode == 2);
        modeButton(c, "Pulse", x + 12 + btnW + 6, y + 240, btnW, 18, mx, my, mode == 3);

        text(c, "Outline Color", x + 12, y + 266, 0xFFD4D8E0);
        int swatchX = x + w - 28;
        box(c, swatchX, y + 264, 16, 12, 0xFF777777);
        box(c, swatchX, y + 264, 16, 12, BameClientConfig.outlineColor);

        picker.render(c);

        int sliderY = y + 352;
        String widthStr = String.format(java.util.Locale.US, "%.1f", BameClientConfig.outlineWidth);
        text(c, "Width: " + widthStr, x + 12, sliderY, 0xFFD4D8E0);
        int sw = w - 24;
        int sx = x + 12;
        CustomGuiUtils.fillUltraRounded(c, sx, sliderY + 14, sw, 4, 0xFF303442, 2);
        float widthVal = (BameClientConfig.outlineWidth - 1.0f) / 4.0f;
        int fill = Math.round(sw * Math.clamp(widthVal, 0f, 1f));
        if (fill > 0) CustomGuiUtils.fillUltraRounded(c, sx, sliderY + 14, fill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sx + fill - 3, sliderY + 12, 7, 8, 0xFFFFFFFF, 4);
    }
    private void renderFullbright(DrawContext c,int mx,int my,float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        box(c,x,y,w,fullbrightExpanded?92:46,GuiTheme.alpha(GuiTheme.surface(),com.bame.client.BameClientConfig.seeThrough?210:255));
        text(c,"Fullbright",x+12,y+12,0xFFE2E5ED);
        text(c,"KeyBind:",x+12,y+29,0xFF8E95A4);
        button(c,keyNameFullbright(),x+60,y+25,48,16,mx,my);
        toggle(c,x+w-38,y+12,com.bame.client.module.FullbrightModule.enabled,mx,my,delta);
        
        if (!fullbrightExpanded) return;
        c.fill(x+8,y+46,x+w-8,y+47,0xFF292D36);
        
        int sliderY = y+58;
        text(c,"Intensity",x+12,sliderY,0xFFD4D8E0);
        
        int sx = x+12;
        int sw = w-24;
        CustomGuiUtils.fillUltraRounded(c,sx,sliderY+20,sw,4,0xFF303442,2);
        
        float val = com.bame.client.module.FullbrightModule.intensity;
        int fill = Math.round(sw * val);
        if(fill>0) CustomGuiUtils.fillUltraRounded(c,sx,sliderY+20,fill,4,GuiTheme.accent(),2);
        CustomGuiUtils.fillUltraRounded(c,sx+fill-3,sliderY+18,7,8,0xFFFFFFFF,4);
        
        text(c,Math.round(val*100)+"%",sx+w-38,sliderY+8,0xFFD4D8E0);
    }
    
    private void renderShowHudModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getShowHudHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "Show HUD", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);
        String kb = listeningShowHud ? "..." : formatKey(ShowHudModule.keyBind);
        button(c, kb, x + 58, y + 25, 42, 16, mx, my);
        toggle(c, x + w - 38, y + 12, ShowHudModule.enabled, mx, my, delta);

        if (!ShowHudModule.expanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;
        // 1. Clock
        text(c, "Clock", x + 14, curY + 5, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 3, ClockModule.enabled, mx, my, delta);
        curY += 26;

        // 2. Coordinates
        boolean coordHover = inside(mx, my, x + 8, curY, w - 50, 22);
        if (coordHover) {
            CustomGuiUtils.fillUltraRounded(c, x + 8, curY, w - 50, 22, 0x14FFFFFF, 4);
        }
        text(c, (CoordinatesModule.expanded ? "- " : "+ ") + "Coordinates", x + 14, curY + 5, CoordinatesModule.expanded ? GuiTheme.accent() : 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 3, CoordinatesModule.enabled, mx, my, delta);
        curY += 26;

        if (CoordinatesModule.expanded) {
            // Sub 1: Layout
            text(c, "Layout", x + 24, curY + 4, 0xFFB8BCC6);
            button(c, CoordinatesModule.getLayoutName(), x + w - 76, curY, 64, 16, mx, my);
            curY += 26;

            // Sub 2: Style
            text(c, "Style", x + 24, curY + 4, 0xFFB8BCC6);
            button(c, CoordinatesModule.getStyleName(), x + w - 76, curY, 64, 16, mx, my);
            curY += 26;

            // Sub 3: Show X
            text(c, "Show X", x + 24, curY + 5, 0xFFB8BCC6);
            toggle(c, x + w - 38, curY + 3, CoordinatesModule.showX, mx, my, delta);
            curY += 26;

            // Sub 4: Show Y
            text(c, "Show Y", x + 24, curY + 5, 0xFFB8BCC6);
            toggle(c, x + w - 38, curY + 3, CoordinatesModule.showY, mx, my, delta);
            curY += 26;

            // Sub 5: Show Z
            text(c, "Show Z", x + 24, curY + 5, 0xFFB8BCC6);
            toggle(c, x + w - 38, curY + 3, CoordinatesModule.showZ, mx, my, delta);
            curY += 26;

            // Sub 6: Decimals (.0)
            text(c, "Decimals (.0)", x + 24, curY + 5, 0xFFB8BCC6);
            toggle(c, x + w - 38, curY + 3, CoordinatesModule.decimals, mx, my, delta);
            curY += 26;

            // Sub 7: Nether Coords
            text(c, "Nether Coords", x + 24, curY + 5, 0xFFB8BCC6);
            toggle(c, x + w - 38, curY + 3, CoordinatesModule.showNether, mx, my, delta);
            curY += 26;

            // Sub 8: Direction
            text(c, "Direction", x + 24, curY + 5, 0xFFB8BCC6);
            toggle(c, x + w - 38, curY + 3, CoordinatesModule.showDirection, mx, my, delta);
            curY += 26;
        }

        // 3. Potions
        text(c, "Potions", x + 14, curY + 5, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 3, PotionsModule.enabled, mx, my, delta);
        curY += 26;

        // 4. Target HUD
        boolean thHover = inside(mx, my, x + 8, curY, w - 16, 22);
        if (thHover) {
            CustomGuiUtils.fillUltraRounded(c, x + 8, curY, w - 16, 22, 0x14FFFFFF, 4);
        }
        text(c, (TargetHudModule.expanded ? "- " : "+ ") + "Target HUD", x + 14, curY + 5, TargetHudModule.expanded ? GuiTheme.accent() : 0xFFD4D8E0);
        curY += 26;

        if (TargetHudModule.expanded) {
            // Sub 1: Show Hearts
            text(c, "Show Hearts", x + 24, curY + 5, 0xFFB8BCC6);
            toggle(c, x + w - 38, curY + 3, TargetHudModule.showHearts, mx, my, delta);
            curY += 26;

            // Sub 2: Show Armor
            text(c, "Show Armor", x + 24, curY + 5, 0xFFB8BCC6);
            toggle(c, x + w - 38, curY + 3, TargetHudModule.showArmor, mx, my, delta);
            curY += 26;

            // Sub 3: Target Players Only
            text(c, "Target Players Only", x + 24, curY + 5, 0xFFB8BCC6);
            toggle(c, x + w - 38, curY + 3, TargetHudModule.playersOnly, mx, my, delta);
            curY += 26;
        }

        // 6. Armor HUD
        text(c, "Armor HUD", x + 14, curY + 5, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 3, ArmorHudModule.enabled, mx, my, delta);
        curY += 26;

        // 7. Keystrokes
        text(c, "Keystrokes", x + 14, curY + 5, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 3, KeyStrokesModule.enabled, mx, my, delta);
        curY += 26;

        // 8. CPS
        text(c, "CPS", x + 14, curY + 5, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 3, CpsModule.enabled, mx, my, delta);
        curY += 26;

        // 9. FPS
        text(c, "FPS", x + 14, curY + 5, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 3, FpsModule.enabled, mx, my, delta);
        curY += 26;

        // 10. Ping
        text(c, "Ping", x + 14, curY + 5, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 3, PingModule.enabled, mx, my, delta);
        curY += 26;

        // 11. Server Info
        text(c, "Server Info", x + 14, curY + 5, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 3, ServerInfoModule.enabled, mx, my, delta);
        curY += 26;
        if (ServerInfoModule.enabled) {
            modeButton(c, "Name", x + 14, curY + 2, 44, 18, mx, my, ServerInfoModule.showName);
            modeButton(c, "Server", x + 62, curY + 2, 48, 18, mx, my, ServerInfoModule.showServer);
            modeButton(c, "Time", x + 114, curY + 2, 42, 18, mx, my, ServerInfoModule.showTime);
            curY += 24;
        }

        // Bottom buttons in dropdown
        curY += 4;
        button(c, "Edit HUD", x + 14, curY, 50, 16, mx, my);
    }

    private void renderZoom(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getZoomHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "Zoom", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);
        
        String kb = listeningZoom ? "..." : formatKey(ZoomModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        
        toggle(c, x + w - 38, y + 12, ZoomModule.enabled, mx, my, delta);
        if (!ZoomModule.expanded) return;
        
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;

        // Row 1: Mode (Hold / Toggle)
        text(c, "Mode", x + 14, curY + 4, 0xFFD4D8E0);
        button(c, ZoomModule.type == 0 ? "Hold" : "Toggle", x + w - 58, curY, 46, 16, mx, my);
        curY += 26;

        // Row 2: Animation (Smooth / Instant)
        text(c, "Animation", x + 14, curY + 4, 0xFFD4D8E0);
        button(c, ZoomModule.mode == 0 ? "Smooth" : "Instant", x + w - 68, curY, 56, 16, mx, my);
        curY += 26;

        // Row 3: Mouse Scroll Zoom
        text(c, "Scroll Zoom", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, ZoomModule.scrollZoom, mx, my, delta);
        curY += 26;

        // Row 4: Default Zoom Level
        text(c, "Default Zoom", x + 14, curY + 4, 0xFFD4D8E0);
        button(c, ZoomModule.getDefaultZoomName(), x + w - 48, curY, 36, 16, mx, my);
        curY += 26;

        // Row 5: Reset button
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderSpotifyHudModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getSpotifyHudHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "Spotify HUD", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningSpotify ? "..." : formatKey(SpotifyHudModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, SpotifyHudModule.enabled, mx, my, delta);

        if (!SpotifyHudModule.expanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;
        text(c, "Auto-Hide (wenn pausiert)", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, SpotifyHudModule.autoHide, mx, my, delta);

        curY += 24;
        button(c, "Edit HUD", x + 14, curY, 50, 16, mx, my);
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderScoreboardModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getScoreboardHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "Scoreboard", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningScoreboard ? "..." : formatKey(ScoreboardModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, ScoreboardModule.enabled, mx, my, delta);

        if (!ScoreboardModule.expanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;
        button(c, "Edit HUD", x + 14, curY, 50, 16, mx, my);
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderCustomCrosshairModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getCustomCrosshairHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "Custom Crosshair", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningCrosshair ? "..." : formatKey(CustomCrosshairModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, CustomCrosshairModule.enabled, mx, my, delta);

        if (!CustomCrosshairModule.expanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        // 1. 15x15 Pixel Editor Grid (Bild 1) & 1:1 Live Preview Box (CROSSHAIR AT TOP)
        int gridY = y + 52;
        int gridX = x + 12;
        int cellSize = 7;
        int gridW = 15 * cellSize; // 105px
        int gridH = 15 * cellSize; // 105px

        CustomGuiUtils.fillUltraRounded(c, gridX - 1, gridY - 1, gridW + 2, gridH + 2, 0xFF14171E, 3);
        CustomGuiUtils.drawUltraRoundedOutline(c, gridX - 1, gridY - 1, gridW + 2, gridH + 2, 0xFF353C4D, 3);

        for (int r = 0; r < 15; r++) {
            for (int col = 0; col < 15; col++) {
                int cellX = gridX + col * cellSize;
                int cellY = gridY + r * cellSize;
                boolean active = CustomCrosshairModule.grid[r][col];
                int cellBg = active ? CustomCrosshairModule.color : 0xFF16181F;
                c.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, cellBg);
                // Grid cell borders (subtle lines)
                c.fill(cellX, cellY + cellSize - 1, cellX + cellSize, cellY + cellSize, 0x1EFFFFFF);
                c.fill(cellX + cellSize - 1, cellY, cellX + cellSize, cellY + cellSize - 1, 0x1EFFFFFF);

                // Center cell (7, 7) - Blue outline (matching Bild 1)
                if (col == 7 && r == 7) {
                    int blue = 0xFF3B82F6;
                    c.fill(cellX, cellY, cellX + cellSize, cellY + 1, blue);
                    c.fill(cellX, cellY + cellSize - 1, cellX + cellSize, cellY + cellSize, blue);
                    c.fill(cellX, cellY + 1, cellX + 1, cellY + cellSize - 1, blue);
                    c.fill(cellX + cellSize - 1, cellY + 1, cellX + cellSize, cellY + cellSize - 1, blue);
                }
            }
        }

        // Preview box (to the right of the grid)
        int prevX = gridX + gridW + 12;
        int prevW = Math.max(50, w - gridW - 24);
        int prevH = gridH;
        CustomGuiUtils.fillUltraRounded(c, prevX, gridY, prevW, prevH, 0x880D1117, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, prevX, gridY, prevW, prevH, 0xFF2A2E39, 4);

        int pcx = prevX + prevW / 2;
        int pcy = gridY + prevH / 2;
        CustomCrosshairRenderer.renderAt(c, pcx, pcy, 1, CustomCrosshairModule.color);

        // 2. Presets row (UNTER DAS CROSSHAIR)
        int pY = gridY + gridH + 8; // y + 165
        text(c, "PRESETS", x + 12, pY + 4, 0xFF8E95A4);
        int pBtnSize = 17;
        int pGap = 2;
        int pStartX = x + 58;
        for (int i = 0; i < 9; i++) {
            int bx = pStartX + i * (pBtnSize + pGap);
            int by = pY;
            boolean hoverP = inside(mx, my, bx, by, pBtnSize, pBtnSize);
            c.fill(bx, by, bx + pBtnSize, by + pBtnSize, 0xFF151820);
            CustomGuiUtils.drawUltraRoundedOutline(c, bx, by, pBtnSize, pBtnSize, hoverP ? GuiTheme.accent() : 0xFF2A2E39, 2);
            boolean[][] tmpl = CustomCrosshairModule.getPresetTemplate(i);
            for (int r = 0; r < 15; r++) {
                for (int col = 0; col < 15; col++) {
                    if (tmpl[r][col]) {
                        c.fill(bx + 1 + col, by + 1 + r, bx + 2 + col, by + 2 + r, 0xFFFFFFFF);
                    }
                }
            }
        }

        // 3. Color, Clear on the left & Reset button on the bottom right (UNTEN RECHTS)
        int actY = pY + 23; // y + 188
        text(c, "Color:", x + 12, actY + 4, 0xFFD4D8E0);
        int colBtnX = x + 44;
        int colBtnY = actY;
        int colBtnSize = 16;
        boolean hoverCol = inside(mx, my, colBtnX, colBtnY, colBtnSize, colBtnSize);
        c.fill(colBtnX, colBtnY, colBtnX + colBtnSize, colBtnY + colBtnSize, CustomCrosshairModule.color);
        CustomGuiUtils.drawUltraRoundedOutline(c, colBtnX, colBtnY, colBtnSize, colBtnSize, hoverCol ? 0xFFFFFFFF : 0xFF353C4D, 2);

        button(c, "Clear", x + 68, actY, 38, 16, mx, my);
        button(c, "Reset", x + w - 58, actY, 46, 16, mx, my);
    }

    private void renderInvMoveModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getInvMoveHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "InvMove", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningInvMove ? "..." : formatKey(InvMoveModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, InvMoveModule.enabled, mx, my, delta);

        if (!InvMoveModule.expanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;
        text(c, "Jump", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, InvMoveModule.jump, mx, my, delta);
        curY += 26;

        text(c, "Sprint", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, InvMoveModule.sprint, mx, my, delta);
        curY += 26;

        text(c, "Sneak", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, InvMoveModule.sneak, mx, my, delta);
        curY += 26;

        text(c, "Arrow Key Rotation", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, InvMoveModule.rotateWithArrows, mx, my, delta);
        curY += 26;

        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderAutoClickerModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getAutoClickerHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "AutoClicker", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningAutoClicker ? "..." : formatKey(AutoClickerModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, AutoClickerModule.enabled, mx, my, delta);

        if (!AutoClickerModule.expanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;
        // Row 1: Mode Selection [CPS] [Delay] [Cooldown]
        text(c, "Mode:", x + 14, curY + 4, 0xFFD4D8E0);
        modeButton(c, "CPS", x + 54, curY + 2, 38, 18, mx, my, AutoClickerModule.mode == 0);
        modeButton(c, "Delay", x + 96, curY + 2, 44, 18, mx, my, AutoClickerModule.mode == 1);
        modeButton(c, "Cooldown", x + 144, curY + 2, 58, 18, mx, my, AutoClickerModule.mode == 2);
        curY += 26;

        // Row 2: Dynamic setting depending on Mode
        if (AutoClickerModule.mode == 0) {
            // CPS Slider (1 to 20 CPS)
            text(c, "CPS: " + AutoClickerModule.cps, x + 14, curY + 4, 0xFFD4D8E0);
            int sx = x + 72;
            int sw = w - 86;
            CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
            float cpsNorm = Math.clamp((AutoClickerModule.cps - 1) / 19.0f, 0f, 1f);
            int fill = Math.round(sw * cpsNorm);
            if (fill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, fill, 4, GuiTheme.accent(), 2);
            CustomGuiUtils.fillUltraRounded(c, sx + fill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        } else if (AutoClickerModule.mode == 1) {
            // Delay Slider (0.1s to 5.0s)
            String delayText = String.format(java.util.Locale.US, "%.1fs", AutoClickerModule.delaySeconds);
            text(c, "Delay: " + delayText, x + 14, curY + 4, 0xFFD4D8E0);
            int sx = x + 88;
            int sw = w - 102;
            CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
            float delayNorm = Math.clamp((AutoClickerModule.delaySeconds - 0.1f) / 4.9f, 0f, 1f);
            int fill = Math.round(sw * delayNorm);
            if (fill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, fill, 4, GuiTheme.accent(), 2);
            CustomGuiUtils.fillUltraRounded(c, sx + fill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        } else {
            // Cooldown Mode
            text(c, "100% Full Charge (1.9+ Cooldown)", x + 14, curY + 4, 0xFF8E95A4);
        }
        curY += 26;

        // Row 3: Hold Mouse
        text(c, "Hold Mouse", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, AutoClickerModule.holdMouse, mx, my, delta);
        curY += 26;

        // Row 4: Only on Target
        text(c, "Only on Target", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, AutoClickerModule.onlyOnTarget, mx, my, delta);
        curY += 26;

        // Row 5: Random Jitter
        text(c, "Random Jitter", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, AutoClickerModule.randomJitter, mx, my, delta);
        curY += 26;

        // Row 6: Weapon Only
        text(c, "Weapon Only", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, AutoClickerModule.weaponOnly, mx, my, delta);
        curY += 26;

        // Row 7: Button Mode
        text(c, "Button:", x + 14, curY + 4, 0xFFD4D8E0);
        modeButton(c, "Left", x + 60, curY + 2, 44, 18, mx, my, AutoClickerModule.button == 0);
        modeButton(c, "Right", x + 108, curY + 2, 48, 18, mx, my, AutoClickerModule.button == 1);
        curY += 26;

        // Row 8: Reset button
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderHitColorModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getHitColorHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "HitColor", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningHitColor ? "..." : formatKey(HitColorModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, HitColorModule.enabled, mx, my, delta);

        if (!HitColorModule.expanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;
        // Color row
        text(c, "Color:", x + 14, curY + 4, 0xFFD4D8E0);
        int colBtnX = x + 56;
        int colBtnSize = 16;
        boolean hoverCol = inside(mx, my, colBtnX, curY + 2, colBtnSize, colBtnSize);
        c.fill(colBtnX, curY + 2, colBtnX + colBtnSize, curY + 2 + colBtnSize, 0xFF000000 | (HitColorModule.color & 0xFFFFFF));
        CustomGuiUtils.drawUltraRoundedOutline(c, colBtnX, curY + 2, colBtnSize, colBtnSize, hoverCol ? 0xFFFFFFFF : 0xFF353C4D, 2);
        curY += 26;

        // Opacity / Intensity Slider (10% to 100%)
        int pct = Math.round(HitColorModule.alpha * 100);
        text(c, "Opacity: " + pct + "%", x + 14, curY + 4, 0xFFD4D8E0);
        int sx = x + 88;
        int sw = w - 102;
        CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
        float alphaNorm = (HitColorModule.alpha - 0.1f) / 0.9f;
        int fill = Math.round(sw * Math.clamp(alphaNorm, 0f, 1f));
        if (fill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, fill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sx + fill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        // Reset button
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderReachDisplayModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getReachDisplayHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "ReachDisplay", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningReachDisplay ? "..." : formatKey(ReachDisplayModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, ReachDisplayModule.enabled, mx, my, delta);

        if (!ReachDisplayModule.expanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;
        text(c, "Display Mode:", x + 14, curY + 4, 0xFFD4D8E0);
        curY += 20;

        modeButton(c, "Above Crosshair", x + 14, curY, 96, 18, mx, my, ReachDisplayModule.mode == 0);
        modeButton(c, "HUD Element", x + 114, curY, 78, 18, mx, my, ReachDisplayModule.mode == 1);
        curY += 26;

        if (ReachDisplayModule.mode == 1) {
            button(c, "Edit HUD", x + 14, curY, 50, 16, mx, my);
        }
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderLowShieldModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getLowShieldHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "LowShield", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningLowShield ? "..." : formatKey(LowShieldModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, LowShieldModule.enabled, mx, my, delta);

        if (!LowShieldModule.expanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;
        int sx = x + 84;
        int sw = w - 98;

        // Shield Height Slider (1% to 100%)
        text(c, "Shield: " + LowShieldModule.heightPercent + "%", x + 14, curY + 4, 0xFFD4D8E0);
        CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
        float hNorm = (LowShieldModule.heightPercent - 1) / 99.0f;
        int fill = Math.round(sw * Math.clamp(hNorm, 0f, 1f));
        if (fill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, fill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sx + fill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        // Totem Size Slider (10% to 100%)
        text(c, "Totem: " + LowShieldModule.totemSizePercent + "%", x + 14, curY + 4, 0xFFD4D8E0);
        CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
        float tNorm = (LowShieldModule.totemSizePercent - 10) / 90.0f;
        int tfill = Math.round(sw * Math.clamp(tNorm, 0f, 1f));
        if (tfill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, tfill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sx + tfill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        // Reset button
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderCustomHitboxesModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getCustomHitboxesHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "Custom Hitboxes", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningHitboxes ? "..." : formatKey(CustomHitboxesModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, CustomHitboxesModule.enabled, mx, my, delta);

        if (!CustomHitboxesModule.expanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;

        // Row 1: Color
        text(c, "Color:", x + 14, curY + 4, 0xFFD4D8E0);
        int colBtnX = x + 56;
        int colBtnSize = 16;
        boolean hoverCol = inside(mx, my, colBtnX, curY + 2, colBtnSize, colBtnSize);
        c.fill(colBtnX, curY + 2, colBtnX + colBtnSize, curY + 2 + colBtnSize, 0xFF000000 | (CustomHitboxesModule.color & 0xFFFFFF));
        CustomGuiUtils.drawUltraRoundedOutline(c, colBtnX, curY + 2, colBtnSize, colBtnSize, hoverCol ? 0xFFFFFFFF : 0xFF353C4D, 2);
        curY += 26;

        // Row 2: Fill Opacity / Transparency (0% to 100%)
        int pct = Math.round(CustomHitboxesModule.fillOpacity * 100);
        text(c, "Fill: " + pct + "%", x + 14, curY + 4, 0xFFD4D8E0);
        int sx = x + 72;
        int sw = w - 86;
        CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
        int fill = Math.round(sw * Math.clamp(CustomHitboxesModule.fillOpacity, 0f, 1f));
        if (fill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, fill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sx + fill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        // Row 3: Line Width (1.0 to 5.0)
        String widthText = String.format(java.util.Locale.US, "%.1f", CustomHitboxesModule.lineWidth);
        text(c, "Width: " + widthText, x + 14, curY + 4, 0xFFD4D8E0);
        int wx = x + 82;
        int ww = w - 96;
        CustomGuiUtils.fillUltraRounded(c, wx, curY + 6, ww, 4, 0xFF303442, 2);
        float widthNorm = (CustomHitboxesModule.lineWidth - 1.0f) / 4.0f;
        int wfill = Math.round(ww * Math.clamp(widthNorm, 0f, 1f));
        if (wfill > 0) CustomGuiUtils.fillUltraRounded(c, wx, curY + 6, wfill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, wx + wfill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        // Row 4: Target Filter [All] [Players] [Mobs]
        text(c, "Target:", x + 14, curY + 4, 0xFFD4D8E0);
        modeButton(c, "All", x + 60, curY + 2, 34, 18, mx, my, CustomHitboxesModule.targetMode == 0);
        modeButton(c, "Players", x + 98, curY + 2, 50, 18, mx, my, CustomHitboxesModule.targetMode == 1);
        modeButton(c, "Mobs", x + 152, curY + 2, 44, 18, mx, my, CustomHitboxesModule.targetMode == 2);
        curY += 26;

        // Row 5: Eye Height Line
        text(c, "Eye Height Line", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, CustomHitboxesModule.showEyeHeight, mx, my, delta);
        curY += 26;

        // Row 6: View Direction
        text(c, "View Direction", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, CustomHitboxesModule.showViewVector, mx, my, delta);
        curY += 26;

        // Row 7: Reset button
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderNoFogModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getNoFogHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));

        text(c, "No Fog", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningNoFog ? "..." : formatKey(NoFogModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, NoFogModule.enabled, mx, my, delta);

        if (!NoFogModule.expanded) return;

        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;

        // Row 1: All Fog
        text(c, "All Fog (Max View)", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, NoFogModule.allFog, mx, my, delta);
        curY += 26;

        // Row 2: Nether Fog
        text(c, "Nether Fog", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, NoFogModule.netherFog, mx, my, delta);
        curY += 26;

        // Row 3: Water Fog
        text(c, "Water Fog", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, NoFogModule.waterFog, mx, my, delta);
        curY += 26;

        // Row 4: Lava Fog
        text(c, "Lava Fog", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, NoFogModule.lavaFog, mx, my, delta);
        curY += 26;

        // Row 5: Reset
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderAutoToolModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getAutoToolHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));

        text(c, "Auto-Tool", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningAutoTool ? "..." : formatKey(AutoToolModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, AutoToolModule.enabled, mx, my, delta);

        if (!AutoToolModule.expanded) return;

        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;

        // Row 1: Switch Back
        text(c, "Switch Back", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, AutoToolModule.switchBack, mx, my, delta);
        curY += 26;

        // Row 2: Reset
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderBlockOutlineModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getBlockOutlineHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));

        text(c, "Block Outline", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningBlockOutline ? "..." : formatKey(BlockOutlineModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, BlockOutlineModule.enabled, mx, my, delta);

        if (!BlockOutlineModule.expanded) return;

        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;

        // Row 1: Color
        text(c, "Color:", x + 14, curY + 4, 0xFFD4D8E0);
        int colBtnX = x + 56;
        int colBtnSize = 16;
        boolean hoverCol = inside(mx, my, colBtnX, curY + 2, colBtnSize, colBtnSize);
        c.fill(colBtnX, curY + 2, colBtnX + colBtnSize, curY + 2 + colBtnSize, 0xFF000000 | (BlockOutlineModule.color & 0xFFFFFF));
        CustomGuiUtils.drawUltraRoundedOutline(c, colBtnX, curY + 2, colBtnSize, colBtnSize, hoverCol ? 0xFFFFFFFF : 0xFF353C4D, 2);
        curY += 26;

        // Row 2: Chroma (Rainbow)
        text(c, "Chroma (RGB)", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, BlockOutlineModule.chroma, mx, my, delta);
        curY += 26;

        // Row 3: Line Width (1.0 to 6.0)
        String widthText = String.format(java.util.Locale.US, "%.1f", BlockOutlineModule.lineWidth);
        text(c, "Width: " + widthText, x + 14, curY + 4, 0xFFD4D8E0);
        int wx = x + 82;
        int ww = w - 96;
        CustomGuiUtils.fillUltraRounded(c, wx, curY + 6, ww, 4, 0xFF303442, 2);
        float widthNorm = (BlockOutlineModule.lineWidth - 1.0f) / 5.0f;
        int wfill = Math.round(ww * Math.clamp(widthNorm, 0f, 1f));
        if (wfill > 0) CustomGuiUtils.fillUltraRounded(c, wx, curY + 6, wfill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, wx + wfill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        // Row 4: Opacity (20% to 100%)
        int pct = Math.round(BlockOutlineModule.opacity * 100);
        text(c, "Opacity: " + pct + "%", x + 14, curY + 4, 0xFFD4D8E0);
        int ox = x + 90;
        int ow = w - 104;
        CustomGuiUtils.fillUltraRounded(c, ox, curY + 6, ow, 4, 0xFF303442, 2);
        float opNorm = (BlockOutlineModule.opacity - 0.2f) / 0.8f;
        int ofill = Math.round(ow * Math.clamp(opNorm, 0f, 1f));
        if (ofill > 0) CustomGuiUtils.fillUltraRounded(c, ox, curY + 6, ofill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, ox + ofill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        // Row 5: Reset button
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderTimeChangerModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getTimeChangerHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));

        text(c, "Time Changer", x + 12, y + 12, 0xFFE2E5ED);

        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);
        String kb = listeningTimeChanger ? "..." : formatKey(TimeChangerModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, TimeChangerModule.enabled, mx, my, delta);

        if (!TimeChangerModule.expanded) return;

        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;

        // Row 1: Time mode button
        text(c, "Time", x + 14, curY + 4, 0xFFD4D8E0);
        button(c, TimeChangerModule.getCurrentModeName(), x + w - 74, curY, 60, 16, mx, my);
        curY += 26;

        // Row 2: Reset button
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderHandPositionModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getHandPositionHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));

        text(c, "Hand Position", x + 12, y + 12, 0xFFE2E5ED);

        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);
        String kb = listeningHandPosition ? "..." : formatKey(HandPositionModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, HandPositionModule.enabled, mx, my, delta);

        if (!HandPositionModule.expanded) return;

        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;

        // Row 1: Swing Style mode buttons
        String[] swingStyles = {"Slash", "Thrust", "Swipe", "Punch"};
        int sBtnW = (w - 24 - 9) / 4;
        for (int i = 0; i < 4; i++) {
            modeButton(c, swingStyles[i], x + 12 + i * (sBtnW + 3), curY, sBtnW, 16, mx, my, HandPositionModule.swingStyle == i);
        }
        curY += 22;

        // Row 2: Presets
        String[] pNames = {"Default", "Lowered", "Doom", "Small"};
        int pBtnW = (w - 24 - 9) / 4;
        for (int i = 0; i < 4; i++) {
            button(c, pNames[i], x + 12 + i * (pBtnW + 3), curY, pBtnW, 16, mx, my);
        }
        curY += 24;

        // Two Column Layout
        int colGap = 10;
        int colW = (w - 24 - colGap) / 2;
        int c1X = x + 12;
        int c2X = c1X + colW + colGap;

        // Row 3: Section Headers
        text(c, "POSITION", c1X + 1, curY + 2, 0xFF8E95A4);
        text(c, "ROTATION", c2X + 1, curY + 2, 0xFF8E95A4);
        curY += 14;

        // Row 4: X Offset (Left) | Pitch (Right)
        String xStr = String.format(java.util.Locale.US, "%.2f", HandPositionModule.posX);
        text(c, "X Offset", c1X, curY, 0xFFD4D8E0);
        int xValW = textRenderer.getWidth(CustomGuiUtils.getFontText(xStr));
        text(c, xStr, c1X + colW - xValW, curY, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, colW, 4, 0xFF303442, 2);
        float xNorm = (HandPositionModule.posX - (-0.80f)) / 1.60f;
        int xFill = Math.round(colW * Math.clamp(xNorm, 0f, 1f));
        if (xFill > 0) CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, xFill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, c1X + xFill - 3, curY + 9, 7, 8, 0xFFFFFFFF, 4);

        String pitchStr = Math.round(HandPositionModule.pitch) + "°";
        text(c, "Pitch (Tilt)", c2X, curY, 0xFFD4D8E0);
        int pitchValW = textRenderer.getWidth(CustomGuiUtils.getFontText(pitchStr));
        text(c, pitchStr, c2X + colW - pitchValW, curY, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, c2X, curY + 11, colW, 4, 0xFF303442, 2);
        float pitchNorm = (HandPositionModule.pitch - (-180f)) / 360f;
        int pitchFill = Math.round(colW * Math.clamp(pitchNorm, 0f, 1f));
        if (pitchFill > 0) CustomGuiUtils.fillUltraRounded(c, c2X, curY + 11, pitchFill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, c2X + pitchFill - 3, curY + 9, 7, 8, 0xFFFFFFFF, 4);
        curY += 23;

        // Row 5: Y Offset (Left) | Yaw (Right)
        String yStr = String.format(java.util.Locale.US, "%.2f", HandPositionModule.posY);
        text(c, "Y Offset", c1X, curY, 0xFFD4D8E0);
        int yValW = textRenderer.getWidth(CustomGuiUtils.getFontText(yStr));
        text(c, yStr, c1X + colW - yValW, curY, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, colW, 4, 0xFF303442, 2);
        float yNorm = (HandPositionModule.posY - (-0.80f)) / 1.60f;
        int yFill = Math.round(colW * Math.clamp(yNorm, 0f, 1f));
        if (yFill > 0) CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, yFill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, c1X + yFill - 3, curY + 9, 7, 8, 0xFFFFFFFF, 4);

        String yawStr = Math.round(HandPositionModule.yaw) + "°";
        text(c, "Yaw (Turn)", c2X, curY, 0xFFD4D8E0);
        int yawValW = textRenderer.getWidth(CustomGuiUtils.getFontText(yawStr));
        text(c, yawStr, c2X + colW - yawValW, curY, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, c2X, curY + 11, colW, 4, 0xFF303442, 2);
        float yawNorm = (HandPositionModule.yaw - (-180f)) / 360f;
        int yawFill = Math.round(colW * Math.clamp(yawNorm, 0f, 1f));
        if (yawFill > 0) CustomGuiUtils.fillUltraRounded(c, c2X, curY + 11, yawFill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, c2X + yawFill - 3, curY + 9, 7, 8, 0xFFFFFFFF, 4);
        curY += 23;

        // Row 6: Z Offset (Left) | Roll (Right)
        String zStr = String.format(java.util.Locale.US, "%.2f", HandPositionModule.posZ);
        text(c, "Z Offset", c1X, curY, 0xFFD4D8E0);
        int zValW = textRenderer.getWidth(CustomGuiUtils.getFontText(zStr));
        text(c, zStr, c1X + colW - zValW, curY, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, colW, 4, 0xFF303442, 2);
        float zNorm = (HandPositionModule.posZ - (-0.80f)) / 1.60f;
        int zFill = Math.round(colW * Math.clamp(zNorm, 0f, 1f));
        if (zFill > 0) CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, zFill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, c1X + zFill - 3, curY + 9, 7, 8, 0xFFFFFFFF, 4);

        String rollStr = Math.round(HandPositionModule.roll) + "°";
        text(c, "Roll (Angle)", c2X, curY, 0xFFD4D8E0);
        int rollValW = textRenderer.getWidth(CustomGuiUtils.getFontText(rollStr));
        text(c, rollStr, c2X + colW - rollValW, curY, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, c2X, curY + 11, colW, 4, 0xFF303442, 2);
        float rollNorm = (HandPositionModule.roll - (-180f)) / 360f;
        int rollFill = Math.round(colW * Math.clamp(rollNorm, 0f, 1f));
        if (rollFill > 0) CustomGuiUtils.fillUltraRounded(c, c2X, curY + 11, rollFill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, c2X + rollFill - 3, curY + 9, 7, 8, 0xFFFFFFFF, 4);
        curY += 23;

        // Row 7: Scale Slider (Left) | Offhand Toggle (Right)
        int scalePct = Math.round(HandPositionModule.scale * 100);
        text(c, "Item Scale", c1X, curY, 0xFFD4D8E0);
        String sPctStr = scalePct + "%";
        int sPctW = textRenderer.getWidth(CustomGuiUtils.getFontText(sPctStr));
        text(c, sPctStr, c1X + colW - sPctW, curY, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, colW, 4, 0xFF303442, 2);
        float sNorm = (HandPositionModule.scale - 0.30f) / 1.20f;
        int sFill = Math.round(colW * Math.clamp(sNorm, 0f, 1f));
        if (sFill > 0) CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, sFill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, c1X + sFill - 3, curY + 9, 7, 8, 0xFFFFFFFF, 4);

        text(c, "Offhand", c2X, curY + 2, 0xFFD4D8E0);
        toggle(c, c2X + colW - 28, curY, HandPositionModule.applyToOffhand, mx, my, delta);
        curY += 23;

        // Row 8: Weapons Only Toggle (Left) | Reset Button (Right)
        text(c, "Weapons Only", c1X, curY + 2, 0xFFD4D8E0);
        toggle(c, c1X + colW - 28, curY, HandPositionModule.weaponsOnly, mx, my, delta);

        button(c, "Reset", c2X + colW - 46, curY, 46, 16, mx, my);
    }

    private void renderFreelookModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getFreelookHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));

        text(c, "Freelook", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningFreelook ? "..." : formatKey(FreelookModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, FreelookModule.enabled, mx, my, delta);

        if (!FreelookModule.expanded) return;

        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;

        // Row 1: Mode (Hold / Toggle)
        text(c, "Mode", x + 14, curY + 4, 0xFFD4D8E0);
        button(c, FreelookModule.toggleMode ? "Toggle" : "Hold", x + w - 58, curY, 46, 16, mx, my);
        curY += 26;

        // Row 2: Invert Pitch
        text(c, "Invert Pitch", x + 14, curY + 4, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 2, FreelookModule.invertPitch, mx, my, delta);
        curY += 26;

        // Row 3: Sensitivity (50% to 200%)
        int sensPct = Math.round(FreelookModule.sensitivity * 100);
        text(c, "Sens: " + sensPct + "%", x + 14, curY + 4, 0xFFD4D8E0);
        int sx = x + 84;
        int sw = w - 98;
        CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
        float sensNorm = (FreelookModule.sensitivity - 0.5f) / 1.5f;
        int sfill = Math.round(sw * Math.clamp(sensNorm, 0f, 1f));
        if (sfill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sfill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sx + sfill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        // Row 4: Reset
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderItemSizeModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getItemSizeHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));

        text(c, "Item Size", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningItemSize ? "..." : formatKey(ItemSizeModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, ItemSizeModule.enabled, mx, my, delta);

        if (!ItemSizeModule.expanded) return;

        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;

        // Row 1: Scale (25% to 400%)
        int scalePct = Math.round(ItemSizeModule.scale * 100);
        text(c, "Scale: " + scalePct + "%", x + 14, curY + 4, 0xFFD4D8E0);
        int sx = x + 88;
        int sw = w - 102;
        CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
        float scaleNorm = (ItemSizeModule.scale - 0.25f) / 3.75f;
        int sfill = Math.round(sw * Math.clamp(scaleNorm, 0f, 1f));
        if (sfill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sfill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sx + sfill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        // Row 2: Y-Offset (-0.20 to 1.00)
        String yStr = String.format(java.util.Locale.US, "%.2f", ItemSizeModule.yOffset);
        text(c, "Y-Offset: " + yStr, x + 14, curY + 4, 0xFFD4D8E0);
        int yx = x + 96;
        int yw = w - 110;
        CustomGuiUtils.fillUltraRounded(c, yx, curY + 6, yw, 4, 0xFF303442, 2);
        float yNorm = (ItemSizeModule.yOffset - (-0.2f)) / 1.2f;
        int yfill = Math.round(yw * Math.clamp(yNorm, 0f, 1f));
        if (yfill > 0) CustomGuiUtils.fillUltraRounded(c, yx, curY + 6, yfill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, yx + yfill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        // Row 3: Blocks (Item selection button, matching Image 2)
        text(c, "Blocks", x + 14, curY, 0xFFABB1BE);
        int boxX = x + 14;
        int boxY = curY + 12;
        int boxW = w - 28;
        int boxH = 22;
        boolean boxHovered = inside(mx, my, boxX, boxY, boxW, boxH);
        CustomGuiUtils.fillUltraRounded(c, boxX, boxY, boxW, boxH, boxHovered ? 0xFF181C26 : 0xFF12151D, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, boxX, boxY, boxW, boxH, boxHovered ? GuiTheme.accent() : 0xFF2A2E3D, 4);

        int count = ItemSizeModule.selectedItems.size();
        String countText = count > 0 ? count + " item(s) selected" : "All items (Click to filter)";
        text(c, countText, boxX + 8, boxY + 7, 0xFFE2E5ED);

        curY += 12 + 22 + 8;

        // Row 4: Configured items (2 side-by-side per row)
        java.util.List<String> selList = new java.util.ArrayList<>(ItemSizeModule.selectedItems);
        int colW = (w - 28 - 6) / 2;
        for (int i = 0; i < selList.size(); i += 2) {
            for (int col = 0; col < 2; col++) {
                int idx = i + col;
                if (idx >= selList.size()) break;
                String itemId = selList.get(idx);
                int colX = x + 14 + (col * (colW + 6));

                Item item = null;
                try {
                    item = Registries.ITEM.get(Identifier.of(itemId));
                } catch (Exception ignored) {}

                ItemStack stack = (item != null && item != Items.AIR) ? new ItemStack(item) : ItemStack.EMPTY;
                if (!stack.isEmpty()) {
                    c.drawItem(stack, colX, curY + 2);
                }

                String itemName = (!stack.isEmpty()) ? stack.getName().getString() : itemId;
                int nameX = colX + 18;
                int nameMaxW = colW - 36;
                c.enableScissor(nameX, curY, nameX + nameMaxW, curY + 22);
                text(c, itemName, nameX, curY + 6, 0xFFE2E5ED);
                c.disableScissor();

                // Trash icon button
                int trashX = colX + colW - 16;
                int trashY = curY + 2;
                boolean trashHover = inside(mx, my, trashX, trashY, 16, 16);
                CustomGuiUtils.fillUltraRounded(c, trashX, trashY, 16, 16, trashHover ? 0xFF3A1C20 : 0xFF181C24, 3);
                CustomGuiUtils.drawUltraRoundedOutline(c, trashX, trashY, 16, 16, trashHover ? 0xFFE55757 : 0xFF292D36, 3);
                CustomGuiUtils.drawTrashIcon(c, trashX, trashY, trashHover ? 0xFFE55757 : 0xFF8E95A4);
            }
            curY += 24;
        }

        // Reset
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderDurabilityGuardModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getDurabilityGuardHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));

        text(c, "Durability Guard", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningDurabilityGuard ? "..." : formatKey(DurabilityGuardModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, DurabilityGuardModule.enabled, mx, my, delta);

        if (!DurabilityGuardModule.expanded) return;

        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;

        // Row 1: Alert Type (Subtitle / ActionBar / Both)
        text(c, "Alert Type", x + 14, curY + 4, 0xFFD4D8E0);
        String alertStr = DurabilityGuardModule.alertMode == 0 ? "Subtitle" : (DurabilityGuardModule.alertMode == 1 ? "ActionBar" : "Both");
        button(c, alertStr, x + w - 74, curY + 2, 60, 16, mx, my);
        curY += 26;

        // Row 2: Tools (Item selection button, matching Image 2 & ItemSize)
        text(c, "Tools", x + 14, curY, 0xFFABB1BE);
        int boxX = x + 14;
        int boxY = curY + 12;
        int boxW = w - 28;
        int boxH = 22;
        boolean boxHovered = inside(mx, my, boxX, boxY, boxW, boxH);
        CustomGuiUtils.fillUltraRounded(c, boxX, boxY, boxW, boxH, boxHovered ? 0xFF181C26 : 0xFF12151D, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, boxX, boxY, boxW, boxH, boxHovered ? GuiTheme.accent() : 0xFF2A2E3D, 4);

        int count = DurabilityGuardModule.toolThresholds.size();
        String countText = count > 0 ? count + " tool(s) configured" : "Click to select tools";
        text(c, countText, boxX + 8, boxY + 7, 0xFFE2E5ED);

        curY += 12 + 22 + 8;

        // Row 3: Configured Tools list
        for (java.util.Map.Entry<String, Integer> entry : DurabilityGuardModule.toolThresholds.entrySet()) {
            String toolId = entry.getKey();
            int threshold = entry.getValue();

            Item item = null;
            try {
                item = Registries.ITEM.get(Identifier.of(toolId));
            } catch (Exception ignored) {}

            ItemStack stack = (item != null && item != Items.AIR) ? new ItemStack(item) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                c.drawItem(stack, x + 14, curY + 4);
            }

            String toolName = (!stack.isEmpty()) ? stack.getName().getString() : toolId;
            c.enableScissor(x + 36, curY, x + w - 86, curY + 24);
            text(c, toolName, x + 36, curY + 7, 0xFFE2E5ED);
            c.disableScissor();

            // Right side:
            int fieldW = 42;
            int fieldH = 16;
            int fieldX = x + w - 38 - fieldW;
            int fieldY = curY + 3;

            // Number input field
            CustomTextFieldWidget field = toolThresholdFields.get(toolId);
            if (field == null) {
                field = new CustomTextFieldWidget(fieldX, fieldY, fieldW, fieldH, Text.literal("Threshold"));
                field.setPlaceholder("10");
                field.setText(String.valueOf(threshold));
                String captureId = toolId;
                field.setChangedListener(val -> {
                    try {
                        int parsed = Integer.parseInt(val.trim());
                        if (parsed > 0) {
                            DurabilityGuardModule.toolThresholds.put(captureId, parsed);
                            BameClientConfig.save();
                        }
                    } catch (NumberFormatException ignored) {}
                });
                toolThresholdFields.put(toolId, field);
            } else {
                field.setX(fieldX);
                field.setY(fieldY);
                field.setWidth(fieldW);
                field.setHeight(fieldH);
                if (!field.isFocused() && !field.getText().equals(String.valueOf(threshold))) {
                    field.setText(String.valueOf(threshold));
                }
            }
            field.render(c, mx, my, delta);

            // Trash remove button
            int trashX = x + w - 32;
            int trashY = curY + 3;
            boolean trashHover = inside(mx, my, trashX, trashY, 16, 16);
            CustomGuiUtils.fillUltraRounded(c, trashX, trashY, 16, 16, trashHover ? 0xFF3A1C20 : 0xFF181C24, 3);
            CustomGuiUtils.drawUltraRoundedOutline(c, trashX, trashY, 16, 16, trashHover ? 0xFFE55757 : 0xFF292D36, 3);
            CustomGuiUtils.drawTrashIcon(c, trashX, trashY, trashHover ? 0xFFE55757 : 0xFF8E95A4);

            curY += 24;
        }

        // Row 4: Reset
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderItemModal(DrawContext c, int mx, int my, float delta) {
        int modalW = 350;
        int modalH = 285;
        int modalX = (width - modalW) / 2;
        int modalY = (height - modalH) / 2;

        // 1. Dark dim background backdrop
        c.fill(0, 0, width, height, 0x99000000);

        // 2. Modal card
        CustomGuiUtils.fillUltraRounded(c, modalX, modalY, modalW, modalH, 0xFA14161E, 6);
        CustomGuiUtils.drawUltraRoundedOutline(c, modalX, modalY, modalW, modalH, 0xFF2A2E3D, 6);

        // 3. Title (Centered)
        String title = itemModalType == 1 ? "SELECT: TOOLS" : "SELECT: BLOCKS";
        int titleW = textRenderer.getWidth(CustomGuiUtils.getFontText(title));
        c.drawText(textRenderer, CustomGuiUtils.getFontText(title), modalX + (modalW - titleW) / 2, modalY + 10, 0xFFFFFFFF, false);

        // 4. Search bar
        int searchX = modalX + 16;
        int searchY = modalY + 24;
        int searchW = modalW - 32;
        int searchH = 20;
        if (itemSearchWidget != null) {
            itemSearchWidget.setX(searchX);
            itemSearchWidget.setY(searchY);
            itemSearchWidget.setWidth(searchW);
            itemSearchWidget.setHeight(searchH);
            itemSearchWidget.render(c, mx, my, delta);
            CustomGuiUtils.drawUltraRoundedOutline(c, searchX, searchY, searchW, searchH, GuiTheme.accent(), 4);
        }

        // 5. Grid of items (12 cols, matching Image 3)
        if (cachedFilteredItems == null) {
            updateItemFilter();
        }
        int gridX = modalX + 16;
        int gridY = modalY + 50;
        int gridW = 297;
        int gridH = 185;

        int totalItems = cachedFilteredItems.size();
        int totalRows = (totalItems + 11) / 12;
        int totalContentH = totalRows * 25;
        int maxScroll = Math.max(0, totalContentH - gridH);
        itemModalScroll = Math.clamp(itemModalScroll, 0, maxScroll);

        c.enableScissor(gridX, gridY, gridX + gridW + 2, gridY + gridH);

        Item hoveredItem = null;
        int startRow = (int) (itemModalScroll / 25);
        int endRow = Math.min(totalRows, startRow + (gridH / 25) + 2);

        for (int row = startRow; row < endRow; row++) {
            for (int col = 0; col < 12; col++) {
                int index = row * 12 + col;
                if (index >= totalItems) break;

                Item item = cachedFilteredItems.get(index);
                int sx = gridX + col * 25;
                int sy = (int) (gridY + row * 25 - itemModalScroll);

                if (sy + 22 < gridY || sy > gridY + gridH) continue;

                String id = Registries.ITEM.getId(item).toString();
                boolean selected = itemModalType == 1 ? tempToolThresholds.containsKey(id) : tempSelectedItems.contains(id);
                boolean hovered = mx >= sx && mx < sx + 22 && my >= sy && my < sy + 22 && my >= gridY && my < gridY + gridH;

                if (hovered) {
                    hoveredItem = item;
                    c.fill(sx, sy, sx + 22, sy + 22, 0x33FFFFFF);
                }

                c.drawItem(new ItemStack(item), sx + 3, sy + 3);

                if (selected) {
                    CustomGuiUtils.drawUltraRoundedOutline(c, sx, sy, 22, 22, GuiTheme.accent(), 3);
                }
            }
        }
        c.disableScissor();

        // Scrollbar
        int scrollbarX = modalX + modalW - 18;
        int scrollbarW = 4;
        if (maxScroll > 0) {
            box(c, scrollbarX, gridY, scrollbarW, gridH, 0xFF1E212A);
            int thumbH = Math.max(16, (int) ((float) gridH * gridH / totalContentH));
            int thumbY = gridY + (int) ((gridH - thumbH) * (itemModalScroll / maxScroll));
            CustomGuiUtils.fillUltraRounded(c, scrollbarX, thumbY, scrollbarW, thumbH, itemScrollDragging ? 0xFFFFFFFF : GuiTheme.accent(), 2);
        }

        // 6. Bottom Buttons (RESET, CANCEL, SAVE)
        int btnY = modalY + modalH - 34;
        int btnH = 22;
        int btnW = 100;
        int btn1X = modalX + 16;
        int btn2X = btn1X + btnW + 9;
        int btn3X = btn2X + btnW + 9;

        // RESET
        boolean rHover = inside(mx, my, btn1X, btnY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, btn1X, btnY, btnW, btnH, rHover ? 0xFF2A1C20 : 0xFF1B1D25, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, btn1X, btnY, btnW, btnH, rHover ? 0xFFE55757 : 0xFF2A2E3D, 4);
        String rText = "RESET";
        int rW = textRenderer.getWidth(CustomGuiUtils.getFontText(rText));
        c.drawText(textRenderer, CustomGuiUtils.getFontText(rText), btn1X + (btnW - rW) / 2, btnY + 7, 0xFFE55757, false);

        // CANCEL
        boolean cHover = inside(mx, my, btn2X, btnY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, btn2X, btnY, btnW, btnH, cHover ? 0xFF222632 : 0xFF1B1D25, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, btn2X, btnY, btnW, btnH, cHover ? 0xFFFFFFFF : 0xFF2A2E3D, 4);
        String cText = "CANCEL";
        int cW = textRenderer.getWidth(CustomGuiUtils.getFontText(cText));
        c.drawText(textRenderer, CustomGuiUtils.getFontText(cText), btn2X + (btnW - cW) / 2, btnY + 7, 0xFFD4D8E0, false);

        // SAVE
        boolean sHover = inside(mx, my, btn3X, btnY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, btn3X, btnY, btnW, btnH, sHover ? 0xFF1A2B20 : 0xFF1B1D25, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, btn3X, btnY, btnW, btnH, sHover ? 0xFF4ADE80 : 0xFF2A2E3D, 4);
        String sText = "SAVE";
        int sW = textRenderer.getWidth(CustomGuiUtils.getFontText(sText));
        c.drawText(textRenderer, CustomGuiUtils.getFontText(sText), btn3X + (btnW - sW) / 2, btnY + 7, 0xFF4ADE80, false);

        // 7. Tooltip on top
        if (hoveredItem != null) {
            c.drawItemTooltip(textRenderer, new ItemStack(hoveredItem), mx, my);
        }
    }

    private void syncHitboxHsv() {
        int col = CustomHitboxesModule.color;
        float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
        cpHue = hsb[0];
        cpSat = hsb[1];
        cpVal = hsb[2];
    }

    private void syncBlockOutlineHsv() {
        int col = BlockOutlineModule.color;
        float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
        cpHue = hsb[0];
        cpSat = hsb[1];
        cpVal = hsb[2];
    }

    private void syncCrosshairHsv() {
        int col = CustomCrosshairModule.color;
        float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
        cpHue = hsb[0];
        cpSat = hsb[1];
        cpVal = hsb[2];
    }

    private void syncHitColorHsv() {
        int col = HitColorModule.color;
        float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
        cpHue = hsb[0];
        cpSat = hsb[1];
        cpVal = hsb[2];
    }

    private void updateModalColor(double mx, double my) {
        int CP_W = 150;
        int CP_H = 100;
        int myCpX = (width - CP_W) / 2;
        int myCpY = (height - CP_H) / 2;
        int svX = myCpX + 46;
        int svY = myCpY + 22;
        int svW = 92;
        int svH = 30;
        int barX = myCpX + 10;
        int barW = 128;

        if (cpDrag == 0) { // SV
            cpSat = (float) Math.clamp((mx - svX) / (double) (svW - 1), 0.0, 1.0);
            cpVal = (float) Math.clamp(1.0 - (my - svY) / (double) (svH - 1), 0.0, 1.0);
        } else if (cpDrag == 1) { // Hue
            cpHue = (float) Math.clamp((mx - barX) / (double) (barW - 1), 0.0, 1.0);
        }
        int rgb = java.awt.Color.HSBtoRGB(cpHue, cpSat, cpVal);
        if (hitColorColorPickerOpen) {
            HitColorModule.color = 0xFF000000 | (rgb & 0xFFFFFF);
            HitColorModule.apply();
        } else if (hitboxColorPickerOpen) {
            CustomHitboxesModule.color = 0xFF000000 | (rgb & 0xFFFFFF);
        } else if (blockOutlineColorPickerOpen) {
            BlockOutlineModule.color = 0xFF000000 | (rgb & 0xFFFFFF);
        } else {
            CustomCrosshairModule.color = 0xFF000000 | (rgb & 0xFFFFFF);
        }
        BameClientConfig.save();
    }

    private void renderModalColorPicker(DrawContext context, int mouseX, int mouseY) {
        int CP_W = 150;
        int CP_H = 100;
        cpX = (width - CP_W) / 2;
        cpY = (height - CP_H) / 2;

        // Dark dim backdrop
        context.fill(0, 0, width, height, 0x66000000);

        CustomGuiUtils.fillUltraRounded(context, cpX, cpY, CP_W, CP_H, 0xFA151820, 6);
        CustomGuiUtils.drawUltraRoundedOutline(context, cpX, cpY, CP_W, CP_H, 0xFF353C4D, 6);

        // Title
        String title = hitColorColorPickerOpen ? "HIT COLOR" : (hitboxColorPickerOpen ? "HITBOX COLOR" : (blockOutlineColorPickerOpen ? "BLOCK OUTLINE COLOR" : "COLOR PICKER"));
        context.drawText(textRenderer, CustomGuiUtils.getFontText(title), cpX + 10, cpY + 8, 0xFFFFFFFF, false);
        context.drawText(textRenderer, Text.literal("×"), cpX + CP_W - 14, cpY + 6, 0xFF8E95A4, false);

        int curCol = hitColorColorPickerOpen ? HitColorModule.color : (hitboxColorPickerOpen ? CustomHitboxesModule.color : (blockOutlineColorPickerOpen ? BlockOutlineModule.color : CustomCrosshairModule.color));

        // 1. Preview box (Top Left, Bild 3)
        int prevX = cpX + 10;
        int prevY = cpY + 22;
        int prevSize = 30;
        context.fill(prevX, prevY, prevX + prevSize, prevY + prevSize, curCol);
        CustomGuiUtils.drawRoundedOutline(context, prevX - 1, prevY - 1, prevSize + 2, prevSize + 2, 0xFFFFFFFF);

        // 2. SV Box (Top Right, Bild 3)
        int svX = cpX + 46;
        int svY = cpY + 22;
        int svW = 92;
        int svH = 30;
        for (int i = 0; i < svW; i++) {
            float s = i / (float)(svW - 1);
            int top = java.awt.Color.HSBtoRGB(cpHue, s, 1.0f);
            context.fillGradient(svX + i, svY, svX + i + 1, svY + svH, top, 0xFF000000);
        }
        CustomGuiUtils.drawRoundedOutline(context, svX - 1, svY - 1, svW + 2, svH + 2, 0xFF353C4D);

        // SV Handle cursor (small hollow white square, Bild 3)
        int curSvX = svX + Math.round(cpSat * (svW - 1));
        int curSvY = svY + Math.round((1.0f - cpVal) * (svH - 1));
        context.fill(curSvX - 2, curSvY - 2, curSvX + 3, curSvY + 3, 0xAA000000);
        context.fill(curSvX - 2, curSvY - 2, curSvX + 3, curSvY - 1, 0xFFFFFFFF);
        context.fill(curSvX - 2, curSvY + 2, curSvX + 3, curSvY + 3, 0xFFFFFFFF);
        context.fill(curSvX - 2, curSvY - 1, curSvX - 1, curSvY + 2, 0xFFFFFFFF);
        context.fill(curSvX + 2, curSvY - 1, curSvX + 3, curSvY + 2, 0xFFFFFFFF);

        // 3. Hue Rainbow Bar (Bild 3)
        int barX = cpX + 10;
        int barW = 128;
        int hueY = cpY + 58;
        int barH = 8;
        for (int i = 0; i < barW; i++) {
            int rgb = java.awt.Color.HSBtoRGB(i / (float)(barW - 1), 1.0f, 1.0f);
            context.fill(barX + i, hueY, barX + i + 1, hueY + barH, rgb);
        }
        CustomGuiUtils.drawRoundedOutline(context, barX - 1, hueY - 1, barW + 2, barH + 2, 0xFF353C4D);

        // Hue indicator knob
        int curHueX = barX + Math.round(cpHue * (barW - 1));
        context.fill(curHueX - 2, hueY - 1, curHueX + 3, hueY + barH + 1, 0xFFFFFFFF);
        context.fill(curHueX - 1, hueY, curHueX + 2, hueY + barH, java.awt.Color.HSBtoRGB(cpHue, 1.0f, 1.0f));

        // 4. Hex text box (Bild 3)
        int hexY = cpY + 72;
        int hexH = 18;
        CustomGuiUtils.fillUltraRounded(context, barX, hexY, barW, hexH, 0xFF0D0F14, 3);
        CustomGuiUtils.drawUltraRoundedOutline(context, barX, hexY, barW, hexH, 0xFF2A2E39, 3);
        String hexStr = String.format("#%06X", curCol & 0xFFFFFF);
        context.drawText(textRenderer, CustomGuiUtils.getFontText(hexStr), barX + 8, hexY + 5, 0xFFFFFFFF, false);
    }

    private void renderTheme(DrawContext c,int mx,int my,float delta) {
        int y=baseY(),cols=columns(),tileW=(cw-8*(cols-1))/cols;
        text(c,"PRESETS",cx+4,y+4,0xFFE2E5ED);
        text(c,"Choose a palette for your client",cx+4,y+23,0xFF8E95A4);
        for(int i=0;i<GuiTheme.PRESETS.length;i++) {
            var p=GuiTheme.PRESETS[i]; int x=cx+(i%cols)*(tileW+8),ty=y+44+(i/cols)*58;
            box(c,x,ty,tileW,50,0xFF151A21);
            if(!BameClientConfig.customMainColor && p.name().equals(GuiTheme.current().name())) CustomGuiUtils.drawUltraRoundedOutline(c,x,ty,tileW,50,GuiTheme.accent(),6);
            c.enableScissor(x+4,ty,x+tileW-4,ty+23);
            text(c,p.name(),x+7,ty+7,0xFFCCD2DD);
            c.disableScissor();
            int[] colors={p.primary(),p.secondary(),p.highlight(),p.background()};
            for(int j=0;j<4;j++) c.fill(x+6+j*(tileW-12)/4,ty+23,x+6+(j+1)*(tileW-12)/4,ty+43,0xFF000000|colors[j]);
        }
        themeSettings.render(c,mx,my,delta);
    }
    private void renderSettings(DrawContext c,int mx,int my,float delta) {
        int iy = baseY() + 12;
        CustomGuiUtils.fillUltraRounded(c,cx,iy,cw,80,0xE812161E,6);
        CustomGuiUtils.drawUltraRoundedOutline(c,cx,iy,cw,80,0xFF292D36,6);
        text(c,"INTERFACE",cx+12,iy+12,0xFFB5BCC9);
        text(c,"Menu Bind",cx+12,iy+34,0xFFD4D8E0);
        button(c,keyNameMenuBind(),cx+cw-72,iy+30,60,16,mx,my);
        text(c,"GUI Animation",cx+12,iy+58,0xFFD4D8E0);
        button(c,animName(),cx+cw-104,iy+54,92,16,mx,my);

        // Sounds Section (matching Image)
        int soundY = iy + 80 + 16;
        CustomGuiUtils.drawMusicIcon(c, cx + 2, soundY + 1, GuiTheme.accent());
        text(c, "Sounds", cx + 18, soundY, 0xFFFFFFFF);
        text(c, "Client Sounds", cx + 2, soundY + 14, 0xFF8E95A4);

        int cardY = soundY + 30;
        int cardH = 180;
        CustomGuiUtils.fillUltraRounded(c, cx, cardY, cw, cardH, 0xE812161E, 6);
        CustomGuiUtils.drawUltraRoundedOutline(c, cx, cardY, cw, cardH, 0xFF292D36, 6);

        // Row 1: Module Sound
        int r1Y = cardY + 4;
        text(c, "Module Sound", cx + 14, r1Y + 6, 0xFFD4D8E0);
        toggle(c, cx + cw - 38, r1Y + 4, BameClientConfig.moduleSound, mx, my, delta);
        c.fill(cx + 10, r1Y + 28, cx + cw - 10, r1Y + 29, 0xFF202430);

        // Row 2: Sound Pack
        int r2Y = r1Y + 29;
        text(c, "Sound Pack", cx + 14, r2Y + 6, 0xFFD4D8E0);
        String spText = com.bame.client.sound.ClientSoundManager.SOUND_PACKS[Math.clamp(BameClientConfig.soundPack, 0, com.bame.client.sound.ClientSoundManager.SOUND_PACKS.length - 1)] + "  v";
        button(c, spText, cx + cw - 100, r2Y + 3, 86, 18, mx, my);
        c.fill(cx + 10, r2Y + 28, cx + cw - 10, r2Y + 29, 0xFF202430);

        // Row 3: Volume
        int r3Y = r2Y + 29;
        text(c, "Volume", cx + 14, r3Y + 6, 0xFFD4D8E0);
        int sliderW = 100;
        int sliderX = cx + cw - 14 - sliderW;
        String volPct = Math.round(BameClientConfig.soundVolume * 100) + "%";
        int pctW = textRenderer.getWidth(CustomGuiUtils.getFontText(volPct));
        text(c, volPct, sliderX - pctW - 8, r3Y + 6, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, sliderX, r3Y + 8, sliderW, 4, 0xFF303442, 2);
        int fill = Math.round(sliderW * Math.clamp(BameClientConfig.soundVolume, 0f, 1f));
        if (fill > 0) CustomGuiUtils.fillUltraRounded(c, sliderX, r3Y + 8, fill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sliderX + fill - 3, r3Y + 6, 7, 8, 0xFFFFFFFF, 4);
        c.fill(cx + 10, r3Y + 28, cx + cw - 10, r3Y + 29, 0xFF202430);

        // Row 4: Hover Sound
        int r4Y = r3Y + 29;
        text(c, "Hover Sound", cx + 14, r4Y + 6, 0xFFD4D8E0);
        toggle(c, cx + cw - 38, r4Y + 4, BameClientConfig.hoverSound, mx, my, delta);
        c.fill(cx + 10, r4Y + 28, cx + cw - 10, r4Y + 29, 0xFF202430);

        // Row 5: Hover Style
        int r5Y = r4Y + 29;
        text(c, "Hover Style", cx + 14, r5Y + 6, 0xFFD4D8E0);
        String hsText = com.bame.client.sound.ClientSoundManager.HOVER_STYLES[Math.clamp(BameClientConfig.hoverStyle, 0, com.bame.client.sound.ClientSoundManager.HOVER_STYLES.length - 1)] + "  v";
        button(c, hsText, cx + cw - 100, r5Y + 3, 86, 18, mx, my);
        c.fill(cx + 10, r5Y + 28, cx + cw - 10, r5Y + 29, 0xFF202430);

        // Row 6: Hover Volume
        int r6Y = r5Y + 29;
        text(c, "Hover Volume", cx + 14, r6Y + 6, 0xFFD4D8E0);
        String hVolPct = Math.round(BameClientConfig.hoverVolume * 100) + "%";
        int hPctW = textRenderer.getWidth(CustomGuiUtils.getFontText(hVolPct));
        text(c, hVolPct, sliderX - hPctW - 8, r6Y + 6, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, sliderX, r6Y + 8, sliderW, 4, 0xFF303442, 2);
        int hFill = Math.round(sliderW * Math.clamp(BameClientConfig.hoverVolume, 0f, 1f));
        if (hFill > 0) CustomGuiUtils.fillUltraRounded(c, sliderX, r6Y + 8, hFill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sliderX + hFill - 3, r6Y + 6, 7, 8, 0xFFFFFFFF, 4);
    }
    private int thumbHeight() { return Math.max(24,(int)(ch*(ch/(double)Math.max(ch,contentHeight())))); }
    private int thumbY() { return cy+(int)((ch-thumbHeight())*(scroll/Math.max(1,maxScroll()))); }
    private void unfocus() {
        search.setFocused(false); corner1.setFocused(false); corner2.setFocused(false);
        if (itemSearchWidget != null) itemSearchWidget.setFocused(false);
        for (java.util.Map.Entry<String, CustomTextFieldWidget> fe : toolThresholdFields.entrySet()) {
            CustomTextFieldWidget f = fe.getValue();
            f.setFocused(false);
            if (f.getText().trim().isEmpty()) {
                Integer th = DurabilityGuardModule.toolThresholds.get(fe.getKey());
                f.setText(th != null ? String.valueOf(th) : "10");
            }
        }
        setFocused(null);
    }
    @Override public boolean mouseClicked(Click click,boolean twice) {
        layout(); double mx=click.x(),my=click.y();
        if(click.button()!=0) return false;

        if (itemModalOpen) {
            int modalW = 350;
            int modalH = 285;
            int modalX = (width - modalW) / 2;
            int modalY = (height - modalH) / 2;

            if (itemSearchWidget != null && itemSearchWidget.mouseClicked(click, twice)) {
                setFocused(itemSearchWidget);
                return true;
            }

            int btnY = modalY + modalH - 34;
            int btnH = 22;
            int btnW = 100;
            int btn1X = modalX + 16;
            int btn2X = btn1X + btnW + 9;
            int btn3X = btn2X + btnW + 9;

            if (inside(mx, my, btn1X, btnY, btnW, btnH)) {
                if (itemModalType == 1) {
                    tempToolThresholds.clear();
                } else {
                    tempSelectedItems.clear();
                }
                return true;
            }
            if (inside(mx, my, btn2X, btnY, btnW, btnH)) {
                itemModalOpen = false;
                return true;
            }
            if (inside(mx, my, btn3X, btnY, btnW, btnH)) {
                applyModalSave();
                return true;
            }

            if (cachedFilteredItems == null) updateItemFilter();
            int gridX = modalX + 16;
            int gridY = modalY + 50;
            int gridW = 297;
            int gridH = 185;
            int totalItems = cachedFilteredItems.size();
            int totalRows = (totalItems + 11) / 12;
            int totalContentH = totalRows * 25;
            int maxScroll = Math.max(0, totalContentH - gridH);

            int scrollbarX = modalX + modalW - 18;
            int scrollbarW = 6;
            if (maxScroll > 0 && inside(mx, my, scrollbarX - 2, gridY, scrollbarW + 4, gridH)) {
                itemScrollDragging = true;
                itemModalScroll = Math.clamp((my - gridY) / (double) gridH * maxScroll, 0, maxScroll);
                return true;
            }

            if (inside(mx, my, gridX, gridY, gridW, gridH)) {
                int relX = (int) mx - gridX;
                int relY = (int) (my - gridY + itemModalScroll);
                int col = relX / 25;
                int row = relY / 25;
                if (col >= 0 && col < 12 && (relX % 25) < 22 && (relY % 25) < 22) {
                    int index = row * 12 + col;
                    if (index >= 0 && index < totalItems) {
                        Item item = cachedFilteredItems.get(index);
                        String id = Registries.ITEM.getId(item).toString();
                        if (itemModalType == 1) {
                            if (tempToolThresholds.containsKey(id)) {
                                tempToolThresholds.remove(id);
                            } else {
                                tempToolThresholds.put(id, 10);
                            }
                        } else {
                            if (tempSelectedItems.contains(id)) {
                                tempSelectedItems.remove(id);
                            } else {
                                tempSelectedItems.add(id);
                            }
                        }
                        return true;
                    }
                }
                return true;
            }

            if (!inside(mx, my, modalX, modalY, modalW, modalH)) {
                applyModalSave();
                return true;
            }

            return true;
        }

        if (crosshairColorPickerOpen || hitColorColorPickerOpen || hitboxColorPickerOpen || blockOutlineColorPickerOpen) {
            int CP_W = 150;
            int CP_H = 100;
            int myCpX = (width - CP_W) / 2;
            int myCpY = (height - CP_H) / 2;
            if (inside(mx, my, myCpX + CP_W - 16, myCpY + 4, 14, 14)) {
                crosshairColorPickerOpen = false;
                hitColorColorPickerOpen = false;
                hitboxColorPickerOpen = false;
                blockOutlineColorPickerOpen = false;
                cpDrag = -1;
                return true;
            }
            int svX = myCpX + 46;
            int svY = myCpY + 22;
            int svW = 92;
            int svH = 30;
            if (inside(mx, my, svX, svY, svW, svH)) {
                cpDrag = 0;
                updateModalColor(mx, my);
                return true;
            }
            int barX = myCpX + 10;
            int barW = 128;
            int hueY = myCpY + 58;
            int barH = 8;
            if (inside(mx, my, barX, hueY, barW, barH)) {
                cpDrag = 1;
                updateModalColor(mx, my);
                return true;
            }
            if (inside(mx, my, myCpX, myCpY, CP_W, CP_H)) {
                return true;
            }
            crosshairColorPickerOpen = false;
            hitColorColorPickerOpen = false;
            hitboxColorPickerOpen = false;
            blockOutlineColorPickerOpen = false;
            cpDrag = -1;
            return true;
        }

        picker.release();
        if (inside(mx, my, px + 10, py + 11, 28, 28)) {
            if (com.bame.client.BameClientConfig.secretUnlocked) {
                com.bame.client.sound.ClientSoundManager.playClick();
                client.setScreen(new SecretScreen(this));
                return true;
            }
        }
        int step=Math.min(26,Math.max(17,(ph-165)/6));
        for(int i=0;i<CATEGORIES.length;i++) if(inside(mx,my,px+6,py+70+i*step,sidebar-12,22)) { select(CATEGORIES[i]); return true; }
        int gy=py+73+5*step+9;
        if(inside(mx,my,px+6,gy+16,sidebar-12,23)) { select("Theme"); return true; }
        if(inside(mx,my,px+6,gy+39,sidebar-12,23)) { select("Settings"); return true; }
        if(maxScroll()>0&&inside(mx,my,px+pw-14,cy,12,ch)) {
            unfocus(); scrollDragging=true;
            scrollGrab=inside(mx,my,px+pw-14,thumbY(),12,thumbHeight())?my-thumbY():thumbHeight()/2.0;
            dragScroll(my); return true;
        }
        unfocus();
        if(search.mouseClicked(click,twice)) { setFocused(search); return true; }
        if(!inside(mx,my,cx,cy,cw,ch)) return false;
        int y=baseY();
        if(selected.equals("Theme")) {
            int cols=columns(),tw=(cw-8*(cols-1))/cols;
            for(int i=0;i<GuiTheme.PRESETS.length;i++) if(inside(mx,my,cx+i%cols*(tw+8),y+44+i/cols*58,tw,50)) {
                com.bame.client.BameClientConfig.theme=GuiTheme.PRESETS[i].name(); com.bame.client.BameClientConfig.customMainColor=false; themeSettings.close(); com.bame.client.BameClientConfig.save(); return true;
            }
            if(themeSettings.click(mx,my)) { layout(); return true; }
        } else if(selected.equals("Settings")) {
            int iy = baseY() + 12;
            if(inside(mx,my,cx+cw-72,iy+30,60,16)) { listeningMenuBind = true; return true; }
            if(inside(mx,my,cx+cw-104,iy+54,92,16)) {
                com.bame.client.BameClientConfig.guiAnimation = (com.bame.client.BameClientConfig.guiAnimation + 1) % 5;
                com.bame.client.BameClientConfig.save();
                return true;
            }

            int soundY = iy + 80 + 16;
            int cardY = soundY + 30;

            // Row 1: Module Sound toggle
            int r1Y = cardY + 4;
            if (inside(mx, my, cx + cw - 38, r1Y + 4, 26, 14)) {
                BameClientConfig.moduleSound = !BameClientConfig.moduleSound;
                com.bame.client.sound.ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            // Row 2: Sound Pack button
            int r2Y = r1Y + 29;
            if (inside(mx, my, cx + cw - 100, r2Y + 3, 86, 18)) {
                BameClientConfig.soundPack = (BameClientConfig.soundPack + 1) % com.bame.client.sound.ClientSoundManager.SOUND_PACKS.length;
                com.bame.client.sound.ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            // Row 3: Volume slider
            int r3Y = r2Y + 29;
            int sliderW = 100;
            int sliderX = cx + cw - 14 - sliderW;
            if (inside(mx, my, sliderX - 4, r3Y + 4, sliderW + 8, 14)) {
                draggingSoundVolume = true;
                float fval = (float) Math.clamp((mx - sliderX) / (double) sliderW, 0.0, 1.0);
                BameClientConfig.soundVolume = Math.round(fval * 100f) / 100f;
                com.bame.client.sound.ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            // Row 4: Hover Sound toggle
            int r4Y = r3Y + 29;
            if (inside(mx, my, cx + cw - 38, r4Y + 4, 26, 14)) {
                BameClientConfig.hoverSound = !BameClientConfig.hoverSound;
                com.bame.client.sound.ClientSoundManager.playHover("toggle_hover_sound");
                BameClientConfig.save();
                return true;
            }

            // Row 5: Hover Style button
            int r5Y = r4Y + 29;
            if (inside(mx, my, cx + cw - 100, r5Y + 3, 86, 18)) {
                BameClientConfig.hoverStyle = (BameClientConfig.hoverStyle + 1) % com.bame.client.sound.ClientSoundManager.HOVER_STYLES.length;
                com.bame.client.sound.ClientSoundManager.playHover("style_preview_" + BameClientConfig.hoverStyle);
                BameClientConfig.save();
                return true;
            }

            // Row 6: Hover Volume slider
            int r6Y = r5Y + 29;
            if (inside(mx, my, sliderX - 4, r6Y + 4, sliderW + 8, 14)) {
                draggingHoverVolume = true;
                float fval = (float) Math.clamp((mx - sliderX) / (double) sliderW, 0.0, 1.0);
                BameClientConfig.hoverVolume = Math.round(fval * 100f) / 100f;
                com.bame.client.sound.ClientSoundManager.playHover("volume_preview");
                BameClientConfig.save();
                return true;
            }
        } else {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int leftY = 0;
            int rightY = 0;
            if(minerVisible()) {
                int myY = baseY() + leftY;
                if(inside(mx,my,cx+halfW-38,myY+12,26,14)) {
                    com.bame.client.module.AutoAreaMinerModule.enabled=!com.bame.client.module.AutoAreaMinerModule.enabled;
                    if(com.bame.client.module.AutoAreaMinerModule.enabled && (com.bame.client.module.AutoAreaMinerModule.corner1==null || com.bame.client.module.AutoAreaMinerModule.corner2==null)) {
                        if(client != null && client.player != null) {
                            client.player.sendMessage(Text.literal("§c[AutoAreaMiner] Bitte zuerst Corner 1 und Corner 2 festlegen!"), false);
                        }
                        com.bame.client.module.AutoAreaMinerModule.enabled=false;
                    }
                    return true;
                }
                if(inside(mx,my,cx+60,myY+25,48,16)) { listening=true; return true; }
                if(inside(mx,my,cx,myY,halfW,46)) { expanded=!expanded; layout(); return true; }
                if(expanded) {
                    if(corner1.mouseClicked(click,twice)) { setFocused(corner1); return true; }
                    if(corner2.mouseClicked(click,twice)) { setFocused(corner2); return true; }
                    if(inside(mx,my,cx+halfW-46,myY+134,34,18)) { setCorner(true); return true; }
                    if(inside(mx,my,cx+halfW-46,myY+154,34,18)) { setCorner(false); return true; }
                    if(inside(mx,my,cx+12,myY+178,halfW-24,20)) { AutoAreaMinerModule.mode3x3=!AutoAreaMinerModule.mode3x3; BameClientConfig.save(); return true; }
                    int btnW = (halfW - 24 - 6) / 2;
                    if(inside(mx,my,cx+12,myY+218,btnW,18)) { BameClientConfig.renderMode=0; BameClientConfig.save(); return true; }
                    if(inside(mx,my,cx+12+btnW+6,myY+218,btnW,18)) { BameClientConfig.renderMode=1; BameClientConfig.save(); return true; }
                    if(inside(mx,my,cx+12,myY+240,btnW,18)) { BameClientConfig.renderMode=2; BameClientConfig.save(); return true; }
                    if(inside(mx,my,cx+12+btnW+6,myY+240,btnW,18)) { BameClientConfig.renderMode=3; BameClientConfig.save(); return true; }
                    
                    if(picker.click(mx,my)) return true;

                    int sliderY = myY + 352;
                    int sx = cx + 12;
                    int sw = halfW - 24;
                    if (inside(mx, my, sx - 4, sliderY + 10, sw + 8, 14)) {
                        draggingWidth = true;
                        float val = (float)Math.clamp(((mx - sx) / (double)sw), 0.0, 1.0);
                        BameClientConfig.outlineWidth = 1.0f + val * 4.0f;
                        BameClientConfig.save();
                        return true;
                    }
                }
                leftY += getMinerHeight() + 12;
            }
            
            if (showHudVisible()) {
                int myY = baseY() + leftY;
                if (inside(mx, my, cx + halfW - 38, myY + 12, 26, 14)) {
                    ShowHudModule.enabled = !ShowHudModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, cx + 58, myY + 25, 42, 16)) {
                    listeningShowHud = true;
                    return true;
                }
                if (inside(mx, my, cx, myY, halfW, 46)) {
                    ShowHudModule.expanded = !ShowHudModule.expanded;
                    layout();
                    return true;
                }
                if (ShowHudModule.expanded) {
                    int curY = myY + 54;
                    // 1. Clock
                    if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                        ClockModule.enabled = !ClockModule.enabled;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // 2. Coordinates
                    if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                        CoordinatesModule.enabled = !CoordinatesModule.enabled;
                        BameClientConfig.save();
                        return true;
                    }
                    if (inside(mx, my, cx + 8, curY, halfW - 50, 22)) {
                        CoordinatesModule.expanded = !CoordinatesModule.expanded;
                        BameClientConfig.save();
                        layout();
                        return true;
                    }
                    curY += 26;

                    if (CoordinatesModule.expanded) {
                        // Sub 1: Layout
                        if (inside(mx, my, cx + halfW - 76, curY, 64, 16)) {
                            CoordinatesModule.cycleLayout();
                            BameClientConfig.save();
                            return true;
                        }
                        curY += 26;

                        // Sub 2: Style
                        if (inside(mx, my, cx + halfW - 76, curY, 64, 16)) {
                            CoordinatesModule.cycleStyle();
                            BameClientConfig.save();
                            return true;
                        }
                        curY += 26;

                        // Sub 3: Show X
                        if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                            CoordinatesModule.showX = !CoordinatesModule.showX;
                            BameClientConfig.save();
                            return true;
                        }
                        curY += 26;

                        // Sub 4: Show Y
                        if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                            CoordinatesModule.showY = !CoordinatesModule.showY;
                            BameClientConfig.save();
                            return true;
                        }
                        curY += 26;

                        // Sub 5: Show Z
                        if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                            CoordinatesModule.showZ = !CoordinatesModule.showZ;
                            BameClientConfig.save();
                            return true;
                        }
                        curY += 26;

                        // Sub 6: Decimals
                        if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                            CoordinatesModule.decimals = !CoordinatesModule.decimals;
                            BameClientConfig.save();
                            return true;
                        }
                        curY += 26;

                        // Sub 7: Nether Coords
                        if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                            CoordinatesModule.showNether = !CoordinatesModule.showNether;
                            BameClientConfig.save();
                            return true;
                        }
                        curY += 26;

                        // Sub 8: Direction
                        if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                            CoordinatesModule.showDirection = !CoordinatesModule.showDirection;
                            BameClientConfig.save();
                            return true;
                        }
                        curY += 26;
                    }

                    // 3. Potions
                    if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                        PotionsModule.enabled = !PotionsModule.enabled;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // 4. Target HUD (Click row to expand/collapse dropdown)
                    if (inside(mx, my, cx + 8, curY, halfW - 16, 22)) {
                        TargetHudModule.expanded = !TargetHudModule.expanded;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    if (TargetHudModule.expanded) {
                        // Sub 1: Show Hearts
                        if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                            TargetHudModule.showHearts = !TargetHudModule.showHearts;
                            TargetHudModule.enabled = TargetHudModule.showHearts || TargetHudModule.showArmor;
                            BameClientConfig.save();
                            return true;
                        }
                        curY += 26;

                        // Sub 2: Show Armor
                        if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                            TargetHudModule.showArmor = !TargetHudModule.showArmor;
                            TargetHudModule.enabled = TargetHudModule.showHearts || TargetHudModule.showArmor;
                            BameClientConfig.save();
                            return true;
                        }
                        curY += 26;

                        // Sub 3: Target Players Only
                        if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                            TargetHudModule.playersOnly = !TargetHudModule.playersOnly;
                            BameClientConfig.save();
                            return true;
                        }
                        curY += 26;
                    }

                    // 6. Armor HUD
                    if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                        ArmorHudModule.enabled = !ArmorHudModule.enabled;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // 7. Keystrokes
                    if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                        KeyStrokesModule.enabled = !KeyStrokesModule.enabled;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // 8. CPS
                    if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                        CpsModule.enabled = !CpsModule.enabled;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // 9. FPS
                    if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                        FpsModule.enabled = !FpsModule.enabled;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // 10. Ping
                    if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                        PingModule.enabled = !PingModule.enabled;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // 11. Server Info
                    if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                        ServerInfoModule.enabled = !ServerInfoModule.enabled;
                        BameClientConfig.save();
                        layout();
                        return true;
                    }
                    curY += 26;

                    if (ServerInfoModule.enabled) {
                        if (inside(mx, my, cx + 14, curY + 2, 44, 18)) {
                            ServerInfoModule.showName = !ServerInfoModule.showName;
                            BameClientConfig.save();
                            return true;
                        }
                        if (inside(mx, my, cx + 62, curY + 2, 48, 18)) {
                            ServerInfoModule.showServer = !ServerInfoModule.showServer;
                            BameClientConfig.save();
                            return true;
                        }
                        if (inside(mx, my, cx + 114, curY + 2, 42, 18)) {
                            ServerInfoModule.showTime = !ServerInfoModule.showTime;
                            BameClientConfig.save();
                            return true;
                        }
                        curY += 24;
                    }

                    // Bottom buttons in dropdown
                    curY += 4;
                    if (inside(mx, my, cx + 14, curY, 50, 16)) {
                        client.setScreen(new HudEditorScreen(this));
                        return true;
                    }
                    if (inside(mx, my, cx + halfW - 58, curY, 46, 16)) {
                        resetShowHud();
                        return true;
                    }
                }
                leftY += getShowHudHeight() + 12;
            }

            if (scoreboardVisible()) {
                int myY = baseY() + leftY;
                if (inside(mx, my, cx + halfW - 38, myY + 12, 26, 14)) {
                    ScoreboardModule.enabled = !ScoreboardModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, cx + 60, myY + 25, 48, 16)) {
                    listeningScoreboard = true;
                    return true;
                }
                if (inside(mx, my, cx, myY, halfW, 46)) {
                    ScoreboardModule.expanded = !ScoreboardModule.expanded;
                    layout();
                    return true;
                }
                if (ScoreboardModule.expanded) {
                    int editHudY = myY + 54;
                    if (inside(mx, my, cx + 14, editHudY, 50, 16)) {
                        client.setScreen(new HudEditorScreen(this));
                        return true;
                    }
                    if (inside(mx, my, cx + halfW - 58, editHudY, 46, 16)) {
                        resetScoreboard();
                        return true;
                    }
                }
                leftY += getScoreboardHeight() + 12;
            }

            if (invMoveVisible()) {
                int myY = baseY() + leftY;
                if (inside(mx, my, cx + halfW - 38, myY + 12, 26, 14)) {
                    InvMoveModule.enabled = !InvMoveModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, cx + 60, myY + 25, 48, 16)) {
                    listeningInvMove = true;
                    return true;
                }
                if (inside(mx, my, cx, myY, halfW, 46)) {
                    InvMoveModule.expanded = !InvMoveModule.expanded;
                    layout();
                    return true;
                }
                if (InvMoveModule.expanded) {
                    int curY = myY + 54;
                    // Jump
                    if (inside(mx, my, cx + halfW - 38, curY + 2, 26, 14)) {
                        InvMoveModule.jump = !InvMoveModule.jump;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Sprint
                    if (inside(mx, my, cx + halfW - 38, curY + 2, 26, 14)) {
                        InvMoveModule.sprint = !InvMoveModule.sprint;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Sneak
                    if (inside(mx, my, cx + halfW - 38, curY + 2, 26, 14)) {
                        InvMoveModule.sneak = !InvMoveModule.sneak;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Arrow Key Rotation
                    if (inside(mx, my, cx + halfW - 38, curY + 2, 26, 14)) {
                        InvMoveModule.rotateWithArrows = !InvMoveModule.rotateWithArrows;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Reset button
                    if (inside(mx, my, cx + halfW - 58, curY, 46, 16)) {
                        resetInvMove();
                        return true;
                    }
                }
                leftY += getInvMoveHeight() + 12;
            }

            if (autoClickerVisible()) {
                int myY = baseY() + leftY;
                if (inside(mx, my, cx + halfW - 38, myY + 12, 26, 14)) {
                    AutoClickerModule.enabled = !AutoClickerModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, cx + 60, myY + 25, 48, 16)) {
                    listeningAutoClicker = true;
                    return true;
                }
                if (inside(mx, my, cx, myY, halfW, 46)) {
                    AutoClickerModule.expanded = !AutoClickerModule.expanded;
                    layout();
                    return true;
                }
                if (AutoClickerModule.expanded) {
                    int curY = myY + 54;
                    // Row 1: Mode buttons
                    if (inside(mx, my, cx + 54, curY + 2, 38, 18)) {
                        AutoClickerModule.mode = 0;
                        BameClientConfig.save();
                        return true;
                    }
                    if (inside(mx, my, cx + 96, curY + 2, 44, 18)) {
                        AutoClickerModule.mode = 1;
                        BameClientConfig.save();
                        return true;
                    }
                    if (inside(mx, my, cx + 144, curY + 2, 58, 18)) {
                        AutoClickerModule.mode = 2;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 2: Dynamic setting (CPS or Delay)
                    if (AutoClickerModule.mode == 0) {
                        int sx = cx + 72;
                        int sw = halfW - 86;
                        if (inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                            draggingCps = true;
                            float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                            AutoClickerModule.cps = Math.max(1, Math.round(1 + fval * 19));
                            BameClientConfig.save();
                            return true;
                        }
                    } else if (AutoClickerModule.mode == 1) {
                        int sx = cx + 88;
                        int sw = halfW - 102;
                        if (inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                            draggingAutoClickerDelay = true;
                            float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                            AutoClickerModule.delaySeconds = Math.max(0.1f, Math.round((0.1f + fval * 4.9f) * 10f) / 10f);
                            BameClientConfig.save();
                            return true;
                        }
                    }
                    curY += 26;

                    // Row 3: Hold Mouse
                    if (inside(mx, my, cx + halfW - 38, curY + 2, 26, 14)) {
                        AutoClickerModule.holdMouse = !AutoClickerModule.holdMouse;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 4: Only on Target
                    if (inside(mx, my, cx + halfW - 38, curY + 2, 26, 14)) {
                        AutoClickerModule.onlyOnTarget = !AutoClickerModule.onlyOnTarget;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 5: Random Jitter
                    if (inside(mx, my, cx + halfW - 38, curY + 2, 26, 14)) {
                        AutoClickerModule.randomJitter = !AutoClickerModule.randomJitter;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Weapon Only
                    if (inside(mx, my, cx + halfW - 38, curY + 2, 26, 14)) {
                        AutoClickerModule.weaponOnly = !AutoClickerModule.weaponOnly;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Button Left / Right
                    if (inside(mx, my, cx + 60, curY + 2, 44, 18)) {
                        AutoClickerModule.button = 0;
                        BameClientConfig.save();
                        return true;
                    }
                    if (inside(mx, my, cx + 108, curY + 2, 48, 18)) {
                        AutoClickerModule.button = 1;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Reset button
                    if (inside(mx, my, cx + halfW - 58, curY, 46, 16)) {
                        resetAutoClicker();
                        return true;
                    }
                }
                leftY += getAutoClickerHeight() + 12;
            }

            if (reachDisplayVisible()) {
                int myY = baseY() + leftY;
                if (inside(mx, my, cx + halfW - 38, myY + 12, 26, 14)) {
                    ReachDisplayModule.enabled = !ReachDisplayModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, cx + 60, myY + 25, 48, 16)) {
                    listeningReachDisplay = true;
                    return true;
                }
                if (inside(mx, my, cx, myY, halfW, 46)) {
                    ReachDisplayModule.expanded = !ReachDisplayModule.expanded;
                    layout();
                    return true;
                }
                if (ReachDisplayModule.expanded) {
                    int curY = myY + 54 + 20;
                    if (inside(mx, my, cx + 14, curY, 96, 18)) {
                        ReachDisplayModule.mode = 0;
                        BameClientConfig.save();
                        return true;
                    }
                    if (inside(mx, my, cx + 114, curY, 78, 18)) {
                        ReachDisplayModule.mode = 1;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    if (ReachDisplayModule.mode == 1 && inside(mx, my, cx + 14, curY, 50, 16)) {
                        client.setScreen(new HudEditorScreen(this));
                        return true;
                    }
                    if (inside(mx, my, cx + halfW - 58, curY, 46, 16)) {
                        resetReachDisplay();
                        return true;
                    }
                }
                leftY += getReachDisplayHeight() + 12;
            }

            if (autoToolVisible()) {
                int myY = baseY() + leftY;
                if (inside(mx, my, cx + halfW - 38, myY + 12, 26, 14)) {
                    AutoToolModule.enabled = !AutoToolModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, cx + 60, myY + 25, 48, 16)) {
                    listeningAutoTool = true;
                    return true;
                }
                if (inside(mx, my, cx, myY, halfW, 46)) {
                    AutoToolModule.expanded = !AutoToolModule.expanded;
                    layout();
                    return true;
                }
                if (AutoToolModule.expanded) {
                    int curY = myY + 54;
                    // Row 1: Switch Back
                    if (inside(mx, my, cx + halfW - 38, curY + 2, 26, 14)) {
                        AutoToolModule.switchBack = !AutoToolModule.switchBack;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 2: Reset
                    if (inside(mx, my, cx + halfW - 58, curY, 46, 16)) {
                        resetAutoTool();
                        return true;
                    }
                }
                leftY += getAutoToolHeight() + 12;
            }

            if (blockOutlineVisible()) {
                int myY = baseY() + leftY;
                if (inside(mx, my, cx + halfW - 38, myY + 12, 26, 14)) {
                    BlockOutlineModule.enabled = !BlockOutlineModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, cx + 60, myY + 25, 48, 16)) {
                    listeningBlockOutline = true;
                    return true;
                }
                if (inside(mx, my, cx, myY, halfW, 46)) {
                    BlockOutlineModule.expanded = !BlockOutlineModule.expanded;
                    layout();
                    return true;
                }
                if (BlockOutlineModule.expanded) {
                    int curY = myY + 54;
                    // Row 1: Color square
                    if (inside(mx, my, cx + 56, curY + 2, 16, 16)) {
                        blockOutlineColorPickerOpen = true;
                        hitboxColorPickerOpen = false;
                        hitColorColorPickerOpen = false;
                        crosshairColorPickerOpen = false;
                        syncBlockOutlineHsv();
                        return true;
                    }
                    curY += 26;

                    // Row 2: Chroma
                    if (inside(mx, my, cx + halfW - 38, curY + 2, 26, 14)) {
                        BlockOutlineModule.chroma = !BlockOutlineModule.chroma;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 3: Width slider
                    int wx = cx + 82;
                    int ww = halfW - 96;
                    if (inside(mx, my, wx - 4, curY + 2, ww + 8, 14)) {
                        draggingBlockOutlineWidth = true;
                        float fval = (float) Math.clamp((mx - wx) / (double) ww, 0.0, 1.0);
                        BlockOutlineModule.lineWidth = Math.max(1.0f, Math.round((1.0f + fval * 5.0f) * 10f) / 10f);
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 4: Opacity slider
                    int ox = cx + 90;
                    int ow = halfW - 104;
                    if (inside(mx, my, ox - 4, curY + 2, ow + 8, 14)) {
                        draggingBlockOutlineOpacity = true;
                        float fval = (float) Math.clamp((mx - ox) / (double) ow, 0.0, 1.0);
                        BlockOutlineModule.opacity = Math.max(0.2f, Math.round((0.2f + fval * 0.8f) * 100f) / 100f);
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 5: Reset
                    if (inside(mx, my, cx + halfW - 58, curY, 46, 16)) {
                        resetBlockOutline();
                        return true;
                    }
                }
                leftY += getBlockOutlineHeight() + 12;
            }

            if (timeChangerVisible()) {
                int myY = baseY() + leftY;
                int h = getTimeChangerHeight();
                // Toggle
                if (inside(mx, my, cx + halfW - 38, myY + 12, 26, 14)) {
                    TimeChangerModule.enabled = !TimeChangerModule.enabled;
                    com.bame.client.sound.ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }
                // Keybind
                if (inside(mx, my, cx + 60, myY + 25, 48, 16)) {
                    listeningTimeChanger = true;
                    return true;
                }
                // Card header expand / collapse
                if (inside(mx, my, cx, myY, halfW, 46)) {
                    TimeChangerModule.expanded = !TimeChangerModule.expanded;
                    com.bame.client.sound.ClientSoundManager.playClick();
                    BameClientConfig.save();
                    layout();
                    return true;
                }
                if (TimeChangerModule.expanded) {
                    int curY = myY + 54;
                    // Row 1: Time Button
                    if (inside(mx, my, cx + halfW - 74, curY, 60, 16)) {
                        TimeChangerModule.nextMode();
                        com.bame.client.sound.ClientSoundManager.playClick();
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 2: Reset Button
                    if (inside(mx, my, cx + halfW - 58, curY, 46, 16)) {
                        TimeChangerModule.reset();
                        com.bame.client.sound.ClientSoundManager.playClick();
                        BameClientConfig.save();
                        return true;
                    }
                }
                leftY += getTimeChangerHeight() + 12;
            }

            if (handPositionVisible()) {
                int myY = baseY() + leftY;
                // Toggle
                if (inside(mx, my, cx + halfW - 38, myY + 12, 26, 14)) {
                    HandPositionModule.enabled = !HandPositionModule.enabled;
                    com.bame.client.sound.ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }
                // Keybind
                if (inside(mx, my, cx + 60, myY + 25, 48, 16)) {
                    listeningHandPosition = true;
                    return true;
                }
                // Card header expand / collapse
                if (inside(mx, my, cx, myY, halfW, 46)) {
                    HandPositionModule.expanded = !HandPositionModule.expanded;
                    com.bame.client.sound.ClientSoundManager.playClick();
                    BameClientConfig.save();
                    layout();
                    return true;
                }
                if (HandPositionModule.expanded) {
                    int curY = myY + 54;
                    // Row 1: Swing Style mode buttons
                    int sBtnW = (halfW - 24 - 9) / 4;
                    for (int i = 0; i < 4; i++) {
                        if (inside(mx, my, cx + 12 + i * (sBtnW + 3), curY, sBtnW, 16)) {
                            HandPositionModule.swingStyle = i;
                            com.bame.client.sound.ClientSoundManager.playClick();
                            BameClientConfig.save();
                            return true;
                        }
                    }
                    curY += 22;

                    // Row 2: Presets
                    int pBtnW = (halfW - 24 - 9) / 4;
                    for (int i = 0; i < 4; i++) {
                        if (inside(mx, my, cx + 12 + i * (pBtnW + 3), curY, pBtnW, 16)) {
                            HandPositionModule.applyPreset(i);
                            com.bame.client.sound.ClientSoundManager.playClick();
                            BameClientConfig.save();
                            return true;
                        }
                    }
                    curY += 24;

                    // Two columns
                    int colGap = 10;
                    int colW = (halfW - 24 - colGap) / 2;
                    int c1X = cx + 12;
                    int c2X = c1X + colW + colGap;

                    curY += 14; // skip section headers

                    // Row 4: X Slider (Left) | Pitch Slider (Right)
                    if (inside(mx, my, c1X - 2, curY + 6, colW + 4, 14)) {
                        draggingHandX = true;
                        float fval = (float) Math.clamp((mx - c1X) / (double) colW, 0.0, 1.0);
                        HandPositionModule.posX = Math.round((-0.80f + fval * 1.60f) * 100f) / 100f;
                        BameClientConfig.save();
                        return true;
                    }
                    if (inside(mx, my, c2X - 2, curY + 6, colW + 4, 14)) {
                        draggingHandPitch = true;
                        float fval = (float) Math.clamp((mx - c2X) / (double) colW, 0.0, 1.0);
                        HandPositionModule.pitch = Math.round(-180f + fval * 360f);
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 23;

                    // Row 5: Y Slider (Left) | Yaw Slider (Right)
                    if (inside(mx, my, c1X - 2, curY + 6, colW + 4, 14)) {
                        draggingHandY = true;
                        float fval = (float) Math.clamp((mx - c1X) / (double) colW, 0.0, 1.0);
                        HandPositionModule.posY = Math.round((-0.80f + fval * 1.60f) * 100f) / 100f;
                        BameClientConfig.save();
                        return true;
                    }
                    if (inside(mx, my, c2X - 2, curY + 6, colW + 4, 14)) {
                        draggingHandYaw = true;
                        float fval = (float) Math.clamp((mx - c2X) / (double) colW, 0.0, 1.0);
                        HandPositionModule.yaw = Math.round(-180f + fval * 360f);
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 23;

                    // Row 6: Z Slider (Left) | Roll Slider (Right)
                    if (inside(mx, my, c1X - 2, curY + 6, colW + 4, 14)) {
                        draggingHandZ = true;
                        float fval = (float) Math.clamp((mx - c1X) / (double) colW, 0.0, 1.0);
                        HandPositionModule.posZ = Math.round((-0.80f + fval * 1.60f) * 100f) / 100f;
                        BameClientConfig.save();
                        return true;
                    }
                    if (inside(mx, my, c2X - 2, curY + 6, colW + 4, 14)) {
                        draggingHandRoll = true;
                        float fval = (float) Math.clamp((mx - c2X) / (double) colW, 0.0, 1.0);
                        HandPositionModule.roll = Math.round(-180f + fval * 360f);
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 23;

                    // Row 7: Scale Slider (Left) | Offhand Toggle (Right)
                    if (inside(mx, my, c1X - 2, curY + 6, colW + 4, 14)) {
                        draggingHandScale = true;
                        float fval = (float) Math.clamp((mx - c1X) / (double) colW, 0.0, 1.0);
                        HandPositionModule.scale = Math.round((0.30f + fval * 1.20f) * 100f) / 100f;
                        BameClientConfig.save();
                        return true;
                    }
                    if (inside(mx, my, c2X + colW - 28, curY, 26, 14)) {
                        HandPositionModule.applyToOffhand = !HandPositionModule.applyToOffhand;
                        com.bame.client.sound.ClientSoundManager.playClick();
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 23;

                    // Row 8: Weapons Only Toggle (Left) | Reset Button (Right)
                    if (inside(mx, my, c1X + colW - 28, curY, 26, 14)) {
                        HandPositionModule.weaponsOnly = !HandPositionModule.weaponsOnly;
                        com.bame.client.sound.ClientSoundManager.playClick();
                        BameClientConfig.save();
                        return true;
                    }
                    if (inside(mx, my, c2X + colW - 46, curY, 46, 16)) {
                        resetHandPosition();
                        com.bame.client.sound.ClientSoundManager.playClick();
                        return true;
                    }
                }
                leftY += getHandPositionHeight() + 12;
            }

            if (noFogVisible()) {
                int myY = baseY() + rightY;
                int nfX = cx + halfW + gap;
                if (inside(mx, my, nfX + halfW - 38, myY + 12, 26, 14)) {
                    NoFogModule.enabled = !NoFogModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, nfX + 60, myY + 25, 48, 16)) {
                    listeningNoFog = true;
                    return true;
                }
                if (inside(mx, my, nfX, myY, halfW, 46)) {
                    NoFogModule.expanded = !NoFogModule.expanded;
                    layout();
                    return true;
                }
                if (NoFogModule.expanded) {
                    int curY = myY + 54;
                    // Row 1: All Fog
                    if (inside(mx, my, nfX + halfW - 38, curY + 2, 26, 14)) {
                        NoFogModule.allFog = !NoFogModule.allFog;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 2: Nether Fog
                    if (inside(mx, my, nfX + halfW - 38, curY + 2, 26, 14)) {
                        NoFogModule.netherFog = !NoFogModule.netherFog;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 3: Water Fog
                    if (inside(mx, my, nfX + halfW - 38, curY + 2, 26, 14)) {
                        NoFogModule.waterFog = !NoFogModule.waterFog;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 4: Lava Fog
                    if (inside(mx, my, nfX + halfW - 38, curY + 2, 26, 14)) {
                        NoFogModule.lavaFog = !NoFogModule.lavaFog;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 5: Reset
                    if (inside(mx, my, nfX + halfW - 58, curY, 46, 16)) {
                        resetNoFog();
                        return true;
                    }
                }
                rightY += getNoFogHeight() + 12;
            }

            if (fullbrightVisible()) {
                int myY = baseY() + rightY;
                int fX = cx + halfW + gap;
                if (inside(mx, my, fX + halfW - 38, myY + 12, 26, 14)) {
                    FullbrightModule.enabled = !FullbrightModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, fX + 60, myY + 25, 48, 16)) {
                    listeningFullbright = true;
                    return true;
                }
                if (inside(mx, my, fX, myY, halfW, 46)) {
                    fullbrightExpanded = !fullbrightExpanded;
                    layout();
                    return true;
                }
                if (fullbrightExpanded) {
                    int sliderY = myY + 58;
                    int sx = fX + 12;
                    int sw = halfW - 24;
                    if (inside(mx, my, sx, sliderY + 16, sw, 12)) {
                        draggingFullbright = true;
                        float fval = (float)Math.clamp(((mx - sx) / (double)sw), 0.0, 1.0);
                        com.bame.client.module.FullbrightModule.intensity = fval;
                        return true;
                    }
                }
                rightY += (fullbrightExpanded ? 92 : 46) + 12;
            }

            if (zoomVisible()) {
                int myY = baseY() + rightY;
                int zX = cx + halfW + gap;
                if (inside(mx, my, zX + halfW - 38, myY + 12, 26, 14)) {
                    ZoomModule.enabled = !ZoomModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, zX + 60, myY + 25, 48, 16)) {
                    listeningZoom = true;
                    return true;
                }
                if (inside(mx, my, zX, myY, halfW, 46)) {
                    ZoomModule.expanded = !ZoomModule.expanded;
                    layout();
                    return true;
                }
                if (ZoomModule.expanded) {
                    int curY = myY + 54;
                    // Row 1: Mode (Hold / Toggle)
                    if (inside(mx, my, zX + halfW - 58, curY, 46, 16)) {
                        ZoomModule.type = (ZoomModule.type == 0) ? 1 : 0;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;
                    // Row 2: Animation (Smooth / Instant)
                    if (inside(mx, my, zX + halfW - 68, curY, 56, 16)) {
                        ZoomModule.mode = (ZoomModule.mode == 0) ? 1 : 0;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;
                    // Row 3: Scroll Zoom (Toggle)
                    if (inside(mx, my, zX + halfW - 38, curY + 2, 26, 14)) {
                        ZoomModule.scrollZoom = !ZoomModule.scrollZoom;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;
                    // Row 4: Default Zoom level
                    if (inside(mx, my, zX + halfW - 48, curY, 36, 16)) {
                        ZoomModule.cycleDefaultZoom();
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;
                    // Row 5: Reset
                    if (inside(mx, my, zX + halfW - 58, curY, 46, 16)) {
                        ZoomModule.type = 0;
                        ZoomModule.mode = 0;
                        ZoomModule.scrollZoom = true;
                        ZoomModule.defaultLevel = 1;
                        ZoomModule.keyBind = org.lwjgl.glfw.GLFW.GLFW_KEY_C;
                        BameClientConfig.save();
                        return true;
                    }
                }
                rightY += getZoomHeight() + 12;
            }

            if (spotifyHudVisible()) {
                int myY = baseY() + rightY;
                int sX = cx + halfW + gap;
                if (inside(mx, my, sX + halfW - 38, myY + 12, 26, 14)) {
                    SpotifyHudModule.enabled = !SpotifyHudModule.enabled;
                    if (SpotifyHudModule.enabled) {
                        com.bame.client.spotify.SpotifyService.start();
                    }
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, sX + 60, myY + 25, 48, 16)) {
                    listeningSpotify = true;
                    return true;
                }
                if (inside(mx, my, sX, myY, halfW, 46)) {
                    SpotifyHudModule.expanded = !SpotifyHudModule.expanded;
                    layout();
                    return true;
                }
                if (SpotifyHudModule.expanded) {
                    if (inside(mx, my, sX + halfW - 38, myY + 56, 26, 14)) {
                        SpotifyHudModule.autoHide = !SpotifyHudModule.autoHide;
                        BameClientConfig.save();
                        return true;
                    }
                    int editHudY = myY + 54 + 24;
                    if (inside(mx, my, sX + 14, editHudY, 50, 16)) {
                        client.setScreen(new HudEditorScreen(this));
                        return true;
                    }
                    if (inside(mx, my, sX + halfW - 58, editHudY, 46, 16)) {
                        resetSpotifyHud();
                        return true;
                    }
                }
                rightY += getSpotifyHudHeight() + 12;
            }

            if (customCrosshairVisible()) {
                int myY = baseY() + rightY;
                int cX = cx + halfW + gap;
                if (inside(mx, my, cX + halfW - 38, myY + 12, 26, 14)) {
                    CustomCrosshairModule.enabled = !CustomCrosshairModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, cX + 60, myY + 25, 48, 16)) {
                    listeningCrosshair = true;
                    return true;
                }
                if (inside(mx, my, cX, myY, halfW, 46)) {
                    CustomCrosshairModule.expanded = !CustomCrosshairModule.expanded;
                    layout();
                    return true;
                }
                if (CustomCrosshairModule.expanded) {
                    // 1. 15x15 Editor Grid click (at top: gridY = myY + 52)
                    int gridY = myY + 52;
                    int gridX = cX + 12;
                    if (inside(mx, my, gridX, gridY, 105, 105)) {
                        int col = (int)((mx - gridX) / 7);
                        int row = (int)((my - gridY) / 7);
                        if (col >= 0 && col < 15 && row >= 0 && row < 15) {
                            boolean newState = !CustomCrosshairModule.grid[row][col];
                            CustomCrosshairModule.grid[row][col] = newState;
                            gridDragMode = newState ? 1 : 0;
                            lastEditedCol = col;
                            lastEditedRow = row;
                            BameClientConfig.save();
                            return true;
                        }
                    }

                    // 2. Presets click (under crosshair: pY = myY + 165)
                    int pY = myY + 165;
                    int pStartX = cX + 58;
                    for (int i = 0; i < 9; i++) {
                        int bx = pStartX + i * 19;
                        if (inside(mx, my, bx, pY, 17, 17)) {
                            CustomCrosshairModule.applyPreset(i);
                            BameClientConfig.save();
                            return true;
                        }
                    }

                    // 3. Actions row: Color, Clear on the left, Reset on bottom right (actY = myY + 188)
                    int actY = myY + 188;
                    if (inside(mx, my, cX + 44, actY, 16, 16)) {
                        crosshairColorPickerOpen = true;
                        hitColorColorPickerOpen = false;
                        hitboxColorPickerOpen = false;
                        blockOutlineColorPickerOpen = false;
                        syncCrosshairHsv();
                        return true;
                    }

                    if (inside(mx, my, cX + 68, actY, 38, 16)) {
                        CustomCrosshairModule.clearGrid();
                        BameClientConfig.save();
                        return true;
                    }

                    if (inside(mx, my, cX + halfW - 58, actY, 46, 16)) {
                        resetCustomCrosshair();
                        return true;
                    }
                }
                rightY += getCustomCrosshairHeight() + 12;
            }

            if (hitColorVisible()) {
                int myY = baseY() + rightY;
                int hX = cx + halfW + gap;
                if (inside(mx, my, hX + halfW - 38, myY + 12, 26, 14)) {
                    HitColorModule.enabled = !HitColorModule.enabled;
                    HitColorModule.apply();
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, hX + 60, myY + 25, 48, 16)) {
                    listeningHitColor = true;
                    return true;
                }
                if (inside(mx, my, hX, myY, halfW, 46)) {
                    HitColorModule.expanded = !HitColorModule.expanded;
                    layout();
                    return true;
                }
                if (HitColorModule.expanded) {
                    int curY = myY + 54;
                    // Color square
                    if (inside(mx, my, hX + 56, curY + 2, 16, 16)) {
                        hitColorColorPickerOpen = true;
                        crosshairColorPickerOpen = false;
                        hitboxColorPickerOpen = false;
                        blockOutlineColorPickerOpen = false;
                        syncHitColorHsv();
                        return true;
                    }
                    curY += 26;

                    // Opacity Slider (0.1 to 1.0)
                    int sx = hX + 88;
                    int sw = halfW - 102;
                    if (inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                        draggingHitColorAlpha = true;
                        float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                        HitColorModule.alpha = 0.1f + fval * 0.9f;
                        HitColorModule.apply();
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Reset button
                    if (inside(mx, my, hX + halfW - 58, curY, 46, 16)) {
                        resetHitColor();
                        return true;
                    }
                }
                rightY += getHitColorHeight() + 12;
            }

            if (lowShieldVisible()) {
                int myY = baseY() + rightY;
                int lX = cx + halfW + gap;
                if (inside(mx, my, lX + halfW - 38, myY + 12, 26, 14)) {
                    LowShieldModule.enabled = !LowShieldModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, lX + 60, myY + 25, 48, 16)) {
                    listeningLowShield = true;
                    return true;
                }
                if (inside(mx, my, lX, myY, halfW, 46)) {
                    LowShieldModule.expanded = !LowShieldModule.expanded;
                    layout();
                    return true;
                }
                if (LowShieldModule.expanded) {
                    int curY = myY + 54;
                    // Height Slider (1 to 100%)
                    int sx = lX + 84;
                    int sw = halfW - 98;
                    if (inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                        draggingLowShieldHeight = true;
                        float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                        LowShieldModule.heightPercent = Math.max(1, Math.round(1 + fval * 99));
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Totem Slider (10 to 100%)
                    if (inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                        draggingLowShieldTotem = true;
                        float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                        LowShieldModule.totemSizePercent = Math.max(10, Math.round(10 + fval * 90));
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Reset button
                    if (inside(mx, my, lX + halfW - 58, curY, 46, 16)) {
                        resetLowShield();
                        return true;
                    }
                }
                rightY += getLowShieldHeight() + 12;
            }

            if (customHitboxesVisible()) {
                int myY = baseY() + rightY;
                int hbX = cx + halfW + gap;
                if (inside(mx, my, hbX + halfW - 38, myY + 12, 26, 14)) {
                    CustomHitboxesModule.setEnabled(!CustomHitboxesModule.enabled);
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, hbX + 60, myY + 25, 48, 16)) {
                    listeningHitboxes = true;
                    return true;
                }
                if (inside(mx, my, hbX, myY, halfW, 46)) {
                    CustomHitboxesModule.expanded = !CustomHitboxesModule.expanded;
                    layout();
                    return true;
                }
                if (CustomHitboxesModule.expanded) {
                    int curY = myY + 54;
                    // Row 1: Color square
                    if (inside(mx, my, hbX + 56, curY + 2, 16, 16)) {
                        hitboxColorPickerOpen = true;
                        hitColorColorPickerOpen = false;
                        crosshairColorPickerOpen = false;
                        blockOutlineColorPickerOpen = false;
                        syncHitboxHsv();
                        return true;
                    }
                    curY += 26;

                    // Row 2: Fill Opacity Slider (0.0 to 1.0)
                    int sx = hbX + 72;
                    int sw = halfW - 86;
                    if (inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                        draggingHitboxAlpha = true;
                        float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                        CustomHitboxesModule.fillOpacity = Math.round(fval * 100f) / 100f;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 3: Line Width Slider (1.0 to 5.0)
                    int wx = hbX + 82;
                    int ww = halfW - 96;
                    if (inside(mx, my, wx - 4, curY + 2, ww + 8, 14)) {
                        draggingHitboxWidth = true;
                        float fval = (float) Math.clamp((mx - wx) / (double) ww, 0.0, 1.0);
                        CustomHitboxesModule.lineWidth = Math.max(1.0f, Math.round((1.0f + fval * 4.0f) * 10f) / 10f);
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 4: Target Mode [All] [Players] [Mobs]
                    if (inside(mx, my, hbX + 60, curY + 2, 34, 18)) {
                        CustomHitboxesModule.targetMode = 0;
                        BameClientConfig.save();
                        return true;
                    }
                    if (inside(mx, my, hbX + 98, curY + 2, 50, 18)) {
                        CustomHitboxesModule.targetMode = 1;
                        BameClientConfig.save();
                        return true;
                    }
                    if (inside(mx, my, hbX + 152, curY + 2, 44, 18)) {
                        CustomHitboxesModule.targetMode = 2;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 5: Eye Height Line
                    if (inside(mx, my, hbX + halfW - 38, curY + 2, 26, 14)) {
                        CustomHitboxesModule.showEyeHeight = !CustomHitboxesModule.showEyeHeight;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 6: View Direction
                    if (inside(mx, my, hbX + halfW - 38, curY + 2, 26, 14)) {
                        CustomHitboxesModule.showViewVector = !CustomHitboxesModule.showViewVector;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 7: Reset button
                    if (inside(mx, my, hbX + halfW - 58, curY, 46, 16)) {
                        resetCustomHitboxes();
                        return true;
                    }
                }
                rightY += getCustomHitboxesHeight() + 12;
            }

            if (freelookVisible()) {
                int myY = baseY() + rightY;
                int flX = cx + halfW + gap;
                if (inside(mx, my, flX + halfW - 38, myY + 12, 26, 14)) {
                    FreelookModule.enabled = !FreelookModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, flX + 60, myY + 25, 48, 16)) {
                    listeningFreelook = true;
                    return true;
                }
                if (inside(mx, my, flX, myY, halfW, 46)) {
                    FreelookModule.expanded = !FreelookModule.expanded;
                    layout();
                    return true;
                }
                if (FreelookModule.expanded) {
                    int curY = myY + 54;
                    // Row 1: Mode (Hold / Toggle)
                    if (inside(mx, my, flX + halfW - 58, curY, 46, 16)) {
                        FreelookModule.toggleMode = !FreelookModule.toggleMode;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 2: Invert Pitch
                    if (inside(mx, my, flX + halfW - 38, curY + 2, 26, 14)) {
                        FreelookModule.invertPitch = !FreelookModule.invertPitch;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 3: Sensitivity Slider
                    int sx = flX + 84;
                    int sw = halfW - 98;
                    if (inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                        draggingFreelookSensitivity = true;
                        float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                        FreelookModule.sensitivity = Math.round((0.5f + fval * 1.5f) * 100f) / 100f;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 4: Reset
                    if (inside(mx, my, flX + halfW - 58, curY, 46, 16)) {
                        resetFreelook();
                        return true;
                    }
                }
                rightY += getFreelookHeight() + 12;
            }

            if (itemSizeVisible()) {
                int myY = baseY() + rightY;
                int isX = cx + halfW + gap;
                if (inside(mx, my, isX + halfW - 38, myY + 12, 26, 14)) {
                    ItemSizeModule.enabled = !ItemSizeModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, isX + 60, myY + 25, 48, 16)) {
                    listeningItemSize = true;
                    return true;
                }
                if (inside(mx, my, isX, myY, halfW, 46)) {
                    ItemSizeModule.expanded = !ItemSizeModule.expanded;
                    layout();
                    return true;
                }
                if (ItemSizeModule.expanded) {
                    int curY = myY + 54;
                    // Row 1: Scale Slider (25% to 400%)
                    int sx = isX + 88;
                    int sw = halfW - 102;
                    if (inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                        draggingItemScale = true;
                        float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                        ItemSizeModule.scale = Math.round((0.25f + fval * 3.75f) * 100f) / 100f;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 2: Y-Offset Slider (-0.20 to 1.00)
                    int yx = isX + 96;
                    int yw = halfW - 110;
                    if (inside(mx, my, yx - 4, curY + 2, yw + 8, 14)) {
                        draggingItemYOffset = true;
                        float fval = (float) Math.clamp((mx - yx) / (double) yw, 0.0, 1.0);
                        ItemSizeModule.yOffset = Math.round((-0.2f + fval * 1.2f) * 100f) / 100f;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 3: Blocks button (Box)
                    int boxX = isX + 14;
                    int boxY = curY + 12;
                    int boxW = halfW - 28;
                    int boxH = 22;
                    if (inside(mx, my, boxX, boxY, boxW, boxH)) {
                        openItemModal();
                        return true;
                    }
                    curY += 12 + 22 + 8;

                    // Configured items (2 side-by-side per row)
                    String itemToRemove = null;
                    java.util.List<String> selList = new java.util.ArrayList<>(ItemSizeModule.selectedItems);
                    int colW = (halfW - 28 - 6) / 2;
                    for (int i = 0; i < selList.size(); i += 2) {
                        for (int col = 0; col < 2; col++) {
                            int idx = i + col;
                            if (idx >= selList.size()) break;
                            int colX = isX + 14 + (col * (colW + 6));
                            int trashX = colX + colW - 16;
                            int trashY = curY + 2;
                            if (inside(mx, my, trashX, trashY, 16, 16)) {
                                itemToRemove = selList.get(idx);
                                break;
                            }
                        }
                        if (itemToRemove != null) break;
                        curY += 24;
                    }

                    if (itemToRemove != null) {
                        ItemSizeModule.selectedItems.remove(itemToRemove);
                        BameClientConfig.save();
                        layout();
                        return true;
                    }

                    // Reset
                    if (inside(mx, my, isX + halfW - 58, curY, 46, 16)) {
                        resetItemSize();
                        return true;
                    }
                }
                rightY += getItemSizeHeight() + 12;
            }

            if (durabilityGuardVisible()) {
                int myY = baseY() + rightY;
                int dgX = cx + halfW + gap;
                if (inside(mx, my, dgX + halfW - 38, myY + 12, 26, 14)) {
                    DurabilityGuardModule.enabled = !DurabilityGuardModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, dgX + 60, myY + 25, 48, 16)) {
                    listeningDurabilityGuard = true;
                    return true;
                }
                if (inside(mx, my, dgX, myY, halfW, 46)) {
                    DurabilityGuardModule.expanded = !DurabilityGuardModule.expanded;
                    layout();
                    return true;
                }
                if (DurabilityGuardModule.expanded) {
                    int curY = myY + 54;
                    // Row 1: Alert Type (Title / ActionBar / Both)
                    if (inside(mx, my, dgX + halfW - 74, curY + 2, 60, 16)) {
                        DurabilityGuardModule.alertMode = (DurabilityGuardModule.alertMode + 1) % 3;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    // Row 2: Tools button
                    int boxX = dgX + 14;
                    int boxY = curY + 12;
                    int boxW = halfW - 28;
                    int boxH = 22;
                    if (inside(mx, my, boxX, boxY, boxW, boxH)) {
                        openToolModal();
                        return true;
                    }
                    curY += 12 + 22 + 8;

                    // Row 3: Configured tools list
                    String toolToRemove = null;
                    for (java.util.Map.Entry<String, Integer> entry : DurabilityGuardModule.toolThresholds.entrySet()) {
                        String toolId = entry.getKey();

                        // Check text field click
                        CustomTextFieldWidget field = toolThresholdFields.get(toolId);
                        if (field != null && field.mouseClicked(click, twice)) {
                            unfocus();
                            field.setFocused(true);
                            setFocused(field);
                            return true;
                        }

                        // Trash button click
                        if (inside(mx, my, dgX + halfW - 32, curY + 3, 16, 16)) {
                            toolToRemove = toolId;
                            break;
                        }

                        curY += 24;
                    }

                    if (toolToRemove != null) {
                        DurabilityGuardModule.toolThresholds.remove(toolToRemove);
                        toolThresholdFields.remove(toolToRemove);
                        BameClientConfig.save();
                        layout();
                        return true;
                    }

                    // Row 4: Reset
                    if (inside(mx, my, dgX + halfW - 58, curY, 46, 16)) {
                        resetDurabilityGuard();
                        return true;
                    }
                }
                rightY += getDurabilityGuardHeight() + 12;
            }
        }
        return false;
    }

    private void resetScoreboard() {
        ScoreboardModule.hudX = -1;
        ScoreboardModule.hudY = -1;
        ScoreboardModule.scale = 1.0f;
        ScoreboardModule.customWidth = -1;
        ScoreboardModule.customHeight = -1;
        ScoreboardModule.bgMode = 0;
        ScoreboardModule.outlineColor = 0xFFFFFFFF;
        BameClientConfig.save();
        layout();
    }

    private void resetSpotifyHud() {
        SpotifyHudModule.hudX = 20;
        SpotifyHudModule.hudY = 20;
        SpotifyHudModule.scale = 1.0f;
        SpotifyHudModule.customWidth = -1;
        SpotifyHudModule.customHeight = -1;
        SpotifyHudModule.autoHide = false;
        SpotifyHudModule.bgMode = 0;
        SpotifyHudModule.outlineColor = 0xFFFFFFFF;
        BameClientConfig.save();
        layout();
    }

    private void resetCustomCrosshair() {
        CustomCrosshairModule.resetToDefault();
        BameClientConfig.save();
        layout();
    }

    private void resetInvMove() {
        InvMoveModule.resetToDefault();
        BameClientConfig.save();
        layout();
    }

    private void resetAutoClicker() {
        AutoClickerModule.resetToDefault();
        BameClientConfig.save();
        layout();
    }

    private void resetHitColor() {
        HitColorModule.resetToDefault();
        BameClientConfig.save();
        layout();
    }

    private void resetReachDisplay() {
        ReachDisplayModule.resetToDefault();
        BameClientConfig.save();
        layout();
    }

    private void resetLowShield() {
        LowShieldModule.resetToDefault();
        BameClientConfig.save();
        layout();
    }

    private void resetCustomHitboxes() {
        CustomHitboxesModule.resetToDefault();
        BameClientConfig.save();
        layout();
    }

    private void resetNoFog() {
        NoFogModule.resetToDefault();
        BameClientConfig.save();
        layout();
    }

    private void resetAutoTool() {
        AutoToolModule.resetToDefault();
        BameClientConfig.save();
        layout();
    }

    private void resetBlockOutline() {
        BlockOutlineModule.resetToDefault();
        BameClientConfig.save();
        layout();
    }

    private void resetFreelook() {
        FreelookModule.resetToDefault();
        BameClientConfig.save();
        layout();
    }

    private void resetItemSize() {
        ItemSizeModule.resetToDefault();
        BameClientConfig.save();
        layout();
    }

    private void resetDurabilityGuard() {
        DurabilityGuardModule.resetToDefault();
        toolThresholdFields.clear();
        BameClientConfig.save();
        layout();
    }

    private void resetShowHud() {
        ClockModule.enabled = false;
        CoordinatesModule.enabled = false;
        CoordinatesModule.expanded = false;
        CoordinatesModule.layoutMode = 0;
        CoordinatesModule.style = 0;
        CoordinatesModule.showX = true;
        CoordinatesModule.showY = true;
        CoordinatesModule.showZ = true;
        CoordinatesModule.decimals = false;
        CoordinatesModule.showNether = false;
        CoordinatesModule.showDirection = false;
        PotionsModule.enabled = false;
        TargetHudModule.enabled = false;
        TargetHudModule.expanded = false;
        TargetHudModule.showHearts = true;
        TargetHudModule.showArmor = false;
        TargetHudModule.playersOnly = false;
        KeyStrokesModule.enabled = false;
        CpsModule.enabled = false;
        FpsModule.enabled = false;
        PingModule.enabled = false;
        ServerInfoModule.enabled = false;
        ServerInfoModule.showName = true;
        ServerInfoModule.showServer = true;
        ServerInfoModule.showTime = true;
        ServerInfoModule.dockedElements = new java.util.ArrayList<>(java.util.Arrays.asList("name", "server", "time"));
        ServerInfoModule.nameX = 10; ServerInfoModule.nameY = 10;
        ServerInfoModule.serverX = 10; ServerInfoModule.serverY = 32;
        ServerInfoModule.timeX = 10; ServerInfoModule.timeY = 54;
        ArmorHudModule.enabled = false;

        FpsModule.hudX = 10; FpsModule.hudY = 10; FpsModule.scale = 1.0f; FpsModule.bgMode = 0; FpsModule.outlineColor = 0xFFFFFFFF;
        FpsModule.customWidth = -1; FpsModule.customHeight = -1;
        PingModule.hudX = 10; PingModule.hudY = 34; PingModule.scale = 1.0f; PingModule.bgMode = 0; PingModule.outlineColor = 0xFFFFFFFF;
        PingModule.customWidth = -1; PingModule.customHeight = -1;
        CpsModule.hudX = 10; CpsModule.hudY = 58; CpsModule.scale = 1.0f; CpsModule.bgMode = 0; CpsModule.outlineColor = 0xFFFFFFFF;
        CpsModule.customWidth = -1; CpsModule.customHeight = -1;
        ClockModule.hudX = 10; ClockModule.hudY = 82; ClockModule.scale = 1.0f; ClockModule.bgMode = 0; ClockModule.outlineColor = 0xFFFFFFFF;
        ClockModule.customWidth = -1; ClockModule.customHeight = -1;
        CoordinatesModule.hudX = 10; CoordinatesModule.hudY = 106; CoordinatesModule.scale = 1.0f; CoordinatesModule.bgMode = 0; CoordinatesModule.outlineColor = 0xFFFFFFFF;
        CoordinatesModule.customWidth = -1; CoordinatesModule.customHeight = -1;
        PotionsModule.hudX = 10; PotionsModule.hudY = 130; PotionsModule.scale = 1.0f; PotionsModule.bgMode = 0; PotionsModule.outlineColor = 0xFFFFFFFF;
        PotionsModule.customWidth = -1; PotionsModule.customHeight = -1;
        ServerInfoModule.hudX = -1; ServerInfoModule.hudY = 10; ServerInfoModule.scale = 1.0f; ServerInfoModule.bgMode = 0; ServerInfoModule.outlineColor = 0xFFFFFFFF;
        ServerInfoModule.customWidth = -1; ServerInfoModule.customHeight = -1;
        KeyStrokesModule.hudX = 10; KeyStrokesModule.hudY = 160; KeyStrokesModule.scale = 1.0f; KeyStrokesModule.bgMode = 0; KeyStrokesModule.outlineColor = 0xFFFFFFFF;
        TargetHudModule.hudX = -1; TargetHudModule.hudY = -1; TargetHudModule.scale = 1.0f; TargetHudModule.bgMode = 0; TargetHudModule.outlineColor = 0xFFFFFFFF;
        TargetHudModule.customWidth = -1; TargetHudModule.customHeight = -1;
        ArmorHudModule.hudX = -1; ArmorHudModule.hudY = -1; ArmorHudModule.scale = 1.0f; ArmorHudModule.bgMode = 0; ArmorHudModule.outlineColor = 0xFFFFFFFF;
        ArmorHudModule.customWidth = -1; ArmorHudModule.customHeight = -1;

        BameClientConfig.save();
        layout();
    }
    private void resetHandPosition() {
        HandPositionModule.resetToDefault();
        BameClientConfig.save();
        layout();
    }
    private void select(String category) { com.bame.client.sound.ClientSoundManager.playClick(); itemModalOpen=false; picker.release(); themeSettings.close(); selected=category; BameClientConfig.save(); scroll=0; listening=false; listeningZoom=false; listeningShowHud=false; listeningFullbright=false; listeningSpotify=false; listeningScoreboard=false; listeningCrosshair=false; listeningInvMove=false; listeningAutoClicker=false; listeningHitColor=false; listeningReachDisplay=false; listeningLowShield=false; listeningHitboxes=false; listeningNoFog=false; listeningAutoTool=false; listeningBlockOutline=false; listeningFreelook=false; listeningItemSize=false; listeningDurabilityGuard=false; listeningTimeChanger=false; listeningHandPosition=false; unfocus(); layout(); }
    private void setCorner(boolean first) {
        if(client.player==null || client.world==null) return;
        BlockPos p = null;
        HitResult hit = client.crosshairTarget;
        if(hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            p = bhr.getBlockPos();
        } else {
            Vec3d eye = client.player.getEyePos();
            Vec3d look = client.player.getRotationVec(1.0f);
            Vec3d end = eye.add(look.multiply(50.0));
            HitResult ray = client.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player));
            if(ray.getType() == HitResult.Type.BLOCK && ray instanceof BlockHitResult bhr) {
                p = bhr.getBlockPos();
            }
        }
        if(p == null) {
            p = client.player.getBlockPos();
        }
        if(first) {
            AutoAreaMinerModule.corner1 = p;
            corner1.setText(format(p));
        } else {
            AutoAreaMinerModule.corner2 = p;
            corner2.setText(format(p));
        }
        BameClientConfig.save();
    }
    private void dragScroll(double my) { scroll=Math.clamp((my-cy-scrollGrab)/Math.max(1,ch-thumbHeight())*maxScroll(),0,maxScroll()); layout(); }
    @Override public boolean mouseDragged(Click click,double dx,double dy) {
        if ((crosshairColorPickerOpen || hitColorColorPickerOpen || hitboxColorPickerOpen || blockOutlineColorPickerOpen) && cpDrag >= 0) {
            updateModalColor(click.x(), click.y());
            return true;
        }
        if (gridDragMode >= 0) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int cX = cx + halfW + gap;
            int rightY = 0;
            if (fullbrightVisible()) rightY += (fullbrightExpanded ? 92 : 46) + 12;
            if (zoomVisible()) rightY += getZoomHeight() + 12;
            if (spotifyHudVisible()) rightY += getSpotifyHudHeight() + 12;
            int myY = baseY() + rightY;
            int gridX = cX + 12;
            int gridY = myY + 52;
            int col = (int)((click.x() - gridX) / 7.0);
            int row = (int)((click.y() - gridY) / 7.0);
            if (col >= 0 && col < 15 && row >= 0 && row < 15) {
                if (col != lastEditedCol || row != lastEditedRow) {
                    CustomCrosshairModule.grid[row][col] = (gridDragMode == 1);
                    lastEditedCol = col;
                    lastEditedRow = row;
                }
            }
            return true;
        }
        if(themeSettings.dragging()) { themeSettings.drag(click.x(),click.y()); return true; }
        if(scrollDragging) { dragScroll(click.y()); return true; }
        if(draggingFullbright) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int sx = cx + halfW + gap + 12; int sw = halfW-24;
            float fval = (float)Math.clamp(((click.x() - sx) / (double)sw), 0.0, 1.0);
            com.bame.client.module.FullbrightModule.intensity = fval;
            return true;
        }
        if(draggingCps) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int sx = cx + 72;
            int sw = halfW - 86;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            AutoClickerModule.cps = Math.max(1, Math.round(1 + fval * 19));
            return true;
        }
        if(draggingAutoClickerDelay) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int sx = cx + 88;
            int sw = halfW - 102;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            AutoClickerModule.delaySeconds = Math.max(0.1f, Math.round((0.1f + fval * 4.9f) * 10f) / 10f);
            return true;
        }
        if(draggingHitColorAlpha) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int sx = cx + halfW + gap + 88;
            int sw = halfW - 102;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            HitColorModule.alpha = 0.1f + fval * 0.9f;
            HitColorModule.apply();
            return true;
        }
        if(draggingLowShieldHeight) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int sx = cx + halfW + gap + 84;
            int sw = halfW - 98;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            LowShieldModule.heightPercent = Math.max(1, Math.round(1 + fval * 99));
            return true;
        }
        if(draggingLowShieldTotem) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int sx = cx + halfW + gap + 84;
            int sw = halfW - 98;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            LowShieldModule.totemSizePercent = Math.max(10, Math.round(10 + fval * 90));
            return true;
        }
        if(draggingHitboxAlpha) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int sx = cx + halfW + gap + 72;
            int sw = halfW - 86;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            CustomHitboxesModule.fillOpacity = Math.round(fval * 100f) / 100f;
            return true;
        }
        if(draggingHitboxWidth) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int wx = cx + halfW + gap + 82;
            int ww = halfW - 96;
            float fval = (float) Math.clamp(((click.x() - wx) / (double) ww), 0.0, 1.0);
            CustomHitboxesModule.lineWidth = Math.max(1.0f, Math.round((1.0f + fval * 4.0f) * 10f) / 10f);
            return true;
        }
        if(draggingWidth) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int sx = cx + 12;
            int sw = halfW - 24;
            float val = (float)Math.clamp(((click.x() - sx) / (double)sw), 0.0, 1.0);
            BameClientConfig.outlineWidth = 1.0f + val * 4.0f;
            return true;
        }
        if (draggingBlockOutlineWidth) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int wx = cx + 82;
            int ww = halfW - 96;
            float fval = (float) Math.clamp(((click.x() - wx) / (double) ww), 0.0, 1.0);
            BlockOutlineModule.lineWidth = Math.max(1.0f, Math.round((1.0f + fval * 5.0f) * 10f) / 10f);
            return true;
        }
        if (draggingBlockOutlineOpacity) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int ox = cx + 90;
            int ow = halfW - 104;
            float fval = (float) Math.clamp(((click.x() - ox) / (double) ow), 0.0, 1.0);
            BlockOutlineModule.opacity = Math.max(0.2f, Math.round((0.2f + fval * 0.8f) * 100f) / 100f);
            return true;
        }
        if (draggingFreelookSensitivity) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int sx = cx + halfW + gap + 84;
            int sw = halfW - 98;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            FreelookModule.sensitivity = Math.round((0.5f + fval * 1.5f) * 100f) / 100f;
            return true;
        }
        if (itemModalOpen && itemScrollDragging) {
            int modalH = 285;
            int modalY = (height - modalH) / 2;
            int gridY = modalY + 50;
            int gridH = 185;
            if (cachedFilteredItems != null) {
                int totalItems = cachedFilteredItems.size();
                int totalRows = (totalItems + 11) / 12;
                int totalContentH = totalRows * 25;
                int maxScroll = Math.max(0, totalContentH - gridH);
                if (maxScroll > 0) {
                    itemModalScroll = Math.clamp((click.y() - gridY) / (double) gridH * maxScroll, 0, maxScroll);
                }
            }
            return true;
        }
        if (draggingItemScale) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int sx = cx + halfW + gap + 88;
            int sw = halfW - 102;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            ItemSizeModule.scale = Math.round((0.25f + fval * 3.75f) * 100f) / 100f;
            return true;
        }
        if (draggingItemYOffset) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int yx = cx + halfW + gap + 96;
            int yw = halfW - 110;
            float fval = (float) Math.clamp(((click.x() - yx) / (double) yw), 0.0, 1.0);
            ItemSizeModule.yOffset = Math.round((-0.2f + fval * 1.2f) * 100f) / 100f;
            return true;
        }
        if (draggingSoundVolume) {
            int sliderW = 100;
            int sliderX = cx + cw - 14 - sliderW;
            float fval = (float) Math.clamp(((click.x() - sliderX) / (double) sliderW), 0.0, 1.0);
            BameClientConfig.soundVolume = Math.round(fval * 100f) / 100f;
            return true;
        }
        if (draggingHoverVolume) {
            int sliderW = 100;
            int sliderX = cx + cw - 14 - sliderW;
            float fval = (float) Math.clamp(((click.x() - sliderX) / (double) sliderW), 0.0, 1.0);
            BameClientConfig.hoverVolume = Math.round(fval * 100f) / 100f;
            return true;
        }
        if (draggingHandX) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int colGap = 10;
            int colW = (halfW - 24 - colGap) / 2;
            int c1X = cx + 12;
            float fval = (float) Math.clamp(((click.x() - c1X) / (double) colW), 0.0, 1.0);
            HandPositionModule.posX = Math.round((-0.80f + fval * 1.60f) * 100f) / 100f;
            return true;
        }
        if (draggingHandY) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int colGap = 10;
            int colW = (halfW - 24 - colGap) / 2;
            int c1X = cx + 12;
            float fval = (float) Math.clamp(((click.x() - c1X) / (double) colW), 0.0, 1.0);
            HandPositionModule.posY = Math.round((-0.80f + fval * 1.60f) * 100f) / 100f;
            return true;
        }
        if (draggingHandZ) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int colGap = 10;
            int colW = (halfW - 24 - colGap) / 2;
            int c1X = cx + 12;
            float fval = (float) Math.clamp(((click.x() - c1X) / (double) colW), 0.0, 1.0);
            HandPositionModule.posZ = Math.round((-0.80f + fval * 1.60f) * 100f) / 100f;
            return true;
        }
        if (draggingHandScale) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int colGap = 10;
            int colW = (halfW - 24 - colGap) / 2;
            int c1X = cx + 12;
            float fval = (float) Math.clamp(((click.x() - c1X) / (double) colW), 0.0, 1.0);
            HandPositionModule.scale = Math.round((0.30f + fval * 1.20f) * 100f) / 100f;
            return true;
        }
        if (draggingHandPitch) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int colGap = 10;
            int colW = (halfW - 24 - colGap) / 2;
            int c2X = cx + 12 + colW + colGap;
            float fval = (float) Math.clamp(((click.x() - c2X) / (double) colW), 0.0, 1.0);
            HandPositionModule.pitch = Math.round(-180f + fval * 360f);
            return true;
        }
        if (draggingHandYaw) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int colGap = 10;
            int colW = (halfW - 24 - colGap) / 2;
            int c2X = cx + 12 + colW + colGap;
            float fval = (float) Math.clamp(((click.x() - c2X) / (double) colW), 0.0, 1.0);
            HandPositionModule.yaw = Math.round(-180f + fval * 360f);
            return true;
        }
        if (draggingHandRoll) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int colGap = 10;
            int colW = (halfW - 24 - colGap) / 2;
            int c2X = cx + 12 + colW + colGap;
            float fval = (float) Math.clamp(((click.x() - c2X) / (double) colW), 0.0, 1.0);
            HandPositionModule.roll = Math.round(-180f + fval * 360f);
            return true;
        }
        if(picker.dragging()) { picker.update(click.x(),click.y()); return true; }
        return super.mouseDragged(click,dx,dy);
    }
    @Override public boolean mouseReleased(Click click) {
        if (gridDragMode >= 0) {
            gridDragMode = -1;
            lastEditedCol = -1;
            lastEditedRow = -1;
            BameClientConfig.save();
        }
        cpDrag = -1;
        itemScrollDragging = false;
        boolean handled=scrollDragging||picker.dragging()||themeSettings.dragging()||draggingWidth||draggingFullbright||draggingCps||draggingAutoClickerDelay||draggingHitColorAlpha||draggingLowShieldHeight||draggingLowShieldTotem||draggingHitboxAlpha||draggingHitboxWidth||draggingBlockOutlineWidth||draggingBlockOutlineOpacity||draggingFreelookSensitivity||draggingItemScale||draggingItemYOffset||draggingSoundVolume||draggingHoverVolume||draggingHandX||draggingHandY||draggingHandZ||draggingHandScale||draggingHandPitch||draggingHandYaw||draggingHandRoll;
        scrollDragging=false; draggingWidth=false; draggingFullbright=false; draggingCps=false; draggingAutoClickerDelay=false; draggingHitColorAlpha=false; draggingLowShieldHeight=false; draggingLowShieldTotem=false; draggingHitboxAlpha=false; draggingHitboxWidth=false; draggingBlockOutlineWidth=false; draggingBlockOutlineOpacity=false; draggingFreelookSensitivity=false; draggingItemScale=false; draggingItemYOffset=false; draggingSoundVolume=false; draggingHoverVolume=false; draggingHandX=false; draggingHandY=false; draggingHandZ=false; draggingHandScale=false; draggingHandPitch=false; draggingHandYaw=false; draggingHandRoll=false; com.bame.client.BameClientConfig.save(); picker.release(); themeSettings.release();
        return handled||super.mouseReleased(click);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        if (itemModalOpen) {
            int modalW = 350;
            int modalH = 285;
            int modalX = (width - modalW) / 2;
            int modalY = (height - modalH) / 2;
            if (inside(mx, my, modalX, modalY, modalW, modalH)) {
                if (cachedFilteredItems != null) {
                    int totalRows = (cachedFilteredItems.size() + 11) / 12;
                    int totalContentH = totalRows * 25;
                    int maxScroll = Math.max(0, totalContentH - 185);
                    if (maxScroll > 0) {
                        itemModalScroll = Math.clamp(itemModalScroll - vertical * 25, 0, maxScroll);
                    }
                }
                return true;
            }
            return true;
        }
        if(inside(mx,my,cx,cy,cw+14,ch)&&!picker.dragging()&&!themeSettings.dragging()) { scroll=Math.clamp(scroll-vertical*26,0,maxScroll()); layout(); return true; }
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public boolean keyPressed(KeyInput input) {
        for (CustomTextFieldWidget f : toolThresholdFields.values()) {
            if (f.isFocused()) {
                if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
                    f.setFocused(false);
                    setFocused(null);
                    return true;
                }
                if (f.keyPressed(input)) return true;
            }
        }
        if (itemModalOpen) {
            if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                applyModalSave();
                return true;
            }
            if (itemSearchWidget != null && itemSearchWidget.isFocused()) {
                if (itemSearchWidget.keyPressed(input)) return true;
            }
            return true;
        }
        if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && (crosshairColorPickerOpen || hitColorColorPickerOpen || hitboxColorPickerOpen || blockOutlineColorPickerOpen)) {
            crosshairColorPickerOpen = false;
            hitColorColorPickerOpen = false;
            hitboxColorPickerOpen = false;
            blockOutlineColorPickerOpen = false;
            cpDrag = -1;
            return true;
        }
        if(input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && !listening && !listeningFullbright && !listeningMenuBind && !listeningZoom && !listeningShowHud && !listeningSpotify && !listeningScoreboard && !listeningCrosshair && !listeningInvMove && !listeningAutoClicker && !listeningHitColor && !listeningReachDisplay && !listeningLowShield && !listeningHitboxes && !listeningNoFog && !listeningAutoTool && !listeningBlockOutline && !listeningFreelook && !listeningItemSize && !listeningDurabilityGuard && !listeningTimeChanger && !listeningHandPosition) themeSettings.close();
        if(listening) { AutoAreaMinerModule.keyBind=input.key()==GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listening=false; BameClientConfig.save(); return true; }
        if(listeningMenuBind) { com.bame.client.BameClientConfig.menuBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningMenuBind=false; com.bame.client.BameClientConfig.save(); return true; }
        if(listeningFullbright) { com.bame.client.module.FullbrightModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningFullbright=false; BameClientConfig.save(); return true; }
        if(listeningShowHud) { com.bame.client.module.ShowHudModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningShowHud=false; BameClientConfig.save(); return true; }
        if(listeningZoom) { com.bame.client.module.ZoomModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningZoom=false; BameClientConfig.save(); return true; }
        if(listeningSpotify) { com.bame.client.module.SpotifyHudModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningSpotify=false; BameClientConfig.save(); return true; }
        if(listeningScoreboard) { com.bame.client.module.ScoreboardModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningScoreboard=false; BameClientConfig.save(); return true; }
        if(listeningCrosshair) { com.bame.client.module.CustomCrosshairModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningCrosshair=false; BameClientConfig.save(); return true; }
        if(listeningInvMove) { com.bame.client.module.InvMoveModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningInvMove=false; BameClientConfig.save(); return true; }
        if(listeningAutoClicker) { AutoClickerModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningAutoClicker=false; BameClientConfig.save(); return true; }
        if(listeningHitColor) { HitColorModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningHitColor=false; BameClientConfig.save(); return true; }
        if(listeningReachDisplay) { ReachDisplayModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningReachDisplay=false; BameClientConfig.save(); return true; }
        if(listeningLowShield) { LowShieldModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningLowShield=false; BameClientConfig.save(); return true; }
        if(listeningHitboxes) { CustomHitboxesModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningHitboxes=false; BameClientConfig.save(); return true; }
        if(listeningNoFog) { NoFogModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningNoFog=false; BameClientConfig.save(); return true; }
        if(listeningAutoTool) { AutoToolModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningAutoTool=false; BameClientConfig.save(); return true; }
        if(listeningBlockOutline) { BlockOutlineModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningBlockOutline=false; BameClientConfig.save(); return true; }
        if(listeningFreelook) { FreelookModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningFreelook=false; BameClientConfig.save(); return true; }
        if(listeningItemSize) { ItemSizeModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningItemSize=false; BameClientConfig.save(); return true; }
        if(listeningDurabilityGuard) { DurabilityGuardModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningDurabilityGuard=false; BameClientConfig.save(); return true; }
        if(listeningTimeChanger) { TimeChangerModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningTimeChanger=false; BameClientConfig.save(); return true; }
        if(listeningHandPosition) { HandPositionModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningHandPosition=false; BameClientConfig.save(); return true; }
        if (isSecretComboPressed(input)) {
            com.bame.client.BameClientConfig.secretUnlocked = !com.bame.client.BameClientConfig.secretUnlocked;
            com.bame.client.sound.ClientSoundManager.playClick();
            return true;
        }
        return super.keyPressed(input);
    }
    @Override public boolean charTyped(CharInput input) {
        if (itemModalOpen && itemSearchWidget != null && itemSearchWidget.isFocused()) {
            return itemSearchWidget.charTyped(input);
        }
        for (CustomTextFieldWidget f : toolThresholdFields.values()) {
            if (f.isFocused()) {
                char ch = (char) input.codepoint();
                if (Character.isDigit(ch) && f.getText().length() < 5) {
                    return f.charTyped(input);
                }
                return true;
            }
        }
        return super.charTyped(input);
    }
    @Override public void removed() { picker.release(); themeSettings.close(); BameClientConfig.save(); super.removed(); }
    @Override public boolean shouldPause() { return false; }

    @Override public void close() {
        super.close();
        openTime = 0;
        BameClientConfig.save();
    }

}
