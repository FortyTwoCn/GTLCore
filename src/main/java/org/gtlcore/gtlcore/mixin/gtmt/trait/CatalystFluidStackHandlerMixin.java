package org.gtlcore.gtlcore.mixin.gtmt.trait;

import org.gtlcore.gtlcore.api.recipe.ingredient.CatalystInputMatcher;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.hepdd.gtmthings.common.block.machine.trait.CatalystFluidStackHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(CatalystFluidStackHandler.class)
public abstract class CatalystFluidStackHandlerMixin extends NotifiableFluidTank {
    @Unique private final Object2LongOpenCustomHashMap<FluidStack> gtlcore$catalysts = new Object2LongOpenCustomHashMap<>(CatalystInputMatcher.FLUID_COMPONENT_KEYS);
    protected CatalystFluidStackHandlerMixin(int slots, int capacity, IO io, IO capabilityIO) { super(slots, capacity, io, capabilityIO); }
    @Inject(method = "<init>(IILcom/gregtechceu/gtceu/api/capability/recipe/IO;Lcom/gregtechceu/gtceu/api/capability/recipe/IO;)V", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$hideExternalCapabilities(int slots, int capacity, IO io, IO capabilityIO, CallbackInfo ci) { setCapabilityValidator(side -> false); }
    /** @author GTLCore @reason Cache exact native fluid components and satisfy only reusable catalyst checks. */
    @Overwrite(remap = false)
    public List<SizedFluidIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedFluidIngredient> left, boolean simulate) {
        return handlerIO == IO.IN ? CatalystInputMatcher.fluids(io, recipe, left, simulate, gtlcore$catalysts) : left;
    }
    @Override public void onContentsChanged() { super.onContentsChanged(); if (!isRemote()) gtlcore$rebuild(); }
    @Override public void onMachineLoad() { super.onMachineLoad(); if (!isRemote()) { gtlcore$rebuild(); notifyListeners(); } }
    @Unique private void gtlcore$rebuild() {
        gtlcore$catalysts.clear();
        for (var tank : getStorages()) {
            var stack = tank.getFluid();
            if (!stack.isEmpty()) gtlcore$catalysts.put(stack.copyWithAmount(1), Math.addExact(gtlcore$catalysts.getLong(stack), stack.getAmount()));
        }
    }
}
