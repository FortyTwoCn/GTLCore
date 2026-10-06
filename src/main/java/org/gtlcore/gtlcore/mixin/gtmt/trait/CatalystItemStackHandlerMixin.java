package org.gtlcore.gtlcore.mixin.gtmt.trait;

import org.gtlcore.gtlcore.api.recipe.ingredient.CatalystInputMatcher;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;
import com.hepdd.gtmthings.common.block.machine.trait.CatalystItemStackHandler;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(CatalystItemStackHandler.class)
public abstract class CatalystItemStackHandlerMixin extends NotifiableItemStackHandler {
    @Unique private final Object2LongOpenCustomHashMap<ItemStack> gtlcore$catalysts = new Object2LongOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount());
    protected CatalystItemStackHandlerMixin(int slots, IO io, IO capabilityIO) { super(slots, io, capabilityIO); }
    @Inject(method = "<init>(ILcom/gregtechceu/gtceu/api/capability/recipe/IO;Lcom/gregtechceu/gtceu/api/capability/recipe/IO;)V", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$hideExternalCapabilities(int slots, IO io, IO capabilityIO, CallbackInfo ci) { setCapabilityValidator(side -> false); }
    /** @author GTLCore @reason Cache exact component quantities and satisfy only reusable catalyst checks. */
    @Overwrite(remap = false)
    public List<SizedIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedIngredient> left, boolean simulate) {
        return handlerIO == IO.IN ? CatalystInputMatcher.items(io, recipe, left, simulate, gtlcore$catalysts) : left;
    }
    @Override public void onContentsChanged() { super.onContentsChanged(); if (!isRemote()) gtlcore$rebuild(); }
    @Override public void onMachineLoad() { super.onMachineLoad(); if (!isRemote()) { gtlcore$rebuild(); notifyListeners(); } }
    @Unique private void gtlcore$rebuild() {
        gtlcore$catalysts.clear();
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            var stack = storage.getStackInSlot(slot);
            if (!stack.isEmpty()) gtlcore$catalysts.put(stack.copyWithCount(1), Math.addExact(gtlcore$catalysts.getLong(stack), stack.getCount()));
        }
    }
}
