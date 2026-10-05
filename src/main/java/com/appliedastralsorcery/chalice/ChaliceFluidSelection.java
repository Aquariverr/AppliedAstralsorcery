package com.appliedastralsorcery.chalice;

import javax.annotation.Nonnull;

import com.appliedastralsorcery.AppliedAstralsorcery;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import appeng.api.stacks.AEFluidKey;
import net.minecraft.world.entity.player.Player;

public record ChaliceFluidSelection(int containerId, FluidStack fluid) implements CustomPacketPayload {
    public static final Type<ChaliceFluidSelection> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            AppliedAstralsorcery.MOD_ID, "chalice_fluid_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChaliceFluidSelection> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChaliceFluidSelection::containerId,
            FluidStack.OPTIONAL_STREAM_CODEC, ChaliceFluidSelection::fluid, ChaliceFluidSelection::new);

    @Nonnull
    @Override public Type<ChaliceFluidSelection> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playBidirectional(TYPE, STREAM_CODEC, ChaliceFluidSelection::handle);
    }

    private static void handle(ChaliceFluidSelection packet, IPayloadContext context) {
        packet.apply(context.player());
    }

    @SuppressWarnings("resource")
    public boolean apply(Player player) {
        if (player.containerMenu instanceof MEChaliceMenu menu && menu.containerId == containerId) {
            var key = AEFluidKey.of(fluid);
            if (player.level().isClientSide()) {
                menu.setClientSelection(key);
                return true;
            }
            return menu.setFluidMarker(player, key);
        }
        return false;
    }
}
