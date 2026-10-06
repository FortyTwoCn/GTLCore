package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeStockItemGameTests {
    private static ItemStack item() { var stack = new ItemStack(Items.IRON_INGOT); stack.set(DataComponents.CUSTOM_NAME, Component.literal("native static stocking owner")); return stack; }
    private static ExportOnlyAEItemList list(MEStockingBusPartMachine machine) { return (ExportOnlyAEItemList) machine.getInventory(); }
    private static MEStockingBusPartMachine build(GameTestHelper helper) {
        var pos = new BlockPos(4, 3, 5); helper.setBlock(pos, NativeAeGridFixtures.STOCK_ITEM.getBlock());
        var machine = (MEStockingBusPartMachine) helper.getBlockEntity(pos); machine.setFrontFacing(Direction.NORTH);
        ((IModifiableSyncOffset) machine).setMEUpdateInterval(1);
        helper.setBlock(pos.north(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos.north(2), appeng.core.definitions.AEBlocks.DRIVE.block());
        var drive = (appeng.blockentity.storage.DriveBlockEntity) helper.getBlockEntity(pos.north(2));
        drive.getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack()); return machine;
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 200)
    public static void nativeStaticOwnerStockingReservesDuplicateConfigsAndRestoresWorkingSlotOwnersOnReload(GameTestHelper helper) {
        var initial = build(helper); var restored = new AtomicReference<MEStockingBusPartMachine>(); var key = AEItemKey.of(item());
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(initial.isOnline(), "Native GT stocking bus did not connect"))
                .thenExecute(() -> {
            var storage = initial.getMainNode().getGrid().getStorageService().getInventory(); var source = IActionSource.empty();
            helper.assertTrue(storage.insert(key, 37, Actionable.MODULATE, source) == 37, "Native stocking test cell rejected stock");
            var slots = list(initial).getInventory(); slots[0].setConfig(new GenericStack(key, 1)); slots[15].setConfig(new GenericStack(key, 1)); initial.autoIO();
            var optimized = (IOptimizedMEList) list(initial);
            helper.assertTrue(optimized.getMEItemMap().getLong(item()) == 37, "Duplicate native stocking configs multiplied available ME stock");
            var simulated = new ArrayList<>(List.of(LongIngredient.create(item(), 20), LongIngredient.create(item(), 20)));
            list(initial).handleRecipeInner(IO.IN, null, simulated, true);
            helper.assertTrue(simulated.size() == 1 && LongIngredient.amount(simulated.getFirst()) == 3 && storage.extract(key, 100, Actionable.SIMULATE, source) == 37,
                    "Native static stocking simulation reused or consumed real cell stock");
            list(initial).handleRecipeInner(IO.IN, null, new ArrayList<>(List.of(LongIngredient.create(item(), 5))), false);
            helper.assertTrue(optimized.getMEItemMap().getLong(item()) == 32, "Native recipe extraction did not refresh stock snapshots");
            slots[0].setStock(new GenericStack(key, 1));
            helper.assertTrue(slots[0].extractItem(0, 5, false).getCount() == 5 && slots[0].getStock() == null && storage.extract(key, 100, Actionable.SIMULATE, source) == 27,
                    "Stale native slot display produced negative stock or hid actual network extraction");
            helper.assertTrue(optimized.getMEItemMap().getLong(item()) == 27 && slots[0].extractItem(0, 5, false).getCount() == 5 &&
                    optimized.getMEItemMap().getLong(item()) == 22, "Extraction with an empty virtual display did not refresh the network snapshot");
            initial.setWorkingEnabled(false);
            helper.assertTrue(optimized.getMEItemMap() == null && slots[15].extractItem(0, 1, false).isEmpty(), "Stopped native stocking bus still exposed inventory");
            initial.setWorkingEnabled(true);
            var registries = helper.getLevel().registryAccess(); var saved = initial.saveWithoutMetadata(registries);
            var copy = (MEStockingBusPartMachine) NativeAeGridFixtures.STOCK_ITEM.getBlockEntityType().create(initial.getBlockPos(), initial.getBlockState());
            helper.getLevel().removeBlockEntity(initial.getBlockPos()); copy.loadAdditional(saved, registries); helper.getLevel().setBlockEntity(copy); restored.set(copy);
        }).thenWaitUntil(() -> helper.assertTrue(restored.get() != null && restored.get().isOnline(), "Saved native stocking bus did not reconnect"))
                .thenExecute(() -> {
            var copy = restored.get(); var slots = list(copy).getInventory();
            helper.assertTrue(((IModifiableSyncOffset) copy).getMEUpdateInterval() == 1 && key.equals(slots[15].getConfig().what()), "Native stocking reload lost its interval or component configuration");
            helper.assertTrue(slots[15].extractItem(0, 2, false).getCount() == 2 && ((IOptimizedMEList) list(copy)).getMEItemMap().getLong(item()) == 20,
                    "Decoded native stocking slot lost its static owner or config callbacks");
        }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 200)
    public static void standaloneNativeAutoPullAndActualDropKeepVirtualStockInTheRealCell(GameTestHelper helper) {
        var machine = build(helper); var key = AEItemKey.of(item());
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOnline(), "Native standalone stocking bus did not connect"))
                .thenExecute(() -> {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory(); var source = IActionSource.empty();
            storage.insert(key, 11, Actionable.MODULATE, source); machine.setAutoPull(true); machine.autoIO();
            helper.assertTrue(Arrays.stream(list(machine).getInventory()).anyMatch(slot -> slot.getConfig() != null && key.equals(slot.getConfig().what())),
                    "Native default auto-pull predicate blocked the authorized standalone stocking behavior");
            helper.getLevel().destroyBlock(machine.getBlockPos(), true);
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(machine.getBlockPos()).inflate(2)).stream()
                    .filter(entity -> entity.getItem().is(NativeAeGridFixtures.STOCK_ITEM.asStack().getItem())).toList();
            helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().get(DataComponents.CUSTOM_DATA).copyTag().getCompound("gtlcoreBufferedME").getCompound("slots").isEmpty(),
                    "Native stocking drop serialized virtual stock as owned inventory");
            helper.assertTrue(storage.extract(key, 100, Actionable.SIMULATE, source) == 11 &&
                    helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(machine.getBlockPos()).inflate(2)).stream().noneMatch(entity -> entity.getItem().is(Items.IRON_INGOT)),
                    "Native stocking removal duplicated its live network views");
        }).thenSucceed();
    }
}
