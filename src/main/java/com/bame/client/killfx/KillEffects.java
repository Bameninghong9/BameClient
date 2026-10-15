package com.bame.client.killfx;

import com.bame.client.cosmetics.CosmeticsConfig;
import com.bame.client.emotes.EmoteUnlockManager;
import com.bame.client.notification.Notification;
import com.bame.client.notification.NotificationManager;
import com.bame.client.shop.CaeserApi;
import com.bame.client.shop.CaeserSyncClient;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

/**
 * Einstieg für Kill-Effekte: lokales Abspielen nach einem eigenen Kill (Bildschirm- und Welt-Teil), Senden der
 * Sync-Nachricht {@code killfx}, Empfang fremder Effekte (nur Welt-Teil) und der "Testen"-Knopf.
 * Alles nur auf dem Client, nichts geht an den Minecraft-Server.
 */
public final class KillEffects {

    /** Fremde Effekte nur in diesem Umkreis (Blöcke). */
    public static final double REMOTE_RANGE = 48.0;

    private static final KillLogic.Cooldown TEST_COOLDOWN = new KillLogic.Cooldown(KillLogic.TEST_COOLDOWN_MS);
    private static final KillLogic.Cooldown SEND_LIMIT = new KillLogic.Cooldown(KillLogic.SEND_INTERVAL_MS);

    private KillEffects() {}

    /** Ausgerüsteter und besessener Effekt, sonst {@code null}. */
    public static KillEffectType equipped() {
        KillEffectType type = KillEffectType.fromId(CosmeticsConfig.killEffect);
        if (type == null) return null;
        return EmoteUnlockManager.isCosmeticUnlocked(KillEffectType.CATEGORY, type.id) ? type : null;
    }

    /** Vom {@link KillDetector}: der lokale Spieler hat jemanden getötet. */
    public static void onLocalKill(Vec3d pos, String victimName) {
        onLocalKill(pos, victimName, null, true);
    }

    /**
     * Vom {@link KillDetector}: der lokale Spieler hat jemanden getötet.
     *
     * @param victimUuid UUID des Opfers (für die zuletzt gesehene Rüstung der Figur) oder {@code null}
     * @param player     war das Opfer ein Spieler?
     */
    public static void onLocalKill(Vec3d pos, String victimName, java.util.UUID victimUuid, boolean player) {
        KillEffectType type = equipped();
        if (type == null || pos == null) return;
        String name = KillLogic.sanitizeVictimName(victimName);
        KillVictims.Figure figure = type.usesFigure() ? KillVictims.figure(victimUuid, name, player) : null;
        play(type, pos, name, true, figure);
        send(type, pos, name);
    }

    /**
     * Spielt einen Effekt ab.
     *
     * @param screenPart Bildschirm-Teil (nur für den Täter bzw. beim Testen, und nur mit eingeschaltetem Schalter)
     */
    static void play(KillEffectType type, Vec3d pos, String victimName, boolean screenPart, KillVictims.Figure figure) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        if (screenPart && CosmeticsConfig.killFxScreen && type.hasScreenPart) {
            playScreen(mc, type);
        }
        KillWorldEffects.spawn(type, pos, victimName, figure);
    }

    private static void playScreen(MinecraftClient mc, KillEffectType type) {
        switch (type) {
            case TOTEM -> {
                // Standard-Totem-Animation von Minecraft (Vanilla-Modell), ohne zusätzlichen Text
                if (mc.gameRenderer != null) mc.gameRenderer.showFloatingItem(new ItemStack(Items.TOTEM_OF_UNDYING));
            }
            case LIGHTNING -> KillEffectHud.flash();
            default -> { }
        }
    }

    /** Sync-Nachricht an den Caeser-Server (höchstens 1 pro 2 s, wie der Server es annimmt). */
    private static void send(KillEffectType type, Vec3d pos, String victimName) {
        if (!CaeserSyncClient.isConnected()) return;
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) return;
        if (!SEND_LIMIT.tryAcquire(System.currentTimeMillis())) return;
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "killfx");
        msg.addProperty("effect", type.id);
        msg.addProperty("x", round2(pos.x));
        msg.addProperty("y", round2(pos.y));
        msg.addProperty("z", round2(pos.z));
        if (victimName != null) msg.addProperty("victimName", victimName);
        CaeserSyncClient.send(msg);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Vom Sync-Client (Haupt-Thread): Effekt eines anderen Caeser-Spielers. Nur der Welt-Teil (ohne Bildschirm-Teil). */
    public static void onRemote(String uuid32, String name, String effect, double x, double y, double z, String victimName) {
        if (!CosmeticsConfig.killFxShowOthers) return;
        KillEffectType type = KillEffectType.fromId(effect);
        if (type == null) return; // unbekannte bzw. entfernte Effekt-IDs (z. B. KILL_FIREWORK) ignorieren
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (uuid32 != null && uuid32.equals(CaeserApi.uuid32(mc.player.getUuid()))) return; // eigener Effekt (läuft schon lokal)
        Vec3d pos = new Vec3d(x, y, z);
        if (mc.player.getEntityPos().squaredDistanceTo(pos) > REMOTE_RANGE * REMOTE_RANGE) return;
        // Fairness: nichts zeigen, was man sonst nicht sehen würde (z. B. einen Kampf hinter einer Wand)
        if (!inLineOfSight(mc, pos)) return;
        // Amboss, Snap, Hell Gate: Figur aus der zuletzt gesehenen Rüstung/dem Skin des Opfers (sonst Leder bzw. Skin über den Namen)
        play(type, pos, KillLogic.sanitizeVictimName(victimName), false, null);
    }

    /** Freie Sicht von der Kamera auf den Todesort (Körpermitte oder Füße). */
    private static boolean inLineOfSight(MinecraftClient mc, Vec3d pos) {
        if (mc.world == null || mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return false;
        Vec3d eye = mc.gameRenderer.getCamera().getCameraPos();
        for (double dy : new double[]{1.0, 0.2}) {
            Vec3d target = pos.add(0, dy, 0);
            net.minecraft.util.hit.HitResult hit = mc.world.raycast(new net.minecraft.world.RaycastContext(eye, target,
                    net.minecraft.world.RaycastContext.ShapeType.VISUAL, net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player));
            if (hit == null || hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS) return true;
        }
        return false;
    }

    /**
     * "Testen"-Knopf: schließt das Menü und spielt den Effekt einmal 3 Blöcke vor dem Spieler ab (mit Bildschirm-Teil).
     * Nur lokal, nichts wird gesendet. Höchstens einmal pro 3 s.
     *
     * @return {@code false}, wenn noch die Abklingzeit läuft bzw. keine Welt geladen ist
     */
    public static boolean test(KillEffectType type) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (type == null) return false;
        if (mc.player == null || mc.world == null) {
            NotificationManager.add(new Notification("Kill Effects", "Testing only works in a world", Notification.Type.INFO, 2500));
            return false;
        }
        long now = System.currentTimeMillis();
        if (!TEST_COOLDOWN.tryAcquire(now)) return false;
        mc.setScreen(null);
        Vec3d look = mc.player.getRotationVec(1.0F);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        Vec3d pos = mc.player.getEntityPos().add(flat.multiply(3.0));
        // Amboss, Snap, Hell Gate: eigener Skin und eigene Rüstung als Figur (ohne Rüstung: Leder)
        play(type, pos, "Steve", true, type.usesFigure() ? KillVictims.localFigure() : null);
        return true;
    }

    public static boolean testReady() {
        return TEST_COOLDOWN.ready(System.currentTimeMillis());
    }

    public static void tick(MinecraftClient mc) {
        KillVictims.tick(mc);
        KillWorldEffects.tick(mc);
    }

    /** WorldRenderEvents.BEFORE_ENTITIES: Sarg, Amboss, Snap und Hell Gate (mit Opfer-Figur) zusammen mit den Entities. */
    public static void submitWorld(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
        KillWorldEffects.submit(context);
    }

    /** WorldRenderEvents.END_MAIN: Grabsteine und Blitze. */
    public static void renderWorld(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
        KillWorldEffects.render(context);
    }

    /** Welt verlassen: laufende Effekte verwerfen. */
    public static void clear() {
        KillWorldEffects.clear();
        KillEffectHud.clear();
        KillVictims.clear();
        KillFigureTextures.clear();
    }
}
