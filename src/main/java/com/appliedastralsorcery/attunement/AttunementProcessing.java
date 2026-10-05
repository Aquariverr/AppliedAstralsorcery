package com.appliedastralsorcery.attunement;

import hellfirepvp.astralsorcery.common.component.AttunedConstellationComponent;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.constellation.level.LevelSkyHandler;
import hellfirepvp.astralsorcery.common.item.base.AttuneableItem;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.constants.TagsAS;
import hellfirepvp.astralsorcery.common.util.ItemUtil;
import hellfirepvp.astralsorcery.common.util.level.DayTimeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class AttunementProcessing {
    private AttunementProcessing() {}

    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && stack.is(TagsAS.Items.FUNCTIONAL_ATTUNEABLE_ITEM)
                && stack.getOrDefault(DataComponentsAS.ATTUNED_CONSTELLATION,
                        AttunedConstellationComponent.EMPTY).getConstellation().isEmpty();
    }

    public static boolean isVisible(Level level, BlockPos altar, BaseConstellation constellation) {
        return DayTimeHelper.isNight(level) && level.canSeeSky(altar.above())
                && LevelSkyHandler.getContext(level)
                        .map(context -> context.getConstellationHandler().isCurrentlyActive(constellation)).orElse(false);
    }

    public static ItemStack attune(Level level, ItemStack input, BaseConstellation constellation) {
        if (!accepts(input)) return ItemStack.EMPTY;
        var result = input.copyWithCount(1);
        // Match native attunement, preserving crystal attributes and all other components.
        if (input.getItem() instanceof AttuneableItem item) {
            result = ItemUtil.swapItem(level.registryAccess(), result, item.getAttunedItem()).orElse(ItemStack.EMPTY);
        }
        if (!result.isEmpty()) result.set(DataComponentsAS.ATTUNED_CONSTELLATION,
                new AttunedConstellationComponent(constellation));
        return result;
    }
}
