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

    // Models
    public static BedrockCosmeticModel goldWingsModel;
    public static BedrockCosmeticModel dragonWingsModel;
    public static BedrockCosmeticModel angelHaloModel;
    public static BedrockCosmeticModel demonHornsModel;

    public static ModelPart staticChibiRoot;
    public static PlayerEntityModel staticChibiModel;

    private final ModelPart chibiRoot;
    private final PlayerEntityModel chibiModel;

    private static String cachedCustomName = null;
    private static Supplier<SkinTextures> cachedCustomSupplier = null;

    public CaeserCosmeticsRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        initModels();
        this.chibiRoot = staticChibiRoot;
        this.chibiModel = staticChibiModel;
    }

    public static synchronized void initModels() {
        if (staticChibiRoot == null) {
            staticChibiRoot = TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64).createModel();
            staticChibiModel = new PlayerEntityModel(staticChibiRoot, false);
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

        // 3. Shoulder Companion
        if ("self".equalsIgnoreCase(CosmeticsConfig.pet) || "custom".equalsIgnoreCase(CosmeticsConfig.pet)) {
            renderShoulderPet(matrices, queue, light, state);
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

        demonHornsModel.resetAllAnimations();
        RenderLayer layer = RenderLayers.entityCutoutNoCull(DEMON_HORNS_TEXTURE);
        queue.getBatchingQueue(0).submitCustom(matrices, layer, (entry, consumer) -> {
            demonHornsModel.render(entry, consumer, light, 0, 1);
        });

        matrices.pop();
    }

    // ==========================================
    // SHOULDER PET (Textured Mini-Player with Skin)
    // ==========================================
    private void renderShoulderPet(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                  int light, PlayerEntityRenderState state) {
        poseChibi();

        matrices.push();
        this.getContextModel().body.applyTransform(matrices);

        float hoverBob = (float) Math.sin((System.currentTimeMillis() % 100000) * 0.0035) * 0.010f;

        // Position perched neatly on the right shoulder:
        // Torso top is y = 0.0f. Right shoulder center is x = -0.34f, z = -0.01f.
        matrices.translate(-0.34f, -0.215f + hoverBob, -0.01f);
        matrices.scale(0.30f, 0.30f, 0.30f);

        Identifier skinTexture = getPetSkin(state);
        RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);
        queue.submitModelPart(chibiRoot, matrices, layer, light, OverlayTexture.DEFAULT_UV, null);

        matrices.pop();
    }

    private void poseChibi() {
        // Sitting cute chibi pose
        chibiModel.rightLeg.pitch = -1.5707963f; // 90° forward sitting
        chibiModel.rightLeg.yaw = 0.14f;
        chibiModel.rightLeg.roll = 0.0f;

        chibiModel.leftLeg.pitch = -1.5707963f;
        chibiModel.leftLeg.yaw = -0.14f;
        chibiModel.leftLeg.roll = 0.0f;

        chibiModel.rightArm.pitch = -0.65f;
        chibiModel.rightArm.yaw = -0.08f;
        chibiModel.rightArm.roll = 0.12f;

        chibiModel.leftArm.pitch = -0.65f;
        chibiModel.leftArm.yaw = 0.08f;
        chibiModel.leftArm.roll = -0.12f;

        chibiModel.head.pitch = -0.06f;
        chibiModel.head.yaw = 0.0f;
        chibiModel.head.roll = 0.08f; // Cute slight head tilt
    }

    private static Identifier getPetSkin(PlayerEntityRenderState state) {
        if ("custom".equalsIgnoreCase(CosmeticsConfig.pet)) {
            return PlayerSkinResolver.resolve(CosmeticsConfig.petCustomPlayer);
        }
        if (SkinProtectModule.enabled) {
            return SkinProtectModule.getCurrentSkin().body().texturePath();
        }
        if (state != null && state.skinTextures != null && state.skinTextures.body() != null) {
            return state.skinTextures.body().texturePath();
        }
        return net.minecraft.client.util.DefaultSkinHelper.getTexture();
    }

    public static void poseChibiModel(PlayerEntityModel m) {
        if (m == null) return;
        m.rightLeg.pitch = -1.5707963f;
        m.rightLeg.yaw = 0.14f;
        m.rightLeg.roll = 0.0f;

        m.leftLeg.pitch = -1.5707963f;
        m.leftLeg.yaw = -0.14f;
        m.leftLeg.roll = 0.0f;

        m.rightArm.pitch = -0.65f;
        m.rightArm.yaw = -0.08f;
        m.rightArm.roll = 0.12f;

        m.leftArm.pitch = -0.65f;
        m.leftArm.yaw = 0.08f;
        m.leftArm.roll = -0.12f;

        m.head.pitch = -0.06f;
        m.head.yaw = 0.0f;
        m.head.roll = 0.08f;
    }

    public static Identifier getPetSkinDirect(String petType) {
        if ("custom".equalsIgnoreCase(petType)) {
            return PlayerSkinResolver.resolve(CosmeticsConfig.petCustomPlayer);
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (SkinProtectModule.enabled) {
            return SkinProtectModule.getCurrentSkin().body().texturePath();
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

            demonHornsModel.resetAllAnimations();
            RenderLayer layer = RenderLayers.entityCutoutNoCull(DEMON_HORNS_TEXTURE);
            VertexConsumer consumer = vertexConsumers.getBuffer(layer);
            demonHornsModel.render(matrices.peek(), consumer, light, 0, 1);

            matrices.pop();
        }

        // 3. Pet on Shoulder
        if (("self".equalsIgnoreCase(activePet) || "custom".equalsIgnoreCase(activePet)) && staticChibiRoot != null) {
            poseChibiModel(staticChibiModel);

            matrices.push();
            if (playerModel != null) playerModel.body.applyTransform(matrices);

            float hoverBob = (float) Math.sin((System.currentTimeMillis() % 100000) * 0.0035) * 0.010f;
            matrices.translate(-0.34f, -0.215f + hoverBob, -0.01f);
            matrices.scale(0.30f, 0.30f, 0.30f);

            Identifier skinTexture = getPetSkinDirect(activePet);
            RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);
            VertexConsumer consumer = vertexConsumers.getBuffer(layer);
            staticChibiRoot.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);

            matrices.pop();
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
                // Cancel default skin Y translate (-1.601f) and center wings
                matrices.translate(0.0f, 1.601f - 0.38f, 0.0f);
                matrices.scale(1.50f, 1.50f, 1.50f);
                goldWingsModel.resetAllAnimations();
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
            }
            case "dragon" -> {
                if (dragonWingsModel == null) return;
                matrices.push();
                matrices.translate(0.0f, 1.601f - 0.22f, 0.0f);
                matrices.scale(1.45f, 1.45f, 1.45f);
                dragonWingsModel.resetAllAnimations();
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
            case "halo" -> {
                if (angelHaloModel == null) return;
                matrices.push();
                // Cancel default skin translate and center halo
                matrices.translate(0.0f, 1.601f + 0.60f, 0.0f);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(20.0f));
                matrices.scale(2.50f, 2.50f, 2.50f);
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
                // Cancel default skin translate and center demon horns
                matrices.translate(0.0f, 1.601f + 0.65f, 0.0f);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(14.0f));
                matrices.scale(2.05f, 2.05f, 2.05f);
                demonHornsModel.resetAllAnimations();
                RenderLayer layer = RenderLayers.entityCutoutNoCull(DEMON_HORNS_TEXTURE);
                VertexConsumer consumer = vertexConsumers.getBuffer(layer);
                demonHornsModel.render(matrices.peek(), consumer, light, 0, 1);
                matrices.pop();
            }
            case "self", "custom" -> {
                if (staticChibiRoot == null) return;
                matrices.push();
                matrices.translate(0.0f, 1.601f - 0.38f, 0.0f);
                matrices.scale(1.15f, 1.15f, 1.15f);
                poseChibiModel(staticChibiModel);
                Identifier skinTexture = getPetSkinDirect(cosmeticId);
                RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);
                VertexConsumer consumer = vertexConsumers.getBuffer(layer);
                staticChibiRoot.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
                matrices.pop();
            }
        }
    }
}
