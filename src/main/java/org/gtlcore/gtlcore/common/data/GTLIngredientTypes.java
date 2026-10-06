package org.gtlcore.gtlcore.common.data;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.fluids.crafting.FluidIngredientType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Native replacement for the old Forge CraftingHelper registration. */
public final class GTLIngredientTypes {
    private static final DeferredRegister<IngredientType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.INGREDIENT_TYPES, LongIngredient.TYPE.getNamespace());
    static { TYPES.register(LongIngredient.TYPE.getPath(), () -> LongIngredient.INGREDIENT_TYPE); }
    private static final DeferredRegister<FluidIngredientType<?>> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_INGREDIENT_TYPES, LongFluidIngredient.TYPE.getNamespace());
    static { FLUID_TYPES.register(LongFluidIngredient.TYPE.getPath(), () -> LongFluidIngredient.INGREDIENT_TYPE); }

    private GTLIngredientTypes() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        FLUID_TYPES.register(modBus);
    }
}
