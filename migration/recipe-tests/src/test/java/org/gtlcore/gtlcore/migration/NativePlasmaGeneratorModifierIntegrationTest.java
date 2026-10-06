package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.common.data.GTLPlasmaGeneratorModifiers;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NativePlasmaGeneratorModifierIntegrationTest {
    private static final class Generator extends WorkableElectricMultiblockMachine {
        final NotifiableFluidTank fluids;
        Generator() {
            super(new BlockEntityCreationInfo(GTMultiMachines.VACUUM_FREEZER.getBlockEntityType(),
                    BlockPos.ZERO, GTMultiMachines.VACUUM_FREEZER.getBlock().defaultBlockState()));
            fluids = attachTrait(new NotifiableFluidTank(3, 1000, IO.IN, IO.IN));
            addHandlerList(RecipeHandlerList.of(IO.IN, 0, fluids));
            var dynamo = attachTrait(com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer
                    .emitterContainer(1_000_000, 512, 2));
            addHandlerList(RecipeHandlerList.of(IO.OUT, 0, dynamo));
            fluids.getStorages()[0].setFluid(GTMaterials.Hydrogen.getFluid(64));
            fluids.getStorages()[1].setFluid(new FluidStack(Fluids.WATER, 2));
            fluids.getStorages()[2].setFluid(new FluidStack(Fluids.LAVA, 3));
        }
        @Override public long getOverclockVoltage() { return 512; }
    }

    private static long amount(com.gregtechceu.gtceu.api.recipe.GTRecipe recipe, net.minecraft.world.level.material.Fluid fluid) {
        return recipe.getInputContents(FluidRecipeCapability.CAP).stream()
                .map(content -> FluidRecipeCapability.CAP.of(content.content()))
                .filter(ingredient -> LongFluidIngredient.matchingIngredient(ingredient).test(new FluidStack(fluid, 1)))
                .mapToLong(LongFluidIngredient::amount).sum();
    }

    @Test void tierSpecificBoostPreviewsNeverDrainAndAttachOnePlasmaAfterParallelScaling() {
        var machine = new Generator();
        var source = GTRecipeTypes.PLASMA_GENERATOR_FUELS.recipeBuilder("native_plasma_preview")
                .inputFluids(GTMaterials.Hydrogen.asSingleFluidIngredient(1)).duration(16).EUt(-32).build();
        var modifier = GTLPlasmaGeneratorModifiers.tiered(Map.of(32L, () -> new FluidStack(Fluids.WATER, 1)));
        for (int i = 0; i < 5; i++) {
            var preview = modifier.applyModifier(machine, source);
            assertNotNull(preview); assertEquals(16, IGTRecipe.of(preview).getRealParallels());
            assertEquals(512, preview.getOutputEUt().getTotalEU()); assertEquals(16, preview.duration);
            assertEquals(16, amount(preview, GTMaterials.Hydrogen.getFluid()));
            assertEquals(1, amount(preview, Fluids.WATER));
            assertEquals(64, machine.fluids.getStorages()[0].getFluidAmount());
            assertEquals(2, machine.fluids.getStorages()[1].getFluidAmount());
        }
        assertEquals(1, source.getInputContents(FluidRecipeCapability.CAP).size());
        assertEquals(32, source.getOutputEUt().getTotalEU()); assertEquals(1, IGTRecipe.of(source).getRealParallels());
    }

    @Test void advancedBoostPreservesBothPlasmasAndFallsBackWhenEachIsAbsent() {
        var machine = new Generator();
        var source = GTRecipeTypes.PLASMA_GENERATOR_FUELS.recipeBuilder("native_advanced_plasma")
                .inputFluids(GTMaterials.Hydrogen.asSingleFluidIngredient(1)).duration(16).EUt(-32).build();
        var modifier = GTLPlasmaGeneratorModifiers.advanced(() -> new FluidStack(Fluids.WATER, 1), () -> new FluidStack(Fluids.LAVA, 1));
        var both = modifier.applyModifier(machine, source);
        assertEquals(16, IGTRecipe.of(both).getRealParallels());
        assertEquals(1, amount(both, Fluids.WATER)); assertEquals(1, amount(both, Fluids.LAVA));
        machine.fluids.getStorages()[2].setFluid(FluidStack.EMPTY);
        var first = modifier.applyModifier(machine, source);
        assertEquals(8, IGTRecipe.of(first).getRealParallels()); assertEquals(1, amount(first, Fluids.WATER)); assertEquals(0, amount(first, Fluids.LAVA));
        machine.fluids.getStorages()[1].setFluid(FluidStack.EMPTY);
        var neither = modifier.applyModifier(machine, source);
        assertEquals(1, IGTRecipe.of(neither).getRealParallels()); assertEquals(0, amount(neither, Fluids.WATER));
        machine.fluids.getStorages()[2].setFluid(new FluidStack(Fluids.LAVA, 3));
        var second = modifier.applyModifier(machine, source);
        assertEquals(16, IGTRecipe.of(second).getRealParallels()); assertEquals(1, amount(second, Fluids.LAVA));
        assertEquals(64, machine.fluids.getStorages()[0].getFluidAmount());
    }

    @Test void unmatchedGeneratorVoltageDoesNotSelectAnotherTiersPlasma() {
        var machine = new Generator();
        var source = GTRecipeTypes.PLASMA_GENERATOR_FUELS.recipeBuilder("native_plasma_voltage")
                .inputFluids(GTMaterials.Hydrogen.asSingleFluidIngredient(1)).duration(16).EUt(-33).build();
        var result = GTLPlasmaGeneratorModifiers.tiered(Map.of(32L, () -> new FluidStack(Fluids.WATER, 1))).applyModifier(machine, source);
        assertEquals(1, IGTRecipe.of(result).getRealParallels()); assertEquals(0, amount(result, Fluids.WATER));
        assertEquals(33, source.getOutputEUt().getTotalEU());
    }
}
