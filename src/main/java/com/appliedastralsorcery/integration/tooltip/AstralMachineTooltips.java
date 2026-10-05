package com.appliedastralsorcery.integration.tooltip;

import appeng.api.integrations.igtooltip.ClientRegistration;
import com.appliedastralsorcery.tree.METreeBeaconBlock;
import com.appliedastralsorcery.tree.METreeBeaconBlockEntity;
import com.appliedastralsorcery.infuser.MEStarlightInfuserBlock;
import com.appliedastralsorcery.infuser.MEStarlightInfuserBlockEntity;
import appeng.api.integrations.igtooltip.CommonRegistration;
import appeng.api.integrations.igtooltip.TooltipProvider;
import appeng.integration.modules.igtooltip.TooltipIds;
import appeng.integration.modules.igtooltip.blocks.GridNodeStateDataProvider;
import com.appliedastralsorcery.chalice.MEChaliceBlock;
import com.appliedastralsorcery.chalice.MEChaliceBlockEntity;
import com.appliedastralsorcery.lumen.MELumenArrayBlock;
import com.appliedastralsorcery.lumen.MELumenArrayBlockEntity;
import com.appliedastralsorcery.transmutation.StarlightTransmutationBlock;
import com.appliedastralsorcery.transmutation.StarlightTransmutationBlockEntity;
import net.minecraft.resources.ResourceLocation;

/** Use AE2's native server data, device status text, colors and Jade settings. */
@SuppressWarnings("UnstableApiUsage") // AE2's tooltip integration API is explicitly experimental.
public final class AstralMachineTooltips implements TooltipProvider {
    private static final GridNodeStateDataProvider GRID_STATE = new GridNodeStateDataProvider();

    @Override public void registerCommon(CommonRegistration registration) {
        // Jade indexes server providers by UID. AE2's base-class shortcut reuses ae2:grid_node
        // for every class, so its class-specific adapters overwrite each other during sync.
        registration.addBlockEntityData(dataId("transmutation"), StarlightTransmutationBlockEntity.class, GRID_STATE);
        registration.addBlockEntityData(dataId("lumen_array"), MELumenArrayBlockEntity.class, GRID_STATE);
        registration.addBlockEntityData(dataId("chalice"), MEChaliceBlockEntity.class, GRID_STATE);
        registration.addBlockEntityData(dataId("tree_beacon"), METreeBeaconBlockEntity.class, GRID_STATE);
        registration.addBlockEntityData(dataId("starlight_infuser"), MEStarlightInfuserBlockEntity.class, GRID_STATE);
    }

    @Override public void registerClient(ClientRegistration registration) {
        // The client ID is a shared display setting, not a server-data identity.
        registration.addBlockEntityBody(StarlightTransmutationBlockEntity.class, StarlightTransmutationBlock.class,
                TooltipIds.GRID_NODE_STATE, GRID_STATE);
        registration.addBlockEntityBody(MELumenArrayBlockEntity.class, MELumenArrayBlock.class,
                TooltipIds.GRID_NODE_STATE, GRID_STATE);
        registration.addBlockEntityBody(MEChaliceBlockEntity.class, MEChaliceBlock.class,
                TooltipIds.GRID_NODE_STATE, GRID_STATE);
        registration.addBlockEntityBody(METreeBeaconBlockEntity.class, METreeBeaconBlock.class,
                TooltipIds.GRID_NODE_STATE, GRID_STATE);
        registration.addBlockEntityBody(MEStarlightInfuserBlockEntity.class, MEStarlightInfuserBlock.class,
                TooltipIds.GRID_NODE_STATE, GRID_STATE);
    }

    private static ResourceLocation dataId(String machine) {
        return ResourceLocation.fromNamespaceAndPath("appliedas", machine + "_grid_node");
    }
}
