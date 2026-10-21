package com.appliedastralsorcery.client;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Reuses one ring transform for all particle origins in an altar tick. No client classes are loaded. */
public final class AttunementRingGeometry {
    static final float REST_RADIUS = 16.5F / 16, RISEN_RADIUS = 17.5F / 16;
    static final float REST_Y = 4.5F / 16, RISE_Y = 6.5F / 16;
    static final float TILT = 9, TILT_NOD = 5;
    static final float NOD_CYCLE = 360, STAR_BOB_CYCLE = 120;
    private final Matrix4f ring = new Matrix4f();
    private final Matrix4f star = new Matrix4f();
    private final Quaternionf rotation = new Quaternionf();
    private float clock, rise, phase;
    private int count;

    public void update(float clock, float rise, float phase, int count) {
        this.clock = clock;
        this.rise = rise;
        this.phase = phase;
        this.count = count;
        float precession = clock * 0.2F;
        float tilt = TILT + TILT_NOD * Mth.sin(clock * Mth.TWO_PI / NOD_CYCLE);
        ring.identity().translate(0.5F, REST_Y + RISE_Y * rise, 0.5F)
                .rotate(rotation.rotationY(precession * Mth.DEG_TO_RAD))
                .rotate(rotation.rotationX(tilt * rise * Mth.DEG_TO_RAD))
                .rotate(rotation.rotationY(-precession * Mth.DEG_TO_RAD));
    }

    public Vector3f starCentre(int index, Vector3f result) {
        float angle = -(phase + index * 360F / count);
        star.set(ring).rotate(rotation.rotationY(angle * Mth.DEG_TO_RAD))
                .translate(0, rise * 0.35F / 16 * Mth.sin(clock * Mth.TWO_PI / STAR_BOB_CYCLE + index * 0.52F),
                        -Mth.lerp(rise, REST_RADIUS, RISEN_RADIUS));
        return star.getTranslation(result);
    }
}
