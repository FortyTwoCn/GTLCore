package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.machine.trait.AECraft.IMECraftPatternContainer;
import org.gtlcore.gtlcore.integration.ae2.MolecularAssemblerPatternHelper;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import appeng.crafting.pattern.EncodedPatternItem;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.SlotGroup;
import lombok.Getter;

/** Original 108-slot molecular pattern container, with native inventory persistence and ModularUI slots. */
public class MECraftPatternContainerPartMachine extends MultiblockPartMachine implements IMECraftPatternContainer, IMuiMachine {
    private static final int SLOT_COUNT = 12 * 9;
    private static final int UI_COLUMNS = 14;

    @Getter @SaveField @SyncToClient
    protected final CustomItemStackHandler patternInventory;

    public MECraftPatternContainerPartMachine(BlockEntityCreationInfo info) {
        super(info);
        patternInventory = new CustomItemStackHandler(SLOT_COUNT);
        patternInventory.setFilter(stack -> MolecularAssemblerPatternHelper.molecularFilter(stack, getLevel()));
        patternInventory.setOnContentsChanged(() -> {
            markAsChanged();
            syncDataHolder.markClientSyncFieldDirty("patternInventory");
        });
    }

    @Override public int getSlots() { return patternInventory.getSlots(); }

    @Override public void onMachineDestroyed() {
        if (!isRemote()) patternInventory.dropInventoryInWorld(getLevel(), getBlockPos());
        super.onMachineDestroyed();
    }

    @Override public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData,
                                      PanelSyncManager syncManager, UISettings settings) {
        var group = new SlotGroup("molecular_patterns", UI_COLUMNS, 0, true);
        mainWidget.child(new Grid().coverChildren().margin(8)
                .minElementMargin(0, 0).minColWidth(18).minRowHeight(18)
                .gridOfSizeWidth(SLOT_COUNT, UI_COLUMNS, (x, y, index) -> {
                    if (index >= SLOT_COUNT) return null;
                    var slot = new ItemSlot();
                    slot.setItemHook(stack -> {
                        if (stack.getItem() instanceof EncodedPatternItem<?> pattern) {
                            var output = pattern.getOutput(stack);
                            if (!output.isEmpty()) return output;
                        }
                        return stack;
                    });
                    return slot.slot(SyncHandlers.itemSlot(patternInventory, index).slotGroup(group).accessibility(true, true))
                            .background(GTGuiTextures.SLOT, GTGuiTextures.PATTERN_OVERLAY);
                }));
    }

    @Override public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return !isFormed();
    }

    @Override public boolean canShared(MultiblockControllerMachine controller, String substructureName) { return false; }
}
