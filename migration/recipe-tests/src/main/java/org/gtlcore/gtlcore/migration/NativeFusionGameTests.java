package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.IFusionHeatTransaction;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import java.util.LinkedHashMap;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeFusionGameTests {
    private static final long START = 100_000;
    private static FusionReactorMachine build(GameTestHelper helper) {
        var entry = GTMultiMachines.FUSION_REACTOR[GTValues.LuV];
        var pattern = (BlockPattern) entry.get().getStructurePatterns().get("main").get();
        var repeats = new Int2IntOpenHashMap();
        for (int n = 0; n < pattern.getSlices().length; n++) repeats.put(n, pattern.getSlices()[n].getMinRepeats());
        var preview = AbstractStructureHelper.blockPattern(repeats);
        var energy = BlockInfo.fromBlock(GTMachines.ENERGY_INPUT_HATCH[GTValues.LuV].getBlock());
        for (var predicate : pattern.getPredicates().values()) for (var base : predicate.expand()) {
            if (base.getCandidates().stream().anyMatch(candidate -> candidate.getBlockState().is(energy.getBlockState().getBlock())))
                preview.getBlockPreferences().put(predicate, base, energy);
            if (base.getCandidates().stream().anyMatch(candidate -> PartAbility.EXPORT_FLUIDS.isApplicable(candidate.getBlockState().getBlock())))
                preview.getMinMaxPreferences().put(predicate, base, IntIntPair.of(1, 1));
        }
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>();
        preview.populate(blocks, pattern, null, Direction.NORTH, Direction.UP, false);
        var origin = new BlockPos(4, 6, 4); BlockPos controller = null;
        for (var block : blocks.entrySet()) {
            var pos = origin.offset(block.getKey()); helper.setBlock(pos, block.getValue().getBlockState());
            if (block.getValue().getBlockState().is(entry.getBlock())) controller = pos;
        }
        var machine = (FusionReactorMachine) helper.getBlockEntity(controller); machine.setFrontFacing(Direction.NORTH);
        machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Original native fusion ring failed to form: " + machine.getDefaultPatternState().getErrors());
        machine.getEnergyContainer().addEnergy(100_000_000);
        ((IFusionHeatTransaction) machine).gtlcore$getFusionEnergy().setEnergyStored(2_000_000);
        return machine;
    }
    private static FluidHatchPartMachine hatch(FusionReactorMachine machine, PartAbility ability) {
        return machine.getParts().stream().filter(FluidHatchPartMachine.class::isInstance).map(FluidHatchPartMachine.class::cast)
                .filter(part -> ability.isApplicable(part.getBlockState().getBlock())).findFirst().orElseThrow();
    }
    private static GTRecipe recipe() {
        return GTRecipeTypes.FUSION_RECIPES.recipeBuilder("native_paid_fusion_cycle")
                .inputFluids(GTMaterials.Hydrogen.getFluid(100)).outputFluids(GTMaterials.Helium.getFluid(50))
                .duration(4).EUt(GTValues.VA[GTValues.LuV]).addData("eu_to_start", START).build();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void originalNativeFusionRingPaysOnlyAcceptedInputsAndCompletesOnePhysicalCycle(GameTestHelper helper) {
        var machine = build(helper); var fusion = (IFusionHeatTransaction) machine;
        var input = hatch(machine, PartAbility.IMPORT_FLUIDS); var output = hatch(machine, PartAbility.EXPORT_FLUIDS);
        var source = recipe(); var logic = machine.getRecipeLogic();
        for (int n = 0; n < 10; n++) helper.assertTrue(FusionReactorMachine.recipeModifier(machine, source).apply(source) != null, "Native fusion preview was rejected");
        helper.assertTrue(fusion.gtlcore$getFusionHeat() == 0 && fusion.gtlcore$getFusionEnergy().getEnergyStored() == 2_000_000,
                "Native fusion previews charged startup heat");
        helper.assertTrue(!logic.checkMatchedRecipeAvailable(source), "Fusion accepted missing physical input");
        logic.setupRecipe(source); // Exercise the actual failed input operation after its heat reservation.
        helper.assertTrue(fusion.gtlcore$getFusionHeat() == 0 && fusion.gtlcore$getFusionEnergy().getEnergyStored() == 2_000_000,
                "Native rejected physical input retained a heat payment");
        input.tank.setFluidInTank(0, GTMaterials.Hydrogen.getFluid(1000));
        output.tank.setFluidInTank(0, new FluidStack(Fluids.WATER, output.tank.getTankCapacity(0)));
        helper.assertTrue(!logic.checkMatchedRecipeAvailable(source) && input.tank.getFluidInTank(0).getAmount() == 1000 &&
                fusion.gtlcore$getFusionEnergy().getEnergyStored() == 2_000_000, "Blocked fusion output consumed input or startup energy");
        output.tank.setFluidInTank(0, FluidStack.EMPTY);
        helper.assertTrue(logic.checkMatchedRecipeAvailable(source), "Native fusion recipe did not start: " + logic.getBestFailureReason());
        helper.assertTrue(fusion.gtlcore$getFusionHeat() == START && fusion.gtlcore$getFusionEnergy().getEnergyStored() == 2_000_000 - START,
                "Accepted fusion input did not commit exactly one heat difference");
        logic.setSuspendAfterFinish(true);
        for (int tick = 0; tick < logic.getDuration(); tick++) logic.serverTick();
        helper.assertTrue(logic.isSuspend() && input.tank.getFluidInTank(0).getAmount() == 900 &&
                output.tank.getFluidInTank(0).is(GTMaterials.Helium.getFluid()) && output.tank.getFluidInTank(0).getAmount() == 50 &&
                fusion.gtlcore$getFusionEnergy().getEnergyStored() == 2_000_000 - START && source.duration == 4,
                "Native fusion cycle duplicated startup payment or physical output");
        helper.succeed();
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeFusionHeatPersistsAndCoolingRequiresOneNewPaymentBeforeAnotherCycle(GameTestHelper helper) {
        var machine = build(helper); var source = recipe();
        hatch(machine, PartAbility.IMPORT_FLUIDS).tank.setFluidInTank(0, GTMaterials.Hydrogen.getFluid(1000));
        var logic = machine.getRecipeLogic();
        helper.assertTrue(logic.checkMatchedRecipeAvailable(source), "Initial native fusion cycle was rejected");
        logic.setSuspendAfterFinish(true);
        for (int tick = 0; tick < 4; tick++) logic.serverTick();
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        var entry = GTMultiMachines.FUSION_REACTOR[GTValues.LuV];
        var restored = (FusionReactorMachine) entry.getBlockEntityType().create(machine.getBlockPos(), machine.getBlockState());
        helper.getLevel().removeBlockEntity(machine.getBlockPos());
        restored.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(restored);
        restored.checkAndFormStructure();
        var fusion = (IFusionHeatTransaction) restored;
        helper.assertTrue(restored.isFormed() && fusion.gtlcore$getFusionHeat() == START &&
                fusion.gtlcore$getFusionEnergy().getEnergyStored() == 2_000_000 - START,
                "Original native SaveField reload lost fusion heat or stored startup energy");
        restored.getEnergyContainer().removeEnergy(restored.getEnergyContainer().getEnergyStored());
        fusion.gtlcore$getFusionEnergy().setEnergyStored(0);
        for (int tick = 0; tick < 10; tick++) restored.updateHeat();
        helper.assertTrue(fusion.gtlcore$getFusionHeat() == 0, "Native paused fusion did not cool at its original rate");
        logic = restored.getRecipeLogic(); logic.setWorkingEnabled(true);
        var input = hatch(restored, PartAbility.IMPORT_FLUIDS); var output = hatch(restored, PartAbility.EXPORT_FLUIDS);
        helper.assertTrue(!logic.checkMatchedRecipeAvailable(source) && input.tank.getFluidInTank(0).getAmount() == 900,
                "A cooled fusion reactor consumed inputs without startup energy");
        restored.getEnergyContainer().addEnergy(100_000_000); fusion.gtlcore$getFusionEnergy().setEnergyStored(START);
        helper.assertTrue(logic.checkMatchedRecipeAvailable(source) && fusion.gtlcore$getFusionHeat() == START &&
                fusion.gtlcore$getFusionEnergy().getEnergyStored() == 0, "Cooled fusion did not pay exactly the missing heat");
        logic.setSuspendAfterFinish(true);
        for (int tick = 0; tick < 4; tick++) logic.serverTick();
        helper.assertTrue(logic.isSuspend() && input.tank.getFluidInTank(0).getAmount() == 800 &&
                output.tank.getFluidInTank(0).getAmount() == 100 && fusion.gtlcore$getFusionEnergy().getEnergyStored() == 0,
                "Native restored fusion duplicated input, output or heat payment");
        helper.succeed();
    }
}
