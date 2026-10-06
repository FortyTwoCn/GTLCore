package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import org.gtlcore.gtlcore.utils.MachineIO;
import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.PanelSyncManager;

import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import com.hepdd.gtmthings.data.WirelessEnergySavaedData;
import com.hepdd.gtmthings.utils.TeamUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.math.BigInteger;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class HarmonyMachine extends NoEnergyMultiblockMachine {
    public static final long STARTUP_GAS = 1_024_000_000L;
    private static final long GAS_TRANSFER = 100_000_000L;
    @SaveField
    private int oc = 0;
    @SaveField
    private long hydrogen = 0, helium = 0;
    @SaveField
    private UUID userid;

    protected ConditionalSubscriptionHandler StartupSubs;

    public HarmonyMachine(BlockEntityCreationInfo holder, Object... args) {
        this(holder, new com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic());
    }

    public HarmonyMachine(BlockEntityCreationInfo holder, com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic logic) {
        super(holder, logic);
        this.StartupSubs = new ConditionalSubscriptionHandler(this, this::StartupUpdate, () -> isFormed() && !isRemoved());
    }

    @Override
    public void onLoad() {
        super.onLoad(); StartupSubs.initialize(getLevel());
    }

    @Override
    public void onUnload() {
        StartupSubs.unsubscribe(); super.onUnload();
    }

    protected void StartupUpdate() {
        if (getOffsetTimer() % 20 == 0) {
            oc = 0;
            if (hydrogen <= Long.MAX_VALUE - GAS_TRANSFER && MachineIO.inputFluid(this, GTMaterials.Hydrogen.getFluid((int) GAS_TRANSFER))) {
                hydrogen += GAS_TRANSFER;
            }
            if (helium <= Long.MAX_VALUE - GAS_TRANSFER && MachineIO.inputFluid(this, GTMaterials.Helium.getFluid((int) GAS_TRANSFER))) {
                helium += GAS_TRANSFER;
            }
            if (MachineIO.notConsumableCircuit(this, 4)) {
                oc = 4;
            }
            if (MachineIO.notConsumableCircuit(this, 3)) {
                oc = 3;
            }
            if (MachineIO.notConsumableCircuit(this, 2)) {
                oc = 2;
            }
            if (MachineIO.notConsumableCircuit(this, 1)) {
                oc = 1;
            }
            markAsChanged();
        }
    }

    public long getStartupEnergy() {
        return oc < 1 || oc > 4 ? 0 : 5_277_655_810_867_200L << (3 * (oc - 1));
    }
    public long getStoredHydrogen() { return hydrogen; }
    public long getStoredHelium() { return helium; }
    public UUID getWirelessOwner() { return userid == null ? getOwnerUUID() : userid; }

    private @Nullable WirelessEnergyContainer account() {
        var owner = getWirelessOwner();
        return owner == null || WirelessEnergySavaedData.INSTANCE == null || WirelessEnergyContainer.server == null ? null :
                WirelessEnergyContainer.getOrCreateContainer(owner);
    }

    private boolean canPayStartup() {
        if (oc < 1 || oc > 4 || hydrogen < STARTUP_GAS || helium < STARTUP_GAS) return false;
        var account = account(); long energy = getStartupEnergy();
        return account != null && account.getStorage().compareTo(BigInteger.valueOf(energy)) >= 0 &&
                (!com.hepdd.gtmthings.config.ConfigHolder.INSTANCE.isWirelessRateEnable || account.getRate() >= energy);
    }

    @Override
    public void formStructure(String patternName) {
        super.formStructure(patternName);
        if (getPatternState(patternName).isFormed()) StartupSubs.initialize(getLevel());
    }

    @Override
    public void invalidateStructure(String patternName) {
        super.invalidateStructure(patternName); StartupSubs.updateSubscription();
    }

    public static ModifierFunction recipeModifier(MetaMachine machine, @NotNull GTRecipe recipe) {
        if (!(machine instanceof HarmonyMachine harmony) || !harmony.canPayStartup()) return ModifierFunction.NULL;
        int duration = 4800 >> harmony.oc;
        // Native modifiers run during candidate matching and preview. Charge only accepted starts, not searches.
        return input -> { var modified = input.copy(); modified.duration = duration; return modified; };
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        if (recipe == null || !canPayStartup() || !super.beforeWorking(recipe) || !canPayStartup()) return false;
        var account = account(); long energy = getStartupEnergy();
        if (account == null || account.removeEnergy(energy, this) != energy) return false;
        hydrogen -= STARTUP_GAS; helium -= STARTUP_GAS; markAsChanged(); return true;
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        if (!isRemote() && !player.getUUID().equals(userid)) {
            this.userid = player.getUUID(); markAsChanged();
        }
        return true;
    }

    @Override
    public InteractionResult onUseWithItem(ExtendedUseOnContext context) {
        var player = context.getPlayer();
        if (player != null && context.getItemInHand().is(GTItems.TOOL_DATA_STICK.asItem())) {
            if (!isRemote()) {
                this.userid = player.getUUID(); markAsChanged(); player.sendSystemMessage(
                        Component.translatable(
                                "gtmthings.machine.wireless_energy_hatch.tooltip.bind",
                                TeamUtil.GetName(player)));
            }
            return InteractionResult.sidedSuccess(isRemote());
        }
        return super.onUseWithItem(context);
    }

    public void addDisplayText(List<Component> textList) {
        if (this.isFormed()) {
            var owner = getWirelessOwner(); var account = account();
            if (owner != null) {
                textList.add(Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.0",
                        TeamUtil.GetName(getLevel(), owner)));
                textList.add(Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.1",
                        account == null ? "0" : FormattingUtil.formatNumbers(account.getStorage())));
            }
            textList.add(Component.translatable("tooltip.gtlcore.startup_energy_cost", FormattingUtil.formatNumbers(getStartupEnergy())));
            textList.add(Component.translatable("tooltip.gtlcore.hydrogen_storage", FormattingUtil.formatNumbers(hydrogen)));
            textList.add(Component.translatable("tooltip.gtlcore.helium_storage", FormattingUtil.formatNumbers(helium)));
        }
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        var widgets = super.getWidgetsForDisplay(syncManager);
        widgets.addAll(GTLMultiblockDisplays.syncedLines("harmony", (controller, lines) ->
                ((HarmonyMachine) controller).addDisplayText(lines)).apply(this, syncManager));
        return widgets;
    }
}
