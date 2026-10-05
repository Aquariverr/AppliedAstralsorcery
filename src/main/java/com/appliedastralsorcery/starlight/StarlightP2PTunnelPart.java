package com.appliedastralsorcery.starlight;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartModels;
import appeng.parts.p2p.P2PModels;
import appeng.parts.p2p.P2PTunnelPart;
import com.appliedastralsorcery.AppliedAstralsorcery;
import hellfirepvp.astralsorcery.common.lib.DataAS;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import hellfirepvp.astralsorcery.common.util.level.DayTimeHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

public final class StarlightP2PTunnelPart extends P2PTunnelPart<StarlightP2PTunnelPart> implements IGridTickable {
    private static final P2PModels MODELS = new P2PModels(ResourceLocation.fromNamespaceAndPath(
            AppliedAstralsorcery.MOD_ID, "part/p2p_tunnel_starlight"));
    // Forward synchronously: there is no saved energy or tick buffer that a feedback loop can refill.
    private static final ThreadLocal<Set<StarlightP2PTunnelPart>> TRANSMISSION_PATH = new ThreadLocal<>();
    private static final int MAX_HOPS = 64;

    public StarlightP2PTunnelPart(IPartItem<?> item) {
        super(item);
        getMainNode().addService(IGridTickable.class, this);
    }

    public static void registerModels() {
        getModels().forEach(model -> PartModels.registerModels(model.getModels()));
    }

    @appeng.items.parts.PartModels
    public static List<IPartModel> getModels() { return MODELS.getModels(); }

    @Override public IPartModel getStaticModels() { return MODELS.getModel(isPowered(), isActive()); }

    public boolean canReceiveStarlight() {
        return !isOutput() && getFrequency() != 0 && getMainNode().isOnline() && getInput() == this;
    }

    void receiveStarlight(StarlightTransmissionPacket packet, StarlightP2PTransport.Kind kind) {
        if (!canReceiveStarlight() || !StarlightP2PTransport.isValid(packet)) return;
        var path = TRANSMISSION_PATH.get();
        boolean root = path == null;
        if (root) {
            path = new HashSet<>();
            TRANSMISSION_PATH.set(path);
        }
        if (path.size() >= MAX_HOPS || !path.add(this)) return;
        try {
            var outputs = getOutputStream().filter(output -> output.isOutput()
                    && output.getMainNode().isOnline() && output.getInput() == this
                    && output.getLevel() instanceof ServerLevel).toList();
            if (outputs.isEmpty()) return;
            var share = packet.withMultiplier(1.0F / outputs.size());
            for (var output : outputs) {
                StarlightP2PTransport.emit((ServerLevel) output.getLevel(),
                        output.getBlockEntity().getBlockPos(), output.getSide(), share, kind);
            }
        } finally {
            path.remove(this);
            if (root) TRANSMISSION_PATH.remove();
        }
    }

    @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(1, 1, false); }

    @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        collectFocalStarlight();
        return TickRateModulation.SAME;
    }

    public void collectFocalStarlight() {
        if (!canReceiveStarlight() || !(getLevel() instanceof ServerLevel level) || !DayTimeHelper.isNight(level)) return;
        var pos = getBlockEntity().getBlockPos();
        if (level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) > pos.getY() + 1) return;
        DataAS.DOMAIN_AS.getData(level, DataAS.KEY_FOCAL_POINT_DATA).getNode(pos).ifPresent(focal -> {
            // The native focal-point transmutation strength is 1 per tick. Multiple ports on the
            // same cable share it just as they share a beam aimed at that cable block.
            int inputs = StarlightP2PTransport.inputsAt(level, pos).size();
            if (inputs > 0) receiveStarlight(new StarlightTransmissionPacket(focal.getConstellation(), 1.0F / inputs),
                    StarlightP2PTransport.Kind.FOCAL);
        });
    }
}
