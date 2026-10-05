package com.appliedastralsorcery.tree;

import java.util.EnumSet;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.me.helpers.IGridConnectedBlockEntity;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.tile.TileTranslucentTree;
import hellfirepvp.astralsorcery.common.tile.TileTreeBeacon;
import hellfirepvp.astralsorcery.common.util.BlockUtil;
import hellfirepvp.astralsorcery.common.util.data.TileRegistryObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The native beacon keeps its tree, starlight and lumen behavior; only its harvest destination changes. */
public final class METreeBeaconBlockEntity extends TileTreeBeacon implements IGridConnectedBlockEntity {
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this,
            (owner, node) -> owner.setChanged())
            .setInWorldNode(true).setExposedOnSides(EnumSet.allOf(Direction.class))
            .setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(1.0);

    public METreeBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(new TileRegistryObject<>(ModContent.TREE_BEACON_ENTITY), pos, state);
    }

    @Override public IManagedGridNode getMainNode() { return mainNode; }
    @Override public IGridNode getGridNode(Direction side) { return mainNode.getNode(); }
    @Override public IGridNode getActionableNode() { return mainNode.getNode(); }
    @Override public void saveChanges() { setChanged(); }

    @Override public void serverTick(ServerLevel server) {
        if (!mainNode.isReady()) {
            mainNode.setVisualRepresentation(ModContent.ME_TREE_BEACON_ITEM.get());
            mainNode.create(server, worldPosition);
        }
        super.serverTick(server);
    }

    @Override protected void doHarvest(ServerLevel server, TileTranslucentTree tree) {
        var stored = tree.getTileData().getStoredState();
        if (stored.isAir()) return;
        // Match the native fortune level and roll once. Never collect unrelated items near the beacon.
        for (var stack : BlockUtil.getDrops(server, stored, tree.getBlockPos(), 2)) {
            var dropPos = worldPosition.offset(Mth.nextInt(rand, -2, 2),
                    Mth.nextInt(rand, -2, 2) + 2, Mth.nextInt(rand, -2, 2));
            if (stack.isEmpty() || !server.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) continue;
            var grid = mainNode.getGrid();
            if (mainNode.isOnline() && grid != null) {
                long inserted = grid.getStorageService().getInventory().insert(
                        AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, IActionSource.ofMachine(this));
                stack.shrink((int) inserted);
            }
            // An offline or full network retains the original drop behavior, including partial insertions.
            if (!stack.isEmpty()) Block.popResource(server, dropPos, stack);
        }
    }

    @Override public void setRemoved() { mainNode.destroy(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { mainNode.destroy(); super.onChunkUnloaded(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
    }
}
