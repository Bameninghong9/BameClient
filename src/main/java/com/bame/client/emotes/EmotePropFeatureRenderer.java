package com.bame.client.emotes;

import com.bame.client.cosmetics.BedrockCosmeticModel;
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
import net.minecraft.util.math.RotationAxis;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;

public class EmotePropFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    public static final Identifier SOCCER_TEXTURE = Identifier.of("bameclient", "textures/entity/soccer_ball.png");
    public static final Identifier BASKETBALL_TEXTURE = Identifier.of("bameclient", "textures/entity/basketball.png");
    public static final Identifier GUITAR_TEXTURE = Identifier.of("bameclient", "textures/entity/electric_guitar.png");
    public static final Identifier COIN_TEXTURE = Identifier.of("bameclient", "textures/entity/coin.png");
    public static final Identifier WHEELCHAIR_TEXTURE = Identifier.of("bameclient", "textures/entity/wheelchair.png");
    public static final Identifier BUTTERFLY_KNIFE_TEXTURE = Identifier.of("bameclient", "textures/entity/butterfly_knife.png");
    public static final Identifier ROASTING_STICK_TEXTURE = Identifier.of("bameclient", "textures/entity/roasting_stick.png");
    private static final String ROASTING_STICK_MODEL = "assets/bameclient/emotes/campfire/roasting_stick.geo.json";
    public static final Identifier SEAT_LOG_TEXTURE = Identifier.of("bameclient", "textures/entity/campfire_seat_log.png");
    public static final Identifier CAMPFIRE_TEXTURE = Identifier.of("bameclient", "textures/entity/campfire_base.png");
    public static final Identifier CAMPFIRE_FLAMES_TEXTURE = Identifier.of("bameclient", "textures/entity/campfire_flames.png");
    /** Weicher Flammenrand (gleiche UVs wie {@link #CAMPFIRE_FLAMES_TEXTURE}), translucent über dem Cutout-Kern. */
    public static final Identifier CAMPFIRE_FLAMES_GLOW_TEXTURE = Identifier.of("bameclient", "textures/entity/campfire_flames_glow.png");
    private static final String SEAT_LOG_MODEL = "assets/bameclient/emotes/campfire/seat_log.geo.json";
    private static final String CAMPFIRE_MODEL = "assets/bameclient/emotes/campfire/campfire.geo.json";
    private static final String CAMPFIRE_FLAMES_MODEL = "assets/bameclient/emotes/campfire/campfire_flames.geo.json";
    public static final Identifier HAMMOCK_POST_TEXTURE = Identifier.of("bameclient", "textures/entity/hammock_post.png");
    public static final Identifier HAMMOCK_SLING_TEXTURE = Identifier.of("bameclient", "textures/entity/hammock_sling.png");
    private static final String HAMMOCK_POST_MODEL = "assets/bameclient/emotes/hammock/hammock_post.geo.json";
    private static final String HAMMOCK_SLING_MODEL = "assets/bameclient/emotes/hammock/hammock_sling.geo.json";

    private static ModelPart ballPart;
    private static ModelPart guitarPart;
    private static ModelPart coinPart;
    private static ModelPart wheelchairFramePart;
    private static ModelPart wheelchairWheelPart;
    // Butterfly-Messer: drei getrennte Teile, die sich um den gemeinsamen Pivot (Ursprung) drehen
    private static ModelPart knifeBladePart;
    private static ModelPart knifeSafeHandlePart;
    private static ModelPart knifeBiteHandlePart;
    private static ModelPart knifeGlintPart;

    private static final int KNIFE_TEX_W = 256, KNIFE_TEX_H = 128;
    /** Bewegungsspur: Anzahl Geisterkopien der Klinge, zeitlicher Abstand und Deckkraft (bei voller Staerke). */
    private static final int TRAIL_GHOSTS = 3;
    private static final float TRAIL_DT = 0.012F;
    private static final float TRAIL_ALPHA = 0.42F;
    /** Glanzlicht: Bereich entlang der Klinge (Modell-Einheiten, Pivot -> Spitze) und Helligkeit. */
    private static final float GLINT_Z_START = -6.0F, GLINT_Z_END = -43.0F;
    private static final float GLINT_BRIGHTNESS = 0.95F;
    // Klingenprofil fuer das Glanzlicht: z, Hoehe, Mitte (Einheiten), siehe Klingen-Cuboids
    private static final float[] PROFILE_Z = {-8.0F, -32.0F, -35.0F, -40.0F, -43.5F};
    private static final float[] PROFILE_H = {8.0F, 8.0F, 6.0F, 4.0F, 2.0F};
    private static final float[] PROFILE_C = {0.0F, 0.0F, 1.0F, 2.0F, 2.5F};

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

        if (knifeBladePart == null) {
            // Butterfly-Messer (Balisong) in 1/8-Pixel-Einheiten (Renderer skaliert mit 1/8), Pivot im Ursprung.
            // Offen: Klinge entlang -Z, Griffe entlang +Z; Klingenebene = YZ (Dicke entlang X), Schneide auf +Y.
            // Safe-Griff auf +X, Bite-Griff (mit Verschluss) auf -X; zugeklappt liegt die Klinge zwischen beiden.
            // UVs + Textur (256x128) werden gemeinsam von einem Node-Skript erzeugt (Atlas-Packing, texelweise Bemalung
            // nach Weltposition) - bei Geometrieaenderungen beides zusammen neu erzeugen.
            knifeBladePart = singleKnifePart(ModelPartBuilder.create()
                    .uv(0, 58).cuboid(-1.0F, -4.0F, -8.0F, 2.0F, 8.0F, 12.0F)        // Angel/Ricasso mit Pivot-Loch
                    .uv(147, 58).cuboid(-1.0F, 4.0F, -6.0F, 2.0F, 2.0F, 4.0F)        // Kicker (schuetzt die Schneide)
                    .uv(202, 0).cuboid(-1.0F, -4.0F, -32.0F, 2.0F, 8.0F, 24.0F)      // Klinge mit Hohlkehle + Schliff
                    .uv(78, 58).cuboid(-1.0F, -2.0F, -38.0F, 2.0F, 6.0F, 6.0F)       // Clip-Point, Stufe 1
                    .uv(95, 58).cuboid(-1.0F, 0.0F, -42.0F, 2.0F, 4.0F, 4.0F)        // Clip-Point, Stufe 2
                    .uv(178, 58).cuboid(-0.5F, 1.5F, -45.0F, 1.0F, 2.0F, 3.0F));     // Spitze
            knifeSafeHandlePart = singleKnifePart(ModelPartBuilder.create()
                    .uv(0, 0).cuboid(1.25F, -5.0F, -4.0F, 3.0F, 10.0F, 47.0F)        // Griffschale (Skelett-Schlitze, Liner)
                    .uv(108, 58).cuboid(1.25F, -3.0F, -6.0F, 3.0F, 6.0F, 2.0F)       // abgerundetes Pivot-Ende
                    .uv(29, 58).cuboid(1.25F, -5.5F, 43.0F, 3.0F, 11.0F, 4.0F)       // Endkappe
                    .uv(160, 58).cuboid(4.25F, -1.5F, -1.5F, 1.0F, 3.0F, 3.0F)       // Pivot-Pin
                    .uv(187, 58).cuboid(4.25F, -1.0F, 39.0F, 1.0F, 2.0F, 2.0F));     // Schraube
            knifeBiteHandlePart = singleKnifePart(ModelPartBuilder.create()
                    .uv(101, 0).cuboid(-4.25F, -5.0F, -4.0F, 3.0F, 10.0F, 47.0F)
                    .uv(119, 58).cuboid(-4.25F, -3.0F, -6.0F, 3.0F, 6.0F, 2.0F)
                    .uv(44, 58).cuboid(-4.25F, -5.5F, 43.0F, 3.0F, 11.0F, 4.0F)
                    .uv(169, 58).cuboid(-5.25F, -1.5F, -1.5F, 1.0F, 3.0F, 3.0F)
                    .uv(194, 58).cuboid(-5.25F, -1.0F, 39.0F, 1.0F, 2.0F, 2.0F)
                    .uv(130, 58).cuboid(-1.0F, 4.0F, 40.0F, 2.0F, 1.0F, 6.0F));      // Verschluss (Latch)
            // Glanzlicht: weicher heller Streifen, wird entlang der Klinge verschoben und additiv (eyes) gezeichnet
            knifeGlintPart = singleKnifePart(ModelPartBuilder.create()
                    .uv(59, 58).cuboid(-1.5F, -4.5F, -3.0F, 3.0F, 9.0F, 6.0F));
        }
    }

    private static ModelPart singleKnifePart(ModelPartBuilder builder) {
        ModelData data = new ModelData();
        data.getRoot().addChild("part", builder, ModelTransform.NONE);
        return TexturedModelData.of(data, KNIFE_TEX_W, KNIFE_TEX_H).createModel();
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
        renderDirectProp(matrices, commandQueue, light, state, this.getContextModel());
    }

    public static void renderDirectProp(
            MatrixStack matrices,
            OrderedRenderCommandQueue commandQueue,
            int light,
            PlayerEntityRenderState state
    ) {
        renderDirectProp(matrices, commandQueue, light, state, null);
    }

    /**
     * @param model das bereits gepostete Spielermodell; nötig für Props in der Hand (Butterfly-Messer),
     *              die der Armtransformation folgen. Ohne Modell werden solche Props übersprungen.
     */
    public static void renderDirectProp(
            MatrixStack matrices,
            OrderedRenderCommandQueue commandQueue,
            int light,
            PlayerEntityRenderState state,
            PlayerEntityModel model
    ) {
        if (state == null || state.invisible) return;
        initModel();
        if (ballPart == null) return;

        // Gleiche Auflösung wie EmoteAnimator (Vorschau, lokaler Spieler, fremder Caeser-Spieler)
        EmoteAnimator.ActiveEmote active = null;
        if ((state instanceof EmotePreviewHolder holder && holder.bame$getPreviewEmote() != null)
                || MinecraftClient.getInstance().player != null) {
            active = EmoteAnimator.resolveActiveEmote(state);
        }
        EmoteType emote = active != null ? active.emote() : null;
        float t = active != null ? active.t() : 0.0F;

        int actualLight = light > 0 ? light : 0x00F000F0;

        if (emote == EmoteType.BUTTERFLY) {
            if (model != null) {
                renderButterflyKnife(matrices, commandQueue, actualLight, model, t);
            }
            return;
        }

        if (emote == EmoteType.CAMPFIRE) {
            // Nur über den Feature-Renderer (mit gepostetem Modell): so wird alles genau einmal gezeichnet
            if (model != null) {
                renderCampfire(matrices, commandQueue, actualLight, state, model, active);
            }
            return;
        }

        if (emote == EmoteType.HAMMOCK) {
            // Wie beim Lagerfeuer nur über den Feature-Renderer, damit alles genau einmal gezeichnet wird
            if (model != null) {
                renderHammock(matrices, commandQueue, actualLight, state, active);
            }
            return;
        }

        if (emote == EmoteType.SWING) {
            // Wie bei der Hängematte nur über den Feature-Renderer, damit alles genau einmal gezeichnet wird
            if (model != null) {
                renderSwing(matrices, commandQueue, actualLight, state, active);
            }
            return;
        }

        if (emote == EmoteType.PICNIC) {
            if (model != null) {
                renderPicnic(matrices, commandQueue, actualLight, state, model, active);
            }
            return;
        }

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

    /**
     * Butterfly-Messer in der rechten Hand: folgt der Armtransformation des (bereits geposten) Modells, daher auch
     * für andere Spieler und in der Menü-Vorschau an der Hand. Klappen/Rollen/Wurf kommen aus ButterflyKnifeAnimation.
     */
    private static void renderButterflyKnife(
            MatrixStack matrices,
            OrderedRenderCommandQueue commandQueue,
            int light,
            PlayerEntityModel model,
            float t
    ) {
        if (knifeBladePart == null || knifeSafeHandlePart == null || knifeBiteHandlePart == null || knifeGlintPart == null) return;
        ModelPart arm = model.rightArm;
        if (arm == null || !arm.visible) return;

        // Slim-Arme (Pivot-Y 2.5 statt 2.0) sind schmaler, die Handmitte liegt weiter innen
        boolean slim = arm.getDefaultTransform().y() > 2.25F;
        ButterflyKnifeAnimation.Pose pose = new ButterflyKnifeAnimation.Pose();
        ButterflyKnifeAnimation.sample(t, pose);

        RenderLayer layer = RenderLayers.entityCutoutNoCull(BUTTERFLY_KNIFE_TEXTURE);
        float partScale = ButterflyKnifeAnimation.KNIFE_SCALE / ButterflyKnifeAnimation.UNITS_PER_PIXEL;

        matrices.push();
        pushKnifeRoot(matrices, arm, slim, pose);
        matrices.scale(partScale, partScale, partScale);

        // Safe-Griff: liegt in der Hand (klappt nur beim Chaplin kurz auf)
        matrices.push();
        if (pose.safeAngle != 0.0F) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.safeAngle));
        commandQueue.submitModelPart(knifeSafeHandlePart, matrices, layer, light, OverlayTexture.DEFAULT_UV, null);
        matrices.pop();

        // Klinge: dreht um den Pivot (180 = zwischen den Griffen, 0 = offen)
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.bladeAngle));
        commandQueue.submitModelPart(knifeBladePart, matrices, layer, light, OverlayTexture.DEFAULT_UV, null);
        if (pose.glint >= 0.0F && pose.glintStrength > 0.01F) {
            renderKnifeGlint(matrices, commandQueue, pose.glint, pose.glintStrength);
        }
        matrices.pop();

        // Bite-Griff: macht beim Auf-/Zuklappen eine volle Umdrehung
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.biteAngle));
        commandQueue.submitModelPart(knifeBiteHandlePart, matrices, layer, light, OverlayTexture.DEFAULT_UV, null);
        matrices.pop();

        matrices.pop();

        // Bewegungsspur bei den schnellsten Drehungen: blasse, transluzente Kopien der Klinge an frueheren Zeitpunkten
        if (pose.trail > 0.02F && t > ButterflyKnifeAnimation.BLEND_IN + TRAIL_GHOSTS * TRAIL_DT) {
            RenderLayer ghostLayer = RenderLayers.entityTranslucent(BUTTERFLY_KNIFE_TEXTURE);
            ButterflyKnifeAnimation.Pose ghost = new ButterflyKnifeAnimation.Pose();
            for (int k = 1; k <= TRAIL_GHOSTS; k++) {
                ButterflyKnifeAnimation.sample(t - k * TRAIL_DT, ghost);
                float alpha = pose.trail * TRAIL_ALPHA * (1.0F - (k - 1.0F) / TRAIL_GHOSTS);
                int a = Math.round(alpha * 255.0F);
                if (a < 4) continue;
                matrices.push();
                pushKnifeRoot(matrices, arm, slim, ghost);
                matrices.scale(partScale, partScale, partScale);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(ghost.bladeAngle));
                commandQueue.submitModelPart(knifeBladePart, matrices, ghostLayer, light, OverlayTexture.DEFAULT_UV,
                        null, (a << 24) | 0xD8E4FF, null);
                matrices.pop();
            }
        }
    }

    // ------------------------------------------------------------------
    // Lagerfeuer
    // ------------------------------------------------------------------

    private static BedrockCosmeticModel roastingStickModel;
    private static boolean roastingStickLoaded;
    private static BedrockCosmeticModel seatLogModel, campfireModel, flamesModel;
    private static boolean campfireModelsLoaded;

    /** Flammen: Anzahl Frames im Textur-Streifen (untereinander). */
    private static final int FLAME_FRAMES = 16;
    /**
     * Flammen-Ebenen (je ein Knochen f0..f7): eigener Frame-Versatz und leicht unterschiedliche Frame-Dauer (ms), damit
     * die Zungen unabhängig voneinander flackern und sich das Bild nicht sichtbar wiederholt.
     */
    private static final String[] FLAME_BONES = new String[CampfireMotion.FLAME_PLANES];
    private static final int[] FLAME_OFFSETS = {0, 5, 11, 3, 8, 14, 2, 9};
    private static final long[] FLAME_FRAME_MS = {58L, 66L, 74L};

    static {
        for (int i = 0; i < FLAME_BONES.length; i++) FLAME_BONES[i] = "f" + i;
    }
    private static final int FULL_BRIGHT = 0x00F000F0;
    /** Bedrock-Modelle: Boden (y = 0) liegt im Java-Modellraum 24 px unter dem Ursprung. */
    private static final float BEDROCK_GROUND = 24.0F / 16.0F;
    /** Flammen-Ebenen mit Normale nach oben (Java-Modellraum, +Y unten): gleichmäßig hell aus allen Richtungen. */
    private static final float[] UP_NORMAL = {0.0F, -1.0F, 0.0F};

    private static final CampfireMotion.Pose CF_POSE = new CampfireMotion.Pose();
    private static final CampfireMotion.Stick CF_STICK = new CampfireMotion.Stick();
    private static final double[] CF_FIRE = new double[3];
    private static final double[] CF_TARGET = new double[3];
    /** Modellraum ist mit 0.9375 skaliert (Spieler); Blöcke sollen echte Weltgröße haben. */
    private static final float MODEL_SCALE = 0.9375F;

    private static BedrockCosmeticModel roastingStick() {
        if (!roastingStickLoaded) {
            roastingStickLoaded = true;
            roastingStickModel = BedrockCosmeticModel.load(ROASTING_STICK_MODEL);
            if (roastingStickModel != null) roastingStickModel.blockbenchAxisFix = true;
        }
        return roastingStickModel;
    }

    /** Sitzstamm, Feuer (Knochen "base" und "embers") und Flammen (animiert) einmalig laden. */
    private static void loadCampfireModels() {
        if (campfireModelsLoaded) return;
        campfireModelsLoaded = true;
        seatLogModel = loadProp(SEAT_LOG_MODEL);
        campfireModel = loadProp(CAMPFIRE_MODEL);
        flamesModel = loadProp(CAMPFIRE_FLAMES_MODEL);
        if (flamesModel != null) flamesModel.fixedNormal = UP_NORMAL;
    }

    private static BedrockCosmeticModel loadProp(String path) {
        BedrockCosmeticModel m = BedrockCosmeticModel.load(path);
        if (m != null) m.blockbenchAxisFix = true;
        return m;
    }

    /** Glut-Puls 0..1 (zwei überlagerte Sinus); {@code phase} versetzt ihn für die zweite Glut-Gruppe. */
    private static double emberPulse(double phase) {
        double now = System.currentTimeMillis() / 1000.0 + phase;
        double w = 0.5 + 0.3 * Math.sin(now * 2.3) + 0.2 * Math.sin(now * 5.9 + 1.7);
        return Math.max(0.0, Math.min(1.0, w));
    }

    /** Glut: leichtes, langsames Pulsieren (Farb-Tint 0,84..1). */
    private static int emberTint(double pulse) {
        int g = (int) Math.round(255.0 * (0.84 + 0.16 * pulse));
        return 0xFF000000 | (g << 16) | (g << 8) | g;
    }

    /** Hitzeschein am Boden: Deckkraft folgt der Glut (Vertex-Alpha 0,7..1, multipliziert mit der Textur). */
    private static int heatColor(double pulse) {
        int a = (int) Math.round(255.0 * (0.7 + 0.3 * pulse));
        return (a << 24) | 0x00FFFFFF;
    }

    /**
     * Lagerfeuer: eigener Sitzstamm unter dem Spieler, eigenes Lagerfeuer vor ihm (nur beim Gastgeber der Runde) und
     * der Röststock in der rechten Hand. Alle drei sind Bedrock-Modelle ({@code emotes/campfire/*.geo.json}), eingereicht
     * wie der Stock über {@code getBatchingQueue(0).submitCustom}:
     * <ul>
     *   <li>Stamm: Entity-Cutout (ohne Culling) mit dem Licht des Spielers (am Feuer aufgehellt).</li>
     *   <li>Feuer: Knochen {@code base} (Steinring, Holz-Enden, Asche) mit demselben Licht, {@code embers} und
     *       {@code embers_b} (Glut, brennende Holzteile) voll hell mit gegeneinander versetztem Pulsieren, {@code heat}
     *       (Hitzeschein am Boden) translucent. Flammen: eigenes Modell, 8 Ebenen (Knochen f0..f7, je eigener Frame
     *       aus 16 im Textur-Streifen), Kern voll hell im Cutout-Layer, weicher Rand translucent darüber.</li>
     * </ul>
     * Die Modelle sind in echter Weltgröße gebaut (1 Modell-px = 1/16 Block, Boden bei y = 0) und werden gegen die
     * Spieler-Skalierung (0,9375) zurückskaliert. Positionen kommen aus {@link CampfireCoordinator#frame}: weltfest,
     * unabhängig vom aktuellen Körper-Yaw.
     */
    private static void renderCampfire(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                       PlayerEntityRenderState state, PlayerEntityModel model,
                                       EmoteAnimator.ActiveEmote active) {
        if (active == null) return;
        CampfireCoordinator.Frame frame = CampfireCoordinator.frame(state, active);
        if (frame == null) return; // fremder Spieler noch keiner Runde zugeordnet (höchstens ein Tick)
        loadCampfireModels();
        float t = active.t();
        CampfireMotion.Pose p = CF_POSE;
        CampfireMotion.sample(t, CampfireCoordinator.burns(frame, t), CampfireMotion.farness(frame.fireDistance), p);
        float baseScale = state.baseScale > 0.0F ? state.baseScale : 1.0F;
        float ground = (float) (CampfireMotion.MODEL_ORIGIN_UP_PX / 16.0);

        // 1. Stamm: quer unter dem Spieler (Länge entlang X), Oberseite auf Sitzhöhe, mit dem Spieler skaliert
        float ps = p.propScale;
        BedrockCosmeticModel logModel = seatLogModel;
        if (ps > 0.01F && logModel != null) {
            matrices.push();
            if (frame.logYaw != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(frame.logYaw));
            matrices.translate(0.0F, ground, CampfireMotion.LOG_BACK_PX / 16.0F);
            float ls = ps / MODEL_SCALE;
            matrices.scale(ls, ls, ls);
            matrices.translate(0.0F, -BEDROCK_GROUND, 0.0F);
            final int logLight = light;
            queue.getBatchingQueue(0).submitCustom(matrices, RenderLayers.entityCutoutNoCull(SEAT_LOG_TEXTURE),
                    (entry, consumer) -> logModel.render(entry, consumer, logLight, 0, 1, false, 0xFFFFFFFF, null));
            matrices.pop();
        }

        // 2. Feuer: nur der Gastgeber (bzw. die Vorschau) zeichnet es, in echter Weltgröße; Glut und Flammen voll hell
        BedrockCosmeticModel fireModel = campfireModel;
        BedrockCosmeticModel flames = flamesModel;
        if (frame.drawFire && frame.fireScale > 0.01F && fireModel != null) {
            float fs = frame.fireScale / (MODEL_SCALE * baseScale);
            matrices.push();
            matrices.translate(frame.fireBase[0] / 16.0, frame.fireBase[1] / 16.0, frame.fireBase[2] / 16.0);
            if (frame.fireYaw != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(frame.fireYaw));
            matrices.scale(fs, fs, fs);
            matrices.translate(0.0F, -BEDROCK_GROUND, 0.0F);
            RenderLayer fireLayer = RenderLayers.entityCutoutNoCull(CAMPFIRE_TEXTURE);
            final int baseLight = light;
            double pulseA = emberPulse(0.0), pulseB = emberPulse(1.37);
            final int tintA = emberTint(pulseA), tintB = emberTint(pulseB);
            // Steine, Holz-Enden und Asche beleuchtet; Glut, brennende Holzteile und Kohlen voll hell, in zwei Gruppen
            // mit versetztem Puls (ungleichmäßiges Glühen)
            queue.getBatchingQueue(0).submitCustom(matrices, fireLayer, (entry, consumer) -> {
                fireModel.render(entry, consumer, baseLight, 0, 1, false, 0xFFFFFFFF, "base");
                fireModel.render(entry, consumer, FULL_BRIGHT, 0, 1, false, tintA, "embers");
                fireModel.render(entry, consumer, FULL_BRIGHT, 0, 1, false, tintB, "embers_b");
            });
            // Hitzeschein: flache, weiche Scheibe über der Asche. Translucent ohne Tiefenschreiben, liegt flach unter
            // allem anderen (Glut und Holz verdecken sie per Tiefentest), daher keine Sortierprobleme.
            final int heat = heatColor(pulseA);
            queue.getBatchingQueue(0).submitCustom(matrices, RenderLayers.entityTranslucentEmissive(CAMPFIRE_TEXTURE, false),
                    (entry, consumer) -> fireModel.render(entry, consumer, FULL_BRIGHT, 0, 1, false, heat, "heat"));
            if (flames != null) {
                // Jede Ebene mit eigenem Frame (Versatz + leicht andere Geschwindigkeit)
                long now = System.currentTimeMillis();
                final int[] frames = new int[FLAME_BONES.length];
                for (int i = 0; i < frames.length; i++) {
                    frames[i] = (int) ((now / FLAME_FRAME_MS[i % FLAME_FRAME_MS.length] + FLAME_OFFSETS[i]) % FLAME_FRAMES);
                }
                // Kern im Cutout-Layer (deckend, schreibt Tiefe: keine Sortierprobleme zwischen den gekreuzten Ebenen),
                // danach der weiche Rand translucent und ohne Tiefenschreiben (geringe Deckkraft, Reihenfolge fällt kaum
                // auf; Ebenen hinter dem Kern werden vom Tiefentest verdeckt)
                queue.getBatchingQueue(0).submitCustom(matrices, RenderLayers.entityCutoutNoCull(CAMPFIRE_FLAMES_TEXTURE),
                        (entry, consumer) -> {
                            for (int i = 0; i < FLAME_BONES.length; i++) {
                                flames.render(entry, consumer, FULL_BRIGHT, frames[i], FLAME_FRAMES, false, 0xFFFFFFFF, FLAME_BONES[i]);
                            }
                        });
                queue.getBatchingQueue(0).submitCustom(matrices, RenderLayers.entityTranslucentEmissive(CAMPFIRE_FLAMES_GLOW_TEXTURE, false),
                        (entry, consumer) -> {
                            for (int i = 0; i < FLAME_BONES.length; i++) {
                                flames.render(entry, consumer, FULL_BRIGHT, frames[i], FLAME_FRAMES, false, 0xFFFFFFFF, FLAME_BONES[i]);
                            }
                        });
            }
            matrices.pop();
        }

        // 3. Röststock in der rechten Hand: folgt der Armtransformation, Spitze zeigt zum Ziel (über die Flammen)
        BedrockCosmeticModel stick = roastingStick();
        ModelPart arm = model.rightArm;
        if (stick == null || arm == null || !arm.visible || ps <= 0.01F) return;
        boolean slim = arm.getDefaultTransform().y() > 2.25F;
        float gripX = slim ? CampfireMotion.GRIP_X_SLIM : CampfireMotion.GRIP_X_WIDE;
        CF_FIRE[0] = frame.fireTarget[0];
        CF_FIRE[1] = frame.fireTarget[1];
        CF_FIRE[2] = frame.fireTarget[2];
        CampfireMotion.stickTarget(p, CF_FIRE, CF_TARGET);
        CampfireMotion.aimStick(arm.originX, arm.originY, arm.originZ, arm.pitch, arm.yaw, arm.roll, gripX,
                CF_TARGET[0], CF_TARGET[1], CF_TARGET[2], CF_STICK);

        RenderLayer layer = RenderLayers.entityCutoutNoCull(ROASTING_STICK_TEXTURE);
        matrices.push();
        arm.applyTransform(matrices);
        matrices.translate(gripX / 16.0F, CampfireMotion.GRIP_Y / 16.0F, CampfireMotion.GRIP_Z / 16.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(CF_STICK.yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(CF_STICK.pitch));
        if (p.stickTwistDeg != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.stickTwistDeg));
        matrices.scale(ps, ps, ps);
        // Näheres Ziel: Stock rutscht durch die Hand nach hinten (slide <= 0 -> +Z)
        if (CF_STICK.slide != 0.0F) matrices.translate(0.0F, 0.0F, -CF_STICK.slide / 16.0F);

        final int stickLight = light;
        matrices.push();
        if (CF_STICK.stretch != 1.0F) matrices.scale(1.0F, 1.0F, CF_STICK.stretch);
        queue.getBatchingQueue(0).submitCustom(matrices, layer, (entry, consumer) ->
                stick.render(entry, consumer, stickLight, 0, 1, false, 0xFFFFFFFF, "stick"));
        matrices.pop();

        if (p.mallowScale > 0.01F) {
            final int color = CampfireMotion.mallowColor(p.toast, p.burnt);
            float c = CampfireMotion.STICK_REACH_PX / 16.0F;
            float extra = CampfireMotion.STICK_REACH_PX * (CF_STICK.stretch - 1.0F) / 16.0F;
            matrices.push();
            matrices.translate(0.0F, 0.0F, -extra - c);
            matrices.scale(p.mallowScale, p.mallowScale, p.mallowScale);
            matrices.translate(0.0F, 0.0F, c);
            // Brennend: etwas heller (Glut), sonst normal beleuchtet
            final int mallowLight = p.burning > 0.0F ? 0x00F000F0 : stickLight;
            queue.getBatchingQueue(0).submitCustom(matrices, layer, (entry, consumer) ->
                    stick.render(entry, consumer, mallowLight, 0, 1, false, color, "marshmallow"));
            matrices.pop();
        }
        matrices.pop();
    }

    // ------------------------------------------------------------------
    // Hängematte
    // ------------------------------------------------------------------

    private static BedrockCosmeticModel hammockPostModel, hammockSlingModel;
    private static boolean hammockModelsLoaded;
    private static final HammockMotion.Pose HM_POSE = new HammockMotion.Pose();
    private static final String[] SLING_SEGMENTS = new String[HammockLogic.SEGMENTS];

    static {
        for (int i = 0; i < SLING_SEGMENTS.length; i++) SLING_SEGMENTS[i] = "s" + i;
    }

    private static void loadHammockModels() {
        if (hammockModelsLoaded) return;
        hammockModelsLoaded = true;
        hammockPostModel = loadProp(HAMMOCK_POST_MODEL);
        hammockSlingModel = loadProp(HAMMOCK_SLING_MODEL);
    }

    /**
     * Hängematte: zwei Pfosten (fest in der Welt, wachsen beim Erscheinen aus dem Boden) und das Tuch mit Seilen und
     * Spreizstäben (schaukelt mit dem Spieler um die Achse durch beide Aufhängepunkte, rollt sich beim Erscheinen vom
     * Kopf- zum Fußende aus). Bedrock-Modelle aus {@code emotes/hammock/*.geo.json}, Entity-Cutout ohne Culling mit dem
     * Licht des Spielers.
     * <p>
     * Der Spieler ist in {@code setupTransforms} gegengedreht, versetzt und in die Liegelage gedreht
     * ({@link EmoteAnimator#pushHammockBody}). Hier wird das wieder herausgerechnet: zurück vor die Modell-Skalierung
     * ({@code scale(-1,-1,1)}, 0,9375, {@code translate(0,-1.501,0)}) und vor die Körpertransformation. Danach liegt der
     * Hängematten-Rahmen aus {@link HammockLogic} vor (Blöcke nach {@code scale(1/Spieler-Skalierung)}).
     */
    private static void renderHammock(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                      PlayerEntityRenderState state, EmoteAnimator.ActiveEmote active) {
        if (active == null || !EmoteAnimator.hammockTransformApplies(state)) return;
        HammockCoordinator.Frame frame = HammockCoordinator.frame(state, active);
        if (frame == null) return;
        loadHammockModels();
        BedrockCosmeticModel post = hammockPostModel, sling = hammockSlingModel;
        if (post == null || sling == null) return;
        float scale = state.baseScale > 0.0F ? state.baseScale : 1.0F;
        HammockMotion.Pose p = HM_POSE;
        HammockMotion.sample(frame.t, 0.0F, 0.0F, false, scale, p);
        final int lt = light;

        matrices.push();
        // 1. Modell-Skalierung herausrechnen (Umkehrung von scale(-1,-1,1) · scale(0.9375) · translate(0,-1.501,0))
        matrices.translate(0.0F, 1.501F, 0.0F);
        matrices.scale(1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE);
        matrices.scale(-1.0F, -1.0F, 1.0F);
        // 2. Körpertransformation herausrechnen -> Hängematten-Rahmen, dann echte Blöcke
        EmoteAnimator.popHammockBody(matrices, p, scale);
        matrices.scale(1.0F / scale, 1.0F / scale, 1.0F / scale);

        // 3. Pfosten: rechts (+X) nach außen geneigt, links um 180° gedreht (Knoten zeigt nach innen)
        float ps = p.postScale;
        if (ps > 0.01F) {
            float pxz = 0.6F + 0.4F * Math.min(ps, 1.0F);
            RenderLayer postLayer = RenderLayers.entityCutoutNoCull(HAMMOCK_POST_TEXTURE);
            for (int side = 1; side >= -1; side -= 2) {
                matrices.push();
                matrices.translate((float) (side * HammockLogic.POST_X), 0.0F, 0.0F);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (-side * HammockLogic.POST_TILT_DEG)));
                if (side < 0) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
                matrices.scale(pxz, ps, pxz);
                bedrockSpace(matrices);
                queue.getBatchingQueue(0).submitCustom(matrices, postLayer,
                        (entry, consumer) -> post.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, "post"));
                matrices.pop();
            }
        }

        // 4. Tuch, Seile und Stäbe: um die Schaukelachse gedreht
        RenderLayer slingLayer = RenderLayers.entityCutoutNoCull(HAMMOCK_SLING_TEXTURE);
        float ay = (float) HammockLogic.AXIS_Y;
        matrices.push();
        matrices.translate(0.0F, ay, 0.0F);
        if (p.swayDeg != 0.0F) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.swayDeg));
        matrices.translate(0.0F, -ay, 0.0F);
        // Segmente rollen sich vom Kopfende (-X) zum Fußende aus: jedes wird ab seinem Anfang entlang X gestreckt
        for (int i = 0; i < SLING_SEGMENTS.length; i++) {
            float k = HammockMotion.segmentScale(frame.t, i);
            if (k <= 0.001F) continue;
            final String bone = SLING_SEGMENTS[i];
            float sx = (float) HammockLogic.segmentX(i), sy = (float) HammockLogic.slingY(HammockLogic.segmentX(i));
            matrices.push();
            if (k < 0.999F) {
                matrices.translate(sx, sy, 0.0F);
                matrices.scale(k, 1.0F, 1.0F);
                matrices.translate(-sx, -sy, 0.0F);
            }
            bedrockSpace(matrices);
            queue.getBatchingQueue(0).submitCustom(matrices, slingLayer,
                    (entry, consumer) -> sling.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, bone));
            matrices.pop();
        }
        // Seile fallen von der Wicklung herab (Größe um den Aufhängepunkt), Stäbe erscheinen mit dem Tuch
        float e = p.ropeExtend;
        if (e > 0.001F) {
            for (int side = -1; side <= 1; side += 2) {
                final String bone = side < 0 ? "rope_l" : "rope_r";
                float axf = (float) (side * HammockLogic.ATTACH_X);
                matrices.push();
                if (e < 0.999F) {
                    matrices.translate(axf, ay, 0.0F);
                    matrices.scale(e, e, e);
                    matrices.translate(-axf, -ay, 0.0F);
                }
                bedrockSpace(matrices);
                queue.getBatchingQueue(0).submitCustom(matrices, slingLayer,
                        (entry, consumer) -> sling.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, bone));
                matrices.pop();
            }
        }
        if (p.unroll > 0.0F) {
            matrices.push();
            bedrockSpace(matrices);
            queue.getBatchingQueue(0).submitCustom(matrices, slingLayer,
                    (entry, consumer) -> sling.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, "bars"));
            matrices.pop();
        }
        matrices.pop();

        matrices.pop();
    }

    // ------------------------------------------------------------------
    // Schaukel
    // ------------------------------------------------------------------

    public static final Identifier SWING_FRAME_TEXTURE = Identifier.of("bameclient", "textures/entity/swing_frame.png");
    public static final Identifier SWING_SEAT_TEXTURE = Identifier.of("bameclient", "textures/entity/swing_seat.png");
    private static final String SWING_FRAME_MODEL = "assets/bameclient/emotes/swing/swing_frame.geo.json";
    private static final String SWING_SEAT_MODEL = "assets/bameclient/emotes/swing/swing_seat.geo.json";
    private static BedrockCosmeticModel swingFrameModel, swingSeatModel;
    private static boolean swingModelsLoaded;
    private static final SwingMotion.Pose SW_POSE = new SwingMotion.Pose();
    private static final SwingMotion.Appear SW_APPEAR = new SwingMotion.Appear();

    private static void loadSwingModels() {
        if (swingModelsLoaded) return;
        swingModelsLoaded = true;
        swingFrameModel = loadProp(SWING_FRAME_MODEL);
        swingSeatModel = loadProp(SWING_SEAT_MODEL);
    }

    /**
     * Schaukel: Der Besitzer des Gerüsts zeichnet A-Beine (wachsen aus dem Boden), Balken mit Haken (fällt von oben ein)
     * und freie Sitze (hängen still); jeder Spieler zeichnet seinen eigenen Sitz samt Seilen, um die Achse geschaukelt.
     * Wie bei der Hängematte werden Modell-Skalierung und Körpertransformation ({@link EmoteAnimator#pushSwingBody})
     * herausgerechnet; danach liegt der Schaukel-Rahmen aus {@link SwingLogic} vor (Ursprung = Sitz 0).
     */
    private static void renderSwing(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                    PlayerEntityRenderState state, EmoteAnimator.ActiveEmote active) {
        if (active == null || !EmoteAnimator.hammockTransformApplies(state)) return;
        SwingCoordinator.Frame frame = SwingCoordinator.frame(state, active);
        if (frame == null) return;
        loadSwingModels();
        BedrockCosmeticModel frameModel = swingFrameModel, seatModel = swingSeatModel;
        if (frameModel == null || seatModel == null) return;
        float scale = state.baseScale > 0.0F ? state.baseScale : 1.0F;
        SwingMotion.Pose p = SW_POSE;
        SwingMotion.sample(frame.t, frame.seat, frame.standFeet, frame.turn0, 0.0F, 0.0F, false, scale, p);
        SwingMotion.Appear ap = SW_APPEAR;
        SwingMotion.appear(frame.frameAge, ap);
        final int lt = light;

        matrices.push();
        matrices.translate(0.0F, 1.501F, 0.0F);
        matrices.scale(1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE);
        matrices.scale(-1.0F, -1.0F, 1.0F);
        EmoteAnimator.popSwingBody(matrices, p, scale);
        matrices.scale(1.0F / scale, 1.0F / scale, 1.0F / scale);

        if (frame.drawFrame) {
            RenderLayer frameLayer = RenderLayers.entityCutoutNoCull(SWING_FRAME_TEXTURE);
            matrices.push();
            matrices.translate((float) SwingLogic.CENTER_X, 0.0F, 0.0F);
            if (ap.legs > 0.01F) {
                matrices.push();
                matrices.scale(1.0F, ap.legs, 1.0F);
                bedrockSpace(matrices);
                queue.getBatchingQueue(0).submitCustom(matrices, frameLayer,
                        (entry, consumer) -> frameModel.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, "legs"));
                matrices.pop();
            }
            if (ap.beam > 0.01F) {
                matrices.push();
                float by = (float) SwingLogic.BEAM_Y;
                matrices.translate(0.0F, (float) (by + SwingMotion.beamOffset(ap.beam)), 0.0F);
                float bs = 0.5F + 0.5F * ap.beam;
                matrices.scale(bs, bs, bs);
                matrices.translate(0.0F, -by, 0.0F);
                bedrockSpace(matrices);
                queue.getBatchingQueue(0).submitCustom(matrices, frameLayer, (entry, consumer) -> {
                    frameModel.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, "beam");
                    frameModel.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, "hooks");
                });
                matrices.pop();
            }
            matrices.pop();
            for (int i = 0; i < 2; i++) {
                if (frame.freeSeat[i]) renderSwingSeat(matrices, queue, lt, seatModel, i, 0.0F, ap);
            }
        }
        renderSwingSeat(matrices, queue, lt, seatModel, frame.seat, p.swingDeg, ap);
        matrices.pop();
    }

    /** Ein Sitz mit Seilen, um die Achse geschaukelt; Brett springt auf, Seile wachsen vom Brett zum Haken. */
    private static void renderSwingSeat(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                        BedrockCosmeticModel seatModel, int seat, float swingDeg, SwingMotion.Appear ap) {
        if (ap.seat <= 0.01F) return;
        RenderLayer layer = RenderLayers.entityCutoutNoCull(SWING_SEAT_TEXTURE);
        float py = (float) SwingLogic.PIVOT_Y, top = (float) SwingLogic.SEAT_TOP_Y;
        matrices.push();
        matrices.translate(0.0F, py, 0.0F);
        if (swingDeg != 0.0F) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swingDeg));
        matrices.translate((float) SwingLogic.seatX(seat), -py, 0.0F);
        matrices.push();
        if (ap.seat < 0.999F || ap.seat > 1.001F) {
            matrices.translate(0.0F, top, 0.0F);
            matrices.scale(ap.seat, ap.seat, ap.seat);
            matrices.translate(0.0F, -top, 0.0F);
        }
        bedrockSpace(matrices);
        queue.getBatchingQueue(0).submitCustom(matrices, layer,
                (entry, consumer) -> seatModel.render(entry, consumer, light, 0, 1, false, 0xFFFFFFFF, "seat"));
        matrices.pop();
        if (ap.ropes > 0.001F) {
            matrices.push();
            if (ap.ropes < 0.999F) {
                matrices.translate(0.0F, top, 0.0F);
                matrices.scale(1.0F, ap.ropes, 1.0F);
                matrices.translate(0.0F, -top, 0.0F);
            }
            bedrockSpace(matrices);
            queue.getBatchingQueue(0).submitCustom(matrices, layer,
                    (entry, consumer) -> seatModel.render(entry, consumer, light, 0, 1, false, 0xFFFFFFFF, "ropes"));
            matrices.pop();
        }
        matrices.pop();
    }

    // ------------------------------------------------------------------
    // Picknick
    // ------------------------------------------------------------------

    public static final Identifier PICNIC_BLANKET_TEXTURE = Identifier.of("bameclient", "textures/entity/picnic_blanket.png");
    public static final Identifier PICNIC_BASKET_TEXTURE = Identifier.of("bameclient", "textures/entity/picnic_basket.png");
    public static final Identifier PICNIC_ITEMS_TEXTURE = Identifier.of("bameclient", "textures/entity/picnic_items.png");
    private static final String PICNIC_BLANKET_MODEL = "assets/bameclient/emotes/picnic/picnic_blanket.geo.json";
    private static final String PICNIC_BASKET_MODEL = "assets/bameclient/emotes/picnic/picnic_basket.geo.json";
    private static final String PICNIC_ITEMS_MODEL = "assets/bameclient/emotes/picnic/picnic_items.geo.json";
    private static BedrockCosmeticModel picnicBlanketModel, picnicBasketModel, picnicItemsModel;
    private static boolean picnicModelsLoaded;
    private static final PicnicMotion.Pose PC_POSE = new PicnicMotion.Pose();
    private static final double[] PC_ANT = new double[6];
    private static final double[] PC_GRIP = new double[3];
    private static final String[] SANDWICH_PARTS = {"s0", "s1", "s2"};
    /** Korbdeckel öffnet bis zu diesem Winkel (Grad). */
    private static final float LID_OPEN_DEG = 112.0F;

    private static void loadPicnicModels() {
        if (picnicModelsLoaded) return;
        picnicModelsLoaded = true;
        picnicBlanketModel = loadProp(PICNIC_BLANKET_MODEL);
        picnicBasketModel = loadProp(PICNIC_BASKET_MODEL);
        picnicItemsModel = loadProp(PICNIC_ITEMS_MODEL);
    }

    /**
     * Picknick: Decke (entfaltet sich), Korb mit Deckel, Flasche, Serviette mit Apfel und Ameisen fest in der Welt
     * (Picknick-Rahmen aus {@link PicnicLogic}); Sandwich bzw. Flasche in der Hand am Griffpunkt des geposten Arms,
     * aufrecht bzw. mit der Neigung aus {@link PicnicMotion}.
     */
    private static void renderPicnic(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                     PlayerEntityRenderState state, PlayerEntityModel model, EmoteAnimator.ActiveEmote active) {
        if (active == null || !EmoteAnimator.hammockTransformApplies(state)) return;
        PicnicCoordinator.Frame frame = PicnicCoordinator.frame(state, active);
        if (frame == null) return;
        loadPicnicModels();
        BedrockCosmeticModel blanket = picnicBlanketModel, basket = picnicBasketModel, items = picnicItemsModel;
        if (blanket == null || basket == null || items == null) return;
        float scale = state.baseScale > 0.0F ? state.baseScale : 1.0F;
        PicnicMotion.Pose p = PC_POSE;
        PicnicMotion.sample(frame.t, frame.id, frame.seedMs, true, 0.0F, 0.0F, false, p);
        final int lt = light;
        RenderLayer itemLayer = RenderLayers.entityCutoutNoCull(PICNIC_ITEMS_TEXTURE);

        // 1. Sachen in der Hand (Modellraum des geposten Spielers)
        if (p.sandwichHeld && model.rightArm.visible) {
            handGrip(model.rightArm, PC_GRIP);
            matrices.push();
            matrices.translate(PC_GRIP[0] / 16.0, PC_GRIP[1] / 16.0, PC_GRIP[2] / 16.0);
            matrices.scale(1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE);
            for (int i = 0; i < 3; i++) {
                float k = p.sandwichParts[i];
                if (k <= 0.01F) continue;
                final String bone = SANDWICH_PARTS[i];
                float zc = (-2.0F + 2.0F * i) / 16.0F;
                matrices.push();
                if (k < 0.999F) {
                    matrices.translate(0.0F, 0.0F, zc);
                    matrices.scale(k, k, k);
                    matrices.translate(0.0F, 0.0F, -zc);
                }
                matrices.translate(0.0F, -BEDROCK_GROUND, 0.0F);
                queue.getBatchingQueue(0).submitCustom(matrices, itemLayer,
                        (entry, consumer) -> items.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, bone));
                matrices.pop();
            }
            matrices.pop();
        }
        if (p.bottleHeld && model.leftArm.visible) {
            handGrip(model.leftArm, PC_GRIP);
            matrices.push();
            matrices.translate(PC_GRIP[0] / 16.0, PC_GRIP[1] / 16.0, PC_GRIP[2] / 16.0);
            if (p.bottleTilt != 0.0F) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.bottleTilt));
            matrices.scale(1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE);
            matrices.translate(0.0F, -BEDROCK_GROUND, 0.0F);
            queue.getBatchingQueue(0).submitCustom(matrices, itemLayer,
                    (entry, consumer) -> items.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, "bottle"));
            matrices.pop();
        }

        // 2. Weltfeste Sachen: Modell-Skalierung und Absenkung herausrechnen -> Picknick-Rahmen (Blöcke)
        matrices.push();
        matrices.translate(0.0F, 1.501F, 0.0F);
        matrices.scale(1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE);
        matrices.scale(-1.0F, -1.0F, 1.0F);
        matrices.translate(0.0F, (float) (p.drop / scale), 0.0F);
        matrices.scale(1.0F / scale, 1.0F / scale, 1.0F / scale);
        float top = (float) PicnicLogic.BLANKET_TOP;

        if (p.blanketX > 0.001F) {
            matrices.push();
            matrices.translate((float) PicnicLogic.BLANKET_CX, 0.0F, (float) PicnicLogic.BLANKET_CZ);
            matrices.scale(0.2F + 0.8F * p.blanketX, 1.0F, 0.2F + 0.8F * p.blanketZ);
            bedrockSpace(matrices);
            queue.getBatchingQueue(0).submitCustom(matrices, RenderLayers.entityCutoutNoCull(PICNIC_BLANKET_TEXTURE),
                    (entry, consumer) -> blanket.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, "blanket"));
            matrices.pop();
        }
        if (p.basket > 0.01F) {
            RenderLayer basketLayer = RenderLayers.entityCutoutNoCull(PICNIC_BASKET_TEXTURE);
            matrices.push();
            matrices.translate((float) PicnicLogic.BASKET_X, top, (float) PicnicLogic.BASKET_Z);
            matrices.scale(p.basket, p.basket, p.basket);
            matrices.push();
            bedrockSpace(matrices);
            queue.getBatchingQueue(0).submitCustom(matrices, basketLayer, (entry, consumer) -> {
                basket.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, "body");
                basket.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, "handle");
                basket.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, "lid_fixed");
            });
            matrices.pop();
            // Deckelklappe zur Spielerseite (Rahmen -x): Scharnier in der Mitte, öffnet nach oben
            matrices.push();
            float h = (float) PicnicLogic.BASKET_H;
            if (p.lid > 0.001F) {
                matrices.translate(0.0F, h, 0.0F);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-LID_OPEN_DEG * p.lid));
                matrices.translate(0.0F, -h, 0.0F);
            }
            bedrockSpace(matrices);
            queue.getBatchingQueue(0).submitCustom(matrices, basketLayer,
                    (entry, consumer) -> basket.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, "lid"));
            matrices.pop();
            matrices.pop();
        }
        if (p.items > 0.01F) {
            float s = p.items;
            if (!p.bottleHeld) {
                matrices.push();
                matrices.translate((float) PicnicLogic.BOTTLE_X, (float) (top + PicnicLogic.BOTTLE_GRIP_Y * s), (float) PicnicLogic.BOTTLE_Z);
                matrices.scale(s, s, s);
                bedrockSpace(matrices);
                queue.getBatchingQueue(0).submitCustom(matrices, itemLayer,
                        (entry, consumer) -> items.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, "bottle"));
                matrices.pop();
            }
            matrices.push();
            matrices.translate((float) PicnicLogic.NAPKIN_X, top, (float) PicnicLogic.NAPKIN_Z);
            matrices.scale(s, s, s);
            matrices.push();
            bedrockSpace(matrices);
            queue.getBatchingQueue(0).submitCustom(matrices, itemLayer,
                    (entry, consumer) -> items.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, "napkin"));
            matrices.pop();
            matrices.translate(0.03F, 0.27F / 16.0F, -0.02F);
            bedrockSpace(matrices);
            queue.getBatchingQueue(0).submitCustom(matrices, itemLayer,
                    (entry, consumer) -> items.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, "apple"));
            matrices.pop();
        }
        // Ameise (höchstens eine gleichzeitig)
        int slot = PicnicLogic.antSlot(frame.t);
        if (slot >= 0) {
            PicnicLogic.Ant ant = PicnicLogic.ant(frame.id, frame.seedMs, slot);
            double[] st = PicnicLogic.antState(ant, frame.t, PC_ANT);
            if (st[4] > 0.5) {
                matrices.push();
                matrices.translate((float) st[0], (float) st[1], (float) st[2]);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) -st[3]));
                if (st[5] > 0.5) matrices.multiply(RotationAxis.POSITIVE_X.rotation((float) (st[3] * 2.0)));
                bedrockSpace(matrices);
                queue.getBatchingQueue(0).submitCustom(matrices, itemLayer,
                        (entry, consumer) -> items.render(entry, consumer, lt, 0, 1, false, 0xFFFFFFFF, "ant"));
                matrices.pop();
            }
        }
        matrices.pop();
    }

    /** Griffpunkt (Modell-px) eines geposten Arms: Drehpunkt + Drehung von (0, 9, 0). */
    private static void handGrip(ModelPart arm, double[] out) {
        CampfireMotion.rotZYX(0.0, PicnicMotion.GRIP_Y, 0.0, arm.pitch, arm.yaw, arm.roll, out);
        out[0] += arm.originX;
        out[1] += arm.originY;
        out[2] += arm.originZ;
    }

    /**
     * Vom Hängematten-Rahmen (Blöcke, +Y oben) in den Raum, den {@link BedrockCosmeticModel#render} erwartet: Bedrock-
     * Punkt (bx, by, bz) px landet bei (-bx, by, bz) / 16 (gleiche Händigkeit wie der Spieler-Modellraum).
     */
    private static void bedrockSpace(MatrixStack matrices) {
        matrices.scale(-1.0F, -1.0F, 1.0F);
        matrices.translate(0.0F, -BEDROCK_GROUND, 0.0F);
    }

    /** Wurfversatz (Modellraum), Armtransformation und Messer-Kette aus ButterflyKnifeAnimation; endet am Pivot (px-Skala). */
    private static void pushKnifeRoot(MatrixStack matrices, ModelPart arm, boolean slim, ButterflyKnifeAnimation.Pose pose) {
        if (pose.tossX != 0.0F || pose.tossY != 0.0F || pose.tossZ != 0.0F) {
            matrices.translate(pose.tossX / 16.0F, pose.tossY / 16.0F, pose.tossZ / 16.0F);
        }
        arm.applyTransform(matrices);
        ButterflyKnifeAnimation.applyKnifeTransform(pose, slim, new ButterflyKnifeAnimation.Xform() {
            @Override public void translate(float x, float y, float z) { matrices.translate(x / 16.0F, y / 16.0F, z / 16.0F); }
            @Override public void rotateX(float deg) { matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(deg)); }
            @Override public void rotateY(float deg) { matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(deg)); }
            @Override public void rotateZ(float deg) { matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(deg)); }
        });
    }

    /** Heller Streifen, der entlang der Klinge laeuft: additiv (eyes-Layer) und voll beleuchtet, nur fuer das Glanzlicht. */
    private static void renderKnifeGlint(MatrixStack matrices, OrderedRenderCommandQueue commandQueue, float g, float strength) {
        float z = GLINT_Z_START + (GLINT_Z_END - GLINT_Z_START) * g;
        int i = 0;
        while (i < PROFILE_Z.length - 2 && z < PROFILE_Z[i + 1]) i++;
        float f = Math.max(0.0F, Math.min(1.0F, (z - PROFILE_Z[i]) / (PROFILE_Z[i + 1] - PROFILE_Z[i])));
        float h = PROFILE_H[i] + (PROFILE_H[i + 1] - PROFILE_H[i]) * f;
        float c = PROFILE_C[i] + (PROFILE_C[i + 1] - PROFILE_C[i]) * f;
        int v = Math.round(255.0F * GLINT_BRIGHTNESS * strength);
        if (v <= 2) return;
        matrices.push();
        matrices.translate(0.0F, c / 16.0F, z / 16.0F);
        matrices.scale(1.0F, h / 8.0F, 1.0F);
        commandQueue.submitModelPart(knifeGlintPart, matrices, RenderLayers.eyes(BUTTERFLY_KNIFE_TEXTURE), 0x00F000F0,
                OverlayTexture.DEFAULT_UV, null, 0xFF000000 | (v << 16) | (v << 8) | v, null);
        matrices.pop();
    }
}
