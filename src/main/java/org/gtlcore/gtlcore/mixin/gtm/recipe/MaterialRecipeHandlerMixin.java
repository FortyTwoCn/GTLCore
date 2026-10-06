package org.gtlcore.gtlcore.mixin.gtm.recipe;

import org.gtlcore.gtlcore.common.recipe.GTLRecipeContents;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.*;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;
import com.gregtechceu.gtceu.data.recipe.generated.MaterialRecipeHandler;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Locale;
import java.util.function.Consumer;

import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.*;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.*;
import static com.gregtechceu.gtceu.common.data.GTMaterials.DistilledWater;
import static com.gregtechceu.gtceu.common.data.GTMaterials.Water;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.*;
import static org.gtlcore.gtlcore.common.data.GTLRecipeTypes.ELECTRIC_IMPLOSION_COMPRESSOR_RECIPES;

@Mixin(MaterialRecipeHandler.class)
public abstract class MaterialRecipeHandlerMixin {

    @Shadow(remap = false)
    private static void processEBFRecipe(Material material, BlastProperty property, ItemStack output, RecipeOutput provider) {}

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    private static void processDust(RecipeOutput provider, Material mat) {
        TagPrefix dustPrefix = dust;
        if (!mat.shouldGenerateRecipesFor(dustPrefix) || !mat.hasProperty(PropertyKey.DUST)) return;
        String id = "%s_%s_".formatted(FormattingUtil.toLowerCaseUnderscore(dustPrefix.name),
                mat.getName().toLowerCase(Locale.ROOT));
        ItemStack dustStack = ChemicalHelper.get(dustPrefix, mat);
        OreProperty oreProperty = mat.hasProperty(PropertyKey.ORE) ? mat.getProperty(PropertyKey.ORE) : null;
        if (mat.hasProperty(PropertyKey.GEM)) {
            ItemStack gemStack = ChemicalHelper.get(gem, mat);

            if (mat.hasFlag(CRYSTALLIZABLE)) {
                {
                    var gtlRecipeBuilder53 = AUTOCLAVE_RECIPES.recipeBuilder("autoclave_" + id + "_water")
                        .inputItems(dustStack)
                        .inputFluids(Water.getFluid(250));
                    GTLRecipeContents.chancedOutput(gtlRecipeBuilder53, gemStack, 7000, 1000);
                    gtlRecipeBuilder53
                        .duration(1200).EUt(24)
                        .save(provider);
                }

                AUTOCLAVE_RECIPES.recipeBuilder("autoclave_" + id + "_distilled")
                        .inputItems(dustStack)
                        .inputFluids(DistilledWater.getFluid(50))
                        .outputItems(gemStack)
                        .duration(600).EUt(24)
                        .save(provider);
            }

            if (!mat.hasFlag(EXPLOSIVE) && !mat.hasFlag(FLAMMABLE)) {
                {
                    var gtlRecipeBuilder69 = IMPLOSION_RECIPES.recipeBuilder("implode_" + id + "_powderbarrel")
                        .inputItems(dustStack.copyWithCount(4))
                        .outputItems(gemStack.copyWithCount(3));
                    GTLRecipeContents.chancedOutput(gtlRecipeBuilder69, dust, GTMaterials.DarkAsh, 2500, 0);
                    gtlRecipeBuilder69
                        .explosivesType(new ItemStack(GTBlocks.POWDERBARREL, 8))
                        .save(provider);
                }

                {
                    var gtlRecipeBuilder76 = IMPLOSION_RECIPES.recipeBuilder("implode_" + id + "_tnt")
                        .inputItems(dustStack.copyWithCount(4))
                        .outputItems(gemStack.copyWithCount(3));
                    GTLRecipeContents.chancedOutput(gtlRecipeBuilder76, dust, GTMaterials.DarkAsh, 2500, 0);
                    gtlRecipeBuilder76
                        .explosivesAmount(4)
                        .save(provider);
                }

                {
                    var gtlRecipeBuilder83 = IMPLOSION_RECIPES.recipeBuilder("implode_" + id + "_dynamite")
                        .inputItems(dustStack.copyWithCount(4))
                        .outputItems(gemStack.copyWithCount(3));
                    GTLRecipeContents.chancedOutput(gtlRecipeBuilder83, dust, GTMaterials.DarkAsh, 2500, 0);
                    gtlRecipeBuilder83
                        .explosivesType(GTItems.DYNAMITE.asStack(2))
                        .save(provider);
                }

                {
                    var gtlRecipeBuilder90 = IMPLOSION_RECIPES.recipeBuilder("implode_" + id + "_itnt")
                        .inputItems(dustStack.copyWithCount(4))
                        .outputItems(gemStack.copyWithCount(3));
                    GTLRecipeContents.chancedOutput(gtlRecipeBuilder90, dust, GTMaterials.DarkAsh, 2500, 0);
                    gtlRecipeBuilder90
                        .explosivesType(new ItemStack(GTBlocks.INDUSTRIAL_TNT))
                        .save(provider);
                }

                ELECTRIC_IMPLOSION_COMPRESSOR_RECIPES.recipeBuilder("electric_implode_" + id)
                        .inputItems(dustStack.copyWithCount(4))
                        .outputItems(gemStack.copyWithCount(3))
                        .save(provider);
            }

            if (oreProperty != null) {
                Material smeltingResult = oreProperty.getDirectSmeltResult();
                if (smeltingResult != null) {
                    VanillaRecipeHelper.addSmeltingRecipe(provider, id + "_ingot",
                            ChemicalHelper.getTag(dustPrefix, mat), ChemicalHelper.get(ingot, smeltingResult));
                }
            }

        } else if (mat.hasProperty(PropertyKey.INGOT)) {
            if (!mat.hasAnyOfFlags(FLAMMABLE, NO_SMELTING)) {

                boolean hasHotIngot = ingotHot.doGenerateItem(mat);
                ItemStack ingotStack = ChemicalHelper.get(hasHotIngot ? ingotHot : ingot, mat);
                if (ingotStack.isEmpty() && oreProperty != null) {
                    Material smeltingResult = oreProperty.getDirectSmeltResult();
                    if (smeltingResult != null) {
                        ingotStack = ChemicalHelper.get(ingot, smeltingResult);
                    }
                }
                int blastTemp = mat.getBlastTemperature();

                if (blastTemp <= 0) {
                    // smelting magnetic dusts is handled elsewhere
                    if (!mat.hasFlag(IS_MAGNETIC)) {
                        // do not register inputs by ore dict here. Let other mods register their own dust -> ingots
                        VanillaRecipeHelper.addSmeltingRecipe(provider, id + "_demagnetize_from_dust",
                                ChemicalHelper.getTag(dustPrefix, mat), ingotStack);
                    }
                } else {
                    IngotProperty ingotProperty = mat.getProperty(PropertyKey.INGOT);
                    BlastProperty blastProperty = mat.getProperty(PropertyKey.BLAST);

                    processEBFRecipe(mat, blastProperty, ingotStack, provider);

                    if (ingotProperty.getMagneticMaterial() != null) {
                        processEBFRecipe(ingotProperty.getMagneticMaterial(), blastProperty, ingotStack, provider);
                    }
                }
            }
        } else {
            if (mat.hasFlag(GENERATE_PLATE) && !mat.hasFlag(EXCLUDE_PLATE_COMPRESSOR_RECIPE)) {
                COMPRESSOR_RECIPES.recipeBuilder("compress_plate_" + id)
                        .inputItems(dustStack)
                        .outputItems(plate, mat)
                        .save(provider);
            }

            // Some Ores with Direct Smelting Results have neither ingot nor gem properties
            if (oreProperty != null) {
                Material smeltingResult = oreProperty.getDirectSmeltResult();
                if (smeltingResult != null) {
                    ItemStack ingotStack = ChemicalHelper.get(ingot, smeltingResult);
                    if (!ingotStack.isEmpty()) {
                        VanillaRecipeHelper.addSmeltingRecipe(provider, id + "_dust_to_ingot",
                                ChemicalHelper.getTag(dustPrefix, mat), ingotStack);
                    }
                }
            }
        }
    }
}
