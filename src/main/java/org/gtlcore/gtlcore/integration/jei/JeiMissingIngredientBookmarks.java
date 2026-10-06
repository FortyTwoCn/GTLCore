package org.gtlcore.gtlcore.integration.jei;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraft.client.renderer.Rect2i;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;

public final class JeiMissingIngredientBookmarks {

    private static @Nullable IJeiRuntime runtime;

    private JeiMissingIngredientBookmarks() {}

    public static void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    public static void onRuntimeUnavailable() {
        runtime = null;
    }

    public static boolean isAvailable() {
        return runtime != null;
    }

    public static boolean showRecipes(AEKey key, boolean uses) {
        IJeiRuntime jei = runtime;
        if (jei == null) return false;
        var role = uses ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT;
        if (key instanceof AEItemKey item) {
            jei.getRecipesGui().show(jei.getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, item.toStack()));
        } else if (key instanceof AEFluidKey fluid) {
            jei.getRecipesGui().show(jei.getJeiHelpers().getFocusFactory().createFocus(role, NeoForgeTypes.FLUID_STACK, fluid.toStack(1000)));
        } else return false;
        return true;
    }

    public static AddResult add(Collection<AEKey> keys) {
        IJeiRuntime jeiRuntime = runtime;
        if (jeiRuntime == null) {
            return new AddResult(AddStatus.UNAVAILABLE, 0);
        }

        IIngredientManager ingredientManager = jeiRuntime.getIngredientManager();
        int supported = 0;
        int added = 0;
        try {
            for (AEKey key : new LinkedHashSet<>(keys)) {
                Optional<ITypedIngredient<?>> ingredient = toTypedIngredient(key, ingredientManager);
                if (ingredient.isEmpty()) {
                    continue;
                }
                supported++;
                if (jeiRuntime.getBookmarkManager().add(ingredient.get())) {
                    added++;
                }
            }
        } catch (RuntimeException exception) {
            GTLCore.LOGGER.error("Failed to add missing ingredients to JEI bookmarks", exception);
            return new AddResult(AddStatus.FAILED, added);
        }

        if (added > 0) {
            return new AddResult(AddStatus.ADDED, added);
        }
        return new AddResult(supported > 0 ? AddStatus.ALREADY_BOOKMARKED : AddStatus.NOTHING_TO_ADD, 0);
    }

    public static Optional<IClickableIngredient<?>> createClickableIngredient(AEKey key, Rect2i area) {
        IJeiRuntime jeiRuntime = runtime;
        if (jeiRuntime == null) {
            return Optional.empty();
        }

        IIngredientManager ingredientManager = jeiRuntime.getIngredientManager();
        if (key instanceof AEItemKey itemKey) {
            return createClickableIngredient(
                    ingredientManager, VanillaTypes.ITEM_STACK, itemKey.toStack(), area);
        }
        if (key instanceof AEFluidKey fluidKey) {
            return createClickableIngredient(
                    ingredientManager, NeoForgeTypes.FLUID_STACK, fluidKey.toStack(1), area);
        }
        return Optional.empty();
    }

    private static <T> Optional<IClickableIngredient<?>> createClickableIngredient(
                                                                                   IIngredientManager ingredientManager,
                                                                                   IIngredientType<T> type,
                                                                                   T ingredient,
                                                                                   Rect2i area) {
        return ingredientManager.createClickableIngredient(type, ingredient, area, false)
                .map(clickableIngredient -> clickableIngredient);
    }

    private static Optional<ITypedIngredient<?>> toTypedIngredient(AEKey key,
                                                                   IIngredientManager ingredientManager) {
        if (key instanceof AEItemKey itemKey) {
            return ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, itemKey.toStack())
                    .map(ingredient -> ingredient);
        }
        if (key instanceof AEFluidKey fluidKey) {
            return ingredientManager.createTypedIngredient(NeoForgeTypes.FLUID_STACK, fluidKey.toStack(1))
                    .map(ingredient -> ingredient);
        }
        return Optional.empty();
    }

    public enum AddStatus {
        ADDED,
        ALREADY_BOOKMARKED,
        NOTHING_TO_ADD,
        UNAVAILABLE,
        FAILED
    }

    public record AddResult(AddStatus status, int added) {}

}
