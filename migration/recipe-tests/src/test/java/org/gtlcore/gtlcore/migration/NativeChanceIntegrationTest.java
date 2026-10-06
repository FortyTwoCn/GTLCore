package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.*;
import org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.RecipeRunner;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Strict production mixins and actual native recipe runner / GT tank / item inventory. */
class NativeChanceIntegrationTest {
    private static Content item(long amount, int chance, int max) {
        return new Content(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), amount), chance, max);
    }
    private static Object2IntMap<Object> cache(Content content, int initial) {
        var cache = new Object2IntOpenHashMap<Object>(); cache.put(content.content(), initial); return cache;
    }
    private static long itemCount(List<Content> contents) {
        long sum = 0;
        for (Content c : contents) sum = Math.addExact(sum, LongIngredient.amount(ItemRecipeCapability.CAP.of(c.content())));
        return sum;
    }
    @Test void originalNativeOrAvoidsOverflowAtAMillionRunsAndReturnsWholeOutputs() {
        var content = item(1, 5000, 10000);
        var result = ChanceLogic.OR.value().roll(ItemRecipeCapability.CAP, List.of(content), cache(content, 0), 1_000_000);
        assertEquals(500_000L, itemCount(result));
        assertEquals(1, LongIngredient.amount(ItemRecipeCapability.CAP.of(content.content())));
    }
    @Test void runCountsAndMultiplicationAboveDoubleIntegerPrecisionRemainExact() {
        long runs = 9_007_199_254_740_993L;
        var content = item(3, 3333, 10000);
        var result = LongChanceLogic.OR.roll(ItemRecipeCapability.CAP, List.of(content), cache(content, 0), runs);
        long expected = BigInteger.valueOf(runs).multiply(BigInteger.valueOf(3333)).divide(BigInteger.valueOf(10000))
                .multiply(BigInteger.valueOf(3)).longValueExact();
        assertEquals(expected, itemCount(result));
        assertEquals(3, LongIngredient.amount(ItemRecipeCapability.CAP.of(content.content())));
    }
    @Test void fluidOutputsKeepTheirLongQuantityAfterTheChanceRoll() {
        var content = new Content(LongFluidIngredient.create(Fluids.WATER, 1000), 5000, 10000);
        var result = LongChanceLogic.OR.roll(FluidRecipeCapability.CAP, List.of(content), cache(content, 0), 3_000_000_002L);
        assertEquals(1_500_000_001_000L, LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(result.getFirst().content())));
    }
    @Test void chanceRemaindersAndCachedBonusesDoNotOverflowAnIntDenominator() {
        var content = item(1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
        var chanceCache = cache(content, Integer.MAX_VALUE - 1);
        var portions = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(Integer.MAX_VALUE - 1L))
                .divideAndRemainder(BigInteger.valueOf(Integer.MAX_VALUE));
        long expected = portions[0].longValueExact() +
                (portions[1].longValueExact() + Integer.MAX_VALUE - 1L) / Integer.MAX_VALUE;
        var result = LongChanceLogic.OR.roll(ItemRecipeCapability.CAP, List.of(content), chanceCache, Long.MAX_VALUE);
        assertEquals(expected, itemCount(result));
        assertTrue(chanceCache.getInt(content.content()) < Integer.MAX_VALUE);
    }
    @Test void quantityOverflowSaturatesAndChanceAboveOneCanProduceMultipleOutputsPerRun() {
        var content = item(Long.MAX_VALUE, 12500, 10000);
        var result = LongChanceLogic.OR.roll(ItemRecipeCapability.CAP, List.of(content), cache(content, 0), 4);
        assertEquals(Long.MAX_VALUE, LongIngredient.amount(ItemRecipeCapability.CAP.of(result.getFirst().content())));
        assertSame(LongChanceLogic.OR, GTRegistries.CHANCE_LOGICS.get(ResourceLocation.parse("gtceu:long_or")));
    }
    @Test void deterministicCacheKeepsTheNativeSequenceAndZeroRollsDoNotChangeIt() {
        var content = item(1, 5000, 10000); var chanceCache = cache(content, 0);
        long[] expected = {0, 0, 1, 0, 1};
        for (long count : expected) assertEquals(count, itemCount(LongChanceLogic.OR.roll(ItemRecipeCapability.CAP, List.of(content), chanceCache, 1L)));
        assertEquals(2500, chanceCache.getInt(content.content()));
        assertTrue(LongChanceLogic.OR.roll(ItemRecipeCapability.CAP, List.of(content), chanceCache, 0L).isEmpty());
        assertEquals(2500, chanceCache.getInt(content.content()));
        assertThrows(IllegalArgumentException.class, () -> LongChanceLogic.OR.roll(ItemRecipeCapability.CAP, List.of(content), chanceCache, -1L));
    }
    @Test void positiveAndNegativeBoostsKeepTheUlvTierRuleAndPreventNegativeProduction() {
        var content = NativeContentState.create(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 1), 5000, 10000, 1000, null, null);
        assertEquals(5000, NativeContentState.boostedChance(content, 0, 1));
        assertEquals(6000, NativeContentState.boostedChance(content, 0, 2));
        assertEquals(8000, NativeContentState.boostedChance(content, 1, 4));
        NativeContentState.of(content).gtlcore$setTierChanceBoost(-2000);
        assertEquals(-1000, NativeContentState.boostedChance(content, 1, 4));
        var display = (IRecipeChanceDisplay) (Object) content;
        display.gtlcore$setChanceDisplayTiers(1, 4);
        assertEquals(-1000, display.gtlcore$getUnclampedDisplayChance());
        assertEquals(0, display.gtlcore$getDisplayChance()); assertEquals(3, display.gtlcore$getChanceBoostTierCount());
        assertTrue(LongChanceLogic.OR.roll(ItemRecipeCapability.CAP, List.of(content), cache(content, 0), 100L, 1, 4).isEmpty());
    }
    private static StorageMachine owner() {
        var d = GTMultiMachines.VACUUM_FREEZER;
        return new StorageMachine(new BlockEntityCreationInfo(d.getBlockEntityType(), BlockPos.ZERO, d.getBlock().defaultBlockState()), 64);
    }
    private static class Holder extends StorageMachine implements IOverclockMachine {
        Holder() { super(creationInfo(), 64); }
        private static BlockEntityCreationInfo creationInfo() {
            var definition = GTMultiMachines.VACUUM_FREEZER;
            return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO, definition.getBlock().defaultBlockState());
        }
        final Map<IO, List<RecipeHandlerList>> proxies = new EnumMap<>(IO.class);
        final Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> flat = new EnumMap<>(IO.class);
        @Override public Map<IO,List<RecipeHandlerList>> getCapabilitiesProxy() { return proxies; }
        @Override public Map<IO,Map<RecipeCapability<?>,List<IRecipeHandler<?>>>> getCapabilitiesFlat() { return flat; }
        @Override public int getOverclockTier() { return 1; }
        @Override public void setOverclockTier(int tier) {}
        @Override public int getMinOverclockTier() { return 1; }
        @Override public int getMaxOverclockTier() { return 1; }
    }
    @Test void actualNativeRunnerFillsATankFromThreeBillionRunsAndSimulationDoesNotRoll() {
        var holder = new Holder();
        var tank = owner().attachTrait(new NotifiableFluidTank(1, Integer.MAX_VALUE, IO.OUT, IO.OUT));
        holder.addHandlerList(RecipeHandlerList.of(IO.OUT, tank));
        var recipe = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("long_chance_tank")
                .chancedOutput(SizedFluidIngredient.of(Fluids.WATER, 1), 5000).duration(1).EUt(32).build();
        IGTRecipe.of(recipe).setRealParallels(3_000_000_002L);
        var content = recipe.outputs.get(FluidRecipeCapability.CAP).getFirst();
        var chanceCache = cache(content, 0);
        Map<RecipeCapability<?>, Object2IntMap<?>> caches = Map.of(FluidRecipeCapability.CAP, chanceCache);
        assertTrue(new RecipeRunner(recipe, IO.OUT, false, holder, caches, true).handle(recipe.outputs).isSuccess());
        assertEquals(0, tank.getStorages()[0].getFluidAmount()); assertEquals(0, chanceCache.getInt(content.content()));
        assertTrue(new RecipeRunner(recipe, IO.OUT, false, holder, caches, false).handle(recipe.outputs).isSuccess());
        assertEquals(1_500_000_001, tank.getStorages()[0].getFluidAmount());
    }
    @Test void nativeRunnerKeepsNonConsumedInputsAndNoneLogicWithLongRuns() {
        var holder = new Holder();
        var inventory = owner().attachTrait(new NotifiableItemStackHandler(1, IO.IN, IO.IN));
        inventory.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 7));
        holder.addHandlerList(RecipeHandlerList.of(IO.IN, inventory));
        var recipe = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("nc_long_chance").notConsumable(Items.IRON_INGOT).EUt(32).build();
        IGTRecipe.of(recipe).setRealParallels(3_000_000_002L);
        assertTrue(new RecipeRunner(recipe, IO.IN, false, holder, Map.of(), false).handle(recipe.inputs).isSuccess());
        assertEquals(7, inventory.storage.getStackInSlot(0).getCount());
        recipe = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("none_long_chance")
                .chancedOutput(new FluidStack(Fluids.WATER, 1), 5000).EUt(32).build();
        recipe.outputChanceLogics.put(FluidRecipeCapability.CAP, ChanceLogic.NONE.value());
        IGTRecipe.of(recipe).setRealParallels(3_000_000_002L);
        assertTrue(new RecipeRunner(recipe, IO.OUT, false, holder, Map.of(), false).handle(recipe.outputs).isSuccess());
    }
}
