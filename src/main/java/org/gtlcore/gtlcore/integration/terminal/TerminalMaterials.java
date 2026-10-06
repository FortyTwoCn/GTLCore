package org.gtlcore.gtlcore.integration.terminal;

import org.gtlcore.gtlcore.integration.ae2.AEOutputBuffer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerInvWrapper;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Materials are persisted before placement. A refused refund remains on the terminal for the next action. */
public final class TerminalMaterials {
    public static final String MATERIALS = "gtlcore_terminal_materials";
    private final AEOutputBuffer pending = new AEOutputBuffer(() -> {});
    private final Player player;
    private final ItemStack terminal;
    private final @Nullable MEStorage network;
    private final IActionSource source;
    private final IItemHandler inventory;

    public TerminalMaterials(Player player, ItemStack terminal, @Nullable MEStorage network) {
        this.player = player; this.terminal = terminal; this.network = network; source = IActionSource.ofPlayer(player);
        var tag = terminal.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        pending.deserializeNBT(player.registryAccess(), tag.getList(MATERIALS, Tag.TAG_COMPOUND));
        var capability = player.getCapability(Capabilities.ItemHandler.ENTITY);
        inventory = capability == null ? new PlayerInvWrapper(player.getInventory()) : capability;
    }

    public long amount(AEKey key) { return pending.storage.getLong(key); }
    public static Component describe(ItemStack terminal, HolderLookup.Provider registries) {
        var contents = new AEOutputBuffer(() -> {});
        contents.deserializeNBT(registries, terminal.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getList(MATERIALS, Tag.TAG_COMPOUND));
        if (contents.isEmpty()) return Component.translatable("gtlcore.terminal.pending_empty");
        var result = Component.translatable("gtlcore.terminal.pending_materials");
        int shown = 0;
        var entries = new ArrayList<>(contents.storage.object2LongEntrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().getId().toString()));
        for (var entry : entries) {
            if (shown == 2) break;
            if (shown++ > 0) result.append(", ");
            result.append(entry.getKey().getDisplayName()).append(" × " + entry.getLongValue());
            if (entry.getKey() instanceof AEFluidKey) result.append(" mB");
        }
        if (entries.size() > shown) result.append(Component.translatable("gtlcore.terminal.pending_more", entries.size() - shown));
        return result;
    }
    public void add(AEKey key, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Terminal material amount must be positive");
        pending.storage.put(key, Math.addExact(amount(key), amount)); save();
    }
    private void remove(AEKey key, long amount) {
        long remaining = Math.subtractExact(amount(key), amount);
        if (remaining < 0) throw new IllegalStateException("Terminal reservation was already consumed");
        if (remaining == 0) pending.storage.removeLong(key); else pending.storage.put(key, remaining); save();
    }
    private void save() {
        var tag = terminal.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (pending.isEmpty()) tag.remove(MATERIALS); else tag.put(MATERIALS, pending.serializeNBT(player.registryAccess()));
        terminal.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    public final class Reservation {
        private final AEKey key; private final long amount; private boolean consumed;
        private Reservation(AEKey key, long amount) { this.key = key; this.amount = amount; }
        public ItemStack item() { return key instanceof AEItemKey item ? item.toStack(1) : ItemStack.EMPTY; }
        public void commit() {
            if (consumed) throw new IllegalStateException("Terminal reservation was already committed");
            if (!player.isCreative()) remove(key, amount); consumed = true;
        }
    }
    public @Nullable Reservation reserve(AEKey key, int count) {
        if (count <= 0) throw new IllegalArgumentException("Invalid terminal reservation");
        if (player.isCreative()) return new Reservation(key, count);
        if (key instanceof AEItemKey item) {
            for (var entry : pending.storage.object2LongEntrySet()) {
                if (entry.getKey() instanceof AEItemKey stored && stored.getItem() == item.getItem() && entry.getLongValue() >= count)
                    return new Reservation(stored, count);
            }
        } else if (amount(key) >= count) return new Reservation(key, count);
        int needed = (int) Math.max(0, count - amount(key));
        if (network != null && network.extract(key, needed, Actionable.SIMULATE, source) >= needed) {
            long extracted = network.extract(key, needed, Actionable.MODULATE, source);
            if (extracted > 0) add(key, extracted);
            if (amount(key) >= count) return new Reservation(key, count);
        }
        needed = (int) Math.max(0, count - amount(key));
        for (var slot : slots()) {
            var stack = slot.handler().getStackInSlot(slot.index());
            if (key instanceof AEItemKey item && stack.getItem() == item.getItem()) {
                var extracted = slot.handler().extractItem(slot.index(), count, false);
                if (!extracted.isEmpty()) {
                    var actual = AEItemKey.of(extracted); add(actual, extracted.getCount());
                    if (amount(actual) >= count) return new Reservation(actual, count);
                }
            } else if (key instanceof AEFluidKey fluid && !stack.isEmpty()) {
                // Capabilities operate on an extracted container, never on a live inventory stack during simulation.
                var candidate = stack.copyWithCount(1); var simulated = candidate.getCapability(Capabilities.FluidHandler.ITEM);
                if (simulated == null || simulated.drain(fluid.toStack(needed), IFluidHandler.FluidAction.SIMULATE).getAmount() != needed) continue;
                var container = slot.handler().extractItem(slot.index(), 1, false); if (container.isEmpty()) continue;
                var handler = container.getCapability(Capabilities.FluidHandler.ITEM);
                if (handler == null) { returnItem(slot, container); continue; }
                var drained = handler.drain(fluid.toStack(needed), IFluidHandler.FluidAction.EXECUTE);
                if (!drained.isEmpty()) add(AEFluidKey.of(drained), drained.getAmount());
                returnItem(slot, handler.getContainer());
                if (amount(key) >= count) return new Reservation(key, count);
            }
        }
        return null;
    }
    public void refund() {
        if (network != null) { pending.insertInventory(network, source); save(); }
        for (var key : new ArrayList<>(pending.storage.keySet())) {
            if (key instanceof AEItemKey item) {
                while (amount(key) > 0) {
                    int count = (int) Math.min(amount(key), item.toStack(1).getMaxStackSize());
                    var remainder = ItemHandlerHelper.insertItemStacked(inventory, item.toStack(count), false);
                    int returned = count - remainder.getCount();
                    if (returned > 0) remove(key, returned);
                    if (!remainder.isEmpty()) { int dropped = remainder.getCount(); player.drop(remainder, false); remove(key, dropped); }
                }
            } else if (key instanceof AEFluidKey fluid) {
                for (var slot : slots()) {
                    int count = (int) Math.min(amount(key), Integer.MAX_VALUE); if (count <= 0) break;
                    var candidate = slot.handler().getStackInSlot(slot.index()).copyWithCount(1);
                    var simulated = candidate.getCapability(Capabilities.FluidHandler.ITEM);
                    if (simulated == null || simulated.fill(fluid.toStack(count), IFluidHandler.FluidAction.SIMULATE) == 0) continue;
                    var container = slot.handler().extractItem(slot.index(), 1, false); if (container.isEmpty()) continue;
                    var handler = container.getCapability(Capabilities.FluidHandler.ITEM);
                    if (handler == null) { returnItem(slot, container); continue; }
                    int filled = handler.fill(fluid.toStack(count), IFluidHandler.FluidAction.EXECUTE);
                    if (filled > 0) remove(key, filled);
                    returnItem(slot, handler.getContainer());
                }
            }
        }
    }
    private void returnItem(Slot slot, ItemStack stack) {
        if (stack.isEmpty()) return;
        var remainder = slot.handler().insertItem(slot.index(), stack, false);
        if (!remainder.isEmpty()) remainder = ItemHandlerHelper.insertItemStacked(inventory, remainder, false);
        if (!remainder.isEmpty()) player.drop(remainder, false);
    }
    private record Slot(IItemHandler handler, int index) {}
    private List<Slot> slots() {
        var result = new ArrayList<Slot>(); collect(inventory, 0, Collections.newSetFromMap(new IdentityHashMap<>()), result); return result;
    }
    private static void collect(IItemHandler handler, int depth, Set<IItemHandler> visited, List<Slot> result) {
        if (depth > 5 || handler == null || !visited.add(handler)) return;
        for (int index = 0; index < handler.getSlots() && result.size() < 256; index++) {
            var stack = handler.getStackInSlot(index);
            var nested = stack.getCapability(Capabilities.ItemHandler.ITEM);
            if (nested != null) collect(nested, depth + 1, visited, result);
            result.add(new Slot(handler, index));
        }
    }
}
