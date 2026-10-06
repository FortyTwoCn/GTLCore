package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.NativeContentState;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.data.GTLCallbackMaterials;
import com.gtladd.gtladditions.common.recipe.GTLAddRecipesTypes;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import com.gregtechceu.gtceu.integration.recipeviewer.jei.GTJEIPlugin;
import com.gregtechceu.gtceu.integration.recipeviewer.jei.recipe.GTRecipeJEICategory;
import mezz.jei.api.recipe.RecipeType;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;

/** Opens actual production recipe layouts on the native client; never included in the release source set. */
@EventBusSubscriber(modid = "gtlcore_recipe_test", value = Dist.CLIENT)
public final class NativeRecipeClientFixtures {
    private static boolean opened;
    @SubscribeEvent public static void ready(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (!opened && Boolean.getBoolean("gtlcore.recipeValidation.enabled") && minecraft.level != null &&
                minecraft.screen == null && GTJEIPlugin.getRuntime() != null) {
            opened = true;
            open();
        }
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("gtlcore_recipe_test", path); }
    private static GTRecipe recipe(int page) {
        if (page == 0) {
            var builder = GTLRecipeTypes.DIMENSIONAL_FOCUS_ENGRAVING_ARRAY_RECIPES.recipeBuilder(id("client_long_item"))
                    .inputItems(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 9_007_199_254_740_993L))
                    .outputItems(LongIngredient.create(Ingredient.of(Items.DIAMOND), 3_000_000_002L))
                    .inputFluids(GTLCallbackMaterials.Photoresist.asSingleFluidIngredient(7)).duration(1000).EUt(32);
            var output = builder.output.get(ItemRecipeCapability.CAP).getFirst();
            builder.output.get(ItemRecipeCapability.CAP).set(0,
                    NativeContentState.create(output.content(), 5000, 10000, -1000, null, null));
            return builder.build();
        }
        if (page == 1) return GTLRecipeTypes.HEAT_EXCHANGER_RECIPES.recipeBuilder(id("client_long_fluid"))
                .inputFluids(LongFluidIngredient.create(Fluids.WATER, 4_096_000_000L))
                .outputFluids(LongFluidIngredient.create(GTLCallbackMaterials.SupercriticalSteam.getFluid(), 4_096_000_000L))
                .addData("eu", 4_096_000_000L).duration(200).build();
        if (page == 2) return GTLAddRecipesTypes.CHAOTIC_ALCHEMY.recipeBuilder(id("client_alchemy_coils"))
                .inputItems(Items.IRON_INGOT).outputFluids(GTMaterials.Iron.asSingleFluidIngredient(144))
                .blastFurnaceTemp(1800).duration(200).EUt(128).build();
        return GTLAddRecipesTypes.MATTER_EXOTIC.recipeBuilder(id("client_catalyst_icon"))
                .inputItems(Items.IRON_INGOT).inputFluids(GTMaterials.Helium.asSingleFluidIngredient(1000))
                .outputItems(Items.DIAMOND).duration(100).EUt(32).build();
    }
    private static void open() {
        var runtime = GTJEIPlugin.getRuntime();
        var types = new ArrayList<RecipeType<?>>();
        for (int page = 0; page < 4; page++) {
            var selected = recipe(page);
            var type = GTRecipeJEICategory.TYPES.apply(selected.recipeCategory);
            var existing = runtime.getRecipeManager().createRecipeLookup(type).get().toList();
            runtime.getRecipeManager().hideRecipes(type, existing);
            runtime.getRecipeManager().addRecipes(type, List.of(selected));
            types.add(type);
            LogManager.getLogger().info("GTL_NATIVE_RECIPE_CLIENT_FIXTURE={} id={} type={}", page, selected.id, type.getUid());
        }
        runtime.getRecipesGui().showTypes(types);
    }
}
