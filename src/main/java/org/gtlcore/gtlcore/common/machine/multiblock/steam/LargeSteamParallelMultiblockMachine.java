package org.gtlcore.gtlcore.common.machine.multiblock.steam;

import org.gtlcore.gtlcore.api.machine.ISteamMachine;
import org.gtlcore.gtlcore.api.machine.trait.*;
import org.gtlcore.gtlcore.api.recipe.CoreRecipeModifiers;
import org.gtlcore.gtlcore.common.machine.multiblock.part.LargeSteamHatchPartMachine;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.sync_system.annotations.*;
import com.gregtechceu.gtceu.common.machine.multiblock.part.SteamHatchPartMachine;
import com.gregtechceu.gtceu.common.mui.GTMultiblockTextUtil;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.layout.Flow;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import java.util.*;

/** Native steam controller, steam energy conversion, persisted OC controls and ModularUI. */
public class LargeSteamParallelMultiblockMachine extends WorkableMultiblockMachine implements ISteamMachine, IMuiMachine, IRecipeInputHandlerPolicy {
    private final int max_parallels;
    @SyncToClient private boolean isOC;
    @SaveField @SyncToClient private int amountOC;
    private ExactSteamEnergyRecipeHandler steamEnergy;
    public LargeSteamParallelMultiblockMachine(BlockEntityCreationInfo info, int maximum, Object... args) {
        super(info);
        if (maximum <= 0) throw new IllegalArgumentException("Steam parallel limit must be positive");
        max_parallels = maximum;
    }
    public boolean isOC() { return isOC; }
    public int getAmountOC() { return amountOC; }
    public int getMaxParallels() { return max_parallels; }
    public void setAmountOC(int amount) {
        int clamped = Mth.clamp(amount, 0, 4);
        if (clamped == amountOC || getLevel() != null && getLevel().isClientSide) return;
        amountOC = clamped;
        if (steamEnergy != null) steamEnergy.setOverclocks(isOC ? amountOC : 0);
        recipeLogic.markLastRecipeDirty(); recipeLogic.updateTickSubscription(); setChanged();
        getSyncDataHolder().markClientSyncFieldDirty("amountOC");
    }
    protected boolean isSteamInputPart(MetaMachine part) { return part instanceof SteamHatchPartMachine || part instanceof LargeSteamHatchPartMachine; }
    protected ExactSteamEnergyRecipeHandler createSteamEnergyHandler(com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank tank) {
        return new ExactSteamEnergyRecipeHandler(tank, () -> isOC ? amountOC : 0);
    }
    @Override public void formStructure(String name) {
        super.formStructure(name); steamEnergy = null; isOC = false;
        for (var part : getParts()) if (isSteamInputPart(part)) {
            var tank = part.getTraits(com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank.class).getFirst();
            isOC = part instanceof LargeSteamHatchPartMachine;
            steamEnergy = createSteamEnergyHandler(tank);
            RecipeInputExclusion.exclude(this, tank);
            addHandlerList(RecipeHandlerList.of(IO.IN, steamEnergy));
            getSyncDataHolder().markClientSyncFieldDirty("isOC"); ((IRecipeCapabilityMachine) (Object) this).upDate();
            return;
        }
    }
    @Override public boolean isRecipeInputHandlerExcluded(IRecipeHandler<?> handler) { return steamEnergy != null && handler == steamEnergy.getSteamTank(); }
    @Override public void invalidateStructure(String name) { super.invalidateStructure(name); steamEnergy = null; isOC = false; }
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe, double reductionDuration) {
        if (!(machine instanceof LargeSteamParallelMultiblockMachine steam)) return ModifierFunction.NULL;
        if (recipe.getInputEUt().getTotalEU() > (steam.isOC ? 128 : 32)) return ModifierFunction.NULL;
        int parallel = CoreRecipeModifiers.parallelAmount(machine, recipe, steam.max_parallels);
        if (parallel == 0) return ModifierFunction.NULL;
        var nativeModifier = ModifierFunction.builder().modifyAllContents(org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier.preciseMultiplier(parallel))
                .eutMultiplier(parallel).parallels(parallel)
                .durationMultiplier(reductionDuration / (steam.isOC ? Math.pow(2, steam.amountOC) : 1)).build();
        int oc = steam.isOC ? steam.amountOC : 0;
        return input -> {
            var result = nativeModifier.apply(input);
            if (result != null) { result.data = result.data.copy(); result.data.putInt(ExactSteamEnergyRecipeHandler.RECIPE_OC, oc); }
            return result;
        };
    }
    @Override public void buildMainUI(ParentWidget<?> main, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        main.size(170, 100);
        var list = new ListWidget<>().width(164).height(96).padding(0).left(3).top(2)
                .child(GTMultiblockTextUtil.addUnformedWarning(this, sync))
                .child(GTMultiblockTextUtil.addSteamUsageLine(steamEnergy, sync))
                .child(GTMultiblockTextUtil.addWorkingStatusLine(this, sync))
                .child(GTMultiblockTextUtil.addProgressLine(this, sync))
                .child(GTMultiblockTextUtil.addParallelLine(this, sync))
                .child(GTMultiblockTextUtil.addOutputLines(this, sync));
        var oc = new IntSyncValue(this::getAmountOC, this::setAmountOC).allowC2S(); sync.syncValue("steamOverclocks", oc);
        var enabled = new BooleanSyncValue(this::isOC); sync.syncValue("hasSteamOverclocks", enabled);
        // The native scroll area reserves scrollbar width dynamically, including while this row is disabled.
        list.child(Flow.row().coverChildrenHeight().widthRel(1).setEnabledIf(widget -> enabled.getBoolValue())
                .child(Text.dynamic(() -> Component.translatable("gtceu.multiblock.oc_amount", oc.getIntValue())).asWidget().widthRelOffset(1, -40))
                .child(new ButtonWidget<>().size(20, 16).overlay(Text.str("−")).onMousePressed((context, button) -> { oc.setIntValue(oc.getIntValue() - 1); return true; }))
                .child(new ButtonWidget<>().size(20, 16).overlay(Text.str("+")).onMousePressed((context, button) -> { oc.setIntValue(oc.getIntValue() + 1); return true; })));
        main.child(list);
    }
    /** Core's public conversion rate is EU per millibucket. */
    public double getConversionRate() { return .5 * (isOC ? Math.pow(3, amountOC) : 1); }
    @Override public double getSteamPerEU() { return 1 / getConversionRate(); }
}
