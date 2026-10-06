package org.gtlcore.gtlcore.api.machine.trait.AECraft;

import net.neoforged.neoforge.items.IItemHandler;

import net.minecraft.core.BlockPos;

public interface IMECraftPatternContainer {

    static int sumNonEmpty(IItemHandler itemTransfer) {
        int sum = 0;
        for (int i = 0, n = itemTransfer.getSlots(); i < n; i++) {
            var stack = itemTransfer.getStackInSlot(i);
            if (!stack.isEmpty()) sum++;
        }
        return sum;
    }

    BlockPos getBlockPos();

    int getSlots();
}
