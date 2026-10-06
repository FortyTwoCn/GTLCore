package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;
import org.gtlcore.gtlcore.api.recipe.*;
import org.gtlcore.gtlcore.api.recipe.ingredient.*;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic.OCParams;
import com.gregtechceu.gtceu.api.recipe.modifier.*;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.gtlcore.gtlcore.migration.NativeCoreRecipeRunnerIntegrationTest.*;

class NativeOverclockIntegrationTest {
    @Test void nativeVoltageOnlyOverclockKeepsDurationAndDoesNotInventSubtickParallels() {
        var params = new OCParams(32, 400, 3, 16);
        for (boolean subtick : new boolean[] {false, true}) {
            var result = OverclockingLogic.create(1, 4, subtick).runOverclockingLogic(params, 2048);
            assertEquals(3, result.ocLevel()); assertEquals(64, result.eutMultiplier());
            assertEquals(1, result.durationMultiplier()); assertEquals(1, result.parallels());
            assertEquals(3, ((IAdvancedOCResult) (Object) result).getBaseOCLevel());
            assertFalse(((IAdvancedOCResult) (Object) result).isSubTickOverclockAvailable());
            var source = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("voltage_only_oc").duration(400).EUt(32).build();
            var modified = result.toModifier().apply(source);
            assertEquals(400, modified.duration); assertEquals(2048, modified.getInputEUt().getTotalEU());
            assertEquals(400, source.duration); assertEquals(32, source.getInputEUt().getTotalEU());
        }
    }

    @Test void invalidNativeDurationFactorsStillFailBeforeApplyingAnOverclock() {
        for (double factor : new double[] {0, -1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,
                    () -> OverclockingLogic.create(factor, 4, false).runOverclockingLogic(new OCParams(32, 400, 3, 1), 2048));
    }

    @Test void standardOverclockStopsAtOneTickAndRecordsUnusedHeadroom() {
        var result = OverclockingLogic.standardOC(new OCParams(32, 4, 3, 1), 2048, .5, 4);
        assertEquals(2, result.ocLevel()); assertEquals(16, result.eutMultiplier()); assertEquals(.25, result.durationMultiplier());
        assertEquals(1, result.parallels()); assertTrue(BatchProcessing.canOverclockBelowOneTick(result));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("standard_oc").duration(4).EUt(32).build();
        var modified = result.toModifier().apply(recipe);
        assertEquals(1, modified.duration); assertEquals(512, modified.getInputEUt().getTotalEU());
        assertTrue(IGTRecipe.of(modified).isSubTickParallelized()); assertFalse(IGTRecipe.of(recipe).isSubTickParallelized());
        assertEquals(4, recipe.duration); assertEquals(32, recipe.getInputEUt().getTotalEU());
    }

    @Test void nonPerfectSubTickOverclockUsesTheActualPartialParallelCountForEnergyAndTier() {
        var result = OverclockingLogic.subTickParallelOC(new OCParams(32, 1, 2, 3), 512, .5, 4);
        assertEquals(3, result.parallels()); assertEquals(2, result.ocLevel()); assertEquals(12, result.eutMultiplier());
        assertEquals(0, ((IAdvancedOCResult) (Object) result).getBaseOCLevel());
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("partial_oc").inputItems(Items.IRON_INGOT)
                .outputFluids(SizedFluidIngredient.of(Fluids.WATER, 100)).duration(1).EUt(32).build();
        var modified = result.toModifier().apply(recipe);
        assertEquals(384, modified.getInputEUt().getTotalEU()); assertEquals(2, modified.ocLevel);
        assertEquals(3, IGTRecipe.of(modified).getRealParallels()); assertEquals(3, modified.subtickParallels);
        assertEquals(300, LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(modified.outputs.get(FluidRecipeCapability.CAP).getFirst().content())));
    }

    @Test void perfectSubTickOverclockAndHeatingCoilsKeepTheCoreSpeedPolicy() {
        var params = new OCParams(32, 4, 3, 16);
        var perfect = OverclockingLogic.subTickParallelOC(params, 2048, .25, 4);
        var coil = OverclockingLogic.heatingCoilOC(params, 2048, 1800, 1800);
        assertEquals(perfect.eutMultiplier(), coil.eutMultiplier()); assertEquals(perfect.durationMultiplier(), coil.durationMultiplier());
        assertEquals(16, coil.parallels()); assertEquals(3, coil.ocLevel());
        assertEquals(1, ((IAdvancedOCResult) (Object) coil).getBaseOCLevel());
    }

    @Test void voltageAndInputHeadroomLimitsNeverApplyAnExtraOverclock() {
        var voltageLimited = OverclockingLogic.subTickParallelOC(new OCParams(32, 1, 20, 64), 127, .5, 4);
        assertEquals(0, voltageLimited.ocLevel()); assertEquals(1, voltageLimited.eutMultiplier()); assertEquals(1, voltageLimited.parallels());
        var inputLimited = OverclockingLogic.subTickParallelOC(new OCParams(32, 1, 20, 1), 2048, .5, 4);
        assertEquals(0, inputLimited.ocLevel()); assertEquals(1, inputLimited.parallels());
        assertTrue(((IAdvancedOCResult) (Object) inputLimited).isSubTickOverclockAvailable());
    }

    @Test void nativeOverclockModifierUsesTheSelectedCoreBusForPartialParallels() {
        var holder = new CoreHolder();
        var rejected = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 64));
        var selected = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.DIAMOND));
        holder.inputs.addAll(List.of(new RecipeHandlePart(List.of(rejected)), new RecipeHandlePart(List.of(selected))));
        holder.outputs.add(new RecipeHandlePart(List.of(fluids(holder, IO.OUT, 1000, 0))));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("oc_selected_route").inputItems(Items.IRON_INGOT)
                .notConsumable(Items.DIAMOND).outputFluids(SizedFluidIngredient.of(Fluids.WATER, 100)).duration(1).EUt(32).build();
        var modified = OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK.getModifier(holder, recipe, 512, true).apply(recipe);
        assertNotNull(modified); assertEquals(3, IGTRecipe.of(modified).getRealParallels()); assertEquals(384, modified.getInputEUt().getTotalEU());
        assertSame(holder.inputs.get(1), holder.getActiveRecipeHandle(recipe)); assertEquals(64, rejected.getStackInSlot(0).getCount());
        assertEquals(3, selected.getStackInSlot(0).getCount()); assertEquals(1, IGTRecipe.of(recipe).getRealParallels());
    }

    @Test void nativeModifierCopiesScaleExactLongCountsAndKeepIndependentNativeFactors() {
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("long_native_modifier").duration(20).build();
        IGTRecipe.of(recipe).setRealParallels(9_007_199_254_740_993L);
        var modified = ModifierFunction.builder().parallels(3).subtickParallels(5).batchParallels(7).build().apply(recipe);
        assertNotNull(modified); assertEquals(945_755_921_747_804_265L, IGTRecipe.of(modified).getRealParallels());
        assertEquals(Integer.MAX_VALUE, modified.parallels); assertEquals(5, modified.subtickParallels); assertEquals(7, modified.batchParallels);
        assertEquals(9_007_199_254_740_993L, IGTRecipe.of(recipe).getRealParallels());
        assertNull(ModifierFunction.builder().parallels(Integer.MAX_VALUE).build().apply(recipe));
    }

    @Test void nativeModifierChainAppliesEachStepOnceAndCapturesPreOverclockReduction() {
        var holder = new CoreHolder(); var applications = new AtomicInteger(); var selections = new AtomicInteger();
        RecipeModifier reduce = (machine, recipe) -> {
            selections.incrementAndGet();
            return source -> {
                applications.incrementAndGet();
                return ModifierFunction.builder().durationMultiplier(.5).eutMultiplier(.5).build().apply(source);
            };
        };
        RecipeModifier overclock = (machine, recipe) -> OverclockingLogic.PERFECT_OVERCLOCK.getModifier(machine, recipe, 512, false);
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_modifier_chain").duration(16).EUt(32).build();
        var result = new RecipeModifierList(reduce, overclock).applyModifier(holder, recipe);
        assertNotNull(result); assertEquals(1, selections.get()); assertEquals(1, applications.get());
        assertEquals(.5, RecipeMultiplierTracker.get(holder).orElseThrow().energy());
        assertEquals(.5, RecipeMultiplierTracker.get(holder).orElseThrow().duration());
        assertEquals(16, recipe.duration); assertEquals(32, recipe.getInputEUt().getTotalEU());
    }

    @Test void modifierChainPreservesTheFailureReasonAndStopsBeforeLaterSteps() {
        var holder = new CoreHolder(); var calls = new AtomicInteger(); var reason = Component.literal("test prerequisite");
        RecipeModifier reject = (machine, recipe) -> ModifierFunction.cancel(reason);
        RecipeModifier later = (machine, recipe) -> { calls.incrementAndGet(); return ModifierFunction.IDENTITY; };
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_modifier_failure").duration(20).build();
        var function = new RecipeModifierList(reject, later).getModifier(holder, recipe);
        assertNull(function.apply(recipe)); assertEquals(reason, function.getFailReason()); assertEquals(0, calls.get());
        assertTrue(RecipeMultiplierTracker.get(holder).isEmpty());
    }
    private static final class CoilHolder extends com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine {
        com.gregtechceu.gtceu.api.recipe.GTRecipeType selectedType = GTRecipeTypes.VACUUM_RECIPES.get();
        CoilHolder() { super(new com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo(
                com.gregtechceu.gtceu.common.data.machines.GTMultiMachines.VACUUM_FREEZER.getBlockEntityType(),
                net.minecraft.core.BlockPos.ZERO, com.gregtechceu.gtceu.common.data.machines.GTMultiMachines.VACUUM_FREEZER.getBlock().defaultBlockState())); }
        @Override public int getTier() { return 3; }
        @Override public long getOverclockVoltage() { return 512; }
        @Override public long getMaxVoltage() { return 512; }
        @Override public com.gregtechceu.gtceu.api.recipe.GTRecipeType getRecipeType() { return selectedType; }
    }

    @Test void vacuumDryingAppliesSpeedBeforeParallelAndOverclockWithoutMutatingPreviews() {
        var holder = new CoilHolder();
        var recipe = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_vacuum_drying_order").duration(16).EUt(32).build();
        var modifier = org.gtlcore.gtlcore.common.data.GTLRecipeModifiers.VACUUM_DRYING;
        var preview = modifier.getModifier(holder, recipe);
        assertEquals(16, recipe.duration); assertEquals(32, recipe.getInputEUt().getTotalEU());
        var first = preview.apply(recipe); var second = preview.apply(recipe);
        assertNotNull(first); assertNotNull(second);
        assertEquals(2, first.duration); assertEquals(512, first.getInputEUt().getTotalEU());
        assertEquals(4, IGTRecipe.of(first).getRealParallels());
        assertEquals(first.duration, second.duration); assertEquals(first.getInputEUt(), second.getInputEUt());
        assertEquals(16, recipe.duration); assertEquals(1, IGTRecipe.of(recipe).getRealParallels());
    }

    @Test void migratedAlloyAndVacuumModifiersKeepNativeTemperatureRejectionAndRecovery() {
        var holder = new CoilHolder();
        holder.selectedType = com.gregtechceu.gtceu.common.data.GCYMRecipeTypes.ALLOY_BLAST_RECIPES.get();
        var tooHot = holder.selectedType.recipeBuilder("native_migrated_temperature").duration(16).EUt(32).blastFurnaceTemp(3000).build();
        var alloy = org.gtlcore.gtlcore.common.data.GTLRecipeModifiers.MEGA_ALLOY_OVERCLOCK;
        assertNull(alloy.applyModifier(holder, tooHot));
        assertNull(org.gtlcore.gtlcore.common.data.GTLRecipeModifiers.VACUUM_DRYING.applyModifier(holder, tooHot));
        assertEquals(16, tooHot.duration); assertEquals(3000, tooHot.data.getInt("ebf_temp"));
        var acceptable = tooHot.copy(); acceptable.data = acceptable.data.copy(); acceptable.data.putInt("ebf_temp", 900);
        assertNotNull(alloy.applyModifier(holder, acceptable));
        assertNotNull(org.gtlcore.gtlcore.common.data.GTLRecipeModifiers.VACUUM_DRYING.applyModifier(holder, acceptable));
        holder.selectedType = GTRecipeTypes.ALLOY_SMELTER_RECIPES.get();
        var ordinary = alloy.applyModifier(holder, tooHot);
        assertNotNull(ordinary); assertEquals(16, ordinary.duration);
    }

    @Test void engravingLaserSelectsSubtickParallelismOnlyForTheOrdinaryRecipeType() {
        var holder = new CoilHolder();
        var input = holder.attachTrait(new com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler(1, IO.IN, IO.IN));
        input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        holder.addHandlerList(RecipeHandlerList.of(IO.IN, 0, input));
        var recipe = GTRecipeTypes.LASER_ENGRAVER_RECIPES.recipeBuilder("native_laser_subtick")
                .inputItems(Items.IRON_INGOT).duration(2).EUt(32).build();
        holder.selectedType = GTRecipeTypes.LASER_ENGRAVER_RECIPES.get();
        var ordinary = org.gtlcore.gtlcore.common.data.GTLRecipeModifiers.ENGRAVING_LASER.applyModifier(holder, recipe);
        holder.selectedType = org.gtlcore.gtlcore.common.data.GTLRecipeTypes.PRECISION_LASER_ENGRAVER_RECIPES.get();
        var precision = org.gtlcore.gtlcore.common.data.GTLRecipeModifiers.ENGRAVING_LASER.applyModifier(holder, recipe);
        assertNotNull(ordinary); assertNotNull(precision);
        assertEquals(2, ordinary.duration); assertEquals(2, precision.duration);
        assertEquals(16, IGTRecipe.of(ordinary).getRealParallels());
        assertEquals(1, IGTRecipe.of(precision).getRealParallels());
        assertEquals(2, recipe.duration); assertEquals(32, recipe.getInputEUt().getTotalEU());
        assertEquals(64, input.getStackInSlot(0).getCount());
    }

    @Test void dimensionalMixerUsesItsSpeedFactorOnlyOnTheOrdinaryMixerType() {
        var holder = new CoilHolder();
        var recipe = GTRecipeTypes.MIXER_RECIPES.recipeBuilder("native_mixer_speed").duration(25).EUt(32).build();
        var modifier = org.gtlcore.gtlcore.common.data.GTLRecipeModifiers.DIMENSIONAL_MIXER;
        holder.selectedType = GTRecipeTypes.MIXER_RECIPES.get();
        var ordinary = modifier.applyModifier(holder, recipe);
        assertNotNull(ordinary); assertEquals(5, ordinary.duration); assertEquals(32, ordinary.getInputEUt().getTotalEU());
        holder.selectedType = org.gtlcore.gtlcore.common.data.GTLRecipeTypes.DIMENSIONALLY_TRANSCENDENT_MIXER_RECIPES.get();
        assertEquals(25, modifier.applyModifier(holder, recipe).duration);
        assertEquals(25, recipe.duration);
    }

    @Test void blastFurnaceDiscountIsAppliedAfterSelectingTheOverclockTier() {
        var holder = new CoilHolder();
        var recipe = GTRecipeTypes.BLAST_RECIPES.recipeBuilder("native_ebf_discount_boundary").duration(16).EUt(33).blastFurnaceTemp(900).build();
        var result = new RecipeModifierList(com.gregtechceu.gtceu.common.data.GTRecipeModifiers::ebfOverclock).applyModifier(holder, recipe);
        assertNotNull(result); assertEquals(1, result.ocLevel); assertEquals(2, result.duration);
        assertEquals(125, result.getInputEUt().getTotalEU()); assertEquals(1, IGTRecipe.of(result).getRealParallels());
        assertEquals(33, recipe.getInputEUt().getTotalEU()); assertEquals(16, recipe.duration);
        assertEquals(.95, RecipeMultiplierTracker.get(holder).orElseThrow().energy());
        assertEquals(.5, RecipeMultiplierTracker.get(holder).orElseThrow().duration());
    }

    @Test void blastFurnaceRejectsInsufficientTemperatureBeforeChangingTheSource() {
        var holder = new CoilHolder();
        var recipe = GTRecipeTypes.BLAST_RECIPES.recipeBuilder("native_ebf_temperature").duration(16).EUt(32).blastFurnaceTemp(3000).build();
        var function = com.gregtechceu.gtceu.common.data.GTRecipeModifiers.ebfOverclock(holder, recipe);
        assertNull(function.apply(recipe)); assertEquals(RecipeResult.FAIL_NO_ENOUGH_TEMPERATURE.reason(), function.getFailReason());
        assertEquals(3000, recipe.data.getInt("ebf_temp")); assertEquals(16, recipe.duration);
    }

    @Test void dissolvingTankRejectsUnequalRatiosThatIntegerDivisionWouldTruncateToTheSameValue() {
        var holder = new CoreHolder(); var first = fluids(holder, IO.IN, 1000, 550); var second = fluids(holder, IO.IN, 1000, 0);
        second.getStorages()[0].setFluid(new net.neoforged.neoforge.fluids.FluidStack(Fluids.LAVA, 850));
        holder.inputs.add(new RecipeHandlePart(List.of(first, second)));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_exact_dissolving_ratio")
                .inputFluids(SizedFluidIngredient.of(Fluids.WATER, 100)).inputFluids(SizedFluidIngredient.of(Fluids.LAVA, 150))
                .outputFluids(SizedFluidIngredient.of(Fluids.WATER, 10)).duration(100).EUt(32).build();
        var result = org.gtlcore.gtlcore.common.data.GTLRecipeModifiers.dissolvingTankOverclock(holder, recipe).apply(recipe);
        assertNotNull(result); assertTrue(result.outputs.isEmpty()); assertFalse(recipe.outputs.isEmpty());
        assertEquals(550, first.getStorages()[0].getFluidAmount()); assertEquals(850, second.getStorages()[0].getFluidAmount());
    }

    @Test void nativeMachineReductionUsesEnergyStackAndKeepsTheOriginalRecipeAndRuntimeCounts() {
        var holder = new CoreHolder();
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_machine_reduction").duration(100).EUt(32).build();
        IGTRecipe.of(recipe).setRealParallels(3_000_000_001L);
        var result = org.gtlcore.gtlcore.common.data.GTLRecipeModifiers.reduction(holder, recipe, .8, .6);
        assertNotNull(result); assertEquals(25, result.getInputEUt().getTotalEU()); assertEquals(60, result.duration);
        assertEquals(3_000_000_001L, IGTRecipe.of(result).getRealParallels());
        assertEquals(32, recipe.getInputEUt().getTotalEU()); assertEquals(100, recipe.duration);
    }

}
