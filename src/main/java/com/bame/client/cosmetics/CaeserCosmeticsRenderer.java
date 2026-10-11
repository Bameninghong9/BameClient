package com.bame.client.cosmetics;

import com.bame.client.module.SkinProtectModule;
import com.mojang.authlib.GameProfile;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class CaeserCosmeticsRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    // Textures
    public static final Identifier GOLD_WINGS_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/wings/gold_wings.png");
    public static final Identifier DRAGON_WINGS_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/wings/dragon_wings.png");
    public static final Identifier ANGEL_HALO_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/hat/angel_halo.png");
    public static final Identifier DEMON_HORNS_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/hat/demon_horns.png");
    public static final Identifier WITCH_HAT_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/hat/witch_hat.png");
    public static final Identifier STRAW_HAT_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/hat/straw_hat.png");
    public static final Identifier SUNGLASSES_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/face/sunglasses.png");
    public static final Identifier SKULL_MASK_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/face/skull_mask.png");
    public static final Identifier GOLD_CHAIN_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/accessory/gold_chain.png");
    public static final Identifier BABY_DRAGON_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/pets/baby_dragon.png");
    public static final Identifier JELLY_SLIME_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/pets/jelly_slime.png");
    public static final Identifier MINIME_HAND_DEFAULT_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/accessory/minimehand.png");

    // Models
    public static BedrockCosmeticModel goldWingsModel;
    public static BedrockCosmeticModel dragonWingsModel;
    public static BedrockCosmeticModel angelHaloModel;
    public static BedrockCosmeticModel demonHornsModel;
    public static BedrockCosmeticModel witchHatModel;
    public static BedrockCosmeticModel strawHatModel;
    public static BedrockCosmeticModel sunglassesModel;
    public static BedrockCosmeticModel skullMaskModel;
    public static BedrockCosmeticModel miniMeHandModel;

    // Independent Chibi models for each accessory so they NEVER overwrite each other's pose!
    public static ModelPart sitChibiRoot;
    public static PlayerEntityModel sitChibiModel;

    public static ModelPart headChibiRoot;
    public static PlayerEntityModel headChibiModel;

    public static ModelPart handChibiRoot;
    public static PlayerEntityModel handChibiModel;

    public static ModelPart handRightChibiRoot;
    public static PlayerEntityModel handRightChibiModel;

    public static ModelPart chainChibiRoot;
    public static PlayerEntityModel chainChibiModel;

    // Dedicated 3D Pet Models
    public static ModelPart babyDragonRoot;
    public static ModelPart babyDragonBody;
    public static ModelPart babyDragonHead;
    public static ModelPart babyDragonWingLeft;
    public static ModelPart babyDragonWingRight;
    public static ModelPart babyDragonTail;

    public static ModelPart jellySlimeRoot;
    public static ModelPart jellySlimeOuter;
    public static ModelPart jellySlimeCore;

    public static long lastSmokeTime = 0L;

    public static class PetStateManager {
        public static double lastX, lastY, lastZ;
        public static float lastYaw, lastPitch;
        public static long lastMoveTime = System.currentTimeMillis();
        public static boolean wasOnGround = true;
        public static float landingImpact = 0.0f;
        public static long lastLandingTime = 0;

        public static void tick() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;

            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();
            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();
            boolean onGround = mc.player.isOnGround();

            double distSq = (x - lastX) * (x - lastX) + (y - lastY) * (y - lastY) + (z - lastZ) * (z - lastZ);
            float rotDiff = Math.abs(yaw - lastYaw) + Math.abs(pitch - lastPitch);

            if (distSq > 0.0001 || rotDiff > 0.5f) {
                lastMoveTime = System.currentTimeMillis();
                lastX = x;
                lastY = y;
                lastZ = z;
                lastYaw = yaw;
                lastPitch = pitch;
            }

            if (!wasOnGround && onGround) {
                lastLandingTime = System.currentTimeMillis();
                landingImpact = (float) Math.min((double) mc.player.fallDistance * 0.25 + 0.35, 0.70);
            }
            wasOnGround = onGround;
        }

        public static boolean isAfk() {
            return (System.currentTimeMillis() - lastMoveTime) > 4500L;
        }
    }

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
        if (handRightChibiRoot == null) {
            handRightChibiRoot = TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64).createModel();
            handRightChibiModel = new PlayerEntityModel(handRightChibiRoot, false);
            poseHangingRightChibiModel(handRightChibiModel, 0.0f, 0.0f);
        }
        if (chainChibiRoot == null) {
            chainChibiRoot = TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64).createModel();
            chainChibiModel = new PlayerEntityModel(chainChibiRoot, false);
            poseChainChibiModel(chainChibiModel);
        }
        if (babyDragonRoot == null) {
            ModelData data = new ModelData();
            ModelPartData root = data.getRoot();
            ModelPartData body = root.addChild("body",
                ModelPartBuilder.create()
                    .uv(0, 16).cuboid(-2.5f, -2.5f, -3.5f, 5.0f, 5.0f, 7.0f)
                    .uv(16, 0).cuboid(-2.0f, 1.6f, -3.0f, 4.0f, 1.0f, 6.0f),
                ModelTransform.origin(0.0f, 0.0f, 0.0f)
            );
            body.addChild("head",
                ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-2.5f, -2.5f, -4.5f, 5.0f, 5.0f, 5.0f)
                    .uv(0, 10).cuboid(-1.5f, -0.5f, -7.0f, 3.0f, 2.5f, 3.0f)
                    .uv(32, 0).cuboid(-2.2f, -5.0f, -1.5f, 1.2f, 3.0f, 2.0f)
                    .uv(32, 0).cuboid(1.0f, -5.0f, -1.5f, 1.2f, 3.0f, 2.0f),
                ModelTransform.origin(0.0f, -1.5f, -3.0f)
            );
            body.addChild("wing_left",
                ModelPartBuilder.create()
                    .uv(0, 32).cuboid(0.0f, -0.5f, -2.0f, 7.0f, 0.5f, 4.0f),
                ModelTransform.origin(2.2f, -1.8f, -1.0f)
            );
            body.addChild("wing_right",
                ModelPartBuilder.create()
                    .uv(0, 32).cuboid(-7.0f, -0.5f, -2.0f, 7.0f, 0.5f, 4.0f),
                ModelTransform.origin(-2.2f, -1.8f, -1.0f)
            );
            body.addChild("tail",
                ModelPartBuilder.create()
                    .uv(32, 32).cuboid(-1.0f, -1.0f, 0.0f, 2.0f, 2.0f, 5.0f)
                    .uv(32, 40).cuboid(-1.5f, -0.2f, 4.5f, 3.0f, 0.5f, 3.0f),
                ModelTransform.origin(0.0f, 0.5f, 3.5f)
            );
            body.addChild("paw_fl", ModelPartBuilder.create().uv(48, 0).cuboid(-0.8f, 0.0f, -0.8f, 1.6f, 2.5f, 1.6f), ModelTransform.origin(2.0f, 2.0f, -2.2f));
            body.addChild("paw_fr", ModelPartBuilder.create().uv(48, 0).cuboid(-0.8f, 0.0f, -0.8f, 1.6f, 2.5f, 1.6f), ModelTransform.origin(-2.0f, 2.0f, -2.2f));
            body.addChild("paw_bl", ModelPartBuilder.create().uv(48, 0).cuboid(-0.8f, 0.0f, -0.8f, 1.6f, 2.5f, 1.6f), ModelTransform.origin(2.0f, 2.0f, 2.2f));
            body.addChild("paw_br", ModelPartBuilder.create().uv(48, 0).cuboid(-0.8f, 0.0f, -0.8f, 1.6f, 2.5f, 1.6f), ModelTransform.origin(-2.0f, 2.0f, 2.2f));

            babyDragonRoot = TexturedModelData.of(data, 64, 64).createModel();
            babyDragonBody = babyDragonRoot.getChild("body");
            babyDragonHead = babyDragonBody.getChild("head");
            babyDragonWingLeft = babyDragonBody.getChild("wing_left");
            babyDragonWingRight = babyDragonBody.getChild("wing_right");
            babyDragonTail = babyDragonBody.getChild("tail");
        }
        if (jellySlimeRoot == null) {
            ModelData data = new ModelData();
            ModelPartData root = data.getRoot();
            root.addChild("outer",
                ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-3.5f, -3.5f, -3.5f, 7.0f, 7.0f, 7.0f),
                ModelTransform.origin(0.0f, 0.0f, 0.0f)
            );
            root.addChild("core",
                ModelPartBuilder.create()
                    .uv(32, 0).cuboid(-2.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f),
                ModelTransform.origin(0.0f, 0.0f, 0.0f)
            );
            jellySlimeRoot = TexturedModelData.of(data, 64, 64).createModel();
            jellySlimeOuter = jellySlimeRoot.getChild("outer");
            jellySlimeCore = jellySlimeRoot.getChild("core");
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
        if (witchHatModel == null) {
            witchHatModel = BedrockCosmeticModel.load("assets/bameclient/models/cosmetics/hat/witch_hat.geo.json");
        }
        if (strawHatModel == null) {
            strawHatModel = BedrockCosmeticModel.load("assets/bameclient/models/cosmetics/hat/straw_hat.geo.json");
        }
        if (sunglassesModel == null) {
            sunglassesModel = BedrockCosmeticModel.load("assets/bameclient/models/cosmetics/face/sunglasses.geo.json");
        }
        if (skullMaskModel == null) {
            skullMaskModel = BedrockCosmeticModel.load("assets/bameclient/models/cosmetics/face/skull_mask.geo.json");
        }
        if (miniMeHandModel == null) {
            miniMeHandModel = BedrockCosmeticModel.load("assets/bameclient/models/cosmetics/accessory/minimehand.geo.json");
        }
    }

    public static String getPlayerUsername(PlayerEntityRenderState state) {
        if (state == null) return null;
        if (state instanceof com.bame.client.emotes.EmotePreviewHolder holder && holder.caeser$getPlayerUsername() != null) {
            return holder.caeser$getPlayerUsername();
        }
        if (state.playerName != null) {
            return state.playerName.getString();
        }
        return null;
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Render cosmetics on the local player OR remote Caeser Client players
        boolean isLocal = (state.id == mc.player.getId());
        String rawUsername = getPlayerUsername(state);
        if (!isLocal && rawUsername != null && mc.getSession() != null) {
            String cleanStateName = CaeserPlayerSyncManager.cleanUsername(rawUsername);
            String cleanLocalName = CaeserPlayerSyncManager.cleanUsername(mc.getSession().getUsername());
            if (cleanStateName != null && cleanStateName.equalsIgnoreCase(cleanLocalName)) {
                isLocal = true;
            }
        }

        String wings = null;
        String head = null;
        String face = null;
        String pet = null;
        Set<String> accessories = null;

        if (isLocal) {
            PetStateManager.tick();
            CosmeticsConfig.checkReload();
            wings = CosmeticsConfig.wings;
            head = CosmeticsConfig.head;
            face = CosmeticsConfig.face;
            pet = CosmeticsConfig.pet;
            accessories = CosmeticsConfig.accessories;
        } else {
            CaeserPlayerSyncManager.PlayerCosmeticsData remote = CaeserPlayerSyncManager.getCosmetics(rawUsername);
            if (remote != null) {
                wings = remote.wings;
                head = remote.head;
                face = remote.face;
                pet = remote.pet;
                accessories = remote.accessories;
            }
        }

        if (wings != null || head != null || face != null || pet != null || (accessories != null && !accessories.isEmpty())) {
            boolean inFirstPerson = isLocal && (mc.options != null && mc.options.getPerspective().isFirstPerson());
            boolean renderHead = this.getContextModel().head.visible && !inFirstPerson;
            boolean renderBody = this.getContextModel().body.visible && !inFirstPerson;

            // 1. Wings (nur wenn Körper sichtbar und nicht in Ego-Perspektive)
            if (renderBody) {
                if ("angel".equalsIgnoreCase(wings)) {
                    renderAngelWings(matrices, queue, light);
                } else if ("dragon".equalsIgnoreCase(wings)) {
                    renderDragonWings(matrices, queue, light);
                }
            }

            // 2. Head Accessories (Halo, Horns, Witch Hat, Straw Hat) - nicht in Ego-Perspektive!
            if (renderHead) {
                if ("halo".equalsIgnoreCase(head)) {
                    renderHalo(matrices, queue, light);
                } else if ("horns".equalsIgnoreCase(head)) {
                    renderDemonHorns(matrices, queue, light);
                } else if ("witch_hat".equalsIgnoreCase(head)) {
                    renderHeadModel(matrices, queue, light, witchHatModel, WITCH_HAT_TEXTURE);
                } else if ("straw_hat".equalsIgnoreCase(head)) {
                    renderHeadModel(matrices, queue, light, strawHatModel, STRAW_HAT_TEXTURE);
                }
            }

            // 2b. Gesichts-Cosmetics (Sunglasses, Skull Mask) - nicht in Ego-Perspektive!
            if (renderHead) {
                if ("sunglasses".equalsIgnoreCase(face)) {
                    renderHeadModel(matrices, queue, light, sunglassesModel, SUNGLASSES_TEXTURE);
                } else if ("skull_mask".equalsIgnoreCase(face)) {
                    renderHeadModel(matrices, queue, light, skullMaskModel, SKULL_MASK_TEXTURE);
                }
            }

            // 3. Accessories & Pets - Multi-equip enabled (nicht in Ego-Perspektive)!
            if (!inFirstPerson) {
                boolean hasSit = isLocal ? CosmeticsConfig.isAccessoryEquipped("minime_sit") : ((accessories != null && accessories.contains("minime_sit")) || "minime_sit".equalsIgnoreCase(pet));
                boolean hasHead = isLocal ? CosmeticsConfig.isAccessoryEquipped("minime_head") : ((accessories != null && accessories.contains("minime_head")) || "minime_head".equalsIgnoreCase(pet));
                boolean hasHand = isLocal ? CosmeticsConfig.isAccessoryEquipped("minime_hand") : ((accessories != null && accessories.contains("minime_hand")) || "minime_hand".equalsIgnoreCase(pet));
                boolean hasChain = isLocal ? CosmeticsConfig.isAccessoryEquipped("minime_chain") : ((accessories != null && accessories.contains("minime_chain")) || "minime_chain".equalsIgnoreCase(pet));
                boolean hasDragon = isLocal ? CosmeticsConfig.isAccessoryEquipped("baby_dragon") : ((accessories != null && accessories.contains("baby_dragon")) || "baby_dragon".equalsIgnoreCase(pet));
                boolean hasSlime = isLocal ? CosmeticsConfig.isAccessoryEquipped("jelly_slime") : ((accessories != null && accessories.contains("jelly_slime")) || "jelly_slime".equalsIgnoreCase(pet));

                if (hasSit) renderShoulderPet(matrices, queue, light, state);
                if (hasHead && renderHead) renderHeadPet(matrices, queue, light, state);
                if (hasHand) renderHandPet(matrices, queue, light, state, limbAngle, limbDistance);
                if (hasChain) renderChainPet(matrices, queue, light, state);
                if (hasDragon) renderBabyDragon(matrices, queue, light, state);
                if (hasSlime) renderJellySlime(matrices, queue, light, state);
            }
        }

        // 4. Active Emote Props (for local AND remote players!)
        com.bame.client.emotes.EmotePropFeatureRenderer.renderDirectProp(matrices, queue, light, state);
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
    // Statische Kopf-Modelle (Hüte, Brille, Maske): im Kopf-Bone, folgen Drehung, Nicken und Schleichen
    // ==========================================
    private void renderHeadModel(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                 BedrockCosmeticModel model, Identifier texture) {
        if (model == null) return;
        matrices.push();
        this.getContextModel().head.applyTransform(matrices);
        if (model == sunglassesModel) {
            matrices.translate(0.0f, 0.11f, 0.0f);
        }
        model.resetAllAnimations();
        RenderLayer layer = RenderLayers.entityCutoutNoCull(texture);
        queue.getBatchingQueue(0).submitCustom(matrices, layer, (entry, consumer) -> {
            model.render(entry, consumer, light, 0, 1);
        });
        matrices.pop();
    }

    private static void renderHeadModelDirect(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                              PlayerEntityModel playerModel, int light,
                                              BedrockCosmeticModel model, Identifier texture) {
        if (model == null) return;
        matrices.push();
        if (playerModel != null) playerModel.head.applyTransform(matrices);
        if (model == sunglassesModel) {
            matrices.translate(0.0f, 0.11f, 0.0f);
        }
        model.resetAllAnimations();
        model.render(matrices.peek(), vertexConsumers.getBuffer(RenderLayers.entityCutoutNoCull(texture)), light, 0, 1);
        matrices.pop();
    }

    // Karten-Vorschau: Modell allein, leicht gedreht, auf Kartengröße skaliert
    private static void renderCardHeadModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                            BedrockCosmeticModel model, Identifier texture,
                                            float translateY, float pitch, float yaw, float scale) {
        if (model == null) return;
        matrices.push();
        matrices.translate(0.0f, translateY, 0.0f);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.scale(scale, scale, scale);
        model.resetAllAnimations();
        model.render(matrices.peek(), vertexConsumers.getBuffer(RenderLayers.entityCutoutNoCull(texture)), light, 0, 1);
        matrices.pop();
    }

    // ==========================================
    // COMPANIONS / PETS (MiniMe Sit, Head, Hand, Chain, Baby Dragon, Jelly Slime)
    // ==========================================
    private void renderShoulderPet(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                  int light, PlayerEntityRenderState state) {
        if (sitChibiRoot == null) return;
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        poseChibiModel(sitChibiModel, timeSec);

        String side = CosmeticsConfig.getAccessorySide("minime_sit");
        boolean renderRight = "right".equalsIgnoreCase(side) || "both".equalsIgnoreCase(side);
        boolean renderLeft = "left".equalsIgnoreCase(side) || "both".equalsIgnoreCase(side);

        Identifier skinTexture = getPetSkin("minime_sit", state);
        RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);

        if (renderRight) {
            matrices.push();
            this.getContextModel().body.applyTransform(matrices);
            matrices.translate(-0.40f, -0.195f, -0.02f);
            matrices.scale(0.28f, 0.28f, 0.28f);
            queue.submitModelPart(sitChibiRoot, matrices, layer, light, OverlayTexture.DEFAULT_UV, null);
            matrices.pop();
        }

        if (renderLeft) {
            matrices.push();
            this.getContextModel().body.applyTransform(matrices);
            matrices.translate(0.40f, -0.195f, -0.02f);
            matrices.scale(0.28f, 0.28f, 0.28f);
            queue.submitModelPart(sitChibiRoot, matrices, layer, light, OverlayTexture.DEFAULT_UV, null);
            matrices.pop();
        }
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
        if (miniMeHandModel == null) return;
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        float walkSwing = (limbDistance > 0.01f) ? (float) Math.sin(limbAngle * 0.8f) * Math.min(limbDistance, 1.0f) * 0.35f : 0.0f;

        String side = CosmeticsConfig.getAccessorySide("minime_hand");
        boolean renderLeft = "left".equalsIgnoreCase(side) || "both".equalsIgnoreCase(side);
        boolean renderRight = "right".equalsIgnoreCase(side) || "both".equalsIgnoreCase(side);

        Identifier skinTexture = getPetSkin("minime_hand", state);
        RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);

        if (renderLeft) {
            matrices.push();
            this.getContextModel().leftArm.applyTransform(matrices);
            matrices.translate(-0.3125f, -0.125f, 0.0f);
            miniMeHandModel.resetAllAnimations();
            applyMiniMeHandAnimation(miniMeHandModel, timeSec, walkSwing);
            queue.getBatchingQueue(0).submitCustom(matrices, layer, (entry, consumer) -> {
                miniMeHandModel.render(entry, consumer, light, 0, 1, false);
            });
            matrices.pop();
        }

        if (renderRight) {
            matrices.push();
            this.getContextModel().rightArm.applyTransform(matrices);
            matrices.translate(0.3125f, -0.125f, 0.0f);
            miniMeHandModel.resetAllAnimations();
            applyMiniMeHandAnimation(miniMeHandModel, timeSec, walkSwing);
            queue.getBatchingQueue(0).submitCustom(matrices, layer, (entry, consumer) -> {
                miniMeHandModel.render(entry, consumer, light, 0, 1, true);
            });
            matrices.pop();
        }
    }

    private void renderBabyDragon(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                  int light, PlayerEntityRenderState state) {
        if (babyDragonRoot == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        boolean afk = PetStateManager.isAfk();
        poseBabyDragon(timeSec, afk);

        // Smoke puff logic for baby dragon (when not AFK)
        if (!afk && mc.world != null && mc.player != null) {
            float sneezeCycle = (timeSec % 4.0f);
            if (sneezeCycle >= 3.6f && sneezeCycle <= 3.8f) {
                long now = System.currentTimeMillis();
                if (now - lastSmokeTime > 120L) {
                    lastSmokeTime = now;
                    double sx = mc.player.getX() + (mc.player.getRandom().nextFloat() - 0.5f) * 0.15;
                    double sy = mc.player.getY() + mc.player.getStandingEyeHeight() - 0.2;
                    double sz = mc.player.getZ() + (mc.player.getRandom().nextFloat() - 0.5f) * 0.15;
                    double vx = (mc.player.getRandom().nextFloat() - 0.5f) * 0.02;
                    double vy = 0.03 + mc.player.getRandom().nextFloat() * 0.03;
                    double vz = (mc.player.getRandom().nextFloat() - 0.5f) * 0.02;
                    mc.world.addParticleClient(ParticleTypes.SMOKE, sx, sy, sz, vx, vy, vz);
                }
            }
        }

        String placement = CosmeticsConfig.getPetPlacement("baby_dragon");
        matrices.push();
        applyPlacementTransform(matrices, this.getContextModel(), placement, timeSec);
        matrices.scale(0.26f, 0.26f, 0.26f);

        RenderLayer layer = RenderLayers.entityCutoutNoCull(BABY_DRAGON_TEXTURE);
        queue.submitModelPart(babyDragonRoot, matrices, layer, light, OverlayTexture.DEFAULT_UV, null);

        if (afk) {
            renderFloatingZzz(matrices, queue, light, timeSec);
        }

        matrices.pop();
    }

    private void renderJellySlime(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                  int light, PlayerEntityRenderState state) {
        if (jellySlimeRoot == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        boolean afk = PetStateManager.isAfk();
        float vy = (mc.player != null) ? (float) mc.player.getVelocity().y : 0.0f;
        float timeSinceLand = (System.currentTimeMillis() - PetStateManager.lastLandingTime) / 1000.0f;
        float[] scales = calculateJellyScales(timeSec, afk, vy, timeSinceLand);

        String placement = CosmeticsConfig.getPetPlacement("jelly_slime");
        matrices.push();
        applyPlacementTransform(matrices, this.getContextModel(), placement, timeSec);
        matrices.scale(0.28f * scales[0], 0.28f * scales[1], 0.28f * scales[2]);

        RenderLayer layer = RenderLayers.entityTranslucent(JELLY_SLIME_TEXTURE);
        queue.submitModelPart(jellySlimeRoot, matrices, layer, light, OverlayTexture.DEFAULT_UV, null);

        if (afk) {
            renderFloatingZzz(matrices, queue, light, timeSec);
        }

        matrices.pop();
    }

    public static void applyPlacementTransform(MatrixStack matrices, PlayerEntityModel playerModel,
                                               String placement, float timeSec) {
        switch (placement != null ? placement.toLowerCase(java.util.Locale.ROOT) : "") {
            case "left_shoulder" -> {
                if (playerModel != null) playerModel.body.applyTransform(matrices);
                matrices.translate(0.38f, -0.22f, -0.02f);
            }
            case "head" -> {
                if (playerModel != null) playerModel.head.applyTransform(matrices);
                matrices.translate(0.0f, -0.70f, -0.02f);
            }
            case "floating" -> {
                if (playerModel != null) playerModel.body.applyTransform(matrices);
                float bob = (float) Math.sin(timeSec * 2.2f) * 0.05f;
                matrices.translate(-0.52f, -0.32f + bob, 0.28f);
            }
            default -> { // right_shoulder
                if (playerModel != null) playerModel.body.applyTransform(matrices);
                matrices.translate(-0.38f, -0.22f, -0.02f);
            }
        }
    }

    private void renderFloatingZzz(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, float timeSec) {
        RenderLayer layer = RenderLayers.entityTranslucent(JELLY_SLIME_TEXTURE);
        queue.getBatchingQueue(0).submitCustom(matrices, layer, (entry, consumer) -> {
            for (int i = 0; i < 3; i++) {
                float phase = ((timeSec * 0.7f) + i * 0.8f) % 2.5f;
                float progress = phase / 2.5f;
                if (progress < 0.05f || progress > 0.95f) continue;
                float alpha = (float) Math.sin(progress * Math.PI);
                int a = Math.clamp((int)(alpha * 240.0f), 0, 255);
                if (a <= 5) continue;
                float rise = progress * 0.45f;
                float drift = (float) Math.sin(progress * 3.5f + i) * 0.05f;
                float size = 0.035f + i * 0.015f;
                drawLetterZ(entry, consumer, drift, -0.30f - rise, 0.0f, size, 165, 243, 252, a, light);
            }
        });
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
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isLocal = (state != null && mc.player != null && state.id == mc.player.getId());
        String rawName = getPlayerUsername(state);
        if (!isLocal && rawName != null && mc.getSession() != null) {
            String cleanStateName = CaeserPlayerSyncManager.cleanUsername(rawName);
            String cleanLocalName = CaeserPlayerSyncManager.cleanUsername(mc.getSession().getUsername());
            if (cleanStateName != null && cleanStateName.equalsIgnoreCase(cleanLocalName)) {
                isLocal = true;
            }
        }

        if (isLocal) {
            String custom = CosmeticsConfig.getAccessoryCustomPlayer(accId);
            if ("default".equalsIgnoreCase(custom) && "minime_hand".equalsIgnoreCase(accId)) {
                return MINIME_HAND_DEFAULT_TEXTURE;
            }
            if (custom != null && !custom.trim().isEmpty() && !"default".equalsIgnoreCase(custom)) {
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
            if (mc.player != null && mc.player.getSkin() != null && mc.player.getSkin().body() != null) {
                return mc.player.getSkin().body().texturePath();
            }
        } else if (rawName != null) {
            CaeserPlayerSyncManager.PlayerCosmeticsData remote = CaeserPlayerSyncManager.getCosmetics(rawName);
            if (remote != null && remote.petCustomPlayer != null && !remote.petCustomPlayer.trim().isEmpty()) {
                if ("default".equalsIgnoreCase(remote.petCustomPlayer) && "minime_hand".equalsIgnoreCase(accId)) {
                    return MINIME_HAND_DEFAULT_TEXTURE;
                }
                return PlayerSkinResolver.resolve(remote.petCustomPlayer);
            }
        }
        if (state != null && state.skinTextures != null && state.skinTextures.body() != null) {
            return state.skinTextures.body().texturePath();
        }
        if ("minime_hand".equalsIgnoreCase(accId)) {
            return MINIME_HAND_DEFAULT_TEXTURE;
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
        float pendulum = (float) Math.sin(timeSec * 1.8f) * 0.04f + walkSwing * 0.3f;

        m.body.roll = 0.12f + pendulum * 0.3f;
        m.body.pitch = 0.04f + pendulum * 0.2f;
        m.body.yaw = -0.15f;

        // Rechter Arm: Hält die Hand des Spielers fest
        // Pitch ~ 2.92f (reicht steil nach oben), Roll = 0.30f (neigt sich nach innen zur Hand des Spielers)
        m.rightArm.pitch = 2.92f + pendulum * 0.05f;
        m.rightArm.yaw = 0.05f;
        m.rightArm.roll = 0.30f - pendulum * 0.08f;

        // Linker Arm: Hängt entspannt herunter und wiegt sich sanft
        m.leftArm.pitch = 0.10f - pendulum * 0.6f;
        m.leftArm.yaw = 0.04f;
        m.leftArm.roll = 0.18f + (float) Math.cos(timeSec * 1.6f) * 0.04f;

        // Kopf: Schaut niedlich zum Spieler auf
        m.head.roll = -0.12f;
        m.head.pitch = -0.32f;
        m.head.yaw = -0.30f + pendulum * 0.1f;

        // Beine: Hängen vollkommen natürlich herunter und folgen nur dem sanften Pendelschwung
        m.rightLeg.pitch = 0.06f + pendulum * 0.3f;
        m.rightLeg.yaw = 0.04f;
        m.rightLeg.roll = 0.0f;

        m.leftLeg.pitch = -0.04f + pendulum * 0.3f;
        m.leftLeg.yaw = -0.04f;
        m.leftLeg.roll = 0.0f;
    }

    public static void poseHangingRightChibiModel(PlayerEntityModel m, float timeSec, float walkSwing) {
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

        float pendulum = (float) Math.sin(timeSec * 1.8f) * 0.04f + walkSwing * 0.3f;

        m.body.roll = -0.12f - pendulum * 0.3f;
        m.body.pitch = 0.04f + pendulum * 0.2f;
        m.body.yaw = 0.15f;

        // Linker Arm: Hält die rechte Hand des Spielers fest
        // Pitch ~ 2.92f (reicht steil nach oben), Roll = -0.30f (neigt sich nach innen zur Hand des Spielers)
        m.leftArm.pitch = 2.92f + pendulum * 0.05f;
        m.leftArm.yaw = -0.05f;
        m.leftArm.roll = -0.30f + pendulum * 0.08f;

        // Rechter Arm: Hängt entspannt herunter
        m.rightArm.pitch = 0.10f - pendulum * 0.6f;
        m.rightArm.yaw = -0.04f;
        m.rightArm.roll = -0.18f - (float) Math.cos(timeSec * 1.6f) * 0.04f;

        // Kopf: Schaut niedlich zum Spieler auf
        m.head.roll = 0.12f;
        m.head.pitch = -0.32f;
        m.head.yaw = 0.30f - pendulum * 0.1f;

        // Beine: Hängen natürlich herunter
        m.leftLeg.pitch = 0.06f + pendulum * 0.3f;
        m.leftLeg.yaw = -0.04f;
        m.leftLeg.roll = 0.0f;

        m.rightLeg.pitch = -0.04f + pendulum * 0.3f;
        m.rightLeg.yaw = 0.04f;
        m.rightLeg.roll = 0.0f;
    }

    public static void applyMiniMeHandAnimation(BedrockCosmeticModel model, float timeSec, float walkSwing) {
        if (model == null) return;
        float idlePhase = (float) Math.toRadians(timeSec * 100.0f);
        float headRotX = (float) -Math.cos(idlePhase - Math.toRadians(50.0f)) * 2.0f;
        float headPosX = (float) Math.cos(idlePhase) * 0.075f;
        float headPosY = (float) Math.cos(idlePhase) * 0.075f;

        float bodyPosX = (float) Math.cos(idlePhase) * 0.1f;
        float bodyPosY = (float) Math.cos(idlePhase) * 0.1f;

        float rArmRotZ = (float) Math.cos(idlePhase) * -2.0f;
        float rArmPosX = (float) Math.cos(idlePhase) * 0.075f;
        float rArmPosY = (float) Math.cos(idlePhase) * 0.075f;

        float lArmRotZ = (float) Math.cos(idlePhase) * -3.0f;
        float lArmPosX = (float) Math.cos(idlePhase) * 0.075f;
        float lArmPosY = (float) Math.cos(idlePhase) * 0.075f;

        float rLegRotX = (float) Math.cos(idlePhase) * 2.5f;
        float rLegRotZ = (float) Math.cos(idlePhase) * -2.5f;
        float rLegPosX = (float) Math.cos(idlePhase) * 0.125f;
        float rLegPosY = (float) Math.cos(idlePhase) * 0.125f;

        float lLegRotX = (float) Math.cos(idlePhase) * 3.5f;
        float lLegRotZ = (float) Math.cos(idlePhase) * -3.5f;
        float lLegPosX = (float) Math.cos(idlePhase) * 0.075f;
        float lLegPosY = (float) Math.cos(idlePhase) * 0.075f;

        model.setBoneRotation("minime", walkSwing * 12.0f, 0.0f, 0.0f);
        model.setBoneRotation("Head", headRotX, 0.0f, 0.0f);
        model.setBoneTranslation("Head", headPosX, headPosY, 0.0f);

        model.setBoneTranslation("Body", bodyPosX, bodyPosY, 0.0f);

        model.setBoneRotation("Right Arm", 0.0f, 0.0f, rArmRotZ);
        model.setBoneTranslation("Right Arm", rArmPosX, rArmPosY, 0.0f);

        model.setBoneRotation("Left Arm", 0.0f, 0.0f, lArmRotZ);
        model.setBoneTranslation("Left Arm", lArmPosX, lArmPosY, 0.0f);

        model.setBoneRotation("Right Leg", rLegRotX + walkSwing * 18.0f, 0.0f, rLegRotZ);
        model.setBoneTranslation("Right Leg", rLegPosX, rLegPosY, 0.0f);

        model.setBoneRotation("Left Leg", lLegRotX - walkSwing * 18.0f, 0.0f, lLegRotZ);
        model.setBoneTranslation("Left Leg", lLegPosX, lLegPosY, 0.0f);
    }

    public static void poseBabyDragon(float timeSec, boolean isAfk) {
        if (babyDragonRoot == null) return;

        if (isAfk) {
            // AFK: Sleeping, curled up
            float sleepBreath = (float) Math.sin(timeSec * 1.0f) * 0.02f;
            babyDragonHead.pitch = 0.42f + sleepBreath;
            babyDragonHead.yaw = 0.35f;
            babyDragonHead.roll = 0.15f;

            // Wings tucked tightly against body
            babyDragonWingLeft.roll = -0.75f;
            babyDragonWingLeft.yaw = 0.15f;
            babyDragonWingRight.roll = 0.75f;
            babyDragonWingRight.yaw = -0.15f;

            // Tail curled around paws
            babyDragonTail.yaw = 0.95f;
            babyDragonTail.pitch = -0.10f;
        } else {
            // Active animation!
            float breath = (float) Math.sin(timeSec * 2.2f) * 0.03f;

            // Flapping small wings gently
            float wingFlap = (float) Math.sin(timeSec * 4.2f) * 0.35f;
            babyDragonWingLeft.roll = wingFlap;
            babyDragonWingRight.roll = -wingFlap;
            babyDragonWingLeft.yaw = 0.10f;
            babyDragonWingRight.yaw = -0.10f;

            // Tail wagging happily
            babyDragonTail.yaw = (float) Math.sin(timeSec * 2.8f) * 0.40f;
            babyDragonTail.pitch = breath * 2.0f;

            // Sneeze / smoke puff animation cycle
            float sneezeCycle = (timeSec % 4.0f);
            if (sneezeCycle >= 3.4f && sneezeCycle <= 3.8f) {
                float sneezeProg = (sneezeCycle - 3.4f) / 0.4f;
                babyDragonHead.pitch = -0.28f + (float) Math.sin(sneezeProg * Math.PI) * 0.55f;
                babyDragonHead.yaw = 0.0f;
                babyDragonHead.roll = 0.0f;
            } else {
                babyDragonHead.pitch = (float) Math.sin(timeSec * 1.3f) * 0.12f;
                babyDragonHead.yaw = (float) Math.cos(timeSec * 0.9f) * 0.22f;
                babyDragonHead.roll = (float) Math.sin(timeSec * 1.1f) * 0.08f;
            }
        }
    }

    public static float[] calculateJellyScales(float timeSec, boolean isAfk, float vy, float timeSinceLand) {
        float sx = 1.0f, sy = 1.0f, sz = 1.0f;
        if (isAfk) {
            float sleepBreath = (float) Math.sin(timeSec * 1.2f) * 0.03f;
            sy = 0.75f + sleepBreath;
            sx = 1.15f - sleepBreath * 0.5f;
            sz = 1.15f - sleepBreath * 0.5f;
        } else {
            if (timeSinceLand < 1.0f) {
                float decay = (float) Math.exp(-timeSinceLand * 4.5f);
                float wobble = (float) Math.sin(timeSinceLand * 18.0f) * PetStateManager.landingImpact * decay;
                sy -= wobble;
                sx += wobble * 0.5f;
                sz += wobble * 0.5f;
            } else if (vy > 0.08f) {
                float stretch = Math.min(vy * 0.40f, 0.35f);
                sy += stretch;
                sx -= stretch * 0.4f;
                sz -= stretch * 0.4f;
            } else if (vy < -0.15f) {
                float stretch = Math.min(-vy * 0.30f, 0.28f);
                sy += stretch;
                sx -= stretch * 0.35f;
                sz -= stretch * 0.35f;
            } else {
                float idle = (float) Math.sin(timeSec * 3.2f) * 0.04f;
                sy += idle;
                sx -= idle * 0.5f;
                sz -= idle * 0.5f;
            }
        }
        return new float[] { sx, sy, sz };
    }

    public static void drawZzzParticlesDirect(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                              int light, float timeSec) {
        RenderLayer layer = RenderLayers.entityTranslucent(JELLY_SLIME_TEXTURE);
        VertexConsumer consumer = vertexConsumers.getBuffer(layer);
        for (int i = 0; i < 3; i++) {
            float phase = ((timeSec * 0.7f) + i * 0.8f) % 2.5f;
            float progress = phase / 2.5f;
            if (progress < 0.05f || progress > 0.95f) continue;
            float alpha = (float) Math.sin(progress * Math.PI);
            int a = Math.clamp((int)(alpha * 240.0f), 0, 255);
            if (a <= 5) continue;
            float rise = progress * 0.45f;
            float drift = (float) Math.sin(progress * 3.5f + i) * 0.05f;
            float size = 0.035f + i * 0.015f;
            drawLetterZ(matrices.peek(), consumer, drift, -0.30f - rise, 0.0f, size, 165, 243, 252, a, light);
        }
    }

    public static void drawLetterZ(MatrixStack.Entry entry, VertexConsumer consumer,
                                   float cx, float cy, float cz, float size,
                                   int r, int g, int b, int a, int light) {
        float h = size * 0.5f;
        float w = size * 0.45f;
        float t = size * 0.14f;

        drawRectQuad(entry, consumer, cx - w, cy - h, cx + w, cy - h + t, cz, r, g, b, a, light);
        drawRectQuad(entry, consumer, cx - w, cy + h - t, cx + w, cy - h + t, cz, r, g, b, a, light);
        drawRectQuad(entry, consumer, cx - w, cy + h - t, cx + w, cy + h, cz, r, g, b, a, light);
    }

    private static void drawRectQuad(MatrixStack.Entry entry, VertexConsumer consumer,
                                     float x0, float y0, float x1, float y1, float z,
                                     int r, int g, int b, int a, int light) {
        consumer.vertex(entry, x0, y1, z).color(r, g, b, a)
                .texture(0.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);
        consumer.vertex(entry, x1, y1, z).color(r, g, b, a)
                .texture(1.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);
        consumer.vertex(entry, x1, y0, z).color(r, g, b, a)
                .texture(1.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);
        consumer.vertex(entry, x0, y0, z).color(r, g, b, a)
                .texture(0.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);
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
        if ("default".equalsIgnoreCase(custom) && "minime_hand".equalsIgnoreCase(petType)) {
            return MINIME_HAND_DEFAULT_TEXTURE;
        }
        if (custom != null && !custom.trim().isEmpty() && !"default".equalsIgnoreCase(custom)) {
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
        if ("minime_hand".equalsIgnoreCase(petType)) {
            return MINIME_HAND_DEFAULT_TEXTURE;
        }
        return net.minecraft.client.util.DefaultSkinHelper.getTexture();
    }

    public static void renderCosmeticsDirect(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                             PlayerEntityModel playerModel, int light,
                                             String activeWings, String activeHead, String activeFace, String activePet) {
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
        } else if ("witch_hat".equalsIgnoreCase(activeHead)) {
            renderHeadModelDirect(matrices, vertexConsumers, playerModel, light, witchHatModel, WITCH_HAT_TEXTURE);
        } else if ("straw_hat".equalsIgnoreCase(activeHead)) {
            renderHeadModelDirect(matrices, vertexConsumers, playerModel, light, strawHatModel, STRAW_HAT_TEXTURE);
        }

        // 2b. Gesichts-Cosmetics
        if ("sunglasses".equalsIgnoreCase(activeFace)) {
            renderHeadModelDirect(matrices, vertexConsumers, playerModel, light, sunglassesModel, SUNGLASSES_TEXTURE);
        } else if ("skull_mask".equalsIgnoreCase(activeFace)) {
            renderHeadModelDirect(matrices, vertexConsumers, playerModel, light, skullMaskModel, SKULL_MASK_TEXTURE);
        }

        // 3. Accessories (Sit, Head, Hand, Chain) - Multi-equip in Live GUI Preview!
        if (sitChibiRoot != null) {
            java.util.Set<String> toRender = new java.util.LinkedHashSet<>();
            if (CosmeticsConfig.accessories != null && !CosmeticsConfig.accessories.isEmpty()) {
                toRender.addAll(CosmeticsConfig.accessories);
            }
            if (activePet != null && !"none".equalsIgnoreCase(activePet)) {
                toRender.add(activePet);
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
                    if (miniMeHandModel == null) continue;
                    String side = CosmeticsConfig.getAccessorySide("minime_hand");
                    boolean doLeftH = "left".equalsIgnoreCase(side) || "both".equalsIgnoreCase(side);
                    boolean doRightH = "right".equalsIgnoreCase(side) || "both".equalsIgnoreCase(side);

                    if (doLeftH) {
                        matrices.push();
                        if (playerModel != null) playerModel.leftArm.applyTransform(matrices);
                        matrices.translate(-0.3125f, -0.125f, 0.0f);
                        miniMeHandModel.resetAllAnimations();
                        applyMiniMeHandAnimation(miniMeHandModel, timeSec, 0.0f);
                        miniMeHandModel.render(matrices.peek(), consumer, light, 0, 1, false);
                        matrices.pop();
                    }
                    if (doRightH) {
                        matrices.push();
                        if (playerModel != null) playerModel.rightArm.applyTransform(matrices);
                        matrices.translate(0.3125f, -0.125f, 0.0f);
                        miniMeHandModel.resetAllAnimations();
                        applyMiniMeHandAnimation(miniMeHandModel, timeSec, 0.0f);
                        miniMeHandModel.render(matrices.peek(), consumer, light, 0, 1, true);
                        matrices.pop();
                    }
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
                    String sitSide = CosmeticsConfig.getAccessorySide("minime_sit");
                    boolean doRight = "right".equalsIgnoreCase(sitSide) || "both".equalsIgnoreCase(sitSide);
                    boolean doLeft = "left".equalsIgnoreCase(sitSide) || "both".equalsIgnoreCase(sitSide);

                    if (doRight) {
                        matrices.push();
                        if (playerModel != null) playerModel.body.applyTransform(matrices);
                        matrices.translate(-0.40f, -0.195f, -0.02f);
                        matrices.scale(0.28f, 0.28f, 0.28f);
                        sitChibiRoot.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
                        matrices.pop();
                    }
                    if (doLeft) {
                        matrices.push();
                        if (playerModel != null) playerModel.body.applyTransform(matrices);
                        matrices.translate(0.40f, -0.195f, -0.02f);
                        matrices.scale(0.28f, 0.28f, 0.28f);
                        sitChibiRoot.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
                        matrices.pop();
                    }
                } else if ("baby_dragon".equalsIgnoreCase(accId)) {
                    if (babyDragonRoot == null) continue;
                    boolean isAfk = PetStateManager.isAfk();
                    poseBabyDragon(timeSec, isAfk);
                    String placement = CosmeticsConfig.getPetPlacement("baby_dragon");
                    matrices.push();
                    applyPlacementTransform(matrices, playerModel, placement, timeSec);
                    matrices.scale(0.26f, 0.26f, 0.26f);
                    RenderLayer dLayer = RenderLayers.entityCutoutNoCull(BABY_DRAGON_TEXTURE);
                    VertexConsumer dConsumer = vertexConsumers.getBuffer(dLayer);
                    babyDragonRoot.render(matrices, dConsumer, light, OverlayTexture.DEFAULT_UV);
                    if (isAfk) {
                        drawZzzParticlesDirect(matrices, vertexConsumers, light, timeSec);
                    }
                    matrices.pop();
                } else if ("jelly_slime".equalsIgnoreCase(accId)) {
                    if (jellySlimeRoot == null) continue;
                    boolean isAfk = PetStateManager.isAfk();
                    float timeSinceLand = (System.currentTimeMillis() - PetStateManager.lastLandingTime) / 1000.0f;
                    float[] scales = calculateJellyScales(timeSec, isAfk, 0.0f, timeSinceLand);
                    String placement = CosmeticsConfig.getPetPlacement("jelly_slime");
                    matrices.push();
                    applyPlacementTransform(matrices, playerModel, placement, timeSec);
                    matrices.scale(0.28f * scales[0], 0.28f * scales[1], 0.28f * scales[2]);
                    RenderLayer sLayer = RenderLayers.entityTranslucent(JELLY_SLIME_TEXTURE);
                    VertexConsumer sConsumer = vertexConsumers.getBuffer(sLayer);
                    jellySlimeRoot.render(matrices, sConsumer, light, OverlayTexture.DEFAULT_UV);
                    if (isAfk) {
                        drawZzzParticlesDirect(matrices, vertexConsumers, light, timeSec);
                    }
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
            case "witch_hat" -> renderCardHeadModel(matrices, vertexConsumers, light, witchHatModel, WITCH_HAT_TEXTURE, 1.395f, 10.0f, -15.0f, 1.55f);
            case "straw_hat" -> renderCardHeadModel(matrices, vertexConsumers, light, strawHatModel, STRAW_HAT_TEXTURE, 0.85f, 22.0f, -20.0f, 1.4f);
            case "sunglasses" -> renderCardHeadModel(matrices, vertexConsumers, light, sunglassesModel, SUNGLASSES_TEXTURE, 0.55f, 5.0f, -25.0f, 2.4f);
            case "skull_mask" -> renderCardHeadModel(matrices, vertexConsumers, light, skullMaskModel, SKULL_MASK_TEXTURE, 0.55f, 8.0f, -20.0f, 2.0f);
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
                if (miniMeHandModel == null) return;
                matrices.push();
                matrices.translate(0.0f, 0.48f, 0.0f);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-15.0f));
                matrices.scale(1.95f, 1.95f, 1.95f);
                matrices.translate(-0.594f, -1.047f, 0.0f);
                miniMeHandModel.resetAllAnimations();
                applyMiniMeHandAnimation(miniMeHandModel, timeSec, 0.0f);
                Identifier skinTexture = getPetSkinDirect(cosmeticId);
                RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);
                VertexConsumer consumer = vertexConsumers.getBuffer(layer);
                miniMeHandModel.render(matrices.peek(), consumer, light, 0, 1);
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
            case "baby_dragon" -> {
                if (babyDragonRoot == null) return;
                matrices.push();
                matrices.translate(0.0f, 0.46f, 0.0f);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-20.0f));
                matrices.scale(0.78f, 0.78f, 0.78f);
                poseBabyDragon(timeSec, false);
                RenderLayer layer = RenderLayers.entityCutoutNoCull(BABY_DRAGON_TEXTURE);
                VertexConsumer consumer = vertexConsumers.getBuffer(layer);
                babyDragonRoot.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
                matrices.pop();
            }
            case "jelly_slime" -> {
                if (jellySlimeRoot == null) return;
                matrices.push();
                matrices.translate(0.0f, 0.44f, 0.0f);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-15.0f));
                float wobble = (float) Math.sin(timeSec * 3.2f) * 0.05f;
                matrices.scale(0.85f * (1.0f + wobble), 0.85f * (1.0f - wobble * 1.5f), 0.85f * (1.0f + wobble));
                RenderLayer layer = RenderLayers.entityTranslucent(JELLY_SLIME_TEXTURE);
                VertexConsumer consumer = vertexConsumers.getBuffer(layer);
                jellySlimeRoot.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
                matrices.pop();
            }
        }
    }
}
