package com.appliedastralsorcery.transmutation;

import com.appliedastralsorcery.AppliedAstralsorcery;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Changes only the quantity of an existing configuration, never the carried or stored items. */
public record TransmutationMarkerAmount(int containerId, int slot, ItemStack expectedItem, int amount,
        boolean relative) implements CustomPacketPayload {
    public static final Type<TransmutationMarkerAmount> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            AppliedAstralsorcery.MOD_ID, "transmutation_marker_amount"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TransmutationMarkerAmount> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TransmutationMarkerAmount::containerId,
            ByteBufCodecs.VAR_INT, TransmutationMarkerAmount::slot,
            ItemStack.OPTIONAL_STREAM_CODEC, TransmutationMarkerAmount::expectedItem,
            ByteBufCodecs.VAR_INT, TransmutationMarkerAmount::amount,
            ByteBufCodecs.BOOL, TransmutationMarkerAmount::relative, TransmutationMarkerAmount::new);

    @Override public Type<TransmutationMarkerAmount> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (packet, context) -> packet.apply(context.player()));
    }
    public boolean apply(Player player) {
        return player.containerMenu instanceof StarlightTransmutationMenu menu && menu.containerId == containerId
                && menu.setMarkerAmount(player, slot, expectedItem, amount, relative);
    }
}
