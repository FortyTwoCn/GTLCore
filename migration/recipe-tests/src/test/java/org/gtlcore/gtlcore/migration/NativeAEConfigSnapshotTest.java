package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.gui.NativeAEConfigSnapshot;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemList;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import net.minecraft.core.BlockPos;
import appeng.api.stacks.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAEConfigSnapshotTest {
    private ExportOnlyAEItemList inventory(int size) {
        var definition = GTMultiMachines.VACUUM_FREEZER;
        var owner = new StorageMachine(new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.getBlock().defaultBlockState()), 64);
        return owner.attachTrait(new ExportOnlyAEItemList(size));
    }
    private RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)); }
    @Test void repeatedEncodingAndLaterTicksCannotEraseAChangedLongSlot() {
        var slots = inventory(2);
        var item = new ItemStack(Items.IRON_INGOT); item.set(DataComponents.CUSTOM_NAME, Component.literal("native immutable slot"));
        var key = AEItemKey.of(item);
        var config = new GenericStack[]{new GenericStack(key, 9_007_199_254_740_993L), null};
        var stock = new GenericStack[]{new GenericStack(key, 7), null};
        slots.getInventory()[0].setConfig(new GenericStack(key, Long.MAX_VALUE));
        slots.getInventory()[0].setStock(stock[0]);
        var snapshot = NativeAEConfigSnapshot.capture(slots, config, stock, false);
        assertTrue(snapshot.hasChanges()); snapshot.updateCache(config, stock);
        assertEquals(Long.MAX_VALUE, config[0].amount());
        assertFalse(NativeAEConfigSnapshot.capture(slots, config, stock, false).hasChanges());
        slots.getInventory()[0].setConfig(null); slots.getInventory()[0].setStock(null);
        var first = buffer(); var second = buffer();
        try {
            snapshot.write(first); snapshot.write(second);
            assertEquals(first, second);
            assertTrue(first.readBoolean()); assertTrue(first.readBoolean());
            assertEquals(new GenericStack(key, Long.MAX_VALUE), GenericStack.readBuffer(first));
            assertTrue(first.readBoolean()); assertEquals(new GenericStack(key, 7), GenericStack.readBuffer(first));
            assertFalse(first.readBoolean()); assertEquals(0, first.readableBytes());
        } finally { first.release(); second.release(); }
        assertTrue(NativeAEConfigSnapshot.capture(slots, config, stock, false).hasChanges());
    }
    @Test void initializationIncludesEmptySlotsAndRejectsMismatchedInventories() {
        var slots = inventory(2);
        assertTrue(NativeAEConfigSnapshot.capture(slots, new GenericStack[2], new GenericStack[2], true).hasChanges());
        assertThrows(IllegalArgumentException.class, () -> NativeAEConfigSnapshot.capture(slots, new GenericStack[1], new GenericStack[2], false));
        assertThrows(IllegalArgumentException.class, () -> NativeAEConfigSnapshot.capture(slots, new GenericStack[3], new GenericStack[3], false));
    }
    @Test void aNativeSyncHandlerMayExposeOnlyAPrefixOfTheInventory() {
        var slots = inventory(3);
        var key = AEItemKey.of(Items.IRON_INGOT);
        slots.getInventory()[0].setConfig(new GenericStack(key, Long.MAX_VALUE));
        slots.getInventory()[2].setConfig(new GenericStack(key, 42));
        var config = new GenericStack[1]; var stock = new GenericStack[1];
        var snapshot = NativeAEConfigSnapshot.capture(slots, config, stock, true);
        snapshot.updateCache(config, stock);
        var packet = buffer();
        try {
            snapshot.write(packet);
            assertTrue(packet.readBoolean()); assertTrue(packet.readBoolean());
            assertEquals(new GenericStack(key, Long.MAX_VALUE), GenericStack.readBuffer(packet));
            assertFalse(packet.readBoolean()); assertEquals(0, packet.readableBytes());
            slots.getInventory()[2].setConfig(null);
            assertFalse(NativeAEConfigSnapshot.capture(slots, config, stock, false).hasChanges());
        } finally { packet.release(); }
    }
}
