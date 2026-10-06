package org.gtlcore.gtlcore.data.recipe;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.apache.logging.log4j.LogManager;

import java.util.List;

import static org.gtlcore.gtlcore.common.data.GTLRecipeTypes.DISASSEMBLY_RECIPES;
import static org.gtlcore.gtlcore.common.data.GTLRecipes.DISASSEMBLY_RECORD;

public class GenerateDisassembly {
    private static final String[] inputItem = { "suprachronal_", "_circuit", "_processor", "_assembly",
            "_computer", "_mainframe", "circuit_resonatic_" };
    private static final String[] outputItem = { "_cable", "_frame", "_fence", "_electric_motor",
            "_electric_pump", "_conveyor_module", "_electric_piston", "_robot_arm", "_field_generator",
            "_emitter", "_sensor", "smd_", "_lamp", "_crate", "_drum", "_machine_casing",
            "ae2:blank_pattern", "gtceu:carbon_nanoswarm", "gtlcore:virtual_ingredient" };

    private static boolean isExcludeItems(String id, String[] patterns) {
        for (String pattern : patterns) if (id.contains(pattern)) return true;
        return false;
    }

    private static String itemId(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }

    public static void generateDisassembly(GTRecipeBuilder source, RecipeOutput output) {
        List<Content> products = source.output.get(ItemRecipeCapability.CAP);
        if (products == null || products.isEmpty()) {
            LogManager.getLogger("GTLCore").error("配方{}没有输出", source.id);
            return;
        }
        var product = ItemRecipeCapability.CAP.of(products.getFirst().content());
        ItemStack[] candidates = product.getItems();
        if (candidates.length == 0 || candidates[0].isEmpty()) return;
        String id = itemId(candidates[0]);
        boolean circuitLine = source.recipeType.getId().equals(GTCEu.id("circuit_assembly_line"));
        if (isExcludeItems(id, outputItem)) return;
        var builder = DISASSEMBLY_RECIPES.recipeBuilder(ResourceLocation.parse(id))
                .inputItems(LongIngredient.copy(product)).duration(1).EUt(1);
        if (DISASSEMBLY_RECORD.remove(id) && !circuitLine) {
            DISASSEMBLY_RECORD.add(id);
        } else {
            DISASSEMBLY_RECORD.add(id);
            for (var content : source.input.getOrDefault(ItemRecipeCapability.CAP, List.of())) {
                var ingredient = ItemRecipeCapability.CAP.of(content.content());
                ItemStack[] items = ingredient.getItems();
                if (items.length == 0) continue;
                ItemStack item = items[0];
                if (content.chance() == ChanceLogic.getMaxChancedValue() && !item.isEmpty() &&
                        item.getComponentsPatch().isEmpty() &&
                        (circuitLine || !isExcludeItems(itemId(item), inputItem))) {
                    builder.outputItems(LongIngredient.copy(ingredient));
                }
            }
            for (var content : source.input.getOrDefault(FluidRecipeCapability.CAP, List.of())) {
                var fluid = FluidRecipeCapability.CAP.of(content.content());
                if (content.chance() == ChanceLogic.getMaxChancedValue() && fluid.amount() > 0) {
                    builder.output(FluidRecipeCapability.CAP, fluid);
                }
            }
        }
        builder.save(output);
    }
}
