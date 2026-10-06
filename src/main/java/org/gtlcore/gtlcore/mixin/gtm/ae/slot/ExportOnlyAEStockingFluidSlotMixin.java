package org.gtlcore.gtlcore.mixin.gtm.ae.slot;

import org.gtlcore.gtlcore.api.machine.trait.MEStock.IMESlot;
import org.gtlcore.gtlcore.mixin.gtm.ae.machine.MEHatchPartMachineAccessor;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

@Mixin(targets = "com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine$ExportOnlyAEStockingFluidSlot", remap = false)
public abstract class ExportOnlyAEStockingFluidSlotMixin extends ExportOnlyAEFluidSlot implements IMESlot {
    @Shadow @Final private MEStockingHatchPartMachine this$0;
    @Unique private Runnable gtlcore$onConfigChanged = () -> {};

    @Override public void setOnConfigChanged(Runnable listener) { gtlcore$onConfigChanged = listener; }
    @Override public Runnable getOnConfigChanged() { return gtlcore$onConfigChanged; }
    @Override public void setConfig(@Nullable GenericStack config) {
        super.setConfig(config);
        if (!this$0.isRemote()) gtlcore$onConfigChanged.run();
    }
    @Override public void setConfigWithoutNotify(@Nullable GenericStack config) { this.config = config; }
    @Override public FluidStack drain(FluidStack requested, FluidAction action) {
        return !requested.isEmpty() && config != null && config.what().equals(AEFluidKey.of(requested)) ?
                drain(requested.getAmount(), action) : FluidStack.EMPTY;
    }
    /** @author GTLCore @reason Guard live access and clamp stale virtual display quantities. */
    @Overwrite public FluidStack drain(int amount, FluidAction action) {
        if (amount <= 0 || !this$0.isWorkingEnabled() || !this$0.isOnline() || config == null ||
                !(config.what() instanceof AEFluidKey key)) return FluidStack.EMPTY;
        var grid = this$0.getMainNode().getGrid();
        if (grid == null) return FluidStack.EMPTY;
        long extracted = grid.getStorageService().getInventory().extract(key, amount,
                action.simulate() ? Actionable.SIMULATE : Actionable.MODULATE,
                ((MEHatchPartMachineAccessor) this$0).getActionSource());
        if (extracted <= 0) return FluidStack.EMPTY;
        if (action.execute()) {
            boolean emptyDisplay = stock == null;
            long remaining = stock == null || !stock.what().equals(key) ? 0 : Math.max(0, stock.amount() - extracted);
            setStock(remaining == 0 ? null : new GenericStack(key, remaining));
            if (emptyDisplay) getOnContentsChanged().run();
        }
        return key.toStack(Math.toIntExact(extracted));
    }
}
