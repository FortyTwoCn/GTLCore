package org.gtlcore.gtlcore.common.machine.multiblock.generator;

import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import org.gtlcore.gtlcore.common.machine.trait.GeneratorArrayRecipeLogic;
import org.gtlcore.gtlcore.api.recipe.CoreRecipeModifiers;
import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.sync_system.annotations.*;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.ISubscription;
import com.hepdd.gtmthings.api.machine.IWirelessEnergyContainerHolder;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import com.hepdd.gtmthings.data.WirelessEnergySavaedData;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.*;
import brachy.modularui.widgets.ToggleButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import java.math.BigInteger;
import java.util.*;

/** Native inserted-generator inventory, recipe selection and physical/wireless energy output. */
public class GeneratorArrayMachine extends StorageMachine implements IWirelessEnergyContainerHolder {
    public static final String WIRELESS_EUT = "gtlcore:wireless_output_eut", WIRELESS_OWNER = "gtlcore:wireless_output_owner";
    private GTRecipeType[] recipeTypeCache;
    private MachineDefinition cachedGenerator;
    @SaveField private UUID userid;
    @SaveField @SyncToClient private boolean isw;
    @SaveField private long eut;
    private ISubscription storageSubscription;
    private WirelessEnergyContainer wirelessEnergyContainerCache;

    public GeneratorArrayMachine(BlockEntityCreationInfo info, Object... args) {
        super(info, 16, new GeneratorArrayRecipeLogic());
        getRecipeLogic().setRegressWhenWaiting(false);
    }
    @Override protected boolean filter(ItemStack stack) {
        if (!(stack.getItem() instanceof MetaMachineItem item) || item.getDefinition() instanceof MultiblockMachineDefinition) return false;
        return Arrays.stream(item.getDefinition().getRecipeTypes()).anyMatch(GeneratorArrayMachine::isGeneratorType);
    }
    private static boolean isGeneratorType(GTRecipeType type) {
        return type == GTRecipeTypes.STEAM_TURBINE_FUELS.value() || type == GTRecipeTypes.GAS_TURBINE_FUELS.value() ||
                type == GTRecipeTypes.COMBUSTION_GENERATOR_FUELS.value() || type == GTLRecipeTypes.SEMI_FLUID_GENERATOR_FUELS.value() ||
                type == GTLRecipeTypes.ROCKET_ENGINE_FUELS.value() || type == GTLRecipeTypes.NAQUADAH_REACTOR.value();
    }
    public MachineDefinition getMachineDefinition() {
        return machineStorage != null && getMachineStorageItem().getItem() instanceof MetaMachineItem item ? item.getDefinition() : null;
    }
    @Override public GTRecipeType[] getRecipeTypes() {
        var definition = getMachineDefinition();
        if (recipeTypeCache == null || definition != cachedGenerator) {
            cachedGenerator = definition;
            recipeTypeCache = definition == null ? new GTRecipeType[] { GTRecipeTypes.DUMMY_RECIPES.value() } : definition.getRecipeTypes();
        }
        return recipeTypeCache;
    }
    @Override public GTRecipeType getRecipeType() {
        var types = getRecipeTypes(); int index = getActiveRecipeType();
        return types[index >= 0 && index < types.length ? index : 0];
    }
    @Override public void onLoad() {
        super.onLoad();
        if (!isRemote() && storageSubscription == null) storageSubscription = machineStorage.addChangedListener(this::onMachineChanged);
        onMachineChanged();
    }
    @Override public void onUnload() {
        if (storageSubscription != null) { storageSubscription.unsubscribe(); storageSubscription = null; }
        recipeTypeCache = null; wirelessEnergyContainerCache = null; super.onUnload();
    }
    protected void onMachineChanged() {
        recipeTypeCache = null;
        if (isFormed()) { getRecipeLogic().markLastRecipeDirty(); getRecipeLogic().updateTickSubscription(); }
    }
    @Override public int getTier() { var definition = getMachineDefinition(); return definition == null ? 0 : definition.getTier(); }
    @Override public int getOverclockTier() { var definition = getMachineDefinition(); return definition == null ? getDefinition().getTier() : definition.getTier(); }
    @Override public int getMinOverclockTier() { return getOverclockTier(); }
    @Override public int getMaxOverclockTier() { return getOverclockTier(); }
    @Override public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        if (!isRemote() && !player.getUUID().equals(userid)) { userid = player.getUUID(); wirelessEnergyContainerCache = null; setChanged(); }
        return true;
    }
    @Override public UUID getUUID() { return userid == null ? getOwnerUUID() : userid; }
    @Override public WirelessEnergyContainer getWirelessEnergyContainerCache() { return wirelessEnergyContainerCache; }
    @Override public void setWirelessEnergyContainerCache(WirelessEnergyContainer container) { wirelessEnergyContainerCache = container; }
    public boolean isWireless() { return isw; }
    public void setWireless(boolean enabled) {
        if (isRemote() || isw == enabled) return;
        isw = enabled; setChanged(); getSyncDataHolder().markClientSyncFieldDirty("isw");
        getRecipeLogic().markLastRecipeDirty(); getRecipeLogic().updateTickSubscription();
    }
    public static WirelessEnergyContainer accountFor(GTRecipe recipe) {
        if (WirelessEnergySavaedData.INSTANCE == null || WirelessEnergyContainer.server == null || !recipe.data.hasUUID(WIRELESS_OWNER)) return null;
        return WirelessEnergyContainer.getOrCreateContainer(recipe.data.getUUID(WIRELESS_OWNER));
    }
    @Override public boolean beforeWorking(GTRecipe recipe) {
        if (recipe != null && recipe.data.contains(WIRELESS_EUT)) {
            var account = accountFor(recipe);
            if (account == null || com.hepdd.gtmthings.config.ConfigHolder.INSTANCE.isWirelessRateEnable && account.getRate() < recipe.data.getLong(WIRELESS_EUT)) {
                org.gtlcore.gtlcore.api.recipe.RecipeResult.of(this, org.gtlcore.gtlcore.api.recipe.RecipeResult.fail(Component.translatable("gui.gtlcore.generator_array.wireless_unavailable")));
                return false;
            }
        }
        if (!super.beforeWorking(recipe)) return false;
        eut = recipe == null ? 0 : recipe.data.getLong(WIRELESS_EUT);
        ((GeneratorArrayRecipeLogic) getRecipeLogic()).clearPendingOutput();
        return true;
    }
    @Override public void afterWorking() { eut = 0; super.afterWorking(); }
    public static int getAmperage(int tier) { return tier > GTValues.HV ? 16 - 2 * tier : 10 - 2 * tier; }
    public static int getEfficiency(GTRecipeType type, int tier) {
        if (type == GTRecipeTypes.STEAM_TURBINE_FUELS.value()) return 125 - 25 * tier;
        if (type == GTLRecipeTypes.ROCKET_ENGINE_FUELS.value()) return 80 - 15 * (tier - GTValues.EV);
        if (type == GTLRecipeTypes.NAQUADAH_REACTOR.value()) return 100 + 50 * (tier - GTValues.IV);
        return 105 - 5 * tier;
    }
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof GeneratorArrayMachine array)) return ModifierFunction.NULL;
        int count = array.getMachineStorageItem().getCount(), tier = array.getOverclockTier(), amperage = getAmperage(tier);
        long eu = recipe.getOutputEUt().getTotalEU(); int efficiency = getEfficiency(array.getRecipeType(), tier);
        if (count <= 0 || eu <= 0 || amperage <= 0 || efficiency <= 0) return ModifierFunction.NULL;
        boolean wireless = array.isw; UUID owner = array.getUUID();
        if (wireless && owner == null) return ModifierFunction.cancel(Component.translatable("gui.gtlcore.generator_array.wireless_unavailable"));
        int maximum = BigInteger.valueOf(GTValues.V[tier]).multiply(BigInteger.valueOf(count)).multiply(BigInteger.TWO)
                .multiply(BigInteger.valueOf(amperage)).divide(BigInteger.valueOf(eu)).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
        int parallels = CoreRecipeModifiers.parallelAmount(array, recipe, maximum, false);
        if (parallels == 0) return ModifierFunction.NULL;
        var modifier = ModifierFunction.builder().modifyAllContents(IAdvancedContentModifier.preciseMultiplier(parallels))
                .eutMultiplier(parallels).parallels(parallels).durationMultiplier(efficiency / 100.0).build();
        return source -> {
            var result = modifier.apply(source);
            if (result != null && wireless) {
                result.data = result.data.copy(); result.data.putLong(WIRELESS_EUT, result.getOutputEUt().getTotalEU());
                result.data.putUUID(WIRELESS_OWNER, owner); result.tickOutputs.remove(EURecipeCapability.CAP);
                return result.copy();
            }
            return result;
        };
    }
    @Override public List<IWidget> getWidgetsForDisplay(PanelSyncManager sync) {
        var widgets = super.getWidgetsForDisplay(sync);
        var wireless = new BooleanSyncValue(this::isWireless, this::setWireless).allowC2S(); sync.syncValue("arrayWireless", wireless);
        widgets.add(new ToggleButton().value(wireless).width(160).height(18).background(GTGuiTextures.BUTTON)
                .overlay(Text.dynamic(() -> Component.translatable("gtceu.machine.generator_array.wireless")
                        .append(Component.translatable(wireless.getBoolValue() ? "gtceu.machine.on" : "gtceu.machine.off")))));
        var power = new LongSyncValue(() -> eut); sync.syncValue("arrayWirelessEUt", power);
        widgets.add(Text.dynamic(() -> Component.translatable("gtceu.recipe.eu_inverted", FormattingUtil.formatNumbers(power.getLongValue())))
                .asWidget().setEnabledIf(widget -> power.getLongValue() > 0));
        return widgets;
    }
}
