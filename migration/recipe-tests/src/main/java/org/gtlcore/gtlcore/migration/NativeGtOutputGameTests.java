package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.*;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.mixin.gtmt.MEOutputPartMachineAccessor;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.*;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.core.definitions.*;
import appeng.blockentity.storage.DriveBlockEntity;
import brachy.modularui.value.sync.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import io.netty.buffer.Unpooled;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeGtOutputGameTests {
    private static final long LARGE = 9_007_199_254_740_993L;
    private static final List<MachineEntry<MachineDefinition>> DEFINITIONS = List.of(NativeGtOutputFixtures.ITEM, NativeGtOutputFixtures.FLUID, NativeGtOutputFixtures.DUAL);
    private static ItemStack item() { var value = new ItemStack(Items.IRON_INGOT); value.set(DataComponents.CUSTOM_NAME, Component.literal("native GT output components")); return value; }
    private static FluidStack fluid() { var value = new FluidStack(Fluids.WATER, 1); value.set(DataComponents.CUSTOM_NAME, Component.literal("native GT fluid output components")); return value; }
    private static KeyStorage ledger(MetaMachine machine, boolean fluid) {
        if (machine instanceof IMENativeOutputBuffer owner) return owner.gtlcore$getOutputBuffer();
        var owner = (MEOutputPartMachineAccessor) machine; return fluid ? owner.gtlcore$getInternalTankBuffer() : owner.gtlcore$getInternalBuffer();
    }
    private static boolean items(int type) { return type != 1; }
    private static boolean fluids(int type) { return type != 0; }
    private static void acceptLongOutput(MetaMachine machine, boolean fluid, GameTestHelper helper) {
        var buffer = ledger(machine, fluid); var key = fluid ? AEFluidKey.of(fluid()) : AEItemKey.of(item());
        long amount = fluid ? Long.MAX_VALUE : LARGE;
        for (boolean simulate : new boolean[]{true, false}) {
            if (fluid) {
                var handler = machine.getAllTraits().stream().filter(trait -> trait instanceof NotifiableFluidTank).map(trait -> (NotifiableFluidTank) trait).findFirst().orElseThrow();
                var left = new ArrayList<>(List.of(LongFluidIngredient.create(fluid(), amount)));
                handler.handleRecipeInner(IO.OUT, null, left, simulate);
                helper.assertTrue(left.isEmpty(), "Native fluid output refused available long capacity");
            } else {
                var handler = machine.getAllTraits().stream().filter(trait -> trait instanceof NotifiableItemStackHandler).map(trait -> (NotifiableItemStackHandler) trait).findFirst().orElseThrow();
                var left = new ArrayList<>(List.of(LongIngredient.create(item(), amount)));
                handler.handleRecipeInner(IO.OUT, null, left, simulate);
                helper.assertTrue(left.isEmpty(), "Native item output refused available long capacity");
            }
            helper.assertTrue(buffer.storage.getLong(key) == (simulate ? 0 : amount), "Native output truncated a long quantity or changed ownership during simulation");
        }
        buffer.storage.clear();
    }
    private static void output(MetaMachine machine, boolean fluid, long first, long second, boolean simulate, GameTestHelper helper) {
        if (fluid) {
            var handler = machine.getAllTraits().stream().filter(trait -> trait instanceof NotifiableFluidTank).map(trait -> (NotifiableFluidTank) trait).findFirst().orElseThrow();
            var left = new ArrayList<>(List.of(LongFluidIngredient.create(fluid(), first), LongFluidIngredient.create(fluid(), second)));
            handler.handleRecipeInner(IO.OUT, null, left, simulate);
            helper.assertTrue(left.size() == 1 && LongFluidIngredient.amount(left.getFirst()) == 3 && AEFluidKey.of(left.getFirst().getFluids()[0]).equals(AEFluidKey.of(fluid())), "Native fluid handler lost or re-rolled rejected long output");
        } else {
            var handler = machine.getAllTraits().stream().filter(trait -> trait instanceof NotifiableItemStackHandler).map(trait -> (NotifiableItemStackHandler) trait).findFirst().orElseThrow();
            var left = new ArrayList<>(List.of(LongIngredient.create(item(), first), LongIngredient.create(item(), second)));
            handler.handleRecipeInner(IO.OUT, null, left, simulate);
            helper.assertTrue(left.size() == 1 && LongIngredient.amount(left.getFirst()) == 3 && AEItemKey.of(left.getFirst().getItems()[0]).equals(AEItemKey.of(item())), "Native item handler lost rejected long output or its components");
        }
    }
    private static List<MetaMachine> place(GameTestHelper helper, boolean cells) {
        var result = new ArrayList<MetaMachine>();
        for (int i = 0; i < 3; i++) {
            var pos = new BlockPos(3 + i * 5, 4, 4); helper.setBlock(pos, DEFINITIONS.get(i).getBlock());
            var host = (MetaMachine) helper.getBlockEntity(pos); host.setFrontFacing(Direction.NORTH); result.add(host);
            helper.setBlock(pos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block());
            if (cells) { helper.setBlock(pos.north(2), AEBlocks.DRIVE.block()); var drive = (DriveBlockEntity) helper.getBlockEntity(pos.north(2));
                drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack()); drive.getInternalInventory().setItemDirect(1, AEItems.FLUID_CELL_1K.stack()); }
        }
        return result;
    }
    private static void cycle(MetaMachine host) {
        try { var method = host.getClass().getDeclaredMethod("autoIO"); method.setAccessible(true); method.invoke(host); }
        catch (ReflectiveOperationException error) { throw new AssertionError("Actual native output tick failed", error); }
    }
    private static PanelSyncManager menu(GameTestHelper helper, MetaMachine host) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel()); player.setPos(host.getBlockPos().getCenter());
        var settings = new brachy.modularui.screen.UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
        var sync = new ModularSyncManager(false); var manager = new PanelSyncManager(sync, true); var data = new brachy.modularui.factory.PosGuiData(player, host.getBlockPos());
        var panel = com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory.INSTANCE.createPanel(data, manager, settings);
        brachy.modularui.widget.WidgetTree.collectSyncValues(manager, panel);
        var container = new brachy.modularui.screen.ModularContainerMenu(47); container.construct(player, sync, settings, panel.getName(), data); return manager;
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 200)
    public static void nativeGtAndGtmtOutputHandlersReserveDuplicateKeysAndRetainRejectedLongComponents(GameTestHelper helper) {
        var hosts = place(helper, false);
        helper.startSequence().thenWaitUntil(() -> hosts.forEach(host -> helper.assertTrue(((IGridConnectedMachine) host).isOnline(), "Native output handler fixture has no AE node")))
                .thenExecute(() -> {
                    for (int i = 0; i < 3; i++) {
                        var host = hosts.get(i); ((com.gregtechceu.gtceu.api.capability.IControllable) host).setWorkingEnabled(false);
                        for (boolean fluid : new boolean[]{false, true}) {
                            if (fluid ? !fluids(i) : !items(i)) continue;
                            acceptLongOutput(host, fluid, helper);
                            var key = fluid ? AEFluidKey.of(fluid()) : AEItemKey.of(item()); var buffer = ledger(host, fluid);
                            buffer.storage.put(key, Long.MAX_VALUE - 5); output(host, fluid, 4, 4, true, helper);
                            helper.assertTrue(buffer.storage.getLong(key) == Long.MAX_VALUE - 5, "Native output simulation consumed storage");
                            output(host, fluid, 4, 4, false, helper); helper.assertTrue(buffer.storage.getLong(key) == Long.MAX_VALUE, "Native output overflow acknowledged refused quantities");
                        }
                    }
                }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeGtOutputCyclesAboveByteRangeRespectPauseAndManualServerReturnOverridesOnlyOutputCall(GameTestHelper helper) {
        var hosts = place(helper, true);
        helper.startSequence().thenWaitUntil(() -> hosts.forEach(host -> helper.assertTrue(((IGridConnectedMachine) host).isOnline(), "Native output cycle has no AE node")))
                .thenExecute(() -> {
                    for (int i = 0; i < 3; i++) {
                        var host = hosts.get(i); var part = (IMEOutputPart) host; var control = (com.gregtechceu.gtceu.api.capability.IControllable) host;
                        ((IModifiableSyncOffset) host).setMEUpdateInterval(300); part.setTime(299); control.setWorkingEnabled(false);
                        if (items(i)) ledger(host, false).storage.put(AEItemKey.of(item()), 19);
                        if (fluids(i)) ledger(host, true).storage.put(AEFluidKey.of(fluid()), 750);
                        cycle(host); helper.assertTrue(part.getTime() == 299, "Paused native output advanced its return cycle"); control.setWorkingEnabled(true);
                        cycle(host); helper.assertTrue(part.getTime() == 0, "Native output cycle wrapped through a byte or ignored configured interval");
                        part.setTime(277); var menu = menu(helper, host); var ready = menu.findSyncHandler("gtlcoreMEOutputReady", BooleanSyncValue.class); ready.updateCacheFromSource(true);
                        helper.assertTrue(!ready.getBoolValue() && !ready.isAllowC2S(), "Native output readiness is editable from the client");
                        var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
                        try { menu.receiveWidgetUpdate("gtlcoreMEOutputReturn", true, 0, packet); } finally { packet.release(); }
                        ready.updateCacheFromSource(true); helper.assertTrue(ready.getBoolValue() && part.getTime() == 0, "Manual return C2S action did not reset the native output cycle");
                        helper.assertTrue(((IGridConnectedMachine) host).shouldSyncME() == (host.getOffsetTimer() % 300 == 0), "Manual output readiness changed the shared native refresh query"); cycle(host);
                        var storage = ((IGridConnectedMachine) host).getMainNode().getGrid().getStorageService().getInventory();
                        if (items(i)) helper.assertTrue(ledger(host, false).isEmpty() && storage.extract(AEItemKey.of(item()), 20, Actionable.SIMULATE, IActionSource.empty()) == 19, "Native manual item return was delayed or duplicated");
                        if (fluids(i)) helper.assertTrue(ledger(host, true).isEmpty() && storage.extract(AEFluidKey.of(fluid()), 1000, Actionable.SIMULATE, IActionSource.empty()) == 750, "Native manual fluid return lost components or quantities");
                    }
                }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeGtOutputLongBuffersAndCycleSurviveReloadActualDestructionAndBlockItemPlacement(GameTestHelper helper) {
        var hosts = place(helper, true);
        helper.startSequence().thenWaitUntil(() -> hosts.forEach(host -> helper.assertTrue(((IGridConnectedMachine) host).isOnline(), "Native output drop fixture has no AE node")))
                .thenExecute(() -> {
                    for (int i = 0; i < 3; i++) {
                        var host = hosts.get(i); var part = (IMEOutputPart) host;
                        ((com.gregtechceu.gtceu.api.capability.IControllable) host).setWorkingEnabled(false); ((IModifiableSyncOffset) host).setMEUpdateInterval(300); part.setTime(278);
                        if (items(i)) ledger(host, false).storage.put(AEItemKey.of(item()), LARGE);
                        if (fluids(i)) ledger(host, true).storage.put(AEFluidKey.of(fluid()), Long.MAX_VALUE);
                        var definition = DEFINITIONS.get(i); var saved = host.saveWithoutMetadata(helper.getLevel().registryAccess());
                        var storage = ((IGridConnectedMachine) host).getMainNode().getGrid();
                        helper.assertTrue(storage != null, "Native destruction fixture lost the original AE storage grid");
                        var copy = (MetaMachine) definition.getBlockEntityType().create(host.getBlockPos(), host.getBlockState()); helper.getLevel().removeBlockEntity(host.getBlockPos());
                        copy.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(copy);
                        helper.assertTrue(((IMEOutputPart) copy).getTime() == 278 && ((IModifiableSyncOffset) copy).getMEUpdateInterval() == 300, "Native output world reload lost cycle state above byte range");
                        helper.getLevel().destroyBlock(copy.getBlockPos(), true);
                        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(copy.getBlockPos()).inflate(2)).stream().filter(entity -> entity.getItem().is(definition.asStack().getItem())).toList();
                        helper.assertTrue(drops.size() == 1, "Native output destruction did not drop exactly one machine");
                        var dropped = drops.getFirst().getItem().copy(); drops.getFirst().discard();
                        var player = FakePlayerFactory.getMinecraft(helper.getLevel()); player.closeContainer(); var ground = helper.absolutePos(new BlockPos(3 + i * 5, 2, 10));
                        helper.getLevel().setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState()); player.setPos(ground.getCenter().add(2, 2, 0)); player.setItemInHand(InteractionHand.MAIN_HAND, dropped);
                        var hit = new BlockHitResult(ground.getCenter().add(0, 0.5, 0), Direction.UP, ground, false);
                        helper.assertTrue(((BlockItem) dropped.getItem()).place(new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit))).consumesAction(), "Native output BlockItem placement failed");
                        var placed = (MetaMachine) helper.getLevel().getBlockEntity(ground.above());
                        helper.assertTrue(((IMEOutputPart) placed).getTime() == 278 && ((IModifiableSyncOffset) placed).getMEUpdateInterval() == 300, "Native output item components lost return-cycle settings");
                        if (items(i)) helper.assertTrue(ledger(placed, false).storage.getLong(AEItemKey.of(item())) == LARGE, "Native output item drop lost long component keys");
                        if (fluids(i)) helper.assertTrue(ledger(placed, true).storage.getLong(AEFluidKey.of(fluid())) == Long.MAX_VALUE, "Native output fluid drop truncated long quantities");
                        if (items(i)) helper.assertTrue(storage.getStorageService().getInventory().extract(AEItemKey.of(item()), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()) == 0, "Destroyed native output returned dropped item ownership twice");
                        if (fluids(i)) helper.assertTrue(storage.getStorageService().getInventory().extract(AEFluidKey.of(fluid()), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()) == 0, "Destroyed native output returned dropped fluid ownership twice");
                    }
                }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeGtmtOutputConsumesActualControllerRecipeAndRetainsExactLongProductionOutputs(GameTestHelper helper) {
        var origin = new BlockPos(4, 4, 4); helper.setBlock(origin, NativeGtOutputFixtures.CONTROLLER.getBlock());
        var definition = NativeGtOutputFixtures.CONTROLLER.get();
        var pattern = (com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern) definition.getStructurePatterns().get("main").get();
        var blocks = new LinkedHashMap<BlockPos, com.gregtechceu.gtceu.api.multiblock.util.BlockInfo>(); var repeats = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap(); repeats.put(0, 1);
        com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper.blockPattern(repeats).populate(blocks, pattern, null, Direction.NORTH, Direction.UP, false);
        NoEnergyMultiblockMachine controller = null; MetaMachine output = null;
        for (var entry : blocks.entrySet()) { var pos = origin.offset(entry.getKey()); helper.setBlock(pos, entry.getValue().getBlockState());
            var block = (MetaMachine) helper.getBlockEntity(pos); if (block instanceof NoEnergyMultiblockMachine machine) controller = machine; else output = block; }
        var machine = controller; var host = output; machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Actual native GTMT output controller did not form");
        var input = machine.getAllTraits().stream().filter(trait -> trait instanceof NotifiableItemStackHandler).map(trait -> (NotifiableItemStackHandler) trait).findFirst().orElseThrow(); input.storage.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT));
        ((com.gregtechceu.gtceu.api.capability.IControllable) host).setWorkingEnabled(false);
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_gtmt_long_outputs").inputItems(Items.COPPER_INGOT)
                .outputItems(LongIngredient.create(item(), LARGE)).outputFluids(LongFluidIngredient.create(fluid(), Long.MAX_VALUE)).duration(3).build();
        recipe.tickInputs.remove(com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP);
        helper.assertTrue(machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe), "Native controller rejected actual GTMT output handlers"); machine.getRecipeLogic().setSuspendAfterFinish(true);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.getRecipeLogic().isSuspend(), "Native GTMT output recipe never finished"))
                .thenExecute(() -> helper.assertTrue(input.storage.getStackInSlot(0).isEmpty() && ledger(host, false).storage.getLong(AEItemKey.of(item())) == LARGE &&
                        ledger(host, true).storage.getLong(AEFluidKey.of(fluid())) == Long.MAX_VALUE, "Native recipe execution lost exact GTMT long outputs")).thenSucceed();
    }
}
