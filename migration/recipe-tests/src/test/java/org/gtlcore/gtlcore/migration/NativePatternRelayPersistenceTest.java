package org.gtlcore.gtlcore.migration;

import appeng.api.stacks.*;
import appeng.util.SettingsFrom;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativePatternRelayPersistenceTest {
    private static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    @Test void nativeRelayPersistsComponentPendingOutputsAndSupplierObservationsAtLongLimits() {
        var item = new ItemStack(Items.IRON_INGOT); item.set(DataComponents.CUSTOM_NAME, Component.literal("Relay item component"));
        var fluid = new FluidStack(Fluids.WATER, 1); fluid.set(DataComponents.CUSTOM_NAME, Component.literal("Relay fluid component"));
        var expected = Map.<AEKey, Long>of(AEItemKey.of(item), Long.MAX_VALUE, AEFluidKey.of(fluid), 9_007_199_254_740_993L);
        var data = new CompoundTag(); data.putInt("mode", 1);
        for (String name : List.of("pendingOutputs", "observedSupplierStock")) {
            var entries = new ListTag(); expected.forEach((key, amount) -> entries.add(GenericStack.writeTag(REGISTRIES, new GenericStack(key, amount)))); data.put(name, entries);
        }
        var relay = NativeThroughputFixtures.RELAY.get().createPart(); relay.readFromNBT(data, REGISTRIES);
        var saved = new CompoundTag(); relay.writeToNBT(saved, REGISTRIES); assertTrue(relay.isAccessMode());
        for (String name : List.of("pendingOutputs", "observedSupplierStock")) {
            var actual = new HashMap<AEKey, Long>();
            for (var tag : saved.getList(name, Tag.TAG_COMPOUND)) { var stack = GenericStack.readTag(REGISTRIES, (CompoundTag) tag); assertNotNull(stack); assertNull(actual.put(stack.what(), stack.amount())); }
            assertEquals(expected, actual);
        }
        var restored = NativeThroughputFixtures.RELAY.get().createPart(); restored.readFromNBT(saved, REGISTRIES);
        var savedAgain = new CompoundTag(); restored.writeToNBT(savedAgain, REGISTRIES);
        for (String name : List.of("pendingOutputs", "observedSupplierStock")) {
            var actual = new HashMap<AEKey, Long>();
            for (var tag : savedAgain.getList(name, Tag.TAG_COMPOUND)) {
                var stack = GenericStack.readTag(REGISTRIES, (CompoundTag) tag); assertNotNull(stack);
                assertNull(actual.put(stack.what(), stack.amount()));
            }
            assertEquals(expected, actual);
        }
    }
    @Test void nativeRelayStreamsVisualModeAndExportsMemoryCardComponentsWithoutClobberingOtherData() {
        var relay = NativeThroughputFixtures.RELAY.get().createPart(); var data = new CompoundTag(); data.putInt("mode", 1); relay.readFromNBT(data, REGISTRIES);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), REGISTRIES);
        try {
            relay.writeToStream(buffer); var client = NativeThroughputFixtures.RELAY.get().createPart();
            assertTrue(client.readFromStream(buffer)); assertTrue(client.isAccessMode()); assertEquals(0, buffer.readableBytes());
            var visual = new CompoundTag(); relay.writeVisualStateToNBT(visual); client.readVisualStateFromNBT(visual); assertTrue(client.isAccessMode());
        } finally { buffer.release(); }
        var unrelated = new CompoundTag(); unrelated.putString("other", "Preserve another component owner");
        var builder = DataComponentMap.builder().set(DataComponents.CUSTOM_DATA, CustomData.of(unrelated));
        relay.exportSettings(SettingsFrom.MEMORY_CARD, builder); var exported = builder.build().get(DataComponents.CUSTOM_DATA).copyTag();
        assertEquals("Preserve another component owner", exported.getString("other")); assertEquals(1, exported.getInt("mode"));
    }
}
