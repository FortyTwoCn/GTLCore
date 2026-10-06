package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.NativeLargeBoilerFuel;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.LargeBoilerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.*;
import com.gregtechceu.gtceu.common.machine.trait.customlogic.LargeBoilerLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import java.util.LinkedHashMap;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeLargeBoilerGameTests {
    private static LargeBoilerMachine build(GameTestHelper helper) {
        var entry = GTMultiMachines.LARGE_BOILER_BRONZE;
        var pattern = (BlockPattern) entry.get().getStructurePatterns().get("main").get();
        var repeats = new Int2IntOpenHashMap();
        for (int n = 0; n < pattern.getSlices().length; n++) repeats.put(n, pattern.getSlices()[n].getMinRepeats());
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>();
        var preview = AbstractStructureHelper.blockPattern(repeats);
        for (var predicate : pattern.getPredicates().values()) for (var base : predicate.expand())
            if (base.getCandidates().stream().anyMatch(candidate -> PartAbility.IMPORT_ITEMS.isApplicable(candidate.getBlockState().getBlock())))
                preview.getMinMaxPreferences().put(predicate, base, IntIntPair.of(1, 1));
        preview.populate(blocks, pattern, null, Direction.NORTH, Direction.UP, false);
        var origin = new BlockPos(3, 3, 3); BlockPos controller = null;
        for (var block : blocks.entrySet()) {
            var pos = origin.offset(block.getKey()); helper.setBlock(pos, block.getValue().getBlockState());
            if (block.getValue().getBlockState().is(entry.getBlock())) controller = pos;
        }
        for (var pos : blocks.keySet()) {
            var part = helper.getLevel().getBlockEntity(helper.absolutePos(origin.offset(pos)));
            if (part instanceof MaintenanceHatchPartMachine maintenance) maintenance.setMaintenanceProblems(MaintenanceHatchPartMachine.NO_PROBLEMS);
            if (part instanceof MufflerPartMachine muffler) helper.getLevel().setBlockAndUpdate(muffler.getBlockPos().relative(muffler.getFrontFacing()), Blocks.AIR.defaultBlockState());
        }
        var machine = (LargeBoilerMachine) helper.getBlockEntity(controller); machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Original native large boiler failed to form: " + machine.getDefaultPatternState().getErrors());
        item(machine).getInventory().storage.setStackInSlot(0, new ItemStack(Items.COAL, 2));
        fluid(machine, PartAbility.IMPORT_FLUIDS).tank.setFluidInTank(0, new FluidStack(Fluids.WATER, 10_000));
        return machine;
    }
    private static ItemBusPartMachine item(LargeBoilerMachine machine) { return machine.getParts().stream().filter(ItemBusPartMachine.class::isInstance).map(ItemBusPartMachine.class::cast).findFirst().orElseThrow(); }
    private static FluidHatchPartMachine fluid(LargeBoilerMachine machine, PartAbility ability) { return machine.getParts().stream().filter(FluidHatchPartMachine.class::isInstance).map(FluidHatchPartMachine.class::cast).filter(p -> ability.isApplicable(p.getBlockState().getBlock())).findFirst().orElseThrow(); }
    private static void throttle(LargeBoilerMachine machine, int value) {
        try { var method = LargeBoilerMachine.class.getDeclaredMethod("setThrottle", int.class); method.setAccessible(true); method.invoke(machine, value); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void originalBoilerBurnsOneNativeCoalRecipeAndThrottleChangesPreserveConsumedFuel(GameTestHelper helper) {
        var machine = build(helper); throttle(machine, 50);
        var source = LargeBoilerLogic.INSTANCE.createCustomRecipe(machine);
        helper.assertTrue(source != null && source.duration > 0, "Original native coal fuel lookup returned no recipe");
        int thermal = NativeLargeBoilerFuel.thermalModifier(machine.getMaxTemperature()).apply(source).duration;
        var logic = machine.getRecipeLogic();
        helper.assertTrue(logic.checkMatchedRecipeAvailable(source), "Native boiler coal recipe was rejected: " + logic.getBestFailureReason());
        helper.assertTrue(logic.getDuration() == NativeLargeBoilerFuel.burnTicks(thermal, 50), "Boiler applied throttle twice");
        for (int tick = 0; tick < 10; tick++) logic.serverTick();
        helper.assertTrue(logic.getProgress() == 10, "Native fuel recipe did not advance");
        throttle(machine, 25);
        helper.assertTrue(logic.getProgress() == 20 && logic.getDuration() == NativeLargeBoilerFuel.burnTicks(thermal, 25), "Lower throttle lost the burned fuel fraction");
        throttle(machine, 100);
        helper.assertTrue(logic.getProgress() == 5 && logic.getDuration() == thermal, "Higher throttle duplicated burned fuel");
        logic.setSuspendAfterFinish(true);
        int remaining = logic.getDuration() - logic.getProgress();
        for (int tick = 0; tick < remaining; tick++) logic.serverTick();
        helper.assertTrue(logic.isSuspend() && item(machine).getInventory().getStackInSlot(0).getCount() == 1 && source.duration > 0,
                "Native boiler did not consume exactly one original coal fuel cycle");
        throttle(machine, 0); helper.assertTrue(machine.getThrottle() == 25, "Invalid server throttle bypassed the lower bound");
        throttle(machine, Integer.MAX_VALUE); helper.assertTrue(machine.getThrottle() == 100, "Invalid server throttle bypassed the upper bound");
        helper.succeed();
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeHotBoilerRestoresItsTickSubscriptionAndConservesWaterAfterSaveReload(GameTestHelper helper) {
        var machine = build(helper); throttle(machine, 50);
        var source = LargeBoilerLogic.INSTANCE.createCustomRecipe(machine);
        helper.assertTrue(machine.getRecipeLogic().checkMatchedRecipeAvailable(source), "Native hot boiler did not accept its coal recipe");
        try {
            // Controlled hot state isolates persistence and actual steam IO from the separate warmup period.
            var field = LargeBoilerMachine.class.getDeclaredField("currentTemperature"); field.setAccessible(true); field.setInt(machine, machine.getMaxTemperature());
            var update = LargeBoilerMachine.class.getDeclaredMethod("updateSteamSubscription"); update.setAccessible(true); update.invoke(machine);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        helper.runAfterDelay(6, () -> {
            var output = fluid(machine, PartAbility.EXPORT_FLUIDS);
            helper.assertTrue(output.tank.getFluidInTank(0).is(GTMaterials.Steam.getFluid()) && output.tank.getFluidInTank(0).getAmount() > 0,
                    "Real native server ticks produced no steam from the hot boiler");
            var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
            var entry = GTMultiMachines.LARGE_BOILER_BRONZE;
            var restored = (LargeBoilerMachine) entry.getBlockEntityType().create(machine.getBlockPos(), machine.getBlockState());
            helper.getLevel().removeBlockEntity(machine.getBlockPos());
            restored.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(restored); restored.onLoad();
            try {
                var field = LargeBoilerMachine.class.getDeclaredField("temperatureSubs"); field.setAccessible(true);
                helper.assertTrue(field.get(restored) != null, "Native onLoad did not restore the persisted hot boiler subscription");
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            restored.checkAndFormStructure();
            helper.assertTrue(restored.isFormed() && restored.getThrottle() == 50 && restored.getCurrentTemperature() == restored.getMaxTemperature(),
                    "Native boiler reload lost structure, throttle or hot state");
            verifyRestoredSteam(helper, restored);
        });
    }

    private static void verifyRestoredSteam(GameTestHelper helper, LargeBoilerMachine restored) {
        var input = fluid(restored, PartAbility.IMPORT_FLUIDS); var output = fluid(restored, PartAbility.EXPORT_FLUIDS);
        int water = input.tank.getFluidInTank(0).getAmount(), steam = output.tank.getFluidInTank(0).getAmount();
        int progress = restored.getRecipeLogic().getProgress();
        helper.runAfterDelay(6, () -> {
            int drained = water - input.tank.getFluidInTank(0).getAmount();
            int generated = output.tank.getFluidInTank(0).getAmount() - steam;
            helper.assertTrue(drained > 0 && generated == drained * com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.machines.largeBoilers.steamPerWater &&
                    restored.getRecipeLogic().getProgress() > progress && item(restored).getInventory().getStackInSlot(0).getCount() == 1,
                    "Restored native boiler stalled, duplicated coal or broke the original water/steam ratio");
            helper.succeed();
        });
    }
}
