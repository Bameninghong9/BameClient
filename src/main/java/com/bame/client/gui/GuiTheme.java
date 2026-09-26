package com.bame.client.gui;

import com.bame.client.BameClientConfig;

public final class GuiTheme {
    public record Preset(String name, int primary, int secondary, int highlight, int background) {}
    public static final Preset[] PRESETS = {
        new Preset("Cyberpunk",0x00D9EF,0xFF269C,0x8428EE,0x0D1022),
        new Preset("Ultraviolet",0xB23CEE,0xD600F0,0x6200DF,0x12091F),
        new Preset("Toxic",0x54EF00,0xD6FF00,0x00EDAB,0x09180D),
        new Preset("Inferno",0xFF7900,0xFF2424,0xFFCC00,0x200D05),
        new Preset("Glacier",0x00C7EE,0x44DFF2,0x829BF7,0x09182A),
        new Preset("Bloodmoon",0xFF0645,0xFF4879,0xB30029,0x21060E),
        new Preset("Aurora",0x00E8B8,0x30DCEC,0x7055FA,0x071F21),
        new Preset("Bubblegum",0xFF43D9,0xF58DDF,0xB54AF2,0x240E2C),
        new Preset("Limelight",0xB5F800,0xEAFF63,0x00E68B,0x142004),
        new Preset("Sunset",0xFF008B,0xFF7A00,0xB52BF5,0x220924),
        new Preset("Electric",0x3470F8,0x04BDEB,0x5C7BF3,0x0C1530),
        new Preset("Nebula",0xA647F2,0xC77CF5,0x6319BA,0x17092A),
        new Preset("Goldrush",0xFFC600,0xFFE268,0xFF9D00,0x251B05),
        new Preset("Emerald",0x00D99B,0x2DF3BC,0x00AA79,0x062018),
        new Preset("Plasma",0xF22DCB,0x7252F6,0x04CDE9,0x1A0930),
        new Preset("Crimson",0xFA335F,0xFF7868,0xD80008,0x23070D)
    };
    public static Preset current() {
        for(var p:PRESETS) if(p.name().equals(BameClientConfig.theme)) return p;
        return PRESETS[1];
    }
    public static int accent() { return BameClientConfig.customMainColor ? BameClientConfig.mainColor : 0xFF000000 | current().primary(); }
    public static int alpha(int color,int alpha) { return (alpha<<24)|(color&0xFFFFFF); }
    public static int surface() { return 0xFF12151B; }
}
