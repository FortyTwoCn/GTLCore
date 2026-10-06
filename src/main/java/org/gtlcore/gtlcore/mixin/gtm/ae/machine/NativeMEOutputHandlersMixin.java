package org.gtlcore.gtlcore.mixin.gtm.ae.machine;

import org.gtlcore.gtlcore.integration.ae2.AEOutputIngredients;
import org.gtlcore.gtlcore.integration.ae2.NativeMEOutputRecipeTransfer;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMENativeOutputBuffer;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

public final class NativeMEOutputHandlersMixin {
    @Mixin(targets = "com.gregtechceu.gtceu.integration.ae2.machine.MEOutputBusPartMachine$InaccessibleInfiniteHandler", remap = false)
    public static abstract class Items extends NotifiableItemStackHandler {
        @Shadow(remap = false) private KeyStorage internalBuffer;
        protected Items(int slots, IO io, IO capability) { super(slots, io, capability); }
        @Override public List<SizedIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedIngredient> left, boolean simulate) {
            if (io != IO.OUT) return left;
            return NativeMEOutputRecipeTransfer.accept(internalBuffer, left, simulate,
                    ingredient -> AEOutputIngredients.item(ingredient, simulate),
                    stack -> LongIngredient.create(((AEItemKey) stack.what()).toStack(1), stack.amount()));
        }
    }
    @Mixin(targets = "com.gregtechceu.gtceu.integration.ae2.machine.MEOutputHatchPartMachine$InaccessibleInfiniteTank", remap = false)
    public static abstract class Fluids extends NotifiableFluidTank {
        @Unique private KeyStorage gtlcore$buffer;
        protected Fluids(int slots, int capacity, IO io, IO capability) { super(slots, capacity, io, capability); }
        @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 1)
        private void gtlcore$captureOwnedLedger(MEOutputHatchPartMachine owner, CallbackInfo ci) {
            gtlcore$buffer = ((IMENativeOutputBuffer) owner).gtlcore$getOutputBuffer();
        }
        /** @author GTLCore @reason Preserve long fluid outputs and reserve repeated component keys. */
        @Overwrite(remap = false)
        public List<SizedFluidIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedFluidIngredient> left, boolean simulate) {
            if (io != IO.OUT) return left;
            return NativeMEOutputRecipeTransfer.accept(gtlcore$buffer, left, simulate,
                    ingredient -> AEOutputIngredients.fluid(ingredient, simulate),
                    stack -> LongFluidIngredient.create(((AEFluidKey) stack.what()).toStack(1), stack.amount()));
        }
    }
}
