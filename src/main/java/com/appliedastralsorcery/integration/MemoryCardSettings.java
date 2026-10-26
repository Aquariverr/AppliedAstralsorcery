package com.appliedastralsorcery.integration;

import net.minecraft.nbt.CompoundTag;

/** User configuration only; never include inventories, tanks, ownership or work in progress. */
public interface MemoryCardSettings {
    CompoundTag exportMemoryCardSettings();

    void importMemoryCardSettings(CompoundTag settings);
}
