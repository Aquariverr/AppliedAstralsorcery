package com.appliedastralsorcery.mixin;

import java.util.Optional;

import com.appliedastralsorcery.starlight.FocalStarlightReceiver;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import hellfirepvp.astralsorcery.common.tile.network.FocusCrystalSourceNode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FocusCrystalSourceNode.class, remap = false)
public abstract class FocusCrystalSourceP2PMixin {
    @Inject(method = "produceStarlight", at = @At("HEAD"), cancellable = true)
    private void appliedas$focusTransportedStarlight(Level level,
            CallbackInfoReturnable<Optional<StarlightTransmissionPacket>> cir) {
        var source = (FocusCrystalSourceNode) (Object) this;
        // Naturally initialized sources keep their original production, without an extra P2P copy.
        if (source.getConstellation().isPresent() || !(level instanceof ServerLevel server)) return;
        var pos = source.getNodePos();
        if (server.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && server.getBlockEntity(pos) instanceof FocalStarlightReceiver receiver)
            cir.setReturnValue(receiver.appliedas$produceFocusedStarlight(server));
    }
}
