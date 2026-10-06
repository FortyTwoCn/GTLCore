package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.NativeMEOutputPersistence;
import org.gtlcore.gtlcore.integration.ae2.NativeMEOutputRecipeTransfer;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEOutputPart;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import appeng.api.stacks.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class NativeGtOutputIntegrationTest {
    private static RegistryAccess registry() { return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY); }
    private static AEItemKey item(String name) { var stack = new ItemStack(Items.IRON_INGOT); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return AEItemKey.of(stack); }
    private static AEFluidKey fluid(String name) { var stack = new FluidStack(Fluids.WATER, 1); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return AEFluidKey.of(stack); }
    private static final class Part implements IMEOutputPart, IModifiableSyncOffset {
        private int time, interval;
        @Override public int getTime() { return time; }
        @Override public void setTime(int value) { time = value; }
        @Override public void returnStorage() { time = 0; }
        @Override public int getMEUpdateInterval() { return interval; }
        @Override public void setMEUpdateInterval(int value) { interval = value; }
    }
    @Test void duplicateOutputsReserveSharedCapacityInSimulationAndReturnOnlyExactComponentRemainders() {
        var key = item("native duplicate output"); var other = item("separate component output");
        var buffer = new KeyStorage(); var changed = new AtomicInteger(); buffer.setOnContentsChanged(changed::incrementAndGet);
        buffer.storage.put(key, Long.MAX_VALUE - 5);
        var request = List.of(new GenericStack(key, 4), new GenericStack(key, 4), new GenericStack(other, 9_007_199_254_740_993L));
        for (boolean simulate : new boolean[]{true, false}) {
            var left = new ArrayList<>(request);
            assertSame(left, NativeMEOutputRecipeTransfer.accept(buffer, left, simulate, Function.identity(), Function.identity()));
            assertEquals(List.of(new GenericStack(key, 3)), left);
            assertEquals(simulate ? Long.MAX_VALUE - 5 : Long.MAX_VALUE, buffer.storage.getLong(key));
            assertEquals(simulate ? 0 : 9_007_199_254_740_993L, buffer.storage.getLong(other));
            assertEquals(simulate ? 0 : 1, changed.get());
        }
    }
    @Test void failedResolutionOrRemainderCannotPartiallyAcknowledgeRecipeOutputs() {
        var key = item("retained recipe output"); var buffer = new KeyStorage(); buffer.storage.put(key, Long.MAX_VALUE - 5);
        var request = List.of(new GenericStack(key, 4), new GenericStack(key, 4));
        for (boolean resolveFailure : new boolean[]{true, false}) {
            var left = new ArrayList<>(request); var index = new AtomicInteger();
            assertThrows(IllegalArgumentException.class, () -> NativeMEOutputRecipeTransfer.accept(buffer, left, false,
                    output -> { if (resolveFailure && index.incrementAndGet() == 2) throw new IllegalArgumentException("invalid native output"); return output; },
                    output -> { if (!resolveFailure) throw new IllegalArgumentException("invalid native remainder"); return output; }));
            assertEquals(request, left); assertEquals(Long.MAX_VALUE - 5, buffer.storage.getLong(key));
        }
    }
    @Test void outputMachineItemPreservesExactNativeKeysLongAmountsAndIntegerCycleSettings() {
        var source = new KeyStorage(); var key = item("item ownership"); var water = fluid("fluid ownership");
        source.storage.put(key, 9_007_199_254_740_993L); source.storage.put(water, Long.MAX_VALUE);
        var dropped = new ItemStack(Items.STONE); var existing = new CompoundTag(); existing.putString("other_machine_data", "retained"); dropped.set(DataComponents.CUSTOM_DATA, CustomData.of(existing));
        NativeMEOutputPersistence.write(dropped, registry(), source, 278, 300);
        var restored = new KeyStorage(); var part = new Part();
        NativeMEOutputPersistence.read(dropped.get(DataComponents.CUSTOM_DATA), registry(), restored, part);
        assertEquals(source.storage, restored.storage); assertEquals(278, part.getTime()); assertEquals(300, part.returnInterval());
        assertEquals("retained", dropped.get(DataComponents.CUSTOM_DATA).copyTag().getString("other_machine_data"));
        var builder = net.minecraft.core.component.DataComponentMap.builder(); builder.set(DataComponents.CUSTOM_DATA, CustomData.of(existing));
        NativeMEOutputPersistence.writeSettings(builder, part, 300); var other = new Part(); NativeMEOutputPersistence.readSettings(builder.build().get(DataComponents.CUSTOM_DATA), other);
        assertEquals(278, other.getTime()); assertEquals(300, other.getMEUpdateInterval());
    }
    @Test void malformedDroppedBufferCannotClearExistingOwnershipOrReplaceCycleSettings() {
        var key = item("existing owned output"); var buffer = new KeyStorage(); buffer.storage.put(key, 29);
        var part = new Part(); part.setTime(278); part.setMEUpdateInterval(300);
        for (boolean wrongListType : new boolean[]{true, false}) {
            var entries = new ListTag(); if (wrongListType) entries.add(StringTag.valueOf("wrong buffer type"));
            else { var entry = new CompoundTag(); entry.put("key", key.toTagGeneric(registry())); entry.putLong("value", -1); entries.add(entry); }
            var payload = new CompoundTag(); payload.put("buffer", entries); payload.putInt("time", 3); payload.putInt("interval", 5);
            var custom = new CompoundTag(); custom.put("gtlcoreNativeMEOutput", payload);
            assertThrows(IllegalArgumentException.class, () -> NativeMEOutputPersistence.read(CustomData.of(custom), registry(), buffer, part));
            assertEquals(29, buffer.storage.getLong(key)); assertEquals(278, part.getTime()); assertEquals(300, part.getMEUpdateInterval());
        }
    }
}
