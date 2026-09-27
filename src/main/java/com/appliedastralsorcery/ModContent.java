package com.appliedastralsorcery;

import java.util.List;

import appeng.api.stacks.AEKeyType;
import appeng.items.storage.BasicStorageCell;
import appeng.api.AECapabilities;
import appeng.blockentity.AEBaseBlockEntity;
import com.appliedastralsorcery.lumen.LumenKeyType;
import com.appliedastralsorcery.lumen.ArtifactLumenStorageCell;
import com.appliedastralsorcery.lumen.LumenCellEnhancement;
import com.appliedastralsorcery.lumen.MELumenFilamentBlock;
import com.appliedastralsorcery.lumen.MELumenFilamentBlockEntity;
import com.appliedastralsorcery.lumen.MELumenFilamentMenu;
import com.appliedastralsorcery.lumen.MELumenArrayBlock;
import com.appliedastralsorcery.lumen.MELumenArrayBlockEntity;
import com.appliedastralsorcery.lumen.MELumenArrayMenu;
import com.appliedastralsorcery.chalice.MEChaliceBlock;
import com.appliedastralsorcery.chalice.MEChaliceBlockEntity;
import com.appliedastralsorcery.chalice.MEChaliceMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModContent {
    public static final int LUMEN_CELL_TYPES = 5;
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AppliedAstralsorcery.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AppliedAstralsorcery.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AppliedAstralsorcery.MOD_ID);
    private static final DeferredRegister<AEKeyType> KEY_TYPES =
            DeferredRegister.create(AEKeyType.REGISTRY_KEY, AppliedAstralsorcery.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AppliedAstralsorcery.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, AppliedAstralsorcery.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<MELumenFilamentMenu>> FILAMENT_MENU =
            MENUS.register("me_lumen_filament", () -> new MenuType<>(MELumenFilamentMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<MELumenArrayMenu>> ARRAY_MENU =
            MENUS.register("me_lumen_array", () -> new MenuType<>(MELumenArrayMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<MEChaliceMenu>> CHALICE_MENU =
            MENUS.register("me_chalice", () -> new MenuType<>(MEChaliceMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredBlock<MEChaliceBlock> ME_CHALICE = BLOCKS.register("me_chalice", MEChaliceBlock::new);
    public static final DeferredItem<BlockItem> ME_CHALICE_ITEM = ITEMS.registerSimpleBlockItem(ME_CHALICE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MEChaliceBlockEntity>> CHALICE_ENTITY =
            BLOCK_ENTITIES.register("me_chalice", () -> BlockEntityType.Builder.of(
                    MEChaliceBlockEntity::new, ME_CHALICE.get()).build(null));
    public static final DeferredBlock<MELumenArrayBlock> ME_LUMEN_ARRAY = BLOCKS.register("me_lumen_array", MELumenArrayBlock::new);
    public static final DeferredItem<BlockItem> ME_LUMEN_ARRAY_ITEM = ITEMS.registerSimpleBlockItem(ME_LUMEN_ARRAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MELumenArrayBlockEntity>> ARRAY_ENTITY =
            BLOCK_ENTITIES.register("me_lumen_array", () -> BlockEntityType.Builder.of(
                    MELumenArrayBlockEntity::new, ME_LUMEN_ARRAY.get()).build(null));

    public static final DeferredItem<Item> LUMEN_CELL_HOUSING = ITEMS.registerSimpleItem("lumen_cell_housing");
    public static final DeferredItem<Item> LUMEN_COMPONENT = ITEMS.registerSimpleItem("lumen_storage_component_1k");
    public static final DeferredItem<BasicStorageCell> LUMEN_CELL = ITEMS.register("lumen_storage_cell_1k",
            () -> new BasicStorageCell(new Item.Properties().stacksTo(1), 0.5, 1, 8, LUMEN_CELL_TYPES, LumenKeyType.INSTANCE));
    public static final DeferredItem<Item> LUMEN_COMPONENT_4K = ITEMS.registerSimpleItem("lumen_storage_component_4k");
    public static final DeferredItem<Item> LUMEN_COMPONENT_16K = ITEMS.registerSimpleItem("lumen_storage_component_16k");
    public static final DeferredItem<Item> LUMEN_COMPONENT_64K = ITEMS.registerSimpleItem("lumen_storage_component_64k");
    public static final DeferredItem<Item> LUMEN_COMPONENT_256K = ITEMS.registerSimpleItem("lumen_storage_component_256k");
    public static final DeferredItem<BasicStorageCell> LUMEN_CELL_4K = registerCell(4, 1.0);
    public static final DeferredItem<BasicStorageCell> LUMEN_CELL_16K = registerCell(16, 1.5);
    public static final DeferredItem<BasicStorageCell> LUMEN_CELL_64K = registerCell(64, 2.0);
    public static final DeferredItem<BasicStorageCell> LUMEN_CELL_256K = ITEMS.register(
            "lumen_storage_cell_256k", ArtifactLumenStorageCell::new);
    public static final List<DeferredItem<Item>> LUMEN_COMPONENTS = List.of(
            LUMEN_COMPONENT, LUMEN_COMPONENT_4K, LUMEN_COMPONENT_16K, LUMEN_COMPONENT_64K, LUMEN_COMPONENT_256K);
    public static final List<DeferredItem<BasicStorageCell>> LUMEN_CELLS = List.of(
            LUMEN_CELL, LUMEN_CELL_4K, LUMEN_CELL_16K, LUMEN_CELL_64K, LUMEN_CELL_256K);

    private static DeferredItem<BasicStorageCell> registerCell(int kilobytes, double idleDrain) {
        return ITEMS.register("lumen_storage_cell_" + kilobytes + "k",
                () -> new BasicStorageCell(new Item.Properties().stacksTo(1), idleDrain,
                        kilobytes, kilobytes * 8, LUMEN_CELL_TYPES, LumenKeyType.INSTANCE));
    }
    public static final DeferredBlock<MELumenFilamentBlock> ME_LUMEN_FILAMENT =
            BLOCKS.register("me_lumen_filament", MELumenFilamentBlock::new);
    public static final DeferredItem<BlockItem> ME_LUMEN_FILAMENT_ITEM = ITEMS.registerSimpleBlockItem(ME_LUMEN_FILAMENT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MELumenFilamentBlockEntity>> FILAMENT_ENTITY =
            BLOCK_ENTITIES.register("me_lumen_filament", ModContent::createFilamentType);

    private static BlockEntityType<MELumenFilamentBlockEntity> createFilamentType() {
        var type = BlockEntityType.Builder.of(ModContent::newFilament, ME_LUMEN_FILAMENT.get()).build(null);
        AEBaseBlockEntity.registerBlockEntityItem(type, ME_LUMEN_FILAMENT_ITEM.get());
        ME_LUMEN_FILAMENT.get().setBlockEntity(MELumenFilamentBlockEntity.class, type, null,
                (level, pos, state, entity) -> entity.serverTick());
        return type;
    }

    private static MELumenFilamentBlockEntity newFilament(net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        return new MELumenFilamentBlockEntity(FILAMENT_ENTITY.get(), pos, state);
    }

    public static void registerFilamentClientTicker(BlockEntityTicker<MELumenFilamentBlockEntity> ticker) {
        ME_LUMEN_FILAMENT.get().setBlockEntity(MELumenFilamentBlockEntity.class, FILAMENT_ENTITY.get(), ticker,
                (level, pos, state, entity) -> entity.serverTick());
    }

    static {
        KEY_TYPES.register("lumen", () -> LumenKeyType.INSTANCE);
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.appliedas"))
                .icon(() -> LUMEN_CELL.toStack())
                .displayItems((parameters, output) -> {
                    output.accept(LUMEN_CELL_HOUSING);
                    LUMEN_COMPONENTS.forEach(output::accept);
                    LUMEN_CELLS.forEach(output::accept);
                    output.accept(ME_LUMEN_FILAMENT_ITEM);
                    output.accept(ME_LUMEN_ARRAY_ITEM);
                    output.accept(ME_CHALICE_ITEM);
                }).build());
    }

    private ModContent() {}

    public static void register(IEventBus bus) {
        LumenCellEnhancement.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        KEY_TYPES.register(bus);
        TABS.register(bus);
        MENUS.register(bus);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, FILAMENT_ENTITY.get(),
                        (entity, context) -> entity));
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, CHALICE_ENTITY.get(), (entity, context) -> entity);
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, CHALICE_ENTITY.get(),
                    (entity, side) -> side == null || side == net.minecraft.core.Direction.DOWN ? entity.getTankView() : null);
            event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, ARRAY_ENTITY.get(), (entity, context) -> entity);
            event.registerBlockEntity(hellfirepvp.astralsorcery.common.lumen.ILumenHandler.BLOCK, ARRAY_ENTITY.get(),
                    (entity, side) -> side == null || side == net.minecraft.core.Direction.DOWN ? entity.getTileData().getLumenHandler() : null);
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, ARRAY_ENTITY.get(),
                    (entity, side) -> side == null || side == net.minecraft.core.Direction.DOWN ? entity.getTileData().getFluidTank() : null);
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ARRAY_ENTITY.get(),
                    (entity, side) -> side == null || side.getAxis().isHorizontal() ? entity.getTileData().getInventory() : null);
        });
    }
}
