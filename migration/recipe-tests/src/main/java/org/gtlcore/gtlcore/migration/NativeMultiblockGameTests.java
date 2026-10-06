package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.pattern.PatternMatchData;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.api.recipe.BatchProcessing;
import org.gtlcore.gtlcore.api.machine.trait.IBatchMachine;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import java.util.Map;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.AdvancedAssemblyLineMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.TierCasingMachine;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.TypeDeclaration;
import com.gregtechceu.gtceu.api.sync_system.data_transformers.ValueTransformer.TransformerContext;
import com.gregtechceu.gtceu.api.sync_system.data_transformers.gtceu.GTRecipeTransformer;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import io.netty.buffer.Unpooled;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public class NativeMultiblockGameTests {
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void tierFormationInvalidationAndReformation(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(x, y, 3), Blocks.IRON_BLOCK);
        }
        helper.setBlock(new BlockPos(3, 2, 3), NativeMachineFixtures.TIER.getBlock());
        var machine = (TierCasingMachine) helper.getBlockEntity(new BlockPos(3, 2, 3));
        machine.setFrontFacing(Direction.NORTH);
        machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getCasingTier() == 3, "Uniform iron casing failed to form");
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.GOLD_BLOCK);
        machine.checkAndFormStructure();
        helper.assertTrue(!machine.isFormed() && machine.getCasingTier() == 0, "Mixed casing tiers remained formed");
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.IRON_BLOCK);
        machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getCasingTier() == 3, "Casing tier failed to recover");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void repeatCountExcludesSpeculativeSlice(GameTestHelper helper) {
        helper.setBlock(new BlockPos(5, 2, 6), NativeMachineFixtures.ASSEMBLY.getBlock());
        helper.setBlock(new BlockPos(3, 2, 6), Blocks.IRON_BLOCK);
        for (int z = 4; z <= 5; z++) for (int x = 3; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 2, z), Blocks.IRON_BLOCK);
        }
        helper.setBlock(new BlockPos(5, 2, 3), Blocks.IRON_BLOCK);
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.GOLD_BLOCK);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.IRON_BLOCK);
        var machine = (AdvancedAssemblyLineMachine) helper.getBlockEntity(new BlockPos(5, 2, 6));
        machine.setFrontFacing(Direction.NORTH);
        machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Repeatable assembly fixture failed to form");
        helper.assertTrue(PatternMatchData.of(machine.getDefaultPatternState().getContext()).blockCount("Unit") == 9,
                "Failed repeat probe changed the accepted unit count");
        helper.assertTrue(machine.getSpeed() == 91, "Assembly speed disagrees with the matched units");
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.AIR);
        machine.checkAndFormStructure();
        helper.assertTrue(!machine.isFormed() && machine.getSpeed() == 0, "Broken repeat layer remained formed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void storageCapabilityPersistsWithoutJoiningRecipeInputs(GameTestHelper helper) {
        var pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, NativeMachineFixtures.STORAGE.getBlock());
        var machine = (StorageMachine) helper.getBlockEntity(pos);
        var capability = GTCapabilityHelper.getItemHandler(helper.getLevel(), helper.absolutePos(pos), Direction.NORTH);
        helper.assertTrue(capability != null, "Native item capability was not registered");
        var remainder = capability.insertItem(0, new ItemStack(Items.DIAMOND, 4), false);
        helper.assertTrue(remainder.getCount() == 3 && machine.getMachineStorageItem().getCount() == 1,
                "Storage capability ignored the slot limit");
        machine.checkAndFormStructure();
        helper.assertTrue(machine.getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).isEmpty(),
                "Non-recipe storage joined recipe inputs");
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        capability.extractItem(0, 1, false);
        machine.loadAdditional(saved, helper.getLevel().registryAccess());
        helper.assertTrue(machine.getMachineStorageItem().is(Items.DIAMOND) &&
                machine.getMachineStorageItem().getCount() == 1, "Native storage data did not restore");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void runtimeRecipeStateSurvivesBlockEntitySaveAndRecipePacket(GameTestHelper helper)
            throws ReflectiveOperationException {
        var pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, NativeMachineFixtures.STORAGE.getBlock());
        var source = (StorageMachine) helper.getBlockEntity(pos);
        GTRecipe recipe = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("saved_runtime_recipe")
                .inputItems(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 3_000_000_001L))
                .inputFluids(LongFluidIngredient.create(Fluids.WATER, 3_000_000_002L)).EUt(32).duration(100).build();
        IGTRecipe.of(recipe).setRealParallels(9_007_199_254_740_993L);
        IGTRecipe.of(recipe).setBatchSize(37);
        IGTRecipe.of(recipe).setBatchProcessed(true);
        IGTRecipe.of(recipe).setSubTickParallelized(true);
        recipe.ocLevel = 5;
        // Seed a runtime field through reflection; the actual native BE serializers perform the test.
        // This test does not claim that this storage-only fixture can execute the seeded recipe.
        for (String name : new String[] { "lastRecipe", "lastUnrolledRecipe" }) {
            var field = RecipeLogic.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(source.getRecipeLogic(), recipe);
        }
        var registries = helper.getLevel().registryAccess();
        var saved = source.saveWithoutMetadata(registries);
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, NativeMachineFixtures.STORAGE.getBlock());
        var restored = (StorageMachine) helper.getBlockEntity(pos);
        restored.loadAdditional(saved, registries);
        assertRuntimeState(helper, restored.getRecipeLogic().getLastRecipe());
        var context = new TransformerContext<GTRecipe>(restored.getRecipeLogic(), new TypeDeclaration(GTRecipe.class),
                recipe, "lastUnrolledRecipe", false, true, registries);
        var transformer = new GTRecipeTransformer();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            transformer.writeToPacket(buffer, restored.getRecipeLogic().getLastUnrolledRecipe(), context);
            assertRuntimeState(helper, transformer.readFromPacket(buffer, context));
            helper.assertTrue(buffer.readableBytes() == 0, "Native recipe packet left unread bytes");
        } finally { buffer.release(); }
        helper.succeed();
    }

    private static NoEnergyMultiblockMachine routingController(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 3), NativeMachineFixtures.ROUTING.getBlock());
        helper.setBlock(new BlockPos(2, 2, 3), GTMachines.ITEM_IMPORT_BUS[1].getBlock());
        helper.setBlock(new BlockPos(4, 2, 3), GTMachines.ITEM_IMPORT_BUS[1].getBlock());
        var machine = (NoEnergyMultiblockMachine) helper.getBlockEntity(new BlockPos(3, 2, 3));
        machine.setWorkingEnabled(false);
        machine.setFrontFacing(Direction.NORTH);
        machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getParts().size() == 2, "Native routing fixture failed to discover its two item buses");
        return machine;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void controllerDistinctRoutingCacheInvalidationAndNativeSave(GameTestHelper helper) {
        var machine = routingController(helper);
        var core = (IRecipeCapabilityMachine) (Object) machine;
        var first = ((ItemBusPartMachine) helper.getBlockEntity(new BlockPos(2, 2, 3))).getInventory();
        var second = ((ItemBusPartMachine) helper.getBlockEntity(new BlockPos(4, 2, 3))).getInventory();
        first.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        second.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        var recipe = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_distinct_route").inputItems(Items.IRON_INGOT, 5).build();
        helper.assertTrue(!core.usesCoreRecipeRouting() && RecipeHelper.matchRecipe(machine, recipe).isSuccess(), "Default native undyed buses did not combine");
        core.setDistinct(true); core.upDate();
        helper.assertTrue(core.usesCoreRecipeRouting() && !RecipeHelper.handleRecipeIO(machine, recipe, IO.IN, Map.of()).isSuccess(),
                "Core global distinct mode combined independent native buses");
        helper.assertTrue(first.getStackInSlot(0).getCount() == 3 && second.getStackInSlot(0).getCount() == 3,
                "Failed native dispatch consumed inputs");
        second.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        helper.assertTrue(RecipeHelper.handleRecipeIO(machine, recipe, IO.IN, Map.of()).isSuccess(), "Native RecipeLogic IO did not enter Core routes");
        helper.assertTrue(first.getStackInSlot(0).getCount() == 3 && second.getStackInSlot(0).getCount() == 3,
                "Core dispatch consumed the wrong bus");
        var previous = core.getActiveRecipeHandle(recipe);
        first.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        helper.assertTrue(RecipeHelper.handleRecipeIO(machine, recipe, IO.IN, Map.of()).isSuccess(), "Routing did not recover from an exhausted cached bus");
        var active = core.getActiveRecipeHandle(recipe);
        var order = core.getAllCachedRecipeHandlesIter(recipe);
        helper.assertTrue(active != previous && core.getAllCachedRecipeHandles(recipe).size() == 2 &&
                order.hasNext() && order.next() == active && order.hasNext() && order.next() == previous && !order.hasNext(),
                "Native controller cache lost active-first ordering");
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        machine.onPartUnload();
        helper.assertTrue(core.emptyHandlePart() && core.getAllCachedRecipeHandles(recipe).isEmpty(), "Part unload retained stale Core handlers");
        // A direct onPartUnload call leaves the native pattern marked formed. Force the real
        // invalidation/reformation transition before expecting native part handlers to reattach.
        machine.invalidateStructure();
        machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && !core.emptyHandlePart(), "Part routes did not rebuild after reformation");
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.AIR);
        machine.checkAndFormStructure();
        helper.assertTrue(!machine.isFormed() && core.emptyHandlePart() && core.getAllCachedRecipeHandles(recipe).isEmpty(),
                "Broken structure retained routes or cached inventories");
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 2, 3), NativeMachineFixtures.ROUTING.getBlock());
        var restored = (NoEnergyMultiblockMachine) helper.getBlockEntity(new BlockPos(3, 2, 3));
        restored.loadAdditional(saved, helper.getLevel().registryAccess());
        helper.assertTrue(((IRecipeCapabilityMachine) restored).isDistinct(), "Native SaveField did not persist the Core distinct setting");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void distinctInputUsesNativeControllerGhostCircuit(GameTestHelper helper) {
        var machine = routingController(helper); var core = (IRecipeCapabilityMachine) (Object) machine;
        core.setDistinct(true); core.upDate();
        var first = ((ItemBusPartMachine) helper.getBlockEntity(new BlockPos(2, 2, 3))).getInventory();
        var second = ((ItemBusPartMachine) helper.getBlockEntity(new BlockPos(4, 2, 3))).getInventory();
        first.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        second.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        var circuit = machine.getTraits(ProgrammableCircuitSlotTrait.class).getFirst();
        circuit.setCurrentCircuit(17);
        var recipe = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_controller_circuit").inputItems(Items.IRON_INGOT, 5).circuitMeta(17).build();
        helper.assertTrue(RecipeRunnerHelper.matchRecipeInput(machine, recipe) && RecipeHelper.handleRecipeIO(machine, recipe, IO.IN, Map.of()).isSuccess(),
                "Controller ghost circuit did not supplement the selected distinct bus");
        helper.assertTrue(first.getStackInSlot(0).getCount() == 3 && second.getStackInSlot(0).getCount() == 3 && circuit.getCurrentCircuit() == 17,
                "Distinct route consumed the wrong bus or the non-consumed circuit");
        circuit.setCurrentCircuit(16); second.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        helper.assertTrue(!RecipeHelper.handleRecipeIO(machine, recipe, IO.IN, Map.of()).isSuccess() && second.getStackInSlot(0).getCount() == 8,
                "Wrong circuit allowed input consumption");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeDatabaseSearchKeepsRealDistinctBusesAndControllerCircuitInTheSameRoute(GameTestHelper helper) {
        var machine = routingController(helper); var core = (IRecipeCapabilityMachine) machine;
        core.setDistinct(true); core.upDate();
        var first = ((ItemBusPartMachine) helper.getBlockEntity(new BlockPos(2, 2, 3))).getInventory();
        var second = ((ItemBusPartMachine) helper.getBlockEntity(new BlockPos(4, 2, 3))).getInventory();
        first.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        second.storage.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 8));
        var circuit = machine.getTraits(ProgrammableCircuitSlotTrait.class).getFirst(); circuit.setCurrentCircuit(17);
        var valid = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_db_distinct_circuit").inputItems(Items.IRON_INGOT, 2).circuitMeta(17).build();
        var mixed = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_db_distinct_mixed").inputItems(Items.IRON_INGOT, 2).inputItems(Items.GOLD_INGOT, 2).circuitMeta(17).build();
        var staging = new com.gregtechceu.gtceu.api.recipe.lookup.StagingRecipeDB(); staging.add(valid); staging.add(mixed);
        var database = new com.gregtechceu.gtceu.api.recipe.lookup.RecipeDB(); staging.populateDB(database);
        var matches = org.gtlcore.gtlcore.api.recipe.IRecipeIterator.search(valid.recipeType, database, machine, core,
                recipe -> RecipeRunnerHelper.matchRecipeInput(machine, recipe));
        helper.assertTrue(matches.hasNext() && matches.next().id.equals(valid.id) && !matches.hasNext(),
                "Native database search combined real distinct buses or omitted the controller circuit");
        circuit.setCurrentCircuit(16);
        helper.assertTrue(!org.gtlcore.gtlcore.api.recipe.IRecipeIterator.search(valid.recipeType, database, machine, core,
                recipe -> RecipeRunnerHelper.matchRecipeInput(machine, recipe)).hasNext(), "Native database search kept a stale controller circuit");
        circuit.setCurrentCircuit(17); first.storage.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 2));
        matches = org.gtlcore.gtlcore.api.recipe.IRecipeIterator.search(valid.recipeType, database, machine, core,
                recipe -> recipe.id.equals(mixed.id) && RecipeRunnerHelper.matchRecipeInput(machine, recipe));
        helper.assertTrue(matches.hasNext() && matches.next().id.equals(mixed.id), "Native database search failed when the full input was present in one physical bus");
        helper.assertTrue(first.getStackInSlot(0).getCount() == 3 && second.getStackInSlot(0).getCount() == 8,
                "Native database lookup consumed physical input");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeColoredGroupsAndBusSettingChangesRemainActive(GameTestHelper helper) {
        var machine = routingController(helper); var core = (IRecipeCapabilityMachine) (Object) machine;
        var first = (ItemBusPartMachine) helper.getBlockEntity(new BlockPos(2, 2, 3));
        var second = (ItemBusPartMachine) helper.getBlockEntity(new BlockPos(4, 2, 3));
        first.getInventory().storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        second.getInventory().storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        first.setPaintingColor(0xFF0000); second.setPaintingColor(0x0000FF);
        var recipe = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_colored_routes").inputItems(Items.IRON_INGOT, 5).build();
        helper.assertTrue(!core.usesCoreRecipeRouting() && !RecipeHelper.matchRecipe(machine, recipe).isSuccess(),
                "Independent native colored groups were combined");
        second.setPaintingColor(0xFF0000);
        helper.assertTrue(RecipeHelper.matchRecipe(machine, recipe).isSuccess() && recipe.groupColor == 0xFF0000,
                "Matching native colored buses failed to combine or record the selected color");
        first.setPaintingColor(-1); second.setPaintingColor(-1);
        first.setDistinct(true); second.setDistinct(true);
        helper.assertTrue(core.usesCoreRecipeRouting() && !RecipeHelper.handleRecipeIO(machine, recipe, IO.IN, Map.of()).isSuccess(),
                "Native bus distinct changes did not rebuild Core routes");
        first.setDistinct(false); second.setDistinct(false);
        helper.assertTrue(!core.usesCoreRecipeRouting() && RecipeHelper.matchRecipe(machine, recipe).isSuccess(),
                "Disabling bus distinct mode retained stale Core routes");
        helper.succeed();
    }

    private static StorageMachine batchController(GameTestHelper helper) {
        var pos = new BlockPos(3, 2, 3); helper.setBlock(pos, NativeMachineFixtures.BATCH.getBlock());
        var machine = (StorageMachine) helper.getBlockEntity(pos);
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && ((IBatchMachine) machine).supportsBatchProcessing(), "Native Core batch fixture did not form or support batching");
        var core = (IRecipeCapabilityMachine) (Object) machine; core.setDistinct(true); core.upDate();
        machine.setBatchEnabled(true);
        return machine;
    }
    private static NotifiableItemStackHandler batchInputs(StorageMachine machine) {
        return machine.getTraits(NotifiableItemStackHandler.class).stream().filter(handler -> handler.getHandlerIO() == IO.IN).findFirst().orElseThrow();
    }
    private static GTRecipe batchRecipe(String id, boolean chance) {
        var builder = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder(id).inputItems(Items.IRON_INGOT, 2).notConsumable(Items.DIAMOND).duration(4);
        if (chance) builder.chancedOutput(SizedFluidIngredient.of(Fluids.WATER, 100), 5000);
        else builder.outputFluids(SizedFluidIngredient.of(Fluids.WATER, 100));
        builder.tickInput.clear(); // The base chemical type supplies EU/t; this fixture tests batching without an energy hatch.
        return builder.build();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void batchRunsThroughNativeSetupTicksOutputAndPause(GameTestHelper helper) {
        var machine = batchController(helper); var input = batchInputs(machine); var logic = machine.getRecipeLogic();
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 41)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        var source = batchRecipe("native_batch_full_cycle", false);
        helper.assertTrue(logic.checkMatchedRecipeAvailable(source), "Native recipe setup rejected the batch");
        helper.assertTrue(logic.getDuration() == 80 && IGTRecipe.of(logic.getLastRecipe()).getBatchSize() == 20 &&
                input.getStackInSlot(0).getCount() == 1 && input.getStackInSlot(1).is(Items.DIAMOND),
                "Batch setup changed NC inputs, batch count, duration or input consumption");
        helper.assertTrue(source.duration == 4 && LongIngredient.amount(ItemRecipeCapability.CAP.of(source.getInputContents(ItemRecipeCapability.CAP).getFirst().content())) == 2 &&
                !IGTRecipe.of(source).isBatchProcessed(), "Native setup changed the registered source recipe");
        logic.setSuspendAfterFinish(true);
        for (int tick = 0; tick < 80; tick++) logic.serverTick();
        var tank = machine.getTraits(NotifiableFluidTank.class).getFirst();
        helper.assertTrue(tank.getStorages()[0].getFluidAmount() == 2000 && logic.getStatus() == RecipeLogic.Status.SUSPEND &&
                logic.getProgress() == 0 && logic.getDuration() == 0, "Native tick/finish/pause lifecycle did not deliver one complete batch");
        logic.serverTick(); helper.assertTrue(tank.getStorages()[0].getFluidAmount() == 2000, "Paused batch delivered outputs twice");
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        machine.setBatchEnabled(false); machine.loadAdditional(saved, helper.getLevel().registryAccess());
        helper.assertTrue(machine.isBatchEnabled(), "Native batchEnabled SaveField did not restore");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void blockedBatchOutputWaitsAndResumesWithoutConsumingAgain(GameTestHelper helper) {
        var machine = batchController(helper); var input = batchInputs(machine); var logic = machine.getRecipeLogic();
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 41)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        helper.assertTrue(logic.checkMatchedRecipeAvailable(batchRecipe("native_batch_blocked", false)), "Blocked-output batch failed to start");
        var tank = machine.getTraits(NotifiableFluidTank.class).getFirst();
        tank.getStorages()[0].setFluid(new FluidStack(Fluids.WATER, 10_000));
        logic.setProgress(logic.getDuration()); logic.serverTick();
        helper.assertTrue(logic.isWaiting() && logic.getLastRecipe() != null && input.getStackInSlot(0).getCount() == 1 &&
                tank.getStorages()[0].getFluidAmount() == 10_000, "Blocked batch discarded outputs or consumed another cycle");
        logic.serverTick(); helper.assertTrue(input.getStackInSlot(0).getCount() == 1, "Waiting batch consumed inputs twice");
        tank.getStorages()[0].setFluid(FluidStack.EMPTY); logic.setSuspendAfterFinish(true); logic.serverTick();
        helper.assertTrue(logic.isSuspend() && tank.getStorages()[0].getFluidAmount() == 2000 && input.getStackInSlot(0).getCount() == 1,
                "Unblocked batch failed to output exactly once");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void batchedChanceIsRolledOnceAndCapacityQueriesLeaveItsCacheAlone(GameTestHelper helper) {
        var machine = batchController(helper); var input = batchInputs(machine); var logic = machine.getRecipeLogic();
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 41)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        var recipe = batchRecipe("native_batch_chance", true); var content = recipe.getOutputContents(FluidRecipeCapability.CAP).getFirst();
        var cache = logic.getChanceCaches().get(FluidRecipeCapability.CAP);
        org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic.updateCachedChance(content.content(), cache, 0);
        var prepared = BatchProcessing.apply(machine, recipe.copy(), false);
        helper.assertTrue(prepared != null && IGTRecipe.of(prepared).getBatchSize() == 20 && cache.getInt(content.content()) == 0,
                "Batch capacity calculation advanced the output chance cache");
        helper.assertTrue(logic.checkMatchedRecipeAvailable(recipe), "Chance batch failed to start");
        logic.setSuspendAfterFinish(true); logic.setProgress(logic.getDuration()); logic.serverTick();
        var tank = machine.getTraits(NotifiableFluidTank.class).getFirst();
        helper.assertTrue(tank.getStorages()[0].getFluidAmount() == 1000 && recipe.getOutputContents(FluidRecipeCapability.CAP).getFirst().chance() == 5000,
                "Batched output chance was rolled twice or changed the source recipe");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void longDurationWithBatchEnabledRunsOneNativeCycle(GameTestHelper helper) {
        var machine = batchController(helper); var input = batchInputs(machine); var logic = machine.getRecipeLogic();
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 41)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        var recipe = batchRecipe("native_batch_ineligible", false); recipe.duration = 200;
        helper.assertTrue(logic.checkMatchedRecipeAvailable(recipe) && logic.getDuration() == 200 &&
                IGTRecipe.of(logic.getLastRecipe()).getBatchSize() == 1 && input.getStackInSlot(0).getCount() == 39,
                "Batch-ineligible recipe did not run once through native setup");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void recipeLockRestrictsNativeSearchAndPersistsInTheBlockEntity(GameTestHelper helper) {
        var machine = batchController(helper); machine.setBatchEnabled(false);
        var input = batchInputs(machine); var logic = machine.getRecipeLogic(); var lock = (ILockRecipe) logic;
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        var recipe = batchRecipe("native_locked_recipe", false); lock.setLock(true);
        helper.assertTrue(logic.checkMatchedRecipeAvailable(recipe) && lock.isLock() && lock.getLockRecipe() != null &&
                lock.getLockRecipe().id.equals(recipe.id), "Native setup failed to capture the recipe lock");
        logic.setProgress(logic.getDuration()); logic.setSuspendAfterFinish(true); logic.serverTick();
        var matches = logic.searchRecipe();
        helper.assertTrue(matches.hasNext() && matches.next().id.equals(recipe.id) && !matches.hasNext(), "Locked native search returned another recipe");
        helper.assertTrue(!logic.checkMatchedRecipeAvailable(lock.getLockRecipe()) && input.getStackInSlot(0).isEmpty(),
                "Locked recipe ignored exhausted input stock");
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        lock.setLock(false); machine.loadAdditional(saved, helper.getLevel().registryAccess());
        helper.assertTrue(((ILockRecipe) logic).isLock() && ((ILockRecipe) logic).getLockRecipe() != null &&
                ((ILockRecipe) logic).getLockRecipe().id.equals(recipe.id), "Native lock SaveFields did not restore");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void chanceBatchReservesItsWholePendingOutputWithoutRolling(GameTestHelper helper) {
        var machine = batchController(helper); var input = batchInputs(machine); var logic = machine.getRecipeLogic();
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 41)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        var recipe = batchRecipe("native_chance_batch_pending_capacity", true); var content = recipe.getOutputContents(FluidRecipeCapability.CAP).getFirst();
        var cache = logic.getChanceCaches().get(FluidRecipeCapability.CAP);
        org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic.updateCachedChance(content.content(), cache, 0);
        helper.assertTrue(logic.checkMatchedRecipeAvailable(recipe), "Chance batch failed to start");
        var tank = machine.getTraits(NotifiableFluidTank.class).getFirst(); tank.getStorages()[0].setFluid(new FluidStack(Fluids.WATER, 9500));
        logic.setProgress(logic.getDuration()); logic.serverTick();
        helper.assertTrue(logic.isWaiting() && tank.getStorages()[0].getFluidAmount() == 9500 && input.getStackInSlot(0).getCount() == 1 &&
                cache.getInt(content.content()) == 0, "Chance batch checked only its unscaled output or rolled while waiting for capacity");
        tank.getStorages()[0].setFluid(FluidStack.EMPTY); logic.setSuspendAfterFinish(true); logic.serverTick();
        helper.assertTrue(logic.isSuspend() && tank.getStorages()[0].getFluidAmount() == 1000 && input.getStackInSlot(0).getCount() == 1,
                "Unblocked chance batch failed to deliver its exact output once");
        helper.succeed();
    }


    private static StorageMachine poweredBatchController(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 2, 3), GTMachines.ENERGY_INPUT_HATCH[2].getBlock());
        helper.setBlock(new BlockPos(3, 2, 3), NativeMachineFixtures.ENERGY_BATCH.getBlock());
        helper.setBlock(new BlockPos(4, 2, 3), GTMachines.ENERGY_INPUT_HATCH[2].getBlock());
        var machine = (StorageMachine) helper.getBlockEntity(new BlockPos(3, 2, 3));
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getMaxVoltage() >= 128, "Native energy hatches did not form or set the voltage tier");
        var core = (IRecipeCapabilityMachine) (Object) machine; core.setDistinct(true); core.upDate(); machine.setBatchEnabled(true);
        return machine;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void poweredBatchConsumesEnergyOncePerTickAndOnlyConsumesMaterialInputsOnce(GameTestHelper helper) {
        var machine = poweredBatchController(helper); var input = batchInputs(machine); var logic = machine.getRecipeLogic();
        for (int x : new int[]{2, 4}) ((com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine)
                helper.getBlockEntity(new BlockPos(x, 2, 3))).energyContainer.addEnergy(2000);
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 41)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        var recipe = batchRecipe("native_powered_batch", false);
        com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.putEUContent(recipe.tickInputs,
                new com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack(32));
        helper.assertTrue(logic.checkMatchedRecipeAvailable(recipe), "Powered native batch failed to start");
        var selected = ((IRecipeCapabilityMachine) machine).getActiveRecipeHandle(recipe);
        logic.setSuspendAfterFinish(true);
        for (int tick = 0; tick < 80; tick++) logic.serverTick();
        helper.assertTrue(logic.isSuspend() && machine.getEnergyContainer().getEnergyStored() == 1440 &&
                input.getStackInSlot(0).getCount() == 1 && machine.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[0].getFluidAmount() == 2000,
                "Powered batch multiplied tick energy by batch size, reused materials, or failed to finish");
        helper.assertTrue(selected == ((IRecipeCapabilityMachine) machine).getActiveRecipeHandle(recipe), "EU tick IO overwrote the selected material route");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativePowerFailureSuspendsAfterFiveAttemptsWithoutRepeatingMaterialConsumption(GameTestHelper helper) {
        var machine = poweredBatchController(helper); machine.setBatchEnabled(false);
        var input = batchInputs(machine); var logic = machine.getRecipeLogic();
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        var hatch = (com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine) helper.getBlockEntity(new BlockPos(2, 2, 3));
        hatch.energyContainer.addEnergy(32);
        var recipe = batchRecipe("native_power_failure", false); recipe.duration = 20;
        com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.putEUContent(recipe.tickInputs,
                new com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack(32));
        helper.assertTrue(logic.checkMatchedRecipeAvailable(recipe), "Powered native recipe failed to start");
        logic.serverTick();
        helper.assertTrue(logic.getProgress() == 1 && hatch.energyContainer.getEnergyStored() == 0, "Native first tick failed to consume exactly 32 EU");
        for (int attempt = 0; attempt < 5; attempt++) logic.handleRecipeWorking();
        helper.assertTrue(logic.isSuspend() && logic.getProgress() == 1 && input.getStackInSlot(0).getCount() == 3 &&
                machine.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[0].getFluidAmount() == 0,
                "Core tick routing bypassed native five-attempt power-failure suspension or repeated input consumption");
        helper.succeed();
    }


    private static NativeMachineFixtures.NativeCrossMachine crossController(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 2, 3), GTMachines.ENERGY_INPUT_HATCH[2].getBlock());
        helper.setBlock(new BlockPos(3, 2, 3), NativeMachineFixtures.CROSS.getBlock());
        helper.setBlock(new BlockPos(4, 2, 3), GTMachines.ENERGY_INPUT_HATCH[2].getBlock());
        var machine = (NativeMachineFixtures.NativeCrossMachine) helper.getBlockEntity(new BlockPos(3, 2, 3));
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Cross-recipe fixture did not form");
        for (int x : new int[]{2, 4}) ((com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine)
                helper.getBlockEntity(new BlockPos(x, 2, 3))).energyContainer.addEnergy(2000);
        var core = (IRecipeCapabilityMachine) (Object) machine; core.setDistinct(true); core.upDate();
        return machine;
    }
    private static NotifiableItemStackHandler crossInputs(NativeMachineFixtures.NativeCrossMachine machine) {
        return machine.getTraits(NotifiableItemStackHandler.class).stream().filter(handler -> handler.getHandlerIO() == IO.IN).findFirst().orElseThrow();
    }
    private static void crossSources(NativeMachineFixtures.NativeCrossMachine machine) {
        var first = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_cross_iron").inputItems(Items.IRON_INGOT, 2)
                .notConsumable(Items.DIAMOND).outputFluids(SizedFluidIngredient.of(Fluids.WATER, 100)).duration(2).EUt(32).build();
        var second = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_cross_gold").inputItems(Items.GOLD_INGOT)
                .notConsumable(Items.DIAMOND).outputFluids(SizedFluidIngredient.of(Fluids.WATER, 50)).duration(4).EUt(16).build();
        ((NativeMachineFixtures.CrossFixtureLogic) machine.getRecipeLogic()).sources = java.util.List.of(first, second);
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void crossRecipeParallelCombinesDifferentRecipesThroughNativeTicksWithoutMutatingSources(GameTestHelper helper) {
        var machine = crossController(helper); var input = crossInputs(machine); var logic = machine.getRecipeLogic(); crossSources(machine);
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 9)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        input.storage.setStackInSlot(2, new ItemStack(Items.GOLD_INGOT, 3)); logic.findAndHandleRecipe();
        helper.assertTrue(logic.isWorking() && logic.getDuration() == 20 && input.getStackInSlot(0).getCount() == 1 &&
                input.getStackInSlot(2).isEmpty() && input.getStackInSlot(1).is(Items.DIAMOND), "Cross-recipe collection consumed the wrong inputs or NC catalyst");
        long eut = logic.getLastRecipe().getInputEUt().getTotalEU(); logic.setSuspendAfterFinish(true);
        for (int tick = 0; tick < 20; tick++) logic.serverTick();
        helper.assertTrue(logic.isSuspend() && machine.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[0].getFluidAmount() == 550 &&
                machine.getEnergyContainer().getEnergyStored() == 4000 - 20 * eut, "Cross-recipe output or native tick energy was duplicated or dropped");
        helper.assertTrue(((NativeMachineFixtures.CrossFixtureLogic) logic).sources.stream().allMatch(recipe ->
                IGTRecipe.of(recipe).getRealParallels() == 1 && !IGTRecipe.of(recipe).isBatchProcessed()), "Cross-recipe collection modified registered source recipes");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void crossRecipeBlockedOutputSurvivesNativeSaveAndResumesWithoutNewMaterialConsumption(GameTestHelper helper) {
        var machine = crossController(helper); var input = crossInputs(machine); var logic = machine.getRecipeLogic(); crossSources(machine);
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 9)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        input.storage.setStackInSlot(2, new ItemStack(Items.GOLD_INGOT, 3));
        var tank = machine.getTraits(NotifiableFluidTank.class).getFirst(); tank.getStorages()[0].setFluid(new FluidStack(Fluids.WATER, 9900));
        logic.findAndHandleRecipe(); helper.assertTrue(logic.getLastRecipe() != null, "Blocked combined output discarded already-consumed materials");
        for (int tick = 0; tick < 20; tick++) logic.serverTick();
        helper.assertTrue(logic.isWaiting() && tank.getStorages()[0].getFluidAmount() == 9900 && input.getStackInSlot(0).getCount() == 1,
                "Cross-recipe work was discarded or output twice while blocked");
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess()); machine.loadAdditional(saved, helper.getLevel().registryAccess());
        tank.getStorages()[0].setFluid(FluidStack.EMPTY); logic.setSuspendAfterFinish(true); logic.serverTick();
        helper.assertTrue(logic.isSuspend() && tank.getStorages()[0].getFluidAmount() == 550 && input.getStackInSlot(0).getCount() == 1 && input.getStackInSlot(2).isEmpty(),
                "Saved cross-recipe work did not resume its exact pending output once");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void crossRecipeLockRestrictsTheCombinedRunToTheSelectedRecipe(GameTestHelper helper) {
        var machine = crossController(helper); var input = crossInputs(machine); var logic = machine.getRecipeLogic(); crossSources(machine);
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 9)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        input.storage.setStackInSlot(2, new ItemStack(Items.GOLD_INGOT, 3));
        var lock = (ILockRecipe) logic; lock.setLock(true); lock.setLockRecipe(((NativeMachineFixtures.CrossFixtureLogic) logic).sources.getFirst());
        logic.findAndHandleRecipe(); logic.setSuspendAfterFinish(true);
        for (int tick = 0; tick < 20; tick++) logic.serverTick();
        helper.assertTrue(logic.isSuspend() && input.getStackInSlot(0).getCount() == 1 && input.getStackInSlot(2).getCount() == 3 &&
                machine.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[0].getFluidAmount() == 400,
                "Cross-recipe locking allowed another source recipe into the combined run");
        helper.succeed();
    }


    private static org.gtlcore.gtlcore.common.machine.multiblock.steam.LargeSteamParallelMultiblockMachine steamController(GameTestHelper helper, boolean large) {
        helper.setBlock(new BlockPos(2, 2, 3), large ? NativeMachineFixtures.STEAM_HATCH.getBlock() : GTMachines.STEAM_HATCH.getBlock());
        helper.setBlock(new BlockPos(3, 2, 3), NativeMachineFixtures.STEAM.getBlock()); helper.setBlock(new BlockPos(4, 2, 3), Blocks.IRON_BLOCK);
        var machine = (org.gtlcore.gtlcore.common.machine.multiblock.steam.LargeSteamParallelMultiblockMachine) helper.getBlockEntity(new BlockPos(3, 2, 3));
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.isOC() == large, "Core steam fixture did not form or detect its large steam hatch");
        var core = (IRecipeCapabilityMachine) (Object) machine; core.setDistinct(true); core.upDate();
        return machine;
    }
    private static GTRecipe steamRecipe(String id) {
        return GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder(id).inputItems(Items.IRON_INGOT, 2).notConsumable(Items.DIAMOND)
                .outputFluids(SizedFluidIngredient.of(Fluids.WATER, 100)).duration(20).EUt(8).build();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void coreSteamUsesTwoMillibucketsPerEUAndKeepsTheEnergyHatchOutOfRecipeFluidInputs(GameTestHelper helper) {
        var machine = steamController(helper, false); var logic = machine.getRecipeLogic();
        var input = machine.getTraits(NotifiableItemStackHandler.class).getFirst();
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 15)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        var hatch = (com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine) helper.getBlockEntity(new BlockPos(2, 2, 3));
        hatch.tank.getStorages()[0].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Steam.getFluid(5000));
        helper.assertTrue(machine.getCapabilitiesFlat(IO.IN, FluidRecipeCapability.CAP).stream().noneMatch(handler -> handler == hatch.tank),
                "Dedicated steam tank remained in native recipe-fluid inputs");
        var consumesSteam = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_reserved_steam_tank")
                .inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.Steam.asSingleFluidIngredient(1)).duration(1).build();
        helper.assertTrue(!RecipeRunnerHelper.matchRecipeInput(machine, consumesSteam), "Core distinct routing reused the dedicated steam-energy tank as recipe stock");
        var source = steamRecipe("native_core_steam_cycle");
        helper.assertTrue(logic.checkMatchedRecipeAvailable(source) && IGTRecipe.of(logic.getLastRecipe()).getRealParallels() == 7 &&
                input.getStackInSlot(0).getCount() == 1 && logic.getLastRecipe().getInputEUt().getTotalEU() == 56,
                "Core steam modifier chose the wrong parallel count or energy");
        logic.setSuspendAfterFinish(true); for (int tick = 0; tick < 20; tick++) logic.serverTick();
        helper.assertTrue(logic.isSuspend() && hatch.tank.getStorages()[0].getFluidAmount() == 2760 &&
                machine.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[0].getFluidAmount() == 700,
                "Core steam conversion reversed EU/mB, miscounted ticks, or repeated the output");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void steamOverclockChangesApplyToTheNextRecipeAndNativeSaveRestoresTheSelectedLevel(GameTestHelper helper) {
        var machine = steamController(helper, true); var logic = machine.getRecipeLogic(); machine.setAmountOC(2);
        var input = machine.getTraits(NotifiableItemStackHandler.class).getFirst();
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 7)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        var hatch = (com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine) helper.getBlockEntity(new BlockPos(2, 2, 3));
        hatch.tank.getStorages()[0].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Steam.getFluid(1000));
        helper.assertTrue(logic.checkMatchedRecipeAvailable(steamRecipe("native_core_steam_oc")) && logic.getDuration() == 5 &&
                logic.getLastRecipe().getInputEUt().getTotalEU() == 24, "Core steam OC speed or energy changed");
        machine.setAmountOC(4); logic.setSuspendAfterFinish(true);
        for (int tick = 0; tick < 5; tick++) logic.serverTick();
        helper.assertTrue(logic.isSuspend() && hatch.tank.getStorages()[0].getFluidAmount() == 970 &&
                machine.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[0].getFluidAmount() == 300,
                "Changing OC altered an already-started recipe's steam cost or output");
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess()); machine.setAmountOC(0); machine.loadAdditional(saved, helper.getLevel().registryAccess());
        helper.assertTrue(machine.getAmountOC() == 4 && machine.getConversionRate() == 40.5,
                "Native steam OC SaveField did not restore the selected conversion rate");
        var energy = machine.getCapabilitiesFlat(IO.IN, com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP).getFirst();
        helper.assertTrue((long) energy.getContents().getFirst() == 39_285, "Steam parallel budget used the pre-load OC level or inverted the conversion rate");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeSimpleSteamPressurePreservesTheOldConsumptionAndTime(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 3), NativeMachineFixtures.LOW_PRESSURE.getBlock());
        helper.setBlock(new BlockPos(6, 2, 3), NativeMachineFixtures.HIGH_PRESSURE.getBlock());
        helper.runAfterDelay(2, () -> {
        for (boolean high : new boolean[] { false, true }) {
            var pos = new BlockPos(high ? 6 : 3, 2, 3);
            var machine = (com.gregtechceu.gtceu.api.machine.steam.SimpleSteamMachine) helper.getBlockEntity(pos);
            machine.setFrontFacing(Direction.NORTH);
            machine.steamTank.getStorages()[0].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Steam.getFluid(1000));
            machine.importItems.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            var recipe = GTRecipeTypes.FURNACE_RECIPES.recipeBuilder("native_pressure_" + high).inputItems(Items.IRON_INGOT)
                    .outputItems(Items.GOLD_INGOT).duration(10).EUt(8).build();
            var logic = machine.getRecipeLogic();
            helper.assertTrue(logic.checkMatchedRecipeAvailable(recipe) && logic.getDuration() == (high ? 10 : 20), "Native pressure changed steam recipe time: pressure=" + high + " duration=" + logic.getDuration() + " inputs=" + machine.getCapabilitiesProxy());
            helper.assertTrue(logic.getLastRecipe().getInputEUt().getTotalEU() == 8, "Pressure doubled EU as well as mB/EU");
            logic.setSuspendAfterFinish(true);
            for (int tick = 0; tick < (high ? 10 : 20); tick++) logic.serverTick();
            helper.assertTrue(logic.isSuspend() && machine.steamTank.getStorages()[0].getFluidAmount() == 840 &&
                    machine.importItems.getStackInSlot(0).getCount() == 1 && machine.exportItems.getStackInSlot(0).is(Items.GOLD_INGOT),
                    "Native steam pressure consumed the wrong amount or repeated its output");
        }
        helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeSteamVentingAndVoltageRejectionPreserveInputs(GameTestHelper helper) {
        var pos = new BlockPos(3, 2, 3); helper.setBlock(pos, NativeMachineFixtures.HIGH_PRESSURE.getBlock());
        helper.runAfterDelay(2, () -> {
        var machine = (com.gregtechceu.gtceu.api.machine.steam.SimpleSteamMachine) helper.getBlockEntity(pos);
        machine.setFrontFacing(Direction.NORTH); machine.getExhaustVentTrait().setVentingDirection(Direction.SOUTH);
        machine.importItems.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        machine.steamTank.getStorages()[0].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Steam.getFluid(1000));
        var recipe = GTRecipeTypes.FURNACE_RECIPES.recipeBuilder("native_venting").inputItems(Items.IRON_INGOT).outputItems(Items.GOLD_INGOT).duration(10).EUt(8).build();
        helper.setBlock(pos.south(), Blocks.IRON_BLOCK); machine.getExhaustVentTrait().setNeedsVenting(true);
        helper.assertTrue(!machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe), "Steam machine started with a blocked exhaust");
        helper.setBlock(pos.south(), Blocks.AIR);
        var tooHigh = recipe.copy(); com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.putEUContent(tooHigh.tickInputs, new com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack(128));
        helper.assertTrue(!machine.getRecipeLogic().checkMatchedRecipeAvailable(tooHigh), "Steam machine accepted a recipe above LV");
        helper.assertTrue(machine.importItems.getStackInSlot(0).getCount() == 2 && machine.steamTank.getStorages()[0].getFluidAmount() == 1000,
                "Rejected steam recipe consumed materials or energy");
        helper.assertTrue(machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe), "Clearing the exhaust did not restore native steam operation");
        helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeHeatExchangeKeepsCoolantUntilOutputsFitAndConsumesItOnceAtSetup(GameTestHelper helper) {
        var pos = new BlockPos(3, 2, 3); helper.setBlock(pos, NativeMachineFixtures.HEAT_EXCHANGER.getBlock());
        var machine = (org.gtlcore.gtlcore.common.machine.multiblock.noenergy.HeatExchangerMachine) helper.getBlockEntity(pos);
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Native heat exchanger fixture failed to form");
        var tanks = machine.getTraits(NotifiableFluidTank.class); var input = tanks.get(0); var output = tanks.get(1);
        input.getStorages()[0].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Helium.getFluid(com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys.PLASMA, 5000));
        input.getStorages()[1].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.DistilledWater.getFluid(43));
        output.getStorages()[0].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Helium.getFluid(19_001));
        output.getStorages()[1].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Steam.getFluid(19_999));
        var recipe = org.gtlcore.gtlcore.common.data.GTLRecipeTypes.HEAT_EXCHANGER_RECIPES.recipeBuilder("native_distilled_cooling")
                .inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.Helium.getFluid(com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys.PLASMA, 1000), com.gregtechceu.gtceu.common.data.GTMaterials.DistilledWater.getFluid(10))
                .outputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.Helium.getFluid(1000), com.gregtechceu.gtceu.common.data.GTMaterials.Steam.getFluid(1600)).duration(50).build();
        helper.assertTrue(machine.fullModifyRecipe(recipe) == null && machine.fullModifyRecipe(recipe) == null, "Blocked heat exchange was accepted");
        helper.assertTrue(input.getStorages()[0].getFluidAmount() == 5000 && input.getStorages()[1].getFluidAmount() == 43,
                "Recipe modification consumed plasma or coolant before setup");
        output.getStorages()[0].setFluid(FluidStack.EMPTY); output.getStorages()[1].setFluid(FluidStack.EMPTY);
        var preview = machine.fullModifyRecipe(recipe);
        helper.assertTrue(preview != null && IGTRecipe.of(preview).getRealParallels() == 4 && preview.duration == 200 &&
                input.getStorages()[1].getFluidAmount() == 43, "Heat exchange preview failed to limit parallels by available coolant");
        var logic = machine.getRecipeLogic(); helper.assertTrue(logic.checkMatchedRecipeAvailable(recipe), "Feasible heat exchange did not start");
        helper.assertTrue(input.getStorages()[0].getFluidAmount() == 1000 && input.getStorages()[1].getFluidAmount() == 3,
                "Heat exchange setup did not consume each input once");
        logic.setSuspendAfterFinish(true); for (int tick = 0; tick < 200; tick++) logic.serverTick();
        helper.assertTrue(logic.isSuspend() && output.getStorages()[0].getFluidAmount() == 4000 && output.getStorages()[1].getFluidAmount() == 6400 && recipe.duration == 50,
                "Heat exchange output, duration or source recipe changed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeCoilCrossRecipeRejectsMixedCoilsAndResetsBeforeReformation(GameTestHelper helper) {
        var coils = com.gregtechceu.gtceu.api.GTCEuAPI.HEATING_COILS;
        var cupronickel = com.gregtechceu.gtceu.common.block.CoilBlock.CoilType.CUPRONICKEL;
        var kanthal = com.gregtechceu.gtceu.common.block.CoilBlock.CoilType.KANTHAL;
        helper.setBlock(new BlockPos(2, 2, 3), coils.get(kanthal).get()); helper.setBlock(new BlockPos(4, 2, 3), coils.get(kanthal).get());
        helper.setBlock(new BlockPos(3, 2, 3), NativeMachineFixtures.COIL_CROSS.getBlock());
        var machine = (org.gtlcore.gtlcore.common.machine.multiblock.electric.CoilWorkableElectricMultipleRecipesMultiblockMachine) helper.getBlockEntity(new BlockPos(3, 2, 3));
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getCoilType() == kanthal && machine.getCoilTier() == kanthal.getTier() &&
                machine.getMaxParallel() == (int) Math.pow(2, kanthal.getCoilTemperature() / 900.0), "Matched native coil did not determine cross-recipe parallel limit");
        helper.setBlock(new BlockPos(2, 2, 3), coils.get(cupronickel).get()); machine.checkAndFormStructure();
        helper.assertTrue(!machine.isFormed() && machine.getCoilType() == cupronickel, "Mixed coils remained formed or retained the old parallel limit");
        helper.setBlock(new BlockPos(4, 2, 3), coils.get(cupronickel).get()); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getCoilType() == cupronickel && machine.getMaxParallel() == 4,
                "Native coil cross-recipe controller failed to reform after repair");
        helper.succeed();
    }

    private static org.gtlcore.gtlcore.common.machine.multiblock.generator.GeneratorArrayMachine generatorArray(GameTestHelper helper, boolean wireless) {
        helper.setBlock(new BlockPos(2, 2, 3), wireless ? Blocks.IRON_BLOCK : GTMachines.ENERGY_OUTPUT_HATCH[com.gregtechceu.gtceu.api.GTValues.LV].getBlock());
        helper.setBlock(new BlockPos(4, 2, 3), wireless ? Blocks.IRON_BLOCK : GTMachines.ENERGY_OUTPUT_HATCH[com.gregtechceu.gtceu.api.GTValues.LV].getBlock());
        helper.setBlock(new BlockPos(3, 2, 3), NativeMachineFixtures.GENERATOR_ARRAY.getBlock());
        var machine = (org.gtlcore.gtlcore.common.machine.multiblock.generator.GeneratorArrayMachine) helper.getBlockEntity(new BlockPos(3, 2, 3));
        machine.setMachineStorageItem(GTMachines.GAS_TURBINE[com.gregtechceu.gtceu.api.GTValues.LV].asStack(2));
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getRecipeType() == GTRecipeTypes.GAS_TURBINE_FUELS.value() && machine.getTier() == 1,
                "Inserted native gas turbines did not determine array recipe type and tier");
        machine.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[0].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(1000));
        machine.setWireless(wireless); return machine;
    }
    private static GTRecipe arrayFuel(String id) {
        return GTRecipeTypes.GAS_TURBINE_FUELS.recipeBuilder(id).inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(100)).duration(10).EUt(-32).build();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeGeneratorArrayFeedsPhysicalDynamoAndRetainsInsertedMachines(GameTestHelper helper) {
        var machine = generatorArray(helper, false); var source = arrayFuel("native_wired_array"); var logic = machine.getRecipeLogic();
        helper.assertTrue(logic.checkMatchedRecipeAvailable(source) && IGTRecipe.of(logic.getLastRecipe()).getRealParallels() == 10 &&
                logic.getLastRecipe().getOutputEUt().getTotalEU() == 320, "Native array chose the wrong fluid or output parallel budget: status=" + logic.getStatus() +
                        " reason=" + logic.getBestFailureReason() + " recipe=" + logic.getLastRecipe());
        logic.setSuspendAfterFinish(true); for (int tick = 0; tick < 10; tick++) logic.serverTick();
        helper.assertTrue(logic.isSuspend() && machine.getEnergyContainer().getEnergyStored() == 3200 && machine.getMachineStorageItem().getCount() == 2 &&
                machine.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[0].getFluidAmount() == 0,
                "Physical array lost energy, consumed installed machines or repeated fuel consumption");
        helper.assertTrue(!source.tickOutputs.isEmpty() && source.getOutputEUt().getTotalEU() == 32, "Native array mutated the registered fuel recipe");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeWirelessArrayWaitsForRateLimitAndKeepsItsAccountAndModeAcrossSave(GameTestHelper helper) {
        var machine = generatorArray(helper, true); var owner = java.util.UUID.randomUUID(); machine.setOwnerUUID(owner);
        var account = machine.getWirelessEnergyContainer(); helper.assertTrue(account != null, "Native GTMThings world did not initialize wireless accounts");
        var config = com.hepdd.gtmthings.config.ConfigHolder.INSTANCE; boolean original = config.isWirelessRateEnable;
        try {
            config.isWirelessRateEnable = true; account.setRate(1); var source = arrayFuel("native_wireless_array"); var logic = machine.getRecipeLogic();
            helper.assertTrue(!logic.checkMatchedRecipeAvailable(source) && machine.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[0].getFluidAmount() == 1000,
                    "Rate-limited wireless setup consumed fuel");
            account.setRate(320); helper.assertTrue(logic.checkMatchedRecipeAvailable(source), "Native wireless array did not start after its rate limit was raised: status=" +
                    logic.getStatus() + " reason=" + logic.getBestFailureReason());
            helper.assertTrue(logic.getLastRecipe().getOutputEUt().isEmpty(), "Wireless array retained cached physical EU output after redirecting it to the account");
            logic.serverTick(); helper.assertTrue(account.getStorage().longValueExact() == 320 && logic.getProgress() == 1, "Native account did not receive one recipe tick");
            account.setRate(1); logic.serverTick(); helper.assertTrue(logic.isWaiting() && logic.getProgress() == 1 && account.getStorage().longValueExact() == 320,
                    "A rate limit partially credited or advanced a blocked wireless tick");
            machine.setWireless(false); machine.setOwnerUUID(java.util.UUID.randomUUID());
            var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess()); machine.loadAdditional(saved, helper.getLevel().registryAccess());
            account.setRate(320); logic.setSuspendAfterFinish(true); for (int tick = 0; tick < 9; tick++) logic.serverTick();
            helper.assertTrue(logic.isSuspend() && account.getStorage().longValueExact() == 3200 && !machine.isWireless() && machine.getMachineStorageItem().getCount() == 2,
                    "Mode/account changes or native save/load altered an in-flight wireless run or duplicated its energy");
            helper.assertTrue(source.getOutputEUt().getTotalEU() == 32 && !source.data.contains(org.gtlcore.gtlcore.common.machine.multiblock.generator.GeneratorArrayMachine.WIRELESS_EUT),
                    "Wireless output leaked into the registered recipe");
        } finally { config.isWirelessRateEnable = original; }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeSimpleGeneratorEmitsItsFullAmperageThroughActualEnergyCapabilities(GameTestHelper helper) {
        var pos = new BlockPos(3, 2, 3); helper.setBlock(pos, GTMachines.GAS_TURBINE[com.gregtechceu.gtceu.api.GTValues.LV].getBlock());
        helper.setBlock(pos.north(), NativeMachineFixtures.ENERGY_RECEIVER.getBlock());
        helper.runAfterDelay(2, () -> {
            var machine = (com.gregtechceu.gtceu.api.machine.SimpleGeneratorMachine) helper.getBlockEntity(pos); machine.setFrontFacing(Direction.NORTH);
            machine.importFluids.getStorages()[0].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(6400));
            var recipe = GTRecipeTypes.GAS_TURBINE_FUELS.recipeBuilder("native_generator_amperage").inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(100)).duration(10).EUt(-4).build();
            var logic = machine.getRecipeLogic(); helper.assertTrue(logic.checkMatchedRecipeAvailable(recipe) && logic.getLastRecipe().getOutputEUt().getTotalEU() == 256 &&
                    IGTRecipe.of(logic.getLastRecipe()).getRealParallels() == 64 && machine.energyContainer.getOutputAmperage() == 8,
                    "Native generator lost its configured eight-amp output while running: status=" + logic.getStatus() + " reason=" + logic.getBestFailureReason() +
                            " amperage=" + machine.energyContainer.getOutputAmperage() + " voltage=" + machine.energyContainer.getOutputVoltage() +
                            " capacity=" + machine.energyContainer.getEnergyCapacity() + " tier=" + machine.getTier() +
                            " energy=" + (logic.getLastRecipe() == null ? -1 : logic.getLastRecipe().getOutputEUt().getTotalEU()) +
                            " parallel=" + (logic.getLastRecipe() == null ? -1 : IGTRecipe.of(logic.getLastRecipe()).getRealParallels()));
            logic.setSuspendAfterFinish(true); logic.serverTick(); machine.energyContainer.serverTick();
            var receiver = (com.gregtechceu.gtceu.api.machine.MetaMachine) helper.getBlockEntity(pos.north());
            helper.assertTrue(receiver.getTraits(com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer.class).getFirst().getEnergyStored() == 256 &&
                    machine.energyContainer.getEnergyStored() == 0, "Native EU capability transfer emitted only a single amp");
            for (int tick = 1; tick < 10; tick++) logic.serverTick();
            helper.assertTrue(logic.isSuspend() && machine.energyContainer.getEnergyStored() == 2304 && machine.importFluids.getStorages()[0].getFluidAmount() == 0,
                    "Native generator output or fuel was lost during the complete cycle");
            helper.succeed();
        });
    }

    private static org.gtlcore.gtlcore.common.machine.multiblock.generator.GTLLargeCombustionEngineMachine largeEngine(GameTestHelper helper, boolean chemical) {
        int outputTier = chemical ? com.gregtechceu.gtceu.api.GTValues.UV : com.gregtechceu.gtceu.api.GTValues.IV;
        helper.setBlock(new BlockPos(3, 4, 4), GTMachines.ENERGY_OUTPUT_HATCH[outputTier].getBlock());
        helper.setBlock(new BlockPos(5, 4, 4), GTMachines.ENERGY_OUTPUT_HATCH[outputTier].getBlock());
        helper.setBlock(new BlockPos(4, 4, 4), (chemical ? NativeMachineFixtures.CHEMICAL_ENGINE : NativeMachineFixtures.COMBUSTION_ENGINE).getBlock());
        var machine = (org.gtlcore.gtlcore.common.machine.multiblock.generator.GTLLargeCombustionEngineMachine) helper.getBlockEntity(new BlockPos(4, 4, 4));
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        if (chemical && machine.isIntakesObstructed()) {
            for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) {
                if (x == 0 && y == 0) continue;
                var intake = com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.offsetPos(machine.getBlockPos(), machine.getFrontFacing(), machine.getUpwardsFacing(), machine.isFlipped(), x, y, 1);
                helper.getLevel().setBlockAndUpdate(intake, Blocks.AIR.defaultBlockState());
            }
        }
        helper.assertTrue(machine.isFormed() && machine.isBoostAllowed() && !machine.isIntakesObstructed(), "Native engine fixture failed to form with unobstructed intake and boost-capable dynamos: formed=" +
                machine.isFormed() + " boostAllowed=" + machine.isBoostAllowed() + " obstructed=" + machine.isIntakesObstructed() + " voltage=" + machine.getMaxVoltage());
        return machine;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeCombustionEngineOnlyConsumesAuxiliaryFluidsOnAcceptedTicksAndRetainsItsTimer(GameTestHelper helper) {
        var engine = largeEngine(helper, false); var tanks = engine.getTraits(NotifiableFluidTank.class).getFirst().getStorages();
        tanks[0].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(100));
        tanks[1].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Lubricant.getFluid(2));
        tanks[2].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Oxygen.getFluid(100));
        var source = GTRecipeTypes.COMBUSTION_GENERATOR_FUELS.recipeBuilder("native_engine_auxiliary").inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(100)).duration(80).EUt(-8192).build();
        var logic = engine.getRecipeLogic(); helper.assertTrue(logic.checkMatchedRecipeAvailable(source), "Native combustion engine rejected valid fuel and lubricant");
        var energy = engine.getEnergyContainer(); energy.changeEnergy(energy.getEnergyCapacity()); logic.serverTick();
        helper.assertTrue(logic.isWaiting() && logic.getProgress() == 0 && tanks[1].getFluidAmount() == 2 && tanks[2].getFluidAmount() == 100,
                "Blocked native engine consumed auxiliary fluids or advanced progress");
        energy.changeEnergy(-energy.getEnergyStored());
        for (int tick = 0; tick < 10; tick++) logic.serverTick();
        helper.assertTrue(logic.getProgress() == 10 && tanks[1].getFluidAmount() == 1 && tanks[2].getFluidAmount() == 80 && engine.isOxygenBoosted(),
                "Native engine did not apply the first accepted tick's lubricant/oxygen rates: status=" + logic.getStatus() + " progress=" + logic.getProgress() +
                        " lube=" + tanks[1].getFluidAmount() + " oxygen=" + tanks[2].getFluidAmount() + " boost=" + engine.isOxygenBoosted() + " reason=" + logic.getBestFailureReason());
        var saved = engine.saveWithoutMetadata(helper.getLevel().registryAccess()); engine.loadAdditional(saved, helper.getLevel().registryAccess());
        logic.setSuspendAfterFinish(true); for (int tick = 10; tick < 80; tick++) logic.serverTick();
        helper.assertTrue(logic.isSuspend() && tanks[0].getFluidAmount() == 0 && tanks[1].getFluidAmount() == 0 && tanks[2].getFluidAmount() == 0 &&
                        engine.getEnergyContainer().getEnergyStored() == 655360 && source.getOutputEUt().getTotalEU() == 8192,
                "Native engine auxiliary timer, frozen in-flight output or registered fuel changed after save/load");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeChemicalEngineCombinesBothBoostersAndRejectsAnObstructedIntake(GameTestHelper helper) {
        var engine = (org.gtlcore.gtlcore.common.machine.multiblock.generator.ChemicalEnergyDevourerMachine) largeEngine(helper, true);
        var tanks = engine.getTraits(NotifiableFluidTank.class).getFirst().getStorages();
        tanks[0].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(100));
        tanks[1].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Lubricant.getFluid(4));
        tanks[2].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Oxygen.getFluid(com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys.LIQUID, 240));
        tanks[3].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.DinitrogenTetroxide.getFluid(160));
        var source = GTRecipeTypes.COMBUSTION_GENERATOR_FUELS.recipeBuilder("native_chemical_auxiliary").inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(100)).duration(20).EUt(-262144).build();
        var logic = engine.getRecipeLogic(); helper.assertTrue(logic.checkMatchedRecipeAvailable(source), "Native chemical engine rejected valid fuel");
        logic.serverTick(); helper.assertTrue(engine.isOxygenBoosted() && engine.isDinitrogenTetroxideBoosted() &&
                tanks[1].getFluidAmount() == 2 && tanks[2].getFluidAmount() == 120 && tanks[3].getFluidAmount() == 80, "Chemical engine lost a booster or used incorrect auxiliary rates");
        var intake = com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.offsetPos(engine.getBlockPos(), engine.getFrontFacing(), engine.getUpwardsFacing(), engine.isFlipped(), 3, 3, 1);
        helper.getLevel().setBlockAndUpdate(intake, Blocks.IRON_BLOCK.defaultBlockState()); long stored = engine.getEnergyContainer().getEnergyStored();
        logic.serverTick(); helper.assertTrue(logic.isWaiting() && logic.getProgress() == 1 && engine.getEnergyContainer().getEnergyStored() == stored && tanks[2].getFluidAmount() == 120,
                "Obstructed chemical engine emitted energy or consumed boosters");
        helper.getLevel().setBlockAndUpdate(intake, Blocks.AIR.defaultBlockState());
        var saved = engine.saveWithoutMetadata(helper.getLevel().registryAccess()); engine.loadAdditional(saved, helper.getLevel().registryAccess());
        logic.setSuspendAfterFinish(true); for (int tick = 1; tick < 20; tick++) logic.serverTick();
        helper.assertTrue(logic.isSuspend() && engine.isOxygenBoosted() && engine.isDinitrogenTetroxideBoosted() && tanks[2].getFluidAmount() == 0 && tanks[3].getFluidAmount() == 0,
                "Chemical engine boosters or timer changed on restore");
        tanks[0].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(400));
        var boosted = org.gtlcore.gtlcore.common.machine.multiblock.generator.ChemicalEnergyDevourerMachine.recipeModifier(engine, source).apply(source);
        helper.assertTrue(boosted != null && IGTRecipe.of(boosted).getRealParallels() == 4 && boosted.getOutputEUt().getTotalEU() == 4194304 && source.getOutputEUt().getTotalEU() == 262144,
                "Joint chemical boost or native parallel scaling lost the original production multiplier: oxygen=" + engine.isOxygenBoosted() + " nitro=" + engine.isDinitrogenTetroxideBoosted() +
                        " voltage=" + engine.getOverclockVoltage() + " output=" + (boosted == null ? -1 : boosted.getOutputEUt().getTotalEU()) +
                        " parallel=" + (boosted == null ? -1 : IGTRecipe.of(boosted).getRealParallels()) + " original=" + source.getOutputEUt().getTotalEU());
        helper.succeed();
    }

    private static ItemStack nativeRotor(com.gregtechceu.gtceu.api.data.chemical.material.Material material) {
        var stack = com.gregtechceu.gtceu.common.data.GTItems.TURBINE_ROTOR.asStack();
        com.gregtechceu.gtceu.common.item.behavior.TurbineRotorBehaviour.getBehaviour(stack).setPartMaterial(stack, material); return stack;
    }
    private static org.gtlcore.gtlcore.common.machine.multiblock.generator.MegaTurbineMachine megaTurbine(GameTestHelper helper) {
        for (int x = 2; x <= 3; x++) helper.setBlock(new BlockPos(x, 4, 4), GTMachines.ROTOR_HOLDER[com.gregtechceu.gtceu.api.GTValues.IV].getBlock());
        helper.setBlock(new BlockPos(4, 4, 4), NativeMachineFixtures.MEGA_TURBINE.getBlock());
        helper.setBlock(new BlockPos(5, 4, 4), NativeMachineFixtures.ROTOR_HATCH.getBlock());
        helper.setBlock(new BlockPos(6, 4, 4), GTMachines.ENERGY_OUTPUT_HATCH[com.gregtechceu.gtceu.api.GTValues.UV].getBlock());
        var turbine = (org.gtlcore.gtlcore.common.machine.multiblock.generator.MegaTurbineMachine) helper.getBlockEntity(new BlockPos(4, 4, 4));
        turbine.setFrontFacing(Direction.NORTH); turbine.checkAndFormStructure();
        helper.assertTrue(turbine.isFormed() && turbine.getParts().size() == 4, "Native turbine did not discover two rotor holders, reserve hatch and dynamo: formed=" + turbine.isFormed() +
                " parts=" + turbine.getParts().stream().map(part -> part.getClass().getSimpleName()).toList()); return turbine;
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeMegaTurbineRefillsRotorsAndGeneratesWithoutMutatingTheFuelRecipe(GameTestHelper helper) {
        var turbine = megaTurbine(helper);
        helper.runAfterDelay(2, () -> {
            var rotors = turbine.getParts().stream().filter(com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine.class::isInstance)
                    .map(com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine.class::cast).toList();
            rotors.getFirst().setRotorStack(nativeRotor(com.gregtechceu.gtceu.common.data.GTMaterials.Aluminium));
            var reserve = (org.gtlcore.gtlcore.common.machine.multiblock.part.RotorHatchPartMachine) helper.getBlockEntity(new BlockPos(5, 4, 4));
            helper.assertTrue(!reserve.getInventory().isItemValid(0, new ItemStack(Items.DIAMOND)), "Reserve rotor hatch accepted a non-rotor item");
            reserve.getInventory().setStackInSlot(0, nativeRotor(com.gregtechceu.gtceu.common.data.GTMaterials.Aluminium));
            var savedReserve = reserve.saveWithoutMetadata(helper.getLevel().registryAccess()); reserve.getInventory().setStackInSlot(0, ItemStack.EMPTY);
            reserve.loadAdditional(savedReserve, helper.getLevel().registryAccess()); turbine.refillRotors();
            helper.assertTrue(rotors.stream().allMatch(com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine::hasRotor) && reserve.getInventory().isEmpty(),
                    "Reserve restoration/refill duplicated a rotor or failed to insert it");
            rotors.forEach(rotor -> rotor.setRotorSpeed(rotor.getMaxRotorHolderSpeed()));
            turbine.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[0].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(1000));
            var source = GTRecipeTypes.GAS_TURBINE_FUELS.recipeBuilder("native_mega_fuel").inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(100)).duration(10).EUt(-8192).build();
            var modifier = org.gtlcore.gtlcore.common.machine.multiblock.generator.MegaTurbineMachine.recipeModifier(turbine, source);
            var preview = modifier.apply(source); var again = org.gtlcore.gtlcore.common.machine.multiblock.generator.MegaTurbineMachine.recipeModifier(turbine, source).apply(source);
            helper.assertTrue(preview != null && again != null && preview.getOutputEUt().getTotalEU() == 57344 && again.getOutputEUt().getTotalEU() == 57344 &&
                    !source.data.contains("gtlcore:turbine_excess_voltage"), "Turbine preview changed fuel-rounding carry, source metadata or cached EU output");
            var logic = turbine.getRecipeLogic(); helper.assertTrue(logic.checkMatchedRecipeAvailable(source) && IGTRecipe.of(logic.getLastRecipe()).getRealParallels() == 5,
                    "Native turbine did not accept its full-speed five-parallel fuel");
            long output = logic.getLastRecipe().getOutputEUt().getTotalEU(); logic.setSuspendAfterFinish(true); for (int tick = 0; tick < 10; tick++) logic.serverTick();
            helper.assertTrue(logic.isSuspend() && turbine.getEnergyContainer().getEnergyStored() == output * 10 &&
                    turbine.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[0].getFluidAmount() == 500 && source.getOutputEUt().getTotalEU() == 8192,
                    "Native turbine lost physical output, consumed fuel twice or changed the registered fuel");
            helper.succeed();
        });
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeMegaTurbineRejectsMixedMaterialsAndSpinsEachRotorOnlyOncePerTick(GameTestHelper helper) {
        var turbine = megaTurbine(helper);
        helper.runAfterDelay(2, () -> {
            var rotors = turbine.getParts().stream().filter(com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine.class::isInstance)
                    .map(com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine.class::cast).toList();
            rotors.getFirst().setRotorStack(nativeRotor(com.gregtechceu.gtceu.common.data.GTMaterials.Aluminium));
            rotors.getLast().setRotorStack(nativeRotor(com.gregtechceu.gtceu.common.data.GTMaterials.Steel));
            turbine.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[0].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(1000));
            var source = GTRecipeTypes.GAS_TURBINE_FUELS.recipeBuilder("native_mega_rotor_types").inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(100)).duration(10).EUt(-8192).build();
            var logic = turbine.getRecipeLogic(); helper.assertTrue(!logic.checkMatchedRecipeAvailable(source) &&
                    turbine.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[0].getFluidAmount() == 1000, "Mixed turbine rotor materials consumed fuel");
            var replacement = nativeRotor(com.gregtechceu.gtceu.common.data.GTMaterials.Aluminium); replacement.setDamageValue(1);
            rotors.getLast().setRotorStack(replacement);
            helper.assertTrue(logic.checkMatchedRecipeAvailable(source) && logic.getLastRecipe().getOutputEUt().isEmpty(), "Matching rotor materials with different wear were rejected or zero-speed output was cached incorrectly");
            logic.setSuspendAfterFinish(true); for (int tick = 0; tick < 10; tick++) logic.serverTick();
            helper.assertTrue(logic.isSuspend() && rotors.stream().allMatch(rotor -> rotor.getRotorSpeed() == 10), "A rotor received duplicate native working callbacks");
            turbine.invalidateStructure("main"); helper.assertTrue(!turbine.isFormed() && turbine.getOverclockVoltage() == 0, "Invalid turbine retained its rotor power");
            helper.succeed();
        });
    }

    /** Build the preview of the real GT registration, then form that exact production pattern. */
    private static com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine registeredGenerator(
            GameTestHelper helper, com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry<com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition> entry) {
        var definition = entry.get(); var pattern = definition.getStructurePatterns().get("main").get();
        var repeats = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
        var slices = ((com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern) pattern).getSlices();
        for (int index = 0; index < slices.length; index++) repeats.put(index, slices[index].getMinRepeats());
        var preview = com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper.blockPattern(repeats);
        var blocks = new java.util.LinkedHashMap<BlockPos, com.gregtechceu.gtceu.api.multiblock.util.BlockInfo>();
        preview.populate(blocks, pattern, null, Direction.NORTH, Direction.UP, false);
        var fluidBlock = GTMachines.FLUID_IMPORT_HATCH_4X[com.gregtechceu.gtceu.api.GTValues.IV].getBlock();
        var fluidPos = blocks.entrySet().stream().filter(e -> !(e.getValue().getBlockState().getBlock() instanceof com.gregtechceu.gtceu.api.block.MetaMachineBlock))
                .filter(e -> preview.getPredicateFromPos(pattern, e.getKey(), Direction.NORTH, Direction.UP, false).getCandidates()
                        .stream().flatMap(java.util.Collection::stream).anyMatch(info -> info.getBlockState().is(fluidBlock)))
                .map(java.util.Map.Entry::getKey).findFirst().orElseThrow();
        blocks.put(fluidPos, com.gregtechceu.gtceu.api.multiblock.util.BlockInfo.fromBlock(fluidBlock));
        var origin = new BlockPos(3, 4, 3); BlockPos controller = null;
        for (var e : blocks.entrySet()) {
            var state = e.getValue().getBlockState(); var block = state.getBlock();
            if (com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.OUTPUT_ENERGY.isApplicable(block)) {
                state = GTMachines.ENERGY_OUTPUT_HATCH[com.gregtechceu.gtceu.api.GTValues.UV].getBlock().defaultBlockState();
            }
            helper.setBlock(origin.offset(e.getKey()), state);
            if (block == entry.getBlock()) controller = origin.offset(e.getKey());
        }
        helper.assertTrue(controller != null, "Native preview omitted the registered controller");
        var machine = (com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine) helper.getBlockEntity(controller);
        machine.setFrontFacing(Direction.NORTH);
        var fluid = (com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine) helper.getBlockEntity(origin.offset(fluidPos));
        fluid.setFrontFacing(Direction.SOUTH);
        for (var e : blocks.entrySet()) {
            var entity = helper.getLevel().getBlockEntity(helper.absolutePos(origin.offset(e.getKey())));
            if (entity instanceof com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine rotor) {
                var facing = rotor.getFrontFacing();
                var up = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
                for (int left = -1; left <= 1; left++) for (int height = -1; height <= 1; height++) {
                    var front = com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.offsetPos(rotor.getBlockPos(), facing, up, false, height, left, 1);
                    helper.getLevel().setBlockAndUpdate(front, Blocks.AIR.defaultBlockState());
                }
                rotor.setRotorStack(nativeRotor(com.gregtechceu.gtceu.common.data.GTMaterials.Aluminium));
                rotor.setRotorSpeed(rotor.getMaxRotorHolderSpeed());
            } else if (entity instanceof com.gregtechceu.gtceu.common.machine.multiblock.part.MufflerPartMachine muffler) {
                helper.getLevel().setBlockAndUpdate(muffler.getBlockPos().relative(muffler.getFrontFacing()), Blocks.AIR.defaultBlockState());
            } else if (entity instanceof com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine maintenance) {
                maintenance.setMaintenanceProblems(com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine.NO_PROBLEMS);
            }
        }
        machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Actual registered generator pattern failed to form: " + entry.getId() + " error=" + machine.getDefaultPatternState().getErrors());
        helper.assertTrue(machine.getParts().contains(fluid), "Production generator did not attach its real fluid hatch");
        return machine;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void originalRegisteredCombustionEngineFormsWorksAndReloadsItsNativeStorage(GameTestHelper helper) {
        var engine = (org.gtlcore.gtlcore.common.machine.multiblock.generator.GTLLargeCombustionEngineMachine) registeredGenerator(helper,
                com.gregtechceu.gtceu.common.data.machines.GTMultiMachines.LARGE_COMBUSTION_ENGINE);
        helper.runAfterDelay(2, () -> {
            var hatch = engine.getParts().stream().filter(com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine.class::isInstance)
                    .map(com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine.class::cast).findFirst().orElseThrow();
            var tanks = hatch.tank.getStorages();
            tanks[0].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(100));
            tanks[1].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Lubricant.getFluid(1));
            var source = GTRecipeTypes.COMBUSTION_GENERATOR_FUELS.recipeBuilder("native_registered_combustion")
                    .inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(100)).duration(10).EUt(-8192).build();
            var logic = engine.getRecipeLogic(); helper.assertTrue(logic.checkMatchedRecipeAvailable(source), "Registered engine refused real hatch fuel: " + logic.getBestFailureReason());
            logic.setSuspendAfterFinish(true); for (int tick = 0; tick < 10; tick++) logic.serverTick();
            helper.assertTrue(logic.isSuspend() && tanks[0].getFluidAmount() == 0 && tanks[1].getFluidAmount() == 0 &&
                    engine.getEnergyContainer().getEnergyStored() == 81920, "Registered engine failed its physical fuel cycle");
            var saved = engine.saveWithoutMetadata(helper.getLevel().registryAccess()); engine.loadAdditional(saved, helper.getLevel().registryAccess());
            helper.assertTrue(engine.getEnergyContainer().getEnergyStored() == 81920, "Registered engine lost native dynamo energy after load");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void originalRegisteredGasTurbineFormsWorksAndRejectsMissingRotor(GameTestHelper helper) {
        var turbine = (org.gtlcore.gtlcore.common.machine.multiblock.generator.MegaTurbineMachine) registeredGenerator(helper,
                com.gregtechceu.gtceu.common.data.machines.GTMultiMachines.LARGE_GAS_TURBINE);
        helper.runAfterDelay(2, () -> {
            var hatch = turbine.getParts().stream().filter(com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine.class::isInstance)
                    .map(com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine.class::cast).findFirst().orElseThrow();
            hatch.tank.getStorages()[0].setFluid(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(1000));
            var source = GTRecipeTypes.GAS_TURBINE_FUELS.recipeBuilder("native_registered_turbine")
                    .inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.Methane.getFluid(100)).duration(10).EUt(-8192).build();
            var rotor = turbine.getParts().stream().filter(com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine.class::isInstance)
                    .map(com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine.class::cast).findFirst().orElseThrow(); rotor.setRotorStack(ItemStack.EMPTY);
            helper.assertTrue(!turbine.getRecipeLogic().checkMatchedRecipeAvailable(source) && hatch.tank.getStorages()[0].getFluidAmount() == 1000,
                    "Registered turbine consumed fuel without a rotor");
            rotor.setRotorStack(nativeRotor(com.gregtechceu.gtceu.common.data.GTMaterials.Aluminium)); rotor.setRotorSpeed(rotor.getMaxRotorHolderSpeed());
            var logic = turbine.getRecipeLogic(); helper.assertTrue(logic.checkMatchedRecipeAvailable(source), "Registered turbine refused real hatch fuel: " + logic.getBestFailureReason());
            long energy = logic.getLastRecipe().getOutputEUt().getTotalEU(); helper.assertTrue(energy > 0, "Registered turbine produced zero power at full rotor speed");
            logic.setSuspendAfterFinish(true); for (int tick = 0; tick < 10; tick++) logic.serverTick();
            helper.assertTrue(logic.isSuspend() && turbine.getEnergyContainer().getEnergyStored() == energy * 10 && source.getOutputEUt().getTotalEU() == 8192,
                    "Registered turbine failed its physical fuel cycle or changed the source recipe");
            helper.succeed();
        });
    }

    private static void assertRuntimeState(GameTestHelper helper, GTRecipe recipe) {
        helper.assertTrue(recipe != null, "Native recipe field failed to deserialize");
        helper.assertTrue(IGTRecipe.of(recipe).getRealParallels() == 9_007_199_254_740_993L &&
                IGTRecipe.of(recipe).getBatchSize() == 37 && IGTRecipe.of(recipe).isBatchProcessed() &&
                IGTRecipe.of(recipe).isSubTickParallelized() && recipe.ocLevel == 5,
                "Runtime quantities, flags or overclock level were lost");
        helper.assertTrue(LongIngredient.amount(ItemRecipeCapability.CAP.of(
                recipe.getInputContents(ItemRecipeCapability.CAP).getFirst().content())) == 3_000_000_001L,
                "Long item quantity changed while saving the block entity");
        helper.assertTrue(LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(
                recipe.getInputContents(FluidRecipeCapability.CAP).getFirst().content())) == 3_000_000_002L,
                "Long fluid quantity changed while synchronizing the block entity");
    }
    private static NativeMachineFixtures.NativeMutableMachine mutableController(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 2, 3), GTMachines.ENERGY_INPUT_HATCH[2].getBlock());
        helper.setBlock(new BlockPos(3, 2, 3), NativeMachineFixtures.ADD_MUTABLE.getBlock());
        helper.setBlock(new BlockPos(4, 2, 3), GTMachines.ENERGY_INPUT_HATCH[2].getBlock());
        var machine = (NativeMachineFixtures.NativeMutableMachine) helper.getBlockEntity(new BlockPos(3, 2, 3));
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Additions mutable controller did not form");
        for (int x : new int[]{2, 4}) ((com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine)
                helper.getBlockEntity(new BlockPos(x, 2, 3))).energyContainer.addEnergy(2000);
        var core = (IRecipeCapabilityMachine) (Object) machine; core.setDistinct(true); core.upDate();
        return machine;
    }
    private static NotifiableItemStackHandler mutableInputs(NativeMachineFixtures.NativeMutableMachine machine) {
        return machine.getTraits(NotifiableItemStackHandler.class).stream().filter(handler -> handler.getHandlerIO() == IO.IN).findFirst().orElseThrow();
    }
    private static NativeMachineFixtures.MutableFixtureLogic mutableLogic(NativeMachineFixtures.NativeMutableMachine machine) {
        return (NativeMachineFixtures.MutableFixtureLogic) machine.getRecipeLogic();
    }
    private static void mutableSources(NativeMachineFixtures.NativeMutableMachine machine) {
        mutableLogic(machine).sources = java.util.List.of(
                GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_mutable_iron").inputItems(Items.IRON_INGOT, 2)
                        .notConsumable(Items.DIAMOND).outputFluids(SizedFluidIngredient.of(Fluids.WATER, 100)).duration(2).EUt(32).build(),
                GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_mutable_gold").inputItems(Items.GOLD_INGOT)
                        .notConsumable(Items.DIAMOND).outputFluids(SizedFluidIngredient.of(Fluids.WATER, 50)).duration(4).EUt(16).build());
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void additionsMutablePaidWorkSurvivesBlockedOutputModeChangeAndNativeSave(GameTestHelper helper) {
        var machine = mutableController(helper); var input = mutableInputs(machine); var logic = mutableLogic(machine); mutableSources(machine);
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 9)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        input.storage.setStackInSlot(2, new ItemStack(Items.GOLD_INGOT, 3));
        var tank = machine.getTraits(NotifiableFluidTank.class).getFirst(); tank.getStorages()[0].setFluid(new FluidStack(Fluids.WATER, 9900));
        logic.setUseMultipleRecipes(true); logic.findAndHandleRecipe();
        helper.assertTrue(logic.getLastRecipe() != null && input.getStackInSlot(0).getCount() == 1 && input.getStackInSlot(2).isEmpty(), "Additions did not retain its consumed combined work");
        for (int tick = 0; tick < 20; tick++) logic.serverTick();
        helper.assertTrue(logic.isWaiting() && tank.getStorages()[0].getFluidAmount() == 9900, "Additions output failed to wait without losing work");
        logic.setUseMultipleRecipes(false);
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess()); machine.loadAdditional(saved, helper.getLevel().registryAccess());
        tank.getStorages()[0].setFluid(FluidStack.EMPTY); logic.setSuspendAfterFinish(true); logic.serverTick();
        helper.assertTrue(logic.isSuspend() && tank.getStorages()[0].getFluidAmount() == 550 && input.getStackInSlot(0).getCount() == 1 && input.getStackInSlot(2).isEmpty(), "Mode switch or reload discarded or duplicated Additions paid outputs");
        helper.assertTrue(!logic.isMultipleRecipeMode(), "Saved mutable mode was not restored");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void additionsMutableSingleModeUsesNativeRecipeStartAndTickLifecycle(GameTestHelper helper) {
        var machine = mutableController(helper); var input = mutableInputs(machine); var logic = mutableLogic(machine); mutableSources(machine);
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 9)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        logic.findAndHandleRecipe(); logic.setSuspendAfterFinish(true);
        helper.assertTrue(logic.getLastRecipe() != null && logic.getDuration() == 2 && input.getStackInSlot(0).getCount() == 7, "Single mode consumed a cross-recipe batch or skipped native input IO");
        for (int tick = 0; tick < 2; tick++) logic.serverTick();
        helper.assertTrue(logic.isSuspend() && machine.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[0].getFluidAmount() == 100 &&
                machine.getEnergyContainer().getEnergyStored() == 4000 - 64, "Native single-mode output or energy was not delivered exactly once");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void additionsMutableOutputFreeRecipesRetainPaidInputsAndEnergy(GameTestHelper helper) {
        var machine = mutableController(helper); var input = mutableInputs(machine); var logic = mutableLogic(machine);
        logic.sources = java.util.List.of(GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_mutable_output_free")
                .inputItems(Items.IRON_INGOT, 2).notConsumable(Items.DIAMOND).duration(2).EUt(32).build());
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 9)); input.storage.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        logic.setUseMultipleRecipes(true); logic.findAndHandleRecipe(); logic.setSuspendAfterFinish(true);
        helper.assertTrue(logic.getLastRecipe() != null && logic.getDuration() == 20 && input.getStackInSlot(0).getCount() == 1, "Output-free consumed recipes were dropped before native processing");
        long eut = logic.getLastRecipe().getInputEUt().getTotalEU();
        for (int tick = 0; tick < 20; tick++) logic.serverTick();
        helper.assertTrue(logic.isSuspend() && machine.getEnergyContainer().getEnergyStored() == 4000 - 20 * eut && input.getStackInSlot(1).is(Items.DIAMOND), "Output-free work skipped energy or consumed its reusable catalyst");
        helper.succeed();
    }

    private record NativeAccountHandler(java.util.UUID user, com.gregtechceu.gtceu.api.machine.MetaMachine machine)
            implements com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler {
        @Override public boolean consumeEnergy(int amount) { return consumeEnergy(java.math.BigInteger.valueOf(amount)); }
        @Override public boolean consumeEnergy(long amount) { return consumeEnergy(java.math.BigInteger.valueOf(amount)); }
        @Override public boolean consumeEnergy(java.math.BigInteger amount) {
            return com.gtladd.gtladditions.api.machine.wireless.NativeWirelessEnergyAccount.transfer(user, amount, machine);
        }
        @Override public java.math.BigInteger getMaxAvailableEnergy() {
            return com.gtladd.gtladditions.api.machine.wireless.NativeWirelessEnergyAccount.balance(user);
        }
        @Override public boolean isOnline() {
            return com.gtladd.gtladditions.api.machine.wireless.NativeWirelessEnergyAccount.find(user) != null;
        }
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void additionsNativeWirelessAccountKeepsExactBalanceRateAndSavedData(GameTestHelper helper) {
        var user = java.util.UUID.randomUUID();
        var account = com.gtladd.gtladditions.api.machine.wireless.NativeWirelessEnergyAccount.find(user);
        helper.assertTrue(account != null, "Native GTMThings world account was not available");
        var energy = java.math.BigInteger.ONE.shiftLeft(120).add(java.math.BigInteger.valueOf(13));
        account.setRate(Long.MAX_VALUE);
        helper.assertTrue(com.gtladd.gtladditions.api.machine.wireless.NativeWirelessEnergyAccount.transfer(user, energy, null), "Native BigInteger generation was truncated or rate-limited by the unlimited default");
        var debit = java.math.BigInteger.ONE.shiftLeft(100).add(java.math.BigInteger.valueOf(9));
        helper.assertTrue(com.gtladd.gtladditions.api.machine.wireless.NativeWirelessEnergyAccount.transfer(user, debit.negate(), null), "Native precise debit failed");
        helper.assertTrue(account.getStorage().equals(energy.subtract(debit)), "Native account balance lost precision");
        helper.assertTrue(!com.gtladd.gtladditions.api.machine.wireless.NativeWirelessEnergyAccount.transfer(user, energy.negate(), null), "Insufficient payment accepted");
        account.setRate(5);
        helper.assertTrue(!com.gtladd.gtladditions.api.machine.wireless.NativeWirelessEnergyAccount.transfer(user, -6L, null) &&
                account.getStorage().equals(energy.subtract(debit)), "Rejected custom-rate payment spent energy");
        helper.assertTrue(com.gtladd.gtladditions.api.machine.wireless.NativeWirelessEnergyAccount.transfer(user, -5L, null), "Allowed custom-rate payment was rejected");
        var saved = com.hepdd.gtmthings.data.WirelessEnergySavaedData.INSTANCE.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());
        var restored = new com.hepdd.gtmthings.data.WirelessEnergySavaedData(saved).containerMap.get(account.getUuid());
        helper.assertTrue(restored != null && restored.getRate() == 5 && restored.getStorage().equals(energy.subtract(debit).subtract(java.math.BigInteger.valueOf(5))), "Native saved account lost its BigInteger balance or rate");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void additionsWirelessRemainderWaitsForPaymentAndSurvivesNativeMachineReload(GameTestHelper helper) {
        var machine = mutableController(helper); var input = mutableInputs(machine); var logic = mutableLogic(machine);
        var user = java.util.UUID.randomUUID();
        var account = com.gtladd.gtladditions.api.machine.wireless.NativeWirelessEnergyAccount.find(user);
        helper.assertTrue(account != null, "Native GTMThings account unavailable for actual recipe ticks");
        account.setRate(Long.MAX_VALUE); account.setStorage(java.math.BigInteger.valueOf(100));
        machine.setWirelessNetworkEnergyHandler(new NativeAccountHandler(user, machine));
        logic.sources = java.util.List.of(GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_wireless_remainder_world")
                .inputItems(Items.IRON_INGOT).outputFluids(SizedFluidIngredient.of(Fluids.WATER, 100)).duration(1).EUt(7).build());
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 9));
        var tank = machine.getTraits(NotifiableFluidTank.class).getFirst();
        tank.getStorages()[0].setFluid(new FluidStack(Fluids.WATER, 9900));
        logic.setUseMultipleRecipes(true); logic.findAndHandleRecipe();
        helper.assertTrue(logic.getDuration() == 20 && input.getStackInSlot(0).getCount() == 1, "Native wireless batch did not retain its eight consumed recipes");
        for (int tick = 0; tick < 10; tick++) logic.serverTick();
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess()); machine.loadAdditional(saved, helper.getLevel().registryAccess());
        for (int tick = 10; tick < 19; tick++) logic.serverTick();
        helper.assertTrue(logic.getProgress() == 19 && account.getStorage().equals(java.math.BigInteger.valueOf(62)), "Remainder was lost, rounded up per tick, or charged before its final tick");
        account.setStorage(java.math.BigInteger.ZERO); logic.serverTick();
        helper.assertTrue(logic.isWaiting() && logic.getProgress() <= 19 && input.getStackInSlot(0).getCount() == 1,
                "Missing final payment advanced work or consumed inputs twice: status=" + logic.getStatus() +
                        ", progress=" + logic.getProgress() + ", input=" + input.getStackInSlot(0) +
                        ", data=" + logic.getLastRecipe().data);
        account.setStorage(java.math.BigInteger.valueOf(62));
        // Native power failure imposes a 60-tick retry delay and may regress to progress 1.
        for (int tick = 0; tick < 100 && logic.getProgress() < logic.getDuration(); tick++) logic.serverTick();
        helper.assertTrue(logic.isWaiting() && logic.getProgress() == 20 && account.getStorage().equals(java.math.BigInteger.valueOf(44)), "Native progress regression billed an already paid prefix again");
        for (int tick = 0; tick < 3; tick++) logic.serverTick();
        helper.assertTrue(account.getStorage().equals(java.math.BigInteger.valueOf(44)) && tank.getStorages()[0].getFluidAmount() == 9900, "Finished work consumed another tick while output was blocked");
        logic.setUseMultipleRecipes(false);
        saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess()); machine.loadAdditional(saved, helper.getLevel().registryAccess());
        tank.getStorages()[0].setFluid(FluidStack.EMPTY); logic.setSuspendAfterFinish(true); logic.serverTick();
        helper.assertTrue(logic.isSuspend() && account.getStorage().equals(java.math.BigInteger.valueOf(44)) &&
                tank.getStorages()[0].getFluidAmount() == 800, "Native resumed batch failed to deliver once and debit its complete cost");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void additionsNativeWirelessRefundAndPreciseMonitorKeepAcceptedTransfersOnly(GameTestHelper helper) {
        var machine = mutableController(helper); var logic = mutableLogic(machine);
        var user = java.util.UUID.randomUUID();
        var account = com.gtladd.gtladditions.api.machine.wireless.NativeWirelessEnergyAccount.find(user);
        account.setRate(Long.MAX_VALUE); account.setStorage(java.math.BigInteger.valueOf(100));
        machine.setWirelessNetworkEnergyHandler(new NativeAccountHandler(user, machine));
        logic.setupRecipe(new com.gtladd.gtladditions.api.recipe.WirelessGTRecipe(
                GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_wireless_report_refund").duration(20).build(),
                java.math.BigInteger.valueOf(-7)));
        var running = logic.getLastRecipe();
        var result = com.gtladd.gtladditions.api.recipe.NativeWirelessRecipeTick.run(machine, running,
                () -> com.gregtechceu.gtceu.api.recipe.ActionResult.FAIL_NO_REASON);
        helper.assertTrue(!result.isSuccess() && account.getStorage().equals(java.math.BigInteger.valueOf(100)),
                "Native rejected tick failed to return its reservation");
        helper.assertTrue(!com.hepdd.gtmthings.api.misc.WirelessEnergyContainer.TRANSFER_DATA.containsKey(machine) &&
                com.gtladd.gtladditions.utils.WirelessEnergyManagerData.BIG_MACHINE_DATA.getIfPresent(machine) == null,
                "Refunded native tick appeared as wireless generation in the monitor");
        result = com.gtladd.gtladditions.api.recipe.NativeWirelessRecipeTick.run(machine, running,
                () -> com.gregtechceu.gtceu.api.recipe.ActionResult.SUCCESS);
        helper.assertTrue(result.isSuccess() && account.getStorage().equals(java.math.BigInteger.valueOf(93)), "Native accepted tick was not paid");
        helper.assertTrue(org.gtlcore.gtlcore.integration.gtmt.NativeWirelessMonitorRows.throughput(
                com.hepdd.gtmthings.api.misc.WirelessEnergyContainer.TRANSFER_DATA.get(machine)).equals(java.math.BigInteger.valueOf(-7)),
                "Accepted native tick lost its signed transfer");
        logic.setStatus(com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic.Status.SUSPEND);

        var hullPos = new BlockPos(4, 2, 3);
        helper.setBlock(hullPos, com.gregtechceu.gtceu.common.data.GTMachines.HULL[com.gregtechceu.gtceu.api.GTValues.HV].getBlock());
        var hull = (com.gregtechceu.gtceu.api.machine.MetaMachine) helper.getBlockEntity(hullPos);
        var huge = java.math.BigInteger.ONE.shiftLeft(120);
        helper.assertTrue(com.gtladd.gtladditions.api.machine.wireless.NativeWirelessEnergyAccount.transfer(user, huge.add(java.math.BigInteger.valueOf(13)), machine) &&
                com.gtladd.gtladditions.api.machine.wireless.NativeWirelessEnergyAccount.transfer(user, huge.subtract(java.math.BigInteger.valueOf(7)), hull),
                "Native precise report setup did not accept unlimited generation");
        var snapshot = new java.util.LinkedHashMap<com.gregtechceu.gtceu.api.machine.MetaMachine, com.hepdd.gtmthings.api.misc.ITransferData>();
        snapshot.put(machine, com.hepdd.gtmthings.api.misc.WirelessEnergyContainer.TRANSFER_DATA.get(machine));
        snapshot.put(hull, com.hepdd.gtmthings.api.misc.WirelessEnergyContainer.TRANSFER_DATA.get(hull));
        var rows = org.gtlcore.gtlcore.integration.gtmt.NativeWirelessMonitorRows.transfers(snapshot, account.getUuid(), false);
        helper.assertTrue(rows.entries().getFirst().machine() == hull && snapshot.size() == 2,
                "Native monitor sorted saturated long views or drained its shared precision snapshot");
        var monitorPos = new BlockPos(1, 2, 4);
        helper.setBlock(monitorPos, com.hepdd.gtmthings.data.WirelessMachines.WIRELESS_ENERGY_MONITOR.getBlock());
        var monitor = (com.hepdd.gtmthings.common.block.machine.electric.WirelessEnergyMonitor) helper.getBlockEntity(monitorPos);
        monitor.setOwnerUUID(user);
        var previous = com.hepdd.gtmthings.api.misc.WirelessEnergyContainer.TRANSFER_SNAPSHOT;
        com.hepdd.gtmthings.api.misc.WirelessEnergyContainer.TRANSFER_SNAPSHOT = snapshot;
        try {
            var display = monitor.getDisplayText(false, 220);
            helper.assertTrue(display.stream().anyMatch(row -> row.getString().contains("EU/t (") &&
                    row.getString().contains(org.gtlcore.gtlcore.integration.gtmt.NewGTValues.VNF[
                            org.gtlcore.gtlcore.utils.NumberUtils.getFakeVoltageTier(Long.MAX_VALUE)])),
                    "Actual native monitor lost Additions' precise transfer row or voltage label");
            for (var row : display) helper.assertTrue(Component.Serializer.fromJson(Component.Serializer.toJson(row,
                    helper.getLevel().registryAccess()), helper.getLevel().registryAccess()) != null,
                    "Precise native monitor row cannot cross the synchronized UI channel");
            helper.assertTrue(snapshot.size() == 2, "Native monitor read removed another monitor's transfers");
        } finally { com.hepdd.gtmthings.api.misc.WirelessEnergyContainer.TRANSFER_SNAPSHOT = previous; }
        account.setRate(5); account.setStorage(java.math.BigInteger.ONE);
        helper.assertTrue(account.removeEnergy(9, hull) == 1 &&
                com.gtladd.gtladditions.utils.WirelessEnergyManagerData.BIG_MACHINE_DATA.getIfPresent(hull).right().equals(java.math.BigInteger.ONE.negate()),
                "Native manager tracking recorded requested energy instead of the accepted debit");
        var accepted = com.hepdd.gtmthings.api.misc.WirelessEnergyContainer.TRANSFER_DATA.get(hull);
        helper.assertTrue(account.removeEnergy(9, hull) == 0 && com.hepdd.gtmthings.api.misc.WirelessEnergyContainer.TRANSFER_DATA.get(hull) == accepted,
                "Native rejected transfer overwrote its last accepted report");
        helper.succeed();
    }

}
