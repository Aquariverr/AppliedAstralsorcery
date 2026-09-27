package com.appliedastralsorcery.mixin;

import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import hellfirepvp.astralsorcery.common.lumen.LumenLike;
import hellfirepvp.astralsorcery.common.lumen.LumenStack;
import hellfirepvp.astralsorcery.common.lumen.capability.LumenHandlerView;
import hellfirepvp.astralsorcery.common.lumen.capability.LumenStackList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Astral Sorcery 2.0.0 loses the lumen identity when a stack reaches zero.
 * Its normal setLumenStack then removes NONE instead of the drained type, duplicating the final batch.
 * Keep the original drain's access checks and simulation behavior, but use the requested identity for removal.
 */
@Mixin(value = LumenHandlerView.class, remap = false)
public abstract class LumenHandlerViewMixin {
    @Shadow
    private void onContentChanged(Lumen lumen) {
        throw new AssertionError();
    }

    @Redirect(method = "drain", at = @At(value = "INVOKE", target =
            "Lhellfirepvp/astralsorcery/common/lumen/capability/LumenStackList;setLumenStack(Lhellfirepvp/astralsorcery/common/lumen/LumenStack;)V"))
    private void appliedas$storeRemainder(LumenStackList contents, LumenStack remainder,
            LumenLike requested, int amount, ILumenHandler.Action action) {
        if (remainder.isEmpty()) {
            contents.getModifiableLumenStacks().removeIf(stack -> stack.is(requested.asLumen()));
        } else {
            contents.setLumenStack(remainder);
        }
    }

    @Redirect(method = "drain", at = @At(value = "INVOKE", target =
            "Lhellfirepvp/astralsorcery/common/lumen/capability/LumenHandlerView;onContentChanged(Lhellfirepvp/astralsorcery/common/lumen/LumenStack;)V"))
    private void appliedas$notifyDrainedType(LumenHandlerView handler, LumenStack remainder,
            LumenLike requested, int amount, ILumenHandler.Action action) {
        onContentChanged(requested.asLumen());
    }
}
