package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.api.recipe.ingredient.*;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.integration.ae2.slot.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Buffered tests use real GT slots; the bounded MEStorage fixture isolates long transaction arithmetic. */
class AEItemIntegrationTest {
    private static final long LARGE = 9_007_199_254_740_993L;
    private static ItemStack named() {
        var item = new ItemStack(Items.IRON_INGOT); item.set(DataComponents.CUSTOM_NAME, Component.literal("AE item components")); return item;
    }
    private static StorageMachine owner() {
        var definition = GTMultiMachines.VACUUM_FREEZER;
        return new StorageMachine(new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO, definition.getBlock().defaultBlockState()), 64);
    }
    private static ExportOnlyAEItemList buffered(GenericStack... stock) {
        var list = owner().attachTrait(new ExportOnlyAEItemList(stock.length));
        for (int i = 0; i < stock.length; i++) list.getInventory()[i].setStock(stock[i]);
        return list;
    }
    private static ArrayList<SizedIngredient> left(SizedIngredient... inputs) { return new ArrayList<>(List.of(inputs)); }
    @Test void actualBufferedNativeSlotsConsumeLongComponentIngredientsOnceWithoutChangingTheRecipe() {
        var key = AEItemKey.of(named()); var list = buffered(new GenericStack(key, LARGE));
        var input = LongIngredient.create(named(), 3_000_000_000L); var changes = new AtomicInteger();
        list.getInventory()[0].setOnContentsChanged(changes::incrementAndGet);
        assertTrue(list.handleRecipeInner(IO.IN, null, left(input), false).isEmpty());
        assertEquals(LARGE - 3_000_000_000L, list.getInventory()[0].getStock().amount());
        assertEquals(3_000_000_000L, LongIngredient.amount(input)); assertEquals(1, changes.get());
        assertEquals(key, list.getInventory()[0].getStock().what());
    }
    @Test void bufferedSimulationReservesOverlappingLongIngredientsWithoutNotificationsOrMutation() {
        var key = AEItemKey.of(named()); var list = buffered(new GenericStack(key, 5_000_000_000L));
        var changes = new AtomicInteger(); list.getInventory()[0].setOnContentsChanged(changes::incrementAndGet);
        var remaining = left(LongIngredient.create(named(), 3_000_000_000L), LongIngredient.create(named(), 3_000_000_000L));
        list.handleRecipeInner(IO.IN, null, remaining, true);
        assertEquals(1, remaining.size()); assertEquals(1_000_000_000L, LongIngredient.amount(remaining.getFirst()));
        assertEquals(5_000_000_000L, list.getInventory()[0].getStock().amount()); assertEquals(0, changes.get());
    }
    @Test void nativeQuantityMapAggregatesIdenticalComponentsAndKeepsDifferentComponentsSeparate() {
        var key = AEItemKey.of(named()); var plain = AEItemKey.of(Items.IRON_INGOT);
        var list = buffered(new GenericStack(key, LARGE), new GenericStack(key, 7), new GenericStack(plain, 13));
        var optimized = (IOptimizedMEList) list;
        assertEquals(2, optimized.getMEItemMap().size()); assertEquals(LARGE + 7, optimized.getMEItemMap().getLong(named()));
        assertEquals(13, optimized.getMEItemMap().getLong(new ItemStack(Items.IRON_INGOT)));
        list.getInventory()[0].setStock(new GenericStack(key, 1));
        assertEquals(8, optimized.getMEItemMap().getLong(named()));
        assertEquals(2, list.getContents().size());
    }
    private static class Network implements MEStorage {
        final KeyCounter quantities = new KeyCounter(); int modulated;
        Network(AEItemKey key, long amount) { quantities.set(key, amount); }
        @Override public Component getDescription() { return Component.literal("bounded item MEStorage fixture"); }
        @Override public void getAvailableStacks(KeyCounter result) { result.addAll(quantities); }
        @Override public long extract(AEKey key, long amount, Actionable action, IActionSource source) {
            MEStorage.checkPreconditions(key, amount, action, source); long extracted = Math.min(amount, quantities.get(key));
            if (action == Actionable.MODULATE) { quantities.remove(key, extracted); modulated++; }
            return extracted;
        }
    }
    @Test void networkDuplicateConfigurationsReserveEachKeyOnceAndPreservePartialLongRemainders() {
        var key = AEItemKey.of(named()); var network = new Network(key, LARGE);
        var slots = new ExportOnlyAEItemSlot[]{new ExportOnlyAEItemSlot(new GenericStack(key, 1), new GenericStack(key, LARGE)),
                new ExportOnlyAEItemSlot(new GenericStack(key, 1), new GenericStack(key, LARGE))};
        var changes = new AtomicInteger(); Runnable callback = changes::incrementAndGet;
        for (var slot : slots) slot.setOnContentsChanged(callback);
        var simulated = left(LongIngredient.create(named(), LARGE - 3), LongIngredient.create(named(), 7));
        AEItemRecipeHandler.handleNetwork(IO.IN, null, simulated, true, slots, null, network, IActionSource.empty());
        assertEquals(1, simulated.size()); assertEquals(4, LongIngredient.amount(simulated.getFirst()));
        assertEquals(LARGE, network.quantities.get(key)); assertEquals(0, network.modulated); assertEquals(0, changes.get());
        var actual = left(LongIngredient.create(named(), LARGE - 3), LongIngredient.create(named(), 7));
        AEItemRecipeHandler.handleNetwork(IO.IN, null, actual, false, slots, null, network, IActionSource.empty());
        assertEquals(4, LongIngredient.amount(actual.getFirst())); assertEquals(0, network.quantities.get(key));
        assertEquals(1, changes.get()); for (var slot : slots) assertNull(slot.getStock());
    }
}
