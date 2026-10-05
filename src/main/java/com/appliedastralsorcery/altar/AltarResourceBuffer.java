package com.appliedastralsorcery.altar;

import java.util.ArrayList;
import java.util.List;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import com.appliedastralsorcery.lumen.LumenKey;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.lumen.capability.LumenHandlerViewFactory;
import hellfirepvp.astralsorcery.common.lumen.capability.LumenStackList;
import hellfirepvp.astralsorcery.common.util.tank.FluidContainerList;
import hellfirepvp.astralsorcery.common.util.tank.FluidTankViewFactory;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

final class AltarResourceBuffer {
    static final int FLUID_TANKS = 16;

    private final FluidContainerList fluids = FluidContainerList.create();
    private final LumenStackList lumens = LumenStackList.create();
    final IFluidHandler fluid;
    final ILumenHandler lumen;

    AltarResourceBuffer(Runnable changed) {
        fluid = FluidTankViewFactory.builder(FLUID_TANKS).tankCapacity(tank -> AltarAutomationBlockEntity.FLUID_CAPACITY)
                .onChange(tank -> changed.run()).createView(fluids);
        lumen = LumenHandlerViewFactory.builder().tankCapacity(type -> AltarAutomationBlockEntity.LUMEN_CAPACITY)
                .onChange(type -> changed.run()).createView(lumens);
    }

    boolean canInsert(List<GenericStack> incoming) {
        // Simulate the entire batch on a copy; individual simulations could reuse the same empty fluid tank.
        var copy = new AltarResourceBuffer(() -> {});
        for (int i = 0; i < FLUID_TANKS; i++) copy.fluids.getTank(i).setContent(fluid.getFluidInTank(i));
        for (var stack : lumen.getContainedLumen()) copy.lumens.setLumenStack(stack.copy());
        for (var stack : incoming) {
            if (copy.insert(stack) != stack.amount()) return false;
        }
        return true;
    }

    void insertAll(List<GenericStack> incoming) {
        for (var stack : incoming) insert(stack);
    }

    private int insert(GenericStack stack) {
        if (stack.amount() <= 0 || stack.amount() > Integer.MAX_VALUE) return 0;
        if (stack.what() instanceof AEFluidKey key) {
            return fluid.fill(key.toStack((int) stack.amount()), IFluidHandler.FluidAction.EXECUTE);
        }
        if (stack.what() instanceof LumenKey key) {
            return lumen.fill(key.lumen().stack((int) stack.amount()), ILumenHandler.Action.EXECUTE);
        }
        return 0;
    }

    List<GenericStack> contents() {
        var result = new ArrayList<GenericStack>();
        for (int i = 0; i < FLUID_TANKS; i++) {
            var stack = fluid.getFluidInTank(i);
            if (!stack.isEmpty()) result.add(GenericStack.fromFluidStack(stack));
        }
        for (var stack : lumen.getContainedLumen()) {
            if (!stack.isEmpty()) result.add(new GenericStack(LumenKey.of(stack.getLumen()), stack.getAmount()));
        }
        return result;
    }

    ListTag save(HolderLookup.Provider registries) {
        var saved = new ListTag();
        for (var stack : contents()) saved.add(GenericStack.writeTag(registries, stack));
        return saved;
    }

    void load(ListTag saved, HolderLookup.Provider registries) {
        clear();
        for (int i = 0; i < saved.size(); i++) {
            var stack = GenericStack.readTag(registries, saved.getCompound(i));
            if (stack != null) insert(stack);
        }
    }

    void clear() {
        for (int i = 0; i < FLUID_TANKS; i++) fluids.getTank(i).clear();
        lumens.clear();
    }
}
