package org.gtlcore.gtlcore.migration;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.recipe.condition.AdjacentFluidCondition;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test") @PrefixGameTestTemplate(false)
public final class NativeRockBreakerGameTests {
    private static GTRecipe original() {
        var recipe = GTRecipeBuilder.ofRaw().build(); recipe.data.putString("fluidA", "minecraft:water"); recipe.data.putString("fluidB", "minecraft:lava"); return recipe;
    }
    private static WorkableElectricMultiblockMachine form(GameTestHelper helper) {
        var shape = new LinkedHashMap<BlockPos, BlockInfo>(); var entry = NativeRockBreakerFixtures.CONTROLLER;
        AbstractStructureHelper.blockPattern(new Int2IntOpenHashMap(Map.of(0, 1))).populate(shape,
                entry.get().getStructurePatterns().get("main").get(), null, Direction.NORTH, Direction.UP, false);
        BlockPos controller = null;
        for (var block : shape.entrySet()) {
            var pos = new BlockPos(4, 12, 4).offset(block.getKey()); helper.setBlock(pos, block.getValue().getBlockState());
            if (block.getValue().getBlockState().is(entry.getBlock())) controller = pos;
        }
        var machine = (WorkableElectricMultiblockMachine) helper.getBlockEntity(Objects.requireNonNull(controller)); machine.checkAndFormStructure(); machine.setWorkingEnabled(false);
        helper.assertTrue(machine.isFormed() && machine.getParts().size() == 2, "Native rock-condition fixture did not form with actual input/output hatches"); return machine;
    }
    private static FluidHatchPartMachine tank(WorkableElectricMultiblockMachine machine, IO io) {
        return machine.getParts().stream().filter(FluidHatchPartMachine.class::isInstance).map(FluidHatchPartMachine.class::cast)
                .filter(part -> part.tank.getHandlerIO() == io).findFirst().orElseThrow();
    }
    private static RecipeLogic simple(GameTestHelper helper) {
        var pos = new BlockPos(4, 12, 4); helper.setBlock(pos, GTMachines.ROCK_CRUSHER[GTValues.LV].getBlock().defaultBlockState());
        return ((com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine) helper.getBlockEntity(pos)).getRecipeLogic();
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_rock_condition", timeoutTicks = 120)
    public static void nativeRockConditionInspectsEveryActualInputTankRejectsOutputStockAndNeverConsumes(GameTestHelper helper) {
        var machine = form(helper); var input = tank(machine, IO.IN).tank; var output = tank(machine, IO.OUT).tank;
        var recipe = original(); var condition = new AdjacentFluidCondition();
        helper.assertTrue(!condition.check(recipe, machine.getRecipeLogic()), "Empty native rock input passed its environment gate");
        input.setFluidInTank(3, new FluidStack(Fluids.WATER, 20000)); output.setFluidInTank(8, new FluidStack(Fluids.LAVA, 20000));
        helper.assertTrue(!condition.check(recipe, machine.getRecipeLogic()), "Native rock condition counted an output hatch as input");
        input.setFluidInTank(8, new FluidStack(Fluids.LAVA, 20000));
        helper.assertTrue(condition.check(recipe, machine.getRecipeLogic()) && input.getFluidInTank(3).getAmount() == 20000
                && input.getFluidInTank(8).getAmount() == 20000 && output.getFluidInTank(8).getAmount() == 20000,
                "Native rock condition ignored nonzero input slots or consumed its environmental fluids");
        recipe.data.putString("fluidB", "minecraft:milk"); helper.assertTrue(!condition.check(recipe, machine.getRecipeLogic()), "Native rock condition cached another recipe's fluid IDs");
        recipe.data.putString("fluidB", "unregistered:missing"); helper.assertTrue(!condition.check(recipe, machine.getRecipeLogic()), "Unknown native rock fluid silently became an empty fluid");
        recipe.data.putString("fluidB", "bad:bad id"); helper.assertTrue(!condition.check(recipe, machine.getRecipeLogic()), "Malformed native rock ID crashed or passed");
        recipe.data.remove("fluidB"); helper.assertTrue(!condition.check(recipe, machine.getRecipeLogic()), "Incomplete original rock requirements passed");
        recipe.data.putString("fluidB", "minecraft:lava");
        var decoded = RecipeCondition.deserialize(helper.getLevel().registryAccess(), condition.serialize(helper.getLevel().registryAccess()));
        helper.assertTrue(decoded.check(recipe, machine.getRecipeLogic()), "Native adjacent-fluid codec lost original data-driven tank requirements");
        machine.invalidateStructure("main"); machine.checkAndFormStructure(); helper.assertTrue(condition.check(recipe, machine.getRecipeLogic()), "Native repaired rock controller did not restore fluid capability lookup");
        helper.succeed();
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_rock_condition", timeoutTicks = 120)
    public static void nativeOriginalSingleRockConditionKeepsHorizontalFluidsDuplicateKeysAndReverseWithoutStaleData(GameTestHelper helper) {
        var logic = simple(helper); var pos = logic.getMachine().getBlockPos(); var level = helper.getLevel(); var recipe = original(); var condition = new AdjacentFluidCondition();
        level.setBlockAndUpdate(pos.above(), Blocks.WATER.defaultBlockState()); level.setBlockAndUpdate(pos.east(), Blocks.LAVA.defaultBlockState());
        helper.assertTrue(!condition.check(recipe, logic), "Original native single-machine rule admitted a vertical fluid");
        level.setBlockAndUpdate(pos.north(), Blocks.WATER.defaultBlockState());
        helper.assertTrue(condition.check(recipe, logic) && level.getBlockState(pos.north()).is(Blocks.WATER)
                && level.getBlockState(pos.east()).is(Blocks.LAVA), "Original horizontal native rock check consumed or lost adjacent fluids");
        recipe.data.putString("fluidB", "minecraft:water"); level.setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(condition.check(recipe, logic), "Duplicate original fluid keys required two owned sources");
        recipe.data.putString("fluidB", "minecraft:lava"); helper.assertTrue(!condition.check(recipe, logic), "Changed original recipe retained stale resolved fluid keys");
        condition.setReverse(true); helper.assertTrue(condition.check(recipe, logic), "Native reverse was ignored or applied twice");
        level.setBlockAndUpdate(pos.east(), Blocks.LAVA.defaultBlockState()); helper.assertTrue(!condition.check(recipe, logic), "Native reverse failed after restoring both horizontal fluids"); helper.succeed();
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_rock_condition", timeoutTicks = 120)
    public static void explicitNativeAdjacentFluidParametersKeepTheirWorldSemanticsAndMultiblockTankCodec(GameTestHelper helper) {
        var logic = simple(helper); var pos = logic.getMachine().getBlockPos(); var level = helper.getLevel();
        level.setBlockAndUpdate(pos.above(), Blocks.WATER.defaultBlockState()); level.setBlockAndUpdate(pos.east(), Blocks.LAVA.defaultBlockState());
        var configured = AdjacentFluidCondition.fromFluids(Fluids.WATER, Fluids.LAVA); var recipe = original(); recipe.data.putString("fluidB", "unregistered:missing");
        helper.assertTrue(configured.check(recipe, logic), "Explicit native fluid condition lost its three-dimensional source rule or was overwritten by recipe metadata");
        var controller = form(helper); var input = tank(controller, IO.IN).tank;
        input.setFluidInTank(3, new FluidStack(Fluids.WATER, 37)); input.setFluidInTank(8, new FluidStack(Fluids.LAVA, 91));
        var decoded = RecipeCondition.deserialize(level.registryAccess(), configured.serialize(level.registryAccess()));
        helper.assertTrue(decoded.check(recipe, controller.getRecipeLogic()) && input.getFluidInTank(3).getAmount() == 37 && input.getFluidInTank(8).getAmount() == 91,
                "Native configured fluid codec lost multi-slot environmental requirements or consumed tank stock");
        input.setFluidInTank(8, FluidStack.EMPTY); helper.assertTrue(!decoded.check(recipe, controller.getRecipeLogic()), "Native configured tank gate accepted a missing group"); helper.succeed();
    }
}
