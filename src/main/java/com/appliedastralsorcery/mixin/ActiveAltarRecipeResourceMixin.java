package com.appliedastralsorcery.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.appliedastralsorcery.altar.AltarAutomationBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.lumen.LumenStack;
import hellfirepvp.astralsorcery.common.lumen.transfer.LumenRequestChain;
import hellfirepvp.astralsorcery.common.recipe.altar.ActiveAltarRecipe;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import hellfirepvp.astralsorcery.common.tile.TileAltar;
import hellfirepvp.astralsorcery.common.tile.TileChalice;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ActiveAltarRecipe.class, remap = false)
public abstract class ActiveAltarRecipeResourceMixin {
    @Shadow @Final private UUID playerUUID;
    @Shadow @Final private List<LumenStack> drawnLumen;
    @Unique private AltarAutomationBlockEntity appliedas$resourceSource;
    @Unique private boolean appliedas$drewCachedLumen;

    @Inject(method = "tickTrackAdditionalInputs", at = @At("HEAD"))
    private void appliedas$drawCachedLumen(TileAltar altar, ServerLevel level, BlockPos altarPos,
            Map<BaseConstellation, Float> aggregateStarlight, CallbackInfo ci) {
        appliedas$resourceSource = AltarAutomationBlockEntity.getResourceSource(altar, playerUUID);
        appliedas$drewCachedLumen = false;
        // AS draws at most one lumen type, up to 100 units, every 40 ticks.
        if (appliedas$resourceSource == null || altar.getTileData().getTicksExisted() % 40 != 0) return;
        var recipe = ((ActiveAltarRecipe) (Object) this).getRecipe(level).orElse(null);
        if (recipe == null) return;
        for (var required : appliedas$combineLumen(recipe.getRequiredLumen())) {
            if (required.isEmpty()) continue;
            var drawn = drawnLumen.stream().filter(stack -> stack.isSameLumen(required))
                    .findFirst().orElse(LumenStack.EMPTY);
            int needed = Math.min(100, required.getAmount() - drawn.getAmount());
            if (needed <= 0) continue;
            var drained = appliedas$resourceSource.getLumenHandler().drain(
                    required.copyWithAmount(needed), ILumenHandler.Action.EXECUTE);
            if (drained.isEmpty()) continue;
            if (drawn.isEmpty()) drawnLumen.add(drained.copy());
            else drawn.grow(drained.getAmount());
            appliedas$drewCachedLumen = true;
            break;
        }
    }

    @WrapOperation(method = {"tickTrackAdditionalInputs", "consumeItemInputs"}, at = @At(value = "INVOKE", target =
            "Lhellfirepvp/astralsorcery/common/recipe/altar/AltarRecipe;getRequiredLumen()Ljava/util/List;"))
    private List<LumenStack> appliedas$aggregateJobLumen(AltarRecipe recipe,
            Operation<List<LumenStack>> original, @Local(argsOnly = true) ServerLevel level,
            @Local(argsOnly = true) BlockPos altarPos) {
        var required = original.call(recipe);
        if (!(level.getBlockEntity(altarPos) instanceof TileAltar altar)
                || AltarAutomationBlockEntity.getResourceSource(altar, playerUUID) == null) return required;
        // Patterns combine equal keys; intake and final validation must require that same total.
        return appliedas$combineLumen(required);
    }

    @Unique
    private static List<LumenStack> appliedas$combineLumen(List<LumenStack> required) {
        var combined = new ArrayList<LumenStack>();
        for (var stack : required) {
            if (stack.isEmpty()) continue;
            var existing = combined.stream().filter(entry -> entry.isSameLumen(stack)).findFirst().orElse(null);
            if (existing == null) combined.add(stack.copy());
            else existing.setAmount((int) Math.min(Integer.MAX_VALUE, (long) existing.getAmount() + stack.getAmount()));
        }
        return combined;
    }

    @WrapOperation(method = "tickTrackAdditionalInputs", at = @At(value = "INVOKE", target =
            "Lhellfirepvp/astralsorcery/common/lumen/transfer/LumenRequestHelper;requestRelayed(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lhellfirepvp/astralsorcery/common/lumen/LumenStack;)Ljava/util/Optional;"))
    private Optional<LumenRequestChain> appliedas$useOneLumenTransfer(Level level, BlockPos altarPos,
            LumenStack request, Operation<Optional<LumenRequestChain>> original) {
        // A cached draw replaces this tick's relay transfer, preserving the native intake rate.
        return appliedas$drewCachedLumen ? Optional.empty() : original.call(level, altarPos, request);
}
    @WrapOperation(method = "tickTrackAdditionalInputs", at = @At(value = "INVOKE", target =
            "Lhellfirepvp/astralsorcery/common/tile/TileChalice$LiquidDrawInstance;consumeLiquid(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/neoforged/neoforge/fluids/FluidStack;Z)Z"))
    private boolean appliedas$drawCachedFluid(TileChalice.LiquidDrawInstance drawing, Level level,
            BlockPos altarPos, FluidStack request, boolean simulate, Operation<Boolean> original) {
        if (appliedas$resourceSource == null || request.isEmpty()) {
            return original.call(drawing, level, altarPos, request, simulate);
        }
        var handler = appliedas$resourceSource.getFluidHandler();
        var buffered = handler.drain(request, IFluidHandler.FluidAction.SIMULATE);
        int bufferedAmount = Math.min(request.getAmount(), buffered.getAmount());
        if (bufferedAmount <= 0) return original.call(drawing, level, altarPos, request, simulate);
        var worldRequired = request.copyWithAmount(request.getAmount() - bufferedAmount);
        if (!worldRequired.isEmpty()) {
            drawing.update(level, altarPos, worldRequired);
            if (!original.call(drawing, level, altarPos, worldRequired, simulate)) return false;
        }
        if (simulate) return true;
        return handler.drain(request.copyWithAmount(bufferedAmount), IFluidHandler.FluidAction.EXECUTE)
                .getAmount() == bufferedAmount;
    }
}
