package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.machine.trait.IMERecipeHandlerTrait;

import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.utils.ISubscription;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.List;

public abstract class NotifiableMERecipeHandlerTrait<T, S> extends MachineTrait implements IMERecipeHandlerTrait<T, S> {

    protected final List<Runnable> listeners = new ReferenceArrayList<>();

    public ISubscription addChangedListener(Runnable listener) {
        this.listeners.add(listener);
        return () -> this.listeners.remove(listener);
    }

    public ISubscription addBufferChangedListener(Runnable mainListener) {
        this.listeners.add(0, mainListener);
        return () -> this.listeners.remove(mainListener);
    }

    public void notifyListeners() {
        markAsChanged();
        // Listeners may unsubscribe or rebind a proxy while handling this notification.
        List.copyOf(listeners).forEach(Runnable::run);
    }

    @Override
    public void onMachineUnload() {
        listeners.clear();
        super.onMachineUnload();
    }
}
