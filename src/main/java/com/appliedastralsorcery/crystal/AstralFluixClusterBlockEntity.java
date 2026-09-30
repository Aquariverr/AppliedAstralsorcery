package com.appliedastralsorcery.crystal;

import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.block.tile.CelestialCrystalClusterBlock;
import hellfirepvp.astralsorcery.common.crystal.CrystalPropertyGenerator;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.tile.TileCelestialCrystalCluster;
import hellfirepvp.astralsorcery.common.util.data.TileRegistryObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class AstralFluixClusterBlockEntity extends TileCelestialCrystalCluster {
    public AstralFluixClusterBlockEntity(BlockPos pos, BlockState state) {
        super(new TileRegistryObject<>(ModContent.ASTRAL_FLUIX_CLUSTER_ENTITY), pos, state);
        getTileData().setCrystalAttributes(ModContent.ASTRAL_FLUIX_CRYSTAL.toStack().get(DataComponentsAS.CRYSTAL_ATTRIBUTES));
    }

    public void generateProperties() {
        getTileData().setCrystalAttributes(CrystalPropertyGenerator.generateRandomProperties(getTileData().getCrystalAttributes()));
        markForUpdate();
    }

    @Override
    public void setGrowth(Level level, int stage) {
        // The upstream method places its own block; keep this crystal and its saved attributes.
        var state = level.getBlockState(getBlockPos());
        if (state.is(ModContent.ASTRAL_FLUIX_CLUSTER)) {
            level.setBlockAndUpdate(getBlockPos(), state.setValue(CelestialCrystalClusterBlock.STAGE, Math.clamp(stage, 0, 4)));
        }
    }
}
