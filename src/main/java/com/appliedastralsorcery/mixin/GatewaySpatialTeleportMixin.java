package com.appliedastralsorcery.mixin;

import com.appliedastralsorcery.gateway.MECelestialGatewayBlockEntity;
import com.appliedastralsorcery.gateway.SpatialReturnPortalBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import hellfirepvp.astralsorcery.common.network.play.PktRequestGatewayTeleport;
import hellfirepvp.astralsorcery.common.util.data.Vector3;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Leave native source/destination validation and the star windup intact. */
@Mixin(value = PktRequestGatewayTeleport.class, remap = false)
public abstract class GatewaySpatialTeleportMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target =
            "Lhellfirepvp/astralsorcery/common/util/EntityUtil;transferEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/resources/ResourceKey;Lhellfirepvp/astralsorcery/common/util/data/Vector3;)Lnet/minecraft/world/entity/Entity;"))
    private static Entity appliedas$spatialDestination(Entity entity, ResourceKey<Level> dimension,
            Vector3 destination, Operation<Entity> original) {
        if (entity instanceof ServerPlayer player) {
            // The small pedestal only offers its fixed return action, never an outbound gateway network.
            if (player.level().getBlockEntity(player.blockPosition()) instanceof SpatialReturnPortalBlockEntity) return entity;
            var target = player.getServer().getLevel(dimension);
            if (target != null) {
                var tile = target.getBlockEntity(destination.toBlockPos());
                if (tile instanceof MECelestialGatewayBlockEntity) return entity;
                if (tile instanceof SpatialReturnPortalBlockEntity portal) {
                    // Use AS's own transfer, including its teleport event and cancellation hooks.
                    return portal.canReceive(player) ? original.call(entity, dimension, destination) : entity;
                }
            }
        }
        return original.call(entity, dimension, destination);
    }
}
