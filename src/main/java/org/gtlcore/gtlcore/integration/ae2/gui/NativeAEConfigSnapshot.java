package org.gtlcore.gtlcore.integration.ae2.gui;

import com.gregtechceu.gtceu.integration.ae2.slot.IConfigurableSlotList;
import appeng.api.stacks.GenericStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.util.Objects;

/** A packet writer may run after another tick, or more than once. It must never change the delta cache. */
public final class NativeAEConfigSnapshot {
    private final boolean[] changed;
    private final GenericStack[] config, stock;
    private NativeAEConfigSnapshot(boolean[] changed, GenericStack[] config, GenericStack[] stock) {
        this.changed = changed; this.config = config; this.stock = stock;
    }
    public static NativeAEConfigSnapshot capture(IConfigurableSlotList slots, GenericStack[] previousConfig,
                                                 GenericStack[] previousStock, boolean init) {
        if (previousConfig.length != previousStock.length || previousConfig.length > slots.getConfigurableSlots())
            throw new IllegalArgumentException("AE configuration slot counts differ");
        int count = previousConfig.length;
        var changed = new boolean[count]; var config = new GenericStack[count]; var stock = new GenericStack[count];
        for (int i = 0; i < count; i++) {
            var slot = slots.getConfigurableSlot(i);
            config[i] = copy(slot.getConfig()); stock[i] = copy(slot.getStock());
            changed[i] = init || !Objects.equals(config[i], previousConfig[i]) || !Objects.equals(stock[i], previousStock[i]);
        }
        return new NativeAEConfigSnapshot(changed, config, stock);
    }
    private static GenericStack copy(GenericStack value) { return value == null ? null : new GenericStack(value.what(), value.amount()); }
    public boolean hasChanges() { for (boolean value : changed) if (value) return true; return false; }
    public void updateCache(GenericStack[] cachedConfig, GenericStack[] cachedStock) {
        System.arraycopy(config, 0, cachedConfig, 0, config.length); System.arraycopy(stock, 0, cachedStock, 0, stock.length);
    }
    public void write(RegistryFriendlyByteBuf packet) {
        for (int i = 0; i < changed.length; i++) {
            packet.writeBoolean(changed[i]);
            if (changed[i]) { writeStack(packet, config[i]); writeStack(packet, stock[i]); }
        }
    }
    private static void writeStack(RegistryFriendlyByteBuf packet, GenericStack value) {
        packet.writeBoolean(value != null); if (value != null) GenericStack.writeBuffer(value, packet);
    }
}
