package com.appliedastralsorcery.crystal;

import javax.annotation.Nonnull;

import com.appliedastralsorcery.AppliedAstralsorcery;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.component.CrystalAttributesComponent;
import hellfirepvp.astralsorcery.common.entity.item.ItemEntityCrystal;
import hellfirepvp.astralsorcery.common.item.crystal.RockCrystalItem;
import hellfirepvp.astralsorcery.common.util.data.ColorWrapper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** A distinct crystal material with Astral Sorcery's native crystal attributes and splitting. */
@EventBusSubscriber(modid = AppliedAstralsorcery.MOD_ID)
public final class AstralFluixCrystalItem extends RockCrystalItem {
    // Match the azure lit rim of the item texture; the native light fan fades toward a brighter cyan-blue.
    public static final ColorWrapper COLOR = ColorWrapper.opaque(0x7CC8FF);
    public static final CrystalAttributesComponent DEFAULT_ATTRIBUTES = CrystalAttributesComponent.empty(6, 14);

    public AstralFluixCrystalItem() {
        // Use the same initial property budget and growth limit as celestial crystals.
        super(DEFAULT_ATTRIBUTES);
    }

    @Override
    public ColorWrapper getItemEntityColor(ItemStack stack) {
        return COLOR;
    }

    @SubscribeEvent
    public static void restoreDroppedColor(EntityJoinLevelEvent event) {
        // The upstream entity syncs its color but does not save it to NBT.
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof ItemEntityCrystal crystal
                && crystal.getItem().is(ModContent.ASTRAL_FLUIX_CRYSTAL)) {
            crystal.setColor(COLOR);
        }
    }

    @Override
    @Nonnull
    public RockCrystalItem getCrystalSplitItem() {
        return this;
    }

    @Override
    public Item getAttunedItem() {
        // Also retain the material if a datapack makes this crystal eligible for attunement.
        return this;
    }
}
