package org.gtlcore.gtlcore.api.item.tool.ae2.patternTool;

import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import java.util.HashMap;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class Ae2GtmProcessingPattern extends Ae2BaseProcessingPattern {
    public final GTRecipe recipe;
    public Ae2GtmProcessingPattern(ItemStack stack, @Nullable ServerPlayer player, GTRecipe recipe) {
        super(stack, player); this.recipe = recipe;
    }
    public static Ae2GtmProcessingPattern of(GTRecipe recipe, @Nullable ServerPlayer player) {
        var inputs = RecipeStackHelper.getInputKeys(recipe);
        var outputs = RecipeStackHelper.getOutputKeys(recipe);
        validate(inputs, AEProcessingPattern.MAX_INPUT_SLOTS);
        validate(outputs, AEProcessingPattern.MAX_OUTPUT_SLOTS);
        var stack = PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
        PatternQuickUploadMetadata.writeRecipeTypeId(stack, recipe.recipeType.id);
        return new Ae2GtmProcessingPattern(stack, player, recipe);
    }
    private static void validate(List<GenericStack> stacks, int slots) {
        if (stacks.isEmpty() || stacks.size() > slots) throw new IllegalArgumentException("Recipe does not fit an AE processing pattern");
        var totals = new HashMap<AEKey, Long>();
        for (var stack : stacks) totals.merge(stack.what(), stack.amount(), Math::addExact);
    }
}
