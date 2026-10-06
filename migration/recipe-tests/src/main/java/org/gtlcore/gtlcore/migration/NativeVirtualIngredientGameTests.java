package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;
import org.gtlcore.gtlcore.common.machine.VirtualIngredientSupplyMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.core.definitions.AEBlocks;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeVirtualIngredientGameTests {
    private static ItemStack item() { var value = new ItemStack(Items.DIAMOND, 7); value.set(DataComponents.CUSTOM_NAME, Component.literal("real virtual supply input")); return value; }
    private static FluidStack fluid() { var value = new FluidStack(Fluids.WATER, 750); value.set(DataComponents.CUSTOM_NAME, Component.literal("real virtual supply fluid")); return value; }
    private static VirtualIngredientSupplyMachine place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, NativeAePatternFixtures.VIRTUAL_SUPPLY.getBlock().defaultBlockState());
        var machine = (VirtualIngredientSupplyMachine) helper.getBlockEntity(pos); machine.setFrontFacing(Direction.NORTH); return machine;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void actualVirtualSupplierPublishesSealedTokensAndInvalidatesRealAeStorageAfterInventoryChanges(GameTestHelper helper) {
        var machine = place(helper, new BlockPos(4, 4, 4));
        helper.getLevel().setBlockAndUpdate(machine.getBlockPos().north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        machine.getInventory().storage.setStackInSlot(286, item()); machine.getInventory().storage.setStackInSlot(287, VirtualIngredientBehavior.wrap(fluid()));
        var token = AEItemKey.of(VirtualIngredientBehavior.wrap(item())); var fluidToken = AEItemKey.of(VirtualIngredientBehavior.wrap(fluid()));
        long amount = 9_007_199_254_740_993L;
        var restored = new AtomicReference<VirtualIngredientSupplyMachine>();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOnline(), "Actual virtual supplier has no native powered AE network"))
                .thenExecute(() -> {
                    var storage = machine.getMainNode().getGrid().getStorageService();
                    helper.assertTrue(storage.getInventory().extract(token, amount, Actionable.MODULATE, IActionSource.empty()) == amount &&
                            storage.getInventory().extract(fluidToken, amount, Actionable.MODULATE, IActionSource.empty()) == amount &&
                            storage.getInventory().extract(AEItemKey.of(item()), 1, Actionable.SIMULATE, IActionSource.empty()) == 0 &&
                            machine.getInventory().storage.getStackInSlot(286).getCount() == 7,
                            "Actual virtual supplier exposed or consumed its real backing material");
                    machine.getInventory().storage.setStackInSlot(286, ItemStack.EMPTY);
                }).thenWaitUntil(() -> helper.assertTrue(machine.getMainNode().getGrid().getStorageService().getInventory()
                        .extract(token, 1, Actionable.SIMULATE, IActionSource.empty()) == 0 &&
                        machine.getMainNode().getGrid().getStorageService().getCachedInventory().get(token) == 0,
                        "Real AE storage retained a removed virtual key in its provider or cached inventory"))
                .thenExecute(() -> {
                    machine.getInventory().storage.setStackInSlot(286, item());
                    var registry = helper.getLevel().registryAccess(); var saved = machine.saveWithoutMetadata(registry);
                    var copy = (VirtualIngredientSupplyMachine) NativeAePatternFixtures.VIRTUAL_SUPPLY.getBlockEntityType().create(machine.getBlockPos(), machine.getBlockState());
                    helper.getLevel().removeBlockEntity(machine.getBlockPos()); copy.loadAdditional(saved, registry); helper.getLevel().setBlockEntity(copy); restored.set(copy);
                }).thenWaitUntil(() -> helper.assertTrue(restored.get().isOnline() && restored.get().getMainNode().getGrid().getStorageService().getInventory()
                        .extract(fluidToken, amount, Actionable.SIMULATE, IActionSource.empty()) == amount, "Native supplier reload lost its node or virtual-fluid component key"))
                .thenExecute(() -> helper.assertTrue(restored.get().getInventory().storage.getStackInSlot(286).getCount() == 7 &&
                        AEFluidKey.of(fluid()).equals(VirtualIngredientBehavior.payloadFluidKey(restored.get().getInventory().storage.getStackInSlot(287))),
                        "Native supplier reload lost its final real inventory slots")).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeVirtualSupplierMenuRetainsAllSlotsAndDropsBackingItemsOnlyOnce(GameTestHelper helper) {
        var machine = place(helper, new BlockPos(4, 4, 4));
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()); player.setPos(machine.getBlockPos().getCenter());
        var settings = new brachy.modularui.screen.UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
        var sync = new brachy.modularui.value.sync.ModularSyncManager(false);
        var manager = new brachy.modularui.value.sync.PanelSyncManager(sync, true);
        var data = new brachy.modularui.factory.PosGuiData(player, machine.getBlockPos());
        var panel = com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory.INSTANCE.createPanel(data, manager, settings);
        brachy.modularui.widget.WidgetTree.collectSyncValues(manager, panel);
        var menu = new brachy.modularui.screen.ModularContainerMenu(49); menu.construct(player, sync, settings, panel.getName(), data);
        var last = manager.findSyncHandler("virtualSupplySlot", 287, brachy.modularui.value.sync.ItemSlotSyncHandler.class).getSlot();
        helper.assertTrue(menu.slots.size() == 324 && last.getSlotIndex() == 287 && last.isActive(), "Native virtual-supply menu lost an inventory slot or disabled its last real slot");
        menu.setCarried(item()); menu.clicked(menu.slots.indexOf(last), 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && machine.getInventory().getStackInSlot(287).getCount() == 7,
                "Vanilla native menu click rejected the virtual supplier's final real slot");
        var item = NativeAePatternFixtures.VIRTUAL_SUPPLY.asStack(); machine.saveToItem(item, helper.getLevel().registryAccess());
        var copy = place(helper, new BlockPos(7, 4, 4)); copy.applyComponentsFromItemStack(item);
        helper.assertTrue(copy.getInventory().getStackInSlot(287).getCount() == 7 && copy.getInventory().getSlots() == 288,
                "Native machine item components truncated the 288-slot inventory");
        copy.getInventory().storage.setStackInSlot(287, ItemStack.EMPTY);
        var drop = NativeAePatternFixtures.VIRTUAL_SUPPLY.asStack();
        machine.modifyDrops(new java.util.ArrayList<>(java.util.List.of(drop))); machine.saveToItem(drop, helper.getLevel().registryAccess());
        helper.getLevel().setBlockAndUpdate(machine.getBlockPos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        var restored = place(helper, new BlockPos(10, 4, 4)); restored.applyComponentsFromItemStack(drop);
        int diamonds = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(machine.getBlockPos()).inflate(2)).stream()
                .filter(entity -> entity.getItem().is(Items.DIAMOND)).mapToInt(entity -> entity.getItem().getCount()).sum();
        helper.assertTrue(diamonds == 7 && restored.getInventory().getStackInSlot(287).isEmpty(),
                "Native machine break/drop/item placement duplicated its backing inventory");
        helper.succeed();
    }
}
