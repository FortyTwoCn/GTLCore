package org.gtlcore.gtlcore.client.gui.widget;

import org.gtlcore.gtlcore.integration.ae2.MEFluidUnits;
import org.gtlcore.gtlcore.utils.NumberUtils;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.integration.ae2.gui.*;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import appeng.api.stacks.*;
import brachy.modularui.integration.recipeviewer.entry.EntryList;
import brachy.modularui.integration.recipeviewer.entry.item.ItemStackList;
import brachy.modularui.integration.recipeviewer.entry.fluid.FluidStackList;
import brachy.modularui.integration.recipeviewer.handlers.IngredientProvider;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.Widget;
import brachy.modularui.widget.scroll.VerticalScrollData;
import brachy.modularui.widgets.DynamicSyncedWidget;
import brachy.modularui.api.drawable.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/** Native read-only output list. AE2's registry-aware codec carries complete keys and exact long quantities. */
public final class MEOutListGridWidget extends DynamicSyncedWidget<MEOutListGridWidget> {
    private final AEKeyStorageSyncHandler contents;

    public MEOutListGridWidget(KeyStorage buffer, PanelSyncManager manager, String syncName, int visibleRows) {
        if (visibleRows < 1) throw new IllegalArgumentException("Output list must have a visible row");
        contents = new AEKeyStorageSyncHandler(buffer);
        manager.syncValue(syncName, contents);
        int[] savedScroll = {0};
        int height = visibleRows * 18;
        var rows = new DynamicLinkedSyncHandler<>(contents).widgetProvider((panel, value) -> {
            var entries = value.getValue();
            if (entries.isEmpty()) return Text.lang("gtceu.gui.waiting_list_empty").asWidget().size(158, height);
            return new ScrollPreservingGrid(savedScroll).size(158, height).scrollable(new VerticalScrollData())
                    .gridOfSizeWidth(entries.size(), 1, (x, y, index) ->
                            new DisplaySlot(entries, index).name(syncName + "Slot" + index));
        });
        name(syncName + "List").size(158, height).syncHandler(rows);
    }

    public @Nullable GenericStack getAt(int index) {
        var entries = contents.getValue();
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    public static final class DisplaySlot extends Widget<DisplaySlot> implements IngredientProvider<Object> {
        private final List<GenericStack> source;
        private final int index;
        private DisplaySlot(List<GenericStack> source, int index) {
            this.source = source; this.index = index; size(158, 18);
            tooltipDynamic(tooltip -> {
                var value = displayed();
                if (value != null) {
                    tooltip.addFromItem(GenericStack.wrapInItemStack(value));
                    tooltip.newLine().add(Component.literal(AEGuiHelper.formatAmountFull(value.amount()) +
                            (value.what() instanceof AEFluidKey ? " mB" : "")));
                }
            });
            tooltip().autoUpdate(true);
        }
        public @Nullable GenericStack displayed() { return index < source.size() ? source.get(index) : null; }
        @OnlyIn(Dist.CLIENT) @Override public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            GTGuiTextures.SLOT.draw(context, 0, 0, 18, 18);
            var value = displayed(); if (value == null) return;
            var graphics = context.getGraphics();
            if (value.what() instanceof AEItemKey item) graphics.renderItem(item.toStack(), 1, 1);
            else AEGuiHelper.drawFluid(graphics, value, 1, 1);
            String amount = value.what() instanceof AEFluidKey ? MEFluidUnits.formatDisplayAmount(value.amount()) : "x" + NumberUtils.formatLong(value.amount());
            var font = Minecraft.getInstance().font;
            var text = amount + " " + value.what().getDisplayName().getString();
            graphics.drawString(font, font.plainSubstrByWidth(text, 134), 21, 5, 0xFFFFFFFF, false);
        }
        @OnlyIn(Dist.CLIENT) @Override public void drawForeground(ModularGuiContext context) {
            var value = displayed();
            if (value != null && isHovering() && !context.getUISettings().drawTooltipExternally())
                tooltip().draw(context, GenericStack.wrapInItemStack(value));
        }
        @SuppressWarnings({"rawtypes", "unchecked"}) private static EntryList<Object> cast(EntryList<?> entries) { return (EntryList) entries; }
        @OnlyIn(Dist.CLIENT) @Override public EntryList<Object> getIngredients() {
            var value = displayed();
            if (value != null && value.what() instanceof AEFluidKey fluid) return cast(FluidStackList.of(fluid.toStack(1000)));
            return cast(value != null && value.what() instanceof AEItemKey item ? ItemStackList.of(item.toStack()) : new ItemStackList());
        }
        @OnlyIn(Dist.CLIENT) @SuppressWarnings({"rawtypes", "unchecked"}) @Override public Class<Object> ingredientClass() {
            var value = displayed(); return (Class) (value != null && value.what() instanceof AEFluidKey ? FluidStack.class : ItemStack.class);
        }
    }
}
