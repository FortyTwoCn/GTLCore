package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.machine.trait.IMERecipeHandlerTrait;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEFilterIOPartMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEFilterIOTrait;
import org.gtlcore.gtlcore.integration.ae2.AEOutputBuffer;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.integration.ae2.gui.AEKeyStorageSyncHandler;
import com.gregtechceu.gtceu.integration.ae2.gui.AEStackDisplayWidget;
import com.gregtechceu.gtceu.integration.ae2.gui.ScrollPreservingGrid;
import brachy.modularui.screen.UISettings;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.DynamicLinkedSyncHandler;
import brachy.modularui.widgets.DynamicSyncedWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.scroll.VerticalScrollData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.component.CustomData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public abstract class MEExtendedOutputPartMachineBase extends MEIOPartMachine implements IDataStickInteractable, IMEFilterIOPartMachine, IMuiMachine {

    protected final static int MIN_FREQUENCY = ConfigHolder.INSTANCE.MEPatternOutputMin;
    protected final static int MAX_FREQUENCY = ConfigHolder.INSTANCE.MEPatternOutputMax;

    @SaveField
    protected final AEOutputBuffer outputStorage = new AEOutputBuffer(this::flushAsyncQueue);

    @Getter
    protected final Object2LongMap<AEKey> buffer = outputStorage.storage;

    @Getter
    protected final NotifiableMERecipeHandlerTrait<SizedIngredient, ItemStack> itemOutputHandler;

    @Getter
    protected final NotifiableMERecipeHandlerTrait<SizedFluidIngredient, FluidStack> fluidOutputHandler;

    @SaveField
    @SyncToClient
    @Getter
    protected int priority = 10;

    public MEExtendedOutputPartMachineBase(BlockEntityCreationInfo holder) {
        super(holder, IO.OUT);
        itemOutputHandler = attachTrait(createItemOutputHandler());
        fluidOutputHandler = attachTrait(createFluidOutputHandler());
        registerDefaultServices();
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        mainWidget.child(buildOutputDisplay(syncManager));
    }

    protected Flow buildOutputDisplay(PanelSyncManager syncManager) {
        var online = new BooleanSyncValue(this::isOnline);
        syncManager.syncValue("extendedOutputOnline", online);
        var storage = new AEKeyStorageSyncHandler(outputStorage);
        syncManager.syncValue("extendedOutputContents", storage);
        int[] scroll = {0};
        var display = new DynamicLinkedSyncHandler<>(storage).widgetProvider((manager, value) -> {
            var list = value.getValue();
            if (list.isEmpty()) return Text.lang("gtceu.gui.waiting_list_empty").asWidget();
            return new ScrollPreservingGrid(scroll).size(167, 112).scrollable(new VerticalScrollData())
                    .gridOfSizeWidth(list.size(), 1, (x, y, index) -> Flow.row().coverChildren()
                            .child(new AEStackDisplayWidget(list, index))
                            .child(Text.dynamic(() -> Component.literal(Long.toString(list.get(index).amount()))
                                    .append(" ").append(list.get(index).what().getDisplayName())).asWidget().width(145).height(18)));
        });
        return Flow.col().coverChildren().margin(4)
                .child(Text.dynamic(() -> Component.translatable(online.getBoolValue() ?
                        "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline")).asWidget().height(16))
                .child(new DynamicSyncedWidget<>().syncHandler(display).size(167, 112));
    }

    // ========================================
    // ME Output Handlers && Tick Service
    // ========================================

    @Override
    public Pair<IMERecipeHandlerTrait<SizedIngredient, ItemStack>, IMERecipeHandlerTrait<SizedFluidIngredient, FluidStack>> getMERecipeHandlerTraits() {
        return Pair.of(itemOutputHandler, fluidOutputHandler);
    }

    @Override
    public abstract @NotNull IMEFilterIOTrait getMETrait();

    protected abstract NotifiableMERecipeHandlerTrait<SizedIngredient, ItemStack> createItemOutputHandler();

    protected abstract NotifiableMERecipeHandlerTrait<SizedFluidIngredient, FluidStack> createFluidOutputHandler();

    protected void registerDefaultServices() {}

    // ========================================
    // Filter && Priority SYSTEM
    // ========================================

    protected void notifyHandlers() {
        itemOutputHandler.notifyListeners();
        fluidOutputHandler.notifyListeners();
    }

    protected void updatePriority() {
        for (var controller : this.getControllers()) {
            if (controller instanceof IRecipeCapabilityMachine machine) {
                machine.sortMEOutput();
            }
        }
    }

    public void setPriority(int priority) {
        int bounded = Math.max(10, Math.min(100_000, priority));
        if (this.priority == bounded) return;
        this.priority = bounded;
        markAsChanged();
        syncDataHolder.markClientSyncFieldDirty("priority");
        updatePriority();
    }

    public void setTerminalPriority(int priority) { setPriority(priority); }

    // ========================================
    // DataStick Copy
    // ========================================

    @Override
    public InteractionResult onDataStickUse(Player player, ItemStack dataStick) {
        CompoundTag tag = dataStick.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag == null || !tag.contains("MEExtendedExportBuffer")) {
            return InteractionResult.PASS;
        }

        if (!isRemote()) {
            readConfigFromTag(tag.getCompound("MEExtendedExportBuffer"));
            player.sendSystemMessage(Component.translatable("gtceu.machine.me.import_paste_settings"));
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    @Override
    public InteractionResult onDataStickShiftUse(Player player, ItemStack dataStick) {
        if (!isRemote()) {
            CompoundTag tag = dataStick.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            tag.put("MEExtendedExportBuffer", writeConfigToTag());
            dataStick.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            dataStick.set(DataComponents.CUSTOM_NAME, Component.translatable("gtceu.machine.me.me_extended_export_buffer.data_stick.name"));
            player.sendSystemMessage(Component.translatable("gtceu.machine.me.import_copy_settings"));
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    protected CompoundTag writeConfigToTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("priority", priority);
        return tag;
    }

    protected void readConfigFromTag(CompoundTag tag) {
        if (tag.contains("priority")) {
            setPriority(tag.getInt("priority"));
        }
        updatePriority();
    }

    // ========================================
    // Persist
    // ========================================

    protected void flushAsyncQueue() {}

    @Override
    public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        super.saveToItem(stack, registries);
        var payload = new CompoundTag();
        payload.put("buffer", outputStorage.serializeNBT(registries));
        payload.put("config", writeConfigToTag());
        var custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        custom.put("gtlcoreMEOutput", payload);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput components) {
        super.applyImplicitComponents(components);
        var custom = components.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (custom.contains("gtlcoreMEOutput", 10)) {
            var payload = custom.getCompound("gtlcoreMEOutput");
            outputStorage.deserializeNBT(getLevel().registryAccess(), payload.getList("buffer", 10));
            readConfigFromTag(payload.getCompound("config"));
        }
    }

    protected abstract class MEItemOutputHandler extends NotifiableMERecipeHandlerTrait<SizedIngredient, ItemStack> {

        @Override
        public List<SizedIngredient> meHandleRecipeOutput(List<?> contents, boolean simulate) {
            var copy = new java.util.ArrayList<SizedIngredient>(contents.size());
            for (var content : contents) copy.add(copyContent(content));
            return meHandleRecipeOutputInner(copy, simulate);
        }

        public MEItemOutputHandler() {
            super();
        }

        public MEExtendedOutputPartMachineBase getMachine() {
            return (MEExtendedOutputPartMachineBase) super.getMachine();
        }

        @Override
        public RecipeCapability<SizedIngredient> getCapability() {
            return ItemRecipeCapability.CAP;
        }

        @Override
        public IO getIo() {
            return IO.OUT;
        }

        // region Unused
        @Override
        public int[] getActiveSlots() {
            return new int[0];
        }

        @Override
        public Int2ObjectMap<List<ItemStack>> getActiveAndUnCachedSlotsLimitContentsMap() {
            return Int2ObjectMaps.emptyMap();
        }

        @Override
        public Object2LongMap<ItemStack> getStackMapFromFirstAvailableSlot(IntCollection slots) {
            return Object2LongMaps.emptyMap();
        }

        @Override
        public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<SizedIngredient> left, boolean simulate, int trySlot) {
            return false;
        }

        @Override
        public void prepareMEHandleContents(GTRecipe recipe, List<SizedIngredient> left, boolean simulate) {}

        @Override
        public Object2LongMap<SizedIngredient> getPreparedMEHandleContents() {
            return Object2LongMaps.emptyMap();
        }
        // endregion

        @Override
        public int getPriority() {
            return priority;
        }
    }

    public abstract class MEFluidOutputHandler extends NotifiableMERecipeHandlerTrait<SizedFluidIngredient, FluidStack> {

        @Override
        public List<SizedFluidIngredient> meHandleRecipeOutput(List<?> contents, boolean simulate) {
            var copy = new java.util.ArrayList<SizedFluidIngredient>(contents.size());
            for (var content : contents) copy.add(copyContent(content));
            return meHandleRecipeOutputInner(copy, simulate);
        }

        public MEFluidOutputHandler() {
            super();
        }

        public MEExtendedOutputPartMachineBase getMachine() {
            return (MEExtendedOutputPartMachineBase) super.getMachine();
        }

        @Override
        public RecipeCapability<SizedFluidIngredient> getCapability() {
            return FluidRecipeCapability.CAP;
        }

        @Override
        public IO getIo() {
            return IO.OUT;
        }

        // region Unused
        @Override
        public int[] getActiveSlots() {
            return new int[0];
        }

        @Override
        public Int2ObjectMap<List<FluidStack>> getActiveAndUnCachedSlotsLimitContentsMap() {
            return Int2ObjectMaps.emptyMap();
        }

        @Override
        public Object2LongMap<FluidStack> getStackMapFromFirstAvailableSlot(IntCollection slots) {
            return Object2LongMaps.emptyMap();
        }

        @Override
        public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<SizedFluidIngredient> left, boolean simulate, int trySlot) {
            return false;
        }

        @Override
        public void prepareMEHandleContents(GTRecipe recipe, List<SizedFluidIngredient> left, boolean simulate) {}

        @Override
        public Object2LongMap<SizedFluidIngredient> getPreparedMEHandleContents() {
            return Object2LongMaps.emptyMap();
        }
        // endregion

        @Override
        public int getPriority() {
            return priority;
        }
    }
}
