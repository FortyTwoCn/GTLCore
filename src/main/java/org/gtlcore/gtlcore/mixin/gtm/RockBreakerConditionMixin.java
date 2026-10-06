package org.gtlcore.gtlcore.mixin.gtm;

import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.recipe.condition.AdjacentFluidCondition;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;
import java.util.function.Supplier;

/** Core multiblocks inspect input tanks; original data-driven single machines inspect horizontal fluids. */
@Mixin(AdjacentFluidCondition.class)
public abstract class RockBreakerConditionMixin {
    @Shadow(remap = false) @Final private List<Supplier<HolderSet<Fluid>>> fluids;

    @Inject(method = "testCondition", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$checkRockFluids(GTRecipe recipe, RecipeLogic logic, CallbackInfoReturnable<Boolean> cir) {
        var machine = logic.getMachine();
        boolean legacy = fluids.isEmpty() && (recipe.data.contains("fluidA") || recipe.data.contains("fluidB"));
        if (!legacy && !(machine instanceof WorkableElectricMultiblockMachine)) return;
        var required = legacy ? gtlcore$originalRequirements(recipe) : ((AdjacentFluidCondition) (Object) this).getOrInitFluids(recipe);
        boolean matched = required != null;
        if (matched) for (var group : required) {
            boolean present = false;
            if (machine instanceof WorkableElectricMultiblockMachine holder) {
                for (var handler : holder.getCapabilitiesFlat(IO.IN, FluidRecipeCapability.CAP)) {
                    if (!(handler instanceof NotifiableFluidTank tank) || tank.getHandlerIO() != IO.IN) continue;
                    for (int slot = 0; slot < tank.getTanks(); slot++) {
                        var stack = tank.getFluidInTank(slot);
                        if (!stack.isEmpty() && group.contains(stack.getFluid().builtInRegistryHolder())) { present = true; break; }
                    }
                    if (present) break;
                }
            } else if (machine.getLevel() != null) {
                for (var side : Direction.Plane.HORIZONTAL) {
                    var fluid = machine.getLevel().getFluidState(machine.getBlockPos().relative(side)).getType();
                    if (group.contains(fluid.builtInRegistryHolder())) { present = true; break; }
                }
            }
            if (!present) { matched = false; break; }
        }
        if (machine instanceof IRecipeLogicMachine holder)
            RecipeResult.of(holder, matched ? RecipeResult.SUCCESS : RecipeResult.FAIL_LACK_FLUID);
        cir.setReturnValue(matched);
    }

    @Unique private static @Nullable List<HolderSet<Fluid>> gtlcore$originalRequirements(GTRecipe recipe) {
        List<HolderSet<Fluid>> required = new ArrayList<>(2);
        for (String key : List.of("fluidA", "fluidB")) {
            if (!recipe.data.contains(key, Tag.TAG_STRING)) return null;
            var id = ResourceLocation.tryParse(recipe.data.getString(key));
            var fluid = id == null ? null : BuiltInRegistries.FLUID.getOptional(id).orElse(null);
            if (fluid == null || fluid == Fluids.EMPTY) return null;
            required.add(HolderSet.direct(fluid.builtInRegistryHolder()));
        }
        return required;
    }
}
