package org.gtlcore.gtlcore.mixin.gtm.ae.machine;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEOutputPart;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.api.gui.AdvancedMEConfigurator;
import org.gtlcore.gtlcore.integration.ae2.NativeMEOutputPersistence;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.integration.ae2.machine.MEBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import java.util.List;

@Mixin(MEOutputBusPartMachine.class)
public abstract class MEOutputBusPartMachineMixin extends MEBusPartMachine implements IMEOutputPart, IModifiableSyncOffset, org.gtlcore.gtlcore.api.machine.trait.MEPart.IMENativeOutputBuffer {
    @Shadow(remap = false) private KeyStorage internalBuffer;
    @Unique @SaveField private int gtlcore$returnTime;
    @Unique private boolean gtlcore$bufferInDroppedMachine;

    protected MEOutputBusPartMachineMixin(BlockEntityCreationInfo info, IO io, com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler inventory) { super(info, io, inventory); }

    @WrapMethod(method = "autoIO", remap = false)
    private void gtlcore$returnOnCycle(Operation<Void> original) {
        if (!isWorkingEnabled()) return;
        try { if (isReturn()) original.call(); }
        finally { advanceReturnCycle(); }
    }
    @Override public KeyStorage gtlcore$getOutputBuffer() { return internalBuffer; }
    @WrapOperation(method = "autoIO", at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/integration/ae2/machine/MEOutputBusPartMachine;shouldSyncME()Z"), remap = false, require = 1)
    private boolean gtlcore$requestedReturn(MEOutputBusPartMachine instance, Operation<Boolean> original) { return isReturn(); }
    @Override public void returnStorage() { setTime(0); }
    @Override public int getTime() { return gtlcore$returnTime; }
    @Override public void setTime(int time) { gtlcore$returnTime = time; markAsChanged(); }

    @Override public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var builder = new AdvancedMEConfigurator(this::setMEUpdateInterval, this::getMEUpdateInterval)
                .attachTo(super.getPanelBuilder(data, sync, settings).addTitleBar(false), sync);
        return IMEOutputPart.attachRecipeLockable(builder, sync, this);
    }
    /** @author GTLCore @reason Preserve the complete native output list and exact long/component previews. */
    @Overwrite(remap = false)
    public void buildMainUI(brachy.modularui.widget.ParentWidget<?> parent, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        org.gtlcore.gtlcore.integration.ae2.gui.NativeMEOutputUI.build(parent, this, sync, internalBuffer, null);
    }
    @Override public void modifyDrops(List<ItemStack> drops) {
        super.modifyDrops(drops);
        gtlcore$bufferInDroppedMachine = drops.stream().anyMatch(stack ->
                stack.getItem() instanceof MetaMachineItem item && item.getBlock() == getBlockState().getBlock());
    }
    @WrapMethod(method = "onMachineDestroyed", remap = false)
    private void gtlcore$destroyWithSingleOwner(Operation<Void> original) {
        if (gtlcore$bufferInDroppedMachine) super.onMachineDestroyed();
        else original.call();
    }
    @Override public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        super.saveToItem(stack, registries);
        NativeMEOutputPersistence.write(stack, registries, internalBuffer, getTime(), getMEUpdateInterval());
    }
    @Override protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        NativeMEOutputPersistence.read(input.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY), getLevel().registryAccess(), internalBuffer, this);
    }
}
