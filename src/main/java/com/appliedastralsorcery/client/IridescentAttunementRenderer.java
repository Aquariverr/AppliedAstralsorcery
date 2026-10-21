package com.appliedastralsorcery.client;

import static com.appliedastralsorcery.client.AttunementRingGeometry.*;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import com.appliedastralsorcery.AppliedAstralsorcery;
import com.appliedastralsorcery.attunement.AttunementLayout;
import com.appliedastralsorcery.attunement.IridescentAttunementBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import hellfirepvp.astralsorcery.common.util.data.Vector3;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class IridescentAttunementRenderer implements BlockEntityRenderer<IridescentAttunementBlockEntity> {
    public static final ModelResourceLocation STAR = ModelResourceLocation.standalone(ResourceLocation
            .fromNamespaceAndPath(AppliedAstralsorcery.MOD_ID, "block/iridescent_attunement_altar_star"));
    public static final ModelResourceLocation CRYSTAL = ModelResourceLocation.standalone(ResourceLocation
            .fromNamespaceAndPath(AppliedAstralsorcery.MOD_ID, "block/iridescent_attunement_altar_crystal"));
    private static final float SCALE = 0.75F, CORNER_TIP = 35.264F;
    private static final float CRYSTAL_LIFT = 3F / 16, CRYSTAL_BOB = 0.75F / 16;
    private static final float CRYSTAL_HEART = 11F / 16;
    /** Cycle lengths in ring-clock ticks; each divides {@link IridescentAttunementBlockEntity#RING_CLOCK_PERIOD}. */
    private static final float CRYSTAL_BOB_CYCLE = 100;
    static final int BREATH_CYCLE = 40;
    private static final float GEM_INFLATE = 1.04F, GEM_SWELL = 0.1F;
    private static final float WORK_GROWTH = 0.2F;
    private static final float STAR_R = 0.85F, STAR_G = 0.9F, STAR_B = 1;
    private static final Direction[] FACES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, null};
    @Nullable private BakedModel split, crystalModel;
    private final List<BakedQuad> gold = new ArrayList<>(), gems = new ArrayList<>(), crystal = new ArrayList<>();
    private final Quaternionf rotation = new Quaternionf(), cornerTilt = new Quaternionf(), cornerRoll = new Quaternionf();

    /** Separates the tinted gem quads, which glow evenly, from the gold that takes entity shading. */
    private void split(BakedModel model) {
        if (model == split) return;
        split = model;
        gold.clear();
        gems.clear();
        var random = RandomSource.create();
        for (var face : FACES)
            for (var quad : model.getQuads(null, face, random, ModelData.EMPTY, null))
                (quad.isTinted() ? gems : gold).add(quad);
    }

    private void crystal(BakedModel model) {
        if (model == crystalModel) return;
        crystalModel = model;
        crystal.clear();
        var random = RandomSource.create();
        for (var face : FACES) crystal.addAll(model.getQuads(null, face, random, ModelData.EMPTY, null));
    }

    @Override public void render(IridescentAttunementBlockEntity altar, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        var level = altar.getLevel();
        if (level == null) return;
        var models = Minecraft.getInstance().getModelManager();
        split(models.getModel(STAR));
        crystal(models.getModel(CRYSTAL));
        var goldBuffer = buffers.getBuffer(Sheets.cutoutBlockSheet());
        // Lightmap only, no directional shade: every facet of an emissive gem stays fully lit.
        var gemBuffer = buffers.getBuffer(RenderType.cutout());
        float time = level.getGameTime() % 24000 + partialTick;
        float clock = altar.getRingClock(partialTick);
        float rise = rise(altar, partialTick);
        float phase = altar.getRingPhase(partialTick);
        int count = AttunementLayout.STATIONS.size();

        pose.pushPose();
        pose.translate(0, crystalLift(clock, rise), 0);
        for (var quad : crystal) gemBuffer.putBulkData(pose.last(), quad, 1, 1, 1, 1, light, overlay);
        pose.popPose();

        pose.pushPose();
        ringTransform(pose, clock, rise, rotation);
        float breath = 0.5F - 0.5F * Mth.cos(time * Mth.TWO_PI / BREATH_CYCLE);
        float flash = Mth.square(Math.max(0, breath - 0.7F) / 0.3F);
        cornerTilt.rotationX(-CORNER_TIP * rise * Mth.DEG_TO_RAD);
        cornerRoll.rotationZ(45 * rise * Mth.DEG_TO_RAD);
        for (int i = 0; i < count; i++) {
            pose.pushPose();
            orbitTransform(pose, clock, rise, phase, i, rotation);
            float glow = altar.getStationGlow(i), work = altar.getStationWork(i);
            pose.mulPose(rotation.rotationY((clock * 2 + i * 37) * Mth.DEG_TO_RAD));
            pose.mulPose(cornerTilt);
            pose.mulPose(cornerRoll);
            float scale = SCALE * (1 + WORK_GROWTH * work);
            pose.scale(scale, scale, scale);
            pose.translate(-0.5, -0.5, -0.5);
            int goldLight = LightTexture.pack(Math.max(LightTexture.block(light), Math.round(15 * work)), LightTexture.sky(light));
            for (var quad : gold) goldBuffer.putBulkData(pose.last(), quad, 1, 1, 1, 1, goldLight, overlay);

            int color = AttunementLayout.STATIONS.get(i).constellation().get().getConstellationColor().getColor();
            float tint = Mth.lerp(work, 0.15F + 0.35F * glow, 1);
            float bright = Mth.lerp(work, 0.3F + 0.2F * glow, 0.75F + 0.25F * breath);
            float white = 0.45F * flash * work;
            float inflate = GEM_INFLATE + GEM_SWELL * breath * work;
            float r = Mth.lerp(white, Mth.lerp(tint, STAR_R, (color >> 16 & 255) / 255F) * bright, 1);
            float g = Mth.lerp(white, Mth.lerp(tint, STAR_G, (color >> 8 & 255) / 255F) * bright, 1);
            float b = Mth.lerp(white, Mth.lerp(tint, STAR_B, (color & 255) / 255F) * bright, 1);
            pose.translate(0.5, 0.5, 0.5);
            pose.scale(inflate, inflate, inflate);
            pose.translate(-0.5, -0.5, -0.5);
            for (var quad : gems)
                gemBuffer.putBulkData(pose.last(), quad, r, g, b, 1, LightTexture.FULL_BRIGHT, overlay);
            pose.popPose();
        }
        pose.popPose();
    }

    private static float rise(IridescentAttunementBlockEntity altar, float partialTick) {
        return (float) Mth.smoothstep(altar.getRingRise(partialTick));
    }

    private static float crystalLift(float clock, float rise) {
        return rise * (CRYSTAL_LIFT + CRYSTAL_BOB * Mth.sin(clock * Mth.TWO_PI / CRYSTAL_BOB_CYCLE));
    }

    private static void ringTransform(PoseStack pose, float clock, float rise, Quaternionf rotation) {
        pose.translate(0.5, REST_Y + RISE_Y * rise, 0.5);
        float precession = clock * 0.2F;
        float tilt = TILT + TILT_NOD * Mth.sin(clock * Mth.TWO_PI / NOD_CYCLE);
        pose.mulPose(rotation.rotationY(precession * Mth.DEG_TO_RAD));
        pose.mulPose(rotation.rotationX(tilt * rise * Mth.DEG_TO_RAD));
        pose.mulPose(rotation.rotationY(-precession * Mth.DEG_TO_RAD));
    }

    private static void orbitTransform(PoseStack pose, float clock, float rise, float phase, int i, Quaternionf rotation) {
        // Clockwise from north seen from above, like the stations.
        pose.mulPose(rotation.rotationY(-(phase + i * 360F / AttunementLayout.STATIONS.size()) * Mth.DEG_TO_RAD));
        pose.translate(0, rise * 0.35F / 16 * Mth.sin(clock * Mth.TWO_PI / STAR_BOB_CYCLE + i * 0.52F),
                -Mth.lerp(rise, REST_RADIUS, RISEN_RADIUS));
    }

    public static Vector3 crystalCentre(IridescentAttunementBlockEntity altar, float partialTick) {
        float lift = crystalLift(altar.getRingClock(partialTick), rise(altar, partialTick));
        return new Vector3(altar.getBlockPos()).add(0.5, CRYSTAL_HEART + lift, 0.5);
    }

    /** The crystal is drawn here rather than in the block model, so it stays in sight about as far as the slab. */
    @Override public int getViewDistance() { return 256; }

    @Override public AABB getRenderBoundingBox(IridescentAttunementBlockEntity altar) {
        return new AABB(altar.getBlockPos()).inflate(0.8, 0.4, 0.8);
    }
}
