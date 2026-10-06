package org.gtlcore.gtlcore.mixin.gtm.registry;

import org.gtlcore.gtlcore.config.ConfigHolder;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gregtechceu.gtceu.utils.GTUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** Applies the pack's duration policy to native objects, including runtime-generated recipes. */
@Mixin(GTRecipeBuilder.class)
public abstract class GTRecipeBuilderMixin {
    @Unique
    private ResourceLocation gtlcore$loadedRecipeId;
    @Unique
    private GTRecipeType gtlcore$loadedRecipeType;

    /** Native reload regenerates child recipes through a builder and also saves its parent. */
    @Inject(method = "<init>(Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;Lcom/gregtechceu/gtceu/api/recipe/GTRecipeType;)V",
            at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$rememberLoadedRecipe(GTRecipe source, GTRecipeType type, CallbackInfo ci) {
        var id = source.getId();
        if (source.recipeType == type && id != null
                && (id.getNamespace().equals("gtlcore") || id.getNamespace().equals("gtladditions"))
                && !id.getPath().startsWith(type.id.getPath() + "/")) {
            gtlcore$loadedRecipeId = id;
            gtlcore$loadedRecipeType = type;
        }
    }

    @Unique
    private boolean gtlcore$preservesLoadedRecipe() {
        var builder = (GTRecipeBuilder) (Object) this;
        return gtlcore$loadedRecipeId != null && builder.recipeType == gtlcore$loadedRecipeType
                && builder.id.equals(gtlcore$loadedRecipeId);
    }

    @ModifyArg(method = "save", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/data/recipes/RecipeOutput;accept(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/item/crafting/Recipe;Lnet/minecraft/advancements/AdvancementHolder;)V"),
            index = 0, remap = false, require = 1)
    private ResourceLocation gtlcore$saveLoadedRecipeId(ResourceLocation generatedId) {
        return gtlcore$preservesLoadedRecipe() ? gtlcore$loadedRecipeId : generatedId;
    }
    @Unique
    private static final Set<String> gtlcore$unscaledTypes = Set.of(
            "primitive_void_ore", "large_boiler", "steam_boiler", "slaughterhouse", "dyson_sphere",
            "space_elevator", "annihilate_generator", "create_aggregation", "large_naquadah_reactor",
            "hyper_reactor", "advanced_hyper_reactor", "door_of_create", "block_conversion", "weather_control");

    @Inject(method = "EUt(JJ)Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;",
            at = @At("HEAD"), remap = false, require = 1)
    private void gtlcore$voltageTier(long voltage, long amperage, CallbackInfoReturnable<GTRecipeBuilder> cir) {
        long absoluteVoltage = voltage == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(voltage);
        ((GTRecipeBuilder) (Object) this).data.putInt("euTier", GTUtil.getTierByVoltage(absoluteVoltage));
    }

    @Inject(method = "build", at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$nativeDuration(CallbackInfoReturnable<GTRecipe> cir) {
        GTRecipe recipe = cir.getReturnValue();
        if (gtlcore$preservesLoadedRecipe()) recipe.setId(gtlcore$loadedRecipeId);
        // Native builders and copies share data. Building must not change another recipe's metadata.
        recipe.data = recipe.data.copy();
        // Recipe-type templates may already carry a tier from their default EUt. Recompute after all inputs.
        recipe.data.putInt("euTier", RecipeHelper.getRecipeEUtTier(recipe));
        long duration = Math.abs((long) recipe.duration);
        double multiplier = ConfigHolder.INSTANCE.durationMultiplier;
        boolean exempt = "gtceu".equals(recipe.recipeType.id.getNamespace()) &&
                gtlcore$unscaledTypes.contains(recipe.recipeType.id.getPath());
        if (multiplier == 1 || exempt || RecipeHelper.getRealEUtWithIO(recipe).isOutput()) {
            recipe.duration = (int) Math.min(Integer.MAX_VALUE, duration);
        } else {
            double scaled = duration * multiplier;
            recipe.duration = (int) Math.min(Integer.MAX_VALUE, Math.max(1, Double.isNaN(scaled) ? 1 : scaled));
        }
    }
}
