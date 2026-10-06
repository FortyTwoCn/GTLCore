package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.utils.MachineIO;
import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;

import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;

import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.PanelSyncManager;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import static org.gtlcore.gtlcore.utils.MachineIO.inputEU;
import static org.gtlcore.gtlcore.utils.Registries.getItemStack;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class ComputationProviderMachine extends WorkableElectricMultiblockMachine
                                        implements IOpticalComputationProvider, IControllable {

    private static final long INFINITE_PROVIDER_EU_PER_REQUEST = Integer.MAX_VALUE;

    public int allocatedCWUt = 0;
    @SaveField
    public long totalCWU = 0;
    public int maxCWUt = 0;
    @Nullable
    protected TickableSubscription tickSubs;

    boolean canProvideCWUt;
    private final boolean inf;
    private int lastAllocatedCWUt;

    public ComputationProviderMachine(BlockEntityCreationInfo holder, boolean inf) {
        super(holder, new ComputationRecipeLogic());
        this.inf = inf;
    }

    // The native default overloads pre-add this provider. Start with an empty traversal instead,
    // so the collection overload can still reject repeated visits without rejecting direct calls.
    @Override
    public int requestCWUt(int cwut, boolean simulate) {
        return requestCWUt(cwut, simulate, new ArrayList<>());
    }

    @Override
    public int getMaxCWUt() {
        return getMaxCWUt(new ArrayList<>());
    }

    @Override
    public boolean canBridge() {
        return canBridge(new ArrayList<>());
    }

    @Override
    public int requestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        if (!seen.add(this) || cwut <= 0 || !isComputationAvailable()) return 0;
        return allocatedCWUt(cwut, simulate);
    }

    // Retain the original signature: GTL Additions shadows this method to delegate computation requests.
    private int allocatedCWUt(int cwut, boolean simulate) {
        return inf ? requestInfiniteCWUt(cwut, simulate) : allocateFiniteCWUt(cwut, simulate);
    }

    private int requestInfiniteCWUt(int cwut, boolean simulate) {
        if (simulate) {
            return canDrawEnergy(INFINITE_PROVIDER_EU_PER_REQUEST) ? cwut : 0;
        }
        if (!inputEU(this, INFINITE_PROVIDER_EU_PER_REQUEST)) {
            return 0;
        }
        allocatedCWUt = (int) Math.min(Integer.MAX_VALUE, (long) allocatedCWUt + cwut);
        return cwut;
    }

    private int allocateFiniteCWUt(int cwut, boolean simulate) {
        int maximumCWUt = getMaxCWUt();
        int availableCapacity = Math.max(0, maximumCWUt - allocatedCWUt);
        long projectedCWU = totalCWU;
        if (projectedCWU < maximumCWUt && canDrawEnergy(GTValues.VA[getTier()])) {
            long generatedCWU = 1L << getTier();
            if (simulate) {
                projectedCWU = Math.min(maximumCWUt, projectedCWU + generatedCWU);
            } else if (inputEU(this, GTValues.VA[getTier()])) {
                totalCWU = Math.min(maximumCWUt, totalCWU + generatedCWU);
                projectedCWU = totalCWU;
            }
        }
        int toAllocate = Math.min(cwut, (int) Math.min(availableCapacity, Math.max(0, projectedCWU - allocatedCWUt)));
        if (!simulate) allocatedCWUt += toAllocate;
        return toAllocate;
    }

    private boolean canDrawEnergy(long eu) {
        return eu >= 0 && com.gregtechceu.gtceu.api.recipe.RecipeHelper.matchTickRecipe(this,
                GTRecipeTypes.DUMMY_RECIPES.recipeBuilder(GTCEu.id("computation_energy_probe")).EUt(eu).build()).isSuccess();
    }

    private boolean isComputationAvailable() {
        return isFormed() && canProvideCWUt && isWorkingEnabled();
    }

    @Override
    public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        if (!seen.add(this) || !isComputationAvailable()) return 0;
        if (inf) return Integer.MAX_VALUE;
        // Inventory changes now notify recipe traits rather than an onChanged machine callback.
        // Recheck the non-consumed mainframes so removal cannot leave cached capacity behind.
        String mainframe = switch (getTier()) {
            case GTValues.UIV -> "kubejs:optical_mainframe";
            case GTValues.UXV -> "kubejs:exotic_mainframe";
            case GTValues.OpV -> "kubejs:cosmic_mainframe";
            case GTValues.MAX -> "kubejs:supracausal_mainframe";
            default -> null;
        };
        ItemStack stack = mainframe == null ? ItemStack.EMPTY : getItemStack(mainframe, 8);
        maxCWUt = !stack.isEmpty() && MachineIO.notConsumableItem(this, stack) ? switch (getTier()) {
            case GTValues.UIV -> 1024;
            case GTValues.UXV -> 2048;
            case GTValues.OpV -> 4096;
            case GTValues.MAX -> 8192;
            default -> 0;
        } : 0;
        return maxCWUt;
    }

    @Override
    public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
        return seen.add(this) && isComputationAvailable();
    }

    public void tick() {
        if (!isFormed()) {
            allocatedCWUt = 0;
            canProvideCWUt = false;
            updateTickSubscription();
            return;
        }
        if (!inf) {
            totalCWU = Math.max(0, totalCWU - allocatedCWUt);
            markAsChanged();
        }
        lastAllocatedCWUt = allocatedCWUt;
        if (getRecipeLogic().isSuspend()) {
            allocatedCWUt = 0;
            canProvideCWUt = false;
            return;
        }
        canProvideCWUt = true;
        if (allocatedCWUt != 0) {
            getRecipeLogic().setStatus(RecipeLogic.Status.WORKING);
            allocatedCWUt = 0;
        } else {
            getRecipeLogic().setStatus(RecipeLogic.Status.IDLE);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        scheduleForNextServerTick(this::updateTickSubscription);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
    }

    protected void updateTickSubscription() {
        if (isFormed) {
            tickSubs = subscribeServerTick(tickSubs, this::tick);
        } else if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
    }

    @Override
    public void formStructure(@NotNull String name) {
        super.formStructure(name);
        canProvideCWUt = true;
        scheduleForNextServerTick(this::updateTickSubscription);
    }

    @Override
    public void invalidateStructure(String name) {
        super.invalidateStructure(name);
        allocatedCWUt = 0;
        canProvideCWUt = false;
        updateTickSubscription();
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager sync) {
        var widgets = new ArrayList<>(super.getWidgetsForDisplay(sync));
        widgets.addAll(GTLMultiblockDisplays.syncedLines("computationProvider", (controller, lines) -> {
            if (!isFormed()) return;
            Component cwut = Component.literal(lastAllocatedCWUt + " / " + (inf ? "∞" : getMaxCWUt()) + " CWU/t")
                    .withStyle(ChatFormatting.AQUA);
            lines.add(Component.translatable("gtceu.multiblock.hpca.computation", cwut).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(lastAllocatedCWUt > 0 ?
                    "gtceu.multiblock.data_bank.providing" : "gtceu.multiblock.idling"));
        }).apply(this, sync));
        return widgets;
    }

    /** Computation drives this trait's status; there is no ordinary recipe to search or consume. */
    private static final class ComputationRecipeLogic extends RecipeLogic {
        @Override public void serverTick() {}
    }
}
