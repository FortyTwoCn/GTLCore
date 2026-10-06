package org.gtlcore.gtlcore.api.recipe.ingredient;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.ingredient.ExDataComponentFluidIngredient;
import com.gregtechceu.gtceu.api.recipe.lookup.ingredient.MapIngredientTypeManager;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/** Register after native GT's common setup; quantities never affect matching ingredient identity. */
public final class NativeRecipeSearchIndex {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private NativeRecipeSearchIndex() {}
    public static void register(IEventBus modBus) {
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> { register(); }));
    }
    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        MapIngredientTypeManager.registerMapIngredient(DataComponentFluidIngredient.class, ComponentFluidMapIngredient::from);
        MapIngredientTypeManager.registerMapIngredient(ExDataComponentFluidIngredient.class, ComponentFluidMapIngredient::from);
        MapIngredientTypeManager.registerMapIngredient(FluidStack.class, ComponentFluidMapIngredient::from);
        MapIngredientTypeManager.registerMapIngredient(LongFluidIngredient.class,
                ingredient -> MapIngredientTypeManager.getFrom(ingredient.getInner(), FluidRecipeCapability.CAP));
        MapIngredientTypeManager.registerMapIngredient(LongIngredient.class, ingredient -> {
            var inner = ingredient.getInner();
            return MapIngredientTypeManager.getFrom(inner.isCustom() ? inner.getCustomIngredient() : inner, ItemRecipeCapability.CAP);
        });
    }
}
