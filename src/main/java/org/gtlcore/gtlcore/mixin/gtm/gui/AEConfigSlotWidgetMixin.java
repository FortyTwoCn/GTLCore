package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.client.gui.widget.NativeAEWidgetIngredients;
import org.gtlcore.gtlcore.integration.ae2.gui.NativeAEAmounts;
import com.gregtechceu.gtceu.integration.ae2.gui.AEConfigWidget;
import com.gregtechceu.gtceu.integration.ae2.gui.AEConfigSyncHandler;
import appeng.api.stacks.GenericStack;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.integration.recipeviewer.entry.EntryList;
import brachy.modularui.integration.recipeviewer.handlers.IngredientProvider;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import brachy.modularui.screen.ModularPanel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AEConfigWidget.class, remap = false)
public abstract class AEConfigSlotWidgetMixin implements IngredientProvider<Object> {
    @Shadow private AEConfigSyncHandler configSyncHandler;
    @Shadow private PanelSyncManager syncManager;
    @Shadow private float lastMouseX;
    @Shadow private float lastMouseY;
    @Shadow private TextFieldWidget amountField;
    @Shadow protected abstract int getSlotAtLocal(double x, double y);
    @Shadow protected abstract int slotX(int index);
    @Shadow protected abstract int slotY(int index);
    @Shadow protected abstract boolean isStocking();
    // Retain ItemStack.class for the native ghost-item bridge. EMI uses ingredientOverride for fluids.
    @Shadow public abstract Class<Object> ingredientClass();
    @Inject(method = "buildAmountEditor", at = @At("RETURN"), require = 1)
    private void gtlcore$boundExpressionLength(ModularPanel<?> parent, Player player, CallbackInfoReturnable<ModularPanel<?>> cir) {
        amountField.setMaxLength(NativeAEAmounts.MAX_TEXT_LENGTH);
    }
    @Unique private GenericStack gtlcore$hoveredStack() {
        int slot = getSlotAtLocal(lastMouseX, lastMouseY);
        if (slot < 0 || configSyncHandler == null) return null;
        return lastMouseY < slotY(slot) + 18 ? configSyncHandler.getClientConfig(slot) : configSyncHandler.getClientStock(slot);
    }
    @Override public EntryList<Object> getIngredients() { return NativeAEWidgetIngredients.entries(gtlcore$hoveredStack()); }
    @Override public Object ingredientOverride() {
        int slot = getSlotAtLocal(lastMouseX, lastMouseY);
        if (slot < 0) return null;
        boolean stock = lastMouseY >= slotY(slot) + 18;
        return NativeAEWidgetIngredients.clickable(gtlcore$hoveredStack(),
                new Rect2i(getArea().x + slotX(slot), getArea().y + slotY(slot) + (stock ? 18 : 0), 18, 18));
    }
    @Inject(method = "onMouseScrolled", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlcore$longScroll(double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
        if (isStocking() || vertical == 0 || syncManager == null || configSyncHandler == null) { cir.setReturnValue(false); return; }
        // ModularUI already transforms these coordinates into the local widget viewport.
        double x = getContext().getMouseX(), y = getContext().getMouseY();
        int slot = getSlotAtLocal(x, y);
        if (slot < 0 || y >= slotY(slot) + 18) { cir.setReturnValue(false); return; }
        var value = configSyncHandler.getClientConfig(slot);
        if (value == null) { cir.setReturnValue(false); return; }
        long amount = NativeAEAmounts.scroll(value.amount(), vertical > 0, Interactable.hasControlDown());
        syncManager.callSyncedAction("ae_config_amount", packet -> { packet.writeVarInt(slot); packet.writeVarLong(amount); });
        cir.setReturnValue(true);
    }
}
