package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.integration.ae2.AEOutputIngredients;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.StringSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.FluidSlotSyncHandler;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.slot.PhantomItemSlot;
import brachy.modularui.widgets.slot.FluidSlot;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Native ghost configuration. Runtime matching uses the same component-aware keys as AE storage. */
public class MEOutputFilterHandler implements INBTSerializable<CompoundTag> {
    private final ObjectOpenHashSet<AEItemKey> itemFilterHashSet = new ObjectOpenHashSet<>();
    private final ObjectOpenHashSet<AEFluidKey> fluidFilterHashSet = new ObjectOpenHashSet<>();
    private final Runnable markDirty, updatePriority;
    private final Supplier<Integer> prioritySupplier;
    private final Consumer<Integer> onPriorityChanged;
    private final int row, col;
    private final ItemStackHandler filterSlots;
    private final FluidTank[] filterTanks;
    private boolean loading;
    @Getter protected boolean isItemBlackList, isFluidBlackList;
    @Getter protected boolean ignoreItemNbt = true, ignoreFluidNbt = true;
    @Getter private boolean hasItemFilter, hasFluidFilter;

    public MEOutputFilterHandler(int row, int col, Runnable markDirty, Runnable updatePriority,
                                 Supplier<Integer> prioritySupplier, Consumer<Integer> onPriorityChanged) {
        if (row <= 0 || col <= 0 || Math.multiplyExact(row, col) > 256) throw new IllegalArgumentException("Invalid filter dimensions");
        this.row = row; this.col = col; this.markDirty = markDirty; this.updatePriority = updatePriority;
        this.prioritySupplier = prioritySupplier; this.onPriorityChanged = onPriorityChanged;
        filterSlots = new ItemStackHandler(row * col) {
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override protected void onContentsChanged(int slot) { changed(); }
        };
        filterTanks = new FluidTank[row * col];
        Arrays.setAll(filterTanks, i -> new FluidTank(1000) {
            @Override protected void onContentsChanged() { changed(); }
        });
    }

    public int getFilterSlotCount() { return row * col; }
    public @Nullable AEItemKey getItemFilter(int slot) {
        return slot < 0 || slot >= getFilterSlotCount() ? null : AEItemKey.of(filterSlots.getStackInSlot(slot));
    }
    public @Nullable AEFluidKey getFluidFilter(int slot) {
        return slot < 0 || slot >= getFilterSlotCount() ? null : AEFluidKey.of(filterTanks[slot].getFluid());
    }
    public void setItemFilter(int slot, @Nullable AEItemKey key) {
        if (slot < 0 || slot >= getFilterSlotCount()) return;
        filterSlots.setStackInSlot(slot, key == null ? ItemStack.EMPTY : key.toStack(1));
    }
    public void setFluidFilter(int slot, @Nullable AEFluidKey key) {
        if (slot < 0 || slot >= getFilterSlotCount()) return;
        filterTanks[slot].setFluid(key == null ? FluidStack.EMPTY : key.toStack(1000));
        changed();
    }
    public void setItemBlackList(boolean value) { updateItemFilterSettings(value, ignoreItemNbt); }
    public void setFluidBlackList(boolean value) { updateFluidFilterSettings(value, ignoreFluidNbt); }
    public void setIgnoreItemNbt(boolean value) { updateItemFilterSettings(isItemBlackList, value); }
    public void setIgnoreFluidNbt(boolean value) { updateFluidFilterSettings(isFluidBlackList, value); }
    public void updateItemFilterSettings(boolean blackList, boolean ignoreNbt) {
        isItemBlackList = blackList; ignoreItemNbt = ignoreNbt; changed();
    }
    public void updateFluidFilterSettings(boolean blackList, boolean ignoreNbt) {
        isFluidBlackList = blackList; ignoreFluidNbt = ignoreNbt; changed();
    }
    private void changed() {
        if (loading) return;
        rebuild(); markDirty.run(); updatePriority.run();
    }
    private void rebuild() {
        itemFilterHashSet.clear(); fluidFilterHashSet.clear();
        for (int i = 0; i < getFilterSlotCount(); i++) {
            var item = getItemFilter(i); var fluid = getFluidFilter(i);
            if (item != null) itemFilterHashSet.add(ignoreItemNbt ? AEItemKey.of(item.getItem()) : item);
            if (fluid != null) fluidFilterHashSet.add(ignoreFluidNbt ? AEFluidKey.of(fluid.getFluid()) : fluid);
        }
        hasItemFilter = !itemFilterHashSet.isEmpty(); hasFluidFilter = !fluidFilterHashSet.isEmpty();
    }
    public void onUIClosed() { updatePriority.run(); }
    public boolean test(ItemStack stack) {
        return !hasItemFilter || isItemBlackList != itemFilterHashSet.contains(ignoreItemNbt ? AEItemKey.of(stack.getItem()) : AEItemKey.of(stack));
    }
    public boolean test(FluidStack stack) {
        return !hasFluidFilter || isFluidBlackList != fluidFilterHashSet.contains(ignoreFluidNbt ? AEFluidKey.of(stack.getFluid()) : AEFluidKey.of(stack));
    }
    public List<SizedIngredient> testIngredient(List<SizedIngredient> left) {
        left.removeIf(ingredient -> { var output = AEOutputIngredients.item(ingredient, true);
            return output != null && test(((AEItemKey) output.what()).toStack(1)); });
        return left;
    }
    public List<SizedFluidIngredient> testFluidIngredient(List<SizedFluidIngredient> left) {
        left.removeIf(ingredient -> { var output = AEOutputIngredients.fluid(ingredient, true);
            return output != null && test(((AEFluidKey) output.what()).toStack(1)); });
        return left;
    }

    public Flow createMainWidgetGroup(PanelSyncManager manager) {
        var itemBlacklist = new BooleanSyncValue(this::isItemBlackList, this::setItemBlackList).allowC2S();
        var itemComponents = new BooleanSyncValue(this::isIgnoreItemNbt, this::setIgnoreItemNbt).allowC2S();
        var fluidBlacklist = new BooleanSyncValue(this::isFluidBlackList, this::setFluidBlackList).allowC2S();
        var fluidComponents = new BooleanSyncValue(this::isIgnoreFluidNbt, this::setIgnoreFluidNbt).allowC2S();
        var priority = new StringSyncValue(() -> prioritySupplier.get().toString(), value -> {
            try { onPriorityChanged.accept(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
        }).allowC2S();
        manager.syncValue("itemBlacklist", itemBlacklist); manager.syncValue("itemIgnoreComponents", itemComponents);
        manager.syncValue("fluidBlacklist", fluidBlacklist); manager.syncValue("fluidIgnoreComponents", fluidComponents);
        manager.syncValue("filterPriority", priority);
        return Flow.col().coverChildren().margin(4)
                .child(Flow.row().coverChildren()
                        .child(new Grid().size(col * 18, row * 18).gridOfSizeWidth(getFilterSlotCount(), col,
                                (x, y, index) -> itemFilterSlot(manager, index)))
                        .child(Flow.col().coverChildren()
                                .child(new ToggleButton().size(18).value(itemBlacklist).overlay(GTGuiTextures.BUTTON_BLACKLIST).tooltipDynamic(t -> t.addLine(net.minecraft.network.chat.Component.translatable("tooltip.gtlcore.me_chamber_manager_terminal.item_blacklist"))))
                                .child(new ToggleButton().size(18).value(itemComponents).overlay(GTGuiTextures.BUTTON_IGNORE_NBT).tooltipDynamic(t -> t.addLine(net.minecraft.network.chat.Component.translatable("tooltip.gtlcore.me_chamber_manager_terminal.item_nbt"))))))
                .child(Flow.row().coverChildren().marginTop(6)
                        .child(new Grid().size(col * 18, row * 18).gridOfSizeWidth(getFilterSlotCount(), col,
                                (x, y, index) -> fluidFilterSlot(manager, index)))
                        .child(Flow.col().coverChildren()
                                .child(new ToggleButton().size(18).value(fluidBlacklist).overlay(GTGuiTextures.BUTTON_BLACKLIST).tooltipDynamic(t -> t.addLine(net.minecraft.network.chat.Component.translatable("tooltip.gtlcore.me_chamber_manager_terminal.fluid_blacklist"))))
                                .child(new ToggleButton().size(18).value(fluidComponents).overlay(GTGuiTextures.BUTTON_IGNORE_NBT).tooltipDynamic(t -> t.addLine(net.minecraft.network.chat.Component.translatable("tooltip.gtlcore.me_chamber_manager_terminal.fluid_nbt"))))))
                .child(Flow.row().coverChildren().marginTop(6)
                        .child(Text.lang("field.gtlcore.me_chamber_manager_terminal.priority").asWidget().width(65).height(18))
                        .child(new TextFieldWidget().size(100, 18).value(priority).setNumbers(i -> Math.max(10, Math.min(100_000, i)))));
    }

    private PhantomItemSlot itemFilterSlot(PanelSyncManager manager, int index) {
        var handler = new brachy.modularui.value.sync.PhantomItemSlotSyncHandler(SyncHandlers.itemSlot(filterSlots, index));
        manager.syncValue("outputItemFilter", index, handler);
        return new PhantomItemSlot().syncHandler(handler);
    }
    private FluidSlot fluidFilterSlot(PanelSyncManager manager, int index) {
        var handler = new FluidSlotSyncHandler(filterTanks[index]).phantom(true).controlsAmount(false);
        manager.syncValue("outputFluidFilter", index, handler);
        return new FluidSlot().syncHandler(handler).alwaysShowFull(true);
    }

    @Override public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        var tag = new CompoundTag(); var items = new ListTag(); var fluids = new ListTag();
        for (int i = 0; i < getFilterSlotCount(); i++) {
            var item = getItemFilter(i); var fluid = getFluidFilter(i);
            items.add(item == null ? new CompoundTag() : item.toTag(registries));
            fluids.add(fluid == null ? new CompoundTag() : fluid.toTag(registries));
        }
        tag.put("items", items); tag.put("fluids", fluids);
        tag.putBoolean("isItemBlackList", isItemBlackList); tag.putBoolean("ignoreItemNbt", ignoreItemNbt);
        tag.putBoolean("isFluidBlackList", isFluidBlackList); tag.putBoolean("ignoreFluidNbt", ignoreFluidNbt);
        return tag;
    }
    @Override public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        var itemTags = tag.getList("items", 10); var fluidTags = tag.getList("fluids", 10);
        loading = true;
        try {
            for (int i = 0; i < getFilterSlotCount(); i++) {
                setItemFilter(i, i < itemTags.size() && !itemTags.getCompound(i).isEmpty() ? AEItemKey.fromTag(registries, itemTags.getCompound(i)) : null);
                setFluidFilter(i, i < fluidTags.size() && !fluidTags.getCompound(i).isEmpty() ? AEFluidKey.fromTag(registries, fluidTags.getCompound(i)) : null);
            }
            isItemBlackList = tag.getBoolean("isItemBlackList"); isFluidBlackList = tag.getBoolean("isFluidBlackList");
            ignoreItemNbt = !tag.contains("ignoreItemNbt") || tag.getBoolean("ignoreItemNbt");
            ignoreFluidNbt = !tag.contains("ignoreFluidNbt") || tag.getBoolean("ignoreFluidNbt");
        } finally { loading = false; rebuild(); }
    }
}
