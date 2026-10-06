package org.gtlcore.gtlcore.api.gui;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.common.item.behavior.CoverPlaceBehavior;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;
import java.util.Arrays;
import java.util.function.Supplier;

/** A cover is the actual slot contents: installing consumes one item, extracting removes it once. */
public final class NativeCoverInventory implements IItemHandlerModifiable {
    private final ICoverable holder;
    private final Supplier<@Nullable ServerPlayer> player;
    private final ItemStack[] clientView = new ItemStack[6];

    public NativeCoverInventory(ICoverable holder, Supplier<@Nullable ServerPlayer> player) {
        this.holder = holder;
        this.player = player;
        Arrays.fill(clientView, ItemStack.EMPTY);
    }

    private Direction side(int slot) {
        if (slot < 0 || slot >= 6) throw new IndexOutOfBoundsException("Cover side " + slot);
        return Direction.from3DDataValue(slot);
    }

    private @Nullable CoverDefinition definition(ItemStack stack) {
        if (stack.getItem() instanceof IComponentItem item) {
            for (var component : item.getComponents()) {
                if (component instanceof CoverPlaceBehavior behavior) return behavior.coverDefinition().value();
            }
        }
        return null;
    }

    private boolean canEdit() {
        var editor = player.get();
        return editor != null && !holder.isRemoved() && editor.level() == holder.getLevel() &&
                editor.distanceToSqr(Vec3.atCenterOf(holder.getBlockPos())) <= 64;
    }

    @Override public int getSlots() { return 6; }
    @Override public int getSlotLimit(int slot) { side(slot); return 1; }

    @Override public ItemStack getStackInSlot(int slot) {
        Direction side = side(slot);
        if (holder.isRemote()) return clientView[slot];
        var cover = holder.getCoverAtSide(side);
        return cover == null ? ItemStack.EMPTY : cover.getPickItem().copyWithCount(1);
    }

    @Override public boolean isItemValid(int slot, ItemStack stack) {
        var definition = definition(stack);
        return definition != null && holder.canPlaceCoverOnSide(definition, side(slot)) &&
                definition.createCoverBehavior(holder, side(slot)).canAttach();
    }

    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !getStackInSlot(slot).isEmpty() || !isItemValid(slot, stack)) return stack;
        if (!simulate) {
            if (!canEdit() || !holder.placeCoverOnSide(side(slot), stack.copyWithCount(1), definition(stack), player.get())) return stack;
        }
        return stack.getCount() == 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
    }

    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        var current = getStackInSlot(slot);
        if (amount <= 0 || current.isEmpty()) return ItemStack.EMPTY;
        if (!simulate && (!canEdit() || !holder.removeCover(false, side(slot), player.get()))) return ItemStack.EMPTY;
        return current;
    }

    @Override public void setStackInSlot(int slot, ItemStack stack) {
        Direction side = side(slot);
        if (holder.isRemote()) { clientView[slot] = stack.copy(); return; }
        var current = getStackInSlot(slot);
        if (ItemStack.matches(current, stack) || !canEdit()) return;
        if (!stack.isEmpty() && (stack.getCount() != 1 || !isItemValid(slot, stack)))
            throw new IllegalArgumentException("Invalid cover item for " + side);
        if (!current.isEmpty()) holder.removeCover(false, side, player.get());
        if (!stack.isEmpty()) holder.placeCoverOnSide(side, stack.copy(), definition(stack), player.get());
    }
}
