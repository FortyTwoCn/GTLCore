package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.machine.trait.AECraft.IMolecularAssemblerHandlerTrait;

import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.utils.ISubscription;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.List;

public abstract class NotifiableMAHandlerTrait extends MachineTrait implements IMolecularAssemblerHandlerTrait {

    protected final List<Runnable> listeners = new ReferenceArrayList<>();

    @Override
    public ISubscription addChangedListener(Runnable listener) {
        this.listeners.add(listener);
        return () -> this.listeners.remove(listener);
    }

    public void notifyListeners() {
        markAsChanged();
        List.copyOf(listeners).forEach(Runnable::run);
    }

    @Override
    public void onMachineUnload() {
        listeners.clear();
        super.onMachineUnload();
    }
}
