package com.appliedastralsorcery.lumen;

import java.util.List;
import java.util.Objects;

import appeng.api.stacks.AEKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class LumenKey extends AEKey {
    public static final MapCodec<LumenKey> MAP_CODEC = Codec.lazyInitialized(
            () -> RegistriesAS.REGISTRY_LUMEN.byNameCodec().validate(lumen -> lumen == LumenAS.NONE.get()
                    ? DataResult.error(() -> "Empty lumen cannot be stored") : DataResult.success(lumen)))
            .xmap(LumenKey::of, LumenKey::lumen).fieldOf("id");

    private final Lumen lumen;

    private LumenKey(Lumen lumen) {
        this.lumen = Objects.requireNonNull(lumen);
        if (lumen == LumenAS.NONE.get() || RegistriesAS.REGISTRY_LUMEN.getKey(lumen) == null) {
            throw new IllegalArgumentException("Expected registered, non-empty lumen");
        }
    }

    public static LumenKey of(Lumen lumen) {
        return new LumenKey(lumen);
    }

    public Lumen lumen() {
        return lumen;
    }

    @Override
    public LumenKeyType getType() {
        return LumenKeyType.INSTANCE;
    }

    @Override
    public LumenKey dropSecondary() {
        return this;
    }

    @Override
    public CompoundTag toTag(HolderLookup.Provider registries) {
        return (CompoundTag) MAP_CODEC.codec().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this)
                .getOrThrow();
    }

    @Override
    public Object getPrimaryKey() {
        return lumen;
    }

    @Override
    public ResourceLocation getId() {
        return Objects.requireNonNull(RegistriesAS.REGISTRY_LUMEN.getKey(lumen));
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        data.writeResourceLocation(getId());
    }

    public static LumenKey fromPacket(RegistryFriendlyByteBuf data) {
        var id = data.readResourceLocation();
        var lumen = RegistriesAS.REGISTRY_LUMEN.get(id);
        if (lumen == null || lumen == LumenAS.NONE.get()) {
            throw new IllegalArgumentException("Unknown lumen: " + id);
        }
        return of(lumen);
    }

    @Override
    protected Component computeDisplayName() {
        return lumen.getHoverName();
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        // Uncontained lumen dissipates, just like a fluid resource.
    }

    @Override
    public boolean hasComponents() {
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LumenKey key && key.lumen == lumen;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(lumen);
    }
}
