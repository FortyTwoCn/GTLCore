package org.gtlcore.gtlcore.common.machine.multiblock.part;

import org.gtlcore.gtlcore.integration.ae2.gui.MolecularPatternPagination;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;
import java.util.function.Function;
import java.util.function.IntConsumer;

/** Configurable native pattern pages with authoritative slot numbering and page synchronization. */
public final class PaginationUIManager {
    private final int uiWidth, uiHeight, patternsPerRow, rowsPerPage, maxPages, maxPatternCount;
    private final IItemHandlerModifiable patternInventory;
    private final @Nullable Function<Integer, Boolean> isCached;
    private final IntConsumer onPatternChange;

    public PaginationUIManager(int columns, int rows, int pages, int uiWidth, int uiHeight, IntConsumer changed,
                               @Nullable Function<Integer, Boolean> cached, IItemHandlerModifiable inventory) {
        this(columns, rows, Math.multiplyExact(Math.multiplyExact(columns, rows), pages), uiWidth, uiHeight, changed, inventory, cached);
        if (pages < 1 || pages > 10) throw new IllegalArgumentException("Invalid pattern page count");
    }
    public PaginationUIManager(int columns, int rows, int size, int uiWidth, int uiHeight, IntConsumer changed,
                               IItemHandlerModifiable inventory) {
        this(columns, rows, size, uiWidth, uiHeight, changed, inventory, null);
    }
    private PaginationUIManager(int columns, int rows, int size, int uiWidth, int uiHeight, IntConsumer changed,
                                IItemHandlerModifiable inventory, @Nullable Function<Integer, Boolean> cached) {
        if (columns < 1 || columns > 18 || rows < 1 || rows > 10 || size < 0)
            throw new IllegalArgumentException("Invalid pattern page dimensions");
        this.patternsPerRow = columns; this.rowsPerPage = rows; this.maxPatternCount = size;
        this.maxPages = Math.max(1, Math.ceilDiv(size, Math.multiplyExact(columns, rows)));
        this.uiWidth = uiWidth; this.uiHeight = uiHeight;
        this.patternInventory = inventory; this.onPatternChange = changed; this.isCached = cached;
    }
    public IWidget createPaginationUI(PanelSyncManager sync, @Nullable IntConsumer middleClick) {
        return MolecularPatternPagination.build(patternInventory, maxPatternCount, sync, onPatternChange, middleClick,
                slot -> isCached != null && Boolean.TRUE.equals(isCached.apply(slot)), patternsPerRow, rowsPerPage,
                Math.min(MolecularPatternPagination.VISIBLE_ROWS, rowsPerPage));
    }
    public void refreshPage(PanelSyncManager sync, int page) {
        sync.findSyncHandler("molecularPageIndex", IntSyncValue.class).setIntValue(Math.clamp(page, 0, maxPages - 1));
    }
    public int getUiWidth() { return uiWidth; }
    public int getUiHeight() { return uiHeight; }
    public int getPatternsPerRow() { return patternsPerRow; }
    public int getRowsPerPage() { return rowsPerPage; }
    public int getMaxPages() { return maxPages; }
    public int getMaxPatternCount() { return maxPatternCount; }
}
