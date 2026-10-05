package com.appliedastralsorcery.crystal;

import javax.annotation.Nonnull;

import java.util.stream.Stream;

import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import hellfirepvp.astralsorcery.common.component.CrystalAttributesComponent;
import hellfirepvp.astralsorcery.common.lib.CrystalPropertiesAS;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

public record CrystalSizeIngredient(int size) implements ICustomIngredient {
    public static final int MAX_SIZE = 63;
    public static final MapCodec<CrystalSizeIngredient> CODEC = Codec.intRange(0, MAX_SIZE)
            .fieldOf("size").xmap(CrystalSizeIngredient::new, CrystalSizeIngredient::size);

    @Override
    public boolean test(ItemStack stack) {
        if (!stack.is(ModContent.ASTRAL_FLUIX_CRYSTAL)) return false;
        var attributes = stack.get(DataComponentsAS.CRYSTAL_ATTRIBUTES);
        if (attributes == null) return false;
        // Astral Sorcery represents Size 0 by omitting the Size attribute.
        int tier = attributes.getAttributeTier(CrystalPropertiesAS.SIZE);
        return tier >= 0 && Math.min(tier, MAX_SIZE) == size;
    }

    @Override
    @Nonnull
    public Stream<ItemStack> getItems() {
        var stack = ModContent.ASTRAL_FLUIX_CRYSTAL.toStack();
        // No random properties in recipe viewers; purity and cut do not affect the yield.
        stack.set(DataComponentsAS.CRYSTAL_ATTRIBUTES,
                CrystalAttributesComponent.empty(0, 14).setAttributeTier(CrystalPropertiesAS.SIZE, size));
        return Stream.of(stack);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    @Nonnull
    public IngredientType<?> getType() {
        return ModContent.CRYSTAL_SIZE_INGREDIENT.get();
    }
}
