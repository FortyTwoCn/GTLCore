package org.gtlcore.gtlcore.integration.jade;

import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualHatchStockPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEStockingPatternBufferPartMachine;
import org.gtlcore.gtlcore.utils.NumberUtils;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemSlot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MEStockingItemJadeHelper {

    private static final String LONG_AMOUNT_TAG = "GTLJadeLongAmount";

    private MEStockingItemJadeHelper() {}

    public static @Nullable List<ViewGroup<ItemStack>> createGroups(MetaMachine machine) {
        if (machine instanceof MEStockingPatternBufferPartMachine stockingPatternBuffer) {
            List<ItemStack> views = createItemViews(stockingPatternBuffer.getStockingItemMap());
            if (views.isEmpty()) {
                return Collections.emptyList();
            }
            return List.of(new ViewGroup<>(views));
        }

        ExportOnlyAEItemList itemHandler = getStockingItemHandler(machine);
        if (itemHandler == null) {
            return null;
        }

        List<ItemStack> views = createItemViews(machine, itemHandler);
        if (views.isEmpty()) {
            return Collections.emptyList();
        }
        return List.of(new ViewGroup<>(views));
    }

    public static List<ClientViewGroup<ItemView>> createClientGroups(List<ViewGroup<ItemStack>> groups) {
        return ClientViewGroup.map(groups, MEStockingItemJadeHelper::createItemView, null);
    }

    private static @Nullable ExportOnlyAEItemList getStockingItemHandler(MetaMachine machine) {
        if (machine instanceof MEInputBusPartMachine input) return (ExportOnlyAEItemList) input.getInventory();

        ExportOnlyAEItemList itemHandler = null;
        if (machine instanceof MEDualHatchStockPartMachine dualHatch) {
            itemHandler = dualHatch.aeItemHandler;
        }
        if (itemHandler instanceof IOptimizedMEList optimized && optimized.isStocking()) {
            return itemHandler;
        }
        return null;
    }

    private static List<ItemStack> createItemViews(MetaMachine machine, ExportOnlyAEItemList itemHandler) {
        if (itemHandler instanceof IOptimizedMEList optimized && optimized.isStocking()) {
            if (machine instanceof MEBusPartMachine meMachine && meMachine.getMainNode().getGrid() == null) {
                return Collections.emptyList();
            }
            return createItemViews(optimized.getMEItemMap());
        }

        Object2LongLinkedOpenHashMap<AEItemKey> amounts = new Object2LongLinkedOpenHashMap<>();
        for (ExportOnlyAEItemSlot slot : itemHandler.getInventory()) {
            GenericStack stock = slot.getStock();
            if (stock == null || stock.amount() <= 0L || !(stock.what() instanceof AEItemKey itemKey)) {
                continue;
            }
            long amount = NumberUtils.saturatedAdd(amounts.getLong(itemKey), stock.amount());
            amounts.put(itemKey, amount);
        }

        List<ItemStack> views = new ArrayList<>(amounts.size());
        for (Object2LongMap.Entry<AEItemKey> entry : amounts.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            if (amount <= 0L) {
                continue;
            }
            ItemStack stack = entry.getKey().toStack(1);
            if (!stack.isEmpty()) {
                annotate(stack, amount);
                views.add(stack);
            }
        }
        return views;
    }

    private static List<ItemStack> createItemViews(@Nullable Object2LongMap<ItemStack> amounts) {
        if (amounts == null || amounts.isEmpty()) {
            return Collections.emptyList();
        }

        List<ItemStack> views = new ArrayList<>(amounts.size());
        for (Object2LongMap.Entry<ItemStack> entry : amounts.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            ItemStack stack = entry.getKey();
            if (amount <= 0L || stack.isEmpty()) {
                continue;
            }
            stack = stack.copy();
            stack.setCount(1);
            annotate(stack, amount);
            views.add(stack);
        }
        return views;
    }

    private static ItemView createItemView(ItemStack stack) {
        long amount = getLongAmount(stack);
        if (amount <= 0L) {
            return new ItemView(stack);
        }
        return new ItemView(removeLongAmountTag(stack)).amountText(NumberUtils.formatLong(amount));
    }

    private static long getLongAmount(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLong(LONG_AMOUNT_TAG);
    }

    private static void annotate(ItemStack stack, long amount) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putLong(LONG_AMOUNT_TAG, amount); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static ItemStack removeLongAmountTag(ItemStack stack) {
        var displayStack = stack.copy();
        var tag = displayStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); tag.remove(LONG_AMOUNT_TAG);
        if (tag.isEmpty()) displayStack.remove(DataComponents.CUSTOM_DATA);
        else displayStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return displayStack;
    }
}
