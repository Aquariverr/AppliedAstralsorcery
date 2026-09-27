package com.appliedastralsorcery.client;

import com.appliedastralsorcery.AppliedAstralsorcery;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.lumen.MELumenFilamentBlock;
import com.appliedastralsorcery.lumen.MELumenFilamentBlockEntity;
import hellfirepvp.astralsorcery.client.effect.EffectHelper;
import hellfirepvp.astralsorcery.client.effect.function.FXAlphaFunction;
import hellfirepvp.astralsorcery.client.effect.function.FXColorFunction;
import hellfirepvp.astralsorcery.client.lib.EffectTemplatesAS;
import hellfirepvp.astralsorcery.client.lib.TexturesAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import hellfirepvp.astralsorcery.common.util.VectorUtil;
import hellfirepvp.astralsorcery.common.util.data.ColorWrapper;
import hellfirepvp.astralsorcery.common.util.data.Vector3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = AppliedAstralsorcery.MOD_ID, value = Dist.CLIENT)
public final class MELumenFilamentEffects {
    private MELumenFilamentEffects() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ModContent.registerFilamentClientTicker(MELumenFilamentEffects::tick));
    }

    public static void tick(Level level, BlockPos blockPos, BlockState state, MELumenFilamentBlockEntity entity) {
        var random = level.random;
        var offset = MELumenFilamentBlock.getEffectOffset(state);
        var center = new Vector3(blockPos.getX() + offset.x, blockPos.getY() + offset.y,
                blockPos.getZ() + offset.z);
        Lumen transmittedLumen = LumenAS.NONE.get();
        long elapsed = level.getGameTime() - entity.getTransmittedLumenGameTime();
        if (elapsed >= 0 && elapsed < 60) {
            transmittedLumen = entity.getRecentlyTransmittedLumen();
        }

        // Rotate both the emitter and its local upward drift with the knot.
        for (int i = 0; i < 2; i++) {
            Vector3 pos = VectorUtil.withRandomOffset(center, random, 0.08F);
            EffectHelper.of(EffectTemplatesAS.GENERIC_PARTICLE)
                    .spawn(pos)
                    .color(FXColorFunction.constant(random.nextInt(3) == 0
                            ? ColorWrapper.WHITE : transmittedLumen.getColor(level, pos)))
                    .setScale(0.12F + random.nextFloat() * 0.2F)
                    .alpha(FXAlphaFunction.FADE_OUT)
                    .setGravity(outwardAcceleration(state, 0.0012F))
                    .setMaxAge(24 + random.nextInt(10));
        }

        if (transmittedLumen != LumenAS.NONE.get() && random.nextInt(20) == 0) {
            Vector3 pos = VectorUtil.withRandomOffset(center, random, 0.3F);
            EffectHelper.of(EffectTemplatesAS.LUMEN_PARTICLE)
                    .spawn(pos)
                    .setSprite(TexturesAS.ATLAS_LUMEN, RegistriesAS.REGISTRY_LUMEN.getKey(transmittedLumen))
                    .setAlpha(0.3F)
                    .color(FXColorFunction.constant(transmittedLumen.getColor(level, pos)))
                    .setGravity(outwardAcceleration(state, 0.00007F));
        }
    }

    private static Vector3 outwardAcceleration(BlockState state, float acceleration) {
        var outward = state.getValue(MELumenFilamentBlock.BASE_FACE).getOpposite();
        return new Vector3(outward.getStepX() * acceleration, outward.getStepY() * acceleration,
                outward.getStepZ() * acceleration);
    }
}
