package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.gui.MEPatternCatalystUIManager;
import org.gtlcore.gtlcore.api.gui.PatternCircuitConfigurator;
import org.gtlcore.gtlcore.api.machine.trait.*;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternTrait;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import com.gregtechceu.gtceu.api.transfer.fluid.FluidHandlerList;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.integration.ae2.gui.PatternBufferItemSlot;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.value.sync.ItemSlotSyncHandler;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.slot.SlotGroup;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import brachy.modularui.widget.ParentWidget;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.utils.*;


import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.*;
import appeng.api.stacks.*;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.EncodedPatternItem;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;


public class MEPatternBufferPartMachine extends MEPatternBufferPartMachineBase {


    @SyncToClient
    @SaveField
    public boolean isHiddenTerminal = false;

    @Override
    public boolean isVisibleInTerminal() {
        return !isHiddenTerminal;
    }

    private final InternalInventory internalPatternInventory = new InternalInventory() {

        @Override
        public int size() {
            return maxPatternCount;
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            return patternInventory.getStackInSlot(slotIndex);
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            patternInventory.setStackInSlot(slotIndex, stack);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < maxPatternCount && AEUtils.PROCESS_FILTER.apply(stack);
        }
    };

    // ========================================
    // Info
    // ========================================

    protected final int maxPatternCount;
    private final boolean[] hasPatternArray;
    @SyncToClient
    protected final boolean[] cacheRecipe;
    @SaveField
    protected boolean keepByProduct = false;
    @SyncToClient
    @SaveField
    private int embeddedCircuitConfig = 1;
    @SyncToClient
    @SaveField
    private boolean skipExistingCircuitPatterns = true;

    // ========================================
    // Inventory
    // ========================================

    @SaveField
    @SyncToClient
    private final CustomItemStackHandler patternInventory;

    public CustomItemStackHandler getPatternInventory() { return patternInventory; }

    @SaveField
    protected final CustomItemStackHandler[] catalystItems;

    @SaveField
    protected final FluidHandlerList[] catalystFluids;

    @Getter
    @SaveField
    protected final InternalSlot[] internalInventory;

    // ========================================
    // Handlers
    // ========================================

    protected final MEPatternBufferRecipeHandlerTrait recipeHandler;

    // ========================================
    // Cache Map
    // ========================================

    protected final Int2ReferenceMap<ObjectSet<@NotNull GTRecipe>> recipeMultipleCacheMap;
    @SaveField
    protected byte[] cacheRecipeCount;
    @SaveField
    private final PersistedRecipeCache persistedRecipeCache = new PersistedRecipeCache();
    private CompoundTag pendingRecipeCache = new CompoundTag();
    private final BiMap<@NotNull IPatternDetails, Integer> patternSlotMap;
    private final Int2ObjectMap<IPatternDetails> slot2PatternMap;
    protected IntConsumer removeSlotFromMap = i -> {};

    public MEPatternBufferPartMachine(BlockEntityCreationInfo holder, int maxPatternCount, IO io) {
        super(holder, io);

        this.maxPatternCount = maxPatternCount;

        this.hasPatternArray = new boolean[maxPatternCount];
        this.cacheRecipe = new boolean[maxPatternCount];
        org.gtlcore.gtlcore.integration.ae2.PatternCacheSyncTransformer.bind(getClass());
        this.internalInventory = new InternalSlot[maxPatternCount];
        this.catalystItems = new CustomItemStackHandler[maxPatternCount];
        this.catalystFluids = new FluidHandlerList[maxPatternCount];
        this.cacheRecipeCount = new byte[maxPatternCount];

        this.patternSlotMap = HashBiMap.create();
        this.slot2PatternMap = new Int2ObjectOpenHashMap<>();
        this.recipeMultipleCacheMap = new Int2ReferenceOpenHashMap<>();

        this.patternInventory = new CustomItemStackHandler(maxPatternCount) {
            @Override public void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                if (!isRemote() && getLevel() != null) onPatternChange(slot);
                markAsChanged();
                syncDataHolder.markClientSyncFieldDirty("patternInventory");
            }
        };
        this.patternInventory.setFilter(AEUtils.PROCESS_FILTER::apply);
        Arrays.setAll(internalInventory, this::createInternalSlot);
        Arrays.setAll(catalystItems, i -> {
            var transfer = new CustomItemStackHandler(9);
            transfer.setFilter(stack -> !(stack.getItem() instanceof EncodedPatternItem<?>));
            return transfer;
        });
        Arrays.setAll(catalystFluids, i -> createCatalystTanks());
        Arrays.fill(cacheRecipeCount, (byte) 1);

        this.recipeHandler = attachTrait(createRecipeHandler(io));

        for (InternalSlot internalSlot : internalInventory) {
            internalSlot.setOnContentsChanged(() -> {
                recipeHandler.getMeFluidHandler().notifyListeners();
                recipeHandler.getMeItemHandler().notifyListeners();
            });
        }

        if (io == IO.BOTH) {
            getMainNode().addService(IGridTickable.class, new Ticker());
        }
    }

    protected InternalSlot createInternalSlot(int slotIndex) {
        return new PatternBufferInternalSlot(slotIndex);
    }

    protected MEPatternBufferRecipeHandlerTrait createRecipeHandler(IO io) {
        return new MEPatternBufferRecipeHandlerTrait(this, io);
    }

    // ========================================
    // LIFECYCLE & NETWORK MANAGEMENT
    // ========================================

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            scheduleForNextServerTick(() -> {
                if (isRemoved()) return;
                slot2PatternMap.clear();
                Arrays.fill(hasPatternArray, false);
                for (int i = 0; i < patternInventory.getSlots(); i++) {
                    var pattern = patternInventory.getStackInSlot(i);
                    var realPattern = getRealPattern(i, pattern);
                    if (realPattern != null) {
                        this.slot2PatternMap.put(i, realPattern);
                        hasPatternArray[i] = true;
                    }
                }
                for (int slot = 0; slot < maxPatternCount; slot++) { reCalculateCatalystItemMap(slot); reCalculateCatalystFluidMap(slot); }
                restoreRecipeCache();
                reCalculatePatternSlotMap();
                needPatternSync = true;
            });
            for (int i = 0; i < maxPatternCount; i++) {
                final int index = i;
                internalInventory[index].setOnContentsChanged(() -> {
                    recipeHandler.getMeFluidHandler().notifyListeners();
                    recipeHandler.getMeItemHandler().notifyListeners();
                });
                catalystItems[index].setOnContentsChanged(() -> reCalculateCatalystItemMap(index));
                for (IFluidHandler transfer : catalystFluids[index].handlers) {
                    if (transfer instanceof CustomFluidTank storage) {
                        storage.setOnContentsChanged(() -> reCalculateCatalystFluidMap(index));
                    }
                }
            }
        }
    }

    protected void onPatternChange(int index) {
        if (isRemote() || destroying) return;

        var internalInv = internalInventory[index];
        internalInv.getCacheManager().clearAllCaches();
        var newPattern = patternInventory.getStackInSlot(index);
        var newPatternDetailsWithOutCircuit = getRealPattern(index, newPattern);
        var oldPatternDetails = slot2PatternMap.get(index);

        if (newPatternDetailsWithOutCircuit != null) {
            slot2PatternMap.put(index, newPatternDetailsWithOutCircuit);
            hasPatternArray[index] = true;
        } else {
            slot2PatternMap.remove(index);
            hasPatternArray[index] = false;
        }

        if (oldPatternDetails != null && !oldPatternDetails.equals(newPatternDetailsWithOutCircuit)) {
            internalInv.clearVirtualSupply();
            // A dispatched virtual circuit may own the old cache. Restore the newly decoded circuit afterwards.
            getRealPattern(index, newPattern);
            removeSlotFromGTRecipeCache(index);
            refundSlot(internalInv.getItemInventory(), internalInv.getFluidInventory());
            AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource);
        }

        reCalculatePatternSlotMap();
        needPatternSync = true;
    }

    @Override
    public void onMachineDestroyed() {
        destroying = true;
        patternInventory.dropInventoryInWorld(getLevel(), getBlockPos());
        for (CustomItemStackHandler catalystItem : catalystItems) catalystItem.dropInventoryInWorld(getLevel(), getBlockPos());
        super.onMachineDestroyed();
    }

    private void reCalculateCatalystItemMap(int slot) {
        final var internalSlot = internalInventory[slot];
        final var itemCatalystInventory = internalSlot.getItemCatalystInventory();
        itemCatalystInventory.clear();
        internalSlot.getConfiguredVirtualItems().clear();
        internalSlot.getConfiguredVirtualFluids().clear();
        var catalystItem = catalystItems[slot];
        for (int i = 0; i < catalystItem.getSlots(); i++) {
            ItemStack stack = catalystItem.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (VirtualIngredientBehavior.isVirtualIngredient(stack)) {
                if (!VirtualIngredientBehavior.isBound(stack)) continue;
                // A wrapper parked here vouches for its payload without the slot ever holding it. The wrapper itself
                // is not a catalyst, so it must not reach the catalyst map, or recipes would start matching against
                // the wrapper item instead of what it stands for.
                AEItemKey payloadItem = VirtualIngredientBehavior.payloadItemKey(stack);
                if (payloadItem != null) internalSlot.getConfiguredVirtualItems().add(payloadItem);
                AEFluidKey payloadFluid = VirtualIngredientBehavior.payloadFluidKey(stack);
                if (payloadFluid != null) internalSlot.getConfiguredVirtualFluids().add(payloadFluid);
                continue;
            }

            itemCatalystInventory.mergeLong(AEItemKey.of(stack), stack.getCount(), Math::addExact);
        }
        removeSlotFromGTRecipeCache(slot);
        internalSlot.getOnContentsChanged().run();
    }

    private void reCalculateCatalystFluidMap(int slot) {
        final var fluidCatalystInventory = internalInventory[slot].getFluidCatalystInventory();
        fluidCatalystInventory.clear();
        var catalystFluid = catalystFluids[slot];
        for (int i = 0; i < catalystFluid.getTanks(); i++) {
            FluidStack stack = catalystFluid.getFluidInTank(i);
            if (!stack.isEmpty()) {
                fluidCatalystInventory.mergeLong(AEFluidKey.of(stack), stack.getAmount(), Math::addExact);
            }
        }
        internalInventory[slot].getOnContentsChanged().run();
    }

    protected void reCalculatePatternSlotMap() {
        patternSlotMap.clear();
        for (var entry : Int2ObjectMaps.fastIterable(slot2PatternMap)) {
            int slot = entry.getIntKey();
            var pattern = entry.getValue();
            if (pattern != null) {
                if (cacheRecipe[slot]) patternSlotMap.forcePut(pattern, slot);
                else patternSlotMap.putIfAbsent(pattern, slot);
            }
        }
    }

    protected void removeSlotFromGTRecipeCache(int slot) {
        cacheRecipe[slot] = false;
        syncDataHolder.markClientSyncFieldDirty("cacheRecipe");
        markAsChanged();
        recipeMultipleCacheMap.remove(slot);
        removeSlotFromMap.accept(slot);
        notifyProxySlotRemoved(slot);
    }

    protected void refreshAllByProduct() {
        this.slot2PatternMap.clear();
        for (int i = 0; i < patternInventory.getSlots(); i++) {
            var pattern = patternInventory.getStackInSlot(i);
            var realPattern = getRealPattern(i, pattern);
            if (realPattern != null) {
                this.slot2PatternMap.put(i, realPattern);
            }
        }
        reCalculatePatternSlotMap();
        needPatternSync = true;
    }

    // ========================================
    // Slot Access and Pattern Routing
    // ========================================

    @Override
    @Nullable
    protected Integer getSlotIndexForPattern(IPatternDetails pattern) {
        return patternSlotMap.get(pattern);
    }

    @Override
    protected int getInternalSlotCount() {
        return internalInventory.length;
    }

    @Override
    protected InternalSlot getInternalSlot(int slotIndex) {
        return internalInventory[slotIndex];
    }

    @Override
    protected boolean hasRecipeCacheInSlot(int slotIndex) {
        return cacheRecipe[slotIndex];
    }

    @Override
    protected boolean hasPatternInSlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < hasPatternArray.length && hasPatternArray[slotIndex];
    }

    // ========================================
    // Persistence
    // ========================================

    private final class PersistedRecipeCache implements INBTSerializable<CompoundTag> {
        @Override public CompoundTag serializeNBT(HolderLookup.Provider registries) {
            if (!pendingRecipeCache.isEmpty()) return pendingRecipeCache.copy();
            var tag = new CompoundTag();
            for (var entry : Int2ReferenceMaps.fastIterable(recipeMultipleCacheMap)) {
                var recipes = new ListTag();
                for (var recipe : entry.getValue()) recipes.add(StringTag.valueOf(recipe.id.toString()));
                if (!recipes.isEmpty()) tag.put(Integer.toString(entry.getIntKey()), recipes);
            }
            return tag;
        }
        @Override public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
            recipeMultipleCacheMap.clear();
            Arrays.fill(cacheRecipe, false);
            pendingRecipeCache = tag.copy();
        }
    }

    private void restoreRecipeCache() {
        for (int i = 0; i < cacheRecipeCount.length; i++) if (cacheRecipeCount[i] <= 0) cacheRecipeCount[i] = 1;
        if (getLevel() == null) return;
        for (String key : pendingRecipeCache.getAllKeys()) {
            int slot;
            try { slot = Integer.parseInt(key); } catch (NumberFormatException invalid) { continue; }
            if (slot < 0 || slot >= maxPatternCount) continue;
            for (Tag value : pendingRecipeCache.getList(key, Tag.TAG_STRING)) {
                var id = ResourceLocation.tryParse(value.getAsString());
                if (id == null) continue;
                getLevel().getRecipeManager().byKey(id).ifPresent(holder -> {
                    if (holder.value() instanceof GTRecipe recipe) getMETrait().setSlotCacheRecipe(slot, recipe);
                });
            }
        }
        pendingRecipeCache = new CompoundTag();
    }

    public void copyFromTag(CompoundTag tag, ServerPlayer player) {
        var registries = player.level().registryAccess();
        var list = tag.getList("patterns", Tag.TAG_COMPOUND);
        int cursor = 0;
        for (int slot = 0; slot < maxPatternCount && cursor < list.size(); slot++) {
            if (!patternInventory.getStackInSlot(slot).isEmpty()) continue;
            var data = list.getCompound(cursor);
            var pattern = ItemStack.parse(registries, data.getCompound("pattern")).orElse(ItemStack.EMPTY);
            if (pattern.isEmpty() || !AEUtils.PROCESS_FILTER.apply(pattern) || data.getByte("cacheCount") <= 0) return;
            int blank = -1;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (AEItems.BLANK_PATTERN.is(player.getInventory().getItem(i))) { blank = i; break; }
            }
            if (blank < 0) break;
            player.getInventory().removeItem(blank, 1);
            cacheRecipeCount[slot] = data.getByte("cacheCount");
            internalPatternInventory.setItemDirect(slot, pattern.copyWithCount(1));
            cursor++;
        }
        setCustomName(tag.getString("name"));
        markAsChanged();
    }

    public CompoundTag copyToTag(CompoundTag target) {
        var tag = new CompoundTag();
        tag.putString("name", customName);
        var patterns = new ListTag();
        for (int slot = 0; slot < maxPatternCount; slot++) {
            var stack = patternInventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            var data = new CompoundTag();
            data.put("pattern", stack.save(getLevel().registryAccess()));
            data.putByte("cacheCount", cacheRecipeCount[slot]);
            patterns.add(data);
        }
        tag.put("patterns", patterns);
        target.put("tag", tag);
        return target;
    }

    public boolean pasteFromTag(CompoundTag tag) {
        var registries = getLevel().registryAccess();
        var list = tag.getList("patterns", Tag.TAG_COMPOUND);
        // A cut transfers owned inventory. Never overwrite an occupied destination's shared catalysts.
        if (!sharedCatalystInventory.isEmpty() || !sharedCatalystTank.isEmpty() ||
                !sharedCircuitInventory.storage.getStackInSlot(0).isEmpty()) return false;
        var available = new IntArrayList();
        for (int i = 0; i < maxPatternCount; i++) {
            var slot = internalInventory[i];
            boolean fluidsEmpty = true;
            for (int tank = 0; tank < catalystFluids[i].getTanks(); tank++) {
                if (!catalystFluids[i].getFluidInTank(tank).isEmpty()) { fluidsEmpty = false; break; }
            }
            if (patternInventory.getStackInSlot(i).isEmpty() && slot.getItemInventory().isEmpty() && slot.getFluidInventory().isEmpty() &&
                    catalystItems[i].getStacks().stream().allMatch(ItemStack::isEmpty) && fluidsEmpty) available.add(i);
        }
        if (available.size() < list.size()) return false;
        var patterns = new ArrayList<ItemStack>();
        var items = new ArrayList<CustomItemStackHandler>();
        var fluids = new ArrayList<FluidHandlerList>();
        var inputs = new ArrayList<CompoundTag>();
        var sharedItems = new CustomItemStackHandler(9);
        var sharedCircuit = new CustomItemStackHandler(1);
        var sharedFluids = createCatalystTanks();
        try {
        for (Tag value : list) {
            var data = (CompoundTag) value;
            var pattern = ItemStack.parse(registries, data.getCompound("pattern")).orElse(ItemStack.EMPTY);
            if (pattern.isEmpty() || !AEUtils.PROCESS_FILTER.apply(pattern) || data.getByte("cacheCount") <= 0) return false;
            patterns.add(pattern);
            var itemHandler = new CustomItemStackHandler(9);
            itemHandler.deserializeNBT(registries, data.getCompound("catalystItems"));
            items.add(itemHandler);
            var tanks = createCatalystTanks();
            tanks.deserializeNBT(registries, data.getCompound("catalystFluids"));
            fluids.add(tanks);
            var stock = new InternalSlot(available.getInt(inputs.size()));
            stock.deserializeNBT(registries, data.getCompound("inputs"));
            inputs.add(stock.serializeNBT(registries));
        }
        sharedItems.deserializeNBT(registries, tag.getCompound("sharedCatalystInventory"));
        sharedCircuit.deserializeNBT(registries, tag.getCompound("sharedCircuitInventory"));
        var savedTanks = tag.getList("sharedCatalystTank", Tag.TAG_COMPOUND);
        for (int i = 0; i < sharedFluids.getTanks(); i++) ((CustomFluidTank) sharedFluids.handlers[i]).deserializeNBT(
                registries, i < savedTanks.size() ? savedTanks.getCompound(i) : new CompoundTag());
        } catch (RuntimeException invalidData) { return false; }
        setCustomName(tag.getString("name"));
        keepByProduct = tag.getBoolean("keepByProduct");
        for (int index = 0; index < list.size(); index++) {
            int slot = available.getInt(index);
            var data = list.getCompound(index);
            cacheRecipeCount[slot] = data.getByte("cacheCount");
            internalPatternInventory.setItemDirect(slot, patterns.get(index).copyWithCount(1));
            catalystItems[slot].deserializeNBT(registries, items.get(index).serializeNBT(registries));
            catalystFluids[slot].deserializeNBT(registries, fluids.get(index).serializeNBT(registries));
            internalInventory[slot].deserializeNBT(registries, inputs.get(index));
            reCalculateCatalystItemMap(slot); reCalculateCatalystFluidMap(slot);
        }
        sharedCatalystInventory.storage.deserializeNBT(registries, sharedItems.serializeNBT(registries));
        sharedCircuitInventory.storage.deserializeNBT(registries, sharedCircuit.serializeNBT(registries));
        for (int i = 0; i < sharedCatalystTank.getTanks(); i++) {
            sharedCatalystTank.getStorages()[i].deserializeNBT(registries, ((CustomFluidTank) sharedFluids.handlers[i]).serializeNBT(registries));
        }
        sharedCatalystTank.onContentsChanged(); sharedCatalystInventory.onContentsChanged(); sharedCircuitInventory.onContentsChanged();
        releaseProxies();
        for (long position : tag.getLongArray("proxies")) {
            var pos = BlockPos.of(position);
            if (getLevel().hasChunkAt(pos) && MetaMachine.getMachine(getLevel(), pos) instanceof MEPatternBufferProxyPartMachine proxy) proxy.setBuffer(getBlockPos());
        }
        refreshAllByProduct(); markAsChanged();
        return true;
    }

    private static FluidHandlerList createCatalystTanks() {
        return new FluidHandlerList(Stream.generate(() -> (IFluidHandler) new CustomFluidTank(16 * FluidType.BUCKET_VOLUME))
                .limit(9).toArray(IFluidHandler[]::new));
    }

    public CompoundTag cutToTag(CompoundTag target) {
        var registries = getLevel().registryAccess();
        var tag = new CompoundTag();
        tag.putString("name", customName);
        tag.putString("dimension", getLevel().dimension().location().toString());
        tag.putLong("bufferPos", getBlockPos().asLong());
        tag.putBoolean("keepByProduct", keepByProduct);
        tag.put("proxies", new LongArrayTag(getProxyPosList()));
        var patterns = new ListTag();
        // Snapshot all data before changing a slot; the portable cut carries its unconsumed inputs as well.
        for (int slot = 0; slot < maxPatternCount; slot++) {
            var pattern = patternInventory.getStackInSlot(slot);
            if (pattern.isEmpty()) continue;
            var data = new CompoundTag();
            data.putInt("sourceSlot", slot);
            data.put("pattern", pattern.save(registries));
            data.putByte("cacheCount", cacheRecipeCount[slot]);
            data.put("catalystItems", catalystItems[slot].serializeNBT(registries));
            data.put("catalystFluids", catalystFluids[slot].serializeNBT(registries));
            data.put("inputs", internalInventory[slot].serializeNBT(registries));
            patterns.add(data);
        }
        tag.put("patterns", patterns);
        tag.put("sharedCatalystInventory", sharedCatalystInventory.storage.serializeNBT(registries));
        tag.put("sharedCircuitInventory", sharedCircuitInventory.storage.serializeNBT(registries));
        var sharedTanks = new ListTag();
        for (var tank : sharedCatalystTank.getStorages()) sharedTanks.add(tank.serializeNBT(registries));
        tag.put("sharedCatalystTank", sharedTanks);
        for (Tag value : patterns) {
            int slot = ((CompoundTag) value).getInt("sourceSlot");
            internalInventory[slot].getItemInventory().clear(); internalInventory[slot].getFluidInventory().clear();
            internalInventory[slot].clearVirtualSupply();
            internalPatternInventory.setItemDirect(slot, ItemStack.EMPTY);
            for (int i = 0; i < catalystItems[slot].getSlots(); i++) catalystItems[slot].setStackInSlot(i, ItemStack.EMPTY);
            for (int i = 0; i < catalystFluids[slot].getTanks(); i++) catalystFluids[slot].setFluidInTank(i, FluidStack.EMPTY);
            reCalculateCatalystItemMap(slot); reCalculateCatalystFluidMap(slot);
            cacheRecipeCount[slot] = 1;
        }
        for (int i = 0; i < sharedCatalystInventory.getSlots(); i++) sharedCatalystInventory.setStackInSlot(i, ItemStack.EMPTY);
        for (int i = 0; i < sharedCatalystTank.getTanks(); i++) sharedCatalystTank.setFluidInTank(i, FluidStack.EMPTY);
        sharedCircuitInventory.setStackInSlot(0, ItemStack.EMPTY);
        releaseProxies();
        target.put("cut", tag); markAsChanged();
        return target;
    }

    @Override
    public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var builder = super.getPanelBuilder(data, sync, settings);
        var inherited = builder.leftConfigurators();
        var byproducts = SyncHandlers.bool(() -> keepByProduct, value -> {
            if (keepByProduct != value) { keepByProduct = value; refreshAllByProduct(); markAsChanged(); }
        }).allowC2S();
        var hidden = SyncHandlers.bool(() -> isHiddenTerminal, value -> {
            isHiddenTerminal = value; markAsChanged(); syncDataHolder.markClientSyncFieldDirty("isHiddenTerminal");
        }).allowC2S();
        sync.syncValue("patternKeepByProducts", byproducts);
        sync.syncValue("patternHiddenTerminal", hidden);
        var circuit = new PatternCircuitConfigurator(() -> embeddedCircuitConfig, this::setEmbeddedCircuitConfig,
                () -> skipExistingCircuitPatterns, value -> { skipExistingCircuitPatterns = value; markAsChanged(); },
                this::applyConfiguredCircuit, this::removeAllPatternCircuits);
        var circuitPanel = sync.syncedPanel("patternCircuitConfig", true, (manager, handler) -> circuit.createPanel(manager));
        var renamePanel = sync.syncedPanel("patternRename", true, (manager, handler) -> {
            var name = SyncHandlers.string(() -> customName, this::setCustomName).allowC2S();
            manager.syncValue("patternCustomName", name);
            return PopupPanel.createPopupPanel("patternRenamePanel", 150, 52)
                    .child(Text.lang("gtceu.gui.pattern_buffer.set_custom_name").asWidget().pos(4, 4))
                    .child(new TextFieldWidget().value(name).size(140, 18).pos(4, 26));
        });
        return builder.leftConfigurators(flow -> {
            inherited.accept(flow);
            flow.child(new ToggleButton().size(18).value(byproducts).overlay(Text.str("B"))
                            .tooltipDynamic(t -> t.addLine(Component.translatable("tooltip.gtlcore.disable_by_product"))))
                    .child(new ToggleButton().size(18).value(hidden).overlay(Text.str("V"))
                            .tooltipDynamic(t -> t.addLine(Component.translatable(isHiddenTerminal ? "gui.gtlcore.hidden_in_terminal" : "gui.gtlcore.visible_in_terminal"))))
                    .child(new ButtonWidget<>().size(18).overlay(Text.str("#"))
                            .onMousePressed((context, button) -> { if (button != 0) return false; circuitPanel.openPanel(); return true; }))
                    .child(new ButtonWidget<>().size(18).overlay(Text.str("✎"))
                            .onMousePressed((context, button) -> { if (button != 0) return false; renamePanel.openPanel(); return true; }));
        });
    }

    @Override
    public void buildMainUI(ParentWidget<?> parent, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var online = SyncHandlers.bool(this::isOnline, null);
        sync.syncValue("patternBufferOnline", online);
        var catalysts = new MEPatternCatalystUIManager(sync, catalystItems, catalystFluids, cacheRecipeCount,
                this::removeSlotFromGTRecipeCache);
        parent.child(Flow.col().coverChildren()
                .child(Text.dynamic(() -> Component.translatable(online.getBoolValue() ?
                        "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline")).asWidget().height(16))
                .child(buildPatternPagination(sync, catalysts)));
    }

    protected brachy.modularui.api.widget.IWidget buildPatternPagination(PanelSyncManager sync, MEPatternCatalystUIManager catalysts) {
        return org.gtlcore.gtlcore.integration.ae2.gui.MolecularPatternPagination.build(
                patternInventory, maxPatternCount, sync, this::onPatternChange, catalysts::toggleFor, slot -> cacheRecipe[slot]);
    }

    // ========================================
    // CIRCUIT HANDLING
    // ========================================

    private void setEmbeddedCircuitConfig(int embeddedCircuitConfig) {
        this.embeddedCircuitConfig = Math.max(1, Math.min(IntCircuitBehaviour.CIRCUIT_MAX, embeddedCircuitConfig));
        markAsChanged();
        syncDataHolder.markClientSyncFieldDirty("embeddedCircuitConfig");
    }

    private void applyConfiguredCircuit() {
        if (isRemote()) return;
        embedCircuitToPatterns(embeddedCircuitConfig, !skipExistingCircuitPatterns);
    }

    private void removeAllPatternCircuits() {
        if (isRemote()) return;
        for (int i = 0; i < internalPatternInventory.size(); i++) {
            ItemStack stack = internalPatternInventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            ItemStack after = realPatternHelper.removeCircuitFromPattern(stack, getLevel());
            if (!after.isEmpty() && !ItemStack.matches(stack, after)) {
                internalPatternInventory.setItemDirect(i, after);
            }
        }
    }

    private void embedCircuitToPatterns(int circuitConfig, boolean replaceExisting) {
        int safeCircuitConfig = Math.max(1, Math.min(IntCircuitBehaviour.CIRCUIT_MAX, circuitConfig));
        for (int i = 0; i < internalPatternInventory.size(); i++) {
            ItemStack stack = internalPatternInventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            ItemStack after = realPatternHelper.createPatternWithCircuit(stack, safeCircuitConfig, replaceExisting, getLevel());
            if (!after.isEmpty() && !ItemStack.matches(stack, after)) {
                internalPatternInventory.setItemDirect(i, after);
            }
        }
    }

    private IPatternDetails getRealPattern(int slot, ItemStack stack) {
        if (!stack.isEmpty()) {
            var internalSlot = internalInventory[slot];
            return realPatternHelper.processPatternWithCircuit(
                    stack, internalSlot.getCacheManager()::setCircuitCache, getLevel(), keepByProduct);
        }
        return null;
    }

    // ========================================
    // AE2 CRAFTING
    // ========================================

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return patternSlotMap.keySet().stream().toList();
    }

    /** Bounded, read-only context for orders waiting for machine output. */
    public String gtlcore$graphDiagnostic(IPatternDetails pattern) {
        Integer slot = getSlotIndexForPattern(pattern);
        var controllers = new java.util.LinkedHashSet<>(getControllers());
        for (var proxy : getProxies()) {
            controllers.addAll(proxy.getControllers());
            if (controllers.size() >= 8) break;
        }
        String state = controllers.isEmpty() ? "NO_FORMED_CONTROLLER" : controllers.stream().limit(8).map(controller -> {
            var machine = controller.self();
            return machine.getDefinition().getId() + "@" + machine.getBlockPos() + ":" +
                    (machine instanceof com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine working ?
                            working.getRecipeLogic().getStatus() : "connected");
        }).toList().toString();
        String inputs = "";
        if (slot != null && slot >= 0 && slot < internalInventory.length) {
            InternalSlot buffer = internalInventory[slot];
            inputs = "; items=" + buffer.getItemInventory().object2LongEntrySet().stream().limit(4).toList() +
                    "; fluids=" + buffer.getFluidInventory().object2LongEntrySet().stream().limit(4).toList();
        }
        return "buffer=" + getBlockPos() + "; online=" + getMainNode().isActive() + "; slot=" + slot + "; controllers=" + state + inputs;
    }

    // ========================================
    // PATTERN CONTAINER IMPLEMENTATION
    // ========================================

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return internalPatternInventory;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        var controllers = getControllers();

        if (!controllers.isEmpty()) {
            var controller = controllers.first();
            MultiblockMachineDefinition controllerDefinition = controller.getDefinition();

            if (!customName.isEmpty()) {
                return new PatternContainerGroup(
                        AEItemKey.of(controllerDefinition.asStack()),
                        Component.literal(customName),
                        Collections.emptyList());
            } else {
                ItemStack circuitStack = sharedCircuitInventory.storage.getStackInSlot(0);
                int circuitConfiguration = circuitStack.isEmpty() ? -1 :
                        IntCircuitBehaviour.getCircuitConfiguration(circuitStack);

                Component groupName = circuitConfiguration != -1 ?
                        Component.translatable(controllerDefinition.getDescriptionId())
                                .append(" - " + circuitConfiguration) :
                        Component.translatable(controllerDefinition.getDescriptionId());

                return new PatternContainerGroup(
                        AEItemKey.of(controllerDefinition.asStack()), groupName, Collections.emptyList());
            }
        } else {
            if (!customName.isEmpty()) {
                return new PatternContainerGroup(
                        AEItemKey.of(getDefinition().asStack()),
                        Component.literal(customName),
                        Collections.emptyList());
            } else {
                return new PatternContainerGroup(
                        AEItemKey.of(getDefinition().asStack()),
                        getDefinition().asStack().getHoverName(),
                        Collections.emptyList());
            }
        }
    }

    // ========================================
    // IMEPatternPartMachine
    // ========================================

    @Override
    protected @NotNull MEPatternTrait createMETrait() {
        return new MEPatternTrait();
    }

    @Override
    public Pair<IMERecipeHandlerTrait<net.neoforged.neoforge.common.crafting.SizedIngredient, ItemStack>, IMERecipeHandlerTrait<net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient, net.neoforged.neoforge.fluids.FluidStack>> getMERecipeHandlerTraits() {
        return Pair.of(recipeHandler.meItemHandler, recipeHandler.meFluidHandler);
    }

    protected class PatternBufferInternalSlot extends InternalSlot {

        @Getter
        private final Object2LongMap<AEItemKey> itemCatalystInventory = new Object2LongArrayMap<>();

        @Getter
        private final Object2LongMap<AEFluidKey> fluidCatalystInventory = new Object2LongArrayMap<>();

        public PatternBufferInternalSlot(int slotIndex) {
            super(slotIndex);
            itemCatalystInventory.defaultReturnValue(0);
            fluidCatalystInventory.defaultReturnValue(0);
        }

        @Override
        protected boolean hasItemCatalystInventory() {
            return !itemCatalystInventory.isEmpty();
        }

        @Override
        protected boolean hasFluidCatalystInventory() {
            return !fluidCatalystInventory.isEmpty();
        }

        @Override
        protected void appendLimitItemCatalystInputs(ObjectList<ItemStack> limitInput) {
            for (var entry : Object2LongMaps.fastIterable(itemCatalystInventory)) {
                limitInput.add(entry.getKey().toStack((int) Math.min(entry.getLongValue(), Integer.MAX_VALUE)));
            }
        }

        @Override
        protected void appendLimitFluidCatalystInputs(ObjectList<FluidStack> limitInput) {
            for (var entry : Object2LongMaps.fastIterable(fluidCatalystInventory)) {
                limitInput.add(entry.getKey().toStack((int) Math.min(Integer.MAX_VALUE, entry.getLongValue())));
            }
        }

        @Override
        public boolean testCatalystItemInternal(GTRecipe recipe) {
            for (var content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
                if (content.chance() <= 0) continue;
                var ingredient = LongIngredient.matchingIngredient((SizedIngredient) content.content());
                for (ItemStack item : ingredient.getItems()) {
                    AEItemKey key = AEItemKey.of(item);
                    if (itemCatalystInventory.containsKey(key)) return false;
                }
            }
            return true;
        }

        @Override
        public boolean testCatalystFluidInternal(GTRecipe recipe) {
            for (var content : recipe.getInputContents(FluidRecipeCapability.CAP)) {
                if (content.chance() <= 0) continue;
                var fluidIngredient = LongFluidIngredient.matchingIngredient((SizedFluidIngredient) content.content());
                for (FluidStack stack : fluidIngredient.getStacks()) {
                    AEFluidKey key = AEFluidKey.of(stack);
                    if (fluidCatalystInventory.containsKey(key)) return false;
                }
            }
            return true;
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider registries) {
            CompoundTag tag = super.serializeNBT(registries);

            ListTag itemCatalystTag = AEUtils.createListTag(key -> key.toTag(registries), itemCatalystInventory);
            if (!itemCatalystTag.isEmpty()) tag.put("catalystInventory", itemCatalystTag);

            ListTag fluidCatalystTag = AEUtils.createListTag(key -> key.toTag(registries), fluidCatalystInventory);
            if (!fluidCatalystTag.isEmpty()) tag.put("catalystFluidInventory", fluidCatalystTag);

            return tag;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
            super.deserializeNBT(registries, tag);

            itemCatalystInventory.clear();
            ListTag catalystItems = tag.getList("catalystInventory", Tag.TAG_COMPOUND);
            AEUtils.loadInventory(catalystItems, key -> AEItemKey.fromTag(registries, key), itemCatalystInventory);

            fluidCatalystInventory.clear();
            ListTag catalystFluids = tag.getList("catalystFluidInventory", Tag.TAG_COMPOUND);
            AEUtils.loadInventory(catalystFluids, key -> AEFluidKey.fromTag(registries, key), fluidCatalystInventory);
        }
    }

    protected class Ticker implements IGridTickable {

        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(MEExtendedOutputPartMachineBase.MIN_FREQUENCY, MEExtendedOutputPartMachineBase.MAX_FREQUENCY, false);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!getMainNode().isActive()) {
                isSleeping = true;
                return TickRateModulation.SLEEP;
            }

            if (buffer.isEmpty()) {
                if (ticksSinceLastCall >= MEExtendedOutputPartMachineBase.MAX_FREQUENCY) {
                    isSleeping = true;
                    return TickRateModulation.SLEEP;
                } else return TickRateModulation.SLOWER;
            } else {
                boolean changed = AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource);
                if (changed) markAsChanged();
                return changed ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
            }
        }
    }

    protected class MEPatternTrait extends MEIOTrait implements IMEPatternTrait {

        public MEPatternTrait() {
            super();
        }

        @Override
        public MEPatternBufferPartMachine getMachine() {
            return (MEPatternBufferPartMachine) super.getMachine();
        }

        @Override
        public @NotNull ObjectSet<@NotNull GTRecipe> getCachedGTRecipe() {
            ObjectSet<GTRecipe> recipes = new ObjectOpenHashSet<>();
            for (var it = Int2ReferenceMaps.fastIterator(recipeMultipleCacheMap); it.hasNext();) {
                var entry = it.next();
                var recipeSet = entry.getValue();
                int slot = entry.getIntKey();
                if (recipeSet.isEmpty()) it.remove();
                else if (cacheRecipe[slot] && internalInventory[slot].isActive()) recipes.addAll(recipeSet);
            }
            return recipes;
        }

        @Override
        public void setSlotCacheRecipe(int index, GTRecipe recipe) {
            if (index >= 0 && index < maxPatternCount && recipe != null && recipe.recipeType != GTRecipeTypes.DUMMY_RECIPES.get()) {
                var set = recipeMultipleCacheMap.computeIfAbsent(index, integer -> new ObjectArraySet<>());
                if (set.add(recipe)) {
                    cacheRecipe[index] = set.size() >= cacheRecipeCount[index];
                    syncDataHolder.markClientSyncFieldDirty("cacheRecipe");
                    markAsChanged();
                    reCalculatePatternSlotMap();
                }
            }
        }

        @Override
        public @NotNull Int2ReferenceMap<ObjectSet<@NotNull GTRecipe>> getSlot2RecipesCache() {
            return recipeMultipleCacheMap;
        }

        @Override
        public void setOnPatternChange(IntConsumer removeMapOnSlot) {
            removeSlotFromMap = removeMapOnSlot;
        }

        @Override
        public boolean hasCacheInSlot(int slot) {
            return slot >= 0 && slot < cacheRecipe.length && cacheRecipe[slot];
        }
    }
}
