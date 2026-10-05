package com.appliedastralsorcery.mixin;

import hellfirepvp.astralsorcery.common.tile.TileStarlightFocusCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = TileStarlightFocusCrystal.Data.class, remap = false)
public interface FocusCrystalDataAccessor {
    @Invoker("setIsOnFocalNode")
    void appliedas$setIsOnFocalNode(boolean active);
}
