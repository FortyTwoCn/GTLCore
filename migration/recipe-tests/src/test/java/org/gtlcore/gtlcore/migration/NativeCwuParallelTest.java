package org.gtlcore.gtlcore.migration;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableComputationContainer;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import org.junit.jupiter.api.Test;
import java.util.Collection;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.gtlcore.gtlcore.migration.NativeCoreRecipeRunnerIntegrationTest.*;

/** Native capability and parallel dispatch use controlled optical provider budgets; real pipes have world tests. */
class NativeCwuParallelTest {
    private static final class Budget implements IOpticalComputationProvider {
        int amount, allocations;
        Budget(int amount) { this.amount = amount; }
        public int requestCWUt(int count, boolean simulate, Collection<IOpticalComputationProvider> seen) {
            if (!seen.add(this)) return 0;
            int result = Math.min(count, amount);
            if (!simulate) { amount -= result; allocations += result; }
            return result;
        }
        public int getMaxCWUt(Collection<IOpticalComputationProvider> seen) { return seen.add(this) ? amount : 0; }
        public boolean canBridge(Collection<IOpticalComputationProvider> seen) { return true; }
    }
    private static NotifiableComputationContainer receiver(IO io, Budget budget) {
        return new NotifiableComputationContainer(io, false) {
            @Override public int requestCWUt(int count, boolean simulate, Collection<IOpticalComputationProvider> seen) {
                return budget.requestCWUt(count, simulate, seen);
            }
        };
    }
    @Test void actualNativeParallelDispatchLimitsTickComputationAndLeavesTheProviderUnchanged() {
        var holder = new NativeHolder(); var budget = new Budget(1023);
        holder.addHandlerList(RecipeHandlerList.of(IO.IN, receiver(IO.IN, budget)));
        var recipe = GTRecipeBuilder.ofRaw().CWUt(128).build();
        assertEquals(7, CWURecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 100, true));
        assertEquals(7, ParallelLogic.getMaxByInput(holder, recipe, 100, List.of()));
        assertEquals(3, CWURecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 3, true));
        assertEquals(1023, budget.amount); assertEquals(0, budget.allocations);
    }
    @Test void duplicateHatchesAndSharedOpticalSourcesCannotAdvertiseTheSameBudgetTwice() {
        var holder = new NativeHolder(); var source = new Budget(1024); var first = receiver(IO.IN, source);
        holder.addHandlerList(RecipeHandlerList.of(IO.IN, first)); holder.addHandlerList(RecipeHandlerList.of(IO.IN, first, receiver(IO.IN, source)));
        var recipe = GTRecipeBuilder.ofRaw().CWUt(100).build();
        assertEquals(10, ParallelLogic.getMaxByInput(holder, recipe, 100, List.of()));
        assertEquals(1024, source.amount); assertEquals(0, source.allocations);
    }
    @Test void independentNativeBudgetsAggregateAboveIntWithoutOutputHandlersOrWrapping() {
        var holder = new NativeHolder();
        holder.addHandlerList(RecipeHandlerList.of(IO.IN, receiver(IO.IN, new Budget(Integer.MAX_VALUE)), receiver(IO.IN, new Budget(Integer.MAX_VALUE))));
        holder.addHandlerList(RecipeHandlerList.of(IO.OUT, receiver(IO.OUT, new Budget(Integer.MAX_VALUE))));
        var recipe = GTRecipeBuilder.ofRaw().CWUt(Integer.MAX_VALUE).build();
        assertEquals(2, CWURecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 10, true));
        assertEquals(0, CWURecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 0, true));
    }
    @Test void allRequiredContentsAreCountedAndMissingCwuPreservesNativeSkipSemantics() {
        var holder = new NativeHolder(); holder.addHandlerList(RecipeHandlerList.of(IO.IN, receiver(IO.IN, new Budget(1000))));
        var recipe = GTRecipeBuilder.ofRaw().perTick(true).inputCWU(100).inputCWU(200).build();
        assertEquals(3, CWURecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 100, true));
        assertEquals(Integer.MAX_VALUE, CWURecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 100, false));
        assertEquals(Integer.MAX_VALUE, CWURecipeCapability.CAP.getMaxParallelByInput(holder, GTRecipeBuilder.ofRaw().build(), 100, true));
        assertEquals(0, CWURecipeCapability.CAP.getMaxParallelByInput(new NativeHolder(), recipe, 100, true));
    }
}
