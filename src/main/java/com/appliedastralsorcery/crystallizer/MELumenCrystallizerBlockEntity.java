package com.appliedastralsorcery.crystallizer;

import appeng.api.networking.GridFlags;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.util.SettingsFrom;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import hellfirepvp.astralsorcery.common.recipe.lumen.LumenCrystallizationRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Retains placed blocks from older worlds; new panel items install as AE cable parts. */
public final class MELumenCrystallizerBlockEntity extends AENetworkedBlockEntity implements WorldCrystallizerHost {
    private final WorldCrystallization crystallization = new WorldCrystallization();
    private ItemStack clientMarker = ItemStack.EMPTY;

    public MELumenCrystallizerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(2);
    }

    @SuppressWarnings("unused") // Used by src/gametest when -PattunementGameTests is enabled.
    public WorldCrystallization getWorldCrystallization() { return crystallization; }
    public ItemStack getMarker() {
        return level != null && level.isClientSide() ? clientMarker.copy() : crystallization.getMarker();
    }
    @Override public BlockPos getCrystallizationTarget() {
        return worldPosition.relative(getBlockState().getValue(MELumenCrystallizerBlock.BASE_FACE).getOpposite());
    }

    public RecipeHolder<LumenCrystallizationRecipe> findRecipe(ItemStack stack) {
        return crystallization.findRecipe(level, stack);
    }

    public Lumen markCatalyst(ItemStack stack) {
        var lumen = crystallization.markCatalyst(level, stack);
        if (lumen != null) {
            setChanged();
            markForClientUpdate();
        }
        return lumen;
    }

    public void clearMarker() {
        crystallization.clearMarker();
        setChanged();
        markForClientUpdate();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server) || !getMainNode().isOnline()) return;
        var grid = getMainNode().getGrid();
        if (grid != null && crystallization.tick(server, getCrystallizationTarget(),
                grid.getStorageService().getInventory(), IActionSource.ofMachine(this), server.random::nextInt))
            setChanged();
    }

    @Override public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("crystallizer", crystallization.save(registries, true));
    }

    @Override public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        crystallization.load(tag.getCompound("crystallizer"), registries, true);
    }

    @Override protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(data, crystallization.getMarker());
    }

    @Override protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        var next = ItemStack.OPTIONAL_STREAM_CODEC.decode(data);
        changed |= !ItemStack.matches(clientMarker, next);
        clientMarker = next;
        return changed;
    }

    @Override public void exportSettings(SettingsFrom from, DataComponentMap.Builder output, Player player) {
        super.exportSettings(from, output, player);
        if (level != null) {
            var tag = new CompoundTag();
            tag.put("crystallizer", crystallization.save(level.registryAccess(), from == SettingsFrom.DISMANTLE_ITEM));
            output.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override public void importSettings(SettingsFrom from, DataComponentMap input, Player player) {
        super.importSettings(from, input, player);
        var settings = input.get(DataComponents.CUSTOM_DATA);
        if (settings != null && level != null) {
            var tag = settings.copyTag();
            crystallization.load(tag.contains("crystallizer") ? tag.getCompound("crystallizer") : tag,
                    level.registryAccess(), from == SettingsFrom.DISMANTLE_ITEM);
            setChanged();
            markForClientUpdate();
        }
    }
}
