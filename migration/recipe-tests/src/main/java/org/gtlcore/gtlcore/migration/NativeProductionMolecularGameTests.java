package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.multiblock.MolecularAssemblerMultiblockMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEMolecularAssemblerIOPartMachine;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;

import java.util.concurrent.atomic.AtomicReference;

/** Actual production controller, pattern container and IO/provider on native AE power and storage. */
@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeProductionMolecularGameTests {
    private static MEMolecularAssemblerIOPartMachine io(MolecularAssemblerMultiblockMachine machine) {
        return machine.getParts().stream().filter(MEMolecularAssemblerIOPartMachine.class::isInstance)
                .map(MEMolecularAssemblerIOPartMachine.class::cast).findFirst().orElseThrow();
    }
    private static void power(GameTestHelper helper, MEMolecularAssemblerIOPartMachine io, boolean storage) {
        io.setFrontFacing(Direction.NORTH);
        helper.getLevel().setBlockAndUpdate(io.getBlockPos().north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        if (storage) {
            var pos = io.getBlockPos().north(2);
            helper.getLevel().setBlockAndUpdate(pos, AEBlocks.DRIVE.block().defaultBlockState());
            ((DriveBlockEntity) helper.getLevel().getBlockEntity(pos)).getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        }
    }
    private static KeyCounter[] inputs(IPatternDetails pattern, long crafts) {
        var requirements = pattern.getInputs(); var counters = new KeyCounter[requirements.length];
        for (int i = 0; i < counters.length; i++) {
            counters[i] = new KeyCounter(); var stack = requirements[i].getPossibleInputs()[0];
            counters[i].add(stack.what(), Math.multiplyExact(crafts, Math.multiplyExact(stack.amount(), requirements[i].getMultiplier())));
        }
        return counters;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void productionMolecularProviderCraftsThroughRealAeStorage(GameTestHelper helper) {
        var controller = NativeMolecularGameTests.build(helper, true); var io = io(controller);
        power(helper, io, true);
        var encoded = NativeMolecularGameTests.craftingPattern(helper);
        var output = PatternDetailsHelper.decodePattern(encoded, helper.getLevel()).getPrimaryOutput();
        var key = (AEItemKey) output.what();
        io.getTerminalPatternInventory().setItemDirect(0, encoded);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(io.isOnline() && io.getMainNode().isActive() &&
                io.getMainNode().getGrid().getCraftingService().isCraftable(key), "Production molecular IO did not publish its real pattern on the AE crafting service"))
                .thenExecute(() -> {
                    var pattern = io.getAvailablePatterns().getFirst();
                    helper.assertTrue(io.getTerminalPatternInventory().size() == 108 && io.getTerminalGroup().icon().equals(AEItemKey.of(controller.getDefinition().asStack())),
                            "Production molecular terminal inventory or controller grouping differs from its actual native parts");
                    var malformed = inputs(pattern, 1); var first = pattern.getInputs()[0].getPossibleInputs()[0].what();
                    malformed[0].add(first, 1);
                    helper.assertTrue(!io.pushPattern(pattern, malformed) && io.getOutputItems().isEmpty(),
                            "Production molecular IO accepted a fractional input batch");
                    helper.assertTrue(io.pushPattern(pattern, inputs(pattern, 8)), "Production molecular IO rejected an exact real crafting batch");
                }).thenWaitUntil(() -> {
                    var inventory = io.getMainNode().getGrid().getStorageService().getInventory();
                    helper.assertTrue(inventory.extract(key, output.amount() * 8L, Actionable.SIMULATE,
                            IActionSource.ofMachine(io.getMainNode()::getNode)) == output.amount() * 8L &&
                            io.getOutputItems().isEmpty() && io.getBuffer().isEmpty(),
                            "Production controller/IO did not finish and flush the exact output through the native AE drive");
                }).thenIdle(100).thenExecute(() -> {
                    var inventory = io.getMainNode().getGrid().getStorageService().getInventory();
                    helper.assertTrue(inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE,
                            IActionSource.ofMachine(io.getMainNode()::getNode)) == output.amount() * 8L,
                            "Production molecular output was duplicated on later AE ticks");
                }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void productionMolecularIoAndPendingLongCraftSurviveRealNodeReload(GameTestHelper helper) {
        var controller = NativeMolecularGameTests.build(helper, true); var io = io(controller); power(helper, io, false);
        var stack = new ItemStack(Items.DIAMOND); stack.set(DataComponents.CUSTOM_NAME, Component.literal("saved production molecular output"));
        var key = AEItemKey.of(stack); var queued = new GenericStack(key, 9_007_199_254_740_993L);
        var restored = new AtomicReference<MolecularAssemblerMultiblockMachine>();
        var restoredIo = new AtomicReference<MEMolecularAssemblerIOPartMachine>();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(io.isOnline(), "Production molecular IO did not create its real AE node"))
                .thenExecute(() -> {
                    io.getOutputItems().put(queued, 16); io.getNotifiableMAHandlerTrait().notifyListeners();
                    var logic = controller.getRecipeLogic(); logic.findAndHandleRecipe(); logic.serverTick();
                    logic.setStatus(RecipeLogic.Status.SUSPEND);
                    io.setCustomName("native production assembler"); io.setHiddenTerminal(true);
                    io.getSharedToolsInventory().setStackInSlot(8, new ItemStack(Items.BUCKET));
                    var registries = helper.getLevel().registryAccess(); var saved = controller.saveWithoutMetadata(registries);
                    var savedIo = io.saveWithoutMetadata(registries);
                    var copy = (MolecularAssemblerMultiblockMachine) NativeMolecularFixtures.ASSEMBLER.getBlockEntityType()
                            .create(controller.getBlockPos(), controller.getBlockState());
                    var copyIo = (MEMolecularAssemblerIOPartMachine) NativeMolecularFixtures.PRODUCTION_IO.getBlockEntityType()
                            .create(io.getBlockPos(), io.getBlockState());
                    helper.getLevel().removeBlockEntity(io.getBlockPos()); helper.getLevel().removeBlockEntity(controller.getBlockPos());
                    helper.assertTrue(io.getMainNode().getNode() == null, "Production molecular node survived its entity unload");
                    copyIo.loadAdditional(savedIo, registries); copy.loadAdditional(saved, registries);
                    helper.getLevel().setBlockEntity(copyIo); helper.getLevel().setBlockEntity(copy); copy.checkAndFormStructure();
                    helper.assertTrue(copy.isFormed() && copy.getMAHandler() == copyIo.getNotifiableMAHandlerTrait() &&
                            copy.getRecipeLogic().getProgress() == 1 && copyIo.getOutputItems().getLong(queued) == 8 &&
                            copyIo.getSharedToolsInventory().getStackInSlot(8).is(Items.BUCKET) && !copyIo.isVisibleInTerminal() &&
                            copyIo.getTerminalGroup().name().getString().equals("native production assembler"),
                            "Actual production IO reload lost a pending craft, tool, visibility, name or handler binding");
                    restored.set(copy); restoredIo.set(copyIo); copy.getRecipeLogic().setWorkingEnabled(true);
                    copy.getRecipeLogic().setSuspendAfterFinish(true);
                }).thenWaitUntil(() -> {
                    var copy = restored.get(); var copyIo = restoredIo.get();
                    helper.assertTrue(copyIo.isOnline() && copy.getRecipeLogic().isSuspend() && copy.getRecipeLogic().getLastRecipe() == null &&
                            copyIo.getBuffer().getLong(key) == queued.amount() * 8 && copyIo.getOutputItems().getLong(queued) == 8,
                            "Saved production molecular craft did not resume once with exact long output on the recreated AE node");
                }).thenIdle(100).thenExecute(() -> helper.assertTrue(restoredIo.get().getBuffer().getLong(key) == queued.amount() * 8,
                        "No-storage AE ticks lost or duplicated the pending production output")).thenSucceed();
    }
}
