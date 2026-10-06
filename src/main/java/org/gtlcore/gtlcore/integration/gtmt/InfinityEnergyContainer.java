package org.gtlcore.gtlcore.integration.gtmt;

import org.gtlcore.gtlcore.api.capability.IInt128EnergyContainer;
import org.gtlcore.gtlcore.utils.datastructure.Int128;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import net.minecraft.core.Direction;
import java.util.List;

/** Creative energy remains full and is supplied directly to bound multiblock recipe handlers. */
public class InfinityEnergyContainer extends NotifiableEnergyContainer implements IInt128EnergyContainer {
    public InfinityEnergyContainer(long capacity, long inputVoltage, long inputAmperage, long outputVoltage, long outputAmperage) {
        super(capacity, inputVoltage, inputAmperage, outputVoltage, outputAmperage);
    }

    @Override
    public List<EnergyStack> handleRecipeInner(IO io, GTRecipe recipe, List<EnergyStack> left, boolean simulate) {
        if (io == IO.IN && getInputAmperage() > 0) left.clear();
        return left;
    }

    @Override
    public long changeEnergy(long requested) {
        long accepted = requested < 0 ? Math.max(-getEnergyCapacity(), requested) : 0;
        addEnergyPerSec(accepted);
        return accepted;
    }

    @Override public void checkOutputSubscription() {}
    @Override public void serverTick() {}
    @Override public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) { return 0; }
    @Override public boolean outputsEnergy(Direction side) { return false; }
    @Override public boolean inputsEnergy(Direction side) { return false; }
    @Override public long getEnergyStored() { return getEnergyCapacity(); }
    @Override public Int128 getInt128EnergyStored() { return new Int128(getEnergyStored()); }
    @Override public Int128 getInt128EnergyCapacity() { return new Int128(getEnergyCapacity()); }
}
