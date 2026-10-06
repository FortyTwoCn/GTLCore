package org.gtlcore.gtlcore.mixin.gtm.recipe;

import org.gtlcore.gtlcore.common.recipe.GTLRecipeContents;
import org.gtlcore.gtlcore.common.data.GTLItems;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gregtechceu.gtceu.data.recipe.generated.WireRecipeHandler;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.data.recipes.RecipeOutput;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.function.Consumer;

import static com.gregtechceu.gtceu.api.GTValues.*;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.foil;
import static com.gregtechceu.gtceu.common.data.GTMaterials.*;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.ASSEMBLER_RECIPES;

@Mixin(WireRecipeHandler.class)
public abstract class WireRecipeHandlerMixin {

    @Shadow(remap = false)
    @Final
    private static Reference2IntMap<TagPrefix> INSULATION_AMOUNT;

    @Shadow(remap = false)
    private static void generateManualRecipe(RecipeOutput provider, TagPrefix wirePrefix, TagPrefix cablePrefix, int cableAmount, Material material) {}

    /**
     * @author mod_author
     * @reason Need more
     */
    @Overwrite(remap = false)
    private static void generateCableCovering(RecipeOutput provider, WireProperties property,
                                             TagPrefix wirePrefix, Material material) {
        // Superconductors have no Cables, so exit early
        if (!material.shouldGenerateRecipesFor(wirePrefix) || property.isSuperconductor()) return;

        int cableAmount = (int) (wirePrefix.getMaterialAmount(material) * 2 / M);
        TagPrefix cablePrefix = TagPrefix.get("cable" + wirePrefix.name().substring(4));
        int voltageTier = GTUtil.getTierByVoltage(property.getVoltage());
        int insulationAmount = INSULATION_AMOUNT.getInt(cablePrefix);

        // Generate hand-crafting recipes for ULV and LV cables
        if (voltageTier <= LV) {
            generateManualRecipe(provider, wirePrefix, cablePrefix, cableAmount, material);
        }

        // Rubber Recipe (ULV-EV cables)
        if (voltageTier <= EV) {
            GTRecipeBuilder builder = ASSEMBLER_RECIPES
                    .recipeBuilder("cover_" + material.getName() + "_" + wirePrefix + "_rubber")
                    .EUt(VA[ULV]).duration(100)
                    .inputItems(wirePrefix, material)
                    .outputItems(cablePrefix, material)
                    .inputFluids(GTLRecipeContents.fluid(Rubber, L * (long) insulationAmount));

            if (voltageTier == EV) {
                builder.inputItems(foil, PolyvinylChloride, insulationAmount);
            }
            builder.save(provider);
        }

        // Silicone Rubber Recipe (all cables)
        GTRecipeBuilder builder = ASSEMBLER_RECIPES
                .recipeBuilder("cover_" + material.getName() + "_" + wirePrefix + "_silicone")
                .EUt(VA[ULV]).duration(100)
                .inputItems(wirePrefix, material)
                .outputItems(cablePrefix, material);

        if (voltageTier > UEV) {
            builder.inputItems(GTLItems.HIGHLY_INSULATING_FOIL.asStack(insulationAmount));
        }

        // Apply a Polyphenylene Sulfate Foil if LuV or above.
        if (voltageTier >= LuV) {
            builder.inputItems(foil, PolyphenyleneSulfide, insulationAmount);
        }

        // Apply a PVC Foil if EV or above.
        if (voltageTier >= EV) {
            builder.inputItems(foil, PolyvinylChloride, insulationAmount);
        }

        builder.inputFluids(GTLRecipeContents.fluid(SiliconeRubber, L * (long) insulationAmount / 2))
                .save(provider);

        // Styrene Butadiene Rubber Recipe (all cables)
        builder = ASSEMBLER_RECIPES
                .recipeBuilder("cover_" + material.getName() + "_" + wirePrefix + "_styrene_butadiene")
                .EUt(VA[ULV]).duration(100)
                .inputItems(wirePrefix, material)
                .outputItems(cablePrefix, material);

        if (voltageTier > UEV) {
            builder.inputItems(GTLItems.HIGHLY_INSULATING_FOIL.asStack(insulationAmount));
        }

        // Apply a Polyphenylene Sulfate Foil if LuV or above.
        if (voltageTier >= LuV) {
            builder.inputItems(foil, PolyphenyleneSulfide, insulationAmount);
        }

        // Apply a PVC Foil if EV or above.
        if (voltageTier >= EV) {
            builder.inputItems(foil, PolyvinylChloride, insulationAmount);
        }

        builder.inputFluids(GTLRecipeContents.fluid(StyreneButadieneRubber, L * (long) insulationAmount / 4))
                .save(provider);
    }
}
