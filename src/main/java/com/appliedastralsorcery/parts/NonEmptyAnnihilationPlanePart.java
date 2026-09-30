package com.appliedastralsorcery.parts;

import java.util.List;

import appeng.api.behaviors.PickupStrategy;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.automation.AnnihilationPlanePart;
import appeng.parts.automation.ItemPickupStrategy;
import appeng.parts.automation.PlaneModels;
import com.appliedastralsorcery.AppliedAstralsorcery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** An AE2 annihilation plane that leaves blocks with empty loot in place. */
public class NonEmptyAnnihilationPlanePart extends AnnihilationPlanePart {
    private static final ResourceLocation ACCENTS = ResourceLocation.fromNamespaceAndPath(
            AppliedAstralsorcery.MOD_ID, "part/non_empty_annihilation_plane_accents");
    private static final IPartModel MODEL_OFF = new PartModel(PlaneModels.MODEL_CHASSIS_OFF,
            ResourceLocation.parse("ae2:part/annihilation_plane"), ACCENTS);
    private static final IPartModel MODEL_ON = new PartModel(PlaneModels.MODEL_CHASSIS_ON,
            ResourceLocation.parse("ae2:part/annihilation_plane"), ACCENTS);
    private static final IPartModel MODEL_ACTIVE = new PartModel(PlaneModels.MODEL_CHASSIS_HAS_CHANNEL,
            ResourceLocation.parse("ae2:part/annihilation_plane_on"), ACCENTS);

    public static void registerModels() {
        getModels().forEach(model -> PartModels.registerModels(model.getModels()));
    }

    @appeng.items.parts.PartModels
    public static List<IPartModel> getModels() {
        return List.of(MODEL_OFF, MODEL_ON, MODEL_ACTIVE);
    }

    @Override
    public IPartModel getStaticModels() {
        // Inlays face the back; AE2's black front, animation and chassis status indicators stay intact.
        return isPowered() ? (isActive() ? MODEL_ACTIVE : MODEL_ON) : MODEL_OFF;
    }

    public NonEmptyAnnihilationPlanePart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    protected List<PickupStrategy> getPickupStrategies() {
        boolean initialize = pickupStrategies == null;
        var strategies = super.getPickupStrategies();
        if (initialize && pickupStrategies != null) {
            var host = getBlockEntity();
            var node = getMainNode().getNode();
            // Keep fluid and addon strategies, and all of AE2's normal item pickup behavior.
            pickupStrategies = strategies.stream().map(strategy ->
                    strategy.getClass() == ItemPickupStrategy.class
                            ? (PickupStrategy) new NonEmptyItemPickupStrategy((ServerLevel) host.getLevel(),
                                    host.getBlockPos().relative(getSide()), getSide().getOpposite(), host,
                                    getEnchantments(), node.getOwningPlayerProfileId())
                            : strategy).toList();
            return pickupStrategies;
        }
        return strategies;
    }
}
