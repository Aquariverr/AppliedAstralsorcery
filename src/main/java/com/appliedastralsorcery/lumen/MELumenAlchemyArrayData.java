package com.appliedastralsorcery.lumen;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import hellfirepvp.astralsorcery.common.lumen.capability.LumenStackList;
import hellfirepvp.astralsorcery.common.recipe.lumen.LumenGenerationRecipe;
import hellfirepvp.astralsorcery.common.tile.TileLumenArray;
import hellfirepvp.astralsorcery.common.util.RecipeFinder;
import hellfirepvp.astralsorcery.common.util.inventory.InventoryStackList;
import hellfirepvp.astralsorcery.common.util.tank.FluidContainerList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class MELumenAlchemyArrayData extends MELumenArrayData {
    public static final Codec<TileLumenArray.Data> CODEC = RecordCodecBuilder.<MELumenAlchemyArrayData>create(
            instance -> lumenArrayFields(instance).apply(instance, MELumenAlchemyArrayData::new))
            .xmap(data -> data, data -> (MELumenAlchemyArrayData) data);

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private MELumenAlchemyArrayData(long ticks, boolean structure, Map<BlockPos, Boolean> sky, Optional<UUID> owner,
            LumenStackList lumen, FluidContainerList fluid, InventoryStackList inventory, boolean extended, Lumen assigned) {
        super(ticks, structure, sky, owner, lumen, fluid, inventory, extended, assigned);
    }

    @Override protected Optional<RecipeHolder<LumenGenerationRecipe>> findMatchingRecipe(ItemStack stack) {
        return RecipeFinder.of().flatMap(finder -> finder.findLumenGenerationRecipe(stack, getAssignedLumen(), true));
    }

    void consumeStarlight(int amount) {
        var remaining = getContainedFluid().copy();
        remaining.shrink(amount);
        getFluidContents().getTank(0).setContent(remaining);
        markForUpdate();
    }
}
