package org.gtlcore.gtlcore.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.sync_system.annotations.*;
import com.gregtechceu.gtceu.common.data.GTDamageTypes;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.slot.ItemSlot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;

/** Native grinding-ball inventory with working-state synchronization and the original active-use/drop rules. */
public class BallHatchPartMachine extends MultiblockPartMachine implements IMuiMachine {
    @SaveField @SyncToClient @RerenderOnChanged private boolean isWorking;
    @SaveField private final NotifiableItemStackHandler inventory;
    public BallHatchPartMachine(BlockEntityCreationInfo info, Object... args) {
        super(info);
        inventory = attachTrait(createInventoryItemHandler(args));
        inventory.shouldDropInventoryInWorld(false);
    }
    protected NotifiableItemStackHandler createInventoryItemHandler(Object... args) {
        return new NotifiableItemStackHandler(1, IO.NONE, IO.BOTH)
                .setFilter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains("kubejs:grindball_"));
    }
    public NotifiableItemStackHandler getInventory() { return inventory; }
    public boolean isWorking() { return isWorking; }
    private void setWorking(boolean working) {
        if (isWorking == working) return;
        isWorking = working; markAsChanged(); syncDataHolder.markClientSyncFieldDirty("isWorking");
    }
    @Override public boolean beforeWorking(WorkableMultiblockMachine controller) { setWorking(true); return super.beforeWorking(controller); }
    @Override public void afterWorking(WorkableMultiblockMachine controller) { setWorking(false); super.afterWorking(controller); }
    @Override public void removedFromController(MultiblockControllerMachine controller) {
        super.removedFromController(controller); if (getControllers().isEmpty()) setWorking(false);
    }
    @Override public InteractionResult onUse(ExtendedUseOnContext context) {
        if (!isRemote() && isWorking && !context.getPlayer().isCreative()) {
            context.getPlayer().hurt(new net.minecraft.world.damagesource.DamageSource(context.getLevel().registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(GTDamageTypes.TURBINE)), 40);
            return InteractionResult.FAIL;
        }
        return super.onUse(context);
    }
    @Override public void onMachineDestroyed() {
        if (!isWorking) inventory.dropInventoryInWorld();
        super.onMachineDestroyed();
    }
    @Override public void buildMainUI(ParentWidget<?> main, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var slot = SyncHandlers.itemSlot(inventory.storage, 0).accessibility(true, true);
        var handler = new brachy.modularui.value.sync.ItemSlotSyncHandler(slot);
        sync.syncValue("grindBallInventory", handler);
        main.child(new ParentWidget<>().size(34)
                .child(new ItemSlot().name("grindBallSlot").syncHandler(handler).pos(8, 8).background(GTGuiTextures.SLOT)));
    }
}
