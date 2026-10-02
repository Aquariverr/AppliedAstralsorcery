package com.appliedastralsorcery.transmutation;

import javax.annotation.Nonnull;

import com.appliedastralsorcery.AppliedAstralsorcery;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A JEI configuration request. The stack is a marker, never an inventory insertion. */
public record TransmutationFilterSelection(int containerId, int slot, ItemStack stack) implements CustomPacketPayload {
    public static final Type<TransmutationFilterSelection> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            AppliedAstralsorcery.MOD_ID, "transmutation_filter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TransmutationFilterSelection> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TransmutationFilterSelection::containerId,
            ByteBufCodecs.VAR_INT, TransmutationFilterSelection::slot,
            ItemStack.OPTIONAL_STREAM_CODEC, TransmutationFilterSelection::stack, TransmutationFilterSelection::new);

    @Nonnull
    @Override public Type<TransmutationFilterSelection> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (packet, context) -> packet.apply(context.player()));
    }
    public boolean apply(Player player) {
        return player.containerMenu instanceof StarlightTransmutationMenu menu && menu.containerId == containerId
                && menu.setPullMarker(player, slot, stack);
    }
}
