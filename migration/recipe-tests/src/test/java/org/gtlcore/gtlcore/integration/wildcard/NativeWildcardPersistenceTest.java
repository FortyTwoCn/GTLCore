package org.gtlcore.gtlcore.integration.wildcard;

import org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.Ae2BaseProcessingPatternHelper;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.client.gui.widget.PatternCycleWidget;
import org.gtlcore.gtlcore.client.gui.widget.PatternCycleWidget.Preview;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import brachy.modularui.value.sync.GenericSyncValue;
import appeng.api.crafting.*;
import appeng.api.stacks.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import it.unimi.dsi.fastutil.ints.*;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeWildcardPersistenceTest {
    private static RegistryAccess registry() { return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY); }
    private static IPatternDetails pattern(GenericStack input, GenericStack output) {
        var encoded = PatternDetailsHelper.encodeProcessingPattern(List.of(input), List.of(output));
        return Objects.requireNonNull(Ae2BaseProcessingPatternHelper.decodeToAEProcessingPattern(encoded, null));
    }
    private static GenericStack item(Item item, long amount) { return new GenericStack(AEItemKey.of(new ItemStack(item)), amount); }
    private static CompoundTag inventory(int value) { var tag = new CompoundTag(); tag.putInt("inventoryMarker", value); return tag; }
    private static CompoundTag save(MEWildcardPatternBufferPersistenceHelper helper, int count, Int2ReferenceMap<GTRecipe> cache) {
        var tag = new CompoundTag(); helper.save(tag, registry(), count, index -> true, index -> inventory(index + 40), cache::containsKey, cache); return tag;
    }

    @Test void reorderedExpansionWithSameOutputsRestoresInventoriesByCompleteNativeInputSignature() {
        var a = pattern(item(Items.IRON_INGOT, 1), item(Items.GOLD_INGOT, 1));
        var b = pattern(item(Items.DIAMOND, 1), item(Items.GOLD_INGOT, 1));
        var saved = new MEWildcardPatternBufferPersistenceHelper(); saved.indexPattern(a, 0); saved.indexPattern(b, 1);
        var tag = save(saved, 2, new Int2ReferenceOpenHashMap<>());
        var restored = new MEWildcardPatternBufferPersistenceHelper(); restored.indexPattern(b, 0); restored.indexPattern(a, 1); restored.load(tag, registry());
        var values = new HashMap<Integer, Integer>();
        restored.restore(new Int2ReferenceOpenHashMap<>(), (slot, data) -> values.put(slot, data.getInt("inventoryMarker")),
                refunds -> fail("Unchanged patterns refunded their inventory"), id -> null);
        assertEquals(Map.of(0, 41, 1, 40), values);
    }
    @Test void identicalExpandedPatternsKeepSeparateInventoriesAndRemovedDuplicatesRefundExactlyOnce() {
        var pattern = pattern(item(Items.IRON_INGOT, 1), item(Items.GOLD_INGOT, 1));
        var saved = new MEWildcardPatternBufferPersistenceHelper(); saved.indexPattern(pattern, 0); saved.indexPattern(pattern, 1);
        var tag = save(saved, 2, new Int2ReferenceOpenHashMap<>());
        var restored = new MEWildcardPatternBufferPersistenceHelper(); restored.indexPattern(pattern, 5); restored.load(tag, registry());
        var values = new HashMap<Integer, Integer>(); var refunds = new ArrayList<CompoundTag>();
        restored.restore(new Int2ReferenceOpenHashMap<>(), (slot, data) -> values.put(slot, data.getInt("inventoryMarker")), refunds::addAll, id -> null);
        restored.restore(new Int2ReferenceOpenHashMap<>(), (slot, data) -> fail("Restored twice"), refunds::addAll, id -> null);
        assertEquals(Map.of(5, 40), values); assertEquals(1, refunds.size()); assertEquals(41, refunds.getFirst().getInt("inventoryMarker"));
        var both = new MEWildcardPatternBufferPersistenceHelper(); both.indexPattern(pattern, 3); both.indexPattern(pattern, 8); both.load(tag, registry());
        values.clear(); both.restore(new Int2ReferenceOpenHashMap<>(), (slot, data) -> values.put(slot, data.getInt("inventoryMarker")), ignored -> fail("Duplicate inventory lost"), id -> null);
        assertEquals(Map.of(3, 40, 8, 41), values);
    }
    @Test void nativeComponentsAndLongInputOutputAmountsBindCachesWithoutIntOrDoubleTruncation() {
        long amount = 9_007_199_254_740_993L;
        var iron = new ItemStack(Items.IRON_INGOT); iron.set(DataComponents.CUSTOM_NAME, Component.literal("precise wildcard input"));
        var water = new FluidStack(Fluids.WATER, 1); water.set(DataComponents.CUSTOM_NAME, Component.literal("precise wildcard output"));
        var pattern = pattern(new GenericStack(AEItemKey.of(iron), amount), new GenericStack(AEFluidKey.of(water), Long.MAX_VALUE));
        var recipe = GTRecipeTypes.DUMMY_RECIPES.recipeBuilder("native_wildcard_cache").duration(20).build();
        recipe.inputs.put(ItemRecipeCapability.CAP, List.of(new Content(LongIngredient.create(iron, amount), 10000, 10000)));
        recipe.outputs.put(FluidRecipeCapability.CAP, List.of(new Content(LongFluidIngredient.create(water, Long.MAX_VALUE), 10000, 10000)));
        var source = new MEWildcardPatternBufferPersistenceHelper(); source.indexPattern(pattern, 0);
        var cache = new Int2ReferenceOpenHashMap<GTRecipe>(); cache.put(0, recipe); var tag = save(source, 1, cache);
        var target = new MEWildcardPatternBufferPersistenceHelper(); target.indexPattern(pattern, 6); target.load(tag, registry());
        cache.clear(); target.restore(cache, (slot, data) -> assertEquals(6, slot), ignored -> fail("Native component keys failed to restore"), id -> id.equals(recipe.id) ? recipe : null);
        assertSame(recipe, cache.get(6));
        var changed = recipe.copy(); changed.inputs.put(ItemRecipeCapability.CAP, List.of(new Content(LongIngredient.create(iron, amount - 1), 10000, 10000)));
        target.load(tag, registry()); cache.clear(); target.restore(cache, (slot, data) -> {}, ignored -> fail("Inventory lost when recipe amount changed"), id -> changed);
        assertTrue(cache.isEmpty(), "Changed native recipe reused an incompatible wildcard cache");
    }
    @Test void cachedRecipeRetainsAdditionalStockAndReusableInputsAcrossNativeReload() {
        var pattern = pattern(item(Items.IRON_INGOT, 1), item(Items.GOLD_INGOT, 1));
        var recipe = GTRecipeTypes.DUMMY_RECIPES.recipeBuilder("native_wildcard_stock_cache").duration(20).build();
        recipe.inputs.put(ItemRecipeCapability.CAP, List.of(
                new Content(LongIngredient.create(new ItemStack(Items.IRON_INGOT), 3), 10000, 10000),
                new Content(LongIngredient.create(new ItemStack(Items.FLINT), 1), 0, 10000)));
        recipe.outputs.put(ItemRecipeCapability.CAP, List.of(new Content(LongIngredient.create(new ItemStack(Items.GOLD_INGOT), 1), 10000, 10000)));
        var source = new MEWildcardPatternBufferPersistenceHelper(); source.indexPattern(pattern, 0);
        var cache = new Int2ReferenceOpenHashMap<GTRecipe>(); cache.put(0, recipe); var tag = save(source, 1, cache);
        var target = new MEWildcardPatternBufferPersistenceHelper(); target.indexPattern(pattern, 5); target.load(tag, registry());
        cache.clear(); target.restore(cache, (slot, data) -> {}, ignored -> fail("Stocking ownership lost"), id -> recipe);
        assertSame(recipe, cache.get(5));
        var changed = recipe.copy(); changed.inputs.put(ItemRecipeCapability.CAP, List.of(
                new Content(LongIngredient.create(new ItemStack(Items.IRON_INGOT), 3), 10000, 10000),
                new Content(LongIngredient.create(new ItemStack(Items.FLINT), 2), 0, 10000)));
        target.load(tag, registry()); cache.clear(); target.restore(cache, (slot, data) -> {}, ignored -> fail("Changed catalyst lost ownership"), id -> changed);
        assertTrue(cache.isEmpty(), "Changed reusable ingredient retained a stale native cache");
    }
    @Test void nativeCycleRetainsInputMultiplierAndChangesOnlyAtTwentyTicks() {
        var a = pattern(item(Items.IRON_INGOT, 7), item(Items.GOLD_INGOT, 2));
        var b = pattern(item(Items.DIAMOND, 13), item(Items.EMERALD, 3));
        assertEquals(7, Preview.select(List.of(a, b), 0).inputs().getFirst().amount());
        assertEquals(Preview.select(List.of(a, b), 0), Preview.select(List.of(a, b), 19));
        assertEquals(13, Preview.select(List.of(a, b), 20).inputs().getFirst().amount());
        assertEquals(Preview.select(List.of(a, b), 0), Preview.select(List.of(a, b), 40));
        assertEquals(Preview.EMPTY, Preview.select(List.of(), Long.MAX_VALUE));
    }
    @Test void nativeModularUiPreviewCodecPreservesBothComponentKeysAndExactLongAmounts() {
        var iron = new ItemStack(Items.IRON_INGOT); iron.set(DataComponents.CUSTOM_NAME, Component.literal("cycle input"));
        var water = new FluidStack(Fluids.WATER, 1); water.set(DataComponents.CUSTOM_NAME, Component.literal("cycle output"));
        var inputs = new ArrayList<>(List.of(new GenericStack(AEItemKey.of(iron), 9_007_199_254_740_993L)));
        var value = new Preview(inputs, List.of(new GenericStack(AEFluidKey.of(water), Long.MAX_VALUE))); inputs.clear();
        var source = GenericSyncValue.<RegistryFriendlyByteBuf, Preview>builder(Preview.class).getter(() -> value)
                .serializer(Preview::write).deserializer(Preview::read).copy(p -> p).build();
        var target = GenericSyncValue.<RegistryFriendlyByteBuf, Preview>builder(Preview.class).getter(() -> Preview.EMPTY)
                .serializer(Preview::write).deserializer(Preview::read).copy(p -> p).build();
        source.updateCacheFromSource(true); var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registry());
        try { source.write(buffer); target.read(buffer); assertEquals(value, target.getValue()); assertFalse(buffer.isReadable());
            assertThrows(UnsupportedOperationException.class, () -> target.getValue().inputs().clear()); }
        finally { buffer.release(); }
    }
    @Test void nativePreviewRejectsMalformedLengthsAndNonpositiveAmounts() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registry());
        try { buffer.writeVarInt(7); assertThrows(IllegalArgumentException.class, () -> Preview.read(buffer)); }
        finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new Preview(List.of(item(Items.IRON_INGOT, 0)), List.of()));
    }
}
