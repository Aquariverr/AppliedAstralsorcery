package com.appliedastralsorcery.parts;

import java.util.UUID;

import appeng.parts.automation.ItemPickupStrategy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class NonEmptyItemPickupStrategy extends ItemPickupStrategy {
    public NonEmptyItemPickupStrategy(ServerLevel level, BlockPos pos, Direction side, BlockEntity host,
            ItemEnchantments enchantments, @Nullable UUID owner) {
        super(level, pos, side, host, enchantments, owner);
    }
}
