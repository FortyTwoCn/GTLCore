package org.gtlcore.gtlcore.migration;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtladd.gtladditions.api.recipe.ChanceParallelLogic;
import com.gtladd.gtladditions.utils.RecipeCalculationHelper;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;
import org.gtlcore.gtlcore.api.recipe.*;
import org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic;
import org.gtlcore.gtlcore.api.recipe.ingredient.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.gtlcore.gtlcore.migration.NativeCoreRecipeRunnerIntegrationTest.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeAddChanceInputTest {
    private static Content item(long amount, int chance, int max) {
        return new Content(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), amount), chance, max);
    }
    private static Object2IntMap<Object> cache(Content content, int initial) {
        var values = new Object2IntOpenHashMap<Object>(); values.put(content.content(), initial); return values;
    }
    private static long itemCount(List<Content> rolled) {
        return rolled.stream().mapToLong(c -> LongIngredient.amount(ItemRecipeCapability.CAP.of(c.content()))).sum();
    }

    @Test void overlappingInputsAndCatalystsShareOneReadOnlyNativeInventoryBudget() {
        var stock = new Object2LongOpenCustomHashMap<ItemStack>(ItemStackHashStrategy.comparingAllButCount());
        stock.put(new ItemStack(Items.IRON_INGOT), 14L);
        var inputs = List.of(item(3, 10000, 10000), item(2, 0, 10000), item(1, 10000, 10000));
        assertEquals(3, ChanceParallelLogic.limitItemInputs(inputs, stock, Long.MAX_VALUE, null, 1, 1, 1));
        assertEquals(14, stock.getLong(new ItemStack(Items.IRON_INGOT)));
        assertEquals(0, ChanceParallelLogic.limitItemInputs(inputs, stock, 0, null, 1, 1, 1));
    }

    @Test void nativeCachedChanceBoundsFitTheActualRollAndNeverAdvanceRngOrCache() {
        var content = item(3, 2500, 10000); var values = cache(content, 7500);
        var stock = new Object2LongOpenCustomHashMap<ItemStack>(ItemStackHashStrategy.comparingAllButCount());
        stock.put(new ItemStack(Items.IRON_INGOT), 7L);
        GTValues.RNG.setSeed(37219L);
        var parallels = ChanceParallelLogic.limitItemInputs(List.of(content), stock, 32, values, 1, 1, 1);
        assertEquals(8, parallels); assertEquals(7500, values.getInt(content.content()));
        assertEquals(new Random(37219L).nextLong(), GTValues.RNG.nextLong());
        assertEquals(6, itemCount(LongChanceLogic.OR.roll(ItemRecipeCapability.CAP, List.of(content), cache(content, 7500), parallels)));
        assertEquals(9, itemCount(LongChanceLogic.OR.roll(ItemRecipeCapability.CAP, List.of(content), cache(content, 7500), parallels + 1)));
    }

    @Test void longFluidChanceBoundMatchesTheNativeRollAboveDoubleIntegerPrecision() {
        var content = new Content(LongFluidIngredient.create(Fluids.WATER, 3), 3333, 10000);
        var values = cache(content, 9999); var stock = LongFluidParallelLogic.newFluidAmountMap();
        long available = 9_007_199_254_740_993L;
        stock.put(new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 1), available);
        long parallel = ChanceParallelLogic.limitFluidInputs(List.of(content), stock, Long.MAX_VALUE, values, 1, 1, 1);
        long accepted = LongChanceLogic.OR.roll(FluidRecipeCapability.CAP, List.of(content), cache(content, 9999), parallel)
                .stream().mapToLong(c -> LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(c.content()))).sum();
        long rejected = LongChanceLogic.OR.roll(FluidRecipeCapability.CAP, List.of(content), cache(content, 9999), parallel + 1)
                .stream().mapToLong(c -> LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(c.content()))).sum();
        assertTrue(accepted <= available); assertTrue(rejected > available);
        assertEquals(9999, values.getInt(content.content()));
    }

    @Test void nativeRecipeCopyRollsEightTimesWithoutMultiplyingItsChanceIngredientTwice() {
        var original = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_matrix_chance_input").duration(20).build();
        var content = item(3, 2500, 10000);
        original.inputs.put(ItemRecipeCapability.CAP, new ArrayList<>(List.of(content)));
        var copied = RecipeCalculationHelper.INSTANCE.copyFixRecipe(original, IAdvancedContentModifier.preciseMultiplier(8), 10);
        IGTRecipe.of(copied).setRealParallels(8);
        var copiedContent = copied.getInputContents(ItemRecipeCapability.CAP).getFirst();
        assertEquals(3, LongIngredient.amount(ItemRecipeCapability.CAP.of(copiedContent.content())));
        var holder = new CoreHolder(); var inventory = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 7));
        holder.inputs.add(new RecipeHandlePart(List.of(inventory)));
        var values = cache(copiedContent, 7500);
        var result = RecipeRunnerHelper.handleRecipe(IO.IN, holder, copied.inputs,
                Map.of(ItemRecipeCapability.CAP, values), false, copied, false);
        assertTrue(result.isSuccess()); assertEquals(1, inventory.getStackInSlot(0).getCount());
        assertEquals(3, LongIngredient.amount(ItemRecipeCapability.CAP.of(content.content())));
    }
}
