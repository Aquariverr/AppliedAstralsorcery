package com.appliedastralsorcery.mixin;

import com.appliedastralsorcery.starlight.StarlightP2PTransport;
import hellfirepvp.astralsorcery.common.recipe.focal.place.ActiveTransmutationHandler;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.TriState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ActiveTransmutationHandler.class, remap = false)
public abstract class StarlightP2PEndpointMixin {
    @Inject(method = "receiveStarlight(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lhellfirepvp/astralsorcery/common/starlight/transmission/StarlightTransmissionPacket;)Lnet/neoforged/neoforge/common/util/TriState;",
            at = @At("HEAD"), cancellable = true)
    private static void appliedas$receiveTunnelStarlight(ServerLevel level, BlockPos pos,
            StarlightTransmissionPacket packet, CallbackInfoReturnable<TriState> cir) {
        if (StarlightP2PTransport.receiveAt(level, pos, packet)) cir.setReturnValue(TriState.TRUE);
    }
}
