package com.appliedastralsorcery.mixin;

import appeng.items.storage.SpatialStorageCellItem;
import com.appliedastralsorcery.gateway.SpatialCellAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SpatialStorageCellItem.class, remap = false)
public abstract class SpatialCellFormattingMixin {
    @Inject(method = "doSpatialTransition", at = @At("HEAD"), cancellable = true)
    private void appliedas$finishFormattingFirst(ItemStack cell, ServerLevel level, BlockPos min, BlockPos max,
            int playerId, CallbackInfoReturnable<Boolean> cir) {
        if (SpatialCellAccess.isFormatting(cell)) cir.setReturnValue(false);
    }
}
