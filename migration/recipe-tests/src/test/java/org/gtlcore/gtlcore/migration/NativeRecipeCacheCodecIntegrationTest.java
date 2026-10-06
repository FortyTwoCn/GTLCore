package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.utils.GTLUtil;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeCacheCodecIntegrationTest {
    private static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    @Test void nativeItemAndRecipeCachesPreserveRegistryComponentsIdAndLongAmounts() {
        var item = Items.IRON_INGOT.getDefaultInstance(); item.set(DataComponents.CUSTOM_NAME, Component.literal("cached input"));
        var stack = item.copyWithCount(37);
        var restored = GTLUtil.loadItemStack((CompoundTag) stack.save(REGISTRIES), REGISTRIES);
        assertTrue(ItemStack.isSameItemSameComponents(stack, restored)); assertEquals(37, restored.getCount());
        var water = new FluidStack(Fluids.WATER, 1); water.set(DataComponents.CUSTOM_NAME, Component.literal("cached coolant"));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder(ResourceLocation.fromNamespaceAndPath("gtlcore_recipe_test", "native_cache_recipe"))
                .inputItems(LongIngredient.create(item, 9_007_199_254_740_993L))
                .inputFluids(LongFluidIngredient.create(water, 9_007_199_254_741_001L)).outputItems(Items.GOLD_INGOT).build();
        var tag = GTLUtil.serializeNBT(recipe, REGISTRIES); var decoded = GTLUtil.deserializeNBT(tag, REGISTRIES);
        assertNotNull(decoded); assertEquals(recipe.id, decoded.id); assertSame(recipe.getType(), decoded.getType());
        var input = ItemRecipeCapability.CAP.of(decoded.getInputContents(ItemRecipeCapability.CAP).getFirst().content());
        var fluid = FluidRecipeCapability.CAP.of(decoded.getInputContents(FluidRecipeCapability.CAP).getFirst().content());
        assertEquals(9_007_199_254_740_993L, LongIngredient.amount(input)); assertTrue(ItemStack.isSameItemSameComponents(item, input.getItems()[0]));
        assertEquals(9_007_199_254_741_001L, LongFluidIngredient.amount(fluid)); assertEquals(water.getComponentsPatch(), fluid.getFluids()[0].getComponentsPatch());
    }
    @Test void invalidNativeRecipeCacheReturnsNoRecipeWithoutChangingTheSource() {
        var tag = new CompoundTag(); tag.putString("id", "?"); tag.putString("recipe", "broken"); var before = tag.copy();
        assertNull(GTLUtil.deserializeNBT(tag, REGISTRIES)); assertEquals(before, tag);
        tag.putString("id", "gtlcore:invalid_cache"); before = tag.copy();
        assertNull(GTLUtil.deserializeNBT(tag, REGISTRIES)); assertEquals(before, tag);
    }
}
