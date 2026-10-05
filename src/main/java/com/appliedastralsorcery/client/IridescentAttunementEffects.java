package com.appliedastralsorcery.client;

import com.appliedastralsorcery.attunement.AttunementLayout;
import com.appliedastralsorcery.attunement.IridescentAttunementBlockEntity;
import hellfirepvp.astralsorcery.client.effect.EffectHelper;
import hellfirepvp.astralsorcery.client.effect.function.FXAlphaFunction;
import hellfirepvp.astralsorcery.client.effect.function.FXColorFunction;
import hellfirepvp.astralsorcery.client.effect.function.FXScaleFunction;
import hellfirepvp.astralsorcery.client.effect.vfx.VFXImmediateFacingSprite;
import hellfirepvp.astralsorcery.client.lib.EffectTemplatesAS;
import hellfirepvp.astralsorcery.client.lib.SpritesAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.util.data.ColorWrapper;
import hellfirepvp.astralsorcery.common.util.data.Vector3;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** The crystal channels starlight to each working station: its star trails light round the ring, arcs to the
 *  crystal with every breath of its gem, and a stream of its colour flows from the crystal to the relay. */
public final class IridescentAttunementEffects implements IridescentAttunementBlockEntity.ClientEffects {
    /** Relay FX centre on its floating item, as {@link ConstellationRelayRenderer} draws it. */
    private static final double RELAY_FOCUS_Y = 0.85;
    private static final double STREAM_SPEED = 0.22;
    private final IridescentAttunementBlockEntity altar;
    private final Vector3[] lastStar = new Vector3[AttunementLayout.STATIONS.size()];
    private VFXImmediateFacingSprite flare;
    private boolean working;

    public IridescentAttunementEffects(IridescentAttunementBlockEntity altar) { this.altar = altar; }

    private boolean isPresent() {
        var client = Minecraft.getInstance();
        var level = altar.getLevel();
        return level != null && level == client.level && !altar.isRemoved()
                && level.hasChunk(altar.getBlockPos().getX() >> 4, altar.getBlockPos().getZ() >> 4)
                && level.getBlockEntity(altar.getBlockPos()) == altar
                && client.getCameraEntity() != null
                && client.getCameraEntity().distanceToSqr(altar.getBlockPos().getCenter()) <= 64 * 64;
    }

    private static ColorWrapper color(int station) {
        return AttunementLayout.STATIONS.get(station).constellation().get().getConstellationColor();
    }

    @Override public void tick() {
        var level = altar.getLevel();
        if (level == null || !isPresent()) {
            stop();
            return;
        }
        var crystal = IridescentAttunementRenderer.crystalCentre(altar, 0);
        // The gem's breath peaks at half a cycle; the arc lands with its white flash.
        boolean peak = level.getGameTime() % IridescentAttunementRenderer.BREATH_CYCLE
                == IridescentAttunementRenderer.BREATH_CYCLE / 2 - 1;
        var random = level.random;
        working = false;
        for (int i = 0; i < lastStar.length; i++) {
            if (!altar.isStationAttuning(i)) {
                lastStar[i] = null;
                continue;
            }
            working = true;
            var star = IridescentAttunementRenderer.starCentre(altar, i, 0);
            trail(random, i, lastStar[i] == null ? star : lastStar[i], star);
            stream(random, i, crystal);
            if (peak) {
                arc(random, crystal, star, color(i), 0.1F);
                arc(random, crystal, star, ColorWrapper.WHITE, 0.05F);
                sparks(random, star, color(i), 6, 0.02F);
            }
            lastStar[i] = star;
        }
        if (working) {
            if (flare == null || flare.isRemoved()) {
                flare = EffectHelper.of(EffectTemplatesAS.IMMEDIATE_FACING_SPRITE).spawn(crystal.copy());
                flare.setSpriteSheet(SpritesAS.SPRITE_ATTUNEMENT_ITEM_FLARE);
                flare.setScale(0.85F);
                flare.setAlpha(0.6F);
                flare.alpha(FXAlphaFunction.fadeIn(30));
                flare.refresh(fx -> working && fx == flare && isPresent());
            }
            flare.setPos(crystal.copy());
            shimmer(random, crystal);
        }
        if (altar.isLumenFed()) lumenMotes(random, level.getGameTime(), crystal);
    }

    /** Light shed behind a working star as it sweeps round the ring. */
    private void trail(RandomSource random, int station, Vector3 from, Vector3 to) {
        for (int step = 1; step <= 3; step++) {
            var pos = from.copyInterpolateWith(to, step / 3F);
            boolean core = random.nextInt(3) == 0;
            EffectHelper.of(EffectTemplatesAS.GENERIC_PARTICLE).spawn(pos)
                    .color(core ? FXColorFunction.WHITE : FXColorFunction.constant(color(station)))
                    .alpha(FXAlphaFunction.FADE_OUT).setAlpha(0.9F)
                    .scale(FXScaleFunction.SHRINK).setScale(core ? 0.14F : 0.2F + random.nextFloat() * 0.06F)
                    .setMaxAge(16 + random.nextInt(8));
        }
    }

    /** Starlight flowing out of the crystal to the station's relay. */
    private void stream(RandomSource random, int station, Vector3 crystal) {
        var relay = new Vector3(altar.getBlockPos().offset(AttunementLayout.STATIONS.get(station).offset()))
                .add(0.5, RELAY_FOCUS_Y, 0.5);
        var from = crystal.copy().add((random.nextFloat() - 0.5) * 0.15, (random.nextFloat() - 0.5) * 0.3,
                (random.nextFloat() - 0.5) * 0.15);
        var path = relay.subtract(from);
        int age = Math.max(1, (int) Math.round(path.length() / STREAM_SPEED));
        EffectHelper.of(EffectTemplatesAS.GENERIC_PARTICLE).spawn(from)
                .color(random.nextInt(4) == 0 ? FXColorFunction.WHITE : FXColorFunction.constant(color(station)))
                .alpha(FXAlphaFunction.PYRAMID).setAlpha(0.85F)
                .setScale(0.1F + random.nextFloat() * 0.06F)
                .setMotion(path.divide(age)).setMaxAge(age);
    }

    /** A crackling arc, built from short bolts so it kinks along its whole length. */
    private void arc(RandomSource random, Vector3 from, Vector3 to, ColorWrapper color, float jitter) {
        var path = to.copy().subtract(from);
        int pieces = Math.max(3, Mth.ceil(path.length() / 0.25));
        var start = from;
        for (int p = 1; p <= pieces; p++) {
            var end = from.copy().add(path.copy().multiply(p / (double) pieces));
            if (p < pieces) end.add(Vector3.random(random).normalize().multiply(jitter * Mth.sin(Mth.PI * p / pieces)));
            EffectHelper.of(EffectTemplatesAS.LIGHTNING).spawn(start.copy())
                    .make(end, random.nextLong(), 0.01F, jitter / 2, 0.35F, 20, 40)
                    .setBuildTime(2).setBuildFinishWaitTime(3)
                    .color(FXColorFunction.constant(color));
            start = end;
        }
    }

    private void sparks(RandomSource random, Vector3 at, ColorWrapper color, int count, float speed) {
        for (int i = 0; i < count; i++) {
            EffectHelper.of(EffectTemplatesAS.GENERIC_PARTICLE).spawn(at.copy())
                    .color(random.nextBoolean() ? FXColorFunction.WHITE : FXColorFunction.constant(color))
                    .alpha(FXAlphaFunction.FADE_OUT).setScale(0.12F + random.nextFloat() * 0.1F)
                    .setMotion(Vector3.random(random).normalize().multiply(speed * (0.5F + random.nextFloat())))
                    .setMaxAge(14 + random.nextInt(10));
        }
    }

    /** Iridescent glints playing over the crystal while it channels. */
    private void shimmer(RandomSource random, Vector3 crystal) {
        for (int i = 0; i < 2; i++) {
            var pos = crystal.copy().add((random.nextFloat() - 0.5) * 0.35, (random.nextFloat() - 0.5) * 0.6,
                    (random.nextFloat() - 0.5) * 0.35);
            int rgb = Mth.hsvToRgb(random.nextFloat(), 0.45F, 1);
            EffectHelper.of(EffectTemplatesAS.GENERIC_PARTICLE).spawn(pos)
                    .color(FXColorFunction.constant(ColorWrapper.opaque(rgb)))
                    .alpha(FXAlphaFunction.PYRAMID).setScale(0.08F + random.nextFloat() * 0.08F)
                    .setGravity(Vector3.y(0.0008F)).setMaxAge(16 + random.nextInt(12));
        }
    }

    /** Prismatic lumen drawn up from the slab into the crystal while it stands in for the sky. */
    private void lumenMotes(RandomSource random, long gameTime, Vector3 crystal) {
        double angle = random.nextFloat() * Mth.TWO_PI;
        double radius = 0.75 + random.nextFloat() * 0.35;
        var from = Vector3.atBottomCenter(altar.getBlockPos()).add(Math.cos(angle) * radius, 6.5 / 16,
                Math.sin(angle) * radius);
        int age = 26 + random.nextInt(10);
        var color = LumenAS.PRISMATIC.get().getColor(gameTime + random.nextInt(200));
        EffectHelper.of(EffectTemplatesAS.GENERIC_PARTICLE).spawn(from)
                .color(FXColorFunction.constant(color))
                .alpha(FXAlphaFunction.PYRAMID).setScale(0.12F + random.nextFloat() * 0.06F)
                .setMotion(crystal.copy().subtract(from).divide(age)).setMaxAge(age);
    }

    @Override public void finish(int station) {
        var level = altar.getLevel();
        if (level == null || !isPresent()) return;
        var random = level.random;
        var crystal = IridescentAttunementRenderer.crystalCentre(altar, 0);
        var star = IridescentAttunementRenderer.starCentre(altar, station, 0);
        var color = color(station);
        arc(random, crystal, star, color, 0.16F);
        arc(random, crystal, star, ColorWrapper.WHITE, 0.12F);
        sparks(random, star, color, 24, 0.05F);
        EffectHelper.of(EffectTemplatesAS.LIGHT_BEAM).spawn(crystal.copy())
                .setup(crystal.copy().addY(3.5), 0.9, 0.5)
                .color(FXColorFunction.constant(color)).setAlpha(0.9F).setMaxAge(30);
        // A ring of light washes out across the slab.
        for (int i = 0; i < 32; i++) {
            double angle = i * Mth.TWO_PI / 32;
            EffectHelper.of(EffectTemplatesAS.GENERIC_PARTICLE).spawn(crystal.copy())
                    .color(i % 2 == 0 ? FXColorFunction.WHITE : FXColorFunction.constant(color))
                    .alpha(FXAlphaFunction.FADE_OUT).setScale(0.16F + random.nextFloat() * 0.06F)
                    .setMotion(new Vector3(Math.cos(angle), 0, Math.sin(angle)).multiply(0.07))
                    .setMaxAge(22 + random.nextInt(6));
        }
    }

    @Override public void stop() {
        working = false;
        if (flare != null) flare.requestRemoval();
        flare = null;
        java.util.Arrays.fill(lastStar, null);
    }
}
