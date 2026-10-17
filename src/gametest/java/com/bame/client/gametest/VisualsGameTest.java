package com.bame.client.gametest;

import com.bame.client.module.CustomFireModule;
import com.bame.client.module.NametagsModule;
import com.bame.client.module.ShaderFogModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.resource.ResourcePackManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Optische Prüfung von Custom Fire, Shader Fog und Nametags in einer echten Testwelt: jede Szene wird fotografiert
 * (build/run/clientGameTest/screenshots). Die Bilder werden von Hand angesehen, es gibt keine Vergleichsbilder.
 * Optional: Umgebungsvariable CAESER_TEST_PACK = Pfad zu einem Resourcepack (.zip) mit eigenen Feuer-Texturen; dann
 * wird Custom Fire zusätzlich mit diesem Pack geprüft.
 */
public class VisualsGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(true).create()) {
            world.getClientWorld().waitForChunksRender();
            world.getServer().runCommand("time set 6000");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("gamerule doDaylightCycle false");
            world.getServer().runCommand("gamemode creative @a");

            // --- Custom Fire: Feuerfläche vor dem Spieler (Blick nach Süden, leicht nach unten) ---
            world.getServer().runCommand("execute at @p run fill ~-3 ~1 ~2 ~3 ~4 ~8 minecraft:air");
            world.getServer().runCommand("execute at @p run fill ~-3 ~-1 ~2 ~3 ~-1 ~8 minecraft:netherrack");
            world.getServer().runCommand("execute at @p run fill ~-3 ~ ~2 ~3 ~ ~8 minecraft:fire");
            look(context, 0.0f, 28.0f, Perspective.FIRST_PERSON);
            context.waitTicks(40);
            context.takeScreenshot("fire_vanilla");

            context.runOnClient(client -> {
                CustomFireModule.resetToDefault();
                CustomFireModule.enabled = true;
            });
            context.waitTicks(20);
            context.takeScreenshot("fire_native_default");

            context.runOnClient(client -> {
                CustomFireModule.style = CustomFireModule.Style.SHARP;
                CustomFireModule.color = 0xFFFFFFFF;
                CustomFireModule.autoSecondary = true;
                CustomFireModule.edgeGlow = 1.0f;
                CustomFireModule.intensity = 1.5f;
            });
            context.waitTicks(20);
            context.takeScreenshot("fire_sharp_white");

            context.runOnClient(client -> {
                CustomFireModule.style = CustomFireModule.Style.MINIMAL;
                CustomFireModule.color = 0xFF2F8CFF;
                CustomFireModule.edgeGlow = 0.3f;
                CustomFireModule.intensity = 1.0f;
                CustomFireModule.worldFlameHeight = 0.6f;
            });
            context.waitTicks(20);
            context.takeScreenshot("fire_minimal_blue_low");

            // Resourcepack mit eigener Feuer-Textur (fire_floor): muss ebenfalls umgestaltet werden
            String pack = System.getenv("CAESER_TEST_PACK");
            if (pack != null && Files.isRegularFile(Path.of(pack))) {
                Path target = context.computeOnClient(client -> client.runDirectory.toPath())
                        .resolve("resourcepacks").resolve(Path.of(pack).getFileName().toString());
                try {
                    Files.createDirectories(target.getParent());
                    Files.copy(Path.of(pack), target, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                context.runOnClient(client -> {
                    ResourcePackManager manager = client.getResourcePackManager();
                    manager.scanPacks();
                    manager.enable("file/" + target.getFileName());
                    client.options.refreshResourcePacks(manager);
                });
                context.waitFor(client -> client.getOverlay() == null, 1200);
                context.runOnClient(client -> {
                    CustomFireModule.style = CustomFireModule.Style.NATIVE;
                    CustomFireModule.color = 0xFF2F8CFF;
                    CustomFireModule.worldFlameHeight = 1.0f;
                });
                look(context, 0.0f, 40.0f, Perspective.FIRST_PERSON);
                context.waitTicks(40);
                context.takeScreenshot("fire_pack_blue");
                context.runOnClient(client -> CustomFireModule.enabled = false);
                context.waitTicks(20);
                context.takeScreenshot("fire_pack_vanilla");
                context.runOnClient(client -> CustomFireModule.enabled = true);
            }

            // Spieler brennt: Feuer am Bildschirm, dann Third Person mit Nametag und Badge
            context.runOnClient(client -> {
                CustomFireModule.style = CustomFireModule.Style.NATIVE;
                CustomFireModule.color = 0xFFFF6A1A;
                CustomFireModule.height = 0.36f;
            });
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("effect give @a minecraft:resistance 60 4 true");
            world.getServer().runCommand("effect give @a minecraft:regeneration 60 4 true");
            world.getServer().runCommand("execute at @p run setblock ~ ~ ~ minecraft:fire");
            look(context, 0.0f, 10.0f, Perspective.FIRST_PERSON);
            context.waitTicks(20);
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
                NametagsModule.enabled = false;
            });

            // --- Shader Fog: hoch fliegen ---
            world.getServer().runCommand("execute as @p at @s run tp @s ~ ~45 ~");
            context.runOnClient(client -> {
                client.player.getAbilities().flying = true;
                ShaderFogModule.resetToDefault();
                ShaderFogModule.enabled = true;
            });
            world.getClientWorld().waitForChunksRender();
            for (ShaderFogModule.Mode mode : ShaderFogModule.Mode.values()) {
                context.runOnClient(client -> ShaderFogModule.mode = mode);
                // Saturn: Blick zum Planeten, sonst schräg nach oben mit Horizont
                if (mode == ShaderFogModule.Mode.SATURN) look(context, 153.0f, -38.0f, Perspective.FIRST_PERSON);
                else look(context, 30.0f, -25.0f, Perspective.FIRST_PERSON);
                context.waitTicks(20);
                context.takeScreenshot("sky_" + mode.name().toLowerCase());
            }
            context.runOnClient(client -> ShaderFogModule.mode = ShaderFogModule.Mode.SATURN);
            look(context, 200.0f, 6.0f, Perspective.FIRST_PERSON);
            context.waitTicks(10);
            context.takeScreenshot("sky_saturn_horizon");

            context.runOnClient(client -> ShaderFogModule.enabled = false);
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
