package com.appliedastralsorcery.mixin;

import com.appliedastralsorcery.crystallizer.WorldCrystallization;
import hellfirepvp.astralsorcery.common.block.tile.LumenCrystalClusterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LumenCrystalClusterBlock.class, remap = false)
public abstract class LumenCrystalPanelSupportMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void appliedas$supportFromCrystallizationPanel(BlockState state, LevelReader level,
            BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (WorldCrystallization.hasSupportingPanel(level, pos)) cir.setReturnValue(true);
    }
}
