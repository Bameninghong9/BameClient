package com.bame.client;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import java.lang.reflect.Method;

public class Reflector {
    public static void run() {
        System.out.println("--- DrawContext Methods ---");
        for (Method m : DrawContext.class.getMethods()) {
            if (m.getName().equals("drawTexture") || m.getName().equals("drawGuiTexture")) {
                StringBuilder sb = new StringBuilder(m.getName() + "(");
                for (Class<?> p : m.getParameterTypes()) {
                    sb.append(p.getSimpleName()).append(", ");
                }
                System.out.println(sb.toString() + ")");
            }
        }
        System.out.println("--- AbstractClientPlayerEntity Methods ---");
        for (Method m : AbstractClientPlayerEntity.class.getMethods()) {
            if (m.getName().toLowerCase().contains("skin")) {
                System.out.println(m.getName() + " -> " + m.getReturnType().getSimpleName());
            }
        }
        throw new RuntimeException("DONE REFLECTING");
    }
}
