package org.gtlcore.gtlcore.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.item.behavior.TurbineRotorBehaviour;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.SlotGroup;
import net.minecraft.network.chat.Component;

/** Native accessible reserve rotor inventory. The attached trait saves and drops its contents once. */
public class RotorHatchPartMachine extends MultiblockPartMachine implements IMuiMachine {
    @SaveField private final NotifiableItemStackHandler inventory;
    public RotorHatchPartMachine(BlockEntityCreationInfo info, Object... args) {
        super(info);
        inventory = attachTrait(new NotifiableItemStackHandler(1, IO.NONE, IO.BOTH)
                .setFilter(stack -> TurbineRotorBehaviour.getBehaviour(stack) != null));
    }
    public NotifiableItemStackHandler getInventory() { return inventory; }
    @Override public void buildMainUI(ParentWidget<?> main, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        main.child(new ItemSlot().center().background(GTGuiTextures.SLOT, GTGuiTextures.TURBINE_OVERLAY)
                .slot(SyncHandlers.itemSlot(inventory.storage, 0).slotGroup(new SlotGroup("reserve_rotor", 1, 0, true)).accessibility(true, true))
                .tooltip(tooltip -> tooltip.add(Text.comp(Component.translatable("tooltip.gtlcore.turbine_rotor_only")))));
    }
}
