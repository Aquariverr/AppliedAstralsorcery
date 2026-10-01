package com.appliedastralsorcery.lumen;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import hellfirepvp.astralsorcery.common.lumen.capability.LumenHandlerViewFactory;
import hellfirepvp.astralsorcery.common.lumen.capability.LumenStackList;
import hellfirepvp.astralsorcery.common.tile.TileLumenArray;
import hellfirepvp.astralsorcery.common.util.inventory.InventoryStackList;
import hellfirepvp.astralsorcery.common.util.inventory.FilteredInventoryViewFactory;
import hellfirepvp.astralsorcery.common.util.tank.FluidContainerList;
import net.minecraft.core.BlockPos;

/** Same save format and capacities as AS, with ME-specific binding and extraction rules. */
public class MELumenArrayData extends TileLumenArray.Data {
    public static final Codec<TileLumenArray.Data> CODEC = RecordCodecBuilder.<MELumenArrayData>create(
            instance -> lumenArrayFields(instance).apply(instance, MELumenArrayData::new))
            .xmap(data -> data, data -> (MELumenArrayData) data);

    // The native RecordCodecBuilder and superclass represent an absent owner with Optional.
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    protected MELumenArrayData(long ticks, boolean structure, Map<BlockPos, Boolean> sky, Optional<UUID> owner,
            LumenStackList lumen, FluidContainerList fluid, InventoryStackList inventory, boolean extended, Lumen assigned) {
        super(ticks, structure, sky, owner, lumen, fluid, inventory, extended, assigned);
    }

    public Lumen getActiveLumen() { return assignedLumen == LumenAS.NONE.get() ? null : assignedLumen; }

    void bindEmptyArray(Lumen type) {
        if (!lumenContents.isEmpty() || !getInventory().getStackInSlot(0).isEmpty())
            throw new IllegalStateException("Array must be empty before changing its binding");
        assignedLumen = type;
        allowExtendedLumenTransfer = false;
        markForUpdate();
    }

    private boolean acceptsInputs() {
        return !getOptionalTile(MELumenArrayBlockEntity.class).map(MELumenArrayBlockEntity::isSwitchPending).orElse(false);
    }

    @Override protected LumenHandlerViewFactory newLumenHandler() {
        return super.newLumenHandler()
                .extractFilter((amount, existing) -> true)
                .inputFilter((incoming, existing) -> acceptsInputs()
                        && (assignedLumen == LumenAS.NONE.get() || incoming.is(assignedLumen)));
    }

    @Override protected FilteredInventoryViewFactory newInventoryHandler() {
        return super.newInventoryHandler().inputFilter((slot, incoming, existing) -> acceptsInputs()
                && existing.isEmpty() && !getContainedFluid().isEmpty() && findMatchingRecipe(incoming).isPresent());
    }
}
