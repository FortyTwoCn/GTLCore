package org.gtlcore.gtlcore.common.machine;

import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder;


import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import org.gtlcore.gtlcore.integration.ae2.AEGridMachineSettings;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.ItemSlotSyncHandler;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.SlotGroup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;

/**
 * Publishes an unlimited virtual ingredient to the ME network for every real stack parked inside.
 * <p>
 * Adapted from GTOCore's {@code VirtualItemProviderMachine} (com.gtocore.common.machine.noenergy).
 */
public class VirtualIngredientSupplyMachine extends MetaMachine
                                            implements IMuiMachine, MEStorage,
                                            IGridConnectedMachine, IStorageProvider {

    public static final int SLOT_COUNT = 288;
    private static final int COLS = 9;
    private static final int SLOT_SIZE = 18;
    /** Caps how much the crafting planner will pull from here, so anything short of unlimited caps order size. */
    private static final long PUBLISHED_AMOUNT = Long.MAX_VALUE;

    @SaveField
    private final NotifiableItemStackHandler inventory;

    @SaveField
    private final GridNodeHolder nodeHolder;

    @SyncToClient
    private boolean isOnline;

    /** Derived from the inventory, so rebuilt on change rather than persisted. */
    private final Object2LongMap<AEKey> published = new Object2LongOpenHashMap<>();

    private KeyCounter cachedStacks;
    private boolean dirty = true;

    public VirtualIngredientSupplyMachine(BlockEntityCreationInfo holder) {
        super(holder);
        // IO.NONE keeps recipe logic from seeing this as machine input or output.
        this.inventory = attachTrait(new NotifiableItemStackHandler(SLOT_COUNT, IO.NONE, IO.BOTH));
        this.nodeHolder = attachTrait(new GridNodeHolder(this));
        getMainNode().addService(IStorageProvider.class, this);
        this.inventory.addChangedListener(this::rebuildPublishedKeys);
    }

    @Override
    public void onLoad() {
        AEGridMachineSettings.applyExposedSides(this, getFrontFacing());
        super.onLoad();
        inventory.notifyListeners();
        rebuildPublishedKeys();
    }

    private void rebuildPublishedKeys() {
        published.clear();
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            ItemStack wrapper;
            if (VirtualIngredientBehavior.isVirtualIngredient(stack)) {
                if (!VirtualIngredientBehavior.isBound(stack)) continue;
                // Sealed in place: an editable wrapper here would turn one locked copy into unlimited real material.
                VirtualIngredientBehavior.mark(stack);
                wrapper = VirtualIngredientBehavior.canonicalStack(stack);
            } else {
                wrapper = VirtualIngredientBehavior.wrap(stack);
            }
            if (!wrapper.isEmpty()) published.put(AEItemKey.of(wrapper), PUBLISHED_AMOUNT);
        }
        dirty = true;
        if (cachedStacks != null) cachedStacks.clear();
        markAsChanged();
        getMainNode().ifPresent((grid, node) -> {
            IStorageProvider.requestUpdate(getMainNode());
            grid.getStorageService().invalidateCache();
        });
    }

    // ==================== AE storage ====================

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        // Lowest priority: this must never win over real storage for anything.
        storageMounts.mount(this, Integer.MAX_VALUE - 1);
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return what instanceof AEItemKey itemKey && isOwnWrapper(itemKey);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !(what instanceof AEItemKey itemKey) || !isOwnWrapper(itemKey)) return 0;
        return amount;
    }

    private static boolean isOwnWrapper(AEItemKey itemKey) {
        ItemStack stack = itemKey.getReadOnlyStack();
        return VirtualIngredientBehavior.isVirtualIngredient(stack) && VirtualIngredientBehavior.isMarked(stack);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        // Never decrements: wrappers are tokens, and the payload is not reachable from here.
        if (amount <= 0 || !(what instanceof AEItemKey itemKey) || !isOwnWrapper(itemKey)) return 0;
        if (published.containsKey(what)) return amount;
        // Old patterns or manually configured wrappers can retain empty item/fluid
        // UI tags and different payload counts. Accept the same sealed payload;
        // preserve the payload's own NBT, and never supply an editable wrapper.
        ItemStack canonical = VirtualIngredientBehavior.canonicalStack(itemKey.getReadOnlyStack());
        return !canonical.isEmpty() && published.containsKey(AEItemKey.of(canonical)) ? amount : 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var entry : Object2LongMaps.fastIterable(published)) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public KeyCounter getAvailableStacks() {
        if (cachedStacks == null) {
            cachedStacks = new KeyCounter();
            dirty = true;
        }
        if (dirty) {
            cachedStacks.clear();
            getAvailableStacks(cachedStacks);
            cachedStacks.removeEmptySubmaps();
            dirty = false;
        }
        return cachedStacks;
    }

    @Override
    public Component getDescription() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    // ==================== Grid ====================

    @Override
    public IManagedGridNode getMainNode() {
        return nodeHolder.getMainNode();
    }

    @Override
    public boolean isOnline() {
        return isOnline;
    }

    @Override
    public void setOnline(boolean online) {
        if (isOnline == online) return;
        this.isOnline = online;
        syncDataHolder.markClientSyncFieldDirty("isOnline");
    }

    // ==================== Persistence ====================

    @Override
    public void onRotated(@NotNull Direction oldFacing, @NotNull Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        AEGridMachineSettings.applyExposedSides(this, newFacing);
    }

    // Drop the backing material before the machine item is serialized, so it cannot be duplicated on placement.
    @Override
    public void modifyDrops(List<ItemStack> drops) {
        inventory.storage.dropInventoryInWorld(getLevel(), getBlockPos());
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        var data = input.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.contains("gtlcore_virtual_inventory")) inventory.storage.deserializeNBT(getLevel().registryAccess(), data.getCompound("gtlcore_virtual_inventory"));
        rebuildPublishedKeys();
    }

    @Override
    public void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        // The vanilla container component has a 256-slot limit; this machine owns 288 real slots.
        var data = components.build().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.put("gtlcore_virtual_inventory", inventory.storage.serializeNBT(getLevel().registryAccess()));
        components.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    public NotifiableItemStackHandler getInventory() { return inventory; }

    @Override
    public void buildMainUI(ParentWidget<?> parent, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var group = new SlotGroup("virtualSupplyInventory", COLS, false);
        sync.registerSlotGroup(group);
        parent.child(new Grid().size(170, 130).scrollable(new brachy.modularui.widget.scroll.VerticalScrollData())
                .minElementMargin(0, 0).minColWidth(SLOT_SIZE).minRowHeight(SLOT_SIZE)
                .gridOfSizeWidth(SLOT_COUNT, COLS, (x, y, slot) -> {
                    var value = new ItemSlotSyncHandler(SyncHandlers.itemSlot(inventory.storage, slot).slotGroup(group));
                    sync.syncValue("virtualSupplySlot", slot, value);
                    return new ItemSlot().syncHandler(value);
                }));
    }
}
