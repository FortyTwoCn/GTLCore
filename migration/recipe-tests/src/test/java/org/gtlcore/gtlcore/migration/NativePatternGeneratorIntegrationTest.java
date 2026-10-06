package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.*;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.common.item.PatternTestBehavior;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import appeng.api.stacks.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NativePatternGeneratorIntegrationTest {
    private static final long LARGE = 9_007_199_254_740_993L;
    private static ItemStack iron() {
        var stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("native generator ingredient")); return stack;
    }
    private static FluidStack water() {
        var stack = new FluidStack(Fluids.WATER, 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("native generator coolant")); return stack;
    }
    private static GTRecipe largeRecipe(long amount) {
        return GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_generator_components").duration(20).EUt(128)
                .inputItems(LongIngredient.create(iron(), amount)).inputFluids(LongFluidIngredient.create(water(), amount))
                .circuitMeta(17).outputItems(Items.DIAMOND, 3).outputFluids(LongFluidIngredient.create(Fluids.LAVA, amount)).build();
    }
    @Test void generationKeepsLongComponentKeysCircuitMetadataAndTwoLoreLinesAcrossNativeSave() {
        var recipe = largeRecipe(LARGE); var pattern = PatternTestBehavior.createPattern(recipe, null, 2);
        var decoded = Ae2BaseProcessingPatternHelper.decodeToAEProcessingPattern(pattern, null);
        assertNotNull(decoded);
        assertEquals(List.of(new GenericStack(AEItemKey.of(iron()), LARGE * 2), new GenericStack(AEFluidKey.of(water()), LARGE * 2)), decoded.getSparseInputs());
        assertEquals(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 6), new GenericStack(AEFluidKey.of(Fluids.LAVA), LARGE * 2)), decoded.getSparseOutputs());
        assertEquals(Set.of(recipe.recipeType.id), PatternQuickUploadMetadata.readRecipeTypeIds(pattern));
        assertEquals(2, pattern.get(DataComponents.LORE).lines().size());
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        var loaded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, pattern).getOrThrow()).getOrThrow();
        assertEquals(PatternQuickUploadMetadata.readRecipeTypeIds(pattern), PatternQuickUploadMetadata.readRecipeTypeIds(loaded));
        assertEquals(pattern.get(DataComponents.LORE), loaded.get(DataComponents.LORE), "Native lore component must round trip");
        assertEquals(pattern.getComponentsPatch(), loaded.getComponentsPatch());
        assertEquals(decoded.getSparseInputs(), Ae2BaseProcessingPatternHelper.decodeToAEProcessingPattern(loaded, null).getSparseInputs());
        assertEquals(17, GTRecipeManager.circuit(recipe));
    }
    @Test void recipeHolderFilteringCombinesTypeCircuitAndAllFourNativeKeywordLists() {
        var good = largeRecipe(7);
        var otherCircuit = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_generator_other_circuit").inputItems(Items.IRON_INGOT).circuitMeta(18).outputItems(Items.DIAMOND).build();
        var blacklist = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_generator_blacklist").inputItems(Items.GOLD_INGOT).circuitMeta(17).outputItems(Items.DIAMOND).build();
        var otherType = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_generator_other_type").inputItems(Items.IRON_INGOT).circuitMeta(17).outputItems(Items.DIAMOND).build();
        var manager = new GTRecipeManager(List.of(new RecipeHolder<>(good.id, good), new RecipeHolder<>(otherCircuit.id, otherCircuit), new RecipeHolder<>(blacklist.id, blacklist), new RecipeHolder<>(otherType.id, otherType)));
        manager.filterRecipesByType(GTRecipeTypes.CHEMICAL_RECIPES.value()); manager.filterRecipesByCircuit(17);
        manager.filterRecipesByInputsIdArray(new String[]{"iron", "water"}, true);
        manager.filterRecipesByInputsIdArray(new String[]{"gold", ""}, false);
        manager.filterRecipesByOutputsIdArray(new String[]{"diamond"}, true);
        manager.filterRecipesByOutputsIdArray(new String[]{"water"}, false);
        assertEquals(List.of(good), manager.getRecipes());
        assertEquals(LARGE, new ConflictAnalysisObj(largeRecipe(LARGE)).inputFluidIdsMap.get("minecraft:water"));
    }
    @Test void conflictAnalysisIncludesCircuitFreeRecipesAndExcludesOtherConfigurations() {
        var a = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_conflict_a").inputItems(Items.IRON_INGOT).circuitMeta(17).outputItems(Items.DIAMOND).build();
        var b = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_conflict_b").inputItems(Items.IRON_INGOT).outputItems(Items.GOLD_INGOT).build();
        var c = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_conflict_c").inputItems(Items.IRON_INGOT).circuitMeta(18).outputItems(Items.EMERALD).build();
        var results = new ConflictAnalysisManager(List.of(a, b, c)).useFindConflictForAll(17L);
        assertEquals(Set.of(a, b), results.stream().map(result -> result.getConflictAnalysisObj().recipe).collect(java.util.stream.Collectors.toSet()));
        assertTrue(results.stream().noneMatch(result -> result.getMatchPool().stream().anyMatch(id -> id.contains("circuit"))));
    }
    @Test void invalidOrOverflowingMultipliersCannotReturnSilentlyUnscaledPatterns() {
        assertThrows(IllegalArgumentException.class, () -> PatternTestBehavior.createPattern(largeRecipe(3), null, 0));
        assertThrows(IllegalArgumentException.class, () -> PatternTestBehavior.createPattern(largeRecipe(Long.MAX_VALUE), null, 2));
        var duplicate = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_generator_duplicate_overflow")
                .inputItems(LongIngredient.create(iron(), Long.MAX_VALUE), LongIngredient.create(iron(), 1)).outputItems(Items.DIAMOND).build();
        assertThrows(ArithmeticException.class, () -> Ae2GtmProcessingPattern.of(duplicate, null));
    }
}
