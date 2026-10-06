package org.gtlcore.gtlcore.integration.ae2.gui;

import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import appeng.crafting.pattern.EncodedPatternItem;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.ItemSlotSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ScrollWidget;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.scroll.VerticalScrollData;
import brachy.modularui.widget.scroll.HorizontalScrollData;
import brachy.modularui.utils.Alignment;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.PagedWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.SlotGroup;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/** Server-sized native pattern pages; clients do not need the distant pattern-container block entities loaded. */
public final class MolecularPatternPagination {
    private MolecularPatternPagination() {}
    public static final int COLUMNS = 9, ROWS = 8, SLOTS_PER_PAGE = COLUMNS * ROWS;
    // Keep the native title, network status and player inventory visible at Minecraft's minimum GUI height.
    // This limits the viewport, not the physical inventory or the server's page/slot numbering.
    public static final int VISIBLE_ROWS = 3;

    public static IWidget build(IItemHandlerModifiable serverInventory, int size, PanelSyncManager manager, IntConsumer changed) {
        return build(serverInventory, size, manager, changed, null, slot -> false);
    }

    public static IWidget build(IItemHandlerModifiable serverInventory, int size, PanelSyncManager manager, IntConsumer changed, int rows) {
        return build(serverInventory, size, manager, changed, null, slot -> false, rows);
    }

    public static IWidget build(IItemHandlerModifiable serverInventory, int size, PanelSyncManager manager, IntConsumer changed,
                                 IntConsumer openCatalysts, IntPredicate hasCache) {
        return build(serverInventory, size, manager, changed, openCatalysts, hasCache, ROWS);
    }

    private static IWidget build(IItemHandlerModifiable serverInventory, int size, PanelSyncManager manager, IntConsumer changed,
                                 IntConsumer openCatalysts, IntPredicate hasCache, int rows) {
        return build(serverInventory, size, manager, changed, openCatalysts, hasCache, COLUMNS, rows,
                rows == ROWS ? VISIBLE_ROWS : rows);
    }

    /** Configured physical pages remain intact while the native viewport bounds both scroll axes. */
    public static IWidget build(IItemHandlerModifiable serverInventory, int size, PanelSyncManager manager, IntConsumer changed,
                                IntConsumer openCatalysts, IntPredicate hasCache, int columns, int rows, int visibleRowLimit) {
        if (size < 0) throw new IllegalArgumentException("Negative molecular pattern inventory size");
        if (columns < 1 || columns > 18 || rows < 1 || rows > 10 || visibleRowLimit < 1 || visibleRowLimit > rows)
            throw new IllegalArgumentException("Invalid pattern page dimensions");
        int slotsPerPage = Math.multiplyExact(columns, rows);
        if (!manager.isClient() && serverInventory.getSlots() != size) {
            throw new IllegalStateException("Molecular pattern sources changed while opening their menu");
        }
        var inventory = manager.isClient() ? new CustomItemStackHandler(size) : serverInventory;
        var group = new SlotGroup("molecularPagedPatterns", columns, 0, true);
        manager.registerSlotGroup(group);
        int pageCount = Math.max(1, Math.ceilDiv(size, slotsPerPage));
        int visibleRows = Math.min(visibleRowLimit, Math.max(1, Math.ceilDiv(size, columns)));
        boolean scrollHorizontally = columns > COLUMNS;
        int width = Math.max(90, Math.min(columns, COLUMNS) * 18 + (size > columns * visibleRows ? 4 : 0));
        int height = visibleRows * 18 + (scrollHorizontally ? 4 : 0);
        var pages = new PagedWidget<>().name("molecularPatternPages").size(width, height);
        for (int p = 0; p < pageCount; p++) {
            int start = p * slotsPerPage, end = Math.min(size, start + slotsPerPage);
            int pageRows = Math.max(1, Math.ceilDiv(end - start, columns));
            var scroll = new VerticalScrollData(false, 4);
            scroll.setScrollSize(pageRows * 18);
            scroll.setScrollSpeed(18);
            var viewport = new ScrollWidget<>(scroll).name("molecularPatternViewport" + p).size(width, height);
            if (scrollHorizontally) {
                var horizontal = new HorizontalScrollData(false, 4);
                horizontal.setScrollSize(columns * 18);
                horizontal.setScrollSpeed(18);
                viewport.getScrollArea().setScrollX(horizontal);
            }
            // Grid is itself a viewport. A single scrolling viewport keeps hit testing
            // and clipping in the same coordinate system when both axes are scrolled.
            var grid = new ParentWidget<>().name("molecularPatternGrid" + p).size(columns * 18, pageRows * 18).pos(0, 0);
            for (int slotIndex = start; slotIndex < end; slotIndex++) {
                final int index = slotIndex;
                var slotSync = manager.getOrCreateSyncHandler("molecularPatternSlot", slotIndex, ItemSlotSyncHandler.class,
                        () -> new ItemSlotSyncHandler(SyncHandlers.itemSlot(inventory, index).slotGroup(group)
                                .accessibility(true, true).changeListener((oldStack, newStack, client, init) -> {
                                    if (!client && !init) changed.accept(index);
                                })));
                var widget = new PatternBufferItemSlot(openCatalysts == null ? null : () -> openCatalysts.accept(index))
                        .syncHandler(slotSync).background(GTGuiTextures.SLOT, GTGuiTextures.PATTERN_OVERLAY);
                if (openCatalysts != null) widget.itemTooltip().tooltipBuilder(tooltip -> {
                    if (hasCache.test(index)) tooltip.addLine(Component.translatable("gtceu.machine.pattern.recipe.cache"));
                });
                widget.setItemHook(stack -> {
                    if (stack.getItem() instanceof EncodedPatternItem<?> pattern) {
                        var output = pattern.getOutput(stack); if (!output.isEmpty()) return output;
                    }
                    return stack;
                });
                int local = slotIndex - start;
                grid.child(widget.pos(local % columns * 18, local / columns * 18));
            }
            pages.addPage(viewport.child(grid));
        }
        var selection = new AtomicInteger();
        var page = manager.getOrCreateSyncHandler("molecularPageIndex", IntSyncValue.class,
                () -> new IntSyncValue(selection::get, value -> selection.set(Math.clamp(value, 0, pageCount - 1))).allowC2S());
        page.setChangeListener(() -> {
            int value = Math.clamp(page.getIntValue(), 0, pageCount - 1);
            if (page.getIntValue() != value) {
                page.setIntValue(value, true, false);
                return;
            }
            pages.setPage(value);
            for (int i = 0; i < size; i++) {
                var slot = manager.findSyncHandler("molecularPatternSlot", i, ItemSlotSyncHandler.class);
                slot.setEnabled(i / slotsPerPage == value, false);
            }
        });
        page.getChangeListener().run();
        return Flow.col().coverChildren().child(pages).child(Flow.row().coverChildren()
                .child(new ButtonWidget<>().name("molecularPatternPrevious").size(30, 12).overlay(Text.str("<<"))
                        .onMousePressed((context, button) -> { page.setIntValue(Math.max(0, page.getIntValue() - 1)); return true; }))
                .child(Text.dynamic(() -> Component.literal((page.getIntValue() + 1) + " / " + pageCount)).asWidget()
                        .textAlign(Alignment.Center).width(width - 60).height(12))
                .child(new ButtonWidget<>().name("molecularPatternNext").size(30, 12).overlay(Text.str(">>"))
                        .onMousePressed((context, button) -> { page.setIntValue(Math.min(pageCount - 1, page.getIntValue() + 1)); return true; })));
    }
}
