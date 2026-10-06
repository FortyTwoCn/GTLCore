package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMENativeOutputBuffer;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.common.data.GTLBedrockFluids;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.INFFluidDrillMachine;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.FluidVeinWorldEntry;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FluidDrillMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import appeng.api.stacks.AEFluidKey;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeFluidRigGameTests {
    private static WorkableElectricMultiblockMachine form(GameTestHelper helper, MachineEntry<MultiblockMachineDefinition> entry) {
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(new Int2IntOpenHashMap(Map.of(0, 1))).populate(blocks,
                entry.get().getStructurePatterns().get("main").get(), null, Direction.NORTH, Direction.UP, false);
        BlockPos controller = null;
        for (var block : blocks.entrySet()) {
            var pos = new BlockPos(4, 12, 4).offset(block.getKey());
            helper.setBlock(pos, block.getValue().getBlockState());
            if (block.getValue().getBlockState().is(entry.getBlock())) controller = pos;
        }
        var machine = (WorkableElectricMultiblockMachine) helper.getBlockEntity(Objects.requireNonNull(controller));
        machine.checkAndFormStructure(); machine.getRecipeLogic().setWorkingEnabled(false);
        helper.assertTrue(machine.isFormed() && machine.getParts().size() == 2, "Fluid rig did not form around its two actual native hatches");
        return machine;
    }

    private static FluidVeinWorldEntry deposit(GameTestHelper helper, WorkableElectricMultiblockMachine machine, int yield) {
        var definition = helper.getLevel().registryAccess().registryOrThrow(GTRegistries.Keys.BEDROCK_FLUID)
                .getHolderOrThrow(GTLBedrockFluids.VOID_ALT_WATER);
        var entry = new FluidVeinWorldEntry(definition, yield, BedrockFluidVeinSavedData.MAXIMUM_VEIN_OPERATIONS);
        var pos = machine.getBlockPos();
        BedrockFluidVeinSavedData.getOrCreate(helper.getLevel()).veinFluids.put(
                new ChunkPos(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())), entry);
        return entry;
    }

    private static Fluid fluid(FluidVeinWorldEntry entry) { return entry.getDefinition().value().getStoredFluid(); }
    private static FluidHatchPartMachine physicalOutput(WorkableElectricMultiblockMachine machine) {
        return machine.getParts().stream().filter(FluidHatchPartMachine.class::isInstance)
                .map(FluidHatchPartMachine.class::cast).findFirst().orElseThrow();
    }
    private static IMENativeOutputBuffer longOutput(WorkableElectricMultiblockMachine machine) {
        return machine.getParts().stream().filter(IMENativeOutputBuffer.class::isInstance)
                .map(IMENativeOutputBuffer.class::cast).findFirst().orElseThrow();
    }
    private static void start(GameTestHelper helper, WorkableElectricMultiblockMachine machine) {
        machine.getEnergyContainer().changeEnergy(1_000_000);
        var logic = machine.getRecipeLogic(); logic.setWorkingEnabled(true); logic.findAndHandleRecipe();
        helper.assertTrue(logic.getStatus() == RecipeLogic.Status.WORKING && logic.getDuration() == 20,
                "Native fluid rig did not start its twenty-tick physical recipe");
    }
    private static void ticks(RecipeLogic logic, int count) { for (int tick = 0; tick < count; tick++) logic.serverTick(); }
    private static long rawOutput(RecipeLogic logic) {
        var content = logic.getLastRecipe().outputs.get(FluidRecipeCapability.CAP).getFirst();
        return LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(content.content()));
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 160)
    public static void nativeInfiniteRigPaysEveryTickNeverDepletesAndHonorsCoreAndNativeFinishPauses(GameTestHelper helper) {
        var machine = (INFFluidDrillMachine) form(helper, NativeFluidRigFixtures.INFINITE);
        var entry = deposit(helper, machine, 101); var output = physicalOutput(machine);
        start(helper, machine); var logic = machine.getRecipeLogic(); long before = machine.getEnergyContainer().getEnergyStored();
        helper.assertTrue(machine.getTier() == GTValues.MV && machine.getEnergyTier() == GTValues.MV && rawOutput(logic) == 25_856L && logic.getVeinFluid() == fluid(entry), "Core infinite rig changed its configured tier, original 256 multiplier or deposit fluid");
        ((ISuspendableMachine) machine).gtlcore$setSuspendAfterFinish(true);
        ticks(logic, 20);
        helper.assertTrue(logic.isSuspend() && !((ISuspendableMachine) machine).gtlcore$isSuspendAfterFinish() && output.tank.getFluidInTank(0).getAmount() == 25_856 && output.tank.getFluidInTank(0).getFluid() == fluid(entry), "Core finish pause restarted another infinite cycle or lost native production");
        helper.assertTrue(entry.getOperationsRemaining() == 100_000 && machine.getEnergyContainer().getEnergyStored() == before - 20L * GTValues.VA[GTValues.MV], "Infinite rig depleted the vein or paid an incorrect native EU cost");
        ticks(logic, 5);
        helper.assertTrue(output.tank.getFluidInTank(0).getAmount() == 25_856, "Paused infinite rig produced an extra cycle");
        machine.getEnergyContainer().changeEnergy(1_000_000);
        long resumedEnergy = machine.getEnergyContainer().getEnergyStored();
        logic.setWorkingEnabled(true); logic.findAndHandleRecipe();
        helper.assertTrue(logic.getStatus() == RecipeLogic.Status.WORKING, "Recharged native rig failed to resume its second recipe");
        logic.setSuspendAfterFinish(true);
        ticks(logic, 20);
        helper.assertTrue(logic.isSuspend() && output.tank.getFluidInTank(0).getAmount() == 51_712 && entry.getOperationsRemaining() == 100_000 && machine.getEnergyContainer().getEnergyStored() == resumedEnergy - 20L * GTValues.VA[GTValues.MV], "Native finish-pause flag did not stop the resumed infinite rig exactly once with its own full energy charge");
        helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 160)
    public static void nativeInfiniteRigWaitsOnBlockedCompletedOutputWithoutExtraPowerOrLostProduction(GameTestHelper helper) {
        var machine = (INFFluidDrillMachine) form(helper, NativeFluidRigFixtures.INFINITE);
        var entry = deposit(helper, machine, 100); var output = physicalOutput(machine);
        start(helper, machine); var logic = machine.getRecipeLogic();
        output.tank.setFluidInTank(0, new FluidStack(Fluids.LAVA, output.tank.getTankCapacity(0)));
        logic.setSuspendAfterFinish(true);
        ((ISuspendableMachine) machine).gtlcore$setSuspendAfterFinish(true); ticks(logic, 20);
        long stoppedEnergy = machine.getEnergyContainer().getEnergyStored();
        helper.assertTrue(logic.isWaiting() && logic.getProgress() == 20 && output.tank.getFluidInTank(0).getFluid() == Fluids.LAVA && logic.isSuspendAfterFinish() && ((ISuspendableMachine) machine).gtlcore$isSuspendAfterFinish(), "Blocked completed rig dropped its pending output, native/Core pause request or waiting state");
        ticks(logic, 5);
        helper.assertTrue(machine.getEnergyContainer().getEnergyStored() == stoppedEnergy && entry.getOperationsRemaining() == 100_000, "Output waiting charged additional ticks or depleted an infinite deposit");
        output.tank.setFluidInTank(0, FluidStack.EMPTY); logic.serverTick();
        helper.assertTrue(logic.isSuspend() && output.tank.getFluidInTank(0).getAmount() == 25_600 && output.tank.getFluidInTank(0).getFluid() == fluid(entry) && machine.getEnergyContainer().getEnergyStored() == stoppedEnergy, "Unblocked rig failed to commit its original production and pause without an extra charge");
        output.tank.setFluidInTank(0, FluidStack.EMPTY);
        machine.getEnergyContainer().changeEnergy(-machine.getEnergyContainer().getEnergyStored());
        logic.setWorkingEnabled(true); logic.findAndHandleRecipe();
        helper.assertTrue(logic.isIdle() && logic.getLastRecipe() == null && output.tank.isEmpty(), "Unpowered infinite rig admitted a native recipe or created phantom output");
        helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 160)
    public static void nativeInfiniteRigRestoresAnInFlightLongRecipeAndProducesItsExactOwnedOutputOnce(GameTestHelper helper) {
        var machine = (INFFluidDrillMachine) form(helper, NativeFluidRigFixtures.LONG_INFINITE);
        var entry = deposit(helper, machine, 10_000_001); var key = AEFluidKey.of(fluid(entry));
        var buffer = longOutput(machine).gtlcore$getOutputBuffer();
        start(helper, machine); var logic = machine.getRecipeLogic(); long before = machine.getEnergyContainer().getEnergyStored();
        helper.assertTrue(rawOutput(logic) == 2_560_000_256L && machine.getRecipeLogic().getFluidToProduce() == 2_560_000_256L, "Infinite rig truncated production while creating its native recipe or display");
        logic.setSuspendAfterFinish(true);
        ((ISuspendableMachine) machine).gtlcore$setSuspendAfterFinish(true);
        ticks(logic, 7);
        helper.assertTrue(buffer.storage.getLong(key) == 0 && logic.getProgress() == 7, "In-flight rig delivered production before completion");
        var level = helper.getLevel(); var saved = machine.saveWithoutMetadata(level.registryAccess());
        var pos = machine.getBlockPos(); var state = machine.getBlockState(); machine.invalidateStructure("main"); level.removeBlockEntity(pos);
        var restored = (INFFluidDrillMachine) NativeFluidRigFixtures.LONG_INFINITE.getBlockEntityType().create(pos, state);
        restored.loadAdditional(saved, level.registryAccess()); level.setBlockEntity(restored); restored.checkAndFormStructure();
        helper.assertTrue(restored.isFormed() && restored.getRecipeLogic().getProgress() == 7 && restored.getRecipeLogic().isSuspendAfterFinish() && ((ISuspendableMachine) restored).gtlcore$isSuspendAfterFinish() && rawOutput(restored.getRecipeLogic()) == 2_560_000_256L, "Native save/reload lost the running raw recipe, long output, Core tool pause or native finish pause");
        ticks(restored.getRecipeLogic(), 13);
        helper.assertTrue(restored.getRecipeLogic().isSuspend() && buffer.storage.getLong(key) == 2_560_000_256L && entry.getOperationsRemaining() == 100_000 && restored.getEnergyContainer().getEnergyStored() == before - 20L * GTValues.VA[GTValues.MV], "Restored native rig lost or duplicated long output, depleted its deposit or repeated paid ticks");
        ticks(restored.getRecipeLogic(), 5);
        helper.assertTrue(buffer.storage.getLong(key) == 2_560_000_256L, "Reloaded paused rig delivered its owned production again"); helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 160)
    public static void nativeInfiniteRigRetainsConfiguredTierAndCapsOverclockToOneTierWithFiftyPercentMoreFluid(GameTestHelper helper) {
        var machine = (INFFluidDrillMachine) form(helper, NativeFluidRigFixtures.OVERCLOCKED);
        var entry = deposit(helper, machine, 101); var output = physicalOutput(machine);
        start(helper, machine); var logic = machine.getRecipeLogic(); long before = machine.getEnergyContainer().getEnergyStored();
        helper.assertTrue(machine.getTier() == GTValues.MV && machine.getEnergyTier() == GTValues.HV && rawOutput(logic) == 38_784L && logic.getFluidToProduce() == 38_784L, "Infinite rig changed the configured base tier, one-tier cap or original fifty-percent overclock yield");
        logic.setSuspendAfterFinish(true); ticks(logic, 20);
        helper.assertTrue(output.tank.getFluidInTank(0).getAmount() == 38_784 && entry.getOperationsRemaining() == 100_000 && machine.getEnergyContainer().getEnergyStored() == before - 20L * GTValues.VA[GTValues.HV], "Overclocked infinite rig changed actual production, depletion or native EU charges"); helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 160)
    public static void nativeGTFluidRigStillDepletesOnceAndPausesBeforeItCanRestartAnotherRecipe(GameTestHelper helper) {
        var machine = (FluidDrillMachine) form(helper, NativeFluidRigFixtures.GT_RIG);
        var entry = deposit(helper, machine, 101); var output = physicalOutput(machine);
        boolean oldInfinite = com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.worldgen.oreVeins.infiniteBedrockOresFluids;
        try {
            com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.worldgen.oreVeins.infiniteBedrockOresFluids = false;
            start(helper, machine); var logic = machine.getRecipeLogic(); long before = machine.getEnergyContainer().getEnergyStored();
            ((ISuspendableMachine) machine).gtlcore$setSuspendAfterFinish(true); ticks(logic, 20);
            helper.assertTrue(logic.isSuspend() && output.tank.getFluidInTank(0).getAmount() == 101 && output.tank.getFluidInTank(0).getFluid() == fluid(entry) && entry.getOperationsRemaining() == 99_999, "Native GT lifecycle lost its output or depletion, or restarted before Core finish-pause handling");
            ticks(logic, 20);
            helper.assertTrue(output.tank.getFluidInTank(0).getAmount() == 101 && entry.getOperationsRemaining() == 99_999 && machine.getEnergyContainer().getEnergyStored() == before - 20L * GTValues.VA[GTValues.MV], "Paused native GT rig delivered or depleted another cycle");
        } finally { com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.worldgen.oreVeins.infiniteBedrockOresFluids = oldInfinite; }
        helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 160)
    public static void nativeGTFluidRigKeepsProductionBeyondIntAndItsPublicNativeIntAccessorCannotWrap(GameTestHelper helper) {
        var machine = (FluidDrillMachine) form(helper, NativeFluidRigFixtures.LONG_GT_RIG);
        var entry = deposit(helper, machine, 100_000_001); var key = AEFluidKey.of(fluid(entry));
        var buffer = longOutput(machine).gtlcore$getOutputBuffer(); start(helper, machine);
        var logic = machine.getRecipeLogic(); long before = machine.getEnergyContainer().getEnergyStored();
        helper.assertTrue(rawOutput(logic) == 6_400_000_064L && logic.getFluidToProduce() == Integer.MAX_VALUE, "Native GT recipe or int display wrapped or truncated exact long production");
        logic.setSuspendAfterFinish(true); ticks(logic, 20);
        helper.assertTrue(logic.isSuspend() && buffer.storage.getLong(key) == 6_400_000_064L && machine.getEnergyContainer().getEnergyStored() == before - 20L * GTValues.VA[GTValues.EV], "Native GT finish-pause lost long output or charged an extra recipe");
        ticks(logic, 5); helper.assertTrue(buffer.storage.getLong(key) == 6_400_000_064L, "Paused native GT rig delivered long output twice"); helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 160)
    public static void nativeGTRigRetainsItsPowerButtonPauseAndDepletesOnlyAfterBlockedOutputRecovers(GameTestHelper helper) {
        var machine = (FluidDrillMachine) form(helper, NativeFluidRigFixtures.GT_RIG);
        var entry = deposit(helper, machine, 101); var output = physicalOutput(machine);
        boolean oldInfinite = com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.worldgen.oreVeins.infiniteBedrockOresFluids;
        try {
            com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.worldgen.oreVeins.infiniteBedrockOresFluids = false;
            start(helper, machine); var logic = machine.getRecipeLogic();
            output.tank.setFluidInTank(0, new FluidStack(Fluids.LAVA, output.tank.getTankCapacity(0)));
            logic.setSuspendAfterFinish(true); ticks(logic, 20);
            long energy = machine.getEnergyContainer().getEnergyStored();
            helper.assertTrue(logic.isWaiting() && logic.isSuspendAfterFinish() && logic.getProgress() == 20 && entry.getOperationsRemaining() == 100_000,
                    "Native GT rig consumed its finish pause or depleted before its blocked output was delivered");
            ticks(logic, 5);
            helper.assertTrue(machine.getEnergyContainer().getEnergyStored() == energy && entry.getOperationsRemaining() == 100_000,
                    "Waiting native GT rig charged another paid tick or depleted a pending cycle");
            output.tank.setFluidInTank(0, FluidStack.EMPTY); logic.serverTick();
            helper.assertTrue(logic.isSuspend() && output.tank.getFluidInTank(0).getAmount() == 101 && entry.getOperationsRemaining() == 99_999 && machine.getEnergyContainer().getEnergyStored() == energy,
                    "Unblocked native GT rig lost output, depletion, pause or an exact energy charge");
            ticks(logic, 5);
            helper.assertTrue(output.tank.getFluidInTank(0).getAmount() == 101 && entry.getOperationsRemaining() == 99_999,
                    "Paused native GT rig repeated a recovered output or depletion");
        } finally { com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.worldgen.oreVeins.infiniteBedrockOresFluids = oldInfinite; }
        helper.succeed();
    }
}
