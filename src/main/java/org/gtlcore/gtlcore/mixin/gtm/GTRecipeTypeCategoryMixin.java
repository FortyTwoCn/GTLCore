package org.gtlcore.gtlcore.mixin.gtm;

import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.jei.GTRecipeJeiTiming;
import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.integration.recipeviewer.jei.recipe.GTRecipeJEICategory;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Keep native categories and representative recipes; batch unique catalyst links without legacy wrappers. */
@Mixin(value = GTRecipeJEICategory.class, remap = false)
public abstract class GTRecipeTypeCategoryMixin {
    @Shadow @Final public static Function<GTRecipeCategory, RecipeType<GTRecipe>> TYPES;
    @Unique private static final org.slf4j.Logger gtlcore$LOGGER = org.slf4j.LoggerFactory.getLogger("GTLCore/JEI");

    @Inject(method = "registerRecipes", at = @At("HEAD"), cancellable = true, require = 1)
    private static void gtlcore$registerRecipes(IRecipeRegistration registration, CallbackInfo ci) {
        if (!GTRecipeJeiTiming.isEnabled()) return;
        ci.cancel();
        long started = System.nanoTime();
        GTRecipeJeiTiming.reset();
        var subcategories = new ArrayList<GTRecipeCategory>();
        int categories = 0, recipes = 0;
        // Native virtual/scanner recipes can add entries to subcategories: finish main types first.
        for (var category : GTRegistries.RECIPE_CATEGORIES) {
            if (!category.shouldRegisterDisplays()) continue;
            var type = category.getRecipeType();
            if (category != type.getCategory()) { subcategories.add(category); continue; }
            type.buildRepresentativeRecipes();
            recipes += gtlcore$registerCategory(registration, category);
            categories++;
        }
        for (var category : subcategories) {
            recipes += gtlcore$registerCategory(registration, category);
            categories++;
        }
        gtlcore$LOGGER.info("Optimized native GT JEI registration: categories={}, recipes={}, elapsed_ms={}",
                categories, recipes, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    @Unique
    private static int gtlcore$registerCategory(IRecipeRegistration registration, GTRecipeCategory category) {
        long started = System.nanoTime();
        var type = category.getRecipeType();
        var recipes = List.copyOf(type.getRecipesInCategory(category));
        long copied = System.nanoTime();
        GTRecipeJeiTiming.record(type, GTRecipeJeiTiming.Phase.WRAPPER, copied - started);
        registration.addRecipes(TYPES.apply(category), recipes);
        long registered = System.nanoTime();
        GTRecipeJeiTiming.record(type, GTRecipeJeiTiming.Phase.REGISTRATION, registered - copied);
        int threshold = ConfigHolder.INSTANCE == null ? ConfigHolder.DEFAULT_GTCEU_JEI_SLOW_RECIPE_TYPE_WARNING_MILLIS :
                ConfigHolder.INSTANCE.debugLogging.gtceuJeiSlowRecipeTypeWarningMillis;
        if (TimeUnit.NANOSECONDS.toMillis(registered - started) >= threshold)
            gtlcore$LOGGER.warn("Slow native GT JEI category: category={}, recipes={}, snapshot_ms={}, registration_ms={}, elapsed_ms={}",
                    category.id, recipes.size(), TimeUnit.NANOSECONDS.toMillis(copied - started),
                    TimeUnit.NANOSECONDS.toMillis(registered - copied), TimeUnit.NANOSECONDS.toMillis(registered - started));
        return recipes.size();
    }

    @Inject(method = "registerRecipeCatalysts", at = @At("HEAD"), cancellable = true, require = 1)
    private static void gtlcore$registerCatalysts(IRecipeCatalystRegistration registration, CallbackInfo ci) {
        if (!GTRecipeJeiTiming.isEnabled()) return;
        ci.cancel();
        int machines = 0, links = 0;
        long started = System.nanoTime();
        for (var machine : GTRegistries.MACHINES) {
            var categories = new LinkedHashSet<RecipeType<?>>();
            for (var type : machine.getRecipeTypes()) {
                for (var category : type.getCategories()) {
                    if (!category.isXEIVisible() && !GTCEu.isDev()) continue;
                    // The native furnace category is vanilla smelting, not a GT-only category.
                    categories.add(GTRecipeJEICategory.machineType(category));
                }
            }
            if (categories.isEmpty()) continue;
            registration.addRecipeCatalyst(machine.asStack(), categories.toArray(RecipeType[]::new));
            machines++; links += categories.size();
        }
        gtlcore$LOGGER.info("Optimized native GT JEI catalysts: machines={}, links={}, elapsed_ms={}",
                machines, links, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }
}
