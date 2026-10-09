package com.bame.client.cosmetics;

import com.bame.client.module.SkinProtectModule;
import com.mojang.authlib.GameProfile;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class CaeserCosmeticsRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    // Textures
    public static final Identifier GOLD_WINGS_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/wings/gold_wings.png");
    public static final Identifier DRAGON_WINGS_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/wings/dragon_wings.png");
    public static final Identifier ANGEL_HALO_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/hat/angel_halo.png");
    public static final Identifier DEMON_HORNS_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/hat/demon_horns.png");
    public static final Identifier GOLD_CHAIN_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/accessory/gold_chain.png");

    // Models
    public static BedrockCosmeticModel goldWingsModel;
    public static BedrockCosmeticModel dragonWingsModel;
    public static BedrockCosmeticModel angelHaloModel;
    public static BedrockCosmeticModel demonHornsModel;

    // Independent Chibi models for each accessory so they NEVER overwrite each other's pose!
    public static ModelPart sitChibiRoot;
    public static PlayerEntityModel sitChibiModel;

    public static ModelPart headChibiRoot;
    public static PlayerEntityModel headChibiModel;

    public static ModelPart handChibiRoot;
    public static PlayerEntityModel handChibiModel;

    public static ModelPart chainChibiRoot;
    public static PlayerEntityModel chainChibiModel;

    private static String cachedCustomName = null;
    private static Supplier<SkinTextures> cachedCustomSupplier = null;

    public CaeserCosmeticsRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        initModels();
    }

    public static synchronized void initModels() {
        if (sitChibiRoot == null) {
            sitChibiRoot = TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64).createModel();
            sitChibiModel = new PlayerEntityModel(sitChibiRoot, false);
            poseChibiModel(sitChibiModel);
        }
        if (headChibiRoot == null) {
            headChibiRoot = TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64).createModel();
            headChibiModel = new PlayerEntityModel(headChibiRoot, false);
            poseChibiModel(headChibiModel);
        }
        if (handChibiRoot == null) {
            handChibiRoot = TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64).createModel();
            handChibiModel = new PlayerEntityModel(handChibiRoot, false);
            poseHangingChibiModel(handChibiModel);
        }
        if (chainChibiRoot == null) {
            chainChibiRoot = TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64).createModel();
            chainChibiModel = new PlayerEntityModel(chainChibiRoot, false);
            poseChainChibiModel(chainChibiModel);
        }
        if (goldWingsModel == null) {
            goldWingsModel = BedrockCosmeticModel.load("assets/bameclient/models/cosmetics/wings/gold_wings.geo.json");
        }
        if (dragonWingsModel == null) {
            dragonWingsModel = BedrockCosmeticModel.load("assets/bameclient/models/cosmetics/wings/dragon_wings.geo.json");
        }
        if (angelHaloModel == null) {
            angelHaloModel = BedrockCosmeticModel.load("assets/bameclient/models/cosmetics/hat/angel_halo.geo.json");
        }
        if (demonHornsModel == null) {
            demonHornsModel = BedrockCosmeticModel.load("assets/bameclient/models/cosmetics/hat/demon_horns.geo.json");
        }
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Render cosmetics on the local player (third-person view, inventory, paper doll)
        boolean isLocal = false;
        if (state.playerName != null && state.playerName.getString().equalsIgnoreCase(mc.getSession().getUsername())) {
            isLocal = true;
        } else if (state.id == mc.player.getId()) {
            isLocal = true;
        }
        if (!isLocal) return;

        CosmeticsConfig.checkReload();

        // 1. Wings
        if ("angel".equalsIgnoreCase(CosmeticsConfig.wings)) {
            renderAngelWings(matrices, queue, light);
        } else if ("dragon".equalsIgnoreCase(CosmeticsConfig.wings)) {
            renderDragonWings(matrices, queue, light);
        }

        // 2. Head Accessories (Halo, Horns)
        if ("halo".equalsIgnoreCase(CosmeticsConfig.head)) {
            renderHalo(matrices, queue, light);
        } else if ("horns".equalsIgnoreCase(CosmeticsConfig.head)) {
            renderDemonHorns(matrices, queue, light);
        }

        // 3. Accessories (MiniMe Sit, Head, Hand, Chain) - Multi-equip enabled!
        if (CosmeticsConfig.isAccessoryEquipped("minime_sit")) {
            renderShoulderPet(matrices, queue, light, state);
        }
        if (CosmeticsConfig.isAccessoryEquipped("minime_head")) {
            renderHeadPet(matrices, queue, light, state);
        }
        if (CosmeticsConfig.isAccessoryEquipped("minime_hand")) {
            renderHandPet(matrices, queue, light, state, limbAngle, limbDistance);
        }
        if (CosmeticsConfig.isAccessoryEquipped("minime_chain")) {
            renderChainPet(matrices, queue, light, state);
        }
    }

    // ==========================================
    // ANGEL WINGS (Gold Wings pixel-art geometry & animated shimmer)
    // ==========================================
    private void renderAngelWings(MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        if (goldWingsModel == null) return;

        matrices.push();
        this.getContextModel().body.applyTransform(matrices);

        goldWingsModel.resetAllAnimations();
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        float deg = timeSec * 100.0f;
        float flap1 = (float) Math.sin(Math.toRadians(deg - 50.0f)) * 8.5f;
        float flap2 = (float) Math.sin(Math.toRadians(deg - 100.0f)) * 34.0f;

        // Animate left wing bones
        goldWingsModel.setBoneRotation("tl", 25.4876f, -51.1698f + flap1, -12.93082f);
        goldWingsModel.setBoneTranslation("tl", -2.0f, 5.0f, 1.0f);
        goldWingsModel.setBoneRotation("tll", 0.0f, 15.0f + flap2, 0.0f);

        // Animate right wing bones
        goldWingsModel.setBoneRotation("tr", -25.4876f, 51.1698f - flap1, -12.93082f);
        goldWingsModel.setBoneTranslation("tr", -2.0f, 5.0f, -1.0f);
        goldWingsModel.setBoneRotation("trr", 0.0f, -15.0f - flap2, 0.0f);

        int frame = (int) ((System.currentTimeMillis() / 100L) % 15L);
        RenderLayer layer = RenderLayers.entityCutoutNoCull(GOLD_WINGS_TEXTURE);
        queue.getBatchingQueue(0).submitCustom(matrices, layer, (entry, consumer) -> {
            goldWingsModel.render(entry, consumer, light, frame, 15);
        });

        matrices.pop();
    }

    // ==========================================
    // DRAGON WINGS (Ender Dragon membranous geometry & flapping wings)
    // ==========================================
    private void renderDragonWings(MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        if (dragonWingsModel == null) return;

        matrices.push();
        this.getContextModel().body.applyTransform(matrices);

        dragonWingsModel.resetAllAnimations();
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        float deg = timeSec * 100.0f;
        float flap1 = (float) Math.sin(Math.toRadians(deg - 50.0f)) * 21.25f;
        float flap2 = (float) Math.sin(Math.toRadians(deg - 100.0f)) * 29.75f;

        dragonWingsModel.setBoneRotation("L2", 0.0f, -30.0f - flap1, 0.0f);
        dragonWingsModel.setBoneRotation("L3", 0.0f, -flap2, 0.0f);
        dragonWingsModel.setBoneRotation("R2", 0.0f, 30.0f + flap1, 0.0f);
        dragonWingsModel.setBoneRotation("R3", 0.0f, flap2, 0.0f);

        int frame = (int) ((System.currentTimeMillis() / 120L) % 10L);
        RenderLayer layer = RenderLayers.entityCutoutNoCull(DRAGON_WINGS_TEXTURE);
        queue.getBatchingQueue(0).submitCustom(matrices, layer, (entry, consumer) -> {
            dragonWingsModel.render(entry, consumer, light, frame, 10);
        });

        matrices.pop();
    }

    // ==========================================
    // HALO (Floating animated golden angel halo)
    // ==========================================
    private void renderHalo(MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        if (angelHaloModel == null) return;

        matrices.push();
        this.getContextModel().head.applyTransform(matrices);

        angelHaloModel.resetAllAnimations();
        float bob = (float) Math.sin(System.currentTimeMillis() * 0.0035) * 0.5f;
        angelHaloModel.setBoneTranslation("haloBase", 0.0f, bob, 0.0f);

        int frame = (int) ((System.currentTimeMillis() / 102L) % 16L);
        RenderLayer layer = RenderLayers.entityCutoutNoCull(ANGEL_HALO_TEXTURE);
        queue.getBatchingQueue(0).submitCustom(matrices, layer, (entry, consumer) -> {
            angelHaloModel.render(entry, consumer, light, frame, 16);
        });

        matrices.pop();
    }

    // ==========================================
    // DEMON HORNS (Textured curved horns on forehead)
    // ==========================================
    private void renderDemonHorns(MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        if (demonHornsModel == null) return;

        matrices.push();
        this.getContextModel().head.applyTransform(matrices);
        matrices.translate(0.0f, -0.01f, -0.070f);

        demonHornsModel.resetAllAnimations();
        RenderLayer layer = RenderLayers.entityCutoutNoCull(DEMON_HORNS_TEXTURE);
        queue.getBatchingQueue(0).submitCustom(matrices, layer, (entry, consumer) -> {
            demonHornsModel.render(entry, consumer, light, 0, 1);
        });

        matrices.pop();
    }

    // ==========================================
    // COMPANIONS / PETS (MiniMe Sit, Head, Hand, Chain)
    // ==========================================
    private void renderShoulderPet(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                  int light, PlayerEntityRenderState state) {
        if (sitChibiRoot == null) return;
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        poseChibiModel(sitChibiModel, timeSec);

        matrices.push();
        this.getContextModel().body.applyTransform(matrices);

        // Position perched neatly on the right shoulder:
        matrices.translate(-0.40f, -0.195f, -0.02f);
        matrices.scale(0.28f, 0.28f, 0.28f);

        Identifier skinTexture = getPetSkin("minime_sit", state);
        RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);
        queue.submitModelPart(sitChibiRoot, matrices, layer, light, OverlayTexture.DEFAULT_UV, null);

        matrices.pop();
    }

    private void renderHeadPet(MatrixStack matrices, OrderedRenderCommandQueue queue,
                               int light, PlayerEntityRenderState state) {
        if (headChibiRoot == null) return;
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        poseHeadChibiModel(headChibiModel, timeSec);

        matrices.push();
        this.getContextModel().head.applyTransform(matrices);

        // Position perched directly on top center of the head (sitting on top of hat, no clipping!):
        matrices.translate(0.0f, -0.74f, -0.02f);
        matrices.scale(0.28f, 0.28f, 0.28f);

        Identifier skinTexture = getPetSkin("minime_head", state);
        RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);
        queue.submitModelPart(headChibiRoot, matrices, layer, light, OverlayTexture.DEFAULT_UV, null);

        matrices.pop();
    }

    private void renderHandPet(MatrixStack matrices, OrderedRenderCommandQueue queue,
                               int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (handChibiRoot == null) return;
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        float walkSwing = (limbDistance > 0.01f) ? (float) Math.sin(limbAngle * 0.8f) * Math.min(limbDistance, 1.0f) * 0.35f : 0.0f;
        poseHangingChibiModel(handChibiModel, timeSec, walkSwing);

        matrices.push();
        // Dangling directly from player's left hand - grasping the hand firmly, matching NoRisk Bild 3!
        this.getContextModel().leftArm.applyTransform(matrices);

        matrices.translate(0.16f, 0.78f, 0.0f);
        matrices.scale(0.28f, 0.28f, 0.28f);

        Identifier skinTexture = getPetSkin("minime_hand", state);
        RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);
        queue.submitModelPart(handChibiRoot, matrices, layer, light, OverlayTexture.DEFAULT_UV, null);

        matrices.pop();
    }

    private void renderChainPet(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                int light, PlayerEntityRenderState state) {
        if (chainChibiRoot == null) return;
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        poseChainChibiModel(chainChibiModel, timeSec);

        // 1. Render Chibi hanging on player's upper chest (matching NoRisk Bild 2)
        matrices.push();
        this.getContextModel().body.applyTransform(matrices);

        matrices.translate(0.0f, 0.30f, -0.155f);
        matrices.scale(0.24f, 0.24f, 0.24f);

        Identifier skinTexture = getPetSkin("minime_chain", state);
        RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);
        queue.submitModelPart(chainChibiRoot, matrices, layer, light, OverlayTexture.DEFAULT_UV, null);

        matrices.pop();

        // 2. Render sleek 3D metallic chains from collar directly into chibi's hands
        matrices.push();
        this.getContextModel().body.applyTransform(matrices);

        RenderLayer chainLayer = RenderLayers.entityCutoutNoCull(GOLD_CHAIN_TEXTURE);
        queue.getBatchingQueue(0).submitCustom(matrices, chainLayer, (entry, consumer) -> {
            drawNecklaceChain(entry, consumer, light);
        });

        matrices.pop();
    }

    public static void drawNecklaceChain(MatrixStack.Entry entry, VertexConsumer consumer, int light) {
        // Strand Left: from left collar down to Chibi left hand (touching and holding the chain directly, matching NoRisk Bild 1)
        drawChainStrand(entry, consumer, light, 0.18f, -0.01f, -0.135f, 0.135f, 0.174f, -0.165f, 8);
        // Strand Right: from right collar down to Chibi right hand (touching and holding the chain directly, matching NoRisk Bild 1)
        drawChainStrand(entry, consumer, light, -0.18f, -0.01f, -0.135f, -0.135f, 0.174f, -0.165f, 8);
        // Back of collar loop connecting behind the neck
        drawChainStrand(entry, consumer, light, -0.18f, -0.01f, -0.135f, -0.12f, -0.02f, 0.135f, 4);
        drawChainStrand(entry, consumer, light, 0.18f, -0.01f, -0.135f, 0.12f, -0.02f, 0.135f, 4);
        drawChainStrand(entry, consumer, light, -0.12f, -0.02f, 0.135f, 0.12f, -0.02f, 0.135f, 3);
    }

    public static void drawCardChainStrands(MatrixStack.Entry entry, VertexConsumer consumer, int light) {
        // Standalone card preview: chains extending up and outwards from chibi hands (matching NoRisk Bild 2!)
        drawChainStrand(entry, consumer, light, 0.095f, 0.17f, 0.0f, 0.28f, 0.55f, 0.0f, 7);
        drawChainStrand(entry, consumer, light, -0.095f, 0.17f, 0.0f, -0.28f, 0.55f, 0.0f, 7);
    }

    public static void drawChainStrand(MatrixStack.Entry entry, VertexConsumer consumer, int light,
                                       float x1, float y1, float z1, float x2, float y2, float z2, int segments) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;

        // Radius of delicate chain link (~0.12 model pixels wide)
        float r = 0.008f;
        float d = 0.005f;

        for (int i = 0; i < segments; i++) {
            float t0 = (float) i / segments;
            float t1 = (float) (i + 1) / segments;
            float ax = x1 + dx * t0;
            float ay = y1 + dy * t0;
            float az = z1 + dz * t0;
            float bx = x1 + dx * t1;
            float by = y1 + dy * t1;
            float bz = z1 + dz * t1;

            // Alternating metallic shading matching NoRisk Bild 2
            boolean even = (i % 2 == 0);
            int baseRed = even ? 215 : 185;
            int baseGrn = even ? 215 : 185;
            int baseBlu = even ? 210 : 180;

            int edgeRed = 135;
            int edgeGrn = 135;
            int edgeBlu = 130;

            float rx = even ? r : (r * 0.5f);
            float rz = even ? (d * 0.5f) : d;

            // Front face (facing viewer)
            consumer.vertex(entry, ax - rx, ay, az - rz).color(baseRed, baseGrn, baseBlu, 255)
                    .texture(0.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);
            consumer.vertex(entry, ax + rx, ay, az - rz).color(baseRed, baseGrn, baseBlu, 255)
                    .texture(1.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);
            consumer.vertex(entry, bx + rx, by, bz - rz).color(baseRed, baseGrn, baseBlu, 255)
                    .texture(1.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);
            consumer.vertex(entry, bx - rx, by, bz - rz).color(baseRed, baseGrn, baseBlu, 255)
                    .texture(0.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);

            // Back face
            consumer.vertex(entry, bx - rx, by, bz + rz).color(edgeRed, edgeGrn, edgeBlu, 255)
                    .texture(0.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, -1);
            consumer.vertex(entry, bx + rx, by, bz + rz).color(edgeRed, edgeGrn, edgeBlu, 255)
                    .texture(1.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, -1);
            consumer.vertex(entry, ax + rx, ay, az + rz).color(edgeRed, edgeGrn, edgeBlu, 255)
                    .texture(1.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, -1);
            consumer.vertex(entry, ax - rx, ay, az + rz).color(edgeRed, edgeGrn, edgeBlu, 255)
                    .texture(0.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, -1);

            // Side edges (left and right) for 3D thickness
            consumer.vertex(entry, ax - rx, ay, az + rz).color(edgeRed, edgeGrn, edgeBlu, 255)
                    .texture(0.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, -1, 0, 0);
            consumer.vertex(entry, ax - rx, ay, az - rz).color(edgeRed, edgeGrn, edgeBlu, 255)
                    .texture(1.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, -1, 0, 0);
            consumer.vertex(entry, bx - rx, by, bz - rz).color(edgeRed, edgeGrn, edgeBlu, 255)
                    .texture(1.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, -1, 0, 0);
            consumer.vertex(entry, bx - rx, by, bz + rz).color(edgeRed, edgeGrn, edgeBlu, 255)
                    .texture(0.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, -1, 0, 0);

            consumer.vertex(entry, bx + rx, by, bz - rz).color(baseRed, baseGrn, baseBlu, 255)
                    .texture(1.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 1, 0, 0);
            consumer.vertex(entry, ax + rx, ay, az - rz).color(baseRed, baseGrn, baseBlu, 255)
                    .texture(1.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 1, 0, 0);
            consumer.vertex(entry, ax + rx, ay, az + rz).color(edgeRed, edgeGrn, edgeBlu, 255)
                    .texture(0.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 1, 0, 0);
            consumer.vertex(entry, bx + rx, by, bz + rz).color(edgeRed, edgeGrn, edgeBlu, 255)
                    .texture(0.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 1, 0, 0);
        }
    }

    private static Identifier getPetSkin(String accId, PlayerEntityRenderState state) {
        String custom = CosmeticsConfig.getAccessoryCustomPlayer(accId);
        if (custom != null && !custom.trim().isEmpty()) {
            return PlayerSkinResolver.resolve(custom);
        }
        if (SkinProtectModule.enabled) {
            return SkinProtectModule.getCurrentSkin().body().texturePath();
        }
        if (CaeserSkinManager.hasActiveOverride()) {
            SkinTextures customTex = CaeserSkinManager.getActiveSkinTextures();
            if (customTex != null && customTex.body() != null) {
                return customTex.body().texturePath();
            }
        }
        if (state != null && state.skinTextures != null && state.skinTextures.body() != null) {
            return state.skinTextures.body().texturePath();
        }
        return net.minecraft.client.util.DefaultSkinHelper.getTexture();
    }

    public static void poseChibiModel(PlayerEntityModel m) {
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        poseChibiModel(m, timeSec);
    }

    public static void poseChibiModel(PlayerEntityModel m, float timeSec) {
        if (m == null) return;
        m.head.visible = true;
        m.hat.visible = true;
        m.body.visible = true;
        m.rightArm.visible = true;
        m.leftArm.visible = true;
        m.rightLeg.visible = true;
        m.leftLeg.visible = true;
        m.jacket.visible = true;
        m.leftSleeve.visible = true;
        m.rightSleeve.visible = true;
        m.leftPants.visible = true;
        m.rightPants.visible = true;

        // MiniMe Sit (Schulter): Sitzt ruhig und entspannt – KEIN Beinezappeln!
        m.body.pitch = (float) Math.sin(timeSec * 1.5f) * 0.015f; // ganz sanftes Atmen
        m.body.yaw = 0.0f;
        m.body.roll = 0.0f;

        // Beine: Liegen stabil und ruhig auf der Schulter
        m.rightLeg.pitch = -1.52f;
        m.rightLeg.yaw = 0.12f;
        m.rightLeg.roll = 0.0f;

        m.leftLeg.pitch = -1.52f;
        m.leftLeg.yaw = -0.12f;
        m.leftLeg.roll = 0.0f;

        // Arme: Entspannt auf den Beinen liegend
        m.rightArm.pitch = -0.62f;
        m.rightArm.yaw = -0.08f;
        m.rightArm.roll = 0.12f;

        m.leftArm.pitch = -0.62f;
        m.leftArm.yaw = 0.08f;
        m.leftArm.roll = -0.12f;

        // Eigene Bewegung: Schaut sich neugierig in der Welt um (Head Look-Around)
        m.head.yaw = (float) Math.sin(timeSec * 0.85f) * 0.28f;
        m.head.pitch = (float) Math.cos(timeSec * 0.55f) * 0.08f - 0.05f;
        m.head.roll = (float) Math.sin(timeSec * 0.70f) * 0.07f + 0.06f;
    }

    public static void poseHeadChibiModel(PlayerEntityModel m) {
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        poseHeadChibiModel(m, timeSec);
    }

    public static void poseHeadChibiModel(PlayerEntityModel m, float timeSec) {
        if (m == null) return;
        m.head.visible = true;
        m.hat.visible = true;
        m.body.visible = true;
        m.rightArm.visible = true;
        m.leftArm.visible = true;
        m.rightLeg.visible = true;
        m.leftLeg.visible = true;
        m.jacket.visible = true;
        m.leftSleeve.visible = true;
        m.rightSleeve.visible = true;
        m.leftPants.visible = true;
        m.rightPants.visible = true;

        // MiniMe Head (Kopf): Balanciert von ganz oben – KEIN Beinezappeln!
        float balance = (float) Math.sin(timeSec * 1.3f) * 0.03f;
        m.body.pitch = balance * 0.4f;
        m.body.yaw = 0.0f;
        m.body.roll = balance;

        // Beine: Sitzen ruhig im Spreizsitz auf dem Hut
        m.rightLeg.pitch = -1.48f;
        m.rightLeg.yaw = 0.20f;
        m.rightLeg.roll = 0.04f;

        m.leftLeg.pitch = -1.48f;
        m.leftLeg.yaw = -0.20f;
        m.leftLeg.roll = -0.04f;

        // Arme: Hält sich an der Hutkrempe fest
        m.rightArm.pitch = -0.42f;
        m.rightArm.yaw = -0.12f;
        m.rightArm.roll = 0.24f - balance;

        m.leftArm.pitch = -0.42f;
        m.leftArm.yaw = 0.12f;
        m.leftArm.roll = -0.24f - balance;

        // Eigene Bewegung: Schaut von oben herab in die Ferne (Ausguck)
        m.head.yaw = (float) Math.sin(timeSec * 1.1f) * 0.24f;
        m.head.pitch = -0.12f + (float) Math.cos(timeSec * 1.4f) * 0.06f;
        m.head.roll = (float) Math.sin(timeSec * 1.2f) * 0.05f;
    }

    public static void poseHangingChibiModel(PlayerEntityModel m) {
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        poseHangingChibiModel(m, timeSec, 0.0f);
    }

    public static void poseHangingChibiModel(PlayerEntityModel m, float timeSec, float walkSwing) {
        if (m == null) return;
        m.head.visible = true;
        m.hat.visible = true;
        m.body.visible = true;
        m.rightArm.visible = true;
        m.leftArm.visible = true;
        m.rightLeg.visible = true;
        m.leftLeg.visible = true;
        m.jacket.visible = true;
        m.leftSleeve.visible = true;
        m.rightSleeve.visible = true;
        m.leftPants.visible = true;
        m.rightPants.visible = true;

        // MiniMe Hand: Hängt an der Hand und schwingt wie ein Pendel – KEIN Beinezappeln!
        float pendulum = (float) Math.sin(timeSec * 1.8f) * 0.06f + walkSwing * 0.5f;

        m.body.roll = 0.22f + pendulum * 0.6f;
        m.body.pitch = 0.04f + pendulum * 0.3f;
        m.body.yaw = -0.06f;

        // Rechter Arm: Hält die Hand des Spielers fest (matching Bild 3)
        m.rightArm.pitch = 2.82f + pendulum * 0.15f;
        m.rightArm.yaw = -0.22f;
        m.rightArm.roll = -0.32f + pendulum * 0.2f;

        // Linker Arm: Hängt entspannt herunter und wiegt sich sanft
        m.leftArm.pitch = 0.12f - pendulum * 1.2f;
        m.leftArm.yaw = 0.04f;
        m.leftArm.roll = 0.26f + (float) Math.cos(timeSec * 1.6f) * 0.04f;

        // Kopf: Schaut niedlich zum Spieler auf
        m.head.roll = 0.18f + pendulum * 0.3f;
        m.head.pitch = -0.12f;
        m.head.yaw = 0.06f;

        // Beine: Hängen vollkommen natürlich herunter und folgen nur dem sanften Pendelschwung
        m.rightLeg.pitch = 0.08f + pendulum * 0.4f;
        m.rightLeg.yaw = 0.05f;
        m.rightLeg.roll = 0.0f;

        m.leftLeg.pitch = -0.04f + pendulum * 0.4f;
        m.leftLeg.yaw = -0.05f;
        m.leftLeg.roll = 0.0f;
    }

    public static void poseChainChibiModel(PlayerEntityModel m) {
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        poseChainChibiModel(m, timeSec);
    }

    public static void poseChainChibiModel(PlayerEntityModel m, float timeSec) {
        if (m == null) return;
        m.head.visible = true;
        m.hat.visible = true;
        m.body.visible = true;
        m.rightArm.visible = true;
        m.leftArm.visible = true;
        m.rightLeg.visible = true;
        m.leftLeg.visible = true;
        m.jacket.visible = true;
        m.leftSleeve.visible = true;
        m.rightSleeve.visible = true;
        m.leftPants.visible = true;
        m.rightPants.visible = true;

        // MiniMe Chain: Wiegt sich als Kettenanhänger sanft mit der Brust – KEIN Beinezappeln!
        float chainSway = (float) Math.sin(timeSec * 1.6f) * 0.035f;
        m.body.roll = chainSway;
        m.body.pitch = 0.02f;
        m.body.yaw = 0.0f;

        // Beide Hände halten die Goldkette fest
        m.rightArm.pitch = 2.75f;
        m.rightArm.yaw = -0.10f;
        m.rightArm.roll = -0.32f + chainSway * 0.5f;

        m.leftArm.pitch = 2.75f;
        m.leftArm.yaw = 0.10f;
        m.leftArm.roll = 0.32f + chainSway * 0.5f;

        // Beine: Hängen ruhig wie ein echter Kettenanhänger nach unten
        m.rightLeg.pitch = 0.04f;
        m.rightLeg.yaw = 0.04f;
        m.rightLeg.roll = 0.0f;

        m.leftLeg.pitch = 0.04f;
        m.leftLeg.yaw = -0.04f;
        m.leftLeg.roll = 0.0f;

        // Kopf: Hängt aufrecht und schaut nach vorne
        m.head.pitch = -0.10f;
        m.head.yaw = (float) Math.sin(timeSec * 0.8f) * 0.04f;
        m.head.roll = chainSway * 0.5f;
    }

    public static Identifier getPetSkinDirect(String petType) {
        String custom = CosmeticsConfig.getAccessoryCustomPlayer(petType);
        if (custom != null && !custom.trim().isEmpty()) {
            return PlayerSkinResolver.resolve(custom);
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (SkinProtectModule.enabled) {
            return SkinProtectModule.getCurrentSkin().body().texturePath();
        }
        if (CaeserSkinManager.hasActiveOverride()) {
            SkinTextures customTex = CaeserSkinManager.getActiveSkinTextures();
            if (customTex != null && customTex.body() != null) {
                return customTex.body().texturePath();
            }
        }
        if (mc.player != null && mc.player.getSkin() != null && mc.player.getSkin().body() != null) {
            return mc.player.getSkin().body().texturePath();
        }
        return net.minecraft.client.util.DefaultSkinHelper.getTexture();
    }

    public static void renderCosmeticsDirect(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                             PlayerEntityModel playerModel, int light,
                                             String activeWings, String activeHead, String activePet) {
        initModels();

        // 1. Wings (anchored to torso)
        if ("angel".equalsIgnoreCase(activeWings) && goldWingsModel != null) {
            matrices.push();
            if (playerModel != null) playerModel.body.applyTransform(matrices);

            goldWingsModel.resetAllAnimations();
            float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
            float deg = timeSec * 100.0f;
            float flap1 = (float) Math.sin(Math.toRadians(deg - 50.0f)) * 8.5f;
            float flap2 = (float) Math.sin(Math.toRadians(deg - 100.0f)) * 34.0f;

            goldWingsModel.setBoneRotation("tl", 25.4876f, -51.1698f + flap1, -12.93082f);
            goldWingsModel.setBoneTranslation("tl", -2.0f, 5.0f, 1.0f);
            goldWingsModel.setBoneRotation("tll", 0.0f, 15.0f + flap2, 0.0f);

            goldWingsModel.setBoneRotation("tr", -25.4876f, 51.1698f - flap1, -12.93082f);
            goldWingsModel.setBoneTranslation("tr", -2.0f, 5.0f, -1.0f);
            goldWingsModel.setBoneRotation("trr", 0.0f, -15.0f - flap2, 0.0f);

            int frame = (int) ((System.currentTimeMillis() / 100L) % 15L);
            RenderLayer layer = RenderLayers.entityCutoutNoCull(GOLD_WINGS_TEXTURE);
            VertexConsumer consumer = vertexConsumers.getBuffer(layer);
            goldWingsModel.render(matrices.peek(), consumer, light, frame, 15);

            matrices.pop();
        } else if ("dragon".equalsIgnoreCase(activeWings) && dragonWingsModel != null) {
            matrices.push();
            if (playerModel != null) playerModel.body.applyTransform(matrices);

            dragonWingsModel.resetAllAnimations();
            float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
            float deg = timeSec * 100.0f;
            float flap1 = (float) Math.sin(Math.toRadians(deg - 50.0f)) * 21.25f;
            float flap2 = (float) Math.sin(Math.toRadians(deg - 100.0f)) * 29.75f;

            dragonWingsModel.setBoneRotation("L2", 0.0f, -30.0f - flap1, 0.0f);
            dragonWingsModel.setBoneRotation("L3", 0.0f, -flap2, 0.0f);
            dragonWingsModel.setBoneRotation("R2", 0.0f, 30.0f + flap1, 0.0f);
            dragonWingsModel.setBoneRotation("R3", 0.0f, flap2, 0.0f);

            int frame = (int) ((System.currentTimeMillis() / 120L) % 10L);
            RenderLayer layer = RenderLayers.entityCutoutNoCull(DRAGON_WINGS_TEXTURE);
            VertexConsumer consumer = vertexConsumers.getBuffer(layer);
            dragonWingsModel.render(matrices.peek(), consumer, light, frame, 10);

            matrices.pop();
        }

        // 2. Head Accessories (anchored to head)
        if ("halo".equalsIgnoreCase(activeHead) && angelHaloModel != null) {
            matrices.push();
            if (playerModel != null) playerModel.head.applyTransform(matrices);

            angelHaloModel.resetAllAnimations();
            float bob = (float) Math.sin(System.currentTimeMillis() * 0.0035) * 0.5f;
            angelHaloModel.setBoneTranslation("haloBase", 0.0f, bob, 0.0f);

            int frame = (int) ((System.currentTimeMillis() / 102L) % 16L);
            RenderLayer layer = RenderLayers.entityCutoutNoCull(ANGEL_HALO_TEXTURE);
            VertexConsumer consumer = vertexConsumers.getBuffer(layer);
            angelHaloModel.render(matrices.peek(), consumer, light, frame, 16);

            matrices.pop();
        } else if ("horns".equalsIgnoreCase(activeHead) && demonHornsModel != null) {
            matrices.push();
            if (playerModel != null) playerModel.head.applyTransform(matrices);
            matrices.translate(0.0f, -0.01f, -0.070f);

            demonHornsModel.resetAllAnimations();
            RenderLayer layer = RenderLayers.entityCutoutNoCull(DEMON_HORNS_TEXTURE);
            VertexConsumer consumer = vertexConsumers.getBuffer(layer);
            demonHornsModel.render(matrices.peek(), consumer, light, 0, 1);

            matrices.pop();
        }

        // 3. Accessories (Sit, Head, Hand, Chain) - Multi-equip in Live GUI Preview!
        if (sitChibiRoot != null) {
            java.util.Set<String> toRender = new java.util.LinkedHashSet<>();
            if (CosmeticsConfig.accessories != null && !CosmeticsConfig.accessories.isEmpty()) {
                toRender.addAll(CosmeticsConfig.accessories);
            }

            float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;

            for (String accId : toRender) {
                Identifier skinTexture = getPetSkinDirect(accId);
                RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);
                VertexConsumer consumer = vertexConsumers.getBuffer(layer);

                if ("minime_head".equalsIgnoreCase(accId)) {
                    if (headChibiModel == null) continue;
                    poseHeadChibiModel(headChibiModel, timeSec);
                    matrices.push();
                    if (playerModel != null) playerModel.head.applyTransform(matrices);
                    matrices.translate(0.0f, -0.74f, -0.02f);
                    matrices.scale(0.28f, 0.28f, 0.28f);
                    headChibiRoot.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
                    matrices.pop();
                } else if ("minime_hand".equalsIgnoreCase(accId)) {
                    if (handChibiModel == null) continue;
                    poseHangingChibiModel(handChibiModel, timeSec, 0.0f);
                    matrices.push();
                    if (playerModel != null) playerModel.leftArm.applyTransform(matrices);
                    matrices.translate(0.16f, 0.78f, 0.0f);
                    matrices.scale(0.28f, 0.28f, 0.28f);
                    handChibiRoot.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
                    matrices.pop();
                } else if ("minime_chain".equalsIgnoreCase(accId)) {
                    if (chainChibiModel == null) continue;
                    poseChainChibiModel(chainChibiModel, timeSec);
                    matrices.push();
                    if (playerModel != null) playerModel.body.applyTransform(matrices);
                    matrices.translate(0.0f, 0.30f, -0.155f);
                    matrices.scale(0.24f, 0.24f, 0.24f);
                    chainChibiRoot.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
                    matrices.pop();

                    // Render sleek metallic chain strands from collar to hands (matching NoRisk Bild 2)
                    matrices.push();
                    if (playerModel != null) playerModel.body.applyTransform(matrices);
                    RenderLayer chainLayer = RenderLayers.entityCutoutNoCull(GOLD_CHAIN_TEXTURE);
                    VertexConsumer chainConsumer = vertexConsumers.getBuffer(chainLayer);
                    drawNecklaceChain(matrices.peek(), chainConsumer, light);
                    matrices.pop();
                } else if ("minime_sit".equalsIgnoreCase(accId) || "self".equalsIgnoreCase(accId) || "custom".equalsIgnoreCase(accId)) {
                    if (sitChibiModel == null) continue;
                    poseChibiModel(sitChibiModel, timeSec);
                    matrices.push();
                    if (playerModel != null) playerModel.body.applyTransform(matrices);
                    matrices.translate(-0.40f, -0.195f, -0.02f);
                    matrices.scale(0.28f, 0.28f, 0.28f);
                    sitChibiRoot.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
                    matrices.pop();
                }
            }
        }
    }

    public static void renderCardCosmeticStandalone(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                     String cosmeticId, int light) {
        initModels();
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        float deg = timeSec * 100.0f;

        switch (cosmeticId.toLowerCase(java.util.Locale.ROOT)) {
            case "angel" -> {
                if (goldWingsModel == null) return;
                matrices.push();
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
                matrices.translate(0.0f, 0.92f, 0.0f);
                matrices.scale(1.12f, 1.12f, 1.12f);
                goldWingsModel.resetAllAnimations();
                float flap = (float) Math.sin(timeSec * 2.8f) * 8.0f;
                // Wings spread wide open horizontally facing camera (matching NoRisk)
                goldWingsModel.setBoneRotation("tl", 20.0f, -50.0f + flap, -10.0f);
                goldWingsModel.setBoneTranslation("tl", -2.0f, 5.0f, 1.0f);
                goldWingsModel.setBoneRotation("tll", 0.0f, 15.0f + flap * 0.5f, 0.0f);
                goldWingsModel.setBoneRotation("tr", -20.0f, 50.0f - flap, -10.0f);
                goldWingsModel.setBoneTranslation("tr", -2.0f, 5.0f, -1.0f);
                goldWingsModel.setBoneRotation("trr", 0.0f, -15.0f - flap * 0.5f, 0.0f);
                int frame = (int) ((System.currentTimeMillis() / 100L) % 15L);
                RenderLayer layer = RenderLayers.entityCutoutNoCull(GOLD_WINGS_TEXTURE);
                VertexConsumer consumer = vertexConsumers.getBuffer(layer);
                goldWingsModel.render(matrices.peek(), consumer, light, frame, 15);
                matrices.pop();
            }
            case "dragon" -> {
                if (dragonWingsModel == null) return;
                matrices.push();
                // Rotate 180° around Y so wings face forward towards camera!
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
                matrices.translate(0.0f, 0.92f, 0.0f);
                matrices.scale(1.12f, 1.12f, 1.12f);
                dragonWingsModel.resetAllAnimations();
                float flap = (float) Math.sin(timeSec * 2.8f) * 12.0f;
                dragonWingsModel.setBoneRotation("L2", 0.0f, -25.0f - flap, 0.0f);
                dragonWingsModel.setBoneRotation("L3", 0.0f, -15.0f - flap * 0.5f, 0.0f);
                dragonWingsModel.setBoneRotation("R2", 0.0f, 25.0f + flap, 0.0f);
                dragonWingsModel.setBoneRotation("R3", 0.0f, 15.0f + flap * 0.5f, 0.0f);
                int frame = (int) ((System.currentTimeMillis() / 120L) % 10L);
                RenderLayer layer = RenderLayers.entityCutoutNoCull(DRAGON_WINGS_TEXTURE);
                VertexConsumer consumer = vertexConsumers.getBuffer(layer);
                dragonWingsModel.render(matrices.peek(), consumer, light, frame, 10);
                matrices.pop();
            }
            case "halo" -> {
                if (angelHaloModel == null) return;
                matrices.push();
                matrices.translate(0.0f, 1.601f + 0.22f, 0.0f);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(24.0f));
                matrices.scale(2.35f, 2.35f, 2.35f);
                angelHaloModel.resetAllAnimations();
                float bob = (float) Math.sin(System.currentTimeMillis() * 0.0035) * 0.5f;
                angelHaloModel.setBoneTranslation("haloBase", 0.0f, bob, 0.0f);
                int frame = (int) ((System.currentTimeMillis() / 102L) % 16L);
                RenderLayer layer = RenderLayers.entityCutoutNoCull(ANGEL_HALO_TEXTURE);
                VertexConsumer consumer = vertexConsumers.getBuffer(layer);
                angelHaloModel.render(matrices.peek(), consumer, light, frame, 16);
                matrices.pop();
            }
            case "horns" -> {
                if (demonHornsModel == null) return;
                matrices.push();
                matrices.translate(0.0f, 1.601f + 0.20f, 0.0f);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-15.0f));
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(12.0f));
                matrices.scale(2.20f, 2.20f, 2.20f);
                demonHornsModel.resetAllAnimations();
                RenderLayer layer = RenderLayers.entityCutoutNoCull(DEMON_HORNS_TEXTURE);
                VertexConsumer consumer = vertexConsumers.getBuffer(layer);
                demonHornsModel.render(matrices.peek(), consumer, light, 0, 1);
                matrices.pop();
            }
            case "self", "minime_sit", "custom" -> {
                if (sitChibiModel == null) return;
                matrices.push();
                matrices.translate(0.0f, 0.52f, 0.0f);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-20.0f));
                matrices.scale(1.05f, 1.05f, 1.05f);
                poseChibiModel(sitChibiModel, timeSec);
                Identifier skinTexture = getPetSkinDirect(cosmeticId);
                RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);
                VertexConsumer consumer = vertexConsumers.getBuffer(layer);
                sitChibiModel.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, -1);
                matrices.pop();
            }
            case "minime_head" -> {
                if (headChibiModel == null) return;
                matrices.push();
                matrices.translate(0.0f, 0.52f, 0.0f);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-15.0f));
                matrices.scale(1.05f, 1.05f, 1.05f);
                poseHeadChibiModel(headChibiModel, timeSec);
                Identifier skinTexture = getPetSkinDirect(cosmeticId);
                RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);
                VertexConsumer consumer = vertexConsumers.getBuffer(layer);
                headChibiModel.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, -1);
                matrices.pop();
            }
            case "minime_hand" -> {
                if (handChibiModel == null) return;
                matrices.push();
                matrices.translate(-0.04f, 0.50f, 0.0f);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-15.0f));
                matrices.scale(1.05f, 1.05f, 1.05f);
                poseHangingChibiModel(handChibiModel, timeSec, 0.0f);
                Identifier skinTexture = getPetSkinDirect(cosmeticId);
                RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);
                VertexConsumer consumer = vertexConsumers.getBuffer(layer);
                handChibiModel.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, -1);
                matrices.pop();
            }
            case "minime_chain" -> {
                if (chainChibiModel == null) return;
                matrices.push();
                matrices.translate(0.0f, 0.40f, 0.0f);
                matrices.scale(0.95f, 0.95f, 0.95f);
                poseChainChibiModel(chainChibiModel, timeSec);

                Identifier skinTexture = getPetSkinDirect(cosmeticId);
                RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);
                VertexConsumer consumer = vertexConsumers.getBuffer(layer);
                chainChibiModel.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, -1);

                // Standalone chain strands extending upwards from hands (matching NoRisk Bild 2!)
                RenderLayer chainLayer = RenderLayers.entityCutoutNoCull(GOLD_CHAIN_TEXTURE);
                VertexConsumer chainConsumer = vertexConsumers.getBuffer(chainLayer);
                drawCardChainStrands(matrices.peek(), chainConsumer, light);
                matrices.pop();
            }
        }
    }
}
