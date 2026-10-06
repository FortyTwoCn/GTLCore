package org.gtlcore.gtlcore.integration.ae2.widget;

import org.gtlcore.gtlcore.integration.ae2.gui.PatternBufferItemSlot;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import appeng.crafting.pattern.EncodedPatternItem;
import brachy.modularui.value.sync.ItemSlotSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/** Native pattern slot; vanilla menu transactions own clicks and server change notifications. */
public class AEPatternViewExtendSlotWidget extends PatternBufferItemSlot {
    private @Nullable Runnable onMiddleClick, onPatternSlotChanged;
    public AEPatternViewExtendSlotWidget(IItemHandlerModifiable inventory, PanelSyncManager sync, int slot, int x, int y) {
        super(null);
        var handler = sync.getOrCreateSyncHandler("extendedPatternSlot", slot, ItemSlotSyncHandler.class,
                () -> new ItemSlotSyncHandler(SyncHandlers.itemSlot(inventory, slot).accessibility(true, true)
                        .changeListener((oldStack, newStack, client, init) -> {
                            if (!client && !init && onPatternSlotChanged != null) onPatternSlotChanged.run();
                        })));
        syncHandler(handler); pos(x, y); background(GTGuiTextures.SLOT, GTGuiTextures.PATTERN_OVERLAY);
        setItemHook(stack -> {
            if (stack.getItem() instanceof EncodedPatternItem<?> pattern) {
                var output = pattern.getOutput(stack); if (!output.isEmpty()) return output;
            }
            return stack;
        });
    }
    public AEPatternViewExtendSlotWidget setOnMiddleClick(@Nullable Runnable callback) { onMiddleClick = callback; return this; }
    public AEPatternViewExtendSlotWidget setOnPatternSlotChanged(@Nullable Runnable callback) { onPatternSlotChanged = callback; return this; }
    @Override public Result onMousePressed(int button) {
        if (button == 2 && onMiddleClick != null) { onMiddleClick.run(); return Result.SUCCESS; }
        return super.onMousePressed(button);
    }
}
