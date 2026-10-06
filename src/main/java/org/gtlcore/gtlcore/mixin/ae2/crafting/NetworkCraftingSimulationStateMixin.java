package org.gtlcore.gtlcore.mixin.ae2.crafting;

import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.ae2.crafting.IMaxFastNetworkInventoryFingerprint;
import org.gtlcore.gtlcore.integration.ae2.crafting.ManualCraftingInventoryLock;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastFingerprint;

import net.minecraft.core.HolderLookup;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkCraftingSimulationState.class)
public abstract class NetworkCraftingSimulationStateMixin implements IMaxFastNetworkInventoryFingerprint {

    @Shadow(remap = false)
    @Final
    private KeyCounter list;

    @Override
    public MaxFastFingerprint.Result gtlcore$getMaxFastInventoryFingerprint(HolderLookup.Provider registries) {
        return MaxFastFingerprint.ofKeyCounter(list, registries);
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$captureExtractableInventory(IStorageService storage, IActionSource source, CallbackInfo ci) {
        if (source == null) return;
        var inventory = storage.getInventory();
        if (!(ConfigHolder.INSTANCE != null && ConfigHolder.INSTANCE.ae2CraftingSimulatedExtraction) &&
                !ManualCraftingInventoryLock.hasReservations(inventory)) return;
        // AE2 constructs this snapshot before scheduling the calculation worker.
        // Its detached counter is the only inventory consulted by that worker.
        var amounts = new KeyCounter();
        for (var entry : list) {
            long offered = Math.max(0, entry.getLongValue());
            long extractable = inventory.extract(entry.getKey(), offered, Actionable.SIMULATE, source);
            amounts.set(entry.getKey(), Math.max(0, Math.min(offered, extractable)));
        }
        list.reset();
        list.addAll(amounts);
    }
}
