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
    private static ModelPart ballPart;

    public static final float JUGGLE_PERIOD = 9.0F;

    public EmotePropFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        initModel();
    }

    public static synchronized void initModel() {
        if (ballPart == null) {
            ModelData data = new ModelData();
            ModelPartData root = data.getRoot();
            // 8x8x8 cuboid with 64x32 Telstar texture mapping
            root.addChild("ball",
                    ModelPartBuilder.create()
                            .uv(0, 0)
                            .cuboid(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                    ModelTransform.NONE
            );
            ballPart = TexturedModelData.of(data, 64, 32).createModel().getChild("ball");
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
                boolean isLocal = false;
                if (state.playerName != null && state.playerName.getString().equalsIgnoreCase(client.getSession().getUsername())) {
                    isLocal = true;
                } else if (state.id == client.player.getId()) {
                    isLocal = true;
                }
                if (isLocal && EmoteModule.isEmoting()) {
                    emote = EmoteModule.getCurrentEmote();
                    t = EmoteModule.getElapsedSeconds();
                }
            }
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

        int actualLight = light > 0 ? light : 0x00F000F0;
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
