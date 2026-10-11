package com.bame.client.emotes;

import com.bame.client.module.EmoteModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;

public class EmotePropFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    public static final Identifier SOCCER_TEXTURE = Identifier.of("bameclient", "textures/entity/soccer_ball.png");
    public static final Identifier BASKETBALL_TEXTURE = Identifier.of("bameclient", "textures/entity/basketball.png");
    public static final Identifier GUITAR_TEXTURE = Identifier.of("bameclient", "textures/entity/electric_guitar.png");
    public static final Identifier COIN_TEXTURE = Identifier.of("bameclient", "textures/entity/coin.png");
    public static final Identifier WHEELCHAIR_TEXTURE = Identifier.of("bameclient", "textures/entity/wheelchair.png");

    private static ModelPart ballPart;
    private static ModelPart guitarPart;
    private static ModelPart coinPart;
    private static ModelPart wheelchairFramePart;
    private static ModelPart wheelchairWheelPart;

    public static final float JUGGLE_PERIOD = 9.0F;

    public EmotePropFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        initModel();
    }

    public static synchronized void initModel() {
        if (ballPart == null) {
            ModelData data = new ModelData();
            ModelPartData root = data.getRoot();
            // 8x8x8 cuboid with 64x32 mapping (soccer ball and basketball)
            root.addChild("ball",
                    ModelPartBuilder.create()
                            .uv(0, 0)
                            .cuboid(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                    ModelTransform.NONE
            );
            ballPart = TexturedModelData.of(data, 64, 32).createModel().getChild("ball");
        }

        if (guitarPart == null) {
            ModelData gData = new ModelData();
            ModelPartData gRoot = gData.getRoot();
            gRoot.addChild("body",
                    ModelPartBuilder.create()
                            .uv(0, 0)
                            .cuboid(-4.0F, 0.0F, -1.0F, 8.0F, 12.0F, 2.0F),
                    ModelTransform.NONE
            );
            gRoot.addChild("neck",
                    ModelPartBuilder.create()
                            .uv(24, 0)
                            .cuboid(-1.0F, -14.0F, -0.6F, 2.0F, 14.0F, 1.2F),
                    ModelTransform.NONE
            );
            gRoot.addChild("headstock",
                    ModelPartBuilder.create()
                            .uv(32, 0)
                            .cuboid(-1.5F, -18.0F, -0.6F, 3.0F, 4.0F, 1.2F),
                    ModelTransform.NONE
            );
            guitarPart = TexturedModelData.of(gData, 64, 32).createModel();
        }

        if (coinPart == null) {
            ModelData cData = new ModelData();
            ModelPartData cRoot = cData.getRoot();
            cRoot.addChild("coin",
                    ModelPartBuilder.create()
                            .uv(0, 0)
                            .cuboid(-6.0F, -6.0F, -1.0F, 12.0F, 12.0F, 2.0F),
                    ModelTransform.NONE
            );
            coinPart = TexturedModelData.of(cData, 64, 64).createModel().getChild("coin");
        }

        if (wheelchairFramePart == null) {
            ModelData wfData = new ModelData();
            ModelPartData wfRoot = wfData.getRoot();

            // Seat Cushion (elevated at Y = 13.0F, padded black)
            wfRoot.addChild("cushion",
                    ModelPartBuilder.create()
                            .uv(0, 0)
                            .cuboid(-6.0F, 13.0F, -6.0F, 12.0F, 2.0F, 12.0F),
                    ModelTransform.NONE
            );

            // Tall Backrest (matching Bild 2 up to Y = 0.5F)
            wfRoot.addChild("backrest",
                    ModelPartBuilder.create()
                            .uv(0, 14)
                            .cuboid(-6.0F, 0.5F, 4.0F, 12.0F, 13.0F, 2.0F),
                    ModelTransform.NONE
            );

            // Left & Right Push Handles with curved ergonomic tubes
            wfRoot.addChild("handle_l",
                    ModelPartBuilder.create()
                            .uv(28, 14)
                            .cuboid(-5.0F, -1.0F, 5.0F, 1.5F, 1.5F, 6.0F),
                    ModelTransform.NONE
            );
            wfRoot.addChild("handle_r",
                    ModelPartBuilder.create()
                            .uv(28, 14)
                            .cuboid(3.5F, -1.0F, 5.0F, 1.5F, 1.5F, 6.0F),
                    ModelTransform.NONE
            );

            // Left & Right Bottom Chassis Rails (elevated at Y = 14.5F)
            wfRoot.addChild("frame_l",
                    ModelPartBuilder.create()
                            .uv(28, 22)
                            .cuboid(-6.5F, 14.5F, -7.0F, 1.2F, 1.5F, 12.0F),
                    ModelTransform.NONE
            );
            wfRoot.addChild("frame_r",
                    ModelPartBuilder.create()
                            .uv(28, 22)
                            .cuboid(5.3F, 14.5F, -7.0F, 1.2F, 1.5F, 12.0F),
                    ModelTransform.NONE
            );

            // Back vertical silver frame posts
            wfRoot.addChild("post_l",
                    ModelPartBuilder.create()
                            .uv(28, 22)
                            .cuboid(-6.5F, 0.0F, 4.5F, 1.2F, 15.0F, 1.2F),
                    ModelTransform.NONE
            );
            wfRoot.addChild("post_r",
                    ModelPartBuilder.create()
                            .uv(28, 22)
                            .cuboid(5.3F, 0.0F, 4.5F, 1.2F, 15.0F, 1.2F),
                    ModelTransform.NONE
            );

            // Black Side Clothing Guards (Seitenschutzbleche matching Bild 2)
            wfRoot.addChild("guard_l",
                    ModelPartBuilder.create()
                            .uv(0, 29)
                            .cuboid(-6.8F, 7.5F, -4.0F, 0.6F, 7.5F, 9.0F),
                    ModelTransform.NONE
            );
            wfRoot.addChild("guard_r",
                    ModelPartBuilder.create()
                            .uv(0, 29)
                            .cuboid(6.2F, 7.5F, -4.0F, 0.6F, 7.5F, 9.0F),
                    ModelTransform.NONE
            );

            // Left & Right Armrest Pads (resting on top of guards at Y = 7.0F)
            wfRoot.addChild("armrest_l",
                    ModelPartBuilder.create()
                            .uv(42, 14)
                            .cuboid(-7.2F, 7.0F, -3.5F, 1.6F, 1.0F, 8.0F),
                    ModelTransform.NONE
            );
            wfRoot.addChild("armrest_r",
                    ModelPartBuilder.create()
                            .uv(42, 14)
                            .cuboid(5.6F, 7.0F, -3.5F, 1.6F, 1.0F, 8.0F),
                    ModelTransform.NONE
            );

            // Front footrest hanger tubes (curving down from seat to footplates)
            wfRoot.addChild("hanger_l",
                    ModelPartBuilder.create()
                            .uv(28, 22)
                            .cuboid(-5.0F, 15.0F, -10.0F, 1.2F, 6.0F, 3.5F),
                    ModelTransform.NONE
            );
            wfRoot.addChild("hanger_r",
                    ModelPartBuilder.create()
                            .uv(28, 22)
                            .cuboid(3.8F, 15.0F, -10.0F, 1.2F, 6.0F, 3.5F),
                    ModelTransform.NONE
            );
            wfRoot.addChild("footplate_l",
                    ModelPartBuilder.create()
                            .uv(0, 29)
                            .cuboid(-5.5F, 21.0F, -12.5F, 4.0F, 0.8F, 4.5F),
                    ModelTransform.NONE
            );
            wfRoot.addChild("footplate_r",
                    ModelPartBuilder.create()
                            .uv(0, 29)
                            .cuboid(1.5F, 21.0F, -12.5F, 4.0F, 0.8F, 4.5F),
                    ModelTransform.NONE
            );

            // Front Caster Forks & Wheels (ground clearance down to Y = 24.0)
            wfRoot.addChild("fork_l",
                    ModelPartBuilder.create()
                            .uv(48, 22)
                            .cuboid(-5.2F, 19.5F, -7.5F, 1.4F, 2.5F, 1.4F),
                    ModelTransform.NONE
            );
            wfRoot.addChild("fork_r",
                    ModelPartBuilder.create()
                            .uv(48, 22)
                            .cuboid(3.8F, 19.5F, -7.5F, 1.4F, 2.5F, 1.4F),
                    ModelTransform.NONE
            );
            wfRoot.addChild("caster_l",
                    ModelPartBuilder.create()
                            .uv(36, 48)
                            .cuboid(-5.4F, 21.5F, -8.5F, 1.8F, 2.5F, 2.5F),
                    ModelTransform.NONE
            );
            wfRoot.addChild("caster_r",
                    ModelPartBuilder.create()
                            .uv(36, 48)
                            .cuboid(3.6F, 21.5F, -8.5F, 1.8F, 2.5F, 2.5F),
                    ModelTransform.NONE
            );

            // Transverse Axle Connecting Drive Wheels
            wfRoot.addChild("axle",
                    ModelPartBuilder.create()
                            .uv(48, 22)
                            .cuboid(-6.5F, 13.4F, 0.5F, 13.0F, 1.2F, 1.2F),
                    ModelTransform.NONE
            );

            wheelchairFramePart = TexturedModelData.of(wfData, 64, 64).createModel();
        }

        if (wheelchairWheelPart == null) {
            ModelData wwData = new ModelData();
            ModelPartData wwRoot = wwData.getRoot();

            // Central Hub
            wwRoot.addChild("hub",
                    ModelPartBuilder.create()
                            .uv(0, 36)
                            .cuboid(-0.8F, -1.5F, -1.5F, 1.6F, 3.0F, 3.0F),
                    ModelTransform.NONE
            );

            // 8 Rim / Tire segments forming the rounded wheel
            wwRoot.addChild("rim_top",
                    ModelPartBuilder.create()
                            .uv(12, 36)
                            .cuboid(-0.6F, -9.5F, -5.0F, 1.2F, 1.5F, 10.0F),
                    ModelTransform.NONE
            );
            wwRoot.addChild("rim_bot",
                    ModelPartBuilder.create()
                            .uv(12, 36)
                            .cuboid(-0.6F, 8.0F, -5.0F, 1.2F, 1.5F, 10.0F),
                    ModelTransform.NONE
            );
            wwRoot.addChild("rim_fwd",
                    ModelPartBuilder.create()
                            .uv(12, 36)
                            .cuboid(-0.6F, -5.0F, -9.5F, 1.2F, 10.0F, 1.5F),
                    ModelTransform.NONE
            );
            wwRoot.addChild("rim_bwd",
                    ModelPartBuilder.create()
                            .uv(12, 36)
                            .cuboid(-0.6F, -5.0F, 8.0F, 1.2F, 10.0F, 1.5F),
                    ModelTransform.NONE
            );
            wwRoot.addChild("rim_c1",
                    ModelPartBuilder.create()
                            .uv(12, 36)
                            .cuboid(-0.6F, -8.0F, -8.0F, 1.2F, 3.0F, 3.0F),
                    ModelTransform.NONE
            );
            wwRoot.addChild("rim_c2",
                    ModelPartBuilder.create()
                            .uv(12, 36)
                            .cuboid(-0.6F, -8.0F, 5.0F, 1.2F, 3.0F, 3.0F),
                    ModelTransform.NONE
            );
            wwRoot.addChild("rim_c3",
                    ModelPartBuilder.create()
                            .uv(12, 36)
                            .cuboid(-0.6F, 5.0F, -8.0F, 1.2F, 3.0F, 3.0F),
                    ModelTransform.NONE
            );
            wwRoot.addChild("rim_c4",
                    ModelPartBuilder.create()
                            .uv(12, 36)
                            .cuboid(-0.6F, 5.0F, 5.0F, 1.2F, 3.0F, 3.0F),
                    ModelTransform.NONE
            );

            // Chrome Spokes
            wwRoot.addChild("spoke_v",
                    ModelPartBuilder.create()
                            .uv(36, 36)
                            .cuboid(-0.2F, -8.0F, -0.4F, 0.4F, 16.0F, 0.8F),
                    ModelTransform.NONE
            );
            wwRoot.addChild("spoke_h",
                    ModelPartBuilder.create()
                            .uv(36, 36)
                            .cuboid(-0.2F, -0.4F, -8.0F, 0.4F, 0.8F, 16.0F),
                    ModelTransform.NONE
            );
            wwRoot.addChild("spoke_d1",
                    ModelPartBuilder.create()
                            .uv(36, 36)
                            .cuboid(-0.2F, -5.5F, -5.5F, 0.4F, 11.0F, 0.6F),
                    ModelTransform.NONE
            );
            wwRoot.addChild("spoke_d2",
                    ModelPartBuilder.create()
                            .uv(36, 36)
                            .cuboid(-0.2F, -5.5F, 4.9F, 0.4F, 11.0F, 0.6F),
                    ModelTransform.NONE
            );

            // Outer Handrim (Greifreifen)
            wwRoot.addChild("handrim_top",
                    ModelPartBuilder.create()
                            .uv(0, 48)
                            .cuboid(0.7F, -8.0F, -4.0F, 0.6F, 0.8F, 8.0F),
                    ModelTransform.NONE
            );
            wwRoot.addChild("handrim_bot",
                    ModelPartBuilder.create()
                            .uv(0, 48)
                            .cuboid(0.7F, 7.2F, -4.0F, 0.6F, 0.8F, 8.0F),
                    ModelTransform.NONE
            );
            wwRoot.addChild("handrim_fwd",
                    ModelPartBuilder.create()
                            .uv(0, 48)
                            .cuboid(0.7F, -4.0F, -8.0F, 0.6F, 8.0F, 0.8F),
                    ModelTransform.NONE
            );
            wwRoot.addChild("handrim_bwd",
                    ModelPartBuilder.create()
                            .uv(0, 48)
                            .cuboid(0.7F, -4.0F, 7.2F, 0.6F, 8.0F, 0.8F),
                    ModelTransform.NONE
            );

            wheelchairWheelPart = TexturedModelData.of(wwData, 64, 64).createModel();
        }
    }

    @Override
    public void render(
            MatrixStack matrices,
            OrderedRenderCommandQueue commandQueue,
            int light,
            PlayerEntityRenderState state,
            float limbAngle,
            float limbDistance
    ) {
        renderDirectProp(matrices, commandQueue, light, state);
    }

    public static void renderDirectProp(
            MatrixStack matrices,
            OrderedRenderCommandQueue commandQueue,
            int light,
            PlayerEntityRenderState state
    ) {
        if (state == null || state.invisible) return;
        initModel();
        if (ballPart == null) return;

        EmoteType emote = null;
        float t = 0.0F;

        if (state instanceof EmotePreviewHolder holder && holder.bame$getPreviewEmote() != null) {
            emote = holder.bame$getPreviewEmote();
            t = holder.bame$getPreviewTime();
        } else {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                boolean isLocal = (state.id == client.player.getId());
                String rawUsername = com.bame.client.cosmetics.CaeserCosmeticsRenderer.getPlayerUsername(state);
                if (!isLocal && rawUsername != null && client.getSession() != null) {
                    String cleanStateName = com.bame.client.cosmetics.CaeserPlayerSyncManager.cleanUsername(rawUsername);
                    String cleanLocalName = com.bame.client.cosmetics.CaeserPlayerSyncManager.cleanUsername(client.getSession().getUsername());
                    if (cleanStateName != null && cleanStateName.equalsIgnoreCase(cleanLocalName)) {
                        isLocal = true;
                    }
                }
                if (isLocal && EmoteModule.isEmoting()) {
                    emote = EmoteModule.getCurrentEmote();
                    t = EmoteModule.getElapsedSeconds();
                } else if (!isLocal) {
                    com.bame.client.cosmetics.CaeserPlayerSyncManager.PlayerEmoteData remoteEmote =
                            com.bame.client.cosmetics.CaeserPlayerSyncManager.getActiveEmote(rawUsername);
                    if (remoteEmote != null && !remoteEmote.isExpired()) {
                        emote = remoteEmote.emote;
                        t = remoteEmote.getElapsedSeconds();
                    }
                }
            }
        }

        int actualLight = light > 0 ? light : 0x00F000F0;

        if (emote == EmoteType.BASKETBALL) {
            KeyframeAnimation anim = EmoteAnimationLoader.getBasketballAnimation();
            if (anim == null) return;

            float[] ballRot = new float[3];
            float[] ballPos = new float[3];
            anim.sample("ball", t, ballRot, ballPos);

            matrices.push();

            float ballX = ballPos[0] / 16.0F;
            float ballY = (21.0F - ballPos[1]) / 16.0F;
            float ballZ = (-16.0F + ballPos[2]) / 16.0F;

            matrices.translate(ballX, ballY, ballZ);

            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(ballRot[2]));
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(ballRot[1]));
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(ballRot[0]));

            matrices.scale(0.85F, 0.85F, 0.85F);

            RenderLayer layer = RenderLayers.entityCutoutNoCull(BASKETBALL_TEXTURE);

            commandQueue.submitModelPart(
                    ballPart,
                    matrices,
                    layer,
                    actualLight,
                    OverlayTexture.DEFAULT_UV,
                    null
            );

            matrices.pop();
            return;
        }

        if (emote == EmoteType.WHEELCHAIR) {
            if (wheelchairFramePart == null || wheelchairWheelPart == null) return;
            matrices.push();

            // Wheel rotation angle based on movement speed & distance
            float limbAmp = state.limbSwingAmplitude;
            float limbProg = state.limbSwingAnimationProgress;
            float wheelAngle;
            if (limbAmp > 0.02F) {
                // Rotates calmly and realistically when driving / rolling
                wheelAngle = limbProg * 18.0F;
            } else {
                wheelAngle = 0.0F;
            }

            RenderLayer layer = RenderLayers.entityCutoutNoCull(WHEELCHAIR_TEXTURE);

            // 1. Render the main static wheelchair frame (chassis, seat, backrest, footrests, casters)
            commandQueue.submitModelPart(
                    wheelchairFramePart,
                    matrices,
                    layer,
                    actualLight,
                    OverlayTexture.DEFAULT_UV,
                    null
            );

            // 2. Render Left Drive Wheel (placed at X = -7.2, Y = 14.0, Z = 1.0, scale 1.05F for realistic proportions)
            matrices.push();
            matrices.translate(-7.2F / 16.0F, 14.0F / 16.0F, 1.0F / 16.0F);
            // Rotate around wheel axle
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(wheelAngle));
            // Flipped so handrim is on outer left side
            matrices.scale(-1.05F, 1.05F, 1.05F);
            commandQueue.submitModelPart(
                    wheelchairWheelPart,
                    matrices,
                    layer,
                    actualLight,
                    OverlayTexture.DEFAULT_UV,
                    null
            );
            matrices.pop();

            // 3. Render Right Drive Wheel (placed at X = +7.2, Y = 14.0, Z = 1.0, scale 1.05F for realistic proportions)
            matrices.push();
            matrices.translate(7.2F / 16.0F, 14.0F / 16.0F, 1.0F / 16.0F);
            // Rotate around wheel axle
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(wheelAngle));
            // Handrim on outer right side
            matrices.scale(1.05F, 1.05F, 1.05F);
            commandQueue.submitModelPart(
                    wheelchairWheelPart,
                    matrices,
                    layer,
                    actualLight,
                    OverlayTexture.DEFAULT_UV,
                    null
            );
            matrices.pop();

            matrices.pop();
            return;
        }

        if (emote != EmoteType.JUGGLE) return;

        KeyframeAnimation anim = EmoteAnimationLoader.getJuggleAnimation();
        if (anim == null) return;

        float[] ballRot = new float[3];
        float[] ballPos = new float[3];
        anim.sample("ball", t, ballRot, ballPos);

        matrices.push();

        // Exact Bedrock -> Java model coordinate mapping
        float ballX = ballPos[0] / 16.0F;
        float ballY = (21.0F - ballPos[1]) / 16.0F;
        float ballZ = (-16.0F + ballPos[2]) / 16.0F;

        matrices.translate(ballX, ballY, ballZ);

        // Rotations in ZYX order (matching Blockbench/Bedrock)
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(ballRot[2]));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(ballRot[1]));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(ballRot[0]));

        // Scale 8x8x8 model to 6.0 units (0.375m diameter matching football_freestyle.geo.json)
        matrices.scale(0.75F, 0.75F, 0.75F);

        RenderLayer layer = RenderLayers.entityCutoutNoCull(SOCCER_TEXTURE);

        commandQueue.submitModelPart(
                ballPart,
                matrices,
                layer,
                actualLight,
                OverlayTexture.DEFAULT_UV,
                null
        );

        matrices.pop();
    }
}
