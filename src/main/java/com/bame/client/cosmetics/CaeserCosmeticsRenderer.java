package com.bame.client.cosmetics;

import com.bame.client.gui.GuiTheme;
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
    public static final Identifier CROWN_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/hat/crown.png");
    public static final Identifier HEADPHONES_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/hat/headphones.png");
    public static final Identifier VIKING_HELMET_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/hat/viking_helmet.png");
    public static final Identifier PROPELLER_CAP_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/hat/propeller_cap.png");
    public static final Identifier CAT_EARS_TEXTURE = Identifier.of("bameclient", "textures/cosmetics/hat/cat_ears.png");

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
    public static BedrockCosmeticModel crownModel;
    public static BedrockCosmeticModel headphonesModel;
    public static BedrockCosmeticModel vikingHelmetModel;
    public static BedrockCosmeticModel propellerCapModel;
    public static BedrockCosmeticModel catEarsModel;

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
    // Baby Dragon: Bedrock-Modell mit Knochen (Hals, Kopf, Kiefer, Lider, Fluegel mit Hand, 5 Schwanzglieder, Beine)
    public static BedrockCosmeticModel babyDragonModel;
    private static final BabyDragonPose DRAGON_POSE = new BabyDragonPose();

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
        if (babyDragonModel == null) {
            babyDragonModel = BedrockCosmeticModel.load("assets/bameclient/models/cosmetics/pets/baby_dragon.geo.json");
            if (babyDragonModel != null) {
                babyDragonModel.blockbenchAxisFix = true;
            }
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
            if (demonHornsModel != null) {
                // v2: geschwungene Segment-Hoerner, im Kopfraum modelliert (Basis auf der Stirn, keine Verschiebung noetig)
                demonHornsModel.blockbenchAxisFix = true;
            }
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
            if (miniMeHandModel != null) {
                miniMeHandModel.blockbenchAxisFix = true;
            }
        }
        // Neue Huete: im Kopfraum modelliert (Kopf x/z -4..4, y 24..32), Blockbench-Achsen
        if (crownModel == null) crownModel = loadHatModel("crown");
        if (headphonesModel == null) headphonesModel = loadHatModel("headphones");
        if (vikingHelmetModel == null) vikingHelmetModel = loadHatModel("viking_helmet");
        if (propellerCapModel == null) propellerCapModel = loadHatModel("propeller_cap");
        if (catEarsModel == null) catEarsModel = loadHatModel("cat_ears");
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

    /** Lokaler Wert, falls besessen, sonst "none" (gespeicherter Wert bleibt unverändert). */
    private static String ownedOrNone(String category, String value) {
        if (value == null || value.isBlank() || "none".equalsIgnoreCase(value)) return value;
        return com.bame.client.emotes.EmoteUnlockManager.isCosmeticUnlocked(category, value) ? value : "none";
    }

    /** Lokal ausgerüstet UND besessen. */
    private static boolean localAccessory(String id) {
        return CosmeticsConfig.isAccessoryEquipped(id)
                && com.bame.client.emotes.EmoteUnlockManager.isCosmeticUnlocked(
                        "baby_dragon".equals(id) || "jelly_slime".equals(id) ? "PETS" : "ACCESSORIES", id);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Render cosmetics on the local player OR remote Caeser Client players
        // (lokal: Entity-ID bzw. UUID; fremde Spieler werden über ihre UUID nachgeschlagen)
        boolean isLocal = CaeserPlayerSyncManager.isLocalPlayer(state);

        String wings = null;
        String head = null;
        String face = null;
        String pet = null;
        Set<String> accessories = null;

        if (isLocal) {
            PetStateManager.tick();
            CosmeticsConfig.checkReload();
            // Nicht besessene (z. B. vom Launcher gesetzte) Cosmetics wie "none" behandeln
            wings = ownedOrNone("WINGS", CosmeticsConfig.wings);
            head = ownedOrNone("HEAD", CosmeticsConfig.head);
            face = ownedOrNone("FACE", CosmeticsConfig.face);
            pet = ownedOrNone("ACCESSORIES", CosmeticsConfig.pet);
            accessories = CosmeticsConfig.accessories;
        } else {
            // Bereits bereinigt (Sync-Server) bzw. gegen den OwnershipCache gefiltert (LAN)
            CaeserPlayerSyncManager.PlayerCosmeticsData remote = CaeserPlayerSyncManager.getCosmetics(state);
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
                } else if (isAnimatedHat(head)) {
                    renderAnimatedHat(head, matrices, queue, light, state, isLocal);
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
                boolean hasSit = isLocal ? localAccessory("minime_sit") : ((accessories != null && accessories.contains("minime_sit")) || "minime_sit".equalsIgnoreCase(pet));
                boolean hasHead = isLocal ? localAccessory("minime_head") : ((accessories != null && accessories.contains("minime_head")) || "minime_head".equalsIgnoreCase(pet));
                boolean hasHand = isLocal ? localAccessory("minime_hand") : ((accessories != null && accessories.contains("minime_hand")) || "minime_hand".equalsIgnoreCase(pet));
                boolean hasChain = isLocal ? localAccessory("minime_chain") : ((accessories != null && accessories.contains("minime_chain")) || "minime_chain".equalsIgnoreCase(pet));
                boolean hasDragon = isLocal ? localAccessory("baby_dragon") : ((accessories != null && accessories.contains("baby_dragon")) || "baby_dragon".equalsIgnoreCase(pet));
                boolean hasSlime = isLocal ? localAccessory("jelly_slime") : ((accessories != null && accessories.contains("jelly_slime")) || "jelly_slime".equalsIgnoreCase(pet));

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
    // DEMON HORNS (geschwungene, gerippte Hoerner auf der Stirn; folgen dem Kopf)
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
    // ANIMIERTE HUETE (Krone, Kopfhoerer, Wikingerhelm, Propellermuetze, Katzenohren)
    // ==========================================
    /** Zustands-Schluessel der Menue-Vorschau (Spieler) bzw. der Shop-Karten; echte Spieler nutzen die Entity-ID. */
    private static final int PREVIEW_KEY = -1;
    private static final int CARD_KEY = -2;
    private static final int FULL_BRIGHT = 15728880;

    private static BedrockCosmeticModel loadHatModel(String id) {
        BedrockCosmeticModel model = BedrockCosmeticModel.load("assets/bameclient/models/cosmetics/hat/" + id + ".geo.json");
        if (model != null) model.blockbenchAxisFix = true;
        return model;
    }

    public static boolean isAnimatedHat(String id) {
        if (id == null) return false;
        return switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "crown", "headphones", "viking_helmet", "propeller_cap", "cat_ears" -> true;
            default -> false;
        };
    }

    /** Zeichenziel: Befehls-Queue (Welt, verzoegert) oder direkt in einen VertexConsumerProvider (Menue). */
    @FunctionalInterface
    private interface HatSink {
        void draw(RenderLayer layer, OrderedRenderCommandQueue.Custom body);
    }

    private static HatSink directSink(MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        return (layer, body) -> body.render(matrices.peek(), vertexConsumers.getBuffer(layer));
    }

    private static long seedOf(java.util.UUID uuid, int fallback) {
        return uuid != null ? uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits() : fallback * 0x9E3779B97F4A7C15L;
    }

    private static long localSeed() {
        MinecraftClient mc = MinecraftClient.getInstance();
        java.util.UUID uuid = null;
        if (mc.player != null) uuid = mc.player.getUuid();
        else if (mc.getSession() != null) uuid = mc.getSession().getUuidOrNull();
        return seedOf(uuid, 1);
    }

    /** Ingame: am Kopf-Bone, folgt Drehung/Nicken/Schleichen; nicht in der Ego-Perspektive (siehe render). */
    private void renderAnimatedHat(String id, MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                   PlayerEntityRenderState state, boolean isLocal) {
        matrices.push();
        this.getContextModel().head.applyTransform(matrices);
        long seed = seedOf(CaeserPlayerSyncManager.getRenderStateUuid(state), state.id);
        // Logo-Farbe: eigene Akzentfarbe; fremde Spieler in festem Caeser-Lila (deren Theme wird nicht synchronisiert)
        int glow = isLocal ? (GuiTheme.accent() & 0xFFFFFF) : HatAnimations.CAESER_PURPLE;
        HatSink sink = (layer, body) -> queue.getBatchingQueue(0).submitCustom(matrices, layer, body);
        drawAnimatedHat(id, sink, light, state.id, Math.min(state.limbSwingAmplitude, 1.0f), state.isInSneakingPose, seed, glow);
        if ("crown".equalsIgnoreCase(id)) maybeSpawnCrownSparkle(matrices.peek(), state, seed);
        matrices.pop();
    }

    /** Karten-Vorschau: Modell allein, gedreht und skaliert wie die anderen HAT-Karten. */
    private static void renderCardAnimatedHat(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, String id,
                                              float translateY, float pitch, float yaw, float scale) {
        matrices.push();
        matrices.translate(0.0f, translateY, 0.0f);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.scale(scale, scale, scale);
        drawAnimatedHat(id, directSink(matrices, vertexConsumers), light, CARD_KEY, 0.0f, false, 7L, GuiTheme.accent() & 0xFFFFFF);
        matrices.pop();
    }

    /**
     * Zeichnet einen animierten Hut im aktuellen Kopfraum. Animationswerte werden erst im Zeichen-Callback auf das
     * (geteilte) Modell gesetzt, weil die Queue verzoegert zeichnet und mehrere Spieler dasselbe Modell nutzen.
     */
    private static void drawAnimatedHat(String rawId, HatSink sink, int light, int key, float limbAmplitude,
                                        boolean sneaking, long seed, int glowRgb) {
        initModels();
        long nowMs = System.currentTimeMillis();
        float timeSec = (float) (nowMs % 10000000L) / 1000.0f;
        switch (rawId.toLowerCase(java.util.Locale.ROOT)) {
            case "crown" -> {
                BedrockCosmeticModel m = crownModel;
                if (m == null) return;
                sink.draw(RenderLayers.entityCutoutNoCull(CROWN_TEXTURE), (e, c) -> {
                    m.resetAllAnimations();
                    m.render(e, c, light, 0, 1, false, 0xFFFFFFFF, "base");
                });
                // Glanzlichter: additiv und selbstleuchtend, gestaffelt aufblitzend
                for (int i = 0; i < HatAnimations.CROWN_GLINTS; i++) {
                    float k = HatAnimations.crownGlint(i, timeSec);
                    if (k < 0.03f) continue;
                    int argb = HatAnimations.scaleRgb(0xFFFFFF, k);
                    String bone = "glint" + i;
                    sink.draw(RenderLayers.eyes(CROWN_TEXTURE), (e, c) -> m.render(e, c, FULL_BRIGHT, 0, 1, false, argb, bone));
                }
            }
            case "headphones" -> {
                BedrockCosmeticModel m = headphonesModel;
                if (m == null) return;
                sink.draw(RenderLayers.entityCutoutNoCull(HEADPHONES_TEXTURE), (e, c) -> {
                    m.resetAllAnimations();
                    m.render(e, c, light, 0, 1, false, 0xFFFFFFFF, "base");
                });
                int argb = HatAnimations.scaleRgb(glowRgb, HatAnimations.headphonePulse(timeSec));
                sink.draw(RenderLayers.eyes(HEADPHONES_TEXTURE), (e, c) -> m.render(e, c, FULL_BRIGHT, 0, 1, false, argb, "glow"));
            }
            case "viking_helmet" -> {
                BedrockCosmeticModel m = vikingHelmetModel;
                if (m == null) return;
                sink.draw(RenderLayers.entityCutoutNoCull(VIKING_HELMET_TEXTURE), (e, c) -> {
                    m.resetAllAnimations();
                    m.render(e, c, light, 0, 1);
                });
            }
            case "propeller_cap" -> {
                BedrockCosmeticModel m = propellerCapModel;
                if (m == null) return;
                HatAnimations.PlayerHatState hs = HatAnimations.state(key, nowMs);
                float dt = hs.tick(System.nanoTime());
                hs.spinner.advance(HatAnimations.propellerTargetSpeed(limbAmplitude), dt);
                float angle = hs.spinner.angle;
                sink.draw(RenderLayers.entityCutoutNoCull(PROPELLER_CAP_TEXTURE), (e, c) -> {
                    m.resetAllAnimations();
                    m.setBoneRotation("propeller", 0.0f, angle, 0.0f);
                    m.render(e, c, light, 0, 1);
                });
            }
            case "cat_ears" -> {
                BedrockCosmeticModel m = catEarsModel;
                if (m == null) return;
                HatAnimations.PlayerHatState hs = HatAnimations.state(key, nowMs);
                float dt = hs.tick(System.nanoTime());
                hs.sneak = HatAnimations.sneakStep(hs.sneak, sneaking, dt);
                float[] p = HatAnimations.catEarPose(timeSec, seed, hs.sneak);
                sink.draw(RenderLayers.entityCutoutNoCull(CAT_EARS_TEXTURE), (e, c) -> {
                    m.resetAllAnimations();
                    m.setBoneRotation("ear_l", p[0], 0.0f, p[1]);
                    m.setBoneRotation("ear_r", p[2], 0.0f, p[3]);
                    m.render(e, c, light, 0, 1);
                });
            }
            default -> { }
        }
    }

    /** Selten ein winziger Funke an einem Edelstein/einer Spitze der Krone (nur Welt, nie in der Ego-Perspektive). */
    private static void maybeSpawnCrownSparkle(MatrixStack.Entry entry, PlayerEntityRenderState state, long seed) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || crownModel == null || mc.isPaused()) return;
        long nowMs = System.currentTimeMillis();
        float timeSec = (float) (nowMs % 10000000L) / 1000.0f;
        long slot = (long) Math.floor(timeSec / HatAnimations.SPARKLE_SLOT);
        HatAnimations.PlayerHatState hs = HatAnimations.state(state.id, nowMs);
        if (hs.sparkleSlot == slot) return;
        hs.sparkleSlot = slot;
        int gi = HatAnimations.crownSparkle(slot, seed);
        if (gi < 0) return;
        net.minecraft.util.math.Vec3d cam = mc.gameRenderer != null && mc.gameRenderer.getCamera() != null
                ? mc.gameRenderer.getCamera().getCameraPos() : null;
        if (cam == null) return;
        float[] pos = HatAnimations.CROWN_GLINT_POS[gi];
        org.joml.Vector3f local = crownModel.boneToModel("crown", pos[0], pos[1], pos[2]);
        if (local == null) return;
        org.joml.Vector3f v = entry.getPositionMatrix().transformPosition(local, new org.joml.Vector3f());
        double sx = cam.x + v.x, sy = cam.y + v.y, sz = cam.z + v.z;
        double dx = sx - state.x, dy = sy - (state.y + 1.9), dz = sz - state.z;
        if (dx * dx + dy * dy + dz * dz > 9.0) return; // Matrix nicht weltbezogen (z. B. Vorschau): lieber kein Funke
        mc.world.addParticleClient(ParticleTypes.ELECTRIC_SPARK, sx, sy, sz, 0.0, 0.004, 0.0);
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
        // Hinweis: limbAngle/limbDistance sind in 1.21.11 relativeHeadYaw/pitch, daher die echten Laufwerte aus dem State
        float limbProgress = state.limbSwingAnimationProgress;
        float limbAmplitude = Math.min(state.limbSwingAmplitude, 1.0f);
        float walkSwing = (limbAmplitude > 0.01f) ? (float) Math.sin(limbProgress * 0.6662f) * limbAmplitude * 0.35f : 0.0f;

        String side = CosmeticsConfig.getAccessorySide("minime_hand");
        boolean renderLeft = "left".equalsIgnoreCase(side) || "both".equalsIgnoreCase(side);
        boolean renderRight = "right".equalsIgnoreCase(side) || "both".equalsIgnoreCase(side);

        Identifier skinTexture = getPetSkin("minime_hand", state);
        RenderLayer layer = RenderLayers.entityTranslucent(skinTexture);

        if (renderLeft) {
            matrices.push();
            applyMiniMeHandTransform(matrices, this.getContextModel().leftArm, false, timeSec, walkSwing);
            miniMeHandModel.resetAllAnimations();
            applyMiniMeHandAnimation(miniMeHandModel, timeSec, walkSwing);
            queue.getBatchingQueue(0).submitCustom(matrices, layer, (entry, consumer) -> {
                miniMeHandModel.render(entry, consumer, light, 0, 1, false);
            });
            matrices.pop();
        }

        if (renderRight) {
            matrices.push();
            // Rechter Arm schwingt gegenphasig zum linken
            applyMiniMeHandTransform(matrices, this.getContextModel().rightArm, true, timeSec, -walkSwing);
            miniMeHandModel.resetAllAnimations();
            applyMiniMeHandAnimation(miniMeHandModel, timeSec, -walkSwing);
            queue.getBatchingQueue(0).submitCustom(matrices, layer, (entry, consumer) -> {
                miniMeHandModel.render(entry, consumer, light, 0, 1, true);
            });
            matrices.pop();
        }
    }

    // Greifpunkt des MiniMe (Hand des hochgestreckten Arms) in Java-Modellkoordinaten, linke Seite
    private static final float MINIME_GRIP_X = 6.05f / 16.0f;
    private static final float MINIME_GRIP_Y = 12.0f / 16.0f;
    private static final float MINIME_PENDULUM_WALK_DEG = 30.0f; // * walkSwing (max 0.35) = max 10.5 Grad
    private static final float MINIME_PENDULUM_IDLE_DEG = 1.5f;

    // Gemeinsame Arm-Transformation fuer Ingame und Menue-Vorschau: haengt das MiniMe an die Hand
    // und laesst es um den Greifpunkt pendeln.
    public static void applyMiniMeHandTransform(MatrixStack matrices, ModelPart arm, boolean mirrorX,
                                                float timeSec, float walkSwing) {
        if (arm != null) {
            arm.applyTransform(matrices);
            // Ruhe-Pivot des Arms abziehen (Wide 5/2/0, Slim 5/2.5/0). Bewusst nicht originX/Y/Z:
            // Vanilla verschiebt diese beim Schleichen und Schlagen, das MiniMe wuerde von der Hand rutschen.
            ModelTransform rest = arm.getDefaultTransform();
            matrices.translate(-rest.x() / 16.0f, -rest.y() / 16.0f, -rest.z() / 16.0f);
        }

        float gripX = mirrorX ? -MINIME_GRIP_X : MINIME_GRIP_X;
        float idleSway = (float) Math.sin(timeSec * 1.8f) * MINIME_PENDULUM_IDLE_DEG;
        matrices.translate(gripX, MINIME_GRIP_Y, 0.0f);
        // Positiver Pitch = Koerper nach hinten: haengt beim Vorschwingen des Arms leicht nach
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(walkSwing * MINIME_PENDULUM_WALK_DEG));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(mirrorX ? -idleSway : idleSway));
        matrices.translate(-gripX, -MINIME_GRIP_Y, 0.0f);
    }

    private void renderBabyDragon(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                  int light, PlayerEntityRenderState state) {
        if (babyDragonModel == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
        boolean afk = PetStateManager.isAfk();
        String placement = CosmeticsConfig.getPetPlacement("baby_dragon");
        poseBabyDragon(timeSec, afk, BabyDragonPose.isFlying(placement));

        matrices.push();
        applyBabyDragonTransform(matrices, this.getContextModel(), placement, timeSec);

        // Niesen: Rauchwoelkchen direkt aus den Nuestern (Weltposition aus der Modellmatrix)
        if (!afk && mc.world != null && BabyDragonPose.isSmokeMoment(timeSec)) {
            long now = System.currentTimeMillis();
            if (now - lastSmokeTime > 90L) {
                lastSmokeTime = now;
                spawnNostrilSmoke(mc, matrices.peek(), state);
            }
        }

        RenderLayer layer = RenderLayers.entityCutoutNoCull(BABY_DRAGON_TEXTURE);
        queue.getBatchingQueue(0).submitCustom(matrices, layer, (entry, consumer) -> {
            babyDragonModel.render(entry, consumer, light, 0, 1);
        });

        if (afk) {
            applyBabyDragonZzzTransform(matrices);
            renderFloatingZzz(matrices, queue, light, timeSec);
        }

        matrices.pop();
    }

    /** Platzierung + Sitzhoehe (Fuesse auf Schulter/Kopf) + Modellmassstab. */
    public static void applyBabyDragonTransform(MatrixStack matrices, PlayerEntityModel playerModel, String placement, float timeSec) {
        applyPlacementTransform(matrices, playerModel, placement, timeSec);
        matrices.translate(0.0f, BabyDragonPose.seatOffsetY(placement), 0.0f);
        float s = BabyDragonPose.MODEL_SCALE;
        matrices.scale(s, s, s);
    }

    /** Zzz ueber dem schlafenden Kopf, in der Groesse wie beim alten Modell. */
    private static void applyBabyDragonZzzTransform(MatrixStack matrices) {
        // Kopf liegt im Schlaf seitlich vorn (Modell -x, -z); danach alter Massstab fuer die Zzz
        matrices.translate(-3.0f / 16.0f, -2.0f / 16.0f, -8.0f / 16.0f);
        float k = BabyDragonPose.ZZZ_SCALE / BabyDragonPose.MODEL_SCALE;
        matrices.scale(k, k, k);
    }

    private static void spawnNostrilSmoke(MinecraftClient mc, MatrixStack.Entry entry, PlayerEntityRenderState state) {
        net.minecraft.util.math.Vec3d cam = mc.gameRenderer != null && mc.gameRenderer.getCamera() != null
                ? mc.gameRenderer.getCamera().getCameraPos() : null;
        if (cam == null) return;
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (int side = -1; side <= 1; side += 2) {
            org.joml.Vector3f local = babyDragonModel.boneToModel("head",
                    side * BabyDragonPose.NOSTRIL_X, BabyDragonPose.NOSTRIL_Y, BabyDragonPose.NOSTRIL_Z);
            if (local == null) return;
            org.joml.Vector3f v = entry.getPositionMatrix().transformPosition(local, new org.joml.Vector3f());
            double sx = cam.x + v.x, sy = cam.y + v.y, sz = cam.z + v.z;
            // Plausibilitaet (Matrix kamerarelativ): sonst wie frueher an der Spielerposition
            double dx = sx - state.x, dy = sy - (state.y + 1.4), dz = sz - state.z;
            if (dx * dx + dy * dy + dz * dz > 9.0) {
                sx = state.x + (rnd.nextFloat() - 0.5f) * 0.15;
                sy = state.y + 1.42;
                sz = state.z + (rnd.nextFloat() - 0.5f) * 0.15;
            }
            mc.world.addParticleClient(ParticleTypes.SMOKE, sx, sy, sz,
                    (rnd.nextFloat() - 0.5f) * 0.01, 0.015 + rnd.nextFloat() * 0.02, (rnd.nextFloat() - 0.5f) * 0.01);
        }
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
        boolean isLocal = CaeserPlayerSyncManager.isLocalPlayer(state);

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
        } else if (state != null) {
            CaeserPlayerSyncManager.PlayerCosmeticsData remote = CaeserPlayerSyncManager.getCosmetics(state);
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

        // Pendeln des ganzen MiniMe passiert im Renderer um den Greifpunkt (applyMiniMeHandTransform)
        model.setBoneRotation("Head", headRotX, 0.0f, 0.0f);
        model.setBoneTranslation("Head", headPosX, headPosY, 0.0f);

        model.setBoneTranslation("Body", bodyPosX, bodyPosY, 0.0f);

        // Greifarm: keine Translation, damit die Hand an der Spielerhand bleibt
        model.setBoneRotation("Right Arm", 0.0f, 0.0f, rArmRotZ);

        model.setBoneRotation("Left Arm", 0.0f, 0.0f, lArmRotZ);
        model.setBoneTranslation("Left Arm", lArmPosX, lArmPosY, 0.0f);

        model.setBoneRotation("Right Leg", rLegRotX + walkSwing * 18.0f, 0.0f, rLegRotZ);
        model.setBoneTranslation("Right Leg", rLegPosX, rLegPosY, 0.0f);

        model.setBoneRotation("Left Leg", lLegRotX - walkSwing * 18.0f, 0.0f, lLegRotZ);
        model.setBoneTranslation("Left Leg", lLegPosX, lLegPosY, 0.0f);
    }

    /** Animation (BabyDragonPose) auf das Modell uebertragen. */
    public static void poseBabyDragon(float timeSec, boolean isAfk, boolean flying) {
        if (babyDragonModel == null) return;
        DRAGON_POSE.compute(timeSec, isAfk, flying);
        babyDragonModel.resetAllAnimations();
        for (int i = 0; i < BabyDragonPose.BONES.length; i++) {
            float[] r = DRAGON_POSE.rot[i], t = DRAGON_POSE.trans[i];
            babyDragonModel.setBoneRotation(BabyDragonPose.BONES[i], r[0], r[1], r[2]);
            babyDragonModel.setBoneTranslation(BabyDragonPose.BONES[i], t[0], t[1], t[2]);
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

            demonHornsModel.resetAllAnimations();
            RenderLayer layer = RenderLayers.entityCutoutNoCull(DEMON_HORNS_TEXTURE);
            VertexConsumer consumer = vertexConsumers.getBuffer(layer);
            demonHornsModel.render(matrices.peek(), consumer, light, 0, 1);

            matrices.pop();
        } else if ("witch_hat".equalsIgnoreCase(activeHead)) {
            renderHeadModelDirect(matrices, vertexConsumers, playerModel, light, witchHatModel, WITCH_HAT_TEXTURE);
        } else if ("straw_hat".equalsIgnoreCase(activeHead)) {
            renderHeadModelDirect(matrices, vertexConsumers, playerModel, light, strawHatModel, STRAW_HAT_TEXTURE);
        } else if (isAnimatedHat(activeHead)) {
            matrices.push();
            if (playerModel != null) playerModel.head.applyTransform(matrices);
            drawAnimatedHat(activeHead, directSink(matrices, vertexConsumers), light, PREVIEW_KEY, 0.0f, false,
                    localSeed(), GuiTheme.accent() & 0xFFFFFF);
            matrices.pop();
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
                        applyMiniMeHandTransform(matrices, playerModel != null ? playerModel.leftArm : null, false, timeSec, 0.0f);
                        miniMeHandModel.resetAllAnimations();
                        applyMiniMeHandAnimation(miniMeHandModel, timeSec, 0.0f);
                        miniMeHandModel.render(matrices.peek(), consumer, light, 0, 1, false);
                        matrices.pop();
                    }
                    if (doRightH) {
                        matrices.push();
                        applyMiniMeHandTransform(matrices, playerModel != null ? playerModel.rightArm : null, true, timeSec, 0.0f);
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
                    if (babyDragonModel == null) continue;
                    boolean isAfk = PetStateManager.isAfk();
                    String placement = CosmeticsConfig.getPetPlacement("baby_dragon");
                    poseBabyDragon(timeSec, isAfk, BabyDragonPose.isFlying(placement));
                    matrices.push();
                    applyBabyDragonTransform(matrices, playerModel, placement, timeSec);
                    RenderLayer dLayer = RenderLayers.entityCutoutNoCull(BABY_DRAGON_TEXTURE);
                    VertexConsumer dConsumer = vertexConsumers.getBuffer(dLayer);
                    babyDragonModel.render(matrices.peek(), dConsumer, light, 0, 1);
                    if (isAfk) {
                        applyBabyDragonZzzTransform(matrices);
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
                // gleiche Bildmitte/-groesse wie die alten Hoerner, seitlicher gedreht (zeigt den Schwung nach hinten)
                matrices.translate(-0.15f, 1.80f, 0.0f);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-30.0f));
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(15.0f));
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
            case "crown" -> renderCardAnimatedHat(matrices, vertexConsumers, light, "crown", 1.495f, 15.0f, -20.0f, 2.0f);
            case "headphones" -> renderCardAnimatedHat(matrices, vertexConsumers, light, "headphones", 0.85f, 12.0f, -35.0f, 1.95f);
            case "viking_helmet" -> renderCardAnimatedHat(matrices, vertexConsumers, light, "viking_helmet", 1.01f, 12.0f, -25.0f, 1.55f);
            case "propeller_cap" -> renderCardAnimatedHat(matrices, vertexConsumers, light, "propeller_cap", 1.024f, 25.0f, -25.0f, 1.9f);
            case "cat_ears" -> renderCardAnimatedHat(matrices, vertexConsumers, light, "cat_ears", 2.196f, 18.0f, -20.0f, 3.3f);
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
                matrices.translate(-0.658f, -1.047f, 0.0f);
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
                if (babyDragonModel == null) return;
                matrices.push();
                matrices.translate(0.0f, BabyDragonPose.CARD_TRANSLATE_Y, 0.0f);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(BabyDragonPose.CARD_YAW));
                float cs = BabyDragonPose.CARD_SCALE;
                matrices.scale(cs, cs, cs);
                poseBabyDragon(timeSec, false, false);
                RenderLayer layer = RenderLayers.entityCutoutNoCull(BABY_DRAGON_TEXTURE);
                VertexConsumer consumer = vertexConsumers.getBuffer(layer);
                babyDragonModel.render(matrices.peek(), consumer, light, 0, 1);
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
