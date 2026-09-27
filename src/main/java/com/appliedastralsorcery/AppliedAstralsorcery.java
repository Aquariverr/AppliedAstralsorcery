package com.appliedastralsorcery;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import com.appliedastralsorcery.lumen.LumenIntegration;

@Mod(AppliedAstralsorcery.MOD_ID)
public final class AppliedAstralsorcery {
    public static final String MOD_ID = "appliedas";

    public AppliedAstralsorcery(IEventBus modEventBus) {
        ModContent.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(com.appliedastralsorcery.chalice.ChaliceFluidSelection::register);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(LumenIntegration::register);
    }
}
