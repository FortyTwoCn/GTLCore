package org.gtlcore.gtlcore.integration.ae2;

import org.gtlcore.gtlcore.api.recipe.ingredient.CacheHashStrategies;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.config.AE2CalculationMode;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;

import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;


import net.minecraft.nbt.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.inv.ICraftingInventory;
import it.unimi.dsi.fastutil.objects.*;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static appeng.crafting.execution.CraftingCpuHelper.*;

public class AEUtils {

    public static <T extends AEKey> boolean reFunds(Object2LongMap<T> buffer, @Nullable IGrid network, IActionSource source) {
        return AEBufferedOutput.flush(buffer, network, source);
    }

    // ========================================
    // Recipe
    // ========================================

    public static boolean testFluidIngredient(SizedFluidIngredient ingredient, AEFluidKey key) {
        return key != null && LongFluidIngredient.matchingIngredient(ingredient).test(key.toStack(1));
    }

    // ========================================
    // Persist
    // ========================================

    public static <T> ListTag createListTag(Function<T, CompoundTag> keySerializer, Object2LongMap<T> map) {
        ListTag tag = new ListTag();
        for (var it = Object2LongMaps.fastIterator(map); it.hasNext();) {
            var entry = it.next();
            if (entry.getLongValue() <= 0) continue;
            var ct = keySerializer.apply(entry.getKey());
            ct.putLong("real", entry.getLongValue());
            tag.add(ct);
        }
        return tag;
    }

    public static <K> void loadInventory(ListTag tag, Function<CompoundTag, K> keyExtractor, Object2LongMap<K> targetMap) {
        var decoded = decodeInventory(tag, keyExtractor);
        targetMap.clear();
        targetMap.putAll(decoded);
    }

    private static <K> Object2LongMap<K> decodeInventory(ListTag tag, Function<CompoundTag, K> keyExtractor) {
        var decoded = new Object2LongOpenHashMap<K>();
        for (Tag t : tag) {
            if (!(t instanceof CompoundTag ct)) continue;
            K key = keyExtractor.apply(ct);
            long value = ct.getLong("real");
            if (key != null && value > 0) {
                decoded.put(key, Math.addExact(decoded.getLong(key), value));
            }
        }
        return decoded;
    }

    public static <T> ListTag createListTag(Function<T, CompoundTag> keySerializer, ObjectSet<T> map) {
        ListTag tag = new ListTag();
        for (T t : map) {
            var ct = keySerializer.apply(t);
            tag.add(ct);
        }
        return tag;
    }

    public static <K> void loadInventory(ListTag tag, Function<CompoundTag, K> keyExtractor, ObjectSet<K> targetMap) {
        for (Tag t : tag) {
            if (!(t instanceof CompoundTag ct)) continue;
            K key = keyExtractor.apply(ct);
            if (key != null) {
                targetMap.add(key);
            }
        }
    }

    public static <K> void appendPersistedInventory(ListTag tag, Function<CompoundTag, K> keyExtractor,
                                                    Object2LongOpenHashMap<K> targetMap) {
        var decoded = decodeInventory(tag, keyExtractor);
        for (var entry : Object2LongMaps.fastIterable(decoded)) Math.addExact(targetMap.getLong(entry.getKey()), entry.getLongValue());
        for (var entry : Object2LongMaps.fastIterable(decoded)) targetMap.put(entry.getKey(), Math.addExact(targetMap.getLong(entry.getKey()), entry.getLongValue()));
    }

    // ========================================
    // ME IO Machine Utils
    // ========================================

    public static Object2LongMap<SizedIngredient> ingredientsMapWithOutCircuit(List<SizedIngredient> ingredients, Consumer<Integer> consumer) {
        return itemIngredientsMap(ingredients, consumer);
    }

    public static Object2LongMap<SizedIngredient> ingredientsMap(List<SizedIngredient> ingredients) {
        return itemIngredientsMap(ingredients, null);
    }

    private static Object2LongMap<SizedIngredient> itemIngredientsMap(List<SizedIngredient> ingredients, @Nullable Consumer<Integer> circuit) {
        var result = new Object2LongOpenCustomHashMap<>(CacheHashStrategies.SizedIngredientHashStrategy.INSTANCE);
        for (var ingredient : ingredients) {
            var items = LongIngredient.matchingIngredient(ingredient).getItems();
            if (items.length == 0 || items[0].isEmpty()) continue;
            if (circuit != null && IntCircuitBehaviour.isIntegratedCircuit(items[0])) {
                circuit.accept(IntCircuitBehaviour.getCircuitConfiguration(items[0]));
                continue;
            }
            long amount = LongIngredient.amount(ingredient);
            if (amount < 0) throw new IllegalArgumentException("Negative ME item requirement");
            if (amount != 0) result.put(ingredient, Math.addExact(result.getLong(ingredient), amount));
        }
        return result;
    }

    public static Object2LongMap<SizedFluidIngredient> fluidIngredientsMap(List<SizedFluidIngredient> ingredients) {
        var result = new Object2LongOpenCustomHashMap<>(CacheHashStrategies.SizedFluidIngredientHashStrategy.INSTANCE);
        for (var ingredient : ingredients) {
            if (LongFluidIngredient.matchingIngredient(ingredient).hasNoFluids()) continue;
            long amount = LongFluidIngredient.amount(ingredient);
            if (amount < 0) throw new IllegalArgumentException("Negative ME fluid requirement");
            if (amount != 0) result.put(ingredient, Math.addExact(result.getLong(ingredient), amount));
        }
        return result;
    }

    public static Function<ItemStack, Boolean> PROCESS_FILTER = appeng.core.definitions.AEItems.PROCESSING_PATTERN::is;

    public static boolean molecularFilter(ItemStack stack, Level level) {
        return MolecularAssemblerPatternHelper.molecularFilter(stack, level);
    }

    public static Pair<IPatternDetails, @Nullable ObjectSet<Item>> createProcessingFromCraftPattern(IMolecularAssemblerSupportedPattern pattern, Level level) {
        return MolecularAssemblerPatternHelper.createProcessingFromCraftPattern(pattern, level);
    }

    // ========================================
    // ME Processing Pattern Multiply
    // ========================================

    public static AE2CalculationMode getCalculationMode() {
        return ConfigHolder.INSTANCE.ae2CalculationMode;
    }

    public static boolean isIntegratedCircuit(AEKey key) {
        return key instanceof AEItemKey itemKey && IntCircuitBehaviour.isIntegratedCircuit(itemKey.toStack());
    }

    public static void pushInputsToMEPatternBufferInventory(KeyCounter[] inputHolder, IPatternDetails.PatternInputSink inputSink) {
        for (var inputList : inputHolder) {
            for (var input : inputList) {
                inputSink.pushInput(input.getKey(), input.getLongValue());
            }
        }
    }

    public static KeyCounter[] extractForProcessingPattern(IPatternDetails originDetail,
                                                           ICraftingInventory sourceInv,
                                                           KeyCounter expectedOutputs) {
        return extractForProcessingPattern(originDetail, sourceInv, expectedOutputs, 1);
    }

    public static KeyCounter[] extractForProcessingPattern(IPatternDetails originDetail,
                                                           ICraftingInventory sourceInv,
                                                           KeyCounter expectedOutputs,
                                                           long multiplier) {
        if (multiplier <= 0) return null;
        var inputs = originDetail.getInputs();
        var amounts = new long[inputs.length];
        var delta = new KeyCounter();
        try {
            for (int i = 0; i < inputs.length; i++) {
                var choices = inputs[i].getPossibleInputs();
                if (choices.length == 0 || inputs[i].getMultiplier() <= 0 || choices[0].amount() <= 0) return null;
                long runs = isIntegratedCircuit(choices[0].what()) ? 1 : multiplier;
                amounts[i] = Math.multiplyExact(Math.multiplyExact(choices[0].amount(), inputs[i].getMultiplier()), runs);
            }
            for (var output : originDetail.getOutputs()) addExact(delta, output.what(), Math.multiplyExact(output.amount(), multiplier));
            checkMerge(expectedOutputs, delta);
        } catch (ArithmeticException | IllegalArgumentException invalid) { return null; }
        var holders = new KeyCounter[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            var holder = holders[i] = new KeyCounter();
            var key = inputs[i].getPossibleInputs()[0].what();
            long extracted = extractTemplates(sourceInv, key, amounts[i]);
            holder.add(key, extracted);
            if (extracted != amounts[i]) {
                reinjectPatternInputs(sourceInv, holders);
                return null;
            }
        }
        expectedOutputs.addAll(delta);
        return holders;
    }

    private static void addExact(KeyCounter counter, AEKey key, long amount) {
        if (key == null || amount < 0 || counter.get(key) < 0) throw new IllegalArgumentException("Invalid ME quantity");
        counter.set(key, Math.addExact(counter.get(key), amount));
    }

    private static void checkMerge(KeyCounter target, KeyCounter delta) {
        for (var entry : delta) {
            if (target.get(entry.getKey()) < 0) throw new IllegalArgumentException("Negative expected ME output");
            Math.addExact(target.get(entry.getKey()), entry.getLongValue());
        }
    }

    private static long extractTemplates(ICraftingInventory inv, AEKey key, long amount) {
        if (amount == 0) return 0;
        long simEx = inv.extract(key, amount, Actionable.SIMULATE);
        if (simEx == 0) return 0;
        long extracted = inv.extract(key, simEx, Actionable.MODULATE);
        if (extracted == 0 || extracted != simEx) {
            throw new IllegalStateException("Failed to correctly extract whole number. Invalid simulation!");
        }
        return extracted;
    }

    public static KeyCounter[] extractForCraftPattern(IPatternDetails details,
                                                      ICraftingInventory sourceInv,
                                                      Level level,
                                                      KeyCounter expectedOutputs,
                                                      KeyCounter expectedContainerItems) {
        var inputs = details.getInputs();
        var holders = new KeyCounter[inputs.length];
        var outputs = new KeyCounter();
        var containers = new KeyCounter();
        try {
            for (var output : details.getOutputs()) addExact(outputs, output.what(), output.amount());
            checkMerge(expectedOutputs, outputs);
            for (int i = 0; i < inputs.length; i++) {
                var holder = holders[i] = new KeyCounter();
                long remaining = inputs[i].getMultiplier();
                if (remaining <= 0) throw new IllegalArgumentException("Invalid crafting multiplier");
                for (var template : getValidItemTemplates(sourceInv, inputs[i], level)) {
                    if (template.amount() <= 0) throw new IllegalArgumentException("Invalid crafting template");
                    long wanted = Math.multiplyExact(template.amount(), remaining);
                    long available = sourceInv.extract(template.key(), wanted, Actionable.SIMULATE);
                    long runs = available / template.amount();
                    long amount = Math.multiplyExact(template.amount(), runs);
                    if (amount == 0) continue;
                    var remainder = inputs[i].getRemainingKey(template.key());
                    if (remainder != null) {
                        addExact(containers, remainder, runs);
                        checkMerge(expectedContainerItems, containers);
                    }
                    long extracted = sourceInv.extract(template.key(), amount, Actionable.MODULATE);
                    holder.add(template.key(), extracted);
                    if (extracted != amount) throw new IllegalStateException("Crafting simulation changed during extraction");
                    remaining -= runs;
                    if (remaining == 0) break;
                }
                if (remaining != 0) {
                    reinjectPatternInputs(sourceInv, holders);
                    return null;
                }
            }
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            reinjectPatternInputs(sourceInv, holders);
            return null;
        }
        expectedOutputs.addAll(outputs);
        expectedContainerItems.addAll(containers);
        return holders;
    }
}
