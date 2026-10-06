package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.data.machines.GCyMMachines;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MufflerPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.LinkedHashMap;

/** Builds real registered Core GCYM patterns using GT's native preview, hatches and recipe logic. */
@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public class NativeGcymGameTests {
    private static WorkableElectricMultiblockMachine build(GameTestHelper helper,
            MachineEntry<MultiblockMachineDefinition> entry, int repeatCount) {
        var pattern = (BlockPattern) entry.get().getStructurePatterns().get("main").get();
        var repeats = new Int2IntOpenHashMap();
        for (int index = 0; index < pattern.getSlices().length; index++) {
            var slice = pattern.getSlices()[index];
            repeats.put(index, slice.getMinRepeats() == slice.getMaxRepeats() ? slice.getMinRepeats() : repeatCount);
        }
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>();
        var preview = AbstractStructureHelper.blockPattern(repeats);
        var lvEnergy = BlockInfo.fromBlockState(GTMachines.ENERGY_INPUT_HATCH[GTValues.LV].getBlock().defaultBlockState());
        for (var predicate : pattern.getPredicates().values()) {
            for (var base : predicate.expand()) {
                if (base.getCandidates().stream().anyMatch(candidate -> candidate.getBlockState().is(lvEnergy.getBlockState().getBlock()))) {
                    preview.getBlockPreferences().put(predicate, base, lvEnergy);
                }
            }
        }
        preview.populate(blocks, pattern, null, Direction.NORTH, Direction.UP, false);
        var origin = new BlockPos(3, 4, 3);
        BlockPos controller = null;
        for (var entryBlock : blocks.entrySet()) {
            var pos = origin.offset(entryBlock.getKey());
            helper.setBlock(pos, entryBlock.getValue().getBlockState());
            if (entryBlock.getValue().getBlockState().is(entry.getBlock())) controller = pos;
        }
        helper.assertTrue(controller != null, "Native preview lost the registered controller");
        for (var pos : blocks.keySet()) {
            var entity = helper.getLevel().getBlockEntity(helper.absolutePos(origin.offset(pos)));
            if (entity instanceof MaintenanceHatchPartMachine maintenance) maintenance.setMaintenanceProblems(MaintenanceHatchPartMachine.NO_PROBLEMS);
            if (entity instanceof MufflerPartMachine muffler) {
                helper.getLevel().setBlockAndUpdate(muffler.getBlockPos().relative(muffler.getFrontFacing()), Blocks.AIR.defaultBlockState());
            }
        }
        var machine = (WorkableElectricMultiblockMachine) helper.getBlockEntity(controller);
        machine.setFrontFacing(Direction.NORTH);
        machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Core's registered pattern did not form: " + entry.getId() + " " +
                machine.getDefaultPatternState().getErrors().stream().map(error -> {
                    if (error instanceof com.gregtechceu.gtceu.api.multiblock.error.PatternStringError text) return text.getComponent().getString();
                    if (error instanceof com.gregtechceu.gtceu.api.multiblock.error.SimplePatternError mismatch) {
                        return error.getClass().getSimpleName() + " pos=" + error.getPos() + " actual=" + helper.getLevel().getBlockState(error.getPos()) +
                                " candidates=" + mismatch.getCandidates().stream().flatMap(java.util.Collection::stream).map(BlockInfo::getBlockState).limit(5).toList();
                    }
                    if (error instanceof com.gregtechceu.gtceu.api.multiblock.error.SinglePredicateError count) return count.type + " " + count.debugName + " actual=" + count.actualCount;
                    return error.getClass().getSimpleName() + " " + error.getPos();
                }).toList());
        return machine;
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void registeredMacerationRequiresEnergyAndProducesReducedRecipeOutputs(GameTestHelper helper) {
        var machine = build(helper, GCyMMachines.LARGE_MACERATION_TOWER, 1);
        var input = machine.getParts().stream().filter(ItemBusPartMachine.class::isInstance).map(ItemBusPartMachine.class::cast)
                .filter(part -> PartAbility.IMPORT_ITEMS.isApplicable(part.getBlockState().getBlock())).findFirst().orElseThrow();
        var output = machine.getParts().stream().filter(ItemBusPartMachine.class::isInstance).map(ItemBusPartMachine.class::cast)
                .filter(part -> PartAbility.EXPORT_ITEMS.isApplicable(part.getBlockState().getBlock())).findFirst().orElseThrow();
        var energy = machine.getParts().stream().filter(part -> PartAbility.INPUT_ENERGY.isApplicable(part.getBlockState().getBlock())).findFirst().orElseThrow();
        var energyState = energy.getBlockState(); var energyPos = energy.getBlockPos();
        helper.getLevel().setBlockAndUpdate(energyPos, GCyMMachines.LARGE_MACERATION_TOWER.get().getAppearance().get());
        machine.checkAndFormStructure();
        helper.assertTrue(!machine.isFormed(), "GCYM casing bypassed the required native energy hatch");
        helper.getLevel().setBlockAndUpdate(energyPos, energyState); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "GCYM controller did not recover after replacing the energy hatch");
        helper.runAfterDelay(2, () -> {
            machine.getEnergyContainer().changeEnergy(10000);
            input.getInventory().storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            var source = GTRecipeTypes.MACERATOR_RECIPES.recipeBuilder("native_registered_gcym_maceration")
                    .inputItems(Items.IRON_INGOT).outputItems(Items.IRON_NUGGET, 9).duration(10).EUt(32).build();
            var logic = machine.getRecipeLogic();
            helper.assertTrue(logic.checkMatchedRecipeAvailable(source), "GCYM recipe was rejected: " + logic.getBestFailureReason() +
                    " tier=" + machine.getTier() + " overclockVoltage=" + machine.getOverclockVoltage());
            helper.assertTrue(logic.getLastRecipe().duration == 6 && logic.getLastRecipe().getInputEUt().getTotalEU() == 25,
                    "GCYM 0.8 EU and 0.6 duration multipliers changed");
            logic.setSuspendAfterFinish(true);
            for (int tick = 0; tick < 6; tick++) logic.serverTick();
            helper.assertTrue(logic.isSuspend() && input.getInventory().getStackInSlot(0).isEmpty() &&
                    output.getInventory().getStackInSlot(0).getCount() == 9 && source.duration == 10 && source.getInputEUt().getTotalEU() == 32,
                    "GCYM native physical cycle duplicated/lost outputs or mutated the source recipe");
            helper.succeed();
        });
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void registeredAlloyBlastRejectsMixedCoilsAndRecovers(GameTestHelper helper) {
        var machine = (CoilWorkableElectricMultiblockMachine) build(helper, GCyMMachines.BLAST_ALLOY_SMELTER, 1);
        var coil = machine.getDefaultPatternState().getCache().long2ObjectEntrySet().stream()
                .filter(entry -> entry.getValue().getBlockState().getBlock() instanceof CoilBlock).findFirst().orElseThrow();
        var pos = BlockPos.of(coil.getLongKey()); var original = coil.getValue().getBlockState();
        var other = com.gregtechceu.gtceu.api.GTCEuAPI.HEATING_COILS.entrySet().stream()
                .filter(entry -> entry.getKey() != machine.getCoilType()).findFirst().orElseThrow().getValue().get().defaultBlockState();
        helper.getLevel().setBlockAndUpdate(pos, other); machine.checkAndFormStructure();
        helper.assertTrue(!machine.isFormed(), "The registered alloy blast smelter accepted mixed coil types");
        helper.getLevel().setBlockAndUpdate(pos, original); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getCoilType() == ((CoilBlock) original.getBlock()).coilType,
                "Registered smelter did not recover its uniform coil state");
        helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeMufflerReportsObstructionAndPreservesFrontRecovery(GameTestHelper helper) {
        var machine = build(helper, GCyMMachines.BLAST_ALLOY_SMELTER, 1);
        var muffler = machine.getParts().stream().filter(MufflerPartMachine.class::isInstance).map(MufflerPartMachine.class::cast).findFirst().orElseThrow();
        var recipe = machine.getRecipeTypes()[0].recipeBuilder("native_muffler_obstruction").duration(20).build();
        var front = muffler.getBlockPos().relative(muffler.getFrontFacing());
        helper.getLevel().setBlockAndUpdate(front, Blocks.AIR.defaultBlockState());
        helper.assertTrue(muffler.modifyRecipe(recipe) == recipe, "An unobstructed native muffler changed the recipe");
        helper.getLevel().setBlockAndUpdate(front, Blocks.STONE.defaultBlockState());
        helper.assertTrue(muffler.modifyRecipe(recipe) == null, "Native muffler accepted a blocked front");
        var result = ((org.gtlcore.gtlcore.api.machine.trait.IRecipeStatus) machine.getRecipeLogic()).getRecipeStatus();
        helper.assertTrue(result != null && !result.isSuccess() && result.reason().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                        && text.getKey().equals("gtceu.multiblock.universal.muffler_obstructed"), "Native controller lost the muffler failure reason");
        helper.getLevel().setBlockAndUpdate(front, Blocks.AIR.defaultBlockState());
        helper.assertTrue(muffler.modifyRecipe(recipe) == recipe, "Native muffler did not recover after removing the obstruction");
        muffler.removedFromController(machine);
        helper.getLevel().setBlockAndUpdate(front, Blocks.STONE.defaultBlockState());
        helper.assertTrue(muffler.modifyRecipe(recipe) == null, "A detached native muffler failed to reject its blocked front");
        helper.succeed();
    }

    private static void distillery(GameTestHelper helper, int repeats) {
        var machine = build(helper, GCyMMachines.LARGE_DISTILLERY, repeats);
        var outputs = machine.getParts().stream().filter(FluidHatchPartMachine.class::isInstance).map(FluidHatchPartMachine.class::cast)
                .filter(part -> PartAbility.EXPORT_FLUIDS_1X.isApplicable(part.getBlockState().getBlock())).toList();
        helper.assertTrue(outputs.size() == repeats, "Native repeated preview lost one fluid output per distillery layer");
        helper.assertTrue(outputs.stream().map(part -> part.getBlockPos().getY()).distinct().count() == repeats,
                "Distillery outputs collapsed onto one layer");
        var output = outputs.getFirst(); var state = output.getBlockState();
        helper.getLevel().setBlockAndUpdate(output.getBlockPos(), GCyMMachines.LARGE_DISTILLERY.get().getAppearance().get());
        machine.checkAndFormStructure();
        helper.assertTrue(!machine.isFormed(), "A distillery layer without an export hatch still formed");
        helper.getLevel().setBlockAndUpdate(output.getBlockPos(), state); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Replacing a layer's fluid output did not reform the distillery");
        helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void registeredDistilleryMinimumRepeatHasMandatoryLayerOutput(GameTestHelper helper) { distillery(helper, 1); }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void registeredDistilleryMaximumRepeatHasMandatoryLayerOutputs(GameTestHelper helper) { distillery(helper, 12); }
}
