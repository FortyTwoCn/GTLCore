package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.ISteamMachine;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.steam.SteamEnergyRecipeHandler;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.SteamParallelMultiblockMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import java.util.ArrayList;
import java.util.List;
import static org.gtlcore.gtlcore.migration.NativeCoreRecipeRunnerIntegrationTest.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeSteamIntegrationTest {
    private static final java.util.Map<net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid>, List<net.minecraft.core.Holder<net.minecraft.world.level.material.Fluid>>> TAGS = new java.util.HashMap<>();
    @BeforeAll static void bindSteamTagForTheUnitTestRegistry() {
        var registry = net.minecraft.core.registries.BuiltInRegistries.FLUID;
        registry.getTags().forEach(pair -> TAGS.put(pair.getFirst(), pair.getSecond().stream().toList()));
        var tags = new java.util.HashMap<>(TAGS);
        tags.put(GTMaterials.Steam.getFluidTag(), List.of(GTMaterials.Steam.getFluid().builtInRegistryHolder()));
        registry.bindTags(tags);
    }
    @AfterAll static void restoreTags() { net.minecraft.core.registries.BuiltInRegistries.FLUID.bindTags(TAGS); }
    private static NotifiableFluidTank steam(int tanks, int amount) {
        var tank = new NativeHolder().attachTrait(new NotifiableFluidTank(tanks, Integer.MAX_VALUE, IO.IN, IO.IN));
        for (var storage : tank.getStorages()) storage.setFluid(GTMaterials.Steam.getFluid(amount));
        return tank;
    }
    private static ArrayList<EnergyStack> request(long eu) { return new ArrayList<>(List.of(new EnergyStack(eu))); }
    private static long amount(NotifiableFluidTank tank) { long total = 0; for (var storage : tank.getStorages()) total += storage.getFluidAmount(); return total; }

    @Test void nativeSteamBudgetAndPartialConsumptionUseMillibucketsPerEUWithoutChangingSimulatedStorage() {
        var tank = steam(1, 16); var handler = new SteamEnergyRecipeHandler(tank, 2);
        assertEquals(8L, handler.getContents().getFirst()); assertEquals(8, handler.getTotalContentAmount());
        assertEquals(2, handler.handleRecipeInner(IO.IN, null, request(10), true).getFirst().getTotalEU()); assertEquals(16, amount(tank));
        assertEquals(2, handler.handleRecipeInner(IO.IN, null, request(10), false).getFirst().getTotalEU()); assertEquals(0, amount(tank));
    }
    @Test void nativeSteamRequestAboveIntegerMaxConsumesTheCompleteLongQuantity() {
        var tank = steam(4, Integer.MAX_VALUE); var handler = new SteamEnergyRecipeHandler(tank, 2);
        assertTrue(handler.handleRecipeInner(IO.IN, null, request(3_000_000_000L), true).isEmpty());
        assertEquals(8_589_934_588L, amount(tank));
        assertTrue(handler.handleRecipeInner(IO.IN, null, request(3_000_000_000L), false).isEmpty());
        assertEquals(2_589_934_588L, amount(tank)); assertEquals(1_294_967_294L, handler.getContents().getFirst());
    }
    @Test void fractionalSteamConversionRoundsRequirementsUpAndUsableEnergyDown() {
        var tank = steam(1, 12); var handler = new SteamEnergyRecipeHandler(tank, 2.5);
        assertEquals(4L, handler.getContents().getFirst());
        assertTrue(handler.handleRecipeInner(IO.IN, null, request(3), false).isEmpty()); assertEquals(4, amount(tank));
        assertEquals(1L, handler.getContents().getFirst());
    }
    @Test void unusableFluidNeverContributesToTheSteamBudget() {
        var tank = fluids(new NativeHolder(), IO.IN, 1000, 900); var handler = new SteamEnergyRecipeHandler(tank, 2);
        assertEquals(0L, handler.getContents().getFirst());
        assertEquals(1, handler.handleRecipeInner(IO.IN, null, request(1), false).getFirst().getTotalEU()); assertEquals(900, amount(tank));
    }
    @Test void unrepresentableSteamRequirementDoesNotBecomeAnIntegerSizedSuccessfulRequest() {
        var tank = steam(2, Integer.MAX_VALUE); var handler = new SteamEnergyRecipeHandler(tank, 2);
        assertEquals(Long.MAX_VALUE, handler.handleRecipeInner(IO.IN, null, request(Long.MAX_VALUE), false).getFirst().getTotalEU());
        assertEquals(4_294_967_294L, amount(tank));
    }
    @Test void actualSteamOvenDefinitionRetains256LimitAndTheNativeTwoMillibucketConversion() {
        var oven = GTMultiMachines.STEAM_OVEN;
        var machine = new SteamParallelMultiblockMachine(new BlockEntityCreationInfo(oven.getBlockEntityType(), BlockPos.ZERO, oven.getBlock().defaultBlockState()));
        assertEquals(256, machine.getMaxParallels()); assertEquals(2, ((ISteamMachine) (Object) machine).getSteamPerEU());
    }
    private static final class OvenHolder extends CoreHolder {
        @Override public MultiblockMachineDefinition getDefinition() { return GTMultiMachines.STEAM_OVEN.get(); }
        @Override public long getOverclockVoltage() { return Long.MAX_VALUE; }
    }
    @Test void nativeOvenModifierCapsSelectedInputsAt256AndHalvesDurationWithoutMutatingTheSource() {
        var holder = new OvenHolder(); var input = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 600));
        holder.inputs.add(new org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart(List.of(input)));
        holder.outputs.add(new org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart(List.of(fluids(holder, IO.OUT, 100_000, 0))));
        var recipe = GTRecipeTypes.FURNACE_RECIPES.recipeBuilder("native_oven_parallel").inputItems(Items.IRON_INGOT)
                .outputFluids(SizedFluidIngredient.of(Fluids.WATER, 100)).duration(100).EUt(4).build();
        var result = SteamParallelMultiblockMachine.recipeModifier(holder, recipe).apply(recipe);
        assertNotNull(result); assertEquals(256, IGTRecipe.of(result).getRealParallels()); assertEquals(50, result.duration);
        assertEquals(1024, result.getInputEUt().getTotalEU()); assertEquals(100, recipe.duration); assertEquals(600, input.getStackInSlot(0).getCount());
    }
    @Test void nativeOvenModifierRejectsAnUnfulfillableInputInsteadOfDereferencingANullRecipe() {
        var holder = new OvenHolder(); holder.inputs.add(new org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart(List.of(items(holder, IO.IN, ItemStack.EMPTY))));
        var recipe = GTRecipeTypes.FURNACE_RECIPES.recipeBuilder("native_oven_missing_input").inputItems(Items.IRON_INGOT).duration(100).EUt(4).build();
        assertNull(SteamParallelMultiblockMachine.recipeModifier(holder, recipe).apply(recipe));
    }
    @Test void hugeSteamHatchUsesAnExact125EUBudgetWithoutConsumingDuringPreview() {
        var tank = steam(4, Integer.MAX_VALUE);
        var handler = new org.gtlcore.gtlcore.api.machine.trait.ExactSteamEnergyRecipeHandler(tank,
                () -> new org.gtlcore.gtlcore.api.machine.trait.SteamEnergyConversion.Rate(
                        java.math.BigInteger.ONE, java.math.BigInteger.valueOf(125)));
        assertEquals(1_073_741_823_500L, handler.getContents().getFirst());
        assertTrue(handler.handleRecipeInner(IO.IN, null, request(3_000_000_001L), true).isEmpty());
        assertEquals(8_589_934_588L, amount(tank));
        assertTrue(handler.handleRecipeInner(IO.IN, null, request(3_000_000_001L), false).isEmpty());
        assertEquals(8_565_934_587L, amount(tank));
    }
    @Test void hugeSteamPartialAndRoundedRequestsUseTheSameRateAsTheAvailableBudget() {
        var tank = steam(1, 1);
        var handler = new org.gtlcore.gtlcore.api.machine.trait.ExactSteamEnergyRecipeHandler(tank,
                () -> new org.gtlcore.gtlcore.api.machine.trait.SteamEnergyConversion.Rate(
                        java.math.BigInteger.ONE, java.math.BigInteger.valueOf(125)));
        assertEquals(125L, handler.getContents().getFirst());
        assertEquals(5L, handler.handleRecipeInner(IO.IN, null, request(130), true).getFirst().getTotalEU());
        assertEquals(1L, amount(tank));
        assertEquals(5L, handler.handleRecipeInner(IO.IN, null, request(130), false).getFirst().getTotalEU());
        assertEquals(0L, amount(tank));
        tank.getStorages()[0].setFluid(GTMaterials.Steam.getFluid(2));
        assertTrue(handler.handleRecipeInner(IO.IN, null, request(126), false).isEmpty());
        assertEquals(0L, amount(tank));
    }
    @Test void changingSteamHatchModeChangesPreviewAndExecutionTogether() {
        var tank = steam(1, 10);
        var huge = new java.util.concurrent.atomic.AtomicBoolean(false);
        var handler = new org.gtlcore.gtlcore.api.machine.trait.ExactSteamEnergyRecipeHandler(tank,
                () -> huge.get() ? new org.gtlcore.gtlcore.api.machine.trait.SteamEnergyConversion.Rate(
                        java.math.BigInteger.ONE, java.math.BigInteger.valueOf(125)) :
                        org.gtlcore.gtlcore.api.machine.trait.SteamEnergyConversion.Rate.overclocked(0));
        assertEquals(5L, handler.getContents().getFirst());
        huge.set(true);
        assertEquals(1250L, handler.getContents().getFirst());
        assertTrue(handler.handleRecipeInner(IO.IN, null, request(251), false).isEmpty());
        assertEquals(7L, amount(tank));
        huge.set(false);
        assertEquals(3L, handler.getContents().getFirst());
        assertTrue(handler.handleRecipeInner(IO.IN, null, request(3), false).isEmpty());
        assertEquals(1L, amount(tank));
    }
}
