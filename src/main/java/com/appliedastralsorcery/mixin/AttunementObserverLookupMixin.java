package com.appliedastralsorcery.mixin;

import java.util.Optional;

import com.appliedastralsorcery.attunement.AttunementLayout;
import hellfirepvp.astralsorcery.common.lib.ObserversAS;
import hellfirepvp.astralsorcery.common.util.data.ObserverRegistryObject;
import hellfirepvp.observerlib.api.ObserverProvider;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The native preview packet looks up only Astral Sorcery's own deferred register. */
@Mixin(value = ObserversAS.class, remap = false)
public abstract class AttunementObserverLookupMixin {
    @Inject(method = "getByName", at = @At("HEAD"), cancellable = true)
    private static void appliedas$findAttunementObserver(ResourceKey<ObserverProvider<?>> key,
            CallbackInfoReturnable<Optional<ObserverRegistryObject>> cir) {
        if (AttunementLayout.OBSERVER.observer().getKey().equals(key))
            cir.setReturnValue(Optional.of(AttunementLayout.OBSERVER));
    }
}
