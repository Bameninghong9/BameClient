package com.bame.client;
import net.minecraft.text.Style;
import java.lang.reflect.Method;
public class TestDump {
    public static void main(String[] args) {
        for (Method m : Style.class.getMethods()) {
            System.out.println(m.getName() + " -> " + (m.getParameterCount() > 0 ? m.getParameterTypes()[0].getName() : "none"));
        }
    }
}
