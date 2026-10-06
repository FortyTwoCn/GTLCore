package org.gtlcore.gtlcore.mixin.gtm.machine;

import org.gtlcore.gtlcore.common.machine.trait.QuantumChestLongStorage;
import com.gregtechceu.gtceu.common.machine.storage.QuantumChestMachine;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Exposes GT 8's native long storage without keeping a second counter or item template. */
@Mixin(value = QuantumChestMachine.class, remap = false)
public abstract class QuantumChestMachineMixin implements QuantumChestLongStorage {
    @Shadow @Final private long maxAmount;
    @Shadow protected long storedAmount;
    @Shadow protected ItemStack stored;
    @Shadow protected abstract void onItemChanged();

    public ItemStack gtlcore$getStoredStack() {
        return storedAmount > 0 ? stored.copyWithCount(1) : ItemStack.EMPTY;
    }
    public long gtlcore$getStoredAmount() { return storedAmount; }
    public long gtlcore$getStorageCapacity() { return maxAmount; }
    public void gtlcore$setStoredAmount(long amount) {
        storedAmount = Math.clamp(amount, 0, maxAmount);
        if (storedAmount == 0) stored = ItemStack.EMPTY;
        onItemChanged();
    }
    public void gtlcore$changeStoredAmount(long amount) {
        if (amount > 0 && amount > maxAmount - storedAmount) gtlcore$setStoredAmount(maxAmount);
        else if (amount < 0 && amount < -storedAmount) gtlcore$setStoredAmount(0);
        else gtlcore$setStoredAmount(storedAmount + amount);
    }
    public void gtlcore$markStorageChanged() {
        onItemChanged(); ((QuantumChestMachine) (Object) this).setChanged();
    }
}
