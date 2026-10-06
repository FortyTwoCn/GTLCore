package org.gtlcore.gtlcore.api.recipe.ingredient;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderFluidIngredient;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Long residuals over native int tanks, including a complete simulated inventory across ingredients. */
public final class LongFluidRecipeHandler {
    private LongFluidRecipeHandler() {}

    public static List<SizedFluidIngredient> handle(IO io, @Nullable GTRecipe recipe,
                                                    List<SizedFluidIngredient> left, boolean simulate,
                                                    IO handlerIO, CustomFluidTank[] storages,
                                                    boolean allowSameFluids, @Nullable MetaMachine machine) {
        if (io != handlerIO || (io != IO.IN && io != IO.OUT)) return left;
        Runnable[] listeners = new Runnable[storages.length];
        boolean[] changed = new boolean[storages.length];
        FluidStack[] visited = new FluidStack[storages.length];
        for (int i = 0; i < storages.length; i++) {
            listeners[i] = storages[i].getOnContentsChanged();
            storages[i].setOnContentsChanged(() -> {});
        }
        FluidAction action = simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE;
        try {
            for (var it = left.listIterator(); it.hasNext();) {
                var sized = it.next();
                var matching = LongFluidIngredient.matchingIngredient(sized);
                FluidStack[] fluids;
                long amount;
                if (matching instanceof IntProviderFluidIngredient provider && simulate) {
                    fluids = new FluidStack[] { provider.getMaxSizeStack() };
                    amount = provider.getMaxRoll();
                } else {
                    fluids = sized.getFluids();
                    amount = LongFluidIngredient.amount(sized);
                }
                if (fluids.length == 0 || fluids[0].isEmpty()) {
                    it.remove();
                    continue;
                }
                FluidStack output = fluids[0].copy();
                if (io == IO.OUT && recipe != null) recipe.mutateOutput(output);
                int existing = -1;
                if (io == IO.OUT && !allowSameFluids) {
                    for (int tank = 0; tank < storages.length; tank++) {
                        var current = visited[tank] == null ? storages[tank].getFluid() : visited[tank];
                        if (!current.isEmpty() && FluidStack.isSameFluidSameComponents(current, output)) {
                            existing = tank;
                            break;
                        }
                    }
                }
                for (int tank = 0; tank < storages.length && amount > 0; tank++) {
                    if (existing >= 0 && tank != existing) continue;
                    var current = visited[tank] == null ? storages[tank].getFluid() : visited[tank];
                    int count = current.getAmount();
                    if (io == IO.IN) {
                        if (current.isEmpty() || !matching.test(current)) continue;
                        var drained = storages[tank].drain((int) Math.min(count, amount), action);
                        if (drained.isEmpty()) continue;
                        visited[tank] = current.copyWithAmount(count - drained.getAmount());
                        changed[tank] = true;
                        amount -= drained.getAmount();
                        if (!simulate && machine != null) recordConsumed(machine, recipe, drained);
                    } else {
                        if (!current.isEmpty() && !FluidStack.isSameFluidSameComponents(current, output)) continue;
                        int free = storages[tank].getCapacity() - count;
                        if (free <= 0) continue;
                        int filled = storages[tank].fill(output.copyWithAmount((int) Math.min(free, amount)), action);
                        if (filled <= 0) continue;
                        visited[tank] = output.copyWithAmount(count + filled);
                        changed[tank] = true;
                        amount -= filled;
                        if (!allowSameFluids) break;
                    }
                }
                if (amount <= 0) it.remove();
                else if (matching instanceof IntProviderFluidIngredient provider) {
                    it.set(new SizedFluidIngredient(provider.getInner(), Math.toIntExact(amount)));
                } else it.set(LongFluidIngredient.withAmount(sized, amount));
            }
            return left;
        } finally {
            for (int i = 0; i < storages.length; i++) storages[i].setOnContentsChanged(listeners[i]);
            if (!simulate) {
                Set<Runnable> notified = Collections.newSetFromMap(new IdentityHashMap<>());
                for (int i = 0; i < storages.length; i++) {
                    if (changed[i] && notified.add(listeners[i])) listeners[i].run();
                }
            }
        }
    }

    private static void recordConsumed(MetaMachine machine, @Nullable GTRecipe recipe, FluidStack stack) {
        recordConsumed(machine, recipe, SizedFluidIngredient.of(stack.copy()));
    }

    public static void recordConsumed(MetaMachine machine, @Nullable GTRecipe recipe, SizedFluidIngredient ingredient) {
        if (machine instanceof MultiblockPartMachine part) {
            for (MultiblockControllerMachine controller : part.getControllers()) {
                RecipeLogic logic = controller.getTrait(RecipeLogic.class);
                if (logic != null && logic.getStartingRecipe() == recipe) {
                    logic.getConsumedInputs().addConsumedInput(FluidRecipeCapability.CAP,
                            ingredient);
                }
            }
        } else machine.getTraitOptional(RecipeLogic.class).map(RecipeLogic::getConsumedInputs)
                .ifPresent(inputs -> inputs.addConsumedInput(FluidRecipeCapability.CAP,
                        ingredient));
    }
}
