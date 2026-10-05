package com.appliedastralsorcery.mixin;

import java.util.Optional;

import com.appliedastralsorcery.starlight.FocalStarlightReceiver;
import com.appliedastralsorcery.starlight.StarlightP2PTransport;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.event.helper.TemporaryFlightHelper;
import hellfirepvp.astralsorcery.common.focal.node.FocalPointNode;
import hellfirepvp.astralsorcery.common.lib.DataAS;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import hellfirepvp.astralsorcery.common.tile.TileStarlightFocusCrystal;
import hellfirepvp.astralsorcery.common.tile.network.FocusCrystalSourceNode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileStarlightFocusCrystal.class, remap = false)
public abstract class FocusCrystalP2PMixin implements FocalStarlightReceiver {
    @Unique private BaseConstellation appliedas$focalConstellation;
    @Unique private long appliedas$focalTick;
    @Unique private float appliedas$focalAmount;

    @Override
    public void appliedas$receiveFocalStarlight(ServerLevel level, StarlightTransmissionPacket packet) {
        var crystal = (TileStarlightFocusCrystal) (Object) this;
        if (!StarlightP2PTransport.isValid(packet)
                || crystal.getTileData().getConstellation().filter(packet.constellation()::equals).isEmpty()) return;
        if (appliedas$focalTick != level.getGameTime() || appliedas$focalConstellation != packet.constellation())
            appliedas$focalAmount = 0;
        appliedas$focalTick = level.getGameTime();
        appliedas$focalConstellation = packet.constellation();
        // A crystal can focus at most one complete focal point. Split inputs retain their fraction.
        appliedas$focalAmount = Math.min(1, appliedas$focalAmount + packet.amount());
    }

    @Unique
    private boolean appliedas$hasFocalStarlight(ServerLevel level) {
        var crystal = (TileStarlightFocusCrystal) (Object) this;
        long age = level.getGameTime() - appliedas$focalTick;
        return !crystal.isRemoved() && appliedas$focalAmount > 0 && age >= 0 && age <= 2
                && crystal.getTileData().getConstellation().filter(cst -> cst.equals(appliedas$focalConstellation)).isPresent();
    }

    @Override
    public Optional<StarlightTransmissionPacket> appliedas$produceFocusedStarlight(ServerLevel level) {
        if (!appliedas$hasFocalStarlight(level)) return Optional.empty();
        var crystal = (TileStarlightFocusCrystal) (Object) this;
        // Never initialize the saved source: otherwise it keeps generating after P2P disconnects.
        var temporary = new FocusCrystalSourceNode(crystal.getBlockPos());
        temporary.setConstellation(appliedas$focalConstellation);
        temporary.setCrystalProperties(crystal.getTileData().getCrystalAttributes());
        return temporary.produceStarlight(level).map(packet -> packet.withMultiplier(appliedas$focalAmount));
    }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private void appliedas$updateFocalActivation(ServerLevel level, CallbackInfo ci) {
        var crystal = (TileStarlightFocusCrystal) (Object) this;
        var data = crystal.getTileData();
        boolean supplied = appliedas$hasFocalStarlight(level);
        boolean natural = DataAS.DOMAIN_AS.getData(level, DataAS.KEY_FOCAL_POINT_DATA).getNode(crystal.getBlockPos())
                .filter(point -> data.getConstellation().filter(point.getConstellation()::equals).isPresent())
                .flatMap(FocalPointNode::getFocalPosition).filter(crystal.getBlockPos()::equals).isPresent();
        boolean active = supplied || natural;
        // Recompute on load too: the native visual flag is saved, but the P2P supply is not.
        if (data.isOnFocalNode() != active) {
            ((FocusCrystalDataAccessor) data).appliedas$setIsOnFocalNode(active);
            data.markForUpdate(false);
        }
        if (supplied && !natural && data.getTicksExisted() % 20 == 0) {
            crystal.doesSeeSky();
            crystal.hasStructure();
            var area = AABB.ofSize(Vec3.atCenterOf(crystal.getBlockPos()), 17, 17, 17);
            level.getPlayers(player -> player.canBeSeenByAnyone() && area.contains(player.position()))
                    .forEach(player -> TemporaryFlightHelper.allowFlight(player, 60));
        }
    }
}
