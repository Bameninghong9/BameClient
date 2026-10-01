package com.bame.client.module;

public class TimeChangerModule {
    public static boolean enabled = false;
    public static boolean expanded = true;
    public static int keyBind = -1;
    public static int timeMode = 0; // 0=Day, 1=Noon, 2=Sunset, 3=Night, 4=Midnight, 5=Sunrise

    public static final String[] TIME_MODES = { "Day", "Noon", "Sunset", "Night", "Midnight", "Sunrise" };
    public static final long[] TIME_TICKS = { 1000L, 6000L, 12000L, 14000L, 18000L, 23000L };

    public static long getCustomTimeOfDay() {
        return TIME_TICKS[Math.floorMod(timeMode, TIME_TICKS.length)];
    }

    public static String getCurrentModeName() {
        return TIME_MODES[Math.floorMod(timeMode, TIME_MODES.length)];
    }

    public static void nextMode() {
        timeMode = (timeMode + 1) % TIME_MODES.length;
    }

    public static void prevMode() {
        timeMode = (timeMode - 1 + TIME_MODES.length) % TIME_MODES.length;
    }

    public static void reset() {
        enabled = false;
        keyBind = -1;
        timeMode = 0;
    }
}
