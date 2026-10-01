package com.appliedastralsorcery.mixin;

import java.util.Optional;
import com.appliedastralsorcery.gateway.GatewaySourceEntry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import hellfirepvp.astralsorcery.client.helper.GatewayUserInterface;
import hellfirepvp.astralsorcery.common.data.level.CelestialGatewayData.GatewayEntry;
import hellfirepvp.astralsorcery.common.data.sync.client.CelestialGatewayClientData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GatewayUserInterface.class, remap = false)
public abstract class GatewaySourceUiMixin {
    @WrapOperation(method = "create", at = @At(value = "INVOKE", target =
            "Lhellfirepvp/astralsorcery/common/data/sync/client/CelestialGatewayClientData;getEntry(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;)Ljava/util/Optional;"))
    private static Optional<GatewayEntry> appliedas$localSource(CelestialGatewayClientData data, ResourceKey<Level> key,
            BlockPos pos, Operation<Optional<GatewayEntry>> original, @Local(argsOnly = true) Level level) {
        return GatewaySourceEntry.resolve(level, pos).or(() -> original.call(data, key, pos));
    }
}
