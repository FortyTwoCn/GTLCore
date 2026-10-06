package org.gtlcore.gtlcore.common.machine.multiblock.generator;

import org.gtlcore.gtlcore.utils.Registries;
import org.gtlcore.gtlcore.utils.NumberUtils;
import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.PanelSyncManager;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
public class DysonSphereMachine extends WorkableElectricMultiblockMachine {

    @SaveField
    private int DysonSphereData;
    @SaveField
    private int DysonSpheredamageData;
    @SaveField
    private boolean upgradeTriggeredThisRecipe = false;
    @SaveField
    private boolean damageTriggeredThisRecipe = false;

    protected ConditionalSubscriptionHandler nightSubs;

    @Nullable
    private List<@NotNull BlockPos> cachedCheckPositions;

    public DysonSphereMachine(BlockEntityCreationInfo holder) {
        super(holder);
        this.nightSubs = new ConditionalSubscriptionHandler(this, this::nightUpdate, () -> isFormed() && DysonSphereData > 0 && !isRemoved());
    }

    @Override
    public void onLoad() {
        super.onLoad(); nightSubs.initialize(getLevel());
    }

    @Override
    public void onUnload() {
        nightSubs.unsubscribe(); cachedCheckPositions = null; super.onUnload();
    }

    public int getDysonSphereData() { return DysonSphereData; }
    public int getDysonSpheredamageData() { return DysonSpheredamageData; }

    protected void nightUpdate() {
        if (isFormed() && DysonSphereData > 0 && getOffsetTimer() % 10 == 0 && getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.setDayTime(18000L);
        }
    }

    @Override
    public long getMaxVoltage() {
        long totalPower = 0;
        // 获取所有输出仓（动力仓）
        List<IEnergyContainer> outputContainers = getOutputContainers();
        for (IEnergyContainer container : outputContainers) {
            totalPower = NumberUtils.saturatedAdd(totalPower,
                    NumberUtils.saturatedMultiply(container.getOutputVoltage(), container.getOutputAmperage()));
        }
        if (totalPower <= 0) {
            // 没有输出仓时，回退到父类逻辑（避免 NPE）
            return super.getMaxVoltage();
        }
        int equivalentTier = getEquivalentTier(totalPower);
        return GTValues.V[equivalentTier];
    }

    private int getEquivalentTier(long totalPower) {
        int maxTier = GTValues.V.length - 1;
        for (int tier = maxTier; tier >= 0; tier--) {
            if (GTValues.V[tier] <= totalPower) {
                return tier;
            }
        }
        return 0;
    }

    private List<IEnergyContainer> getOutputContainers() {
        List<IEnergyContainer> outputContainers = new ArrayList<>();
        var capabilities = getCapabilitiesFlat(IO.OUT, EURecipeCapability.CAP);
        if (!capabilities.isEmpty()) {
            for (IRecipeHandler<?> handler : capabilities) {
                if (handler instanceof IEnergyContainer container) {
                    outputContainers.add(container);
                }
            }
        }
        return outputContainers;
    }

    @Override
    public void formStructure(String name) {
        super.formStructure(name);
        if (!getPatternState(name).isFormed()) return;
        nightSubs.initialize(getLevel());
        cachedCheckPositions = findCheckPositions();
    }

    @Override
    public void invalidateStructure(String name) {
        boolean wasFormed = isFormed();
        super.invalidateStructure(name);
        cachedCheckPositions = null;
        if (nightSubs != null) nightSubs.unsubscribe();
        if (wasFormed && this.DysonSphereData > 0) { DysonSphereData--; markAsChanged(); }
    }

    @Nullable
    protected List<@NotNull BlockPos> findCheckPositions() {
        Level level = getLevel();
        if (level == null) return null;

        BlockPos pos = getBlockPos();
        BlockPos[] coordinates = new BlockPos[] {
                pos.offset(4, 14, 0),
                pos.offset(-4, 14, 0),
                pos.offset(0, 14, 4),
                pos.offset(0, 14, -4)
        };

        for (BlockPos blockPos : coordinates) {
            if (!level.hasChunkAt(blockPos)) continue;
            if (Objects.equals(Registries.getBlockId(level.getBlockState(blockPos).getBlock()), "kubejs:dyson_receiver_casing")) {
                var checkPositions = new ObjectArrayList<BlockPos>(169);
                for (int i = -6; i < 7; i++) {
                    for (int j = -6; j < 7; j++) {
                        if (i != 0 || j != 0) {
                            checkPositions.add(blockPos.offset(i, 1, j));
                        }
                    }
                }
                return List.copyOf(checkPositions);
            }
        }
        return null;
    }

    @Override
    public boolean onWorking() {
        boolean value = super.onWorking();
        if (!value) return false;
        GTRecipe currentRecipe = getRecipeLogic().getLastRecipe();
        if (currentRecipe == null) return value;

        int progress = getRecipeLogic().getProgress();
        int duration = getRecipeLogic().getDuration();
        if (duration <= 0) return value;

        if (!upgradeTriggeredThisRecipe && progress + 1 >= duration && isLaunch(currentRecipe)) {
            upgradeTriggeredThisRecipe = true;
            if (getDysonSpheredamageData() > 60) {
                this.DysonSpheredamageData = 0;
            } else if (getDysonSphereData() < 10000) {
                this.DysonSphereData++;
            }
            nightSubs.updateSubscription(); markAsChanged();
        }

        int targetProgress = (int) (duration * 0.95);
        if (!damageTriggeredThisRecipe && progress >= targetProgress && getDysonSphereData() > 0 && !isLaunch(currentRecipe)) {
            damageTriggeredThisRecipe = true;
            if (Math.random() < 0.01 * (1 + (double) getDysonSphereData() / 256)) {
                if (getDysonSpheredamageData() > 99) {
                    this.DysonSphereData--;
                    this.DysonSpheredamageData = 0;
                    nightSubs.updateSubscription();
                } else {
                    this.DysonSpheredamageData++;
                }
            }
            markAsChanged();
        }

        return value;
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        if (recipe == null || !super.beforeWorking(recipe)) return false;
        Level level = getLevel();
        if (cachedCheckPositions == null || level == null) return false;
        for (BlockPos checkPos : cachedCheckPositions) {
            if (!level.hasChunkAt(checkPos) || !level.canSeeSky(checkPos)) return false;
        }

        boolean allowed = isLaunch(recipe) ? getDysonSphereData() <= 10000 : getDysonSphereData() > 0;
        if (allowed) { upgradeTriggeredThisRecipe = false; damageTriggeredThisRecipe = false; markAsChanged(); }
        return allowed;
    }

    private boolean isLaunch(GTRecipe recipe) {
        return recipe.getOutputEUt().getTotalEU() < GTValues.V[GTValues.MAX];
    }

    private double getEfficiency() {
        return (double) (50 - Math.max(0, getDysonSpheredamageData() - 60)) / 50;
    }

    @Override
    public int getOutputSignal(@Nullable Direction side) {
        return (int) ((1 - getEfficiency()) * 15);
    }

    @Override
    public long getOverclockVoltage() {
        return (long) (2L * GTValues.V[GTValues.MAX] * getDysonSphereData() * getEfficiency());
    }

    public static ModifierFunction recipeModifier(MetaMachine machine, @NotNull GTRecipe recipe) {
        if (machine instanceof DysonSphereMachine engineMachine) {
            if (!engineMachine.isLaunch(recipe)) {
                return input -> {
                    GTRecipe recipe1 = input.copy();
                    recipe1.tickOutputs.put(EURecipeCapability.CAP, List.of(new Content(
                            new EnergyStack(engineMachine.getOverclockVoltage()),
                            ChanceLogic.getMaxChancedValue(), ChanceLogic.getMaxChancedValue())));
                    recipe1.tickInputs.put(CWURecipeCapability.CAP, List.of(new Content(
                            engineMachine.getDysonSphereData(),
                            ChanceLogic.getMaxChancedValue(), ChanceLogic.getMaxChancedValue())));
                    return recipe1;
                };
            }
            return ModifierFunction.IDENTITY;
        }
        return ModifierFunction.NULL;
    }

    public void addDisplayText(@NotNull List<Component> textList) {
        if (!this.isFormed) return;
        textList.add(Component.translatable("gtceu.machine.dyson_sphere.number", getDysonSphereData()));
        textList.add(Component.translatable("gtceu.machine.dyson_sphere.voltage",
                (getDysonSphereData() > 0 ? getOverclockVoltage() : 0)));
        textList.add(Component.translatable("gtceu.machine.fission_reactor.damaged",
                getDysonSpheredamageData()).append("%"));
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        var widgets = super.getWidgetsForDisplay(syncManager);
        widgets.addAll(GTLMultiblockDisplays.syncedLines("dyson", (controller, lines) ->
                ((DysonSphereMachine) controller).addDisplayText(lines)).apply(this, syncManager));
        return widgets;
    }
}
