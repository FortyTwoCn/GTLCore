package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.AERefundQueue;
import appeng.api.stacks.*;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;

class AERefundQueueIntegrationTest {
    private static RegistryAccess registry() { return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY); }
    private static <T extends AEKey> Object2LongOpenHashMap<T> inventory(T key, long amount) {
        var result = new Object2LongOpenHashMap<T>(); result.put(key, amount); return result;
    }
    private static BigInteger amount(ListTag data, AEKey key) {
        var total = BigInteger.ZERO;
        for (var ledger : data) for (var value : ((CompoundTag) ledger).getList("entries", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            if (key.equals(AEKey.fromTagGeneric(registry(), entry.getCompound("key")))) total = total.add(BigInteger.valueOf(entry.getLong("value")));
        }
        return total;
    }
    @Test void multipleFullLongInventoriesPartiallyDrainWithoutCombiningOrLosingTheirOwnership() {
        var key = AEItemKey.of(Items.IRON_INGOT); var queue = new AERefundQueue(() -> {});
        var first = inventory(key, Long.MAX_VALUE); var second = inventory(key, Long.MAX_VALUE);
        queue.takeOwnership(first, new Object2LongOpenHashMap<AEFluidKey>());
        queue.takeOwnership(second, new Object2LongOpenHashMap<AEFluidKey>());
        assertTrue(first.isEmpty() && second.isEmpty());
        var buffer = inventory((AEKey) key, Long.MAX_VALUE - 3);
        assertTrue(queue.drainInto(buffer)); assertEquals(Long.MAX_VALUE, buffer.getLong(key));
        var total = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        assertEquals(total.subtract(BigInteger.valueOf(3)), amount(queue.serializeNBT(registry()), key));
        assertFalse(queue.drainInto(buffer));
        var delivered = BigInteger.valueOf(buffer.removeLong(key));
        while (!queue.isEmpty()) { assertTrue(queue.drainInto(buffer)); delivered = delivered.add(BigInteger.valueOf(buffer.removeLong(key))); }
        assertEquals(total.add(BigInteger.valueOf(Long.MAX_VALUE - 3)), delivered);
        assertFalse(queue.drainInto(buffer));
    }
    @Test void nativeComponentKeysAndLongSegmentsSurviveSaveReloadAndSnapshotDoesNotConsumeSource() {
        var item = new ItemStack(Items.IRON_INGOT); item.set(DataComponents.CUSTOM_NAME, Component.literal("owned component item"));
        var fluid = new FluidStack(Fluids.WATER, 1); fluid.set(DataComponents.CUSTOM_NAME, Component.literal("owned component fluid"));
        var itemKey = AEItemKey.of(item); var fluidKey = AEFluidKey.of(fluid); long large = 9_007_199_254_740_993L;
        var source = inventory(itemKey, large); var queue = new AERefundQueue(() -> {});
        queue.appendSnapshot(source); queue.appendSnapshot(inventory(fluidKey, Long.MAX_VALUE)); queue.appendSnapshot(inventory(fluidKey, Long.MAX_VALUE));
        assertEquals(large, source.getLong(itemKey));
        var saved = queue.serializeNBT(registry()); var restored = new AERefundQueue(() -> {}); restored.deserializeNBT(registry(), saved);
        assertEquals(saved, restored.serializeNBT(registry()));
        assertEquals(BigInteger.valueOf(large), amount(saved, itemKey));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), amount(saved, fluidKey));
        assertEquals(BigInteger.ZERO, amount(saved, AEFluidKey.of(Fluids.WATER)));
    }
    @Test void malformedRefundLedgerDoesNotPartiallyReplaceExistingOwnedQueue() {
        var queue = new AERefundQueue(() -> {}); queue.appendSnapshot(inventory(AEItemKey.of(Items.IRON_INGOT), 23));
        var before = queue.serializeNBT(registry()); var bad = before.copy(); var entry = new CompoundTag();
        var wrongType = new ListTag(); wrongType.add(StringTag.valueOf("invalid")); entry.put("entries", wrongType); bad.add(entry);
        assertThrows(IllegalArgumentException.class, () -> queue.deserializeNBT(registry(), bad));
        assertEquals(before, queue.serializeNBT(registry()));
    }
    @Test void invalidSecondInventoryCannotConsumeFirstInventoryDuringRefundTransfer() {
        var queue = new AERefundQueue(() -> {}); var items = inventory(AEItemKey.of(Items.IRON_INGOT), 27);
        var fluids = inventory(AEFluidKey.of(Fluids.WATER), -1);
        assertThrows(IllegalArgumentException.class, () -> queue.takeOwnership(items, fluids));
        assertEquals(27, items.getLong(AEItemKey.of(Items.IRON_INGOT))); assertTrue(queue.isEmpty());
    }
}
