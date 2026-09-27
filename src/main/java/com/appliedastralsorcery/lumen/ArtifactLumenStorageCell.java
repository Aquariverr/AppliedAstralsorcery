package com.appliedastralsorcery.lumen;

import java.util.List;

import appeng.items.storage.BasicStorageCell;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.component.FlagsComponent;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Ten artifact enhancements, with compatibility for the original one-time flag. */
public final class ArtifactLumenStorageCell extends BasicStorageCell {
    public ArtifactLumenStorageCell() {
        super(new Properties().stacksTo(1), 2.5, 256, 256 * 8,
                ModContent.LUMEN_CELL_TYPES, LumenKeyType.INSTANCE);
    }

    public static boolean isEnhanced(ItemStack stack) {
        return getEnhancementLevel(stack) > 0;
    }

    public static int getEnhancementLevel(ItemStack stack) {
        int legacyLevel = stack.getOrDefault(DataComponentsAS.FLAGS, FlagsComponent.EMPTY)
                .isSet(FlagsComponent.Flag.IS_ARTIFACT_ENHANCED) ? 1 : 0;
        return Math.clamp(stack.getOrDefault(LumenCellEnhancement.LEVEL, legacyLevel), 0, LumenCellEnhancement.MAX_LEVEL);
    }

    @Override
    public int getBytes(ItemStack stack) {
        return super.getBytes(stack) << getEnhancementLevel(stack);
    }

    @Override
    public int getBytesPerType(ItemStack stack) {
        // Scale type overhead too, so usable capacity doubles for any fixed set of types.
        return super.getBytesPerType(stack) << getEnhancementLevel(stack);
    }

    @Override
    public int getTotalTypes(ItemStack stack) {
        return super.getTotalTypes(stack) + getEnhancementLevel(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        int level = getEnhancementLevel(stack);
        lines.add(Component.translatable("tooltip.appliedas.lumen_cell.artifact_enhanced",
                level, LumenCellEnhancement.MAX_LEVEL).withStyle(ChatFormatting.GRAY));
    }
}
