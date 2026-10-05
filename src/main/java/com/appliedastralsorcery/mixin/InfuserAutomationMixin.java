package com.appliedastralsorcery.mixin;

import java.util.Optional;
import com.appliedastralsorcery.infuser.MEStarlightInfuserBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import hellfirepvp.astralsorcery.common.tile.TileInfuser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TileInfuser.class, remap = false)
public abstract class InfuserAutomationMixin {
    @WrapOperation(method = "lambda$doCraftingCycle$0", at = @At(value = "INVOKE", target =
            "Lhellfirepvp/astralsorcery/common/util/ItemUtil;dropItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Ljava/util/Optional;"))
    private Optional<ItemEntity> appliedas$bufferOutput(Level level, BlockPos pos, ItemStack stack,
            Operation<Optional<ItemEntity>> original) {
        if ((Object) this instanceof MEStarlightInfuserBlockEntity infuser) {
            infuser.collectOutput(stack);
            return Optional.empty();
        }
        return original.call(level, pos, stack);
    }
}
