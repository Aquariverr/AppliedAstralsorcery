package com.appliedastralsorcery.mixin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.appliedastralsorcery.infuser.MEStarlightInfuserBlock;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.structure.PatternInfuser;
import hellfirepvp.observerlib.api.block.SimpleMatchableBlock;
import hellfirepvp.observerlib.api.util.StructureBlockArray;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternInfuser.class, remap = false)
public abstract class InfuserStructureMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void appliedas$acceptMEInfuser(CallbackInfo ci) {
        ((StructureBlockArray) (Object) this).addBlock(new SimpleMatchableBlock(BlocksAS.INFUSER.get()) {
            @Override public boolean matches(@Nullable BlockGetter reader, @Nonnull BlockPos pos, @Nonnull BlockState state) {
                return state.getBlock() instanceof MEStarlightInfuserBlock || super.matches(reader, pos, state);
            }
        }, BlockPos.ZERO);
    }
}
