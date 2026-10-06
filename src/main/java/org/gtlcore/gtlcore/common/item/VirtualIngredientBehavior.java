package org.gtlcore.gtlcore.common.item;

import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.mui.IItemUIHolder;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.common.data.item.GTDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.value.sync.FluidSlotSyncHandler;
import brachy.modularui.value.sync.ItemSlotSyncHandler;
import brachy.modularui.widgets.SlotGroupWidget;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.FluidSlot;
import brachy.modularui.widgets.slot.SlotGroup;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/** A sealed supply token whose real payload uses native, registry-aware data components. */
public class VirtualIngredientBehavior implements IItemUIHolder, IAddInformation {
    public static final VirtualIngredientBehavior INSTANCE = new VirtualIngredientBehavior();
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("gtlcore", "virtual_ingredient");
    private static final String MARKED_TAG = "marked";

    public static boolean isVirtualIngredient(ItemStack stack) {
        return !stack.isEmpty() && ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static Item registeredItem() {
        return BuiltInRegistries.ITEM.getOptional(ID).orElseThrow(() -> new IllegalStateException("Virtual ingredient item is not registered"));
    }

    /** Detached storage; changes must be written back into the wrapper. */
    public static CustomItemStackHandler getItemStorage(ItemStack wrapper) {
        var storage = new CustomItemStackHandler(1);
        storage.setFilter(stack -> stack.isEmpty() || !isVirtualIngredient(stack));
        storage.setStackInSlot(0, wrapper.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyOne());
        return storage;
    }

    public static void saveItemStorage(ItemStack wrapper, CustomItemStackHandler storage) {
        wrapper.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(storage.getStackInSlot(0))));
    }

    public static CustomFluidTank getFluidStorage(ItemStack wrapper) {
        var storage = new CustomFluidTank(FluidType.BUCKET_VOLUME);
        storage.setFluid(wrapper.getOrDefault(GTDataComponents.FLUID_CONTENT, SimpleFluidContent.EMPTY).copy());
        return storage;
    }

    public static void saveFluidStorage(ItemStack wrapper, CustomFluidTank storage) {
        wrapper.set(GTDataComponents.FLUID_CONTENT, SimpleFluidContent.copyOf(storage.getFluid()));
    }

    @Nullable public static AEItemKey payloadItemKey(ItemStack wrapper) {
        var payload = getItemStorage(wrapper).getStackInSlot(0);
        return payload.isEmpty() || isVirtualIngredient(payload) ? null : AEItemKey.of(payload);
    }

    @Nullable public static AEFluidKey payloadFluidKey(ItemStack wrapper) {
        var payload = getFluidStorage(wrapper).getFluid();
        return payload.isEmpty() ? null : AEFluidKey.of(payload);
    }

    public static boolean isBound(ItemStack wrapper) {
        return (payloadItemKey(wrapper) != null) != (payloadFluidKey(wrapper) != null);
    }

    /** The published key depends on the payload, rather than unused UI state or a custom wrapper name. */
    public static ItemStack canonicalStack(ItemStack wrapper) {
        if (!isVirtualIngredient(wrapper) || !isBound(wrapper)) return ItemStack.EMPTY;
        var item = getItemStorage(wrapper).getStackInSlot(0);
        return item.isEmpty() ? wrap(getFluidStorage(wrapper).getFluid()) : wrap(item);
    }

    public static ItemStack wrap(ItemStack payload) {
        if (payload.isEmpty() || isVirtualIngredient(payload)) throw new IllegalArgumentException("Invalid virtual item payload");
        var wrapper = new ItemStack(registeredItem());
        var storage = new CustomItemStackHandler(1);
        storage.setStackInSlot(0, payload.copyWithCount(1));
        saveItemStorage(wrapper, storage);
        mark(wrapper);
        return wrapper;
    }

    public static ItemStack wrap(FluidStack payload) {
        if (payload.isEmpty()) throw new IllegalArgumentException("Empty virtual fluid payload");
        var wrapper = new ItemStack(registeredItem());
        var storage = new CustomFluidTank(FluidType.BUCKET_VOLUME);
        storage.setFluid(payload.copyWithAmount(FluidType.BUCKET_VOLUME));
        saveFluidStorage(wrapper, storage);
        mark(wrapper);
        return wrapper;
    }

    public static boolean isMarked(ItemStack wrapper) {
        return wrapper.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(MARKED_TAG);
    }

    public static void mark(ItemStack wrapper) {
        var tag = wrapper.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(MARKED_TAG, true);
        wrapper.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override public ModularPanel<?> buildUI(PlayerInventoryGuiData<?> data, PanelSyncManager sync, UISettings settings) {
        var wrapper = data.getUsedItemStack();
        boolean editable = !isMarked(wrapper);
        var item = getItemStorage(wrapper);
        var fluid = getFluidStorage(wrapper);
        item.setFilter(stack -> editable && !isVirtualIngredient(stack) && fluid.isEmpty());
        fluid.setValidator(stack -> editable && item.getStackInSlot(0).isEmpty());
        item.setOnContentsChanged(() -> { saveItemStorage(wrapper, item); data.setUsedItemStack(wrapper); });
        fluid.setOnContentsChanged(() -> { saveFluidStorage(wrapper, fluid); data.setUsedItemStack(wrapper); });
        var itemSync = new ItemSlotSyncHandler(SyncHandlers.itemSlot(item, 0).slotGroup(new SlotGroup("virtualPayload", 1, false)).accessibility(editable, editable));
        sync.syncValue("virtualItemPayload", itemSync);
        var fluidSync = new FluidSlotSyncHandler(fluid).canFillSlot(editable).canDrainSlot(editable);
        sync.syncValue("virtualFluidPayload", fluidSync);
        return ModularPanel.defaultPanel("virtualIngredient", 176, 166)
                .child(Text.lang("item.gtlcore.virtual_ingredient").asWidget().pos(7, 6))
                .child(Text.lang(editable ? "gui.gtlcore.virtual_ingredient.hint" : "gui.gtlcore.virtual_ingredient.sealed").asWidget().pos(7, 18))
                .child(new ItemSlot().syncHandler(itemSync).pos(53, 30))
                .child(new FluidSlot().syncHandler(fluidSync).pos(105, 30))
                .child(SlotGroupWidget.playerInventory(false).pos(7, 84).disableSortButtons());
    }

    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var item = payloadItemKey(stack);
        var fluid = payloadFluidKey(stack);
        if (item != null) tooltip.add(Component.translatable("tooltip.gtlcore.virtual_ingredient.payload", item.getDisplayName()).withStyle(ChatFormatting.AQUA));
        if (fluid != null) tooltip.add(Component.translatable("tooltip.gtlcore.virtual_ingredient.payload", fluid.getDisplayName()).withStyle(ChatFormatting.AQUA));
        if (item == null && fluid == null) tooltip.add(Component.translatable("tooltip.gtlcore.virtual_ingredient.empty").withStyle(ChatFormatting.GRAY));
        else if (isMarked(stack)) tooltip.add(Component.translatable("tooltip.gtlcore.virtual_ingredient.sealed").withStyle(ChatFormatting.DARK_GRAY));
    }
}
