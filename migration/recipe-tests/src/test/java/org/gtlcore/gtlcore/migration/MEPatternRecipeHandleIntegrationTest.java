package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.capability.IMERecipeHandler;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternTrait;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.objects.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

/** Production pattern routing and cache, with bounded slot inventories; this does not emulate an AE grid. */
class MEPatternRecipeHandleIntegrationTest {
    private static final class Stock<S> {
        final S stack; long amount;
        Stock(S stack, long amount) { this.stack = stack; this.amount = amount; }
    }
    static final class Handler<T, S> implements IMERecipeHandler<T, S> {
        final RecipeCapability<T> cap;
        final ToLongFunction<T> amount;
        final BiPredicate<T, S> matches;
        final BiFunction<T, Long, T> resize;
        final Map<Integer, List<Stock<S>>> slots = new TreeMap<>();
        final Object2LongMap<T> prepared = new Object2LongOpenHashMap<>();
        final List<Integer> tried = new ArrayList<>();
        boolean filter;
        long outputSpace = Long.MAX_VALUE;
        Handler(RecipeCapability<T> cap, ToLongFunction<T> amount, BiFunction<T, Long, T> resize, BiPredicate<T, S> matches) {
            this.cap = cap; this.amount = amount; this.resize = resize; this.matches = matches;
        }
        Handler<T, S> stock(int slot, S stack, long amount) {
            slots.computeIfAbsent(slot, $ -> new ArrayList<>()).add(new Stock<>(stack, amount)); return this;
        }
        long stored(int slot) { return slots.get(slot).stream().mapToLong(stock -> stock.amount).sum(); }
        @Override public RecipeCapability<T> getCapability() { return cap; }
        @Override public int[] getActiveSlots() { return slots.keySet().stream().mapToInt(Integer::intValue).toArray(); }
        @Override public Int2ObjectMap<List<S>> getActiveAndUnCachedSlotsLimitContentsMap() {
            var result = new Int2ObjectOpenHashMap<List<S>>();
            slots.forEach((slot, stacks) -> result.put(slot.intValue(), stacks.stream().map(stock -> stock.stack).toList()));
            return result;
        }
        @Override public Object2LongMap<S> getStackMapFromFirstAvailableSlot(IntCollection candidates) {
            var result = new Object2LongOpenHashMap<S>();
            for (int slot : candidates) {
                if (!slots.containsKey(slot)) continue;
                slots.get(slot).forEach(stock -> result.put(stock.stack, stock.amount)); break;
            }
            return result;
        }
        @Override public void prepareMEHandleContents(GTRecipe recipe, List<T> contents, boolean simulate) {
            prepared.clear();
            contents.forEach(content -> prepared.put(content, amount.applyAsLong(content)));
        }
        @Override public Object2LongMap<T> getPreparedMEHandleContents() { return prepared; }
        @Override public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<T> requirements, boolean simulate, int slot) {
            tried.add(slot);
            var stocks = slots.get(slot);
            if (stocks == null) return false;
            long[] remaining = stocks.stream().mapToLong(stock -> stock.amount).toArray();
            for (var requirement : requirements.object2LongEntrySet()) {
                long needed = requirement.getLongValue();
                for (int i = 0; i < stocks.size() && needed > 0; i++) {
                    if (!matches.test(requirement.getKey(), stocks.get(i).stack)) continue;
                    long used = Math.min(needed, remaining[i]); needed -= used; remaining[i] -= used;
                }
                if (needed > 0) return false;
            }
            if (!simulate) for (int i = 0; i < stocks.size(); i++) stocks.get(i).amount = remaining[i];
            return true;
        }
        @Override public boolean outputHasFilter() { return filter; }
        @Override public List<T> meHandleRecipeOutputInner(List<T> contents, boolean simulate) {
            List<T> remaining = new ArrayList<>(); long space = outputSpace;
            for (T content : contents) {
                long count = amount.applyAsLong(content), inserted = Math.min(count, space); space -= inserted;
                if (count > inserted) remaining.add(resize.apply(content, count - inserted));
            }
            if (!simulate) outputSpace = space;
            return remaining;
        }
    }
    static final class Trait implements IMEPatternTrait {
        final Int2ReferenceMap<ObjectSet<GTRecipe>> cache = new Int2ReferenceOpenHashMap<>();
        IntConsumer onChange; int notifications;
        @Override public IO getIO() { return IO.IN; }
        @Override public void notifySelfIO() { notifications++; }
        @Override public ObjectSet<GTRecipe> getCachedGTRecipe() {
            var result = new ObjectOpenHashSet<GTRecipe>(); cache.values().forEach(result::addAll); return result;
        }
        @Override public void setSlotCacheRecipe(int slot, GTRecipe recipe) {
            cache.computeIfAbsent(slot, $ -> new ObjectOpenHashSet<>()).add(recipe);
        }
        @Override public Int2ReferenceMap<ObjectSet<GTRecipe>> getSlot2RecipesCache() { return cache; }
        @Override public void setOnPatternChange(IntConsumer callback) { onChange = callback; }
        @Override public boolean hasCacheInSlot(int slot) { return cache.containsKey(slot); }
    }
    record Fixture(Trait trait, Handler<SizedIngredient, ItemStack> items,
                           Handler<SizedFluidIngredient, FluidStack> fluids, MEPatternRecipeHandlePart part) {}
    static Fixture fixture() {
        var trait = new Trait();
        var items = new Handler<SizedIngredient, ItemStack>(ItemRecipeCapability.CAP, LongIngredient::amount,
                LongIngredient::withAmount, (ingredient, stack) -> LongIngredient.matchingIngredient(ingredient).test(stack));
        var fluids = new Handler<SizedFluidIngredient, FluidStack>(FluidRecipeCapability.CAP, LongFluidIngredient::amount,
                LongFluidIngredient::withAmount, (ingredient, stack) -> LongFluidIngredient.matchingIngredient(ingredient).test(stack));
        return new Fixture(trait, items, fluids, new MEPatternRecipeHandlePart(trait, items, fluids));
    }
    private static GTRecipe recipe(long itemCount, long fluidCount) {
        return GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_pattern_routing")
                .inputItems(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), itemCount))
                .inputFluids(LongFluidIngredient.create(Fluids.WATER, fluidCount)).duration(20).build();
    }
    private static Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> contents(GTRecipe recipe) {
        var result = new Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>>();
        recipe.inputs.forEach((cap, list) -> result.put(cap, list.stream().map(content -> content.content()).toList()));
        return result;
    }
    @Test void aFailedFluidMatchDoesNotConsumeItemsBeforeTryingTheNextSlot() {
        var f = fixture(); var recipe = recipe(2, 3);
        f.items.stock(0, new ItemStack(Items.IRON_INGOT), 5).stock(1, new ItemStack(Items.IRON_INGOT), 5);
        f.fluids.stock(0, new FluidStack(Fluids.WATER, 1), 0).stock(1, new FluidStack(Fluids.WATER, 1), 5);
        assertEquals(1, f.part.handleRecipe(recipe, contents(recipe), false, true));
        assertEquals(5, f.items.stored(0)); assertEquals(3, f.items.stored(1)); assertEquals(2, f.fluids.stored(1));
        assertTrue(f.trait.cache.get(1).contains(recipe));
    }
    @Test void separatePatternSlotsCannotCombineTheirItemAndFluidInputs() {
        var f = fixture(); var recipe = recipe(2, 3);
        f.items.stock(0, new ItemStack(Items.IRON_INGOT), 5).stock(1, new ItemStack(Items.IRON_INGOT), 0);
        f.fluids.stock(0, new FluidStack(Fluids.WATER, 1), 0).stock(1, new FluidStack(Fluids.WATER, 1), 5);
        assertEquals(-1, f.part.handleRecipe(recipe, contents(recipe), false, true));
        assertEquals(5, f.items.stored(0)); assertEquals(5, f.fluids.stored(1)); assertTrue(f.trait.cache.isEmpty());
    }
    @Test void nativeCopiesAndPreparationKeepExactLongQuantitiesAndSimulationDoesNotConsume() {
        long itemCount = 9_007_199_254_740_993L, fluidCount = 3_000_000_003L;
        var f = fixture(); var recipe = recipe(itemCount, fluidCount);
        f.items.stock(0, new ItemStack(Items.IRON_INGOT), itemCount + 1);
        f.fluids.stock(0, new FluidStack(Fluids.WATER, 1), fluidCount + 2);
        assertEquals(0, f.part.handleRecipe(recipe, contents(recipe), true, false));
        assertEquals(itemCount, f.items.prepared.values().iterator().nextLong());
        assertEquals(fluidCount, f.fluids.prepared.values().iterator().nextLong());
        assertEquals(itemCount + 1, f.items.stored(0)); assertEquals(fluidCount + 2, f.fluids.stored(0));
        assertEquals(0, f.part.handleRecipe(recipe, contents(recipe), false, false));
        assertEquals(1, f.items.stored(0)); assertEquals(2, f.fluids.stored(0)); assertTrue(f.trait.cache.isEmpty());
        assertEquals(itemCount, LongIngredient.amount(ItemRecipeCapability.CAP.of(recipe.inputs.get(ItemRecipeCapability.CAP).getFirst().content())));
    }
    @Test void restoredSlotCacheIsPreferredAndPatternChangesInvalidateItsRouting() {
        var f = fixture(); var recipe = recipe(2, 3);
        f.items.stock(0, new ItemStack(Items.IRON_INGOT), 5).stock(1, new ItemStack(Items.IRON_INGOT), 5);
        f.fluids.stock(0, new FluidStack(Fluids.WATER, 1), 5).stock(1, new FluidStack(Fluids.WATER, 1), 5);
        f.trait.setSlotCacheRecipe(1, recipe);
        var restored = new ArrayList<GTRecipe>(); f.part.restoreMachineCache((cached, handle) -> restored.add(cached));
        assertEquals(List.of(recipe), restored);
        assertEquals(1, f.part.handleRecipe(recipe, contents(recipe), true, false));
        assertEquals(1, f.items.tried.getFirst());
        f.trait.cache.remove(1); f.trait.onChange.accept(1); f.items.tried.clear();
        assertEquals(0, f.part.handleRecipe(recipe, contents(recipe), true, false));
        assertEquals(0, f.items.tried.getFirst());
    }
    @Test void aPartialOutputWithTheSameListLengthKeepsTheRemainderAndNotifiesTheTrait() {
        var f = fixture(); f.items.filter = true; f.items.outputSpace = 1_000_000_001L;
        var ingredient = LongIngredient.create(Ingredient.of(Items.DIAMOND), 3_000_000_003L);
        var outputs = new Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>>();
        outputs.put(ItemRecipeCapability.CAP, List.of(ingredient));
        var simulated = f.part.meHandleOutput(new Reference2ObjectOpenHashMap<>(outputs), true);
        assertEquals(2_000_000_002L, LongIngredient.amount(ItemRecipeCapability.CAP.of(simulated.get(ItemRecipeCapability.CAP).getFirst())));
        assertEquals(1_000_000_001L, f.items.outputSpace); assertEquals(0, f.trait.notifications);
        var result = f.part.meHandleOutput(outputs, false);
        assertEquals(2_000_000_002L, LongIngredient.amount(ItemRecipeCapability.CAP.of(result.get(ItemRecipeCapability.CAP).getFirst())));
        assertEquals(0, f.items.outputSpace); assertEquals(1, f.trait.notifications);
        assertEquals(3_000_000_003L, LongIngredient.amount(ingredient));
    }
    @Test void anUnfilteredMeOutputSkipsTheSpaceLimitOnlyDuringSimulation() {
        var f = fixture(); f.items.outputSpace = 0;
        var outputs = new Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>>();
        var ingredient = LongIngredient.create(Ingredient.of(Items.DIAMOND), 3_000_000_003L);
        outputs.put(ItemRecipeCapability.CAP, List.of(ingredient));
        assertTrue(f.part.meHandleOutput(new Reference2ObjectOpenHashMap<>(outputs), true).isEmpty());
        assertEquals(List.of(ingredient), f.part.meHandleOutput(outputs, false).get(ItemRecipeCapability.CAP));
        assertEquals(0, f.trait.notifications);
    }
}
