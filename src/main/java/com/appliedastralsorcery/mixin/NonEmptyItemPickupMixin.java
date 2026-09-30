package com.appliedastralsorcery.mixin;

import java.util.List;

import appeng.api.behaviors.PickupSink;
import appeng.api.behaviors.PickupStrategy;
import appeng.api.networking.energy.IEnergySource;
import appeng.parts.automation.ItemPickupStrategy;
import com.appliedastralsorcery.parts.NonEmptyItemPickupStrategy;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemPickupStrategy.class, remap = false)
public abstract class NonEmptyItemPickupMixin {
    @Inject(method = "tryPickup", at = @At(value = "INVOKE", target =
            "Lappeng/parts/automation/ItemPickupStrategy;calculateEnergyUsage(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/util/List;)F"),
            cancellable = true)
    private void appliedas$skipEmptyDrops(IEnergySource energySource, PickupSink sink,
            CallbackInfoReturnable<PickupStrategy.Result> cir, @Local List<ItemStack> items) {
        // Inspect the actual loot roll used by AE2, including its tool and enchantments.
        // Do not roll loot again, consume power, or destroy the block when it has no output.
        if ((Object) this instanceof NonEmptyItemPickupStrategy && items.stream().allMatch(ItemStack::isEmpty)) {
            cir.setReturnValue(PickupStrategy.Result.CANT_PICKUP);
        }
    }
}
