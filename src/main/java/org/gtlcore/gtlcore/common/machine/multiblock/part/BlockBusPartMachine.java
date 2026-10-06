package org.gtlcore.gtlcore.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.ISubscription;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.SlotGroup;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/** A block-only 9 by 9 inventory, with automatic output through the front face. */
public class BlockBusPartMachine extends TieredIOPartMachine implements IMuiMachine {

    @Getter
    @SaveField
    private final NotifiableItemStackHandler inventory;

    @Nullable
    protected TickableSubscription autoIOSubs;
    @Nullable
    private ISubscription inventorySubs;

    public BlockBusPartMachine(BlockEntityCreationInfo info) {
        super(info, 6, IO.BOTH);
        inventory = attachTrait(createInventoryItemHandler());
    }

    protected NotifiableItemStackHandler createInventoryItemHandler() {
        return new NotifiableItemStackHandler(81, IO.BOTH, IO.BOTH)
                .setFilter(stack -> stack.getItem() instanceof BlockItem);
    }

    protected void updateInventorySubscription() {
        updateInventorySubscription(getFrontFacing());
    }

    private void updateInventorySubscription(Direction facing) {
        if (getLevel() == null || isRemote()) return;
        if (isWorkingEnabled() && !inventory.isEmpty() &&
                GTTransferUtils.hasAdjacentItemHandler(getLevel(), getBlockPos(), facing)) {
            autoIOSubs = subscribeServerTick(autoIOSubs, this::autoIO);
        } else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData,
                            PanelSyncManager syncManager, UISettings settings) {
        var slots = new SlotGroup("block_inventory", 9, 0, true);
        mainWidget.child(new Grid().coverChildren().center().margin(8)
                .gridOfSizeHeight(81, 9, (x, y, index) -> new ItemSlot()
                        .slot(SyncHandlers.itemSlot(inventory.storage, index)
                                .slotGroup(slots).accessibility(true, true))));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            inventorySubs = inventory.addChangedListener(this::updateInventorySubscription);
            scheduleForNextServerTick(this::updateInventorySubscription);
        }
    }

    @Override
    public void onUnload() {
        if (inventorySubs != null) {
            inventorySubs.unsubscribe();
            inventorySubs = null;
        }
        if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
        super.onUnload();
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateInventorySubscription();
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        updateInventorySubscription(newFacing);
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        super.setWorkingEnabled(workingEnabled);
        updateInventorySubscription();
    }

    public void autoIO() {
        if (getOffsetTimer() % 5 == 0) {
            if (isWorkingEnabled()) inventory.exportToNearby(getFrontFacing());
            updateInventorySubscription();
        }
    }
}
