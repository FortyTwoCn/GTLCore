package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.*;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.api.recipe.ingredient.*;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory;
import com.gregtechceu.gtceu.common.data.GTItems;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.*;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.WidgetTree;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeTagStockGameTests {
    private static ItemStack item() { var value = new ItemStack(Items.IRON_INGOT); value.set(DataComponents.CUSTOM_NAME, Component.literal("native tag filter stock")); return value; }
    private static FluidStack fluid() { var value = new FluidStack(Fluids.WATER, 1); value.set(DataComponents.CUSTOM_NAME, Component.literal("native tag filter fluid")); return value; }
    private static MetaMachine build(GameTestHelper helper, boolean fluid) {
        var pos = new BlockPos(4, 3, 5); helper.setBlock(pos, (fluid ? NativeAeGridFixtures.TAG_STOCK_FLUID : NativeAeGridFixtures.TAG_STOCK_ITEM).getBlock());
        var machine = (MetaMachine) helper.getBlockEntity(pos); machine.setFrontFacing(Direction.NORTH);
        if (fluid) ((TagFilterMEStockHatchPartMachine) machine).setMEUpdateInterval(1); else ((TagFilterMEStockBusPartMachine) machine).setMEUpdateInterval(1);
        helper.setBlock(pos.north(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos.north(2), appeng.core.definitions.AEBlocks.DRIVE.block());
        var drive = (appeng.blockentity.storage.DriveBlockEntity) helper.getBlockEntity(pos.north(2));
        drive.getInternalInventory().setItemDirect(0, (fluid ? appeng.core.definitions.AEItems.FLUID_CELL_1K : appeng.core.definitions.AEItems.ITEM_CELL_1K).stack()); return machine;
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 200)
    public static void nativeItemTagsSortAndReserveRealNetworkStockAcrossReloadedSlots(GameTestHelper helper) {
        var machine = (TagFilterMEStockBusPartMachine) build(helper, false); var restored = new AtomicReference<TagFilterMEStockBusPartMachine>(); var key = AEItemKey.of(item());
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOnline(), "Tag stocking item network did not connect")).thenExecute(() -> {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory(); var source = IActionSource.empty();
            storage.insert(key, 37, Actionable.MODULATE, source); storage.insert(AEItemKey.of(Items.IRON_INGOT), 13, Actionable.MODULATE, source); storage.insert(AEItemKey.of(Items.GOLD_INGOT), 70, Actionable.MODULATE, source);
            machine.setTagWhite("c:ingots/*"); machine.setTagBlack("c:ingots/gold"); machine.setCountSort(true); machine.autoIO();
            var slots = machine.getStockingInventory().getInventory();
            helper.assertTrue(key.equals(slots[0].getConfig().what()) && slots[0].getStock().amount() == 37 && slots[1].getStock().amount() == 13 && slots[2].getConfig() == null,
                    "Native tag whitelist, blacklist, sort or component-separated display failed");
            slots[15].setConfig(new GenericStack(key, 1)); var optimized = (IOptimizedMEList) machine.getStockingInventory();
            helper.assertTrue(optimized.getMEItemMap().getLong(item()) == 37, "Tag stocking duplicate configs multiplied network quantities");
            var requested = new ArrayList<>(List.of(LongIngredient.create(item(), 20), LongIngredient.create(item(), 20)));
            machine.getStockingInventory().handleRecipeInner(IO.IN, null, requested, true);
            helper.assertTrue(requested.size() == 1 && LongIngredient.amount(requested.getFirst()) == 3 && storage.extract(key, 100, Actionable.SIMULATE, source) == 37, "Tag stocking simulation double-counted or consumed cell stock");
            slots[0].setStock(null); helper.assertTrue(slots[0].extractItem(0, 5, false).getCount() == 5 && optimized.getMEItemMap().getLong(item()) == 32, "Empty tag stocking display prevented real extraction or snapshot invalidation");
            machine.setWorkingEnabled(false); helper.assertTrue(optimized.getMEItemMap() == null && slots[15].extractItem(0, 1, false).isEmpty(), "Stopped tag bus still provided inventory"); machine.setWorkingEnabled(true);
            var registries = helper.getLevel().registryAccess(); var saved = machine.saveWithoutMetadata(registries);
            var copy = (TagFilterMEStockBusPartMachine) NativeAeGridFixtures.TAG_STOCK_ITEM.getBlockEntityType().create(machine.getBlockPos(), machine.getBlockState());
            helper.getLevel().removeBlockEntity(machine.getBlockPos()); copy.loadAdditional(saved, registries); helper.getLevel().setBlockEntity(copy); restored.set(copy);
        }).thenWaitUntil(() -> helper.assertTrue(restored.get() != null && restored.get().isOnline(), "Tag bus reload failed to reconnect")).thenExecute(() -> {
            var copy = restored.get(); helper.assertTrue(copy.getTagWhite().equals("c:ingots/*") && copy.getTagBlack().equals("c:ingots/gold") && copy.isCountSort() && copy.getMEUpdateInterval() == 1,
                    "Native tag bus SaveField reload lost configuration");
            helper.assertTrue(copy.getStockingInventory().getInventory()[0].extractItem(0, 2, false).getCount() == 2 && ((IOptimizedMEList) copy.getStockingInventory()).getMEItemMap().getLong(item()) == 30, "Reloaded tag item slots lost their owner or callbacks");
        }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 200)
    public static void nativeFluidTagViewsKeepComponentsAndActualDroppedMachinesNeverOwnCellStock(GameTestHelper helper) {
        var machine = (TagFilterMEStockHatchPartMachine) build(helper, true); var key = AEFluidKey.of(fluid());
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOnline(), "Tag stocking fluid network did not connect")).thenExecute(() -> {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory(); var source = IActionSource.empty();
            storage.insert(key, 5500, Actionable.MODULATE, source); storage.insert(AEFluidKey.of(Fluids.LAVA), 9000, Actionable.MODULATE, source);
            machine.setTagWhite("minecraft:water"); machine.setCountSort(true); machine.autoIO(); var slots = machine.getStockingInventory().getInventory();
            helper.assertTrue(key.equals(slots[0].getConfig().what()) && slots[1].getConfig() == null, "Native fluid tag filter included lava or lost water components");
            slots[15].setConfig(new GenericStack(key, 1)); var optimized = (IOptimizedMEList) machine.getStockingInventory();
            var requested = new ArrayList<>(List.of(LongFluidIngredient.create(fluid(), 3000), LongFluidIngredient.create(fluid(), 3000)));
            machine.getStockingInventory().handleRecipeInner(IO.IN, null, requested, true);
            helper.assertTrue(requested.size() == 1 && LongFluidIngredient.amount(requested.getFirst()) == 500 && optimized.getMEFluidMap().getLong(fluid()) == 5500,
                    "Tag stocking fluid simulation double-counted real storage");
            helper.assertTrue(slots[0].drain(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE).isEmpty(), "Tag fluid capability ignored components");
            slots[0].setStock(null); helper.assertTrue(slots[0].drain(500, FluidAction.EXECUTE).getAmount() == 500 && optimized.getMEFluidMap().getLong(fluid()) == 5000, "Tag fluid extraction did not invalidate an empty display");
            machine.setWorkingEnabled(false); helper.assertTrue(optimized.getMEFluidMap().isEmpty() && slots[15].drain(1, FluidAction.EXECUTE).isEmpty(), "Stopped tag hatch still provided fluid"); machine.setWorkingEnabled(true);
            helper.getLevel().destroyBlock(machine.getBlockPos(), true);
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(machine.getBlockPos()).inflate(2)).stream().filter(entity -> entity.getItem().is(NativeAeGridFixtures.TAG_STOCK_FLUID.asStack().getItem())).toList();
            helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().get(DataComponents.CUSTOM_DATA).copyTag().getCompound("gtlcoreBufferedME").getCompound("slots").isEmpty() && storage.extract(key, 10000, Actionable.SIMULATE, source) == 5000,
                    "Tag fluid destruction inserted or serialized virtual stock");
            var dropped = drops.getFirst().getItem().copy(); drops.getFirst().discard(); var player = FakePlayerFactory.getMinecraft(helper.getLevel());
            var ground = helper.absolutePos(new BlockPos(7, 2, 9)); helper.getLevel().setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState()); player.setPos(ground.getCenter().add(2, 2, 0)); player.setItemInHand(InteractionHand.MAIN_HAND, dropped);
            var hit = new BlockHitResult(ground.getCenter().add(0, 0.5, 0), Direction.UP, ground, false);
            helper.assertTrue(((BlockItem) dropped.getItem()).place(new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit))).consumesAction(), "Tag hatch actual BlockItem placement failed");
            var placed = (TagFilterMEStockHatchPartMachine) helper.getLevel().getBlockEntity(ground.above());
            helper.assertTrue(placed.getTagWhite().equals("minecraft:water") && placed.isCountSort() && placed.getMEUpdateInterval() == 1 && Arrays.stream(placed.getStockingInventory().getInventory()).allMatch(slot -> slot.getStock() == null), "Placed tag hatch lost configuration or acquired virtual cell stock");
        }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeTagPopupsAndDataStickApplyServerSettingsAndItemPickupConsumesRealStock(GameTestHelper helper) {
        var host = (TagFilterMEStockBusPartMachine) build(helper, false);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(host.isOnline(), "Native tag GUI network did not connect")).thenExecute(() -> {
            var key = AEItemKey.of(item()); var storage = host.getMainNode().getGrid().getStorageService().getInventory(); storage.insert(key, 37, Actionable.MODULATE, IActionSource.empty());
            var player = FakePlayerFactory.getMinecraft(helper.getLevel()); player.setPos(host.getBlockPos().getCenter());
            var settings = new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY); var sync = new ModularSyncManager(false); var manager = new PanelSyncManager(sync, true);
            var data = new PosGuiData(player, host.getBlockPos()); var panel = MachineUIFactory.INSTANCE.createPanel(data, manager, settings); WidgetTree.collectSyncValues(manager, panel);
            var menu = new ModularContainerMenu(83); menu.construct(player, sync, settings, panel.getName(), data); player.containerMenu = menu;
            var popup = (PanelSyncHandler) manager.findPanelHandlerNullable("gtlcoreTagFilter"); popup.openPanel(); var filters = sync.getPanelSyncManager("gtlcoreTagFilterPanel");
            var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                brachy.modularui.utils.NetworkUtils.writeStringSafe(packet, "c:ingots/*", Short.MAX_VALUE - 74); filters.receiveWidgetUpdate("tagWhitelist:0", false, 0, packet);
                packet.clear(); brachy.modularui.utils.NetworkUtils.writeStringSafe(packet, "c:ingots/gold", Short.MAX_VALUE - 74); filters.receiveWidgetUpdate("tagBlacklist:0", false, 0, packet);
                packet.clear(); packet.writeBoolean(true); manager.receiveWidgetUpdate("tagCountSort:0", false, 0, packet);
                helper.assertTrue(host.getTagWhite().equals("c:ingots/*") && host.getTagBlack().equals("c:ingots/gold") && host.isCountSort() && menu.slots.size() == 36,
                        "Native tag popup C2S settings failed or exposed consumable ghost slots");
                host.setMEUpdateInterval(23); var stick = GTItems.TOOL_DATA_STICK.asStack(); host.onDataStickShiftUse(player, stick);
                var pos = new BlockPos(10, 3, 5); helper.setBlock(pos, NativeAeGridFixtures.TAG_STOCK_ITEM.getBlock()); var target = (TagFilterMEStockBusPartMachine) helper.getBlockEntity(pos); target.onDataStickUse(player, stick);
                helper.assertTrue(target.getTagWhite().equals(host.getTagWhite()) && target.getTagBlack().equals(host.getTagBlack()) && target.isCountSort() && target.getMEUpdateInterval() == 23, "Native tag data stick lost filter, sorting or refresh interval");
                player.containerMenu.setCarried(ItemStack.EMPTY); packet.clear(); packet.writeVarInt(0); manager.receiveWidgetUpdate("ae_stock_pickup", true, 0, packet);
                helper.assertTrue(player.containerMenu.getCarried().getCount() == 37 && storage.extract(key, 100, Actionable.SIMULATE, IActionSource.empty()) == 0, "Tag UI stock pickup copied its display without extracting real cell contents");
            } finally { packet.release(); popup.closePanelInternal(); player.containerMenu.setCarried(ItemStack.EMPTY); player.closeContainer(); }
        }).thenSucceed();
    }
}
