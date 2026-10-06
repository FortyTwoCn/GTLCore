package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.multiblock.MolecularAssemblerMultiblockMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MECraftPatternContainerPartMachine;
import org.gtlcore.gtlcore.integration.ae2.MolecularAssemblerPatternHelper;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.LinkedHashMap;

/** Production controller and queue lifecycle on native parts; a live AE grid is a separate acceptance check. */
@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeMolecularGameTests {
    private static MolecularAssemblerMultiblockMachine build(GameTestHelper helper) {
        return build(helper, false);
    }

    static MolecularAssemblerMultiblockMachine build(GameTestHelper helper, boolean productionIo) {
        var definition = NativeMolecularFixtures.ASSEMBLER.get();
        var pattern = (BlockPattern) definition.getStructurePatterns().get("main").get();
        var repeats = new Int2IntOpenHashMap();
        for (int i = 0; i < pattern.getSlices().length; i++) repeats.put(i, pattern.getSlices()[i].getMinRepeats());
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(repeats).populate(blocks, pattern, null, Direction.NORTH, Direction.UP, false);
        var origin = new BlockPos(3, 4, 3);
        BlockPos controller = null;
        for (var entry : blocks.entrySet()) {
            var pos = origin.offset(entry.getKey());
            var state = entry.getValue().getBlockState();
            if (productionIo && state.is(NativeMolecularFixtures.IO.getBlock())) state = NativeMolecularFixtures.PRODUCTION_IO.getBlock().defaultBlockState();
            helper.setBlock(pos, state);
            if (entry.getValue().getBlockState().is(definition.getBlock())) controller = pos;
        }
        helper.assertTrue(controller != null, "Molecular preview omitted its controller");
        var machine = (MolecularAssemblerMultiblockMachine) helper.getBlockEntity(controller);
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getParts().size() == 4 && machine.getMaxParallel() == 8 &&
                machine.getTickDuration() == 3 && machine.getPatternSize() == 108,
                "Native molecular part discovery/speed/parallel/pattern slots changed: formed=" + machine.isFormed() +
                " parts=" + machine.getParts().size() + " parallel=" + machine.getMaxParallel() +
                " duration=" + machine.getTickDuration() + " patterns=" + machine.getPatternSize());
        return machine;
    }

    private static NativeMolecularFixtures.OutputPart output(MolecularAssemblerMultiblockMachine machine) {
        return machine.getParts().stream().filter(NativeMolecularFixtures.OutputPart.class::isInstance)
                .map(NativeMolecularFixtures.OutputPart.class::cast).findFirst().orElseThrow();
    }

    private static AEItemKey key() {
        var item = new ItemStack(Items.IRON_INGOT);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("native queued output"));
        return AEItemKey.of(item);
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void pendingMolecularCraftAndIoQueueSurviveNativeEntityRecreation(GameTestHelper helper) {
        var machine = build(helper); var io = output(machine); var key = key();
        var queued = new GenericStack(key, 9_007_199_254_740_993L);
        io.getOutputItems().put(queued, 16); io.handler.notifyListeners();
        var logic = machine.getRecipeLogic(); logic.findAndHandleRecipe(); logic.serverTick(); logic.setSuspendAfterFinish(true);
        helper.assertTrue(logic.getProgress() == 1 && io.getOutputItems().getLong(queued) == 8 && io.getBuffer().isEmpty(),
                "Molecular setup consumed the wrong craft count or produced output early");
        var registries = helper.getLevel().registryAccess();
        var savedController = machine.saveWithoutMetadata(registries); var savedIo = io.saveWithoutMetadata(registries);
        var restoredIo = (NativeMolecularFixtures.OutputPart) NativeMolecularFixtures.IO.getBlockEntityType()
                .create(io.getBlockPos(), io.getBlockState());
        var restored = (MolecularAssemblerMultiblockMachine) NativeMolecularFixtures.ASSEMBLER.getBlockEntityType()
                .create(machine.getBlockPos(), machine.getBlockState());
        helper.getLevel().removeBlockEntity(io.getBlockPos()); helper.getLevel().removeBlockEntity(machine.getBlockPos());
        restoredIo.loadAdditional(savedIo, registries); restored.loadAdditional(savedController, registries);
        helper.getLevel().setBlockEntity(restoredIo); helper.getLevel().setBlockEntity(restored);
        restored.setFrontFacing(Direction.NORTH); restored.checkAndFormStructure();
        helper.assertTrue(restored.isFormed() && restored.getMAHandler() == restoredIo.handler &&
                restored.getRecipeLogic().getProgress() == 1 && restoredIo.getOutputItems().getLong(queued) == 8,
                "Native molecular reformation lost progress, handler binding or the remaining queue");
        restored.getRecipeLogic().serverTick(); restored.getRecipeLogic().serverTick();
        helper.assertTrue(restored.getRecipeLogic().isSuspend() && restored.getRecipeLogic().getLastRecipe() == null &&
                restoredIo.getBuffer().getLong(key) == queued.amount() * 8 && restoredIo.getOutputItems().getLong(queued) == 8,
                "Pending craft did not finish exactly once after native reload");
        restored.getRecipeLogic().onRecipeFinish();
        helper.assertTrue(restoredIo.notifications == 1 && restoredIo.getBuffer().getLong(key) == queued.amount() * 8,
                "Repeated completion duplicated the saved molecular output");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void molecularStructureRepairAndOutputWaitRetainPendingCraft(GameTestHelper helper) {
        var machine = build(helper); var io = output(machine); var key = key();
        io.getOutputItems().put(new GenericStack(key, 3), 8);
        var logic = machine.getRecipeLogic(); logic.findAndHandleRecipe(); logic.serverTick();
        var pending = logic.getLastRecipe();
        var casing = machine.getDefaultPatternState().getCache().long2ObjectEntrySet().stream()
                .filter(entry -> entry.getValue().getBlockState().is(Blocks.IRON_BLOCK)).findFirst().orElseThrow().getLongKey();
        helper.getLevel().setBlockAndUpdate(BlockPos.of(casing), Blocks.AIR.defaultBlockState()); machine.checkAndFormStructure();
        logic.serverTick();
        helper.assertTrue(!machine.isFormed() && machine.getMAHandler() == null && logic.getLastRecipe() == pending &&
                logic.getProgress() == 1 && io.getBuffer().isEmpty(), "Unforming the native molecular structure discarded or completed a queued craft");
        helper.getLevel().setBlockAndUpdate(BlockPos.of(casing), Blocks.IRON_BLOCK.defaultBlockState()); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getMAHandler() == io.handler, "Native molecular structure repair did not rebind its IO part");
        io.getBuffer().put(key, Long.MAX_VALUE - 1);
        for (int i = 0; i < 5; i++) logic.serverTick();
        helper.assertTrue(logic.isWaiting() && logic.getLastRecipe() == pending && logic.getProgress() == 3 &&
                io.getBuffer().getLong(key) == Long.MAX_VALUE - 1 && io.notifications == 0,
                "An unrepresentable output lost pending work or partially modified the buffer");
        io.getBuffer().put(key, 1); logic.serverTick(); logic.serverTick();
        helper.assertTrue(logic.getLastRecipe() == null && io.getBuffer().getLong(key) == 25 && io.notifications == 1,
                "Molecular output wait did not resume exactly one atomic output transaction");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void realCraftingPatternConversionAndNativeContainerReload(GameTestHelper helper) {
        var level = helper.getLevel();
        var encoded = craftingPattern(helper);
        helper.assertTrue(MolecularAssemblerPatternHelper.molecularFilter(encoded, level), "Native molecular filter rejected a real crafting pattern");
        var decoded = PatternDetailsHelper.decodePattern(encoded, level);
        helper.assertTrue(decoded instanceof IMolecularAssemblerSupportedPattern, "Real AE crafting decoder returned a different pattern kind");
        var converted = MolecularAssemblerPatternHelper.createProcessingFromCraftPattern((IMolecularAssemblerSupportedPattern) decoded, level);
        helper.assertTrue(converted.getLeft() != null && converted.getLeft().getOutputs().equals(decoded.getOutputs()) &&
                converted.getRight().isEmpty(), "Native craft-to-processing conversion changed its real recipe output or reusable tools");
        long plankCount = 0;
        for (var requirement : converted.getLeft().getInputs()) {
            var choice = requirement.getPossibleInputs()[0];
            if (choice.what().equals(AEItemKey.of(Items.OAK_PLANKS))) plankCount += choice.amount() * requirement.getMultiplier();
        }
        helper.assertTrue(plankCount == 4, "Native craft-to-processing conversion changed the four real recipe inputs");
        var pos = new BlockPos(3, 4, 3);
        helper.setBlock(pos, NativeMolecularFixtures.PATTERNS.getBlock().defaultBlockState());
        var container = (MECraftPatternContainerPartMachine) helper.getBlockEntity(pos);
        container.getPatternInventory().setStackInSlot(107, encoded);
        var copy = (MECraftPatternContainerPartMachine) NativeMolecularFixtures.PATTERNS.getBlockEntityType()
                .create(container.getBlockPos(), container.getBlockState());
        var saved = container.saveWithoutMetadata(level.registryAccess());
        level.removeBlockEntity(container.getBlockPos()); copy.loadAdditional(saved, level.registryAccess()); level.setBlockEntity(copy);
        helper.assertTrue(copy.getSlots() == 108 && MolecularAssemblerPatternHelper.molecularFilter(copy.getPatternInventory().getStackInSlot(107), level),
                "Actual native pattern-container entity reload lost its last encoded crafting pattern");
        helper.succeed();
    }

    static ItemStack craftingPattern(GameTestHelper helper) {
        var level = helper.getLevel();
        var ingredients = java.util.stream.IntStream.range(0, 4).mapToObj(i -> new ItemStack(Items.OAK_PLANKS)).toList();
        var input = CraftingInput.of(2, 2, ingredients);
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        var result = recipe.value().assemble(input, level.registryAccess());
        // AE encodes the full 3x3 grid even for a 2x2 recipe.
        var grid = new ItemStack[9]; java.util.Arrays.fill(grid, ItemStack.EMPTY);
        grid[0] = ingredients.get(0); grid[1] = ingredients.get(1);
        grid[3] = ingredients.get(2); grid[4] = ingredients.get(3);
        return PatternDetailsHelper.encodeCraftingPattern(recipe, grid, result, false, false);
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeCraftingExtractionRetainsRemaindersAndCountersOnFailure(GameTestHelper helper) {
        var level = helper.getLevel();
        var ingredients = java.util.stream.IntStream.range(0, 4).mapToObj(i -> new ItemStack(Items.HONEY_BOTTLE)).toList();
        var input = CraftingInput.of(2, 2, ingredients);
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        var grid = new ItemStack[9]; java.util.Arrays.fill(grid, ItemStack.EMPTY);
        grid[0] = ingredients.get(0); grid[1] = ingredients.get(1); grid[3] = ingredients.get(2); grid[4] = ingredients.get(3);
        var encoded = PatternDetailsHelper.encodeCraftingPattern(recipe, grid, recipe.value().assemble(input, level.registryAccess()), false, false);
        var details = PatternDetailsHelper.decodePattern(encoded, level);
        var source = new appeng.crafting.inv.ListCraftingInventory(ignored -> {});
        var honey = AEItemKey.of(Items.HONEY_BOTTLE); var bottle = AEItemKey.of(Items.GLASS_BOTTLE);
        var output = details.getPrimaryOutput();
        var outputs = new appeng.api.stacks.KeyCounter(); var containers = new appeng.api.stacks.KeyCounter();
        outputs.add(output.what(), 20); containers.add(bottle, 10); source.insert(honey, 3, appeng.api.config.Actionable.MODULATE);
        helper.assertTrue(org.gtlcore.gtlcore.integration.ae2.AEUtils.extractForCraftPattern(details, source, level, outputs, containers) == null &&
                source.list.get(honey) == 3 && outputs.get(output.what()) == 20 && containers.get(bottle) == 10,
                "A failed native crafting extraction consumed inputs or modified expected output/container counters");
        source.insert(honey, 1, appeng.api.config.Actionable.MODULATE);
        var extracted = org.gtlcore.gtlcore.integration.ae2.AEUtils.extractForCraftPattern(details, source, level, outputs, containers);
        helper.assertTrue(extracted != null && source.list.get(honey) == 0 && outputs.get(output.what()) == 20 + output.amount() && containers.get(bottle) == 14,
                "Native crafting extraction lost its exact actual recipe output or returned bottle quantity");
        source.insert(honey, 4, appeng.api.config.Actionable.MODULATE); containers.set(bottle, Long.MAX_VALUE - 1);
        helper.assertTrue(org.gtlcore.gtlcore.integration.ae2.AEUtils.extractForCraftPattern(details, source, level, outputs, containers) == null &&
                source.list.get(honey) == 4 && containers.get(bottle) == Long.MAX_VALUE - 1,
                "Unrepresentable native crafting containers partially changed CPU inventory or expected counters");
        helper.succeed();
    }
}
