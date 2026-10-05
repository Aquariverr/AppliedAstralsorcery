package com.appliedastralsorcery.attunement;

import java.util.List;
import java.util.function.Supplier;

import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.AppliedAstralsorcery;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.lib.ConstellationsAS;
import hellfirepvp.astralsorcery.common.structure.PatternAttunementAltar;
import hellfirepvp.astralsorcery.common.util.data.ObserverRegistryObject;
import hellfirepvp.observerlib.api.ObserverProvider;
import hellfirepvp.observerlib.common.change.ObserverProviderStructure;
import hellfirepvp.observerlib.common.registry.RegistryProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Three relays per side, six blocks from the center axis, clockwise from north. */
public final class AttunementLayout {
    public record Station(BlockPos offset, Supplier<BaseConstellation> constellation) {}

    public static final List<Station> STATIONS = List.of(
            station(0, -6, ConstellationsAS.AEVITAS), station(3, -6, ConstellationsAS.ARMARA),
            station(6, -3, ConstellationsAS.DISCIDIA), station(6, 0, ConstellationsAS.EVORSIO),
            station(6, 3, ConstellationsAS.VICIO), station(3, 6, ConstellationsAS.LUCERNA),
            station(0, 6, ConstellationsAS.MINERALIS), station(-3, 6, ConstellationsAS.HOROLOGIUM),
            station(-6, 3, ConstellationsAS.OCTANS), station(-6, 0, ConstellationsAS.BOOTES),
            station(-6, -3, ConstellationsAS.FORNAX), station(-3, -6, ConstellationsAS.PELOTRIO));

    private static PatternAttunementAltar pattern;
    private static final DeferredRegister<ObserverProvider<?>> OBSERVERS =
            DeferredRegister.create(RegistryProviders.REGISTRY_KEY, AppliedAstralsorcery.MOD_ID);
    public static final ObserverRegistryObject OBSERVER = new ObserverRegistryObject(
            OBSERVERS.register("iridescent_attunement_altar", () -> new ObserverProviderStructure(structure())));

    private AttunementLayout() {}

    public static void register(IEventBus bus) { OBSERVERS.register(bus); }

    private static Station station(int x, int z, Supplier<BaseConstellation> constellation) {
        return new Station(new BlockPos(x, 0, z), constellation);
    }

    public static PatternAttunementAltar structure() {
        if (pattern == null) {
            pattern = new PatternAttunementAltar();
            pattern.addBlock(ModContent.IRIDESCENT_ATTUNEMENT_ALTAR.get(), 0, 0, 0);
            for (var station : STATIONS) {
                var offset = station.offset();
                pattern.addBlock(ModContent.CONSTELLATION_RELAY.get(), offset.getX(), 0, offset.getZ());
            }
        }
        return pattern;
    }

    public static boolean matches(Level level, BlockPos center) {
        return isLoaded(level, center) && structure().matches(level, center);
    }

    public static boolean isLoaded(Level level, BlockPos center) {
        for (var offset : structure().getContents().keySet()) {
            var pos = center.offset(offset);
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        }
        return true;
    }
}
