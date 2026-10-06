package org.gtlcore.gtlcore.integration.ae2.gui;

import brachy.modularui.widgets.slot.ItemSlot;
import net.minecraft.world.item.ItemStack;

/** Retains the pattern output preview while middle-click opens the native catalyst panel. */
public class PatternBufferItemSlot extends ItemSlot {
    private final Runnable openCatalysts;
    private ItemStack lastDisplayedStack = ItemStack.EMPTY;
    public PatternBufferItemSlot(Runnable openCatalysts) { this.openCatalysts = openCatalysts; }
    public ItemStack displayStackForRender(ItemStack storedStack) {
        var displayed = renderMappingFunction().apply(storedStack);
        lastDisplayedStack = displayed.copy();
        return displayed;
    }
    public ItemStack getLastDisplayedStack() { return lastDisplayedStack.copy(); }
    @Override public Result onMousePressed(int button) {
        if (button == 2 && openCatalysts != null) { openCatalysts.run(); return Result.SUCCESS; }
        return super.onMousePressed(button);
    }
}
