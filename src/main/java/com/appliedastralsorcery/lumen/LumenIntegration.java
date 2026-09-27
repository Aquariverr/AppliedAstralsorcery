package com.appliedastralsorcery.lumen;

import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackImportStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.parts.automation.ForgeExternalStorageStrategy;
import appeng.parts.automation.HandlerStrategy;
import appeng.parts.automation.StorageExportStrategy;
import appeng.parts.automation.StorageImportStrategy;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.lumen.LumenStack;

public final class LumenIntegration {
    public static final HandlerStrategy<ILumenHandler, LumenStack> HANDLER = new HandlerStrategy<>(LumenKeyType.INSTANCE) {
        @Override
        public LumenStorage getFacade(ILumenHandler handler) {
            return new LumenStorage(handler);
        }

        @Override
        public LumenStack getStack(AEKey what, long amount) {
            return what instanceof LumenKey key ? key.lumen().stack(LumenStorage.clampAmount(amount)) : null;
        }

        @Override
        public long insert(ILumenHandler handler, AEKey what, long amount, Actionable mode) {
            var stack = getStack(what, amount);
            return stack == null || stack.isEmpty() ? 0 : handler.fill(stack, LumenStorage.action(mode));
        }
    };

    private LumenIntegration() {}

    public static void register() {
        var type = LumenKeyType.INSTANCE;
        StackImportStrategy.register(type, (level, pos, side) ->
                new StorageImportStrategy<>(ILumenHandler.BLOCK, HANDLER, level, pos, side));
        StackExportStrategy.register(type, (level, pos, side) ->
                new StorageExportStrategy<>(ILumenHandler.BLOCK, HANDLER, level, pos, side));
        ExternalStorageStrategy.register(type, (level, pos, side) ->
                new ForgeExternalStorageStrategy<>(ILumenHandler.BLOCK, HANDLER, level, pos, side));
        ContainerItemStrategy.register(type, LumenKey.class, new LumenContainerStrategy());
        GenericSlotCapacities.register(type, 8L * LumenStack.FLASK_VALUE);
        for (var cell : ModContent.LUMEN_CELLS) {
            Upgrades.add(AEItems.INVERTER_CARD, cell, 1);
            Upgrades.add(AEItems.VOID_CARD, cell, 1);
        }
    }
}
