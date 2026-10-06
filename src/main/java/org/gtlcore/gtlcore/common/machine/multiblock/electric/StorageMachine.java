package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import net.minecraft.world.item.ItemStack;

import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.SlotGroup;
import org.jetbrains.annotations.NotNull;

/** A recipe controller with an additional, non-recipe storage slot. */
public class StorageMachine extends WorkableElectricMultiblockMachine {

    @SaveField
    public final NotifiableItemStackHandler machineStorage;

    public StorageMachine(BlockEntityCreationInfo info, int slotLimit) {
        this(info, slotLimit, new RecipeLogic());
    }

    public StorageMachine(BlockEntityCreationInfo info, int slotLimit, RecipeLogic recipeLogic) {
        super(info, recipeLogic);
        if (slotLimit <= 0) throw new IllegalArgumentException("Storage slot limit must be positive");
        machineStorage = attachTrait(createMachineStorage(slotLimit));
    }

    protected NotifiableItemStackHandler createMachineStorage(int slotLimit) {
        return new NotifiableItemStackHandler(1, IO.NONE, IO.BOTH,
                slots -> new CustomItemStackHandler(1) {
                    @Override
                    public int getSlotLimit(int slot) { return slotLimit; }
                }).setFilter(this::filter);
    }

    protected boolean filter(@NotNull ItemStack stack) { return true; }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData,
                            PanelSyncManager syncManager, UISettings settings) {
        super.buildMainUI(mainWidget, guiData, syncManager, settings);
        mainWidget.child(new ItemSlot().right(10).bottom(10)
                .slot(SyncHandlers.itemSlot(machineStorage.storage, 0)
                        .slotGroup(new SlotGroup("machine_storage", 1, 0, true))
                        .accessibility(true, true)));
    }

    public ItemStack getMachineStorageItem() { return machineStorage.getStackInSlot(0); }
    public void setMachineStorageItem(ItemStack item) { machineStorage.setStackInSlot(0, item); }
    public boolean isEmpty() { return machineStorage.isEmpty(); }
}
