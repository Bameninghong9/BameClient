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
import com.bame.client.module.FakeScoreboardModule;
import com.bame.client.module.ClockModule;
import com.bame.client.module.CoordinatesModule;
import com.bame.client.module.PotionsModule;
import com.bame.client.module.TargetHudModule;
import com.bame.client.module.ArmorHudModule;
import com.bame.client.module.SpotifyHudModule;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

public class BameClientScreen extends Screen {
    private static final String[] CATEGORIES={"Combat","Movement","Visuals","Misc","World"};
    public static String selected="World";
    private boolean expanded,listening,scrollDragging,draggingWidth,fullbrightExpanded,draggingFullbright,listeningFullbright,listeningMenuBind,listeningZoom,listeningShowHud,listeningFakeScoreboard,listeningSpotify;
    private double scroll,scrollGrab;
    private int px,py,pw,ph,sidebar,cx,cy,cw,ch;
    private CustomTextFieldWidget search,corner1,corner2,nameProtectAliasField,fakeMoneyField,fakeStarsField,fakeKillsField,fakeDeathsField,fakeTimeField;
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

        int gap = 16;
        int halfW = (cw - gap) / 2;
        nameProtectAliasField = new CustomTextFieldWidget(0, 0, halfW - 65, 20, Text.literal("Alias"));
        nameProtectAliasField.setText(NameProtectModule.alias != null ? NameProtectModule.alias : "You");
        nameProtectAliasField.setPlaceholder("Alias (e.g. You)");
        nameProtectAliasField.setChangedListener(s -> {
            NameProtectModule.alias = s;
            BameClientConfig.save();
        });
        addSelectableChild(nameProtectAliasField);

        int labelW = 44;
        int fieldW = halfW - labelW - 24;
        fakeMoneyField = new CustomTextFieldWidget(0, 0, fieldW, 18, Text.literal("Money"));
        fakeMoneyField.setText(FakeScoreboardModule.money != null ? FakeScoreboardModule.money : "670T");
        fakeMoneyField.setPlaceholder("e.g. 670T");
        fakeMoneyField.setChangedListener(s -> { FakeScoreboardModule.money = s; BameClientConfig.save(); });
        addSelectableChild(fakeMoneyField);

        fakeStarsField = new CustomTextFieldWidget(0, 0, fieldW, 18, Text.literal("Stars"));
        fakeStarsField.setText(FakeScoreboardModule.stars != null ? FakeScoreboardModule.stars : "2.5K");
        fakeStarsField.setPlaceholder("e.g. 2.5K");
        fakeStarsField.setChangedListener(s -> { FakeScoreboardModule.stars = s; BameClientConfig.save(); });
        addSelectableChild(fakeStarsField);

        fakeKillsField = new CustomTextFieldWidget(0, 0, fieldW, 18, Text.literal("Kills"));
        fakeKillsField.setText(FakeScoreboardModule.kills != null ? FakeScoreboardModule.kills : "5283");
        fakeKillsField.setPlaceholder("e.g. 5283");
        fakeKillsField.setChangedListener(s -> { FakeScoreboardModule.kills = s; BameClientConfig.save(); });
        addSelectableChild(fakeKillsField);

        fakeDeathsField = new CustomTextFieldWidget(0, 0, fieldW, 18, Text.literal("Deaths"));
        fakeDeathsField.setText(FakeScoreboardModule.deaths != null ? FakeScoreboardModule.deaths : "2983");
        fakeDeathsField.setPlaceholder("e.g. 2983");
        fakeDeathsField.setChangedListener(s -> { FakeScoreboardModule.deaths = s; BameClientConfig.save(); });
        addSelectableChild(fakeDeathsField);

        fakeTimeField = new CustomTextFieldWidget(0, 0, fieldW, 18, Text.literal("Time"));
        fakeTimeField.setText(FakeScoreboardModule.playtime != null ? FakeScoreboardModule.playtime : "10d 22h");
        fakeTimeField.setPlaceholder("e.g. 10d 22h");
        fakeTimeField.setChangedListener(s -> { FakeScoreboardModule.playtime = s; BameClientConfig.save(); });
        addSelectableChild(fakeTimeField);

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
    private boolean fakeScoreboardVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "fake scoreboard".contains(q) || "scoreboard".contains(q);
        return selected.equals("Visuals");
    }
    private boolean spotifyHudVisible() {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return "spotify hud".contains(q) || "spotify".contains(q) || "music".contains(q) || "song".contains(q);
        return selected.equals("Visuals");
    }

    private int columns() { return 4; }
    private int effectsY() { return 44+((GuiTheme.PRESETS.length+columns()-1)/columns())*58+18; }
    private int settingsY() { return effectsY(); }

    private int getShowHudHeight() {
        if (!ShowHudModule.expanded) return 46;
        int extra = (ServerInfoModule.enabled ? 24 : 0) + (NameProtectModule.enabled ? 26 : 0) + (TargetHudModule.expanded ? 3 * 26 : 0) + 28 + 10;
        return 46 + 8 + 11 * 26 + extra;
    }
    private int getFakeScoreboardHeight() {
        if (!FakeScoreboardModule.expanded) return 46;
        return 46 + 8 + 5 * 24 + 8;
    }

    private int contentHeight() { 
        if (selected.equals("Theme")) return settingsY()+themeSettings.height()+8;
        if (selected.equals("Settings")) return 104;
        int leftY = 0;
        if (minerVisible()) leftY += (expanded?346:46) + 12;
        int rightY = leftY;

        if (showHudVisible()) leftY += getShowHudHeight() + 12;
        if (fakeScoreboardVisible()) leftY += getFakeScoreboardHeight() + 12;

        if (fullbrightVisible()) rightY += (fullbrightExpanded?92:46) + 12;
        if (zoomVisible()) rightY += (ZoomModule.expanded?92:46) + 12;
        if (spotifyHudVisible()) rightY += (SpotifyHudModule.expanded?84:46) + 12;

        return Math.max(leftY, rightY);
    }
    private double maxScroll() { return Math.max(0,contentHeight()-ch); }
    private int baseY() { return cy-(int)scroll; }
    private void layout() {
        scroll=Math.clamp(scroll,0,maxScroll());
        int yOffset = 0;
        int leftY = baseY();
        if(minerVisible()) {
            corner1.setX(cx+12); corner1.setY(leftY+64);
            corner2.setX(cx+12); corner2.setY(leftY+104);
            corner1.visible=corner2.visible=expanded;
            corner1.active=corner1.visible && corner1.getY()+22>cy && corner1.getY()<cy+ch;
            corner2.active=corner2.visible && corner2.getY()+22>cy && corner2.getY()<cy+ch;
            if(!corner1.active) corner1.setFocused(false);
            if(!corner2.active) corner2.setFocused(false);
            if(!corner1.visible) { corner1.setFocused(false); corner2.setFocused(false); }
            picker.layout(cx+14,leftY+228,140);
            leftY += (expanded?346:46) + 12;
        } else {
            corner1.visible=corner2.visible=false;
            corner1.active=corner2.active=false;
            corner1.setFocused(false); corner2.setFocused(false);
        }

        if (nameProtectAliasField != null) {
            if (showHudVisible() && ShowHudModule.expanded && NameProtectModule.enabled) {
                nameProtectAliasField.visible = true;
            } else {
                nameProtectAliasField.visible = false;
                nameProtectAliasField.active = false;
                nameProtectAliasField.setFocused(false);
            }
        }
        if (showHudVisible()) leftY += getShowHudHeight() + 12;

        if (fakeMoneyField != null) {
            if (fakeScoreboardVisible() && FakeScoreboardModule.expanded) {
                int gap = 16;
                int halfW = (cw - gap) / 2;
                int startY = leftY + 54;
                int lW = 44;
                int fieldX = cx + 14 + lW;
                int fW = halfW - lW - 24;

                fakeMoneyField.setX(fieldX);
                fakeMoneyField.setY(startY);
                fakeMoneyField.setWidth(fW);
                fakeMoneyField.visible = true;
                fakeMoneyField.active = fakeMoneyField.getY() + 18 > cy && fakeMoneyField.getY() < cy + ch;
                if (!fakeMoneyField.active) fakeMoneyField.setFocused(false);

                fakeStarsField.setX(fieldX);
                fakeStarsField.setY(startY + 24);
                fakeStarsField.setWidth(fW);
                fakeStarsField.visible = true;
                fakeStarsField.active = fakeStarsField.getY() + 18 > cy && fakeStarsField.getY() < cy + ch;
                if (!fakeStarsField.active) fakeStarsField.setFocused(false);

                fakeKillsField.setX(fieldX);
                fakeKillsField.setY(startY + 48);
                fakeKillsField.setWidth(fW);
                fakeKillsField.visible = true;
                fakeKillsField.active = fakeKillsField.getY() + 18 > cy && fakeKillsField.getY() < cy + ch;
                if (!fakeKillsField.active) fakeKillsField.setFocused(false);

                fakeDeathsField.setX(fieldX);
                fakeDeathsField.setY(startY + 72);
                fakeDeathsField.setWidth(fW);
                fakeDeathsField.visible = true;
                fakeDeathsField.active = fakeDeathsField.getY() + 18 > cy && fakeDeathsField.getY() < cy + ch;
                if (!fakeDeathsField.active) fakeDeathsField.setFocused(false);

                fakeTimeField.setX(fieldX);
                fakeTimeField.setY(startY + 96);
                fakeTimeField.setWidth(fW);
                fakeTimeField.visible = true;
                fakeTimeField.active = fakeTimeField.getY() + 18 > cy && fakeTimeField.getY() < cy + ch;
                if (!fakeTimeField.active) fakeTimeField.setFocused(false);
            } else {
                fakeMoneyField.visible = fakeMoneyField.active = false; fakeMoneyField.setFocused(false);
                fakeStarsField.visible = fakeStarsField.active = false; fakeStarsField.setFocused(false);
                fakeKillsField.visible = fakeKillsField.active = false; fakeKillsField.setFocused(false);
                fakeDeathsField.visible = fakeDeathsField.active = false; fakeDeathsField.setFocused(false);
                fakeTimeField.visible = fakeTimeField.active = false; fakeTimeField.setFocused(false);
            }
        }
        if (fakeScoreboardVisible()) leftY += getFakeScoreboardHeight() + 12;

        themeSettings.layout(cx,baseY()+settingsY(),cw);
    }
    private static String format(BlockPos p) { return p==null?"":p.getX()+" "+p.getY()+" "+p.getZ(); }
    private static BlockPos parse(String s) {
        try { var a=s.trim().split("\\s+"); if(a.length==3) return new BlockPos(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2])); }
        catch(NumberFormatException ignored) {} return null;
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
        return a == 0 ? "Fade In" : (a == 1 ? "Slide In" : "None");
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
        
        if (progress < 1.0f && anim != 2) {
            c.getMatrices().pushMatrix();
            if (anim == 0) {
                c.getMatrices().translate((float)width/2f, (float)height/2f);
                float s = 0.6f + 0.4f * p;
                c.getMatrices().scale(s, s);
                c.getMatrices().translate(-(float)width/2f, -(float)height/2f);
                
            } else if (anim == 1) {
                c.getMatrices().translate(0f, (float)height * (1.0f - p));
            }
        }

        int bg=GuiTheme.alpha(GuiTheme.current().background(),BameClientConfig.seeThrough?205:255);
        box(c,px,py,pw,ph,bg);
        CustomGuiUtils.drawUltraRoundedOutline(c,px,py,pw,ph,0xFF292D36);
        ambient.render(c,px+sidebar+1,py+1,pw-sidebar-9,ph-9);
        c.fill(px+sidebar,py+12,px+sidebar+1,py+ph-12,0xFF292D36);
        CustomGuiUtils.drawCLogo(c, px + 14, py + 15, 20);
        text(c,"CAESER",px+40,py+21,0xFFFFFFFF);
        text(c,"MODULES",px+14,py+56,0xFF7F8694);
        int step=Math.min(26,Math.max(17,(ph-165)/6));
        for(int i=0;i<CATEGORIES.length;i++) {
            int y=py+73+i*step; boolean active=selected.equals(CATEGORIES[i]);
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
        if(selected.equals("Theme")) box(c,px+6,gy+16,sidebar-12,23,GuiTheme.alpha(GuiTheme.accent(),40));
        int tc=selected.equals("Theme")?GuiTheme.accent():0xFFABB1BE;
        CustomGuiUtils.drawSmoothRing(c,px+21,gy+27,5,1,tc);
        for(int i=0;i<3;i++) box(c,px+18+i*3,gy+24,2,2,tc);
        text(c,"Theme",px+34,gy+24,tc);
        
        if(selected.equals("Settings")) box(c,px+6,gy+39,sidebar-12,23,GuiTheme.alpha(GuiTheme.accent(),40));
        int sc=selected.equals("Settings")?GuiTheme.accent():0xFFABB1BE;
        CustomGuiUtils.drawGearIcon(c,px+15,gy+44,sc);
        text(c,"Settings",px+34,gy+47,sc);
        int profileY=py+ph-40;
        box(c,px+8,profileY,sidebar-16,30,0xFF14181F);
        if(client.player!=null) {
            net.minecraft.client.gui.PlayerSkinDrawer.draw(c,client.player.getSkin(),px+13,profileY+5,20);
            c.enableScissor(px+38,profileY,px+sidebar-12,profileY+30);
            String pName = client.player.getName().getString();
            if (NameProtectModule.enabled) pName = NameProtectModule.getProtectedName(pName);
            text(c,pName,px+38,profileY+11,0xFFD4D8E0); c.disableScissor();
        }
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
            int leftY = 0;
            if (minerVisible()) {
                renderMiner(c,mx,my,delta,leftY);
                leftY += (expanded?346:46) + 12;
            }
            int rightY = leftY;
            int gap = 16;
            int halfW = (cw - gap) / 2;
            if (showHudVisible()) {
                renderShowHudModule(c, mx, my, delta, cx, leftY, halfW);
                leftY += getShowHudHeight() + 12;
            }
            if (fakeScoreboardVisible()) {
                renderFakeScoreboardModule(c, mx, my, delta, cx, leftY, halfW);
                leftY += getFakeScoreboardHeight() + 12;
            }
            if (fullbrightVisible()) {
                renderFullbright(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += (fullbrightExpanded?92:46) + 12;
            }
            if (zoomVisible()) {
                renderZoom(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += (ZoomModule.expanded?92:46) + 12;
            }
            if (spotifyHudVisible()) {
                renderSpotifyHudModule(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += (SpotifyHudModule.expanded?84:46) + 12;
            }
        }
        c.disableScissor();
        if(maxScroll()>0) {
            box(c,px+pw-10,cy,4,ch,0xFF272C35);
            box(c,px+pw-10,thumbY(),4,thumbHeight(),scrollDragging?0xFFFFFFFF:GuiTheme.accent());
        }
        
        if (progress < 1.0f && anim != 2) {
            c.getMatrices().popMatrix();
            
        }
    }
    private void renderMiner(DrawContext c,int mx,int my,float delta, int yOffset) {
        int y=baseY() + yOffset;
        box(c,cx,y,cw,expanded?346:46,GuiTheme.alpha(GuiTheme.surface(),BameClientConfig.seeThrough?210:255));
        text(c,"Auto Area Miner",cx+12,y+12,0xFFE2E5ED);
        text(c,"KeyBind:",cx+12,y+29,0xFF8E95A4);
        button(c,keyName(),cx+60,y+25,48,16,mx,my);
        toggle(c,cx+cw-38,y+12,AutoAreaMinerModule.enabled,mx,my,delta);
        if(!expanded) return;
        c.fill(cx+8,y+46,cx+cw-8,y+47,0xFF292D36);
        text(c,"Corner 1",cx+12,y+52,0xFFABB1BE); corner1.render(c,mx,my,delta);
        text(c,"Corner 2",cx+12,y+92,0xFFABB1BE); corner2.render(c,mx,my,delta);
        text(c,"Corner 1 - Looking",cx+12,y+138,0xFFABB1BE);
        text(c,"Corner 2 - Looking",cx+12,y+162,0xFFABB1BE);
        button(c,"Set",cx+cw-50,y+132,38,20,mx,my);
        button(c,"Set",cx+cw-50,y+156,38,20,mx,my);
        button(c,AutoAreaMinerModule.mode3x3?"Mode: 3x3 Pickaxe":"Mode: Normal Pickaxe",cx+12,y+184,cw-24,22,mx,my);
        text(c,"Outline Color",cx+14,y+214,0xFFD4D8E0);
        int swatchX=cx+cw-30;
        box(c,swatchX,y+211,16,12,0xFF777777);
        box(c,swatchX,y+211,16,12,BameClientConfig.outlineColor);
        text(c,"Style",cx+170,y+214,0xFFD4D8E0);
        int mode = BameClientConfig.renderMode;
        modeButton(c,"Clean",cx+170,y+228,45,18,mx,my,mode==0);
        modeButton(c,"Outline",cx+220,y+228,55,18,mx,my,mode==1);
        modeButton(c,"Corners",cx+170,y+250,55,18,mx,my,mode==2);
        modeButton(c,"Pulse",cx+230,y+250,45,18,mx,my,mode==3);
        
        picker.render(c);

        int sliderY = y+304;
        text(c,"Outline Width",cx+14,sliderY,0xFFD4D8E0);
        int sw = 140; // width of slider
        CustomGuiUtils.fillUltraRounded(c,cx+14,sliderY+16,sw,4,0xFF303442,2);
        float widthVal = (BameClientConfig.outlineWidth - 1.0f) / 4.0f; // 0 to 1
        int fill = Math.round(sw * widthVal);
        if(fill>0) CustomGuiUtils.fillUltraRounded(c,cx+14,sliderY+16,fill,4,GuiTheme.accent(),2);
        CustomGuiUtils.fillUltraRounded(c,cx+14+fill-3,sliderY+14,7,8,0xFFFFFFFF,4);
        text(c,String.format("%.1f", BameClientConfig.outlineWidth),cx+14+sw+8,sliderY+14,0xFFD4D8E0);
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
        text(c, "Show HUD", x + 12, y + 10, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);
        String kb = listeningShowHud ? "..." : formatKey(ShowHudModule.keyBind);
        button(c, kb, x + 58, y + 25, 42, 16, mx, my);
        button(c, "Edit HUD", x + w - 62, y + 25, 50, 16, mx, my);
        toggle(c, x + w - 50, y + 7, ShowHudModule.enabled, mx, my, delta);

        if (!ShowHudModule.expanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;
        // 1. Clock
        text(c, "Clock", x + 14, curY + 5, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 3, ClockModule.enabled, mx, my, delta);
        curY += 26;

        // 2. Coordinates
        text(c, "Coordinates", x + 14, curY + 5, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 3, CoordinatesModule.enabled, mx, my, delta);
        curY += 26;

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

        // 12. Name Protect
        text(c, "Name Protect", x + 14, curY + 5, 0xFFD4D8E0);
        toggle(c, x + w - 38, curY + 3, NameProtectModule.enabled, mx, my, delta);
        curY += 26;
        if (NameProtectModule.enabled) {
            text(c, "Alias:", x + 14, curY + 5, 0xFF8E95A4);
            if (nameProtectAliasField != null) {
                nameProtectAliasField.setX(x + 56);
                nameProtectAliasField.setY(curY + 2);
                nameProtectAliasField.setWidth(w - 68);
                nameProtectAliasField.visible = true;
                nameProtectAliasField.active = curY + 20 > cy && curY < cy + ch;
                nameProtectAliasField.render(c, mx, my, delta);
            }
            curY += 26;
        } else if (nameProtectAliasField != null) {
            nameProtectAliasField.visible = false;
            nameProtectAliasField.active = false;
        }

        // Reset button
        curY += 4;
        button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderFakeScoreboardModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getFakeScoreboardHeight();
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "Fake Scoreboard", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);
        String kb = listeningFakeScoreboard ? "..." : formatKey(FakeScoreboardModule.keyBind);
        button(c, kb, x + 58, y + 25, 42, 16, mx, my);
        toggle(c, x + w - 38, y + 12, FakeScoreboardModule.enabled, mx, my, delta);

        if (!FakeScoreboardModule.expanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        int curY = y + 54;
        text(c, "Money:", x + 14, curY + 4, 0xFFD4D8E0);
        if (fakeMoneyField != null) fakeMoneyField.render(c, mx, my, delta);
        curY += 24;

        text(c, "Stars:", x + 14, curY + 4, 0xFFD4D8E0);
        if (fakeStarsField != null) fakeStarsField.render(c, mx, my, delta);
        curY += 24;

        text(c, "Kills:", x + 14, curY + 4, 0xFFD4D8E0);
        if (fakeKillsField != null) fakeKillsField.render(c, mx, my, delta);
        curY += 24;

        text(c, "Deaths:", x + 14, curY + 4, 0xFFD4D8E0);
        if (fakeDeathsField != null) fakeDeathsField.render(c, mx, my, delta);
        curY += 24;

        text(c, "Time:", x + 14, curY + 4, 0xFFD4D8E0);
        if (fakeTimeField != null) fakeTimeField.render(c, mx, my, delta);
    }

    private void renderZoom(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        box(c, x, y, w, ZoomModule.expanded ? 92 : 46, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "Zoom", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);
        
        String kb = listeningZoom ? "..." : formatKey(ZoomModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        
        toggle(c, x + w - 38, y + 12, ZoomModule.enabled, mx, my, delta);
        if (!ZoomModule.expanded) return;
        
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);
        button(c, ZoomModule.mode == 0 ? "Smooth" : "Instant", x + 12, y + 58, 60, 20, mx, my);
    }

    private void renderSpotifyHudModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = SpotifyHudModule.expanded ? 84 : 46;
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "Spotify HUD", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);

        String kb = listeningSpotify ? "..." : formatKey(SpotifyHudModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        button(c, "Edit HUD", x + w - 62, y + 25, 50, 16, mx, my);
        toggle(c, x + w - 38, y + 12, SpotifyHudModule.enabled, mx, my, delta);

        if (!SpotifyHudModule.expanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);

        text(c, "Auto-Hide (wenn pausiert)", x + 14, y + 58, 0xFFD4D8E0);
        toggle(c, x + w - 38, y + 56, SpotifyHudModule.autoHide, mx, my, delta);
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
        text(c,"INTERFACE",cx+12,iy+12,0xFFB5BCC9);
        text(c,"Menu Bind",cx+12,iy+34,0xFFD4D8E0);
        button(c,keyNameMenuBind(),cx+cw-72,iy+30,60,16,mx,my);
        text(c,"GUI Animation",cx+12,iy+58,0xFFD4D8E0);
        button(c,animName(),cx+cw-72,iy+54,60,16,mx,my);
    }
    private int thumbHeight() { return Math.max(24,(int)(ch*(ch/(double)Math.max(ch,contentHeight())))); }
    private int thumbY() { return cy+(int)((ch-thumbHeight())*(scroll/Math.max(1,maxScroll()))); }
    private void unfocus() {
        search.setFocused(false); corner1.setFocused(false); corner2.setFocused(false);
        if (nameProtectAliasField != null) nameProtectAliasField.setFocused(false);
        if (fakeMoneyField != null) fakeMoneyField.setFocused(false);
        if (fakeStarsField != null) fakeStarsField.setFocused(false);
        if (fakeKillsField != null) fakeKillsField.setFocused(false);
        if (fakeDeathsField != null) fakeDeathsField.setFocused(false);
        if (fakeTimeField != null) fakeTimeField.setFocused(false);
        setFocused(null);
    }
    @Override public boolean mouseClicked(Click click,boolean twice) {
        layout(); double mx=click.x(),my=click.y();
        if(click.button()!=0) return false;
        picker.release();
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
            if(inside(mx,my,cx+cw-72,iy+54,60,16)) {
                com.bame.client.BameClientConfig.guiAnimation = (com.bame.client.BameClientConfig.guiAnimation + 1) % 3;
                com.bame.client.BameClientConfig.save();
                return true;
            }
        } else {
            int leftY = 0;
            if(minerVisible()) {
                int myY = baseY() + leftY;
                if(inside(mx,my,cx+cw-38,myY+12,26,14)) {
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
                if(inside(mx,my,cx,myY,cw,46)) { expanded=!expanded; layout(); return true; }
                if(expanded) {
                    if(corner1.mouseClicked(click,twice)) { setFocused(corner1); return true; }
                    if(corner2.mouseClicked(click,twice)) { setFocused(corner2); return true; }
                    if(inside(mx,my,cx+cw-50,myY+132,38,20)) { setCorner(true); return true; }
                    if(inside(mx,my,cx+cw-50,myY+156,38,20)) { setCorner(false); return true; }
                    if(inside(mx,my,cx+12,myY+184,cw-24,22)) { AutoAreaMinerModule.mode3x3=!AutoAreaMinerModule.mode3x3; BameClientConfig.save(); return true; }
                    if(inside(mx,my,cx+170,myY+228,45,18)) { BameClientConfig.renderMode=0; BameClientConfig.save(); return true; }
                    if(inside(mx,my,cx+220,myY+228,55,18)) { BameClientConfig.renderMode=1; BameClientConfig.save(); return true; }
                    if(inside(mx,my,cx+170,myY+250,55,18)) { BameClientConfig.renderMode=2; BameClientConfig.save(); return true; }
                    if(inside(mx,my,cx+230,myY+250,45,18)) { BameClientConfig.renderMode=3; BameClientConfig.save(); return true; }
                    
                    if(picker.click(mx,my)) return true;
                }
                leftY += (expanded?346:46) + 12;
            }
            int rightY = leftY;
            int gap = 16;
            int halfW = (cw - gap) / 2;
            
            if (showHudVisible()) {
                int myY = baseY() + leftY;
                if (inside(mx, my, cx + halfW - 52, myY + 5, 30, 18)) {
                    ShowHudModule.enabled = !ShowHudModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, cx + 58, myY + 24, 44, 18)) {
                    listeningShowHud = true;
                    return true;
                }
                if (inside(mx, my, cx + halfW - 64, myY + 24, 54, 18)) {
                    client.setScreen(new HudEditorScreen(this));
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
                    curY += 26;

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

                    // 12. Name Protect
                    if (inside(mx, my, cx + halfW - 38, curY + 3, 26, 14)) {
                        NameProtectModule.enabled = !NameProtectModule.enabled;
                        BameClientConfig.save();
                        layout();
                        return true;
                    }
                    curY += 26;

                    if (NameProtectModule.enabled) {
                        if (nameProtectAliasField != null && nameProtectAliasField.mouseClicked(click, twice)) {
                            setFocused(nameProtectAliasField);
                            return true;
                        }
                        curY += 26;
                    }

                    // Reset button
                    curY += 4;
                    if (inside(mx, my, cx + halfW - 58, curY, 46, 16)) {
                        resetShowHud();
                        return true;
                    }
                }
                leftY += getShowHudHeight() + 12;
            }

            if (fakeScoreboardVisible()) {
                int myY = baseY() + leftY;
                if (inside(mx, my, cx + halfW - 38, myY + 12, 26, 14)) {
                    FakeScoreboardModule.enabled = !FakeScoreboardModule.enabled;
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, cx + 58, myY + 25, 42, 16)) {
                    listeningFakeScoreboard = true;
                    return true;
                }
                if (inside(mx, my, cx, myY, halfW, 46)) {
                    FakeScoreboardModule.expanded = !FakeScoreboardModule.expanded;
                    layout();
                    return true;
                }
                if (FakeScoreboardModule.expanded) {
                    if (fakeMoneyField != null && fakeMoneyField.mouseClicked(click, twice)) {
                        setFocused(fakeMoneyField);
                        return true;
                    }
                    if (fakeStarsField != null && fakeStarsField.mouseClicked(click, twice)) {
                        setFocused(fakeStarsField);
                        return true;
                    }
                    if (fakeKillsField != null && fakeKillsField.mouseClicked(click, twice)) {
                        setFocused(fakeKillsField);
                        return true;
                    }
                    if (fakeDeathsField != null && fakeDeathsField.mouseClicked(click, twice)) {
                        setFocused(fakeDeathsField);
                        return true;
                    }
                    if (fakeTimeField != null && fakeTimeField.mouseClicked(click, twice)) {
                        setFocused(fakeTimeField);
                        return true;
                    }
                }
                leftY += getFakeScoreboardHeight() + 12;
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
                    if (inside(mx, my, zX + 12, myY + 58, 60, 20)) {
                        ZoomModule.mode = (ZoomModule.mode == 0) ? 1 : 0;
                        BameClientConfig.save();
                        return true;
                    }
                }
                rightY += (ZoomModule.expanded ? 92 : 46) + 12;
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
                if (inside(mx, my, sX + halfW - 62, myY + 25, 50, 16)) {
                    if (client != null) client.setScreen(new HudEditorScreen(this));
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
                }
                rightY += (SpotifyHudModule.expanded ? 84 : 46) + 12;
            }
        }
        return false;
    }

    private void resetShowHud() {
        ClockModule.enabled = false;
        CoordinatesModule.enabled = false;
        PotionsModule.enabled = false;
        TargetHudModule.enabled = true;
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
        NameProtectModule.enabled = false;
        NameProtectModule.alias = "";
        if (nameProtectAliasField != null) nameProtectAliasField.setText("");
        ArmorHudModule.enabled = false;

        FpsModule.hudX = 10; FpsModule.hudY = 10; FpsModule.scale = 1.0f; FpsModule.bgMode = 0;
        PingModule.hudX = 10; PingModule.hudY = 34; PingModule.scale = 1.0f; PingModule.bgMode = 0;
        CpsModule.hudX = 10; CpsModule.hudY = 58; CpsModule.scale = 1.0f; CpsModule.bgMode = 0;
        ClockModule.hudX = 10; ClockModule.hudY = 82; ClockModule.scale = 1.0f; ClockModule.bgMode = 0;
        CoordinatesModule.hudX = 10; CoordinatesModule.hudY = 106; CoordinatesModule.scale = 1.0f; CoordinatesModule.bgMode = 0;
        PotionsModule.hudX = 10; PotionsModule.hudY = 130; PotionsModule.scale = 1.0f; PotionsModule.bgMode = 0;
        ServerInfoModule.hudX = -1; ServerInfoModule.hudY = 10; ServerInfoModule.scale = 1.0f; ServerInfoModule.bgMode = 0;
        KeyStrokesModule.hudX = 10; KeyStrokesModule.hudY = 160; KeyStrokesModule.scale = 1.0f; KeyStrokesModule.bgMode = 0;
        TargetHudModule.hudX = -1; TargetHudModule.hudY = -1; TargetHudModule.scale = 1.0f; TargetHudModule.bgMode = 0;
        ArmorHudModule.hudX = -1; ArmorHudModule.hudY = -1; ArmorHudModule.scale = 1.0f; ArmorHudModule.bgMode = 0;

        BameClientConfig.save();
        layout();
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("§a[Show HUD] Einstellungen und Positionen auf Standard zurückgesetzt!"), false);
        }
    }
    private void select(String category) { picker.release(); themeSettings.close(); selected=category; BameClientConfig.save(); scroll=0; listening=false; listeningZoom=false; listeningShowHud=false; listeningFullbright=false; listeningFakeScoreboard=false; listeningSpotify=false; unfocus(); layout(); }
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
        if(draggingWidth) {
            float val = (float)Math.clamp(((click.x() - (cx+14)) / 140.0), 0.0, 1.0);
            BameClientConfig.outlineWidth = 1.0f + val * 4.0f;
            return true;
        }
        if(picker.dragging()) { picker.update(click.x(),click.y()); return true; }
        return super.mouseDragged(click,dx,dy);
    }
    @Override public boolean mouseReleased(Click click) {
        boolean handled=scrollDragging||picker.dragging()||themeSettings.dragging()||draggingWidth||draggingFullbright;
        scrollDragging=false; draggingWidth=false; draggingFullbright=false; com.bame.client.BameClientConfig.save(); picker.release(); themeSettings.release();
        return handled||super.mouseReleased(click);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        if(inside(mx,my,cx,cy,cw+14,ch)&&!picker.dragging()&&!themeSettings.dragging()) { scroll=Math.clamp(scroll-vertical*26,0,maxScroll()); layout(); return true; }
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public boolean keyPressed(KeyInput input) {
        if(input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && !listening && !listeningFullbright && !listeningMenuBind && !listeningZoom && !listeningShowHud && !listeningFakeScoreboard && !listeningSpotify) themeSettings.close();
        if(listening) { AutoAreaMinerModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listening=false; BameClientConfig.save(); return true; }
        if(listeningMenuBind) { com.bame.client.BameClientConfig.menuBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningMenuBind=false; com.bame.client.BameClientConfig.save(); return true; }
        if(listeningFullbright) { com.bame.client.module.FullbrightModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningFullbright=false; BameClientConfig.save(); return true; }
        if(listeningShowHud) { com.bame.client.module.ShowHudModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningShowHud=false; BameClientConfig.save(); return true; }
        if(listeningZoom) { com.bame.client.module.ZoomModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningZoom=false; BameClientConfig.save(); return true; }
        if(listeningFakeScoreboard) { com.bame.client.module.FakeScoreboardModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningFakeScoreboard=false; BameClientConfig.save(); return true; }
        if(listeningSpotify) { com.bame.client.module.SpotifyHudModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningSpotify=false; BameClientConfig.save(); return true; }
        return super.keyPressed(input);
    }
    @Override public void removed() { picker.release(); themeSettings.close(); BameClientConfig.save(); super.removed(); }
    @Override public boolean shouldPause() { return false; }

    @Override public void close() {
        super.close();
        openTime = 0;
        BameClientConfig.save();
    }

}
