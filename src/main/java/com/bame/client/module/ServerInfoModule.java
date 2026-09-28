package com.bame.client.module;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ServerInfoModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static int hudX = -1; // -1 means default top-right
    public static int hudY = 10;
    public static float scale = 1.0f;
    public static int bgMode = 0; // 0 = Dark, 1 = Transparent, 2 = Rainbow, 3 = Theme
    public static int bgColor = 0xD012161E;

    public static boolean showName = true;
    public static boolean showServer = true;
    public static boolean showTime = true;

    public static boolean expanded = false;

    // Modular docked elements in the bar
    public static List<String> dockedElements = new ArrayList<>(Arrays.asList("name", "server", "time"));

    // Standalone positions if undocked
    public static int nameX = 10, nameY = 10;
    public static int serverX = 10, serverY = 32;
    public static int timeX = 10, timeY = 54;

    public static boolean isDocked(String element) {
        return dockedElements.contains(element);
    }

    public static void dock(String element) {
        if (!dockedElements.contains(element)) {
            dockedElements.add(element);
        }
    }

    public static void dock(int index, String element) {
        if (!dockedElements.contains(element)) {
            if (index >= 0 && index <= dockedElements.size()) {
                dockedElements.add(index, element);
            } else {
                dockedElements.add(element);
            }
        }
    }

    public static void undock(String element) {
        dockedElements.remove(element);
    }

    public static List<String> getActiveDockedElements() {
        List<String> list = new ArrayList<>();
        for (String elem : dockedElements) {
            if (elem.equals("name") && !showName) continue;
            if (elem.equals("server") && !showServer) continue;
            if (elem.equals("time") && !showTime) continue;
            if (elem.equals("fps") && !FpsModule.enabled) continue;
            if (elem.equals("ping") && !PingModule.enabled) continue;
            list.add(elem);
        }
        return list;
    }
}
