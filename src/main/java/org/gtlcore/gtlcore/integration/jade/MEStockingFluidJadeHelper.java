package org.gtlcore.gtlcore.integration.jade;

import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualHatchStockPartMachine;
import org.gtlcore.gtlcore.utils.NumberUtils;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEStockingPatternBufferPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MEStockingFluidJadeHelper {

    private MEStockingFluidJadeHelper() {}

    public static @Nullable List<ViewGroup<CompoundTag>> createGroups(MetaMachine machine) {
        if (machine instanceof MEStockingPatternBufferPartMachine stocking) {
            var views = createFluidViews(stocking.getStockingFluidMap());
            return views.isEmpty() ? Collections.emptyList() : List.of(new ViewGroup<>(views));
        }
        ExportOnlyAEFluidList aeFluidHandler = getAeFluidHandler(machine);
        List<CompoundTag> views;
        if (aeFluidHandler != null) {
            views = createFluidViews(machine, aeFluidHandler);
        } else {
            IFluidHandler fluidTransfer = machine.getFluidHandlerCap(null, false);
            if (fluidTransfer == null || fluidTransfer.getTanks() == 0) {
                return null;
            }
            views = createFluidViews(fluidTransfer);
        }

        if (views.isEmpty()) {
            return Collections.emptyList();
        }
        return List.of(new ViewGroup<>(views));
    }

    public static List<ClientViewGroup<FluidView>> createClientGroups(List<ViewGroup<CompoundTag>> groups) {
        return ClientViewGroup.map(groups, MEStockingFluidJadeHelper::readFluidView, null);
    }

    private static @Nullable FluidView readFluidView(CompoundTag viewData) {
        return FluidView.readDefault(viewData);
    }

    private static @Nullable ExportOnlyAEFluidList getAeFluidHandler(MetaMachine machine) {
        if (machine instanceof MEInputHatchPartMachine inputHatch) {
            return (ExportOnlyAEFluidList) inputHatch.tank;
        }
        if (machine instanceof MEDualHatchStockPartMachine dualHatch) {
            return dualHatch.aeFluidHandler;
        }
        return null;
    }

    private static List<CompoundTag> createFluidViews(MetaMachine machine, ExportOnlyAEFluidList fluidHandler) {
        if (fluidHandler instanceof IOptimizedMEList optimized && optimized.isStocking()) {
            if (machine instanceof IGridConnectedMachine meMachine && meMachine.getMainNode().getGrid() == null) {
                return Collections.emptyList();
            }
            return createFluidViews(optimized.getMEFluidMap());
        }

        Object2LongLinkedOpenHashMap<AEFluidKey> amounts = new Object2LongLinkedOpenHashMap<>();
        for (ExportOnlyAEFluidSlot slot : fluidHandler.getInventory()) {
            GenericStack stock = slot.getStock();
            if (stock == null || stock.amount() <= 0L || !(stock.what() instanceof AEFluidKey fluidKey)) {
                continue;
            }
            amounts.put(fluidKey, NumberUtils.saturatedAdd(amounts.getLong(fluidKey), stock.amount()));
        }

        List<CompoundTag> views = new ArrayList<>(amounts.size());
        for (Object2LongMap.Entry<AEFluidKey> entry : amounts.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            JadeFluidObject fluid = JadeFluidObject.of(entry.getKey().getFluid(), amount, entry.getKey().toStack(1).getComponentsPatch());
            views.add(FluidView.writeDefault(fluid, amount));
        }
        return views;
    }

    private static List<CompoundTag> createFluidViews(@Nullable Object2LongMap<FluidStack> fluids) {
        if (fluids == null) return List.of();
        var views = new ArrayList<CompoundTag>();
        for (var entry : fluids.object2LongEntrySet()) {
            var stack = entry.getKey(); long amount = entry.getLongValue();
            if (stack.isEmpty() || amount <= 0) continue;
            views.add(FluidView.writeDefault(JadeFluidObject.of(stack.getFluid(), amount, stack.getComponentsPatch()), amount));
        }
        return views;
    }

    private static List<CompoundTag> createFluidViews(IFluidHandler fluidTransfer) {
        List<CompoundTag> views = new ArrayList<>(fluidTransfer.getTanks());
        for (int tank = 0; tank < fluidTransfer.getTanks(); tank++) {
            FluidStack fluidStack = fluidTransfer.getFluidInTank(tank);
            if (fluidStack.isEmpty()) {
                continue;
            }
            long amount = fluidStack.getAmount();
            if (amount <= 0L) {
                continue;
            }
            long capacity = Math.max(amount, fluidTransfer.getTankCapacity(tank));
            JadeFluidObject fluid = JadeFluidObject.of(fluidStack.getFluid(), amount, fluidStack.getComponentsPatch());
            views.add(FluidView.writeDefault(fluid, capacity));
        }
        return views;
    }
}
