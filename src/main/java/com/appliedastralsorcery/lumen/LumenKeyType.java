package com.appliedastralsorcery.lumen;

import appeng.api.stacks.AEKeyType;
import com.appliedastralsorcery.AppliedAstralsorcery;
import com.mojang.serialization.MapCodec;
import hellfirepvp.astralsorcery.common.lumen.LumenStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class LumenKeyType extends AEKeyType {
    public static final LumenKeyType INSTANCE = new LumenKeyType();

    private LumenKeyType() {
        super(ResourceLocation.fromNamespaceAndPath(AppliedAstralsorcery.MOD_ID, "lumen"), LumenKey.class,
                Component.translatable("keytype.appliedas.lumen"));
    }

    @Override
    public MapCodec<LumenKey> codec() {
        return LumenKey.MAP_CODEC;
    }

    @Override
    public LumenKey readFromPacket(RegistryFriendlyByteBuf input) {
        return LumenKey.fromPacket(input);
    }

    @Override
    public int getAmountPerByte() {
        return LumenStack.FLASK_VALUE / 100;
    }

    @Override
    public int getAmountPerOperation() {
        return LumenStack.FLASK_VALUE;
    }

    @Override
    public String getUnitSymbol() {
        return "Lm";
    }
}
