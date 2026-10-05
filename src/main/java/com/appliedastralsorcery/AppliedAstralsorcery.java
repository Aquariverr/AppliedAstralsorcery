package com.appliedastralsorcery;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import com.appliedastralsorcery.lumen.LumenIntegration;

@Mod(AppliedAstralsorcery.MOD_ID)
public final class AppliedAstralsorcery {
    public static final String MOD_ID = "appliedas";

    public AppliedAstralsorcery(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, ModConfig.SPEC);
        ModContent.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(com.appliedastralsorcery.chalice.ChaliceFluidSelection::register);
        modEventBus.addListener(com.appliedastralsorcery.transmutation.TransmutationFilterSelection::register);
        modEventBus.addListener(com.appliedastralsorcery.transmutation.TransmutationMarkerAmount::register);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(LumenIntegration::register);
        event.enqueueWork(() -> appeng.api.features.P2PTunnelAttunement.registerAttunementTag(ModContent.STARLIGHT_P2P_TUNNEL));
        event.enqueueWork(com.appliedastralsorcery.wand.MEResonatingWandItem::registerLinking);
    }
}
