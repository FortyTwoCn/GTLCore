package org.gtlcore.gtlcore.integration.rei;

import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;
import org.gtlcore.gtlcore.api.gui.preview.NativePreviewShapes;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.integration.rei.ModularDisplay;
import com.lowdragmc.lowdraglib.integration.rei.ModularUIDisplayCategory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;

import java.util.LinkedHashSet;

/** Native REI plugin: the GT 8 baseline no longer has its old REI display classes. */
@REIPluginClient
public final class GTLREIPlugin implements REIClientPlugin {
    public static final CategoryIdentifier<MultiblockDisplay> MULTIBLOCK_INFO =
            CategoryIdentifier.of(GTCEu.id("multiblock_info"));

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new MultiblockCategory());
        registry.removePlusButton(MULTIBLOCK_INFO);
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        GTRegistries.MACHINES.stream()
                .filter(MultiblockMachineDefinition.class::isInstance)
                .map(MultiblockMachineDefinition.class::cast)
                .filter(MultiblockMachineDefinition::isRenderXEIPreview)
                .forEach(definition -> registry.add(new MultiblockDisplay(definition), definition));
    }

    @Override
    public void registerEntries(EntryRegistry registry) {
        for (int configuration = 0; configuration <= 32; configuration++) {
            registry.addEntry(EntryStacks.of(IntCircuitBehaviour.stack(configuration)));
        }
    }

    public static final class MultiblockDisplay extends ModularDisplay<WidgetGroup> {
        public MultiblockDisplay(MultiblockMachineDefinition definition) {
            // LDLib scans the initial supplier eagerly. A scene needs a client world, so defer it
            // until REI opens the display; derive ingredient lookups from world-free native shapes.
            super(() -> new WidgetGroup(0, 0, 160, 160), MULTIBLOCK_INFO);
            widget = () -> PatternPreviewWidget.getPatternWidget(definition);
            outputs.add(EntryIngredients.of(definition.asStack()));
            var pages = NativePreviewShapes.get(definition);
            if (pages.isEmpty()) return;
            var items = new LinkedHashSet<Item>();
            for (var state : pages.getFirst().states().values()) {
                ItemStack stack = new ItemStack(state.getBlock().asItem());
                if (stack.isEmpty() && !state.getFluidState().isEmpty()) {
                    stack = new ItemStack(state.getFluidState().getType().getBucket());
                }
                if (!stack.isEmpty() && items.add(stack.getItem())) inputs.add(EntryIngredients.of(stack));
            }
        }
    }

    public static final class MultiblockCategory extends ModularUIDisplayCategory<MultiblockDisplay> {
        @Override public CategoryIdentifier<MultiblockDisplay> getCategoryIdentifier() { return MULTIBLOCK_INFO; }
        @Override public Component getTitle() { return Component.translatable("gtceu.jei.multiblock_info"); }
        @Override public Renderer getIcon() { return EntryStacks.of(GTMultiMachines.ELECTRIC_BLAST_FURNACE.asStack()); }
        @Override public int getDisplayWidth(MultiblockDisplay display) { return 160; }
        @Override public int getDisplayHeight() { return 160; }
    }
}
