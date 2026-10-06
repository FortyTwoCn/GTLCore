package org.gtlcore.gtlcore.integration.ae2.pattern;

import org.gtlcore.gtlcore.api.recipe.ingredient.*;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.api.registry.registrate.entry.GTRecipeTypeEntry;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeQuickUploadResolverIntegrationTest {
    private static final long LARGE = 9_007_199_254_740_993L;
    private static ItemStack item(Item item, String name) { var stack = new ItemStack(item); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack; }
    private static FluidStack fluid(String name) { var stack = new FluidStack(Fluids.WATER, 1); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack; }
    private static AEProcessingPattern pattern(List<GenericStack> in, List<GenericStack> out) { return new AEProcessingPattern(AEItemKey.of(PatternDetailsHelper.encodeProcessingPattern(in, out))); }
    private static RecipeManager manager(GTRecipe... recipes) {
        var manager = new RecipeManager(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var holders = new ArrayList<RecipeHolder<?>>(); for (var recipe : recipes) holders.add(new RecipeHolder<>(recipe.id, recipe)); manager.replaceRecipes(holders); return manager;
    }
    private static GTRecipe simple(GTRecipeTypeEntry type, String name) { return type.recipeBuilder(name).inputItems(Items.IRON_INGOT, 2).outputItems(Items.GOLD_INGOT, 3).build(); }
    private static AEProcessingPattern simplePattern(long multiplier) { return pattern(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2 * multiplier)), List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 3 * multiplier))); }
    @Test void nativeRecipeHoldersKeepLongScalingAndItemFluidComponents() {
        var iron = item(Items.IRON_INGOT, "resolver input"); var diamond = item(Items.DIAMOND, "resolver output"); var water = fluid("resolver fluid");
        var recipe = GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder("native_resolver_components")
                .inputItems(LongIngredient.create(iron, LARGE)).inputFluids(LongFluidIngredient.create(water, 3_000_000_001L))
                .outputItems(LongIngredient.create(diamond, LARGE)).build();
        var in = List.of(new GenericStack(AEItemKey.of(iron), LARGE * 2), new GenericStack(AEFluidKey.of(water), 6_000_000_002L));
        var out = List.of(new GenericStack(AEItemKey.of(diamond), LARGE * 2)); var manager = manager(recipe);
        assertEquals(Set.of(GTRecipeTypes.ASSEMBLER_RECIPES.value().id), PatternQuickUploadRecipeTypeResolver.findRecipeTypeIds(manager, pattern(in, out)));
        assertTrue(PatternQuickUploadRecipeTypeResolver.findRecipeTypeIds(manager, pattern(List.of(in.getFirst(), new GenericStack(AEFluidKey.of(Fluids.WATER), 6_000_000_002L)), out)).isEmpty());
        assertTrue(PatternQuickUploadRecipeTypeResolver.findRecipeTypeIds(manager, pattern(in, List.of(new GenericStack(AEItemKey.of(diamond), LARGE * 2 + 1)))).isEmpty());
        assertEquals(LARGE, LongIngredient.amount(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP.of(recipe.inputs.get(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP).getFirst().content())));
    }
    @Test void nativeResolverRejectsUnrelatedTypeAmbiguityButCollapsesTheChemicalBuilderCopyPair() {
        var ambiguous = manager(simple(GTRecipeTypes.ASSEMBLER_RECIPES, "native_ambiguous_assembler"), simple(GTRecipeTypes.MIXER_RECIPES, "native_ambiguous_mixer"));
        assertTrue(PatternQuickUploadRecipeTypeResolver.findRecipeTypeIds(ambiguous, simplePattern(3)).isEmpty());
        var chemical = manager(simple(GTRecipeTypes.CHEMICAL_RECIPES, "native_small_chemical"), simple(GTRecipeTypes.LARGE_CHEMICAL_RECIPES, "native_large_chemical"));
        assertEquals(Set.of(GTRecipeTypes.CHEMICAL_RECIPES.value().id), PatternQuickUploadRecipeTypeResolver.findRecipeTypeIds(chemical, simplePattern(3)));
        assertSame(GTRecipeTypes.CHEMICAL_RECIPES.value(), NativeCopiedRecipeTypes.smallType(GTRecipeTypes.LARGE_CHEMICAL_RECIPES.value())); assertNull(NativeCopiedRecipeTypes.smallType(GTRecipeTypes.MIXER_RECIPES.value()));
    }
    @Test void nativeProxyRecipeHoldersAndVanillaMolecularIdsRemainDiscoverable() {
        var furnace = GTRecipeTypes.FURNACE_RECIPES.value(); var list = furnace.getProxyRecipes().get(RecipeType.SMELTING); assertNotNull(list);
        var previous = new ArrayList<>(list);
        try {
            list.clear(); var recipe = simple(GTRecipeTypes.FURNACE_RECIPES, "native_proxy_recipe"); list.add(new RecipeHolder<>(recipe.id, recipe));
            assertEquals(Set.of(furnace.id), PatternQuickUploadRecipeTypeResolver.findRecipeTypeIds(manager(), simplePattern(2)));
            assertTrue(PatternQuickUploadRecipeTypeResolver.expandProxiedRecipeTypeIds(BuiltInRegistries.RECIPE_TYPE.getKey(RecipeType.SMELTING)).contains(furnace.id));
            for (var type : List.of(RecipeType.CRAFTING, RecipeType.SMITHING, RecipeType.STONECUTTING)) {
                var id = BuiltInRegistries.RECIPE_TYPE.getKey(type); assertTrue(PatternQuickUploadRecipeTypeResolver.isMolecularRecipeTypeId(id)); assertTrue(PatternQuickUploadRecipeTypeResolver.expandProxiedRecipeTypeIds(id).contains(id));
            }
        } finally { list.clear(); list.addAll(previous); }
    }
    @Test void nativeResolverSkipsOverflowingRecipeSumsAndRejectsNonIntegralScaling() {
        var recipe = GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder("native_overflowing_signature")
                .inputItems(LongIngredient.create(new ItemStack(Items.IRON_INGOT), Long.MAX_VALUE), LongIngredient.create(new ItemStack(Items.IRON_INGOT), 1)).outputItems(Items.GOLD_INGOT).build();
        assertTrue(PatternQuickUploadRecipeTypeResolver.findRecipeTypeIds(manager(recipe), simplePattern(1)).isEmpty());
        assertTrue(PatternQuickUploadRecipeTypeResolver.findRecipeTypeIds(manager(simple(GTRecipeTypes.ASSEMBLER_RECIPES, "native_nonintegral_signature")),
                pattern(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 3)), List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 3)))).isEmpty());
    }
}
