package org.gtlcore.gtlcore.common.machine.multiblock.generator;

import org.gtlcore.gtlcore.api.recipe.CoreRecipeModifiers;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.common.machine.multiblock.part.RotorHatchPartMachine;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.LongSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import java.math.BigInteger;
import java.util.*;

/** Multi-rotor turbine on native parts, energy handlers and saved recipe state. */
public class MegaTurbineMachine extends WorkableElectricMultiblockMachine {
    public static final int MIN_DURABILITY_TO_WARN = 10;
    private static final String NEXT_EXCESS = "gtlcore:turbine_excess_voltage";
    private final long baseEuOutput;
    private final int turbineTier;
    @SaveField private long excessVoltage;
    private final Set<RotorHolderPartMachine> rotorHolderMachines = new LinkedHashSet<>();
    private RotorHatchPartMachine rotorHatchPartMachine;
    protected final ConditionalSubscriptionHandler rotorSubs;

    public MegaTurbineMachine(BlockEntityCreationInfo info, int tier, int amperage) {
        super(info);
        turbineTier = tier; baseEuOutput = Math.multiplyExact(GTValues.V[tier], amperage);
        getRecipeLogic().setRegressWhenWaiting(false);
        rotorSubs = new ConditionalSubscriptionHandler(this, () -> { if (getOffsetTimer() % 20 == 0) refillRotors(); }, this::isFormed);
    }
    @Override public int getTier() { return turbineTier; }
    @Override public void formStructure(String name) {
        super.formStructure(name); rotorHolderMachines.clear(); rotorHatchPartMachine = null;
        for (var part : getParts()) {
            if (part instanceof RotorHolderPartMachine rotor) rotorHolderMachines.add(rotor);
            if (part instanceof RotorHatchPartMachine hatch) rotorHatchPartMachine = hatch;
        }
        rotorSubs.initialize(getLevel());
    }
    private void clearRotors() { rotorSubs.unsubscribe(); rotorHolderMachines.clear(); rotorHatchPartMachine = null; }
    @Override public void invalidateStructure(String name) { clearRotors(); super.invalidateStructure(name); }
    @Override public void onPartUnload() { clearRotors(); super.onPartUnload(); }
    @Override public void onUnload() { clearRotors(); super.onUnload(); }
    public void refillRotors() {
        if (!isFormed() || rotorHatchPartMachine == null) return;
        var inventory = rotorHatchPartMachine.getInventory();
        for (var holder : rotorHolderMachines) if (!holder.hasRotor()) {
            var rotor = inventory.extractItem(0, 1, false);
            if (rotor.isEmpty()) break;
            holder.setRotorStack(rotor);
        }
    }
    private RotorHolderPartMachine getRotorHolder() {
        return rotorHolderMachines.stream().min(Comparator.comparingInt(RotorHolderPartMachine::getTier)).orElse(null);
    }
    @Override public long getOverclockVoltage() {
        var rotor = getRotorHolder();
        if (rotor == null || !rotor.hasRotor()) return 0;
        int power = rotor.getTotalPower();
        if (power <= 0) {
            RecipeResult.of(this, RecipeResult.fail(Component.translatable("gtceu.recipe.fail.rotor_holder.tier"))); return 0;
        }
        return BigInteger.valueOf(baseEuOutput).multiply(BigInteger.valueOf(power)).divide(BigInteger.valueOf(100)).longValueExact();
    }
    protected long boostProduction(long production) {
        var rotor = getRotorHolder(); if (rotor == null || !rotor.hasRotor()) return 0;
        int maximum = rotor.getMaxRotorHolderSpeed(), current = rotor.getRotorSpeed();
        if (maximum <= 0) return 0;
        return current >= maximum ? production : (long) (production * Math.pow((double) current / maximum, 2));
    }
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof MegaTurbineMachine turbine) || turbine.rotorHolderMachines.isEmpty()) return ModifierFunction.NULL;
        var first = turbine.getRotorHolder();
        if (first == null || !first.hasRotor()) return ModifierFunction.cancel(Component.translatable("gtceu.recipe.fail.rotor.isEmpty"));
        var material = first.getRotorMaterial();
        for (var holder : turbine.rotorHolderMachines) {
            if (!holder.hasRotor()) return ModifierFunction.cancel(Component.translatable("gtceu.recipe.fail.rotor.isEmpty"));
            if (material == null || material != holder.getRotorMaterial()) return ModifierFunction.cancel(Component.translatable("gtceu.recipe.fail.rotor.different"));
        }
        long energy = recipe.getOutputEUt().getTotalEU(), target = turbine.getOverclockVoltage();
        int efficiency = first.getTotalEfficiency();
        if (energy <= 0 || target <= 0 || efficiency <= 0) return ModifierFunction.NULL;
        BigInteger perFuel = BigInteger.valueOf(energy).multiply(BigInteger.valueOf(efficiency));
        int limit = BigInteger.valueOf(target).subtract(BigInteger.valueOf(turbine.excessVoltage)).multiply(BigInteger.valueOf(100))
                .divide(perFuel).max(BigInteger.ONE).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
        return source -> {
            var parallel = CoreRecipeModifiers.fastParallel(turbine, source, limit, false);
            var result = parallel.getFirst(); if (result == null) return null;
            long raw = perFuel.multiply(BigInteger.valueOf(parallel.getSecond())).divide(BigInteger.valueOf(100)).longValueExact();
            EURecipeCapability.putEUContent(result.tickOutputs, new EnergyStack(turbine.boostProduction(raw)));
            // Commit fuel-rounding carry only after native setup accepts the recipe. Read-only searches are pure.
            if (parallel.getSecond() == limit) result.data.putLong(NEXT_EXCESS, Math.addExact(turbine.excessVoltage, raw - target));
            else result.data.remove(NEXT_EXCESS);
            // Native EU getters are cached. Rebuild after changing the tick map to keep matching and GUI consistent.
            return result.copy();
        };
    }
    @Override public boolean beforeWorking(GTRecipe recipe) {
        if (!super.beforeWorking(recipe)) return false;
        if (recipe != null && recipe.data.contains(NEXT_EXCESS)) { excessVoltage = recipe.data.getLong(NEXT_EXCESS); setChanged(); }
        return true;
    }
    @Override public boolean canVoidRecipeOutputs(RecipeCapability<?> capability) { return capability != EURecipeCapability.CAP; }
    @Override public List<IWidget> getWidgetsForDisplay(PanelSyncManager sync) {
        var widgets = super.getWidgetsForDisplay(sync);
        var speed = new IntSyncValue(() -> { var rotor = getRotorHolder(); return rotor == null ? 0 : rotor.getRotorSpeed(); }); sync.syncValue("megaRotorSpeed", speed);
        var maximum = new IntSyncValue(() -> { var rotor = getRotorHolder(); return rotor == null ? 0 : rotor.getMaxRotorHolderSpeed(); }); sync.syncValue("megaRotorMaxSpeed", maximum);
        var efficiency = new IntSyncValue(() -> { var rotor = getRotorHolder(); return rotor == null ? 0 : rotor.getTotalEfficiency(); }); sync.syncValue("megaRotorEfficiency", efficiency);
        var durability = new IntSyncValue(() -> { var rotor = getRotorHolder(); return rotor == null ? 0 : rotor.getRotorDurabilityPercent(); }); sync.syncValue("megaRotorDurability", durability);
        var production = new LongSyncValue(() -> getRecipeLogic().isActive() && getRecipeLogic().getLastUnrolledRecipe() != null ? getRecipeLogic().getLastUnrolledRecipe().getOutputEUt().getTotalEU() : 0);
        sync.syncValue("megaRotorProduction", production);
        widgets.add(Text.dynamic(() -> Component.translatable("gtceu.multiblock.turbine.rotor_speed", FormattingUtil.formatNumbers(speed.getIntValue()), FormattingUtil.formatNumbers(maximum.getIntValue())))
                .asWidget().setEnabledIf(w -> isFormed() && maximum.getIntValue() > 0));
        widgets.add(Text.dynamic(() -> Component.translatable("gtceu.multiblock.turbine.efficiency", efficiency.getIntValue()))
                .asWidget().setEnabledIf(w -> isFormed() && efficiency.getIntValue() > 0));
        widgets.add(Text.dynamic(() -> Component.translatable("gtceu.multiblock.turbine.energy_per_tick", FormattingUtil.formatNumbers(production.getLongValue()), GTValues.VNF[GTUtil.getTierByVoltage(production.getLongValue())]))
                .asWidget().setEnabledIf(w -> isFormed() && production.getLongValue() > 0));
        widgets.add(Text.dynamic(() -> Component.translatable("gtceu.multiblock.turbine.rotor_durability", durability.getIntValue())
                        .withStyle(durability.getIntValue() <= MIN_DURABILITY_TO_WARN ? ChatFormatting.RED : ChatFormatting.GRAY))
                .asWidget().setEnabledIf(w -> isFormed() && maximum.getIntValue() > 0));
        return widgets;
    }
}
