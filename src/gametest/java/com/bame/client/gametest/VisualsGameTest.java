package com.bame.client.gametest;

import com.bame.client.module.CustomFireModule;
import com.bame.client.module.NametagsModule;
import com.bame.client.module.ShaderFogModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;

/**
 * Optische Prüfung von Custom Fire, Shader Fog und Nametags in einer echten Testwelt: jede Szene wird fotografiert
 * (build/run/clientGameTest/screenshots). Die Bilder werden von Hand angesehen, es gibt keine Vergleichsbilder.
 */
public class VisualsGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(true).create()) {
            world.getClientWorld().waitForChunksRender();
            world.getServer().runCommand("time set 6000");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("gamemode creative @a");

            // --- Custom Fire: Feuerfläche vor dem Spieler (Blick nach Süden, leicht nach unten) ---
            world.getServer().runCommand("execute at @p run fill ~-3 ~1 ~2 ~3 ~4 ~8 minecraft:air");
            world.getServer().runCommand("execute at @p run fill ~-3 ~-1 ~2 ~3 ~-1 ~8 minecraft:netherrack");
            world.getServer().runCommand("execute at @p run fill ~-3 ~ ~2 ~3 ~ ~8 minecraft:fire");
            look(context, 0.0f, 28.0f, Perspective.FIRST_PERSON);
            context.waitTicks(40);
            context.takeScreenshot("fire_vanilla");

            context.runOnClient(client -> {
                CustomFireModule.enabled = true;
                CustomFireModule.color = 0xFFFFFFFF;
                CustomFireModule.gradient = false;
                CustomFireModule.chroma = false;
            });
            context.waitTicks(10);
            context.takeScreenshot("fire_white");

            context.runOnClient(client -> {
                CustomFireModule.gradient = true;
                CustomFireModule.color = 0xFF2F8CFF;
                CustomFireModule.topColor = 0xFFB23CEE;
            });
            context.waitTicks(10);
            context.takeScreenshot("fire_gradient");

            // Spieler brennt: Feuer am Bildschirm, dann Third Person mit Nametag und Badge
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("effect give @a minecraft:resistance 60 4 true");
            world.getServer().runCommand("effect give @a minecraft:regeneration 60 4 true");
            world.getServer().runCommand("execute at @p run setblock ~ ~ ~ minecraft:fire");
            look(context, 0.0f, 10.0f, Perspective.FIRST_PERSON);
            context.waitTicks(15);
            context.takeScreenshot("fire_overlay");

            context.runOnClient(client -> {
                NametagsModule.enabled = true;
                NametagsModule.showOwn = true;
                NametagsModule.showBadge = true;
                NametagsModule.scale = 1.3f;
                NametagsModule.backgroundOpacity = 0.45f;
                NametagsModule.backgroundColor = 0xFF1A0F24;
            });
            look(context, 180.0f, 15.0f, Perspective.THIRD_PERSON_FRONT);
            context.waitTicks(10);
            context.takeScreenshot("fire_thirdperson_nametag");

            world.getServer().runCommand("execute at @p run fill ~-1 ~ ~-1 ~1 ~ ~1 minecraft:air");
            world.getServer().runCommand("effect clear @a");
            world.getServer().runCommand("gamemode creative @a");
            context.runOnClient(client -> {
                client.player.extinguish();
                CustomFireModule.enabled = false;
            });

            // --- Shader Fog: hoch fliegen, Blick zum Planeten (Gierwinkel 35°, etwas nach oben) ---
            world.getServer().runCommand("execute as @p at @s run tp @s ~ ~45 ~");
            context.runOnClient(client -> {
                client.player.getAbilities().flying = true;
                ShaderFogModule.enabled = true;
                ShaderFogModule.style = ShaderFogModule.Style.SPACE;
                ShaderFogModule.planet = true;
                ShaderFogModule.hideSunMoon = true;
            });
            look(context, 35.0f, -22.0f, Perspective.FIRST_PERSON);
            world.getClientWorld().waitForChunksRender();
            context.waitTicks(100); // Himmel wird im Hintergrund erzeugt
            context.takeScreenshot("sky_space");

            look(context, 200.0f, 8.0f, Perspective.FIRST_PERSON);
            context.waitTicks(10);
            context.takeScreenshot("sky_space_horizon");

            context.runOnClient(client -> ShaderFogModule.fogDensity = 1.0f);
            context.waitTicks(10);
            context.takeScreenshot("sky_space_horizon_dense_fog");
            context.runOnClient(client -> ShaderFogModule.fogDensity = 0.35f);

            look(context, 125.0f, -8.0f, Perspective.FIRST_PERSON);
            context.waitTicks(10);
            context.takeScreenshot("sky_space_edges");

            context.runOnClient(client -> ShaderFogModule.style = ShaderFogModule.Style.DEEP_SEA);
            look(context, 35.0f, -22.0f, Perspective.FIRST_PERSON);
            context.waitTicks(100);
            context.takeScreenshot("sky_deep_sea");

            context.runOnClient(client -> {
                ShaderFogModule.style = ShaderFogModule.Style.COLOR;
                ShaderFogModule.planet = false;
            });
            context.waitTicks(10);
            context.takeScreenshot("sky_color");

            context.runOnClient(client -> {
                ShaderFogModule.enabled = false;
                NametagsModule.enabled = false;
            });
        }
    }

    private static void look(ClientGameTestContext context, float yaw, float pitch, Perspective perspective) {
        context.runOnClient(client -> {
            client.options.setPerspective(perspective);
            client.player.setYaw(yaw);
            client.player.setPitch(pitch);
            client.player.setHeadYaw(yaw);
            client.player.setBodyYaw(yaw);
            client.player.lastYaw = yaw;
            client.player.lastPitch = pitch;
        });
    }
}
