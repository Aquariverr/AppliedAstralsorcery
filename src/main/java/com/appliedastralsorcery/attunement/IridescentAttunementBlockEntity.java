package com.appliedastralsorcery.attunement;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import java.util.List;
import java.util.Optional;
import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.Codec;
import hellfirepvp.astralsorcery.common.component.StoredLumenComponent;
import hellfirepvp.astralsorcery.common.component.StoredLumenComponent.StoredLumen;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.lumen.LumenStack;
import hellfirepvp.astralsorcery.common.lumen.capability.LumenHandlerViewFactory;
import hellfirepvp.astralsorcery.common.lumen.capability.LumenStackList;
import hellfirepvp.astralsorcery.common.lumen.transfer.LumenRequestHelper;
import hellfirepvp.astralsorcery.common.util.LumenUtil;
import hellfirepvp.astralsorcery.common.tile.base.TileEntityTick;
import hellfirepvp.astralsorcery.common.tile.base.TileEntityLumenDisplay;
import hellfirepvp.astralsorcery.common.util.data.ObserverRegistryObject;
import hellfirepvp.astralsorcery.common.util.data.TileRegistryObject;
import hellfirepvp.astralsorcery.common.util.tooltip.StoredLumenDisplayTooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class IridescentAttunementBlockEntity extends TileEntityTick<TileEntityTick.Data> implements TileEntityLumenDisplay {
    public static final int LUMEN_CAPACITY = 16000;

    /** Client implementation is installed at client setup; dedicated servers never load FX classes. */
    public interface ClientEffects {
        ClientEffects NONE = new ClientEffects() {};
        default void tick() {}
        /** The relay at {@code station} has just finished attuning an item. */
        default void finish(int station) {}
        default void stop() {}
    }

    @Nullable private ClientEffects clientEffects;
    private final LumenStackList contents = LumenStackList.create();
    private final ILumenHandler lumen = LumenHandlerViewFactory.builder()
            .tankCapacity(type -> type == LumenAS.PRISMATIC.get() ? LUMEN_CAPACITY : 0)
            .inputFilter((incoming, stored) -> incoming.is(LumenAS.PRISMATIC.get()))
            .onChange(type -> markForUpdate()).createView(contents);
    private long structureTick = Long.MIN_VALUE;
    private boolean formed;

    // Client-only state of the floating star ring, one star per station.
    private static final float RING_IDLE_SPEED = 0.15F, RING_FORMED_SPEED = 0.6F, RING_WORKING_SPEED = 3;
    /** How much faster the ring's clock runs while a relay works: its tumble, nod and bobbing quicken with its orbit. */
    private static final float WORKING_TEMPO = 2;
    /** Every motion driven by the ring clock completes whole cycles within this many ticks, so it wraps seamlessly. */
    public static final float RING_CLOCK_PERIOD = 3600;
    private final float[] stationGlow = new float[AttunementLayout.STATIONS.size()];
    private final float[] stationWork = new float[AttunementLayout.STATIONS.size()];
    private final boolean[] stationAttuning = new boolean[AttunementLayout.STATIONS.size()];
    private boolean lumenFed;
    private float ringPhase, prevRingPhase, ringSpeed = RING_IDLE_SPEED, ringRise, prevRingRise, ringClock, prevRingClock;

    public IridescentAttunementBlockEntity(BlockPos pos, BlockState state) {
        super(new TileRegistryObject<>(ModContent.IRIDESCENT_ATTUNEMENT_ENTITY), pos, state);
    }

    @Override public Codec<TileEntityTick.Data> dataCodec() { return TileEntityTick.Data.CODEC; }
    @Override public ObserverRegistryObject getRequiredObserver() { return AttunementLayout.OBSERVER; }

    public ILumenHandler getLumenHandler() { return lumen; }
    public int getLumenAmount() {
        return contents.getLumenStack(LumenAS.PRISMATIC.get()).map(LumenStack::getAmount).orElse(0);
    }

    @Override public Optional<StoredLumenDisplayTooltip> getDisplayTooltip() {
        // Keep the assigned type and empty bar visible even before the first fill.
        var stored = new StoredLumen(LumenAS.PRISMATIC.get(), getLumenAmount(), LUMEN_CAPACITY);
        var display = new StoredLumenComponent(List.of(stored))
                .updateLumenAlwaysShow(LumenAS.PRISMATIC.get().getRegistryKey().orElseThrow(), true);
        return Optional.of(StoredLumenDisplayTooltip.of(getDisplayComponentIdentifier(), display));
    }

    @Override public boolean hasStructure() {
        if (level == null || isRemoved()) return false;
        if (level.isClientSide()) return super.hasStructure();
        if (structureTick != level.getGameTime()) {
            structureTick = level.getGameTime();
            formed = AttunementLayout.isLoaded(level, worldPosition) && super.hasStructure();
        }
        return formed;
    }

    @Override public void clientTick(Level level) {
        super.clientTick(level);
        boolean working = false;
        lumenFed = false;
        for (int i = 0; i < AttunementLayout.STATIONS.size(); i++) {
            var station = AttunementLayout.STATIONS.get(i);
            float glow = 0;
            boolean attuning = false;
            if (level.getBlockEntity(worldPosition.offset(station.offset())) instanceof ConstellationRelayBlockEntity relay
                    && relay.getConstellation() == station.constellation().get()) {
                attuning = relay.isWorking();
                glow = relay.isVisible() ? 1 : 0.6F;
                working |= attuning;
                lumenFed |= relay.isUsingLumen();
            }
            stationAttuning[i] = attuning;
            stationGlow[i] += (glow - stationGlow[i]) * 0.08F;
            stationWork[i] += ((attuning ? 1 : 0) - stationWork[i]) * 0.1F;
        }
        boolean formed = hasStructure();
        prevRingRise = ringRise;
        ringRise = Math.clamp(ringRise + (formed ? 1 : -1) / 40F, 0, 1);
        float targetSpeed = !formed ? RING_IDLE_SPEED : working ? RING_WORKING_SPEED : RING_FORMED_SPEED;
        ringSpeed += (targetSpeed - ringSpeed) * 0.05F;
        if (ringPhase >= 360) ringPhase -= 360;
        prevRingPhase = ringPhase;
        ringPhase += ringSpeed;
        float tempo = 1 + Math.max(0, ringSpeed - RING_FORMED_SPEED)
                / (RING_WORKING_SPEED - RING_FORMED_SPEED) * (WORKING_TEMPO - 1);
        if (ringClock >= RING_CLOCK_PERIOD) ringClock -= RING_CLOCK_PERIOD;
        prevRingClock = ringClock;
        ringClock += tempo;
        clientEffects().tick();
    }

    private ClientEffects clientEffects() {
        if (clientEffects == null) clientEffects = ((IridescentAttunementBlock) getBlockState().getBlock()).createClientEffects(this);
        return clientEffects;
    }

    /** Called on the client when the relay bound to {@code station} completes an attunement. */
    public void onStationFinished(int station) {
        if (level != null && level.isClientSide && station >= 0 && station < AttunementLayout.STATIONS.size())
            clientEffects().finish(station);
    }

    @Override public void setRemoved() {
        if (clientEffects != null) {
            clientEffects.stop();
            clientEffects = null;
        }
        super.setRemoved();
    }

    /** Ring rotation in degrees. */
    public float getRingPhase(float partialTick) { return prevRingPhase + (ringPhase - prevRingPhase) * partialTick; }
    /** 0 while the ring hovers round the slab, 1 once the structure has lifted it. */
    public float getRingRise(float partialTick) { return prevRingRise + (ringRise - prevRingRise) * partialTick; }
    /** Ring animation time in ticks, wrapping at {@link #RING_CLOCK_PERIOD}; runs faster while a relay works. */
    public float getRingClock(float partialTick) { return prevRingClock + (ringClock - prevRingClock) * partialTick; }
    /** 0 without a relay, 0.6 with one, 1 while its star map is displayed. */
    public float getStationGlow(int station) { return stationGlow[station]; }
    /** Fades to 1 while the station's relay is attuning. */
    public float getStationWork(int station) { return stationWork[station]; }
    /** Whether the station's relay is attuning right now. */
    public boolean isStationAttuning(int station) { return stationAttuning[station]; }
    /** Whether a relay is attuning on this altar's prismatic lumen, its constellation being out of the sky. */
    public boolean isLumenFed() { return lumenFed; }

    public boolean consumeLumen(int amount, boolean simulate) {
        return lumen.drain(LumenAS.PRISMATIC.get(), amount,
                simulate ? ILumenHandler.Action.SIMULATE : ILumenHandler.Action.EXECUTE).getAmount() == amount;
    }

    @Override public void serverTick(ServerLevel server) {
        super.serverTick(server);
        hasStructure();
        if (server.getGameTime() % 20 == 0 && getLumenAmount() < LUMEN_CAPACITY) {
            var request = LumenAS.PRISMATIC.stack(Math.min(1000, LUMEN_CAPACITY - getLumenAmount()));
            LumenRequestHelper.requestRelayed(server, worldPosition, request).ifPresent(chain -> {
                var received = LumenUtil.tryChainTransfer(lumen, server, chain, request, ILumenHandler.Action.EXECUTE);
                if (!received.isEmpty()) chain.playTransferEffect(server, received.getLumen());
            });
        }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = super.getUpdateTag(registries);
        // Lumen is saved outside native tile data; include it in initial and live client updates too.
        tag.putInt("lumen", getLumenAmount());
        return tag;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("lumen", getLumenAmount());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        contents.clear();
        contents.setLumenStack(LumenAS.PRISMATIC.stack(Math.clamp(tag.getInt("lumen"), 0, LUMEN_CAPACITY)));
        structureTick = Long.MIN_VALUE;
    }
}
