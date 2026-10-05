package com.appliedastralsorcery.crystallizer;

import net.minecraft.core.BlockPos;

/** A panel's working face can support the native cluster in its adjacent block. */
public interface WorldCrystallizerHost {
    BlockPos getCrystallizationTarget();
}
