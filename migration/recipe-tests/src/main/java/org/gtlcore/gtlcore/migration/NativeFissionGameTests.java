package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.FissionReactorMachine;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.ClassSyncData;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test") @PrefixGameTestTemplate(false)
public final class NativeFissionGameTests {
    private static FissionReactorMachine form(GameTestHelper helper) {
        var shape = new LinkedHashMap<BlockPos, BlockInfo>(); var entry = NativeFissionFixtures.CONTROLLER;
        AbstractStructureHelper.blockPattern(new Int2IntOpenHashMap(Map.of(0, 1))).populate(shape,
                entry.get().getStructurePatterns().get("main").get(), null, Direction.NORTH, Direction.UP, false);
        BlockPos controller = null;
        for (var block : shape.entrySet()) {
            var pos = new BlockPos(4, 12, 4).offset(block.getKey()); helper.setBlock(pos, block.getValue().getBlockState());
            if (block.getValue().getBlockState().is(entry.getBlock())) controller = pos;
        }
        var machine = (FissionReactorMachine) helper.getBlockEntity(Objects.requireNonNull(controller));
        machine.checkAndFormStructure(); machine.setWorkingEnabled(false);
        helper.assertTrue(machine.isFormed() && machine.getParts().size() == 5 && machine.getFuelCount() == 2
                && machine.getCoolerCount() == 6 && machine.getFuelAdjacency() == 1 && machine.getCoolerAdjacency() == 7,
                "Native fission controller failed accepted fuel/cooler counts, adjacency or real hatches");
        return machine;
    }
    private static ItemBusPartMachine bus(FissionReactorMachine machine, IO io) {
        return machine.getParts().stream().filter(ItemBusPartMachine.class::isInstance).map(ItemBusPartMachine.class::cast)
                .filter(part -> part.getInventory().getHandlerIO() == io).findFirst().orElseThrow();
    }
    private static FluidHatchPartMachine fluid(FissionReactorMachine machine, IO io) {
        return machine.getParts().stream().filter(FluidHatchPartMachine.class::isInstance).map(FluidHatchPartMachine.class::cast)
                .filter(part -> part.tank.getHandlerIO() == io).findFirst().orElseThrow();
    }
    private static GTRecipe recipe(int duration, int heat) {
        var recipe = GTRecipeBuilder.ofRaw().inputItems(Items.COAL).outputItems(Items.DIAMOND).EUt(100).duration(duration).build();
        recipe.data.putInt("FRheat", heat); return recipe;
    }
    private static void fields(FissionReactorMachine machine, int heat, int damage) {
        var save = ClassSyncData.getClassData(machine.getClass()).getServerSaveFields();
        save.get("heat").handle.set(machine, heat); save.get("damaged").handle.set(machine, damage);
    }
    private static void thermal(FissionReactorMachine machine) {
        try {
            var offset = MetaMachine.class.getDeclaredField("offset"); offset.setAccessible(true);
            offset.setLong(machine, offset.getLong(machine) + Math.floorMod(-machine.getOffsetTimer(), 20));
            var update = FissionReactorMachine.class.getDeclaredMethod("heatUpdate"); update.setAccessible(true); update.invoke(machine);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void start(GameTestHelper helper, FissionReactorMachine machine, int duration, int heat) {
        bus(machine, IO.IN).getInventory().setStackInSlot(0, new ItemStack(Items.COAL, 8));
        machine.getEnergyContainer().changeEnergy(10000); machine.setWorkingEnabled(true);
        var modified = machine.fullModifyRecipe(recipe(duration, heat));
        helper.assertTrue(modified != null && IGTRecipe.of(modified).getRealParallels() == 2 && modified.getInputEUt().getTotalEU() == 200,
                "Native fission modifier lost fuel parallel or exact native energy scaling");
        machine.getRecipeLogic().setupRecipe(modified);
        helper.assertTrue(machine.getRecipeLogic().isWorking() && machine.getRunningParallel() == 2
                && machine.getRecipeHeat() == heat && bus(machine, IO.IN).getInventory().getStackInSlot(0).getCount() == 6,
                "Native fission setup did not commit two real inputs and accepted thermal metadata");
    }
    private static void ticks(RecipeLogic logic, int count) { for (int i = 0; i < count; i++) logic.serverTick(); }
    private static void pause(FissionReactorMachine machine) {
        machine.getRecipeLogic().setSuspendAfterFinish(true); ((ISuspendableMachine) machine).gtlcore$setSuspendAfterFinish(true);
    }
    private static FissionReactorMachine reload(GameTestHelper helper, FissionReactorMachine machine) {
        var pos = machine.getBlockPos(); var state = machine.getBlockState();
        var data = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.getLevel().removeBlockEntity(pos); helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, state);
        var copy = (FissionReactorMachine) helper.getLevel().getBlockEntity(pos);
        copy.loadWithComponents(data, helper.getLevel().registryAccess()); copy.onLoad(); copy.checkAndFormStructure(); return copy;
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_fission", timeoutTicks = 140)
    public static void nativeFissionPreviewIsPureAndAcceptedStructureAccountingSurvivesRepair(GameTestHelper helper) {
        var machine = form(helper); var input = bus(machine, IO.IN).getInventory();
        input.setStackInSlot(0, new ItemStack(Items.COAL, 8)); machine.getEnergyContainer().changeEnergy(10000);
        var source = recipe(20, 15); var first = machine.fullModifyRecipe(source); var second = machine.fullModifyRecipe(source);
        helper.assertTrue(first != null && second != null && IGTRecipe.of(first).getRealParallels() == 2 && IGTRecipe.of(second).getRealParallels() == 2
                && IGTRecipe.of(source).getRealParallels() == 1 && input.getStackInSlot(0).getCount() == 8
                && machine.getEnergyContainer().getEnergyStored() == 10000 && machine.getRunningParallel() == 0 && machine.getRecipeHeat() == 0,
                "Native fission preview changed source, inputs, energy or running thermal metadata");
        fields(machine, 900, 12); machine.onPartUnload();
        helper.assertTrue(machine.getFuelCount() == 0 && machine.getCoolerCount() == 0 && machine.getHeat() == 900,
                "Native part unload retained derived reactor membership or lost heat");
        machine.checkAndFormStructure(); helper.assertTrue(machine.isFormed() && machine.getFuelCount() == 2 && machine.getCoolerAdjacency() == 7,
                "Native fission repair lost accepted counts or resumed with stale adjacency");
        machine.invalidateStructure("main"); helper.assertTrue(machine.getFuelCount() == 0 && machine.getRunningParallel() == 0
                && machine.getHeat() == 900 && machine.getDamage() == 12 && machine.fullModifyRecipe(source) == null,
                "Native fission invalidation retained a runnable structure or erased thermal damage"); helper.succeed();
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_fission", timeoutTicks = 140)
    public static void nativeFissionWaterExchangePreflightsOutputAndAcceleratesOnlyPaidCoolantCycles(GameTestHelper helper) {
        var machine = form(helper); fields(machine, 400, 0); start(helper, machine, 60, 15);
        var input = fluid(machine, IO.IN).tank; var output = fluid(machine, IO.OUT).tank;
        input.setFluidInTank(3, GTMaterials.DistilledWater.getFluid(12800));
        for (int slot = 0; slot < output.getTanks(); slot++) output.setFluidInTank(slot,
                new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, output.getTankCapacity(slot)));
        thermal(machine);
        helper.assertTrue(input.getFluidInTank(3).getAmount() == 12800 && machine.getHeat() == 430 && machine.getRecipeLogic().getProgress() == 0,
                "Blocked native reactor coolant output consumed water or granted progress");
        for (int slot = 0; slot < output.getTanks(); slot++) output.setFluidInTank(slot, FluidStack.EMPTY);
        fields(machine, 400, 0); thermal(machine);
        long steam = 0; for (int slot = 0; slot < output.getTanks(); slot++) steam += output.getFluidInTank(slot).getAmount();
        helper.assertTrue(input.getFluidInTank(3).isEmpty() && steam == 2_048_000 && machine.getHeat() == 400
                && machine.getRecipeLogic().getProgress() == 20 && machine.getEnergyContainer().getEnergyStored() == 10000,
                "Native cooling: input=" + input.getFluidInTank(3) + " steam=" + steam + " heat=" + machine.getHeat() + " progress=" + machine.getRecipeLogic().getProgress() + " energy=" + machine.getEnergyContainer().getEnergyStored());
        pause(machine); ticks(machine.getRecipeLogic(), 40);
        helper.assertTrue(machine.getRecipeLogic().isSuspend() && machine.getEnergyContainer().getEnergyStored() == 2000
                && bus(machine, IO.OUT).getInventory().getStackInSlot(0).is(Items.DIAMOND)
                && bus(machine, IO.OUT).getInventory().getStackInSlot(0).getCount() == 2,
                "Native accelerated reactor did not finish one paid cycle with its two outputs"); helper.succeed();
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_fission", timeoutTicks = 140)
    public static void nativeFissionSodiumPotassiumKeepsTemperatureBoundaryAndIdleDamageRepair(GameTestHelper helper) {
        var machine = form(helper); fields(machine, 825, 0); start(helper, machine, 60, 1);
        var input = fluid(machine, IO.IN).tank; var output = fluid(machine, IO.OUT).tank;
        input.setFluidInTank(8, GTMaterials.SodiumPotassium.getFluid(20)); thermal(machine);
        helper.assertTrue(input.getFluidInTank(8).isEmpty() && output.getFluidInTank(0).getFluid() == NativeFissionFixtures.HOT.getFluid()
                && output.getFluidInTank(0).getAmount() == 20 && machine.getRecipeLogic().getProgress() == 0,
                "Native 825K sodium-potassium fallback produced the wrong fluid or free acceleration");
        output.setFluidInTank(0, FluidStack.EMPTY); fields(machine, 826, 0); input.setFluidInTank(8, GTMaterials.SodiumPotassium.getFluid(20)); thermal(machine);
        helper.assertTrue(input.getFluidInTank(8).isEmpty() && output.getFluidInTank(0).getFluid() == NativeFissionFixtures.SUPERCRITICAL.getFluid()
                && output.getFluidInTank(0).getAmount() == 20, "Native reactor lost its >825K supercritical coolant transition");
        machine.getRecipeLogic().interruptRecipe(); fields(machine, 299, 5); thermal(machine);
        helper.assertTrue(machine.getHeat() == 298 && machine.getDamage() == 5, "Idle native reactor repaired damage before reaching ambient heat");
        thermal(machine); helper.assertTrue(machine.getHeat() == 298 && machine.getDamage() == 4 && machine.getOutputSignal(null) == 15,
                "Idle native reactor failed its original ambient repair or emitted invalid redstone"); helper.succeed();
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_fission", timeoutTicks = 140)
    public static void nativeFissionCompletedBlockedCycleSurvivesReloadWithoutExtraInputsOrEnergy(GameTestHelper helper) {
        var machine = form(helper); start(helper, machine, 20, 15); pause(machine);
        var output = bus(machine, IO.OUT).getInventory();
        for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        ticks(machine.getRecipeLogic(), 20); fields(machine, 650, 17);
        helper.assertTrue(machine.getRecipeLogic().isWaiting() && machine.getRecipeLogic().getProgress() == 20
                && machine.getEnergyContainer().getEnergyStored() == 6000 && machine.getRunningParallel() == 2,
                "Completed native reactor: status=" + machine.getRecipeLogic().getStatus() + " progress=" + machine.getRecipeLogic().getProgress() + " energy=" + machine.getEnergyContainer().getEnergyStored() + " parallel=" + machine.getRunningParallel());
        ticks(machine.getRecipeLogic(), 3); var copy = reload(helper, machine);
        helper.assertTrue(copy.isFormed() && copy.getHeat() == 650 && copy.getDamage() == 17 && copy.getRunningParallel() == 2
                && copy.getRecipeHeat() == 15 && copy.getFuelCount() == 2 && copy.getRecipeLogic().isWaiting()
                && copy.getRecipeLogic().getProgress() == 20 && copy.getRecipeLogic().isSuspendAfterFinish()
                && ((ISuspendableMachine) copy).gtlcore$isSuspendAfterFinish(), "Native reactor reload lost paid completion, pause or thermal fields");
        output = bus(copy, IO.OUT).getInventory();
        for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, ItemStack.EMPTY);
        copy.getRecipeLogic().serverTick();
        helper.assertTrue(copy.getRecipeLogic().isSuspend() && output.getStackInSlot(0).getCount() == 2
                && output.getStackInSlot(0).is(Items.DIAMOND) && copy.getEnergyContainer().getEnergyStored() == 6000
                && bus(copy, IO.IN).getInventory().getStackInSlot(0).getCount() == 6 && copy.getRunningParallel() == 0,
                "Reloaded native reactor repeated inputs/EU, lost outputs or ignored pause"); helper.succeed();
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_fission", timeoutTicks = 140)
    public static void nativeFissionInFlightReloadRetainsExactPaidProgressAndThermalParameters(GameTestHelper helper) {
        var machine = form(helper); fields(machine, 900, 22); start(helper, machine, 20, 30); pause(machine); ticks(machine.getRecipeLogic(), 7);
        var copy = reload(helper, machine);
        helper.assertTrue(copy.getRecipeLogic().getProgress() == 7 && copy.getRecipeHeat() == 30 && copy.getRunningParallel() == 2
                && copy.getHeat() == 900 && copy.getDamage() == 22 && copy.getEnergyContainer().getEnergyStored() == 8600,
                "Native reactor in-flight save lost the paid seven ticks or thermal recipe parameters");
        ticks(copy.getRecipeLogic(), 13);
        helper.assertTrue(copy.getRecipeLogic().isSuspend() && copy.getEnergyContainer().getEnergyStored() == 6000
                && bus(copy, IO.IN).getInventory().getStackInSlot(0).getCount() == 6 && bus(copy, IO.OUT).getInventory().getStackInSlot(0).getCount() == 2,
                "Reloaded native reactor failed exactly thirteen remaining ticks or repeated its inputs"); helper.succeed();
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_fission", timeoutTicks = 140)
    public static void nativeFissionActualPartUnloadStopsThermalWorkAndRebindsWithoutResettingPaidRecipe(GameTestHelper helper) {
        var machine = form(helper); fields(machine, 900, 22); start(helper, machine, 20, 30); pause(machine); ticks(machine.getRecipeLogic(), 7);
        var inputPart = bus(machine, IO.IN); inputPart.onUnload(); thermal(machine);
        helper.assertTrue(!machine.isRecipeLogicAvailable() && machine.getFuelCount() == 0 && machine.getHeat() == 900
                && machine.getDamage() == 22 && machine.getRecipeLogic().getProgress() == 7 && machine.getRunningParallel() == 2,
                "Native part unload kept reactor subscriptions active or discarded paid progress");
        inputPart.onLoad(); machine.checkAndFormStructure();
        helper.assertTrue(machine.isRecipeLogicAvailable() && machine.getFuelCount() == 2 && machine.getCoolerAdjacency() == 7
                && machine.getRecipeLogic().getProgress() == 7 && machine.getRunningParallel() == 2,
                "Native reloaded hatch failed to rebind accepted membership without replaying the recipe");
        ticks(machine.getRecipeLogic(), 13);
        helper.assertTrue(machine.getRecipeLogic().isSuspend() && machine.getEnergyContainer().getEnergyStored() == 6000
                && bus(machine, IO.OUT).getInventory().getStackInSlot(0).getCount() == 2,
                "Native reloaded hatch lost the remaining paid work or repeated energy"); helper.succeed();
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_fission", timeoutTicks = 100)
    public static void nativeFissionActualThermalSubscriptionExplodesAfterDamageReachesOneHundred(GameTestHelper helper) {
        var machine = form(helper); fields(machine, 2000, 99); var pos = machine.getBlockPos();
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(helper.getLevel().getBlockEntity(pos) == null && helper.getLevel().getBlockState(pos).isAir(),
                    "Actual native reactor thermal subscription failed its overheat damage/explosion deadline"); helper.succeed();
        });
    }
}
