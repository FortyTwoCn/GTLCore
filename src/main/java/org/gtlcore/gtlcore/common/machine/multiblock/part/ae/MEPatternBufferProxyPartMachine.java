package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.machine.trait.*;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternPartMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternTrait;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.common.data.item.GTDataComponents;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import com.gregtechceu.gtceu.common.data.GTItems;

import com.gregtechceu.gtceu.utils.ISubscription;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MEPatternBufferProxyPartMachine extends MultiblockPartMachine implements IMEPatternPartMachine, IMuiMachine, IDataStickInteractable {


    protected final ISubscription[] handlerSubscriptions = new ISubscription[2];

    protected final IMEPatternTrait meTrait;
    protected final MEPatternBufferProxyRecipeHandler<SizedIngredient, ItemStack> itemProxyHandler;
    protected final MEPatternBufferProxyRecipeHandler<SizedFluidIngredient, FluidStack> fluidProxyHandler;

    protected IntConsumer removeSlotFromMap = i -> {};

    @SaveField
    @Getter
    @SyncToClient
    private BlockPos bufferPos;
    private @Nullable MEPatternBufferPartMachineBase buffer = null;
    private @Nullable IMEPatternTrait bufferTrait = null;
    private boolean bufferResolved = false;

    public MEPatternBufferProxyPartMachine(BlockEntityCreationInfo holder) {
        super(holder);
        this.itemProxyHandler = attachTrait(new MEPatternBufferProxyRecipeHandler<>(ItemRecipeCapability.CAP));
        this.fluidProxyHandler = attachTrait(new MEPatternBufferProxyRecipeHandler<>(FluidRecipeCapability.CAP));
        this.meTrait = attachTrait(new MEPatternProxyTrait());
    }

    @Override
    public MetaMachine self() {
        var buffer = getBuffer();
        return buffer != null ? buffer.self() : super.self();
    }

    public void setBuffer(@Nullable BlockPos pos) {
        if (pos != null) pos = pos.immutable();
        if (java.util.Objects.equals(bufferPos, pos) && buffer != null && !buffer.isRemoved()) return;
        releaseRuntimeBinding(true);
        bufferPos = pos;
        bufferResolved = pos == null;
        var level = getLevel();
        if (level != null && pos != null && level.hasChunkAt(pos)) {
            bufferResolved = true;
            if (MetaMachine.getMachine(level, pos) instanceof MEPatternBufferPartMachineBase machine && !machine.isRemoved()) {
                buffer = machine;
                machine.addProxy(this);
                if (!isRemote()) {
                    var pair = machine.getMERecipeHandlerTraits();
                    itemProxyHandler.setHandler(pair.left());
                    fluidProxyHandler.setHandler(pair.right());
                    bufferTrait = machine.getMETrait();
                    handlerSubscriptions[0] = pair.left().addChangedListener(itemProxyHandler::notifyListeners);
                    handlerSubscriptions[1] = pair.right().addChangedListener(fluidProxyHandler::notifyListeners);
                }
            } else bufferPos = null;
        }
        markAsChanged();
        syncDataHolder.markClientSyncFieldDirty("bufferPos");
        if (!isRemote()) updateIO();
    }

    @Nullable
    public MEPatternBufferPartMachineBase getBuffer() {
        if (buffer != null && buffer.isRemoved()) {
            releaseRuntimeBinding(false);
            bufferResolved = false;
        }
        if (!bufferResolved) setBuffer(bufferPos);
        return buffer;
    }

    /** Chunk unload releases listeners, while retaining the persisted target position. */
    protected void releaseRuntimeBinding(boolean removeMembership) {
        for (int i = 0; i < handlerSubscriptions.length; i++) {
            if (handlerSubscriptions[i] != null) handlerSubscriptions[i].unsubscribe();
            handlerSubscriptions[i] = null;
        }
        var old = buffer;
        buffer = null;
        bufferTrait = null;
        itemProxyHandler.setHandler(null);
        fluidProxyHandler.setHandler(null);
        if (removeMembership && old != null) old.removeProxy(this);
    }

    protected void updateIO() {
        for (var controller : this.getControllers()) {
            if (controller instanceof IRecipeCapabilityMachine machine) {
                machine.upDate();
            }
        }
        itemProxyHandler.notifyListeners();
        fluidProxyHandler.notifyListeners();
    }

    // ========================================
    // GUI
    // ========================================

    @Override
    public InteractionResult onDataStickUse(Player player, ItemStack stack) {
        if (stack.has(GTDataComponents.RESEARCH_ITEM)) return InteractionResult.PASS;
        var pos = stack.get(GTDataComponents.DATA_COPY_POS);
        if (pos == null) return InteractionResult.PASS;
        if (!isRemote()) {
            setBuffer(pos);
            player.sendSystemMessage(Component.translatable("gtceu.machine.me.import_paste_settings"));
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return getBuffer() != null;
    }

    @Override
    public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var target = getBuffer();
        if (target == null) throw new IllegalStateException("Pattern buffer proxy is not bound");
        return target.getPanelBuilder(data, sync, settings);
    }

    @Override
    public void buildMainUI(ParentWidget<?> parent, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var target = getBuffer();
        if (target == null) throw new IllegalStateException("Pattern buffer proxy is not bound");
        target.buildMainUI(parent, data, sync, settings);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        bufferResolved = false;
        if (!isRemote()) scheduleForNextServerTick(() -> { if (!isRemoved()) getBuffer(); });
    }

    @Override
    public void onUnload() {
        releaseRuntimeBinding(false);
        bufferResolved = false;
        super.onUnload();
    }

    @Override
    public void onMachineDestroyed() {
        releaseRuntimeBinding(true);
        bufferPos = null;
        super.onMachineDestroyed();
    }

    // ========================================
    // IMEPatternPartMachine
    // ========================================

    @Override
    public Pair<IMERecipeHandlerTrait<SizedIngredient, ItemStack>, IMERecipeHandlerTrait<SizedFluidIngredient, FluidStack>> getMERecipeHandlerTraits() {
        getBuffer();
        return Pair.of(itemProxyHandler, fluidProxyHandler);
    }

    @Override
    public @NotNull IMEPatternTrait getMETrait() {
        return this.meTrait;
    }

    protected class MEPatternProxyTrait extends MachineTrait implements IMEPatternTrait {


        public MEPatternProxyTrait() {}

        @Override
        public MEPatternBufferProxyPartMachine getMachine() {
            return (MEPatternBufferProxyPartMachine) super.getMachine();
        }

        @Override
        public @NotNull ObjectSet<@NotNull GTRecipe> getCachedGTRecipe() {
            if (bufferTrait == null) return ObjectSets.emptySet();
            return bufferTrait.getCachedGTRecipe();
        }

        @Override
        public void setSlotCacheRecipe(int index, GTRecipe recipe) {
            if (bufferTrait != null) {
                bufferTrait.setSlotCacheRecipe(index, recipe);
            }
        }

        @Override
        public @NotNull Int2ReferenceMap<ObjectSet<@NotNull GTRecipe>> getSlot2RecipesCache() {
            return bufferTrait == null ? Int2ReferenceMaps.emptyMap() : bufferTrait.getSlot2RecipesCache();
        }

        @Override
        public void setOnPatternChange(IntConsumer removeMapOnSlot) {
            removeSlotFromMap = removeMapOnSlot;
        }

        @Override
        public boolean hasCacheInSlot(int slot) {
            if (bufferTrait == null) return false;
            return bufferTrait.hasCacheInSlot(slot);
        }

        @Override
        public void notifySelfIO() {
            if (bufferTrait != null) {
                bufferTrait.notifySelfIO();
            }
        }

        @Override
        public IO getIO() {
            if (bufferTrait != null) {
                return bufferTrait.getIO();
            }
            return IO.NONE;
        }

    }
}
