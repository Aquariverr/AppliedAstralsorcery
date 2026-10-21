package com.appliedastralsorcery.gametest;

import java.util.HashSet;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.appliedastralsorcery.attunement.AttunementLayout;
import com.appliedastralsorcery.crystallizer.WorldCrystallization;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class PerformanceWorldGameTests {
    @GameTest(template = "attunement_test")
    public static void blockedCrystallizerReturnsRetryOncePerTickWithoutLosingItems(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var engine = new WorldCrystallization();
        var saved = new CompoundTag();
        var job = new CompoundTag();
        var outputs = new ListTag();
        outputs.add(new ItemStack(Items.DIAMOND).save(registries));
        job.put("outputs", outputs);
        saved.put("world_job", job);
        // A loaded return-only job can retain items after its lumen buffer has already emptied.
        engine.load(saved, registries, true);
        var storage = new ReturnStorage();
        var target = helper.absolutePos(new BlockPos(10, 3, 10));
        helper.assertFalse(engine.tick(helper.getLevel(), target, storage, IActionSource.empty(), bound -> 1),
                "A blocked return must retain its saved state");
        helper.assertTrue(storage.calls == 1
                        && engine.save(registries, true).getCompound("world_job")
                                .getList("outputs", Tag.TAG_COMPOUND).size() == 1,
                "A blocked output must be offered once per tick and remain persisted");
        storage.accept = true;
        helper.assertTrue(engine.tick(helper.getLevel(), target, storage, IActionSource.empty(), bound -> 1),
                "The return must complete when storage becomes available on the next tick");
        engine.tick(helper.getLevel(), target, storage, IActionSource.empty(), bound -> 1);
        helper.assertTrue(storage.calls == 2 && storage.returned == 1
                        && engine.save(registries, true).getCompound("world_job")
                                .getList("outputs", Tag.TAG_COMPOUND).isEmpty(),
                "The retained item must return exactly once and stop retrying after completion");
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void attunementChunkFootprintCoversEveryAlignment(GameTestHelper helper)
            throws ReflectiveOperationException {
        var field = AttunementLayout.class.getDeclaredField("REQUIRED_CHUNKS");
        field.setAccessible(true);
        var footprints = (ChunkPos[][]) field.get(null);
        var structure = AttunementLayout.structure().getContents().keySet();
        int min = Integer.MAX_VALUE;
        int max = 0;
        // Test cache reuse across positive and negative chunk boundaries, including far unloaded chunks.
        for (var origin : new BlockPos[]{BlockPos.ZERO, new BlockPos(-32, 0, -32),
                helper.absolutePos(BlockPos.ZERO), new BlockPos(-100000, 0, 100000)}) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    var center = origin.offset(x, 0, z);
                    var expected = new HashSet<ChunkPos>();
                    for (var offset : structure) expected.add(new ChunkPos(center.offset(offset)));
                    boolean expectedLoaded = expected.stream()
                            .allMatch(chunk -> helper.getLevel().hasChunk(chunk.x, chunk.z));
                    helper.assertTrue(AttunementLayout.isLoaded(helper.getLevel(), center) == expectedLoaded,
                            "Chunk availability must agree with the full structure footprint at " + center);
                    var cached = footprints[(center.getX() & 15) * 16 + (center.getZ() & 15)];
                    var actual = new HashSet<ChunkPos>();
                    for (var offset : cached) {
                        actual.add(new ChunkPos((center.getX() >> 4) + offset.x,
                                (center.getZ() >> 4) + offset.z));
                    }
                    helper.assertTrue(actual.equals(expected) && cached.length == actual.size(),
                            "Cached footprint must cover each required chunk exactly once at " + center);
                    min = Math.min(min, cached.length);
                    max = Math.max(max, cached.length);
                }
            }
        }
        System.out.println("Attunement chunk checks: " + structure.size() + " blocks -> " + min + "-" + max
                + " unique chunks across all 256 alignments");
        helper.succeed();
    }

    private static final class ReturnStorage implements MEStorage {
        private boolean accept;
        private int calls;
        private long returned;

        @Override public Component getDescription() { return Component.literal("Return retry test storage"); }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            calls++;
            if (!accept || !AEItemKey.of(Items.DIAMOND).equals(key)) return 0;
            if (mode == Actionable.MODULATE) returned += amount;
            return amount;
        }
    }
}
