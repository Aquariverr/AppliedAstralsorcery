package com.appliedastralsorcery;

import java.util.List;
import com.appliedastralsorcery.tree.METreeBeaconBlock;
import com.appliedastralsorcery.tree.METreeBeaconBlockEntity;
import com.appliedastralsorcery.infuser.MEStarlightInfuserBlock;
import com.appliedastralsorcery.infuser.MEStarlightInfuserBlockEntity;
import com.appliedastralsorcery.crystallizer.MELumenCrystallizerBlock;
import com.appliedastralsorcery.crystallizer.MELumenCrystallizerBlockEntity;
import com.appliedastralsorcery.crystallizer.MELumenCrystallizerItem;
import com.appliedastralsorcery.crystallizer.MELumenCrystallizerPart;
import com.appliedastralsorcery.crystallizer.MELumenCrystalCollectorItem;
import com.appliedastralsorcery.crystallizer.MELumenCrystalCollectorPart;
import com.appliedastralsorcery.attunement.IridescentAttunementBlock;
import com.appliedastralsorcery.attunement.IridescentAttunementBlockEntity;
import com.appliedastralsorcery.attunement.ConstellationRelayBlock;
import com.appliedastralsorcery.attunement.ConstellationRelayBlockEntity;
import com.appliedastralsorcery.gateway.MECelestialGatewayBlock;
import com.appliedastralsorcery.gateway.MECelestialGatewayBlockEntity;
import com.appliedastralsorcery.gateway.MECelestialGatewayMenu;
import com.appliedastralsorcery.gateway.SpatialReturnPortalBlock;
import com.appliedastralsorcery.gateway.SpatialReturnPortalBlockEntity;
import com.appliedastralsorcery.chisel.AutoChiselBlock;
import com.appliedastralsorcery.chisel.AutoChiselBlockEntity;
import com.appliedastralsorcery.chisel.AutoChiselMenu;
import com.appliedastralsorcery.chisel.AutoChiselItem;
import com.appliedastralsorcery.transmutation.StarlightTransmutationBlock;
import com.appliedastralsorcery.transmutation.StarlightTransmutationBlockEntity;
import com.appliedastralsorcery.transmutation.StarlightTransmutationMenu;

import com.appliedastralsorcery.parts.NonEmptyAnnihilationPlaneItem;

import com.appliedastralsorcery.altar.AltarAutomationBlock;
import com.appliedastralsorcery.altar.AltarAutomationBlockEntity;

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
import com.appliedastralsorcery.lumen.MELumenAlchemyArrayBlock;
import com.appliedastralsorcery.lumen.MELumenAlchemyArrayBlockEntity;
import com.appliedastralsorcery.chalice.MEChaliceBlock;
import com.appliedastralsorcery.chalice.MEChaliceBlockEntity;
import com.appliedastralsorcery.chalice.MEChaliceMenu;
import com.appliedastralsorcery.wand.MEResonatingWandItem;
import com.appliedastralsorcery.crystal.AstralFluixCrystalItem;
import com.appliedastralsorcery.crystal.AstralFluixClusterBlock;
import com.appliedastralsorcery.crystal.AstralFluixClusterBlockEntity;
import com.appliedastralsorcery.crystal.FormAstralFluixCluster;
import com.appliedastralsorcery.crystal.CrystalSizeIngredient;
import com.appliedastralsorcery.crystal.LumenCrystalIngredient;
import hellfirepvp.astralsorcery.common.item.block.CelestialCrystalClusterBlockItem;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.recipe.liquid.output.LiquidStarlightRecipeOutputModifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.common.crafting.IngredientType;

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
    private static final DeferredRegister<LiquidStarlightRecipeOutputModifier.Type<?>> LIQUID_OUTPUTS =
            DeferredRegister.create(RegistriesAS.KEY_LIQUID_STARLIGHT_OUTPUT_MODIFIER_TYPES, AppliedAstralsorcery.MOD_ID);
    private static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.INGREDIENT_TYPES, AppliedAstralsorcery.MOD_ID);

    private static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, AppliedAstralsorcery.MOD_ID);
    public static final DeferredBlock<MECelestialGatewayBlock> ME_CELESTIAL_GATEWAY =
            BLOCKS.register("me_celestial_gateway", MECelestialGatewayBlock::new);
    public static final DeferredItem<BlockItem> ME_CELESTIAL_GATEWAY_ITEM = ITEMS.registerSimpleBlockItem(ME_CELESTIAL_GATEWAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MECelestialGatewayBlockEntity>> GATEWAY_ENTITY =
            BLOCK_ENTITIES.register("me_celestial_gateway", () -> buildEntityType(BlockEntityType.Builder.of(
                    MECelestialGatewayBlockEntity::new, ME_CELESTIAL_GATEWAY.get())));
    public static final DeferredHolder<MenuType<?>, MenuType<MECelestialGatewayMenu>> GATEWAY_MENU =
            MENUS.register("me_celestial_gateway", () -> new MenuType<>(MECelestialGatewayMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredBlock<SpatialReturnPortalBlock> SPATIAL_RETURN_PORTAL =
            BLOCKS.register("spatial_return_portal", SpatialReturnPortalBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpatialReturnPortalBlockEntity>> RETURN_PORTAL_ENTITY =
            BLOCK_ENTITIES.register("spatial_return_portal", () -> buildEntityType(BlockEntityType.Builder.of(
                    SpatialReturnPortalBlockEntity::new, SPATIAL_RETURN_PORTAL.get())));
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>,
            net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<hellfirepvp.astralsorcery.common.recipe.RecipeChangeColor>> GATEWAY_DYE_RECIPE =
            RECIPE_SERIALIZERS.register("me_celestial_gateway_change_color", () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(
                    category -> new hellfirepvp.astralsorcery.common.recipe.RecipeChangeColor(category,
                            ModContent.GATEWAY_DYE_RECIPE, ME_CELESTIAL_GATEWAY_ITEM)));

    public static final DeferredHolder<IngredientType<?>, IngredientType<CrystalSizeIngredient>> CRYSTAL_SIZE_INGREDIENT =
            INGREDIENT_TYPES.register("crystal_size", () -> new IngredientType<>(CrystalSizeIngredient.CODEC));
    public static final DeferredBlock<AutoChiselBlock> AUTO_CHISEL = BLOCKS.register("auto_starmetal_chisel", AutoChiselBlock::new);
    public static final DeferredItem<AutoChiselItem> AUTO_CHISEL_ITEM = ITEMS.register("auto_starmetal_chisel",
            () -> new AutoChiselItem(AUTO_CHISEL.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoChiselBlockEntity>> AUTO_CHISEL_ENTITY =
            BLOCK_ENTITIES.register("auto_starmetal_chisel", () -> buildEntityType(BlockEntityType.Builder.of(
                    AutoChiselBlockEntity::new, AUTO_CHISEL.get())));
    public static final DeferredHolder<MenuType<?>, MenuType<AutoChiselMenu>> AUTO_CHISEL_MENU =
            MENUS.register("auto_starmetal_chisel", () -> new MenuType<>(AutoChiselMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredBlock<StarlightTransmutationBlock> STARLIGHT_TRANSMUTATION_CHAMBER =
            BLOCKS.register("starlight_transmutation_chamber", StarlightTransmutationBlock::new);
    public static final DeferredItem<BlockItem> STARLIGHT_TRANSMUTATION_CHAMBER_ITEM =
            ITEMS.registerSimpleBlockItem(STARLIGHT_TRANSMUTATION_CHAMBER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StarlightTransmutationBlockEntity>> TRANSMUTATION_ENTITY =
            BLOCK_ENTITIES.register("starlight_transmutation_chamber", () -> buildEntityType(BlockEntityType.Builder.of(
                    StarlightTransmutationBlockEntity::new, STARLIGHT_TRANSMUTATION_CHAMBER.get())));
    public static final DeferredHolder<MenuType<?>, MenuType<StarlightTransmutationMenu>> TRANSMUTATION_MENU =
            MENUS.register("starlight_transmutation_chamber", () -> new MenuType<>(StarlightTransmutationMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredItem<Item> ASTRAL_PROCESSOR_PRESS = ITEMS.registerSimpleItem("astral_processor_press");
    public static final DeferredItem<Item> PRINTED_ASTRAL_PROCESSOR = ITEMS.registerSimpleItem("printed_astral_processor");
    public static final DeferredItem<Item> ASTRAL_PROCESSOR = ITEMS.registerSimpleItem("astral_processor");
    public static final DeferredItem<Item> CONSTELLATION_CORE = ITEMS.registerSimpleItem("constellation_core");
    public static final DeferredBlock<IridescentAttunementBlock> IRIDESCENT_ATTUNEMENT_ALTAR =
            BLOCKS.register("iridescent_attunement_altar", IridescentAttunementBlock::new);
    public static final DeferredItem<BlockItem> IRIDESCENT_ATTUNEMENT_ALTAR_ITEM = ITEMS.registerSimpleBlockItem(IRIDESCENT_ATTUNEMENT_ALTAR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IridescentAttunementBlockEntity>> IRIDESCENT_ATTUNEMENT_ENTITY =
            BLOCK_ENTITIES.register("iridescent_attunement_altar", () -> buildEntityType(BlockEntityType.Builder.of(
                    IridescentAttunementBlockEntity::new, IRIDESCENT_ATTUNEMENT_ALTAR.get())));
    public static final DeferredBlock<ConstellationRelayBlock> CONSTELLATION_RELAY =
            BLOCKS.register("constellation_relay", ConstellationRelayBlock::new);
    public static final DeferredItem<BlockItem> CONSTELLATION_RELAY_ITEM = ITEMS.registerSimpleBlockItem(CONSTELLATION_RELAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConstellationRelayBlockEntity>> CONSTELLATION_RELAY_ENTITY =
            BLOCK_ENTITIES.register("constellation_relay", () -> buildEntityType(BlockEntityType.Builder.of(
                    ConstellationRelayBlockEntity::new, CONSTELLATION_RELAY.get())));
    public static final DeferredHolder<IngredientType<?>, IngredientType<LumenCrystalIngredient>> LUMEN_CRYSTAL_INGREDIENT =
            INGREDIENT_TYPES.register("lumen_crystal", () -> new IngredientType<>(LumenCrystalIngredient.CODEC));
    public static final DeferredItem<Item> LUMEN_PROCESSOR_PRESS = ITEMS.registerSimpleItem("lumen_processor_press");
    public static final DeferredItem<Item> PRINTED_LUMEN_PROCESSOR = ITEMS.registerSimpleItem("printed_lumen_processor");
    public static final DeferredItem<Item> LUMEN_PROCESSOR = ITEMS.registerSimpleItem("lumen_processor");
    public static final DeferredBlock<AltarAutomationBlock> ALTAR_AUTOMATION =
            BLOCKS.register("altar_automation_interface", AltarAutomationBlock::new);
    public static final DeferredItem<BlockItem> ALTAR_AUTOMATION_ITEM = ITEMS.registerSimpleBlockItem(ALTAR_AUTOMATION);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AltarAutomationBlockEntity>> ALTAR_AUTOMATION_ENTITY =
            BLOCK_ENTITIES.register("altar_automation_interface", () -> buildEntityType(BlockEntityType.Builder.of(
                    AltarAutomationBlockEntity::new, ALTAR_AUTOMATION.get())));
    public static final DeferredBlock<Block> STARLIGHT_MYSTERIOUS_CUBE = BLOCKS.register("starlight_mysterious_cube",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(10, 1000).sound(SoundType.METAL).requiresCorrectToolForDrops()
                    .lightLevel(state -> 7).isRedstoneConductor((state, level, pos) -> false)));
    public static final DeferredItem<BlockItem> STARLIGHT_MYSTERIOUS_CUBE_ITEM =
            ITEMS.registerSimpleBlockItem(STARLIGHT_MYSTERIOUS_CUBE);

    public static final DeferredItem<AstralFluixCrystalItem> ASTRAL_FLUIX_CRYSTAL =
            ITEMS.register("astral_fluix_crystal", AstralFluixCrystalItem::new);
    public static final DeferredBlock<Block> ASTRAL_FLUIX_BLOCK = BLOCKS.register("astral_fluix_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE)
                    .strength(4, 6).sound(SoundType.AMETHYST).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> ASTRAL_FLUIX_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(ASTRAL_FLUIX_BLOCK);
    public static final DeferredBlock<Block> LUMEN_CRYSTAL_BLOCK = BLOCKS.register("lumen_crystal_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
                    .strength(4, 6).sound(SoundType.AMETHYST).requiresCorrectToolForDrops().lightLevel(state -> 7)));
    public static final DeferredItem<BlockItem> LUMEN_CRYSTAL_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(LUMEN_CRYSTAL_BLOCK);
    public static final DeferredBlock<AstralFluixClusterBlock> ASTRAL_FLUIX_CLUSTER =
            BLOCKS.register("astral_fluix_cluster", AstralFluixClusterBlock::new);
    public static final DeferredItem<CelestialCrystalClusterBlockItem> ASTRAL_FLUIX_CLUSTER_ITEM =
            ITEMS.register("astral_fluix_cluster", () -> new CelestialCrystalClusterBlockItem(ASTRAL_FLUIX_CLUSTER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AstralFluixClusterBlockEntity>> ASTRAL_FLUIX_CLUSTER_ENTITY =
            BLOCK_ENTITIES.register("astral_fluix_cluster", () -> buildEntityType(BlockEntityType.Builder.of(
                    AstralFluixClusterBlockEntity::new, ASTRAL_FLUIX_CLUSTER.get())));
    public static final DeferredHolder<LiquidStarlightRecipeOutputModifier.Type<?>, LiquidStarlightRecipeOutputModifier.Type<FormAstralFluixCluster>> FORM_ASTRAL_FLUIX_CLUSTER =
            LIQUID_OUTPUTS.register("form_astral_fluix_cluster", () -> FormAstralFluixCluster.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<MELumenFilamentMenu>> FILAMENT_MENU =
            MENUS.register("me_lumen_filament", () -> new MenuType<>(MELumenFilamentMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<MELumenArrayMenu>> ARRAY_MENU =
            MENUS.register("me_lumen_array", () -> new MenuType<>(MELumenArrayMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<MELumenArrayMenu>> ALCHEMY_ARRAY_MENU =
            MENUS.register("me_lumen_alchemy_array", () -> new MenuType<>(
                    (id, inventory) -> new MELumenArrayMenu(id, inventory, null, true), FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<MEChaliceMenu>> CHALICE_MENU =
            MENUS.register("me_chalice", () -> new MenuType<>(MEChaliceMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<com.appliedastralsorcery.wand.MEResonatingWandMenu>> WAND_MENU =
            MENUS.register("me_resonating_wand", () -> new MenuType<>(
                    com.appliedastralsorcery.wand.MEResonatingWandMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredBlock<MEChaliceBlock> ME_CHALICE = BLOCKS.register("me_chalice", MEChaliceBlock::new);
    public static final DeferredBlock<METreeBeaconBlock> ME_TREE_BEACON =
            BLOCKS.register("me_tree_beacon", METreeBeaconBlock::new);
    public static final DeferredItem<BlockItem> ME_TREE_BEACON_ITEM = ITEMS.registerSimpleBlockItem(ME_TREE_BEACON);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<METreeBeaconBlockEntity>> TREE_BEACON_ENTITY =
            BLOCK_ENTITIES.register("me_tree_beacon", () -> buildEntityType(BlockEntityType.Builder.of(
                    METreeBeaconBlockEntity::new, ME_TREE_BEACON.get())));
    public static final DeferredBlock<MEStarlightInfuserBlock> ME_STARLIGHT_INFUSER =
            BLOCKS.register("me_starlight_infuser", MEStarlightInfuserBlock::new);
    public static final DeferredItem<BlockItem> ME_STARLIGHT_INFUSER_ITEM = ITEMS.registerSimpleBlockItem(ME_STARLIGHT_INFUSER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MEStarlightInfuserBlockEntity>> STARLIGHT_INFUSER_ENTITY =
            BLOCK_ENTITIES.register("me_starlight_infuser", () -> buildEntityType(BlockEntityType.Builder.of(
                    MEStarlightInfuserBlockEntity::new, ME_STARLIGHT_INFUSER.get())));
    public static final DeferredBlock<MELumenCrystallizerBlock> ME_LUMEN_CRYSTALLIZER =
            BLOCKS.register("me_lumen_crystallizer", MELumenCrystallizerBlock::new);
    public static final DeferredItem<MELumenCrystallizerItem> ME_LUMEN_CRYSTALLIZER_ITEM =
            ITEMS.register("me_lumen_crystallizer", MELumenCrystallizerItem::new);
    public static final DeferredItem<MELumenCrystalCollectorItem> ME_LUMEN_CRYSTAL_COLLECTOR_ITEM =
            ITEMS.register("me_lumen_crystal_collector", MELumenCrystalCollectorItem::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MELumenCrystallizerBlockEntity>> CRYSTALLIZER_ENTITY =
            BLOCK_ENTITIES.register("me_lumen_crystallizer", ModContent::createCrystallizerType);
    public static final DeferredItem<BlockItem> ME_CHALICE_ITEM = ITEMS.registerSimpleBlockItem(ME_CHALICE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MEChaliceBlockEntity>> CHALICE_ENTITY =
            BLOCK_ENTITIES.register("me_chalice", () -> buildEntityType(BlockEntityType.Builder.of(
                    MEChaliceBlockEntity::new, ME_CHALICE.get())));
    public static final DeferredBlock<MELumenArrayBlock> ME_LUMEN_ARRAY = BLOCKS.register("me_lumen_array", MELumenArrayBlock::new);
    public static final DeferredItem<BlockItem> ME_LUMEN_ARRAY_ITEM = ITEMS.registerSimpleBlockItem(ME_LUMEN_ARRAY);
    public static final DeferredBlock<MELumenAlchemyArrayBlock> ME_LUMEN_ALCHEMY_ARRAY =
            BLOCKS.register("me_lumen_alchemy_array", MELumenAlchemyArrayBlock::new);
    public static final DeferredItem<BlockItem> ME_LUMEN_ALCHEMY_ARRAY_ITEM = ITEMS.registerSimpleBlockItem(ME_LUMEN_ALCHEMY_ARRAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MELumenAlchemyArrayBlockEntity>> ALCHEMY_ARRAY_ENTITY =
            BLOCK_ENTITIES.register("me_lumen_alchemy_array", () -> buildEntityType(BlockEntityType.Builder.of(
                    MELumenAlchemyArrayBlockEntity::new, ME_LUMEN_ALCHEMY_ARRAY.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MELumenArrayBlockEntity>> ARRAY_ENTITY =
            BLOCK_ENTITIES.register("me_lumen_array", () -> buildEntityType(BlockEntityType.Builder.of(
                    MELumenArrayBlockEntity::new, ME_LUMEN_ARRAY.get())));

    public static final DeferredItem<Item> LUMEN_CELL_HOUSING = ITEMS.registerSimpleItem("lumen_cell_housing");
    public static final DeferredItem<NonEmptyAnnihilationPlaneItem> NON_EMPTY_ANNIHILATION_PLANE =
            ITEMS.register("non_empty_annihilation_plane", NonEmptyAnnihilationPlaneItem::new);
    public static final DeferredItem<MEResonatingWandItem> ME_RESONATING_WAND =
            ITEMS.register("me_resonating_wand", MEResonatingWandItem::new);
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
        var type = buildEntityType(BlockEntityType.Builder.of(ModContent::newFilament, ME_LUMEN_FILAMENT.get()));
        AEBaseBlockEntity.registerBlockEntityItem(type, ME_LUMEN_FILAMENT_ITEM.get());
        ME_LUMEN_FILAMENT.get().setBlockEntity(MELumenFilamentBlockEntity.class, type, null,
                (level, pos, state, entity) -> entity.serverTick());
        return type;
    }

    private static BlockEntityType<MELumenCrystallizerBlockEntity> createCrystallizerType() {
        var type = buildEntityType(BlockEntityType.Builder.of(
                (pos, state) -> new MELumenCrystallizerBlockEntity(CRYSTALLIZER_ENTITY.get(), pos, state),
                ME_LUMEN_CRYSTALLIZER.get()));
        AEBaseBlockEntity.registerBlockEntityItem(type, ME_LUMEN_CRYSTALLIZER_ITEM.get());
        // Old worlds retain the standalone block; mining or picking it returns the native ME part.
        Item.BY_BLOCK.put(ME_LUMEN_CRYSTALLIZER.get(), ME_LUMEN_CRYSTALLIZER_ITEM.get());
        ME_LUMEN_CRYSTALLIZER.get().setBlockEntity(MELumenCrystallizerBlockEntity.class, type, null,
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
                .icon(LUMEN_CELL::toStack)
                .displayItems((parameters, output) -> {
                    output.accept(LUMEN_CELL_HOUSING);
                    LUMEN_COMPONENTS.forEach(output::accept);
                    LUMEN_CELLS.forEach(output::accept);
                    output.accept(ME_LUMEN_FILAMENT_ITEM);
                    output.accept(ME_LUMEN_ARRAY_ITEM);
                    output.accept(ME_LUMEN_ALCHEMY_ARRAY_ITEM);
                    output.accept(ME_CHALICE_ITEM);
                    output.accept(ME_TREE_BEACON_ITEM);
                    output.accept(ME_STARLIGHT_INFUSER_ITEM);
                    output.accept(ME_LUMEN_CRYSTALLIZER_ITEM);
                    output.accept(ME_LUMEN_CRYSTAL_COLLECTOR_ITEM);
                    output.accept(ALTAR_AUTOMATION_ITEM);
                    output.accept(AUTO_CHISEL_ITEM);
                    output.accept(STARLIGHT_TRANSMUTATION_CHAMBER_ITEM);
                    output.accept(ME_RESONATING_WAND);
                    output.accept(ME_CELESTIAL_GATEWAY_ITEM);
                    output.accept(NON_EMPTY_ANNIHILATION_PLANE);
                    output.accept(ASTRAL_FLUIX_CRYSTAL);
                    output.accept(ASTRAL_FLUIX_BLOCK_ITEM);
                    output.accept(LUMEN_CRYSTAL_BLOCK_ITEM);
                    output.accept(ASTRAL_FLUIX_CLUSTER_ITEM);
                    output.accept(STARLIGHT_MYSTERIOUS_CUBE_ITEM);
                    output.accept(ASTRAL_PROCESSOR_PRESS);
                    output.accept(PRINTED_ASTRAL_PROCESSOR);
                    output.accept(ASTRAL_PROCESSOR);
                    output.accept(CONSTELLATION_CORE);
                    output.accept(IRIDESCENT_ATTUNEMENT_ALTAR_ITEM);
                    output.accept(CONSTELLATION_RELAY_ITEM);
                    output.accept(LUMEN_PROCESSOR_PRESS);
                    output.accept(PRINTED_LUMEN_PROCESSOR);
                    output.accept(LUMEN_PROCESSOR);
                }).build());
    }

    /** Mod block entities have no vanilla DFU schema; NeoForge supports a null data type. */
    @SuppressWarnings("DataFlowIssue")
    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> BlockEntityType<T> buildEntityType(
            BlockEntityType.Builder<T> builder) {
        return builder.build(null);
    }

    private ModContent() {}

    public static void register(IEventBus bus) {
        com.appliedastralsorcery.attunement.AttunementLayout.register(bus);
        com.appliedastralsorcery.parts.NonEmptyAnnihilationPlanePart.registerModels();
        MELumenCrystallizerPart.registerModels();
        MELumenCrystalCollectorPart.registerModels();
        LumenCellEnhancement.register(bus);
        MEResonatingWandItem.registerComponents(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        KEY_TYPES.register(bus);
        TABS.register(bus);
        MENUS.register(bus);
        LIQUID_OUTPUTS.register(bus);
        INGREDIENT_TYPES.register(bus);
        RECIPE_SERIALIZERS.register(bus);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, FILAMENT_ENTITY.get(),
                        (entity, context) -> entity));
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, TREE_BEACON_ENTITY.get(), (entity, side) -> entity);
            event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, STARLIGHT_INFUSER_ENTITY.get(), (entity, side) -> entity);
            event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, CRYSTALLIZER_ENTITY.get(), (entity, side) -> entity);
            event.registerBlockEntity(hellfirepvp.astralsorcery.common.lumen.ILumenHandler.BLOCK,
                    TREE_BEACON_ENTITY.get(), (entity, side) -> entity.getTileData().getLumenHandler());
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    STARLIGHT_INFUSER_ENTITY.get(), (entity, side) -> entity.getInventory());
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    CONSTELLATION_RELAY_ENTITY.get(), (entity, side) -> entity.getInventory());
            event.registerBlockEntity(hellfirepvp.astralsorcery.common.lumen.ILumenHandler.BLOCK,
                    IRIDESCENT_ATTUNEMENT_ENTITY.get(), (entity, side) -> entity.getLumenHandler());
            event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, TRANSMUTATION_ENTITY.get(), (entity, side) -> entity);
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    TRANSMUTATION_ENTITY.get(), (entity, side) -> entity.getInventory());
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    AUTO_CHISEL_ENTITY.get(), AutoChiselBlockEntity::getItemHandler);
            event.registerBlockEntity(hellfirepvp.astralsorcery.common.lumen.ILumenHandler.BLOCK,
                    AUTO_CHISEL_ENTITY.get(), (entity, side) -> entity.getLumenHandler());
            event.registerBlockEntity(AECapabilities.CRAFTING_MACHINE, ALTAR_AUTOMATION_ENTITY.get(), (entity, side) -> entity);
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    ALTAR_AUTOMATION_ENTITY.get(), (entity, side) -> entity.getOutput());
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    ALTAR_AUTOMATION_ENTITY.get(), (entity, side) -> entity.getFluidHandler());
            event.registerBlockEntity(hellfirepvp.astralsorcery.common.lumen.ILumenHandler.BLOCK,
                    ALTAR_AUTOMATION_ENTITY.get(), (entity, side) -> entity.getLumenHandler());
            event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, CHALICE_ENTITY.get(), (entity, context) -> entity);
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, CHALICE_ENTITY.get(),
                    (entity, side) -> side == null || side == net.minecraft.core.Direction.DOWN ? entity.getTankView() : null);
            event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, ARRAY_ENTITY.get(), (entity, context) -> entity);
            event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, ALCHEMY_ARRAY_ENTITY.get(), (entity, context) -> entity);
            event.registerBlockEntity(hellfirepvp.astralsorcery.common.lumen.ILumenHandler.BLOCK, ALCHEMY_ARRAY_ENTITY.get(),
                    (entity, side) -> side == null || side == net.minecraft.core.Direction.DOWN ? entity.getTileData().getLumenHandler() : null);
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, ALCHEMY_ARRAY_ENTITY.get(),
                    (entity, side) -> side == null || side == net.minecraft.core.Direction.DOWN ? entity.getTileData().getFluidTank() : null);
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ALCHEMY_ARRAY_ENTITY.get(),
                    (entity, side) -> side == null || side.getAxis().isHorizontal() ? entity.getTileData().getInventory() : null);
            event.registerBlockEntity(hellfirepvp.astralsorcery.common.lumen.ILumenHandler.BLOCK, ARRAY_ENTITY.get(),
                    (entity, side) -> side == null || side == net.minecraft.core.Direction.DOWN ? entity.getTileData().getLumenHandler() : null);
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, ARRAY_ENTITY.get(),
                    (entity, side) -> side == null || side == net.minecraft.core.Direction.DOWN ? entity.getTileData().getFluidTank() : null);
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ARRAY_ENTITY.get(),
                    (entity, side) -> side == null || side.getAxis().isHorizontal() ? entity.getTileData().getInventory() : null);
        });
    }
}
