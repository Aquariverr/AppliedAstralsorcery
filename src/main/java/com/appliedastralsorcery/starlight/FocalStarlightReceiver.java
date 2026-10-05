package com.appliedastralsorcery.starlight;

import java.util.Optional;

import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import net.minecraft.server.level.ServerLevel;

/** Transient, unfocused input for an actual AS focus crystal. Never saved as a focal point. */
public interface FocalStarlightReceiver {
    void appliedas$receiveFocalStarlight(ServerLevel level, StarlightTransmissionPacket packet);

    Optional<StarlightTransmissionPacket> appliedas$produceFocusedStarlight(ServerLevel level);
}
