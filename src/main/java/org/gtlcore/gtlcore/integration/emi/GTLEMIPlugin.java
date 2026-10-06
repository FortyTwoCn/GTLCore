package org.gtlcore.gtlcore.integration.emi;

import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;

@EmiEntrypoint
public class GTLEMIPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.setDefaultComparison(EmiStack.of(GTItems.TURBINE_ROTOR.asItem()), Comparison.compareComponents());
        registry.setDefaultComparison(EmiStack.of(GTItems.PROGRAMMED_CIRCUIT.asItem()), Comparison.compareComponents());

        for (int i = 0; i <= 32; i++) {
            registry.addEmiStack(EmiStack.of(IntCircuitBehaviour.stack(i)));
        }
    }
}
