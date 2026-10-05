package com.appliedastralsorcery.integration.jei;

import com.appliedastralsorcery.lumen.LumenContainerStrategy;
import com.appliedastralsorcery.lumen.LumenKey;
import com.appliedastralsorcery.chalice.ChaliceFluidMarker;
import appeng.api.stacks.AEFluidKey;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import hellfirepvp.astralsorcery.common.lumen.LumenStack;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class GhostIngredientResolver {
    private static final LumenContainerStrategy LUMEN_CONTAINERS = new LumenContainerStrategy();

    private GhostIngredientResolver() {}

    @Nullable
    public static AEFluidKey resolveFluid(Object ingredient) {
        return ChaliceFluidMarker.direct(ingredient);
    }

    @Nullable
    public static Lumen resolveLumen(Object ingredient) {
        Lumen lumen = null;
        if (ingredient instanceof LumenStack lumenStack && !lumenStack.isEmpty()) {
            lumen = lumenStack.getLumen();
        } else if (ingredient instanceof ItemStack itemStack && !itemStack.isEmpty()) {
            var content = LUMEN_CONTAINERS.getContainedStack(itemStack.copyWithCount(1));
            if (content != null && content.what() instanceof LumenKey key) {
                lumen = key.lumen();
            }
        }
        return lumen != null && lumen != LumenAS.NONE.get() && RegistriesAS.REGISTRY_LUMEN.getKey(lumen) != null
                ? lumen : null;
    }
}
