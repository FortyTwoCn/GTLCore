package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.ingredient.CacheHashStrategies;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.*;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Cache reuse must preserve native predicate semantics, including unbound tag IDs and component order. */
class NativeIngredientCacheIntegrationTest {
    private static final CacheHashStrategies.IngredientHashStrategy ITEMS = CacheHashStrategies.IngredientHashStrategy.INSTANCE;
    private static final CacheHashStrategies.FluidIngredientHashStrategy FLUIDS = CacheHashStrategies.FluidIngredientHashStrategy.INSTANCE;

    @Test
    void reorderedVanillaAlternativesUseTheSameHashMapEntry() {
        var first = Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT);
        var second = Ingredient.of(Items.GOLD_INGOT, Items.IRON_INGOT);
        assertTrue(ITEMS.equals(first, second));
        assertEquals(ITEMS.hashCode(first), ITEMS.hashCode(second));
        var cache = new Object2IntOpenCustomHashMap<Ingredient>(ITEMS);
        cache.put(first, 37);
        assertEquals(37, cache.getInt(second));
        assertFalse(ITEMS.equals(first, Ingredient.of(Items.DIAMOND)));
    }

    @Test
    void unboundTagsRemainDistinctEvenWhenBothHaveNoMatches() {
        var first = Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.parse("gtlcore_recipe_test:first")));
        var second = Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.parse("gtlcore_recipe_test:second")));
        var copy = Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.parse("gtlcore_recipe_test:first")));
        assertFalse(first.test(new ItemStack(Items.IRON_INGOT)));
        assertFalse(second.test(new ItemStack(Items.IRON_INGOT)));
        assertFalse(ITEMS.equals(first, second));
        assertTrue(ITEMS.equals(first, copy));
        assertEquals(ITEMS.hashCode(first), ITEMS.hashCode(copy));
    }

    @Test
    void unorderedCompoundAndIntersectionChildrenAreNormalizedRecursively() {
        var iron = Ingredient.of(Items.IRON_INGOT);
        var gold = Ingredient.of(Items.GOLD_INGOT);
        var both = Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT);
        assertTrue(ITEMS.equals(CompoundIngredient.of(iron, gold), CompoundIngredient.of(gold, iron)));
        var first = IntersectionIngredient.of(CompoundIngredient.of(iron, gold), both);
        var second = IntersectionIngredient.of(both, CompoundIngredient.of(gold, iron));
        assertTrue(ITEMS.equals(first, second));
        assertEquals(ITEMS.hashCode(first), ITEMS.hashCode(second));
        assertFalse(ITEMS.equals(DifferenceIngredient.of(both, iron), DifferenceIngredient.of(iron, both)));
    }

    @Test
    void componentStrictnessAndLoreOrderRemainPartOfThePredicateIdentity() {
        var stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("catalyst"));
        var strict = DataComponentIngredient.of(true, stack);
        var partial = DataComponentIngredient.of(false, DataComponents.CUSTOM_NAME, Component.literal("catalyst"), Items.IRON_INGOT);
        assertFalse(ITEMS.equals(strict, partial));
        var extra = stack.copy(); extra.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("extra"))));
        assertFalse(strict.test(extra));
        assertTrue(partial.test(extra));
        var first = stack.copy(); first.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("a"), Component.literal("b"))));
        var second = stack.copy(); second.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("b"), Component.literal("a"))));
        assertFalse(ITEMS.equals(DataComponentIngredient.of(true, first), DataComponentIngredient.of(true, second)));
        assertFalse(ITEMS.equals(strict, Ingredient.of(Items.IRON_INGOT)));
    }

    @Test
    void longAndRangedQuantityWrappersShareOnlyTheirMatchingPredicate() {
        var predicate = Ingredient.of(Items.IRON_INGOT);
        var longSized = LongIngredient.create(predicate, 9_007_199_254_740_993L);
        var ranged = IntProviderIngredient.of(Ingredient.of(Items.IRON_INGOT), UniformInt.of(1, 64)).toVanilla();
        assertTrue(ITEMS.equals(predicate, longSized.ingredient()));
        assertTrue(ITEMS.equals(predicate, ranged));
        assertEquals(ITEMS.hashCode(predicate), ITEMS.hashCode(longSized.ingredient()));
        var small = SizedIngredient.of(Items.IRON_INGOT, 4);
        assertTrue(CacheHashStrategies.SizedIngredientHashStrategy.INSTANCE.equals(longSized, small));
    }

    @Test
    void fluidCacheUsesTagIdsAndUnorderedAlternativesWithoutLosingComponents() {
        var first = CompoundFluidIngredient.of(FluidIngredient.single(Fluids.WATER), FluidIngredient.single(Fluids.LAVA));
        var second = CompoundFluidIngredient.of(FluidIngredient.single(Fluids.LAVA), FluidIngredient.single(Fluids.WATER));
        assertTrue(FLUIDS.equals(first, second));
        assertEquals(FLUIDS.hashCode(first), FLUIDS.hashCode(second));
        var tagOne = FluidIngredient.tag(TagKey.create(Registries.FLUID, ResourceLocation.parse("gtlcore_recipe_test:first")));
        var tagTwo = FluidIngredient.tag(TagKey.create(Registries.FLUID, ResourceLocation.parse("gtlcore_recipe_test:second")));
        assertFalse(FLUIDS.equals(tagOne, tagTwo));
        var stack = new FluidStack(Fluids.WATER, 1); stack.set(DataComponents.CUSTOM_NAME, Component.literal("purified"));
        var named = DataComponentFluidIngredient.of(true, stack);
        assertFalse(FLUIDS.equals(named, FluidIngredient.single(Fluids.WATER)));
        assertFalse(FLUIDS.equals(named, DataComponentFluidIngredient.of(false, stack)));
        assertTrue(CacheHashStrategies.SizedFluidIngredientHashStrategy.INSTANCE.equals(
                LongFluidIngredient.create(named, 3_000_000_001L), new SizedFluidIngredient(named, 1)));
    }

    @Test
    void ingredientComparatorRespectsEqualityAndIsAntisymmetric() {
        var first = Ingredient.of(Items.IRON_INGOT);
        var second = Ingredient.of(Items.GOLD_INGOT);
        var comparator = CacheHashStrategies.IngredientEquality.INGREDIENT_COMPARATOR;
        assertEquals(0, comparator.compare(first, LongIngredient.create(first, 3_000_000_001L).ingredient()));
        assertEquals(-Integer.signum(comparator.compare(second, first)), Integer.signum(comparator.compare(first, second)));
        assertTrue(comparator.compare(null, first) < 0);
    }
}
