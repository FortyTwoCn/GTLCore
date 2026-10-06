package org.gtlcore.gtlcore.mixin.gtm.machine;

import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.item.ItemStack;

@Mixin(ItemBusPartMachine.class)
public abstract class ItemBusPartMachineMixin {
    /** @author GTLCore @reason Preserve the original per-tier bus capacity. */
    @Overwrite(remap = false)
    protected static int getInventorySize(int tier) {
        return Math.multiplyExact(tier + 1, tier + 1);
    }

    // Keep the native non-consumable circuit trait and the native inventory filter.
    @Inject(method = "matchesFilter", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$excludeCircuits(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        var bus = (ItemBusPartMachine) (Object) this;
        if (bus.getInventory().handlerIO == IO.IN && IntCircuitBehaviour.isIntegratedCircuit(stack)) cir.setReturnValue(false);
    }

    @Inject(method = "setDistinct", at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$refreshCoreRoutes(boolean distinct, CallbackInfo ci) {
        for (var controller : ((ItemBusPartMachine) (Object) this).getControllers()) {
            if (controller instanceof IRecipeCapabilityMachine machine && !machine.isDistinct()) machine.upDate();
        }
    }
}
