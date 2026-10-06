package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gregtechceu.gtceu.common.data.GTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeFluidInputGameTests {
    private static final long LARGE = 9_007_199_254_740_993L;
    private static FluidStack fluid() { var stack = new FluidStack(Fluids.WATER, 1); stack.set(DataComponents.CUSTOM_NAME, Component.literal("native fluid inventory identity")); return stack; }
    private static ExportOnlyAEFluidList list(MEInputHatchPartMachine machine) { return (ExportOnlyAEFluidList) machine.tank; }
    private static MEInputHatchPartMachine build(GameTestHelper helper, boolean stocking, boolean cell) {
        var pos = new BlockPos(4, 3, 5); helper.setBlock(pos, (stocking ? NativeAeGridFixtures.STOCK_FLUID : NativeAeGridFixtures.INPUT_FLUID).getBlock());
        var machine = (MEInputHatchPartMachine) helper.getBlockEntity(pos); machine.setFrontFacing(Direction.NORTH);
        ((IModifiableSyncOffset) machine).setMEUpdateInterval(1);
        helper.setBlock(pos.north(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        if (cell) {
            helper.setBlock(pos.north(2), appeng.core.definitions.AEBlocks.DRIVE.block());
            var drive = (appeng.blockentity.storage.DriveBlockEntity) helper.getBlockEntity(pos.north(2));
            drive.getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.FLUID_CELL_1K.stack());
        }
        return machine;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("native_fluid_drop".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "native_fluid_drop"));
    }
    private static PanelSyncManager menu(MEInputHatchPartMachine machine, net.minecraft.server.level.ServerPlayer player) {
        player.setPos(machine.getBlockPos().getCenter());
        var settings = new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY); var sync = new ModularSyncManager(false);
        var manager = new PanelSyncManager(sync, true); var data = new PosGuiData(player, machine.getBlockPos());
        var panel = MachineUIFactory.INSTANCE.createPanel(data, manager, settings); WidgetTree.collectSyncValues(manager, panel);
        var container = new ModularContainerMenu(79); container.construct(player, sync, settings, panel.getName(), data); player.containerMenu = container;
        return manager;
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 200)
    public static void physicalNativeFluidInputRefillsAndRetainsRefusedOldComponentStock(GameTestHelper helper) {
        var machine = build(helper, false, true); var key = AEFluidKey.of(fluid());
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOnline(), "Native fluid input did not become online"))
                .thenExecute(() -> {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory(); var source = IActionSource.empty();
            helper.assertTrue(storage.insert(key, 5500, Actionable.MODULATE, source) == 5500, "Native fluid cell rejected fixture");
            var slot = list(machine).getInventory()[15]; slot.setConfig(new GenericStack(key, 3000));
        }).thenIdle(2).thenExecute(() -> {
            var slot = list(machine).getInventory()[15]; var storage = machine.getMainNode().getGrid().getStorageService().getInventory();
            helper.assertTrue(new GenericStack(key, 3000).equals(slot.getStock()) && storage.extract(key, 10000, Actionable.SIMULATE, IActionSource.empty()) == 2500, "Native fluid input did not extract its owned buffer");
            list(machine).handleRecipeInner(IO.IN, null, new ArrayList<>(List.of(LongFluidIngredient.create(fluid(), 500))), false);
            helper.assertTrue(slot.getStock().amount() == 2500 && slot.drain(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE).isEmpty(), "Native fluid recipe or capability ignored components");
        }).thenIdle(2).thenExecute(() -> {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory();
            helper.assertTrue(list(machine).getInventory()[15].getStock().amount() == 3000 && storage.extract(key, 10000, Actionable.SIMULATE, IActionSource.empty()) == 2000, "Native refill duplicated consumed fluid");
            machine.setWorkingEnabled(false);
            var drive = (appeng.blockentity.storage.DriveBlockEntity) helper.getLevel().getBlockEntity(machine.getBlockPos().north(2)); drive.getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
        }).thenIdle(3).thenExecute(() -> {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory();
            helper.assertTrue(storage.insert(key, 1, Actionable.SIMULATE, IActionSource.empty()) == 0, "Removed native fluid cell accepts return");
            list(machine).getInventory()[15].setConfig(new GenericStack(AEFluidKey.of(Fluids.LAVA), 1000)); machine.setWorkingEnabled(true);
        }).thenIdle(2).thenExecute(() -> {
            helper.assertTrue(new GenericStack(key, 3000).equals(list(machine).getInventory()[15].getStock()), "Refused native fluid returns were erased or replaced"); machine.setWorkingEnabled(false);
        }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 200)
    public static void nativeFluidMenuLongConfigDataStickAndActualBlockItemPlacementPreserveOwnedStock(GameTestHelper helper) {
        var machine = build(helper, false, false); var owner = player(helper); var key = AEFluidKey.of(fluid());
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOnline(), "Native fluid input without storage did not connect"))
                .thenExecute(() -> {
            machine.setWorkingEnabled(false); var manager = menu(machine, owner);
            var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                packet.writeVarInt(15).writeBoolean(true); FluidStack.STREAM_CODEC.encode(packet, fluid()); manager.receiveWidgetUpdate("ae_config_set_ghost", true, 0, packet);
                packet.clear(); packet.writeVarInt(15).writeVarLong(LARGE); manager.receiveWidgetUpdate("ae_config_amount", true, 0, packet);
            } finally { packet.release(); }
            ((IModifiableSyncOffset) machine).setMEUpdateInterval(23); list(machine).getInventory()[15].setStock(new GenericStack(key, LARGE));
            var stick = GTItems.TOOL_DATA_STICK.asStack(); machine.onDataStickShiftUse(owner, stick);
            helper.setBlock(new BlockPos(10, 3, 5), NativeAeGridFixtures.INPUT_FLUID.getBlock());
            var destination = (MEInputHatchPartMachine) helper.getBlockEntity(new BlockPos(10, 3, 5)); destination.onDataStickUse(owner, stick);
            helper.assertTrue(new GenericStack(key, LARGE).equals(list(destination).getInventory()[15].getConfig()) && ((IModifiableSyncOffset) destination).getMEUpdateInterval() == 23, "Native provider-aware fluid data stick truncated configs or interval");
            helper.getLevel().destroyBlock(machine.getBlockPos(), true);
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(machine.getBlockPos()).inflate(2)).stream().filter(entity -> entity.getItem().is(NativeAeGridFixtures.INPUT_FLUID.asStack().getItem())).toList();
            helper.assertTrue(drops.size() == 1, "Native fluid destruction did not drop exactly one machine");
            var dropped = drops.getFirst().getItem().copy(); drops.getFirst().discard(); owner.closeContainer();
            var ground = helper.absolutePos(new BlockPos(7, 2, 9)); helper.getLevel().setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
            owner.setPos(ground.getCenter().add(2, 2, 0)); owner.setItemInHand(InteractionHand.MAIN_HAND, dropped);
            var hit = new BlockHitResult(ground.getCenter().add(0, 0.5, 0), Direction.UP, ground, false);
            var placed = ((BlockItem) dropped.getItem()).place(new BlockPlaceContext(new UseOnContext(owner, InteractionHand.MAIN_HAND, hit)));
            var restored = (MEInputHatchPartMachine) helper.getLevel().getBlockEntity(ground.above());
            helper.assertTrue(placed.consumesAction() && restored != null && new GenericStack(key, LARGE).equals(list(restored).getInventory()[15].getStock()) && new GenericStack(key, LARGE).equals(list(restored).getInventory()[15].getConfig()) && ((IModifiableSyncOffset) restored).getMEUpdateInterval() == 23, "Actual native BlockItem placement lost refused fluid stock or configuration");
        }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 200)
    public static void nativeFluidStockingReservationsGatesReloadAndRemovalNeverOwnVirtualCellStock(GameTestHelper helper) {
        var machine = (MEStockingHatchPartMachine) build(helper, true, true); var key = AEFluidKey.of(fluid()); var restored = new AtomicReference<MEStockingHatchPartMachine>();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOnline(), "Native fluid stocking hatch did not connect"))
                .thenExecute(() -> {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory(); storage.insert(key, 5500, Actionable.MODULATE, IActionSource.empty());
            var slots = list(machine).getInventory(); slots[0].setConfig(new GenericStack(key, 1)); slots[15].setConfig(new GenericStack(key, 1)); machine.autoIO();
            var optimized = (IOptimizedMEList) list(machine); helper.assertTrue(optimized.getMEFluidMap().getLong(fluid()) == 5500, "Duplicate stocking fluid configs counted virtual stock twice");
            var request = new ArrayList<>(List.of(LongFluidIngredient.create(fluid(), 3000), LongFluidIngredient.create(fluid(), 3000))); list(machine).handleRecipeInner(IO.IN, null, request, true);
            helper.assertTrue(request.size() == 1 && LongFluidIngredient.amount(request.getFirst()) == 500 && storage.extract(key, 10000, Actionable.SIMULATE, IActionSource.empty()) == 5500, "Native stocking simulation reused or consumed fluid");
            list(machine).handleRecipeInner(IO.IN, null, new ArrayList<>(List.of(LongFluidIngredient.create(fluid(), 500))), false);
            helper.assertTrue(optimized.getMEFluidMap().getLong(fluid()) == 5000, "Fluid recipe did not invalidate live stocking snapshot");
            slots[0].setStock(new GenericStack(key, 1)); helper.assertTrue(slots[0].drain(500, FluidAction.EXECUTE).getAmount() == 500 && slots[0].getStock() == null && optimized.getMEFluidMap().getLong(fluid()) == 4500, "Stale native fluid display became negative or hid cell extraction");
            helper.assertTrue(slots[0].drain(500, FluidAction.EXECUTE).getAmount() == 500 && optimized.getMEFluidMap().getLong(fluid()) == 4000,
                    "Extraction with an empty virtual fluid display did not refresh the network snapshot");
            helper.assertTrue(slots[15].drain(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE).isEmpty(), "Virtual native fluid capability ignored component identity");
            machine.setWorkingEnabled(false); helper.assertTrue(optimized.getMEFluidMap().isEmpty() && optimized.getMEFluidList().isEmpty() && slots[15].drain(1, FluidAction.EXECUTE).isEmpty(), "Stopped native fluid stocking still exposed inventory"); machine.setWorkingEnabled(true);
            var registries = helper.getLevel().registryAccess(); var saved = machine.saveWithoutMetadata(registries);
            var copy = (MEStockingHatchPartMachine) NativeAeGridFixtures.STOCK_FLUID.getBlockEntityType().create(machine.getBlockPos(), machine.getBlockState()); helper.getLevel().removeBlockEntity(machine.getBlockPos()); copy.loadAdditional(saved, registries); helper.getLevel().setBlockEntity(copy); restored.set(copy);
        }).thenWaitUntil(() -> helper.assertTrue(restored.get() != null && restored.get().isOnline(), "Native stocking fluid reload did not reconnect"))
                .thenExecute(() -> {
            var copy = restored.get(); helper.assertTrue(list(copy).getInventory()[15].drain(500, FluidAction.EXECUTE).getAmount() == 500 && ((IOptimizedMEList) list(copy)).getMEFluidMap().getLong(fluid()) == 3500, "Native fluid reload lost slot owners or callbacks");
            copy.setAutoPull(true); copy.autoIO(); helper.assertTrue(Arrays.stream(list(copy).getInventory()).anyMatch(slot -> slot.getConfig() != null && key.equals(slot.getConfig().what())), "Standalone native fluid auto pull failed");
            var storage = copy.getMainNode().getGrid().getStorageService().getInventory(); helper.getLevel().destroyBlock(copy.getBlockPos(), true);
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(copy.getBlockPos()).inflate(2)).stream().filter(entity -> entity.getItem().is(NativeAeGridFixtures.STOCK_FLUID.asStack().getItem())).toList();
            helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().get(DataComponents.CUSTOM_DATA).copyTag().getCompound("gtlcoreBufferedME").getCompound("slots").isEmpty() && storage.extract(key, 10000, Actionable.SIMULATE, IActionSource.empty()) == 3500, "Native stocking fluid drops duplicated virtual stock");
        }).thenSucceed();
    }
}
