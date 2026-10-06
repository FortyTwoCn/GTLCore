package org.gtlcore.gtlcore.client.gui.widget;

import org.gtlcore.gtlcore.integration.ae2.MEFluidUnits;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.integration.ae2.gui.AEGuiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import appeng.api.stacks.*;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.integration.emi.EmiStackConverter;
import brachy.modularui.integration.recipeviewer.entry.EntryList;
import brachy.modularui.integration.recipeviewer.entry.item.ItemStackList;
import brachy.modularui.integration.recipeviewer.entry.fluid.FluidStackList;
import brachy.modularui.integration.recipeviewer.handlers.*;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.Widget;
import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import java.util.Optional;

/** Separate native ingredient providers for the configured and stocked halves of each visible cell. */
public class AEDualConfigSlotWidget extends ParentWidget<AEDualConfigSlotWidget> {
    public AEDualConfigSlotWidget(int x, int y, AEDualConfigWidget parent, int visible) {
        pos(x, y).size(18, 36);
        child(new StackCell(parent, visible, false).pos(0, 0));
        child(new StackCell(parent, visible, true).pos(0, 18));
    }
    private static class StackCell extends Widget<StackCell>
            implements Interactable, GhostIngredientSlot<Object>, IngredientProvider<Object> {
        private final AEDualConfigWidget parent;
        private final int visible;
        private final boolean stock;
        private StackCell(AEDualConfigWidget parent, int visible, boolean stock) {
            this.parent = parent; this.visible = visible; this.stock = stock; size(18);
            setEnabledIf(widget -> index() >= 0 && index() < parent.getSlotCount());
        }
        private int index() { return parent.getSlotIndex(visible); }
        @Override public void onInit() { super.onInit(); if (!stock) getContext().getRecipeViewerSettings().addGhostIngredientSlot(this); }
        @OnlyIn(Dist.CLIENT) private GenericStack stack() { return parent.getDisplayStack(index(), stock); }
        @OnlyIn(Dist.CLIENT) @Override public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            var graphics = context.getGraphics();
            (stock || parent.isAutoPull() ? GTGuiTextures.SLOT_DARK : GTGuiTextures.FLUID_SLOT).draw(context, 0, 0, 18, 18);
            if (!stock) (parent.isAutoPull() ? GTGuiTextures.CONFIG_ARROW_DARK : GTGuiTextures.CONFIG_ARROW).draw(context, 0, 0, 18, 18);
            var value = stack();
            if (value != null) {
                if (value.what() instanceof AEItemKey item) graphics.renderItem(item.toStack(), 1, 1);
                else AEGuiHelper.drawFluid(graphics, value, 1, 1);
                if (stock || parent.isAmountEditingEnabled()) NativeAEWidgetIngredients.drawAmount(graphics, value, 1, 1);
            }
            if (isHovering()) AEGuiHelper.drawSelectionOverlay(graphics, 1, 1, 16, 16);
        }
        @OnlyIn(Dist.CLIENT) @Override public void drawForeground(ModularGuiContext context) {
            if (!isHovering()) return;
            var value = stack(); var graphics = context.getGraphics();
            if (value == null) {
                if (!stock) graphics.renderTooltip(Minecraft.getInstance().font,
                        List.of(Component.translatable(parent.isAutoPull() ? "gtceu.gui.config_slot.auto_pull_managed" : "gtceu.gui.config_slot.set_only")),
                        Optional.empty(), context.getAbsMouseX(), context.getAbsMouseY());
            } else {
                graphics.renderTooltip(Minecraft.getInstance().font, GenericStack.wrapInItemStack(value), context.getAbsMouseX(), context.getAbsMouseY());
            }
        }
        @OnlyIn(Dist.CLIENT) @Override public Result onMousePressed(int button) {
            if (stock || parent.isAutoPull() || parent.getSyncManager() == null) return Result.IGNORE;
            int index = index();
            if (button == 1) { parent.getSyncManager().callSyncedAction("dual_config_clear", packet -> packet.writeVarInt(index)); parent.disableAmount(); return Result.SUCCESS; }
            if (button != 0) return Result.IGNORE;
            if (!Minecraft.getInstance().player.containerMenu.getCarried().isEmpty())
                parent.getSyncManager().callSyncedAction("dual_config_set", packet -> packet.writeVarInt(index));
            else if (stack() != null) parent.enableAmount(index);
            return Result.SUCCESS;
        }
        @OnlyIn(Dist.CLIENT) @Override public boolean onMouseScrolled(double horizontal, double vertical) {
            if (stock || parent.isAutoPull() || !parent.isAmountEditingEnabled() || stack() == null || vertical == 0) return false;
            long current = stack().amount(); long next;
            if (Interactable.hasControlDown()) next = vertical > 0 ? current > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : current * 2 : Math.max(1, current / 2);
            else next = vertical > 0 ? current == Long.MAX_VALUE ? current : current + 1 : Math.max(1, current - 1);
            int index = index(); parent.getSyncManager().callSyncedAction("dual_config_amount", packet -> { packet.writeVarInt(index); packet.writeVarLong(next); }); return true;
        }
        @OnlyIn(Dist.CLIENT) @Override public EntryList<Object> getIngredients() {
            var value = stack();
            if (value != null && value.what() instanceof AEFluidKey key) return cast(FluidStackList.of(key.toStack(1000)));
            return cast(value != null && value.what() instanceof AEItemKey key ? ItemStackList.of(key.toStack()) : new ItemStackList());
        }
        @SuppressWarnings({"unchecked", "rawtypes"}) private static EntryList<Object> cast(EntryList<?> entries) { return (EntryList) entries; }
        @OnlyIn(Dist.CLIENT) @SuppressWarnings({"unchecked", "rawtypes"}) @Override public Class<Object> ingredientClass() {
            var value = stack(); return (Class) (value != null && value.what() instanceof AEFluidKey ? FluidStack.class : ItemStack.class);
        }
        @Override public Object castGhostIngredientIfValid(Object ingredient) {
            if (stock || parent.isAutoPull() || !areAncestorsEnabled()) return null;
            return ingredient instanceof ItemStack item && !item.isEmpty() || ingredient instanceof FluidStack fluid && !fluid.isEmpty() ? ingredient : null;
        }
        @Override public void setGhostIngredient(Object ingredient) {
            if (castGhostIngredientIfValid(ingredient) == null || parent.getSyncManager() == null) return;
            int index = index();
            parent.getSyncManager().callSyncedAction("dual_config_ghost", packet -> {
                packet.writeVarInt(index); packet.writeBoolean(ingredient instanceof FluidStack);
                if (ingredient instanceof FluidStack fluid) FluidStack.STREAM_CODEC.encode(packet, fluid);
                else ItemStack.STREAM_CODEC.encode(packet, (ItemStack) ingredient);
            });
        }
        @OnlyIn(Dist.CLIENT) @Override public boolean ingredientHandlingOverride(Object ingredient) {
            if (!net.neoforged.fml.ModList.get().isLoaded("emi") || !(ingredient instanceof EmiStack emi) || stock || parent.isAutoPull()) return false;
            var fluid = EmiStackConverter.FLUID.convertFrom(emi);
            if (fluid != null && !fluid.isEmpty()) { setGhostIngredient(fluid); return true; }
            var item = EmiStackConverter.ITEM.convertFrom(emi);
            if (item != null && !item.isEmpty()) { setGhostIngredient(item); return true; }
            return false;
        }
    }
}
