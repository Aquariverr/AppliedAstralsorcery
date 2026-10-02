package com.appliedastralsorcery.crystal;

import javax.annotation.Nonnull;

import java.util.stream.Stream;

import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.MapCodec;
import hellfirepvp.astralsorcery.common.item.LumenCrystalItem;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

/** Accepts every registered lumen and exposes the actual colored crystals to recipe viewers. */
public final class LumenCrystalIngredient implements ICustomIngredient {
    public static final LumenCrystalIngredient INSTANCE = new LumenCrystalIngredient();
    public static final MapCodec<LumenCrystalIngredient> CODEC = MapCodec.unit(INSTANCE);

    private LumenCrystalIngredient() {}

    @Override
    public boolean test(ItemStack stack) {
        if (!stack.is(ItemsAS.LUMEN_CRYSTAL)) return false;
        var component = stack.get(DataComponentsAS.LUMEN);
        return component != null && component.lumen().value() != LumenAS.NONE.get();
    }

    @Override
    @Nonnull
    public Stream<ItemStack> getItems() {
        return RegistriesAS.REGISTRY_LUMEN.holders()
                .filter(lumen -> !lumen.is(LumenAS.NONE))
                .map(LumenCrystalItem::getCrystal);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    @Nonnull
    public IngredientType<?> getType() {
        return ModContent.LUMEN_CRYSTAL_INGREDIENT.get();
    }
}
