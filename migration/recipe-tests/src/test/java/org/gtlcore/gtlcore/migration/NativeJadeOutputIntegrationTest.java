package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.integration.jade.NativeRecipeOutputRows;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeJadeOutputIntegrationTest {
    private static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    @Test void longOutputsGroupByComponentsAndRoundTripExactCountsWithSaturatedSums() {
        var first = Items.IRON_INGOT.getDefaultInstance();
        first.set(DataComponents.CUSTOM_NAME, Component.literal("first item"));
        var second = first.copy(); second.set(DataComponents.CUSTOM_NAME, Component.literal("second item"));
        var firstWater = new FluidStack(Fluids.WATER, 1);
        firstWater.set(DataComponents.CUSTOM_NAME, Component.literal("first coolant"));
        var secondWater = firstWater.copy(); secondWater.set(DataComponents.CUSTOM_NAME, Component.literal("second coolant"));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_jade_component_rows")
                .outputItems(LongIngredient.create(first, Long.MAX_VALUE - 3))
                .outputItems(LongIngredient.create(second, 9_007_199_254_740_993L))
                .outputFluids(LongFluidIngredient.create(firstWater, Long.MAX_VALUE - 5))
                .outputFluids(LongFluidIngredient.create(secondWater, 9_007_199_254_741_001L)).build();
        recipe.outputs.get(ItemRecipeCapability.CAP).add(new Content(LongIngredient.create(first, 7), 10000, 10000));
        recipe.outputs.get(FluidRecipeCapability.CAP).add(new Content(LongFluidIngredient.create(firstWater, 9), 10000, 10000));
        var data = new CompoundTag(); NativeRecipeOutputRows.append(recipe, REGISTRIES, data);
        var items = NativeRecipeOutputRows.readItems(data, REGISTRIES);
        var fluids = NativeRecipeOutputRows.readFluids(data, REGISTRIES);
        assertEquals(2, items.size()); assertEquals(2, fluids.size());
        assertEquals(Long.MAX_VALUE, items.getFirst().amount()); assertEquals(Long.MAX_VALUE, fluids.getFirst().amount());
        assertEquals(9_007_199_254_740_993L, items.get(1).amount()); assertEquals(9_007_199_254_741_001L, fluids.get(1).amount());
        assertEquals(first.getComponents(), items.getFirst().stack().getComponents());
        assertEquals(second.getComponents(), items.get(1).stack().getComponents());
        assertEquals(firstWater.getComponentsPatch(), fluids.getFirst().stack().getComponentsPatch());
        assertEquals(secondWater.getComponentsPatch(), fluids.get(1).stack().getComponentsPatch());
        assertEquals(1, items.getFirst().stack().getCount()); assertEquals(1, fluids.getFirst().stack().getAmount());
        assertEquals(Long.MAX_VALUE - 3, LongIngredient.amount(ItemRecipeCapability.CAP.of(recipe.outputs.get(ItemRecipeCapability.CAP).getFirst().content())));
    }

    @Test void chancedLongOutputsUseExactHalfUpMathAndLeaveNativeSizedOutputsUntouched() {
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_jade_chanced_rows")
                .outputItems(LongIngredient.create(Items.DIAMOND.getDefaultInstance(), 9_007_199_254_740_995L))
                .outputItems(Items.GOLD_INGOT, 3)
                .outputFluids(LongFluidIngredient.create(Fluids.WATER, 9_007_199_254_740_999L)).build();
        var contents = recipe.outputs.get(ItemRecipeCapability.CAP);
        contents.set(0, new Content(contents.getFirst().content(), 5000, 10000));
        recipe.parallels = 3;
        var data = new CompoundTag(); NativeRecipeOutputRows.append(recipe, REGISTRIES, data);
        assertEquals(13_510_798_882_111_493L, NativeRecipeOutputRows.readItems(data, REGISTRIES).getFirst().amount());
        assertEquals(9_007_199_254_740_999L, NativeRecipeOutputRows.readFluids(data, REGISTRIES).getFirst().amount());
        assertFalse(NativeRecipeOutputRows.isLong(ItemRecipeCapability.CAP, contents.get(1)));
        assertEquals(3, ItemRecipeCapability.CAP.of(contents.get(1).content()).count());
        assertEquals(2, contents.size());
    }
}
