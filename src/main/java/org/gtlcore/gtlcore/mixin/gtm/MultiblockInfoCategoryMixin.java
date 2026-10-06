package org.gtlcore.gtlcore.mixin.gtm;

import org.gtlcore.gtlcore.api.gui.NativePatternPreviewWidget;
import org.gtlcore.gtlcore.api.gui.preview.NativePreviewShapes;
import org.gtlcore.gtlcore.integration.jei.NativePreviewJeiSlots;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.integration.recipeviewer.jei.MultiblockInfoJeiCategory;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.integration.jei.ModularUIJeiPlugin;
import brachy.modularui.integration.jei.recipe.ModularUIJeiCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.List;
import java.util.function.Function;

@Mixin(value = MultiblockInfoJeiCategory.class, remap = false)
public abstract class MultiblockInfoCategoryMixin extends ModularUIJeiCategory<MultiblockMachineDefinition> {
    protected MultiblockInfoCategoryMixin(Function<MultiblockMachineDefinition, IWidget> ui,
                                          Function<MultiblockMachineDefinition, ResourceLocation> id) {
        super(ui, id);
    }

    /** @author GTLCore @reason Preserve the production preview's fixed native recipe dimensions. */
    @Overwrite public int getMaxWidth() { return 160; }
    /** @author GTLCore @reason Preserve the production preview's fixed native recipe dimensions. */
    @Overwrite public int getMaxHeight() { return 160; }

    /** @author GTLCore @reason Include custom registered visual shells in native JEI ingredient lookups. */
    @Overwrite public void setupRecipeIngredients(IRecipeLayoutBuilder builder, MultiblockMachineDefinition definition,
                                                 IFocusGroup focuses) {
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
                .addIngredient(VanillaTypes.ITEM_STACK, definition.asStack());
        var pages = NativePreviewShapes.get(definition);
        if (pages.isEmpty()) return;
        var inputs = builder.addInvisibleIngredients(RecipeIngredientRole.INPUT);
        for (var state : pages.getFirst().states().values()) {
            var stack = new ItemStack(state.getBlock().asItem());
            if (stack.isEmpty() && !state.getFluidState().isEmpty()) stack = new ItemStack(state.getFluidState().getType().getBucket());
            if (!stack.isEmpty()) inputs.addIngredient(VanillaTypes.ITEM_STACK, stack);
        }
    }

    @Override public IWidget transformWidget(IRecipeExtrasBuilder builder, IWidget widget) {
        if (widget instanceof NativePatternPreviewWidget preview)
            builder.addSlottedWidget(new NativePreviewJeiSlots(preview, ModularUIJeiPlugin.jeiHelpers.getGuiHelper()), List.of());
        return super.transformWidget(builder, widget);
    }
}
