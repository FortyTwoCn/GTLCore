package org.gtlcore.gtlcore.mixin.gtm.recipe;

import org.gtlcore.gtlcore.common.recipe.GTLRecipeContents;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.OreProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gregtechceu.gtceu.data.recipe.generated.OreRecipeHandler;

import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Unique;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.HIGH_SIFTER_OUTPUT;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.*;
import static com.gregtechceu.gtceu.common.data.GTMaterials.DistilledWater;
import static com.gregtechceu.gtceu.common.data.GTMaterials.Stone;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.*;
import static org.gtlcore.gtlcore.common.data.GTLRecipeTypes.INTEGRATED_ORE_PROCESSOR;

@Mixin(OreRecipeHandler.class)
public abstract class OreRecipeHandlerMixin {

    // Native GT invokes these processors once per material. Keep its downstream crushed/dust processors.
    @WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/data/recipe/generated/OreRecipeHandler;processOre(Lnet/minecraft/data/recipes/RecipeOutput;Lcom/gregtechceu/gtceu/api/data/tag/TagPrefix;Lcom/gregtechceu/gtceu/api/data/chemical/material/properties/OreProperty;Lcom/gregtechceu/gtceu/api/data/chemical/material/Material;)V"), remap = false)
    private static void gtlcore$nativeOre(RecipeOutput provider, TagPrefix prefix, OreProperty property,
                                         Material material, Operation<Void> original) {
        Supplier<Material> stone = ORES.get(prefix).material();
        if (stone != null && stone.get() == GTMaterials.Stone && material.shouldGenerateRecipesFor(prefix)) {
            gtlcore$processOre(prefix, material, property, provider);
            gtlcore$processOreForgeHammer(prefix, material, property, provider);
        }
    }

    @WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/data/recipe/generated/OreRecipeHandler;processRawOre(Lnet/minecraft/data/recipes/RecipeOutput;Lcom/gregtechceu/gtceu/api/data/chemical/material/properties/OreProperty;Lcom/gregtechceu/gtceu/api/data/chemical/material/Material;)V"), remap = false)
    private static void gtlcore$nativeRawOre(RecipeOutput provider, OreProperty property,
                                            Material material, Operation<Void> original) {
        if (material.shouldGenerateRecipesFor(rawOre)) gtlcore$processRawOre(rawOre, material, property, provider);
    }

    @Unique
    private static void gtlcore$processOreForgeHammer(TagPrefix orePrefix, Material material, OreProperty property, RecipeOutput provider) {
        ItemStack crushedStack = ChemicalHelper.get(crushed, material);
        int amountOfCrushedOre = property.getOreMultiplier();
        int oreTypeMultiplier = org.gtlcore.gtlcore.config.ConfigHolder.INSTANCE.oreMultiplier;
        crushedStack.setCount(crushedStack.getCount() * property.getOreMultiplier());

        GTRecipeBuilder builder = FORGE_HAMMER_RECIPES
                .recipeBuilder("hammer_" + material.getName() + "_ore_to_raw_ore")
                .inputItems(orePrefix.getItemTags(material).getFirst())
                .duration(10).EUt(16);
        if (material.hasProperty(PropertyKey.GEM) && !gem.isIgnored(material)) {
            builder.outputItems(ChemicalHelper.get(gem, material, crushedStack.getCount())
                    .copyWithCount(amountOfCrushedOre * oreTypeMultiplier));
        } else {
            builder.outputItems(crushedStack.copyWithCount(amountOfCrushedOre * oreTypeMultiplier));
        }
        builder.save(provider);
    }

    @Unique
    private static void gtlcore$processOre(TagPrefix orePrefix, Material material, OreProperty property, RecipeOutput provider) {
        TagKey<Item> tag = orePrefix.getItemTags(material).getFirst();
        Material byproductMaterial = property.getOreByProduct(0, material);
        ItemStack ingotStack;
        ItemStack byproductStack = ChemicalHelper.get(gem, byproductMaterial);
        if (byproductStack.isEmpty()) byproductStack = ChemicalHelper.get(dust, byproductMaterial);
        Material smeltingMaterial = property.getDirectSmeltResult() == null ? material :
                property.getDirectSmeltResult();
        ItemStack crushedStack = ChemicalHelper.get(crushed, material);
        int amountOfCrushedOre = property.getOreMultiplier();
        if (smeltingMaterial.hasProperty(PropertyKey.INGOT)) {
            ingotStack = ChemicalHelper.get(ingot, smeltingMaterial);
        } else if (smeltingMaterial.hasProperty(PropertyKey.GEM)) {
            ingotStack = ChemicalHelper.get(gem, smeltingMaterial);
        } else {
            ingotStack = ChemicalHelper.get(dust, smeltingMaterial);
        }
        int oreTypeMultiplier = org.gtlcore.gtlcore.config.ConfigHolder.INSTANCE.oreMultiplier;
        ingotStack.setCount(ingotStack.getCount() * property.getOreMultiplier() * oreTypeMultiplier);
        crushedStack.setCount(crushedStack.getCount() * property.getOreMultiplier());

        if (!crushedStack.isEmpty()) {
            GTRecipeBuilder builder = GTLRecipeContents.chancedOutput(MACERATOR_RECIPES
                    .recipeBuilder("macerate_" + material.getName() + "_ore_to_raw_ore")
                    .inputItems(tag)
                    .outputItems(crushedStack.copyWithCount(amountOfCrushedOre * 2 * oreTypeMultiplier)), byproductStack, 1400, 850)
                    .EUt(30)
                    .duration(26);

            for (MaterialStack secondaryMaterial : orePrefix.secondaryMaterials()) {
                if (secondaryMaterial.material().hasProperty(PropertyKey.DUST)) {
                    ItemStack dustStack = ChemicalHelper.getGem(secondaryMaterial);
                    {
                        var gtlRecipeBuilder118 = builder;
                        GTLRecipeContents.chancedOutput(gtlRecipeBuilder118, dustStack, 6700, 800);
                    }
                }
            }

            builder.save(provider);

            int crushedAmount = amountOfCrushedOre * 2 * oreTypeMultiplier;

            // 1 研磨-研磨-离心
            GTRecipeBuilder opBuilder1 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                    .recipeBuilder("integrated_ore_processor_1_" + material.getName())
                    .circuitMeta(1)
                    .inputItems(tag)
                    .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1400, 850), ChemicalHelper.get(dust, byproductMaterial,
                            property.getByProductMultiplier() * crushedAmount), 1400, 850) // 第二步副产
                    .duration((int) (26 + (26 + (material.getMass() * 4)) * crushedAmount))
                    .EUt(30);
            for (MaterialStack secondaryMaterial : orePrefix.secondaryMaterials()) {
                if (secondaryMaterial.material().hasProperty(PropertyKey.DUST)) {
                    ItemStack dustStack = ChemicalHelper.getGem(secondaryMaterial).copyWithCount(crushedAmount);
                    {
                        var gtlRecipeBuilder141 = opBuilder1;
                        GTLRecipeContents.chancedOutput(gtlRecipeBuilder141, dustStack, 6700, 800);
                    }
                }
            }
            Material byproduct1 = property.getOreByProduct(0, material);
            if (byproduct1.hasProperty(PropertyKey.DUST)) {
                {
                    var gtlRecipeBuilder146 = opBuilder1;
                    GTLRecipeContents.chancedOutput(gtlRecipeBuilder146, TagPrefix.dust, byproduct1, crushedAmount, "1/9", 0);
                } // 第三步副产
            }
            opBuilder1.save(provider);

            // 2 研磨-洗矿-热离-研磨
            Material byproductMaterial1 = property.getOreByProduct(1, material);
            ItemStack byproductStack2 = ChemicalHelper.get(dust, property.getOreByProduct(2, material), crushedAmount);
            ItemStack crushedCentrifugedStack = ChemicalHelper.get(crushedRefined, material);
            if (!crushedCentrifugedStack.isEmpty()) {
                GTRecipeBuilder opBuilder2 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                        .recipeBuilder("integrated_ore_processor_2_" + material.getName())
                        .circuitMeta(2)
                        .inputItems(tag)
                        .inputFluids(GTLRecipeContents.fluid(DistilledWater, 100L * crushedAmount))
                        .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1400, 850), TagPrefix.dust, byproductMaterial, crushedAmount, "1/3", 0) // 第二步副产
                        .outputItems(TagPrefix.dust, GTMaterials.Stone, crushedAmount), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/3", 0), byproductStack2, 1400, 850) // 第四步副产
                        .duration(26 + (200 + 200 + 26) * crushedAmount)
                        .EUt(30);
                for (MaterialStack secondaryMaterial : orePrefix.secondaryMaterials()) {
                    if (secondaryMaterial.material().hasProperty(PropertyKey.DUST)) {
                        ItemStack dustStack = ChemicalHelper.getGem(secondaryMaterial).copyWithCount(crushedAmount);
                        {
                            var gtlRecipeBuilder173 = opBuilder2;
                            GTLRecipeContents.chancedOutput(gtlRecipeBuilder173, dustStack, 6700, 800);
                        }
                    }
                }
                opBuilder2.save(provider);
            }

            // 3 研磨-洗矿-研磨-离心
            ItemStack byproductStack1 = ChemicalHelper.get(dust, byproductMaterial1, crushedAmount);
            GTRecipeBuilder opBuilder3 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                    .recipeBuilder("integrated_ore_processor_3_" + material.getName())
                    .circuitMeta(3)
                    .inputItems(tag)
                    .inputFluids(GTLRecipeContents.fluid(DistilledWater, 100L * crushedAmount))
                    .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1400, 850), TagPrefix.dust, byproductMaterial, crushedAmount, "1/3", 0) // 第二步副产
                    .outputItems(TagPrefix.dust, GTMaterials.Stone, crushedAmount), byproductStack1, 1400, 850), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/9", 0) // 第四步副产
                    .duration(26 + (200 + 26 + 16) * crushedAmount)
                    .EUt(30);
            for (MaterialStack secondaryMaterial : orePrefix.secondaryMaterials()) {
                if (secondaryMaterial.material().hasProperty(PropertyKey.DUST)) {
                    ItemStack dustStack = ChemicalHelper.getGem(secondaryMaterial).copyWithCount(crushedAmount);
                    {
                        var gtlRecipeBuilder197 = opBuilder3;
                        GTLRecipeContents.chancedOutput(gtlRecipeBuilder197, dustStack, 6700, 800);
                    }
                }
            }
            opBuilder3.save(provider);

            // 4 研磨-洗矿-筛选-离心
            if (material.hasProperty(PropertyKey.GEM)) {
                ItemStack exquisiteStack = ChemicalHelper.get(gemExquisite, material, crushedAmount);
                ItemStack flawlessStack = ChemicalHelper.get(gemFlawless, material, crushedAmount);
                ItemStack gemStack = ChemicalHelper.get(gem, material, crushedAmount);
                if (material.hasFlag(HIGH_SIFTER_OUTPUT)) {
                    GTRecipeBuilder opBuilder4 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                            .recipeBuilder("integrated_ore_processor_4_" + material.getName())
                            .circuitMeta(4)
                            .inputItems(tag)
                            .inputFluids(GTLRecipeContents.fluid(DistilledWater, 100L * crushedAmount))
                            .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1400, 850), TagPrefix.dust, byproductMaterial, crushedAmount, "1/3", 0) // 第二步副产
                            .outputItems(TagPrefix.dust, GTMaterials.Stone, crushedAmount), exquisiteStack, 500, 150), flawlessStack, 1500, 200), gemStack, 5000, 1000), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/9", 0) // 第四步副产
                            .duration(26 + (200 + 210 + 16) * crushedAmount)
                            .EUt(30);
                    for (MaterialStack secondaryMaterial : orePrefix.secondaryMaterials()) {
                        if (secondaryMaterial.material().hasProperty(PropertyKey.DUST)) {
                            ItemStack dustStack = ChemicalHelper.getGem(secondaryMaterial).copyWithCount(crushedAmount);
                            {
                                var gtlRecipeBuilder226 = opBuilder4;
                                GTLRecipeContents.chancedOutput(gtlRecipeBuilder226, dustStack, 6700, 800);
                            }
                        }
                    }
                    opBuilder4.save(provider);
                } else {
                    GTRecipeBuilder opBuilder4 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                            .recipeBuilder("integrated_ore_processor_4_" + material.getName())
                            .circuitMeta(4)
                            .inputItems(tag)
                            .inputFluids(GTLRecipeContents.fluid(DistilledWater, 100L * crushedAmount))
                            .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1400, 850), TagPrefix.dust, byproductMaterial, crushedAmount, "1/3", 0) // 第二步副产
                            .outputItems(TagPrefix.dust, GTMaterials.Stone, crushedAmount), exquisiteStack, 300, 100), flawlessStack, 1000, 150), gemStack, 3500, 500), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/9", 0) // 第四步副产
                            .duration(26 + (200 + 210 + 16) * crushedAmount)
                            .EUt(30);
                    for (MaterialStack secondaryMaterial : orePrefix.secondaryMaterials()) {
                        if (secondaryMaterial.material().hasProperty(PropertyKey.DUST)) {
                            ItemStack dustStack = ChemicalHelper.getGem(secondaryMaterial).copyWithCount(crushedAmount);
                            {
                                var gtlRecipeBuilder249 = opBuilder4;
                                GTLRecipeContents.chancedOutput(gtlRecipeBuilder249, dustStack, 6700, 800);
                            }
                        }
                    }
                    opBuilder4.save(provider);
                }
            }
            if (property.getWashedIn().left() != null) {
                Material washingByproduct = property.getOreByProduct(3, material);
                var washedInTuple = property.getWashedIn();
                // 5 研磨-浸洗-热离-研磨
                if (!crushedCentrifugedStack.isEmpty()) {
                    GTRecipeBuilder opBuilder5 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                            .recipeBuilder("integrated_ore_processor_5_" + material.getName())
                            .circuitMeta(5)
                            .inputItems(tag)
                            .inputFluids(GTLRecipeContents.fluid(washedInTuple.left(), washedInTuple.rightInt() * crushedAmount))
                            .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1400, 850), ChemicalHelper.get(dust, washingByproduct,
                                    property.getByProductMultiplier() * crushedAmount), 7000, 580), ChemicalHelper.get(dust, Stone, crushedAmount), 4000, 650), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/3", 0), byproductStack2, 1400, 850) // 第四步副产
                            .duration(26 + (200 + 200 + 26) * crushedAmount)
                            .EUt(30);
                    for (MaterialStack secondaryMaterial : orePrefix.secondaryMaterials()) {
                        if (secondaryMaterial.material().hasProperty(PropertyKey.DUST)) {
                            ItemStack dustStack = ChemicalHelper.getGem(secondaryMaterial).copyWithCount(crushedAmount);
                            {
                                var gtlRecipeBuilder279 = opBuilder5;
                                GTLRecipeContents.chancedOutput(gtlRecipeBuilder279, dustStack, 6700, 800);
                            }
                        }
                    }
                    opBuilder5.save(provider);
                }

                // 6 研磨-浸洗-研磨-离心
                GTRecipeBuilder opBuilder6 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                        .recipeBuilder("integrated_ore_processor_6_" + material.getName())
                        .circuitMeta(6)
                        .inputItems(tag)
                        .inputFluids(GTLRecipeContents.fluid(washedInTuple.left(), washedInTuple.rightInt() * crushedAmount))
                        .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1400, 850), ChemicalHelper.get(dust, washingByproduct,
                                property.getByProductMultiplier() * crushedAmount), 7000, 580), ChemicalHelper.get(dust, Stone, crushedAmount), 4000, 650), byproductStack1, 1400, 850), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/9", 0) // 第四步副产
                        .duration(26 + (200 + 26 + 16) * crushedAmount)
                        .EUt(30);
                for (MaterialStack secondaryMaterial : orePrefix.secondaryMaterials()) {
                    if (secondaryMaterial.material().hasProperty(PropertyKey.DUST)) {
                        ItemStack dustStack = ChemicalHelper.getGem(secondaryMaterial).copyWithCount(crushedAmount);
                        {
                            var gtlRecipeBuilder304 = opBuilder6;
                            GTLRecipeContents.chancedOutput(gtlRecipeBuilder304, dustStack, 6700, 800);
                        }
                    }
                }
                opBuilder6.save(provider);

                // 7 研磨-浸洗-筛选-离心
                if (material.hasProperty(PropertyKey.GEM)) {
                    ItemStack exquisiteStack = ChemicalHelper.get(gemExquisite, material, crushedAmount);
                    ItemStack flawlessStack = ChemicalHelper.get(gemFlawless, material, crushedAmount);
                    ItemStack gemStack = ChemicalHelper.get(gem, material, crushedAmount);
                    if (material.hasFlag(HIGH_SIFTER_OUTPUT)) {
                        GTRecipeBuilder opBuilder7 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                                .recipeBuilder("integrated_ore_processor_7_" + material.getName())
                                .circuitMeta(7)
                                .inputItems(tag)
                                .inputFluids(GTLRecipeContents.fluid(washedInTuple.left(), washedInTuple.rightInt() * crushedAmount))
                                .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1400, 850), ChemicalHelper.get(dust, washingByproduct,
                                        property.getByProductMultiplier() * crushedAmount), 7000, 580), ChemicalHelper.get(dust, Stone, crushedAmount), 4000, 650), exquisiteStack, 500, 150), flawlessStack, 1500, 200), gemStack, 5000, 1000), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/9", 0) // 第四步副产
                                .duration(26 + (200 + 210 + 16) * crushedAmount)
                                .EUt(30);
                        for (MaterialStack secondaryMaterial : orePrefix.secondaryMaterials()) {
                            if (secondaryMaterial.material().hasProperty(PropertyKey.DUST)) {
                                ItemStack dustStack = ChemicalHelper.getGem(secondaryMaterial)
                                        .copyWithCount(crushedAmount);
                                {
                                    var gtlRecipeBuilder337 = opBuilder7;
                                    GTLRecipeContents.chancedOutput(gtlRecipeBuilder337, dustStack, 6700, 800);
                                }
                            }
                        }
                        opBuilder7.save(provider);
                    } else {
                        GTRecipeBuilder opBuilder7 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                                .recipeBuilder("integrated_ore_processor_7_" + material.getName())
                                .circuitMeta(7)
                                .inputItems(tag)
                                .inputFluids(GTLRecipeContents.fluid(washedInTuple.left(), washedInTuple.rightInt() * crushedAmount))
                                .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1400, 850), ChemicalHelper.get(dust, washingByproduct,
                                        property.getByProductMultiplier() * crushedAmount), 7000, 580), ChemicalHelper.get(dust, Stone, crushedAmount), 4000, 650), exquisiteStack, 300, 100), flawlessStack, 1000, 150), gemStack, 3500, 500), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/9", 0) // 第四步副产
                                .duration(26 + (200 + 210 + 16) * crushedAmount)
                                .EUt(30);
                        for (MaterialStack secondaryMaterial : orePrefix.secondaryMaterials()) {
                            if (secondaryMaterial.material().hasProperty(PropertyKey.DUST)) {
                                ItemStack dustStack = ChemicalHelper.getGem(secondaryMaterial)
                                        .copyWithCount(crushedAmount);
                                {
                                    var gtlRecipeBuilder364 = opBuilder7;
                                    GTLRecipeContents.chancedOutput(gtlRecipeBuilder364, dustStack, 6700, 800);
                                }
                            }
                        }
                        opBuilder7.save(provider);
                    }
                }
            }
        }

        // do not try to add smelting recipes for materials which require blast furnace
        if (!ingotStack.isEmpty() && gtlcore$doesMaterialUseNormalFurnace(smeltingMaterial) && !orePrefix.isIgnored(material)) {
            float xp = Math.round(((1 + oreTypeMultiplier * 0.5f) * 0.5f - 0.05f) * 10f) / 10f;
            VanillaRecipeHelper.addSmeltingRecipe(provider,
                    "smelt_" + material.getName() + "_ore_to_ingot", tag, ingotStack, xp);
            VanillaRecipeHelper.addBlastingRecipe(provider,
                    "smelt_" + material.getName() + "_ore_to_ingot", tag, ingotStack, xp);
        }
    }

    @Unique
    private static void gtlcore$processRawOre(TagPrefix orePrefix, Material material, OreProperty property, RecipeOutput provider) {
        ItemStack crushedStack = ChemicalHelper.get(crushed, material,
                material.getProperty(PropertyKey.ORE).getOreMultiplier() * org.gtlcore.gtlcore.config.ConfigHolder.INSTANCE.oreMultiplier / 2);
        ItemStack ingotStack;
        Material smeltingMaterial = property.getDirectSmeltResult() == null ? material :
                property.getDirectSmeltResult();
        if (smeltingMaterial.hasProperty(PropertyKey.INGOT)) {
            ingotStack = ChemicalHelper.get(ingot, smeltingMaterial,
                    material.getProperty(PropertyKey.ORE).getOreMultiplier());
        } else if (smeltingMaterial.hasProperty(PropertyKey.GEM)) {
            ingotStack = ChemicalHelper.get(gem, smeltingMaterial,
                    material.getProperty(PropertyKey.ORE).getOreMultiplier());
        } else {
            ingotStack = ChemicalHelper.get(dust, smeltingMaterial,
                    material.getProperty(PropertyKey.ORE).getOreMultiplier());
        }

        if (!crushedStack.isEmpty()) {
            GTRecipeBuilder builder = FORGE_HAMMER_RECIPES
                    .recipeBuilder("hammer_" + orePrefix.name + "_" + material.getName() + "_to_crushed_ore")
                    .inputItems(orePrefix, material)
                    .duration(10).EUt(16);
            if (material.hasProperty(PropertyKey.GEM) && !gem.isIgnored(material)) {
                builder.outputItems(ChemicalHelper.get(gem, material, crushedStack.getCount()));
            } else {
                builder.outputItems(crushedStack.copy());
            }
            builder.save(provider);

            GTRecipeBuilder builder2 = MACERATOR_RECIPES
                    .recipeBuilder("macerate_" + orePrefix.name + "_" + material.getName() + "_ore_to_crushed_ore")
                    .inputItems(orePrefix, material)
                    .outputItems(crushedStack.copyWithCount(crushedStack.getCount() * 2))
                    .EUt(30)
                    .duration(26);

            Material byproductMaterial = property.getOreByProduct(0, material);
            ItemStack byproductStack = ChemicalHelper.get(gem, byproductMaterial);
            if (byproductStack.isEmpty()) {
                byproductStack = ChemicalHelper.get(dust, byproductMaterial);
            }
            {
                var gtlRecipeBuilder426 = builder2;
                GTLRecipeContents.chancedOutput(gtlRecipeBuilder426, byproductStack, 1000, 300);
            }

            for (MaterialStack secondaryMaterial : ore.secondaryMaterials()) {
                if (secondaryMaterial.material().hasProperty(PropertyKey.DUST)) {
                    ItemStack dustStack = ChemicalHelper.getGem(secondaryMaterial);
                    {
                        var gtlRecipeBuilder431 = builder2;
                        GTLRecipeContents.chancedOutput(gtlRecipeBuilder431, dustStack, 500, 100);
                    }
                    break;
                }
            }
            builder2.save(provider);

            int crushedAmount = crushedStack.getCount() * 2;

            // 1 研磨-研磨-离心
            GTRecipeBuilder opBuilder1 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                    .recipeBuilder("raw_integrated_ore_processor_1_" + material.getName())
                    .circuitMeta(1)
                    .inputItems(orePrefix, material)
                    .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1000, 300), ChemicalHelper.get(dust, byproductMaterial,
                            property.getByProductMultiplier() * crushedAmount), 1400, 850) // 第二步副产
                    .duration((int) (26 + (26 + (material.getMass() * 4)) * crushedAmount))
                    .EUt(30);
            Material byproduct1 = property.getOreByProduct(0, material);
            if (byproduct1.hasProperty(PropertyKey.DUST)) {
                {
                    var gtlRecipeBuilder453 = opBuilder1;
                    GTLRecipeContents.chancedOutput(gtlRecipeBuilder453, TagPrefix.dust, byproduct1, crushedAmount, "1/9", 0);
                } // 第三步副产
            }
            opBuilder1.save(provider);

            // 2 研磨-洗矿-热离-研磨
            Material byproductMaterial1 = property.getOreByProduct(1, material);
            ItemStack byproductStack2 = ChemicalHelper.get(dust, property.getOreByProduct(2, material), crushedAmount);
            ItemStack crushedCentrifugedStack = ChemicalHelper.get(crushedRefined, material);
            if (!crushedCentrifugedStack.isEmpty()) {
                GTRecipeBuilder opBuilder2 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                        .recipeBuilder("raw_integrated_ore_processor_2_" + material.getName())
                        .circuitMeta(2)
                        .inputItems(orePrefix, material)
                        .inputFluids(GTLRecipeContents.fluid(DistilledWater, 100L * crushedAmount))
                        .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1000, 300), TagPrefix.dust, byproductMaterial, crushedAmount, "1/3", 0) // 第二步副产
                        .outputItems(TagPrefix.dust, GTMaterials.Stone, crushedAmount), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/3", 0), byproductStack2, 1400, 850) // 第四步副产
                        .duration(26 + (200 + 200 + 26) * crushedAmount)
                        .EUt(30);
                opBuilder2.save(provider);
            }

            // 3 研磨-洗矿-研磨-离心
            ItemStack byproductStack1 = ChemicalHelper.get(dust, byproductMaterial1, crushedAmount);
            GTRecipeBuilder opBuilder3 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                    .recipeBuilder("raw_integrated_ore_processor_3_" + material.getName())
                    .circuitMeta(3)
                    .inputItems(orePrefix, material)
                    .inputFluids(GTLRecipeContents.fluid(DistilledWater, 100L * crushedAmount))
                    .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1000, 300), TagPrefix.dust, byproductMaterial, crushedAmount, "1/3", 0) // 第二步副产
                    .outputItems(TagPrefix.dust, GTMaterials.Stone, crushedAmount), byproductStack1, 1400, 850), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/9", 0) // 第四步副产
                    .duration(26 + (200 + 26 + 16) * crushedAmount)
                    .EUt(30);
            for (MaterialStack secondaryMaterial : ore.secondaryMaterials()) {
                if (secondaryMaterial.material().hasProperty(PropertyKey.DUST)) {
                    ItemStack dustStack = ChemicalHelper.getGem(secondaryMaterial);
                    {
                        var gtlRecipeBuilder498 = opBuilder3;
                        GTLRecipeContents.chancedOutput(gtlRecipeBuilder498, dustStack, 500, 100);
                    }
                    break;
                }
            }
            opBuilder3.save(provider);

            // 4 研磨-洗矿-筛选-离心
            if (material.hasProperty(PropertyKey.GEM)) {
                ItemStack exquisiteStack = ChemicalHelper.get(gemExquisite, material, crushedAmount);
                ItemStack flawlessStack = ChemicalHelper.get(gemFlawless, material, crushedAmount);
                ItemStack gemStack = ChemicalHelper.get(gem, material, crushedAmount);
                if (material.hasFlag(HIGH_SIFTER_OUTPUT)) {
                    GTRecipeBuilder opBuilder4 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                            .recipeBuilder("raw_integrated_ore_processor_4_" + material.getName())
                            .circuitMeta(4)
                            .inputItems(orePrefix, material)
                            .inputFluids(GTLRecipeContents.fluid(DistilledWater, 100L * crushedAmount))
                            .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1000, 300), TagPrefix.dust, byproductMaterial, crushedAmount, "1/3", 0) // 第二步副产
                            .outputItems(TagPrefix.dust, GTMaterials.Stone, crushedAmount), exquisiteStack, 500, 150), flawlessStack, 1500, 200), gemStack, 5000, 1000), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/9", 0) // 第四步副产
                            .duration(26 + (200 + 210 + 16) * crushedAmount)
                            .EUt(30);
                    opBuilder4.save(provider);
                } else {
                    GTRecipeBuilder opBuilder4 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                            .recipeBuilder("raw_integrated_ore_processor_4_" + material.getName())
                            .circuitMeta(4)
                            .inputItems(orePrefix, material)
                            .inputFluids(GTLRecipeContents.fluid(DistilledWater, 100L * crushedAmount))
                            .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1000, 300), TagPrefix.dust, byproductMaterial, crushedAmount, "1/3", 0) // 第二步副产
                            .outputItems(TagPrefix.dust, GTMaterials.Stone, crushedAmount), exquisiteStack, 300, 100), flawlessStack, 1000, 150), gemStack, 3500, 500), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/9", 0) // 第四步副产
                            .duration(26 + (200 + 210 + 16) * crushedAmount)
                            .EUt(30);
                    opBuilder4.save(provider);
                }
            }
            if (property.getWashedIn().left() != null) {
                Material washingByproduct = property.getOreByProduct(3, material);
                var washedInTuple = property.getWashedIn();
                // 5 研磨-浸洗-热离-研磨
                if (!crushedCentrifugedStack.isEmpty()) {
                    GTRecipeBuilder opBuilder5 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                            .recipeBuilder("raw_integrated_ore_processor_5_" + material.getName())
                            .circuitMeta(5)
                            .inputItems(orePrefix, material)
                            .inputFluids(GTLRecipeContents.fluid(washedInTuple.left(), washedInTuple.rightInt() * crushedAmount))
                            .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1000, 300), ChemicalHelper.get(dust, washingByproduct,
                                    property.getByProductMultiplier() * crushedAmount), 7000, 580), ChemicalHelper.get(dust, Stone, crushedAmount), 4000, 650), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/3", 0), byproductStack2, 1400, 850) // 第四步副产
                            .duration(26 + (200 + 200 + 26) * crushedAmount)
                            .EUt(30);
                    opBuilder5.save(provider);
                }

                // 6 研磨-浸洗-研磨-离心
                GTRecipeBuilder opBuilder6 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                        .recipeBuilder("raw_integrated_ore_processor_6_" + material.getName())
                        .circuitMeta(6)
                        .inputItems(orePrefix, material)
                        .inputFluids(GTLRecipeContents.fluid(washedInTuple.left(), washedInTuple.rightInt() * crushedAmount))
                        .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1000, 300), ChemicalHelper.get(dust, washingByproduct,
                                property.getByProductMultiplier() * crushedAmount), 7000, 580), ChemicalHelper.get(dust, Stone, crushedAmount), 4000, 650), byproductStack1, 1400, 850), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/9", 0) // 第四步副产
                        .duration(26 + (200 + 26 + 16) * crushedAmount)
                        .EUt(30);
                opBuilder6.save(provider);

                // 7 研磨-浸洗-筛选-离心
                if (material.hasProperty(PropertyKey.GEM)) {
                    ItemStack exquisiteStack = ChemicalHelper.get(gemExquisite, material, crushedAmount);
                    ItemStack flawlessStack = ChemicalHelper.get(gemFlawless, material, crushedAmount);
                    ItemStack gemStack = ChemicalHelper.get(gem, material, crushedAmount);
                    if (material.hasFlag(HIGH_SIFTER_OUTPUT)) {
                        GTRecipeBuilder opBuilder7 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                                .recipeBuilder("raw_integrated_ore_processor_7_" + material.getName())
                                .circuitMeta(7)
                                .inputItems(orePrefix, material)
                                .inputFluids(GTLRecipeContents.fluid(washedInTuple.left(), washedInTuple.rightInt() * crushedAmount))
                                .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1000, 300), ChemicalHelper.get(dust, washingByproduct,
                                        property.getByProductMultiplier() * crushedAmount), 7000, 580), ChemicalHelper.get(dust, Stone, crushedAmount), 4000, 650), exquisiteStack, 500, 150), flawlessStack, 1500, 200), gemStack, 5000, 1000), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/9", 0) // 第四步副产
                                .duration(26 + (200 + 210 + 16) * crushedAmount)
                                .EUt(30);
                        opBuilder7.save(provider);
                    } else {
                        GTRecipeBuilder opBuilder7 = GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(GTLRecipeContents.chancedOutput(INTEGRATED_ORE_PROCESSOR
                                .recipeBuilder("raw_integrated_ore_processor_7_" + material.getName())
                                .circuitMeta(7)
                                .inputItems(orePrefix, material)
                                .inputFluids(GTLRecipeContents.fluid(washedInTuple.left(), washedInTuple.rightInt() * crushedAmount))
                                .outputItems(ChemicalHelper.get(dust, material, crushedAmount)), byproductStack, 1000, 300), ChemicalHelper.get(dust, washingByproduct,
                                        property.getByProductMultiplier() * crushedAmount), 7000, 580), ChemicalHelper.get(dust, Stone, crushedAmount), 4000, 650), exquisiteStack, 300, 100), flawlessStack, 1000, 150), gemStack, 3500, 500), TagPrefix.dust, byproductMaterial1, crushedAmount, "1/9", 0) // 第四步副产
                                .duration(26 + (200 + 210 + 16) * crushedAmount)
                                .EUt(30);
                        opBuilder7.save(provider);
                    }
                }
            }
        }

        // do not try to add smelting recipes for materials which require blast furnace, or don't have smelting recipes
        // at all.
        if (!ingotStack.isEmpty() && gtlcore$doesMaterialUseNormalFurnace(smeltingMaterial) && !orePrefix.isIgnored(material)) {
            float xp = Math.round(((1 + property.getOreMultiplier() * 0.33f) / 3) * 10f) / 10f;
            VanillaRecipeHelper.addSmeltingRecipe(provider,
                    "smelt_" + orePrefix.name + "_" + material.getName() + "_ore_to_ingot",
                    ChemicalHelper.getTag(orePrefix, material), ingotStack.copy(),
                    xp);
            VanillaRecipeHelper.addBlastingRecipe(provider,
                    "smelt_" + orePrefix.name + "_" + material.getName() + "_ore_to_ingot",
                    ChemicalHelper.getTag(orePrefix, material), ingotStack.copy(),
                    xp);
        }

        if (!ConfigHolder.INSTANCE.recipes.disableManualCompression) {
            VanillaRecipeHelper.addShapedRecipe(provider, "compress_" + material.getName() + "_to_ore_block",
                    ChemicalHelper.get(rawOreBlock, material),
                    "BBB", "BBB", "BBB",
                    'B', Objects.requireNonNull(ChemicalHelper.getTag(rawOre, material)));
            VanillaRecipeHelper.addShapelessRecipe(provider, "decompress_" + material.getName() + "_from_ore_block",
                    ChemicalHelper.get(rawOre, material, 9),
                    Objects.requireNonNull(ChemicalHelper.getTag(rawOreBlock, material)));
        }
        COMPRESSOR_RECIPES.recipeBuilder("compress_" + material.getName() + "to_ore_block")
                .inputItems(rawOre, material, 9)
                .outputItems(rawOreBlock, material)
                .duration(300).EUt(2).save(provider);

        FORGE_HAMMER_RECIPES.recipeBuilder("decompress_" + material.getName() + "to_raw_ore")
                .inputItems(rawOreBlock, material)
                .outputItems(rawOre, material, 9)
                .circuitMeta(2)
                .duration(300).EUt(2).save(provider);
    }

    @Unique
    private static boolean gtlcore$doesMaterialUseNormalFurnace(Material material) {
        return !material.hasProperty(PropertyKey.BLAST) && !material.hasFlag(MaterialFlags.NO_ORE_SMELTING);
    }
}
