package org.gtlcore.gtlcore.mixin.gtm.ae.slot;

import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAESlot;
import appeng.api.stacks.AEFluidKey;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ExportOnlyAEFluidSlot.class)
public abstract class ExportOnlyAEFluidSlotMixin extends ExportOnlyAESlot {
    @Shadow(remap = false) public abstract FluidStack drain(int amount, FluidAction action);
    /** @author GTLCore @reason Physical fluid buffers must match data components too. */
    @Overwrite(remap = false) public FluidStack drain(FluidStack requested, FluidAction action) {
        return !requested.isEmpty() && stock != null && stock.what().equals(AEFluidKey.of(requested)) ?
                drain(requested.getAmount(), action) : FluidStack.EMPTY;
    }
}
