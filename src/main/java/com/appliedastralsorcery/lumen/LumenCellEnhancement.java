package com.appliedastralsorcery.lumen;

import java.util.stream.Stream;

import com.appliedastralsorcery.AppliedAstralsorcery;
import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarCraftingInput;
import hellfirepvp.astralsorcery.common.recipe.altar.output.AltarRecipeOutputModifier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class LumenCellEnhancement {
    public static final int MAX_LEVEL = 10;
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AppliedAstralsorcery.MOD_ID);
    private static final DeferredRegister<IngredientType<?>> INGREDIENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, AppliedAstralsorcery.MOD_ID);
    private static final DeferredRegister<AltarRecipeOutputModifier.Type<?>> OUTPUTS =
            DeferredRegister.create(RegistriesAS.KEY_ALTAR_OUTPUT_MODIFIER_TYPES, AppliedAstralsorcery.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LEVEL =
            COMPONENTS.registerComponentType("lumen_cell_enhancement", builder -> builder
                    .persistent(Codec.intRange(0, MAX_LEVEL)).networkSynchronized(ByteBufCodecs.VAR_INT));
    private static final DeferredHolder<IngredientType<?>, IngredientType<UpgradeableCell>> INGREDIENT =
            INGREDIENTS.register("upgradeable_lumen_cell", () -> new IngredientType<>(UpgradeableCell.CODEC));

    static {
        OUTPUTS.register("enhance_lumen_cell", () -> EnhanceOutput.TYPE);
    }

    private LumenCellEnhancement() {}

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
        INGREDIENTS.register(bus);
        OUTPUTS.register(bus);
    }

    public static final class UpgradeableCell implements ICustomIngredient {
        public static final UpgradeableCell INSTANCE = new UpgradeableCell();
        public static final MapCodec<UpgradeableCell> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public boolean test(ItemStack stack) {
            return stack.is(ModContent.LUMEN_CELL_256K)
                    && ArtifactLumenStorageCell.getEnhancementLevel(stack) < MAX_LEVEL;
        }

        @Override
        public Stream<ItemStack> getItems() {
            return Stream.of(ModContent.LUMEN_CELL_256K.toStack());
        }

        @Override
        public boolean isSimple() {
            return false;
        }

        @Override
        public IngredientType<?> getType() {
            return INGREDIENT.get();
        }
    }

    public static final class EnhanceOutput extends AltarRecipeOutputModifier {
        public static final EnhanceOutput INSTANCE = new EnhanceOutput();
        public static final Type<EnhanceOutput> TYPE = new Type<>(MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

        @Override
        public ItemStack modifyOutput(ItemStack output, AltarCraftingInput input, HolderLookup.Provider registries) {
            if (UpgradeableCell.INSTANCE.test(output)) {
                output.set(LEVEL, ArtifactLumenStorageCell.getEnhancementLevel(output) + 1);
            }
            return output;
        }

        @Override
        public Type<?> getType() {
            return TYPE;
        }
    }
}
