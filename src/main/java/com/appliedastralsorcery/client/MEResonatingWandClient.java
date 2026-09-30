package com.appliedastralsorcery.client;

import appeng.api.ids.AEComponents;
import com.appliedastralsorcery.AppliedAstralsorcery;
import com.appliedastralsorcery.ModContent;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Lights the ME Resonating Wand's orb once the wand is linked to a wireless access point. */
@EventBusSubscriber(modid = AppliedAstralsorcery.MOD_ID, value = Dist.CLIENT)
public final class MEResonatingWandClient {
    /** Item model property: 1 while the wand carries a wireless link target, otherwise 0. */
    public static final ResourceLocation LINKED =
            ResourceLocation.fromNamespaceAndPath(AppliedAstralsorcery.MOD_ID, "linked");

    private MEResonatingWandClient() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModContent.ME_RESONATING_WAND.get(), LINKED,
                (stack, level, entity, seed) -> stack.has(AEComponents.WIRELESS_LINK_TARGET) ? 1.0F : 0.0F));
    }

    /** A wand to draw where only the link state is known, such as the settings screen header. */
    public static ItemStack icon(boolean linked) {
        var icon = ModContent.ME_RESONATING_WAND.toStack();
        // Any target lights the orb; this copy is only drawn, never handed to a player.
        if (linked) icon.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO));
        return icon;
    }
}
