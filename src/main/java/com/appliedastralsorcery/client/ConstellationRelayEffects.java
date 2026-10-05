package com.appliedastralsorcery.client;

import com.appliedastralsorcery.ModConfig;
import com.appliedastralsorcery.attunement.ConstellationRelayBlockEntity;
import hellfirepvp.astralsorcery.client.effect.EffectHelper;
import hellfirepvp.astralsorcery.client.effect.function.FXAlphaFunction;
import hellfirepvp.astralsorcery.client.effect.function.FXColorFunction;
import hellfirepvp.astralsorcery.client.effect.source.FXOrbitalSource;
import hellfirepvp.astralsorcery.client.effect.source.orbital.FXItemAttunementOrbitalSource;
import hellfirepvp.astralsorcery.client.effect.vfx.VFXImmediateFacingSprite;
import hellfirepvp.astralsorcery.client.lib.EffectTemplatesAS;
import hellfirepvp.astralsorcery.client.lib.SpritesAS;
import hellfirepvp.astralsorcery.client.sound.PlayableSoundInstance;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.lib.SoundsAS;
import hellfirepvp.astralsorcery.common.util.VectorUtil;
import hellfirepvp.astralsorcery.common.util.data.ColorWrapper;
import hellfirepvp.astralsorcery.common.util.data.Vector3;
import hellfirepvp.astralsorcery.common.util.data.Vector3.RotAxis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;

/** Native item-attunement FX, fitted to the relay's local star-map area. */
public final class ConstellationRelayEffects implements ConstellationRelayBlockEntity.ClientEffects {
    private final ConstellationRelayBlockEntity relay;
    private FXOrbitalSource orbital;
    private VFXImmediateFacingSprite flare;
    private PlayableSoundInstance loopSound;
    private BaseConstellation constellation;
    private boolean running;
    private int lastStage = -1;

    public ConstellationRelayEffects(ConstellationRelayBlockEntity relay) { this.relay = relay; }

    private boolean isPresent() {
        var client = Minecraft.getInstance();
        var level = relay.getLevel();
        return level != null && level == client.level && !relay.isRemoved()
                && level.hasChunk(relay.getBlockPos().getX() >> 4, relay.getBlockPos().getZ() >> 4)
                && level.getBlockEntity(relay.getBlockPos()) == relay
                && client.getCameraEntity() != null
                && client.getCameraEntity().distanceToSqr(relay.getBlockPos().getCenter()) <= 64 * 64;
    }

    private boolean canRun() {
        // Server processing state also covers daytime/out-of-season attunement powered by lumen.
        return isPresent() && relay.isWorking() && relay.getConstellation() != null
                && !relay.getInventory().getStackInSlot(0).isEmpty();
    }

    private boolean isActive() { return running && canRun() && constellation == relay.getConstellation(); }

    private Vector3 base() { return Vector3.atBottomCenter(relay); }

    @Override public void tick() {
        var level = relay.getLevel();
        if (level == null || !canRun()) {
            stop();
            return;
        }
        // Native attunement stages are defined over 500 ticks; retain them at configured durations.
        int stage = Math.clamp((int) (relay.getVisualProgress(0) * 500.0 / ModConfig.ATTUNEMENT_TICKS.get()), 0, 499);
        if (running && (stage < lastStage || constellation != relay.getConstellation())) stop();
        if (!running) start();

        if (orbital == null || orbital.isRemoved()) {
            orbital = new FXItemAttunementOrbitalSource(base(), base().addY(0.9), constellation);
            orbital.setOrbitRadius(1.1F).setOrbitalPoints(4).setOrbitAxis(RotAxis.Y_AXIS);
            orbital.refresh(fx -> isActive() && fx == orbital);
            EffectHelper.source(orbital);
        }
        if (stage >= 40 && (flare == null || flare.isRemoved())) {
            flare = EffectHelper.of(EffectTemplatesAS.IMMEDIATE_FACING_SPRITE).spawn(base().addY(0.9));
            flare.setSpriteSheet(SpritesAS.SPRITE_ATTUNEMENT_ITEM_FLARE);
            flare.setScale(1.1F);
            flare.alpha(FXAlphaFunction.fadeIn(40));
            flare.refresh(fx -> isActive() && fx == flare);
        }
        var random = level.random;
        if (stage >= 80 && crossedInterval(stage, 40)) beam(random, 4.0, 0.4, 0.4, 35);

        swirl(random, stage);
        borderSparkles(random);
        if (stage >= 200) risingSparkles(random, stage);
        if (stage >= 460 && crossedInterval(stage, 5)) beam(random, 6.0, 0.9, 0.65, 30);
        if (stage >= 490) burst(random, 4);
        lastStage = stage;
    }

    private boolean crossedInterval(int stage, int interval) {
        return lastStage < 0 || stage / interval != lastStage / interval;
    }

    private void start() {
        running = true;
        constellation = relay.getConstellation();
        loopSound = PlayableSoundInstance.of(SoundsAS.ATTUNEMENT_ALTAR_ITEM_LOOP)
                .pos(relay).volume(0.18F).loop(true)
                .stopFunction(sound -> !isActive() || sound != loopSound)
                .fadeInTicks(20).fadeOutTicks(20).play();
        // Use the authoritative snapshot here so network latency cannot skip the start sound.
        if (relay.getProgress() <= 1)
            PlayableSoundInstance.of(SoundsAS.ATTUNEMENT_ALTAR_ITEM_START).pos(relay).volume(0.4F).play();
    }

    private void beam(RandomSource random, double height, double startWidth, double endWidth, int age) {
        var from = VectorUtil.withRandomOffset(base(), random, 0.1F);
        EffectHelper.of(EffectTemplatesAS.LIGHT_BEAM).spawn(from)
                .setup(from.copy().addY(height), startWidth, endWidth)
                .color(FXColorFunction.WHITE).setAlpha(0.8F).setMaxAge(age);
    }

    private void swirl(RandomSource random, int stage) {
        int count = crossedInterval(stage, 50) ? 48 : 2;
        double cycle = stage / 500.0 * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(i * 360.0 / count + Math.sin(cycle) * 120);
            var motion = new Vector3(-Math.cos(angle), 0, -Math.sin(angle)).multiply(0.045);
            var particle = EffectHelper.of(EffectTemplatesAS.GENERIC_PARTICLE).spawn(base().addY(0.08));
            particle.color(FXColorFunction.WHITE).setScale(0.12F + random.nextFloat() * 0.15F)
                    .setMotion(motion).setMaxAge(20 + random.nextInt(10));
            if (random.nextInt(6) == 0) {
                particle.color(FXColorFunction.constant(constellation.getConstellationColor()));
                particle.setGravity(Vector3.y(0.0006F));
            }
        }
    }

    private void borderSparkles(RandomSource random) {
        var pos = base().add(-1.35, 0.05, -1.35);
        if (random.nextBoolean()) pos.add(random.nextBoolean() ? 2.7 : 0, 0, random.nextFloat() * 2.7);
        else pos.add(random.nextFloat() * 2.7, 0, random.nextBoolean() ? 2.7 : 0);
        EffectHelper.of(EffectTemplatesAS.GENERIC_PARTICLE).spawn(pos)
                .color(random.nextBoolean() ? FXColorFunction.WHITE : FXColorFunction.constant(constellation.getConstellationColor()))
                .alpha(FXAlphaFunction.FADE_OUT).setScale(0.15F + random.nextFloat() * 0.08F)
                .setGravity(Vector3.y(0.0003F)).setMaxAge(40 + random.nextInt(10));
    }

    private void risingSparkles(RandomSource random, int stage) {
        var pos = base().add((random.nextFloat() - 0.5) * 2.6, 0.05, (random.nextFloat() - 0.5) * 2.6);
        EffectHelper.of(EffectTemplatesAS.GENERIC_PARTICLE).spawn(pos)
                .color(random.nextBoolean() ? FXColorFunction.WHITE : FXColorFunction.constant(constellation.getConstellationColor()))
                .alpha(FXAlphaFunction.FADE_OUT).setAlpha(0.75F)
                .setScale(stage >= 400 ? 0.25F : 0.17F)
                .setGravity(Vector3.y(0.001F + random.nextFloat() * 0.001F)).setMaxAge(25 + random.nextInt(10));
    }

    private void burst(RandomSource random, int count) {
        var color = constellation == null ? ColorWrapper.WHITE : constellation.getConstellationColor();
        for (int i = 0; i < count; i++) {
            EffectHelper.of(EffectTemplatesAS.GENERIC_PARTICLE).spawn(base().addY(random.nextFloat()))
                    .color(random.nextBoolean() ? FXColorFunction.WHITE : FXColorFunction.constant(color))
                    .alpha(FXAlphaFunction.FADE_OUT).setAlpha(0.75F).setScale(0.18F + random.nextFloat() * 0.1F)
                    .setMotion(Vector3.random(random).setY(0).normalize().multiply(0.025F + random.nextFloat() * 0.04F))
                    .setMaxAge(30 + random.nextInt(20));
        }
    }

    @Override public void finish() {
        stop();
        var level = relay.getLevel();
        if (level == null || !isPresent()) return;
        constellation = relay.getConstellation();
        // Completion is a server block event, never inferred from elapsed client time.
        burst(level.random, 25);
        beam(level.random, 6.0, 1.0, 0.7, 30);
        PlayableSoundInstance.of(SoundsAS.ATTUNEMENT_ALTAR_ITEM_FINISH)
                .pos(relay.getBlockPos().above()).volume(0.5F).pitch(1.25F).play();
    }

    @Override public void stop() {
        running = false;
        lastStage = -1;
        if (orbital != null) orbital.requestRemoval();
        if (flare != null) flare.requestRemoval();
        orbital = null;
        flare = null;
        // The native sound's stop predicate fades it out even after a chunk unload.
        loopSound = null;
    }
}
