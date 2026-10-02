package com.appliedastralsorcery.chisel;

import java.util.List;

import hellfirepvp.astralsorcery.common.component.CrystalAttributesComponent;
import hellfirepvp.astralsorcery.common.item.crystal.RockCrystalItem;
import hellfirepvp.astralsorcery.common.item.ArtifactItem;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.util.MiscUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Pure stack equivalents of AS 2.0.1.21's successful chisel operations, shared by both modes. */
public final class ChiselProcessing {
    private ChiselProcessing() {}

    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof RockCrystalItem
                || stack.is(ItemsAS.STARMETAL_INGOT) || stack.getItem() instanceof ArtifactItem);
    }

    public static boolean canProcess(ItemStack stack) {
        if (!accepts(stack)) return false;
        if (stack.getItem() instanceof RockCrystalItem) {
            var attributes = stack.get(DataComponentsAS.CRYSTAL_ATTRIBUTES);
            return attributes != null && attributes.getTotalTierCount() > 1;
        }
        return !(stack.getItem() instanceof ArtifactItem artifact) || artifact.createShard(stack).isPresent();
    }

    /** Works exclusively on copies. Returns both crystal halves without consuming the caller's source. */
    public static List<ItemStack> process(ItemStack input, RandomSource random, int fortuneLevel) {
        if (!canProcess(input)) return List.of();
        int fortune = Math.max(0, fortuneLevel);
        var original = input.copyWithCount(1);
        if (original.getItem() instanceof RockCrystalItem crystal) {
            var remaining = original.get(DataComponentsAS.CRYSTAL_ATTRIBUTES);
            if (remaining == null) return List.of();
            int splitCount = (remaining.getTotalTierCount() + 1) / 2;
            int lost = 0;
            if (splitCount > 1 && random.nextFloat() < 0.9F / (fortune + 1)) {
                lost++;
                if (splitCount > 2 && random.nextFloat() < 0.6F / (fortune + 1)) lost++;
            }
            var properties = remaining.getProperties();
            var split = CrystalAttributesComponent.empty(properties.generateCount(), properties.maxTierCount());
            for (int i = 0; i < splitCount; i++) {
                var attribute = MiscUtil.getRandomEntry(remaining.getAttributes(), random).orElseThrow();
                remaining = remaining.setAttributeTier(attribute, attribute.getTier() - 1);
                if (lost > 0) lost--;
                else split = split.setAttributeTier(attribute, split.getAttributeTier(attribute) + 1);
            }
            original.set(DataComponentsAS.CRYSTAL_ATTRIBUTES, remaining);
            var fragment = new ItemStack(crystal.getCrystalSplitItem());
            fragment.set(DataComponentsAS.CRYSTAL_ATTRIBUTES, split);
            return List.of(original, fragment);
        }
        if (original.is(ItemsAS.STARMETAL_INGOT)) {
            var dust = ItemsAS.STARDUST.toStack();
            return random.nextFloat() < 0.9F - Mth.clamp(fortune, 0, 10) * 0.07F ? List.of(dust) : List.of(original, dust);
        }
        var shard = ((ArtifactItem) original.getItem()).createShard(original).orElseThrow();
        return random.nextFloat() < 0.4F - Mth.clamp(fortune, 0, 10) * 0.03F ? List.of(shard) : List.of(original, shard);
    }
}
