package org.gtlcore.gtlcore.common.machine.multiblock.noenergy;

import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import org.gtlcore.gtlcore.api.pattern.PatternMatchData;
import org.gtlcore.gtlcore.api.recipe.CoreRecipeModifiers;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.common.data.GTLRecipeModifiers;
import org.gtlcore.gtlcore.common.machine.multiblock.part.NeutronAcceleratorPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.NeutronSensorPartMachine;
import org.gtlcore.gtlcore.common.machine.trait.NeutronActivatorRecipeLogic;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class NeutronActivatorMachine extends NoEnergyMultiblockMachine {

    private int height = 0;

    @Getter
    @SaveField
    @SyncToClient
    private int eV;

    private static final Item dustBeryllium = ChemicalHelper.get(TagPrefix.dust, GTMaterials.Beryllium).getItem();
    private static final Item dustGraphite = ChemicalHelper.get(TagPrefix.dust, GTMaterials.Graphite).getItem();

    protected ConditionalSubscriptionHandler neutronEnergySubs;

    private Set<NeutronSensorPartMachine> sensorMachines;
    private Set<ItemBusPartMachine> busMachines;
    private Set<NeutronAcceleratorPartMachine> acceleratorMachines;
    private boolean workingTickPrepared;

    public NeutronActivatorMachine(BlockEntityCreationInfo info) {
        super(info, new NeutronActivatorRecipeLogic());
        this.neutronEnergySubs = new ConditionalSubscriptionHandler(this, this::neutronEnergyUpdate, this::isFormed);
    }

    @Override
    public void formStructure(String name) {
        super.formStructure(name);
        if (!getPatternState(name).isFormed()) return;
        height = PatternMatchData.of(getPatternState(name).getContext()).blockCount("SpeedPipe");
        sensorMachines = new HashSet<>();
        busMachines = new HashSet<>();
        acceleratorMachines = new HashSet<>();
        for (MultiblockPartMachine part : getParts()) {
            if (part instanceof ItemBusPartMachine itemBusPartMachine) {
                busMachines = Objects.requireNonNullElseGet(busMachines, HashSet::new);
                busMachines.add(itemBusPartMachine);
            }
            if (part instanceof NeutronSensorPartMachine neutronSensorMachine) {
                sensorMachines = Objects.requireNonNullElseGet(sensorMachines, HashSet::new);
                sensorMachines.add(neutronSensorMachine);
            }
            if (part instanceof NeutronAcceleratorPartMachine neutronAccelerator) {
                acceleratorMachines = Objects.requireNonNullElseGet(acceleratorMachines, HashSet::new);
                acceleratorMachines.add(neutronAccelerator);
            }
        }
        neutronEnergySubs.initialize(getLevel());
    }

    @Override
    public void invalidateStructure(String name) {
        super.invalidateStructure(name);
        height = 0;
        sensorMachines = null;
        busMachines = null;
        acceleratorMachines = null;
        neutronEnergySubs.unsubscribe();
    }

    public double getEfficiencyFactor() {
        return Math.pow(0.95, Math.max(height - 4, 0));
    }

    public static ModifierFunction recipeModifier(MetaMachine machine, @NotNull GTRecipe recipe) {
        if (machine instanceof NeutronActivatorMachine nMachine) {
            long minimum = recipe.data.getInt("ev_min") * 1_000_000L;
            long maximum = recipe.data.getInt("ev_max") * 1_000_000L;
            if (nMachine.eV <= minimum) {
                return ModifierFunction.cancel(Component.translatable("gtceu.recipe.fail.no.enough.neutron"));
            } else if (nMachine.eV >= maximum) {
                return ModifierFunction.cancel(Component.translatable("gtceu.recipe.fail.too.much.neutron"));
            }
            int limit = GTLRecipeModifiers.getHatchParallel(nMachine);
            double efficiency = nMachine.getEfficiencyFactor();
            return input -> {
                var parallel = CoreRecipeModifiers.accurateParallel(machine, input, limit, false).getFirst();
                if (parallel == null) return null;
                parallel.duration = (int) Math.round(Math.max(parallel.duration * efficiency, 1));
                return parallel;
            };
        }
        return ModifierFunction.NULL;
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        if (recipe != null) {
            if (eV >= requiredEV(recipe)) return super.beforeWorking(recipe);
            else {
                RecipeResult.of(this, RecipeResult.fail(Component.translatable("gtceu.recipe.fail.no.enough.eV.consumed")));
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean onWorking() {
        if (!workingTickPrepared) return false;
        workingTickPrepared = false;
        var lastRecipe = getRecipeLogic().getLastRecipe();
        if (lastRecipe == null || eV < requiredEV(lastRecipe)) return false;
        eV -= (int) requiredEV(lastRecipe);
        return true;
    }

    /** Execute native part/definition checks once, before the recipe loop commits its working tick. */
    public boolean prepareWorkingTick() {
        workingTickPrepared = super.onWorking();
        return workingTickPrepared;
    }

    public long requiredEV(GTRecipe recipe) {
        double required = recipe.data.getInt("evt") * 1000.0 * getEVtMultiplier(recipe);
        return required >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0, (long) Math.ceil(required));
    }

    private double getEVtMultiplier(GTRecipe recipe) {
        return Math.max(1, Math.pow(IGTRecipe.of(recipe).getRealParallels(), 1.5) * getEfficiencyFactor());
    }

    protected void neutronEnergyUpdate() {
        if (acceleratorMachines == null) return;
        long charge = eV;
        for (var accelerator : acceleratorMachines) {
            long increase = accelerator.consumeEnergy();
            if (increase > 0) {
                charge += Math.round(Math.max(increase * getEfficiencyFactor(), 1));
            }
        }
        this.eV = charge > 1_200_000_000L ? 0 : (int) charge;
        moderateUpdate();
        absorptionUpdate();
    }

    protected void moderateUpdate() {
        boolean anyAcceleratorProducing = acceleratorMachines != null &&
                acceleratorMachines.stream().anyMatch(NeutronAcceleratorPartMachine::isProducing);
        if (!anyAcceleratorProducing) {
            if (getOffsetTimer() % 20 == 0) {
                this.eV = Math.max(eV - 72 * 1000, 0);
            }
            if (this.eV < 0) this.eV = 0;
        }
        if (!isFormed() || sensorMachines == null) return;
        sensorMachines.forEach(sensor -> sensor.update(eV));
    }

    protected void absorptionUpdate() {
        if (busMachines == null || eV <= 0 && getOffsetTimer() % 10 != 0) return;
        for (var bus : busMachines) {
            var inv = bus.getInventory();
            var io = inv.getHandlerIO();
            if (io == IO.IN || io == IO.BOTH) {
                for (int i = 0; i < inv.getSlots(); i++) {
                    var stack = inv.getStackInSlot(i);
                    if (stack.is(dustBeryllium) || stack.is(dustGraphite)) {
                        int consume = Math.min(Math.max(eV / (10 * 1000000), 1), stack.getCount());
                        int extracted = inv.storage.extractItem(i, consume, false).getCount();
                        this.eV = Math.max(0, this.eV - 10 * 1000000 * extracted);
                    }
                }
            }
        }
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        var widgets = super.getWidgetsForDisplay(syncManager);
        var formed = syncManager.getOrCreateSyncHandler("isFormed", BooleanSyncValue.class,
                () -> new BooleanSyncValue(this::isFormed));
        var charge = syncManager.getOrCreateSyncHandler("neutronEV", IntSyncValue.class, () -> new IntSyncValue(this::getEV));
        var pipes = syncManager.getOrCreateSyncHandler("neutronHeight", IntSyncValue.class, () -> new IntSyncValue(() -> height));
        var parallels = syncManager.getOrCreateSyncHandler("neutronParallel", IntSyncValue.class,
                () -> new IntSyncValue(() -> GTLRecipeModifiers.getHatchParallel(this)));
        widgets.add(Text.dynamic(() -> Component.translatable("gtceu.multiblock.parallel",
                Component.literal(FormattingUtil.formatNumbers(parallels.getIntValue())).withStyle(ChatFormatting.DARK_PURPLE))
                .withStyle(ChatFormatting.GRAY)).asWidget().setEnabledIf(widget -> formed.getBoolValue()));
        widgets.add(Text.dynamic(() -> Component.translatable("gtceu.machine.neutron_activator.ev", processNumber(charge.getIntValue())))
                .asWidget().setEnabledIf(widget -> formed.getBoolValue()));
        widgets.add(Text.dynamic(() -> Component.translatable("gtceu.machine.neutron_activator.efficiency",
                FormattingUtil.formatNumbers(Math.max(1, Math.pow(parallels.getIntValue(), 1.5) * Math.pow(.95, Math.max(pipes.getIntValue() - 4, 0))))))
                .asWidget().setEnabledIf(widget -> formed.getBoolValue()));
        widgets.add(Text.dynamic(() -> Component.translatable("gtceu.machine.neutron_activator.height", pipes.getIntValue()))
                .asWidget().setEnabledIf(widget -> formed.getBoolValue()));
        widgets.add(Text.dynamic(() -> Component.translatable("gtceu.machine.neutron_activator.time",
                FormattingUtil.formatNumbers(Math.pow(.95, Math.max(pipes.getIntValue() - 4, 0)) * 100)).append("%"))
                .asWidget().setEnabledIf(widget -> formed.getBoolValue()));
        return widgets;
    }

    private String processNumber(int num) {
        float num2;
        num2 = ((float) num) / 1000F;
        if (num2 <= 0) {
            return String.format("%d", num);
        }
        if (num2 < 1000.0) {
            return String.format("%.1fK", num2);
        }
        num2 /= 1000F;
        return String.format("%.1fM", num2);
    }
}
