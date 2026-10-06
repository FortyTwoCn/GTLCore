package org.gtlcore.gtlcore.mixin.gtmt;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEOutputPart;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.api.gui.AdvancedMEConfigurator;
import org.gtlcore.gtlcore.integration.ae2.*;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.hepdd.gtmthings.common.block.machine.multiblock.part.appeng.MEOutputPartMachine;
import com.hepdd.gtmthings.api.machine.trait.InaccessibleInfiniteHandler;
import com.hepdd.gtmthings.api.machine.trait.InaccessibleInfiniteTank;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(MEOutputPartMachine.class)
public abstract class MEOutputPartMachineMixin extends TieredIOPartMachine implements IMEOutputPart, IModifiableSyncOffset, IMuiMachine, IGridConnectedMachine {
    @Unique @SaveField private int gtlcore$returnTime;
    @Unique @SaveField private int gtlcore$syncOffset;
    protected MEOutputPartMachineMixin(BlockEntityCreationInfo info, int tier, IO io) { super(info, tier, io); }

    @WrapMethod(method = "autoIO", remap = false)
    private void gtlcore$returnOnCycle(Operation<Void> original) {
        if (!isWorkingEnabled()) return;
        try { if (isReturn()) original.call(); }
        finally { advanceReturnCycle(); }
    }
    @WrapOperation(method = "autoIO", at = @At(value = "INVOKE", target = "Lcom/hepdd/gtmthings/common/block/machine/multiblock/part/appeng/MEOutputPartMachine;shouldSyncME()Z"), remap = false, require = 1)
    private boolean gtlcore$requestedReturn(MEOutputPartMachine instance, Operation<Boolean> original) { return isReturn(); }
    @Override public boolean shouldSyncME() { return getOffsetTimer() % returnInterval() == 0; }
    @Override public void returnStorage() { setTime(0); }
    @Override public int getTime() { return gtlcore$returnTime; }
    @Override public void setTime(int time) { gtlcore$returnTime = time; markAsChanged(); }
    @Override public int getMEUpdateInterval() { return gtlcore$syncOffset; }
    @Override public void setMEUpdateInterval(int interval) {
        if (interval < 0) throw new IllegalArgumentException("ME refresh interval must not be negative");
        gtlcore$syncOffset = interval; markAsChanged();
    }
    public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var builder = new AdvancedMEConfigurator(this::setMEUpdateInterval, this::getMEUpdateInterval)
                .attachTo(IMuiMachine.super.getPanelBuilder(data, sync, settings).addTitleBar(false), sync);
        return IMEOutputPart.attachRecipeLockable(builder, sync, this);
    }
    /** @author GTLCore @reason Retain both native ledgers in a bounded component-aware output menu. */
    @Overwrite(remap = false)
    public void buildMainUI(brachy.modularui.widget.ParentWidget<?> parent, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var buffers = (MEOutputPartMachineAccessor) (Object) this;
        org.gtlcore.gtlcore.integration.ae2.gui.NativeMEOutputUI.build(parent, this, sync, buffers.gtlcore$getInternalBuffer(), buffers.gtlcore$getInternalTankBuffer());
    }
    @Inject(method = "onLoad", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$nativeSides(CallbackInfo ci) { AEGridMachineSettings.applyExposedSides(this, getFrontFacing()); }
    @Inject(method = "onRotated", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$nativeRotatedSides(Direction oldFacing, Direction newFacing, CallbackInfo ci) { AEGridMachineSettings.applyExposedSides(this, newFacing); }
    @Inject(method = "collectImplicitComponents", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$saveNativeCycle(DataComponentMap.Builder components, CallbackInfo ci) { NativeMEOutputPersistence.writeSettings(components, this, getMEUpdateInterval()); }
    @Inject(method = "applyImplicitComponents", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$restoreNativeCycle(DataComponentInput input, CallbackInfo ci) { NativeMEOutputPersistence.readSettings(input.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY), this); }

    @Mixin(InaccessibleInfiniteHandler.class)
    public static abstract class InaccessibleInfiniteHandlerMixin {
        @Shadow(remap = false) @Final private KeyStorage buffer;
        /** @author GTLCore @reason Preserve long output quantities and reserve repeated component keys. */
        @Overwrite(remap = false)
        public List<SizedIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedIngredient> left, boolean simulate) {
            if (io != IO.OUT) return left;
            return NativeMEOutputRecipeTransfer.accept(buffer, left, simulate,
                    ingredient -> AEOutputIngredients.item(ingredient, simulate),
                    stack -> LongIngredient.create(((AEItemKey) stack.what()).toStack(1), stack.amount()));
        }
    }
    @Mixin(InaccessibleInfiniteTank.class)
    public static abstract class InaccessibleInfiniteTankMixin {
        @Shadow(remap = false) @Final private KeyStorage buffer;
        /** @author GTLCore @reason Preserve long output quantities and reserve repeated component keys. */
        @Overwrite(remap = false)
        public List<SizedFluidIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedFluidIngredient> left, boolean simulate) {
            if (io != IO.OUT) return left;
            return NativeMEOutputRecipeTransfer.accept(buffer, left, simulate,
                    ingredient -> AEOutputIngredients.fluid(ingredient, simulate),
                    stack -> LongFluidIngredient.create(((AEFluidKey) stack.what()).toStack(1), stack.amount()));
        }
    }
}
