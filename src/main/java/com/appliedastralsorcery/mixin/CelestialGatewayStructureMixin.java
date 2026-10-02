package com.appliedastralsorcery.mixin;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;
import net.minecraft.MethodsReturnNonnullByDefault;

import com.appliedastralsorcery.gateway.MECelestialGatewayBlock;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.structure.PatternCelestialGateway;
import hellfirepvp.observerlib.api.block.SimpleMatchableBlock;
import hellfirepvp.observerlib.api.util.StructureBlockArray;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternCelestialGateway.class, remap = false)
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class CelestialGatewayStructureMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void appliedas$acceptMEGateway(CallbackInfo ci) {
        // Keep the native pattern/observer (and wand previews); only extend its center matcher.
        ((StructureBlockArray) (Object) this).addBlock(new SimpleMatchableBlock(BlocksAS.CELESTIAL_GATEWAY.get()) {
            @Override public boolean matches(@Nullable BlockGetter reader, BlockPos pos, BlockState state) {
                return state.getBlock() instanceof MECelestialGatewayBlock || super.matches(reader, pos, state);
            }
        }, BlockPos.ZERO);
    }
}
