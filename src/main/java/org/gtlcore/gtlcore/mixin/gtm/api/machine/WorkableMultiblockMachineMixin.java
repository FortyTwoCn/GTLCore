package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import org.gtlcore.gtlcore.api.machine.trait.*;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEFilterIOPartMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternPartMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.utils.datastructure.FirstFlagRecipePartSet;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IDataAccessHatch;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.machine.trait.recipe.IRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerGroup;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerGroupColor;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputHatchPartMachine;
import com.hepdd.gtmthings.common.block.machine.multiblock.part.appeng.MEOutputPartMachine;
import com.gregtechceu.gtceu.utils.ISubscription;
import net.minecraft.server.level.ServerLevel;
import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

/** Core input/cache policy over native part handler lists. Native part lifecycle and working callbacks remain active. */
@SuppressWarnings({ "AddedMixinMembersNamePattern", "MissingUnique" })
@Mixin(WorkableMultiblockMachine.class)
public abstract class WorkableMultiblockMachineMixin extends MultiblockControllerMachine
        implements IRecipeCapabilityMachine, IRecipeLogicMachine {
    @Shadow(remap = false) @Final public RecipeLogic recipeLogic;

    public WorkableMultiblockMachineMixin(BlockEntityCreationInfo info) { super(info); }

    @Inject(method = "formStructure", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$formed(String substructure, CallbackInfo ci) {
        if (getLevel() instanceof ServerLevel) upDate();
    }
    @Inject(method = "invalidateStructure(Ljava/lang/String;)V", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$invalidated(String substructure, CallbackInfo ci) {
        gtlcore$clear();
        RecipeResult.of(this, null);
        RecipeResult.ofWorking(this, null);
    }
    @Inject(method = { "onPartUnload", "onUnload" }, at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$unloaded(CallbackInfo ci) { gtlcore$clear(); }

    @SaveField @SyncToClient private boolean isDistinct;
    public boolean isDistinct() { return isDistinct; }
    public void setDistinct(boolean distinct) {
        if (isDistinct == distinct) return;
        isDistinct = distinct;
        getSyncDataHolder().markClientSyncFieldDirty("isDistinct");
    }
    private boolean meOutPutBus, meOutPutHatch, meOutPutDual, meOutPutWithFilter;
    private boolean meItemOutPutWithFilter, meFluidOutPutWithFilter, itemOutPutAlwaysMatch, fluidOutPutAlwaysMatch;
    private @Nullable MaintenanceHatchPartMachine maintenanceMachine;
    private @Nullable IDataAccessHatch dataAccessHatch;
    private @Nullable RecipeHandlePart sharedInputRecipeHandlePart;
    private final Map<IO, List<RecipeHandlePart>> normalCapabilities = new EnumMap<>(IO.class);
    private final ObjectList<MEPatternRecipeHandlePart> mePatternRecipeHandleParts = new ObjectArrayList<>();
    private final ObjectList<MEIORecipeHandlePart<?>> meOutputRecipeHandleParts = new ObjectArrayList<>();
    private final Object2ObjectMap<GTRecipe, FirstFlagRecipePartSet> recipeHandleMap = new Object2ObjectOpenHashMap<>();
    // Native GT handler subscriptions and custom LDLib ME-buffer subscriptions have different interface types.
    @Unique private final List<ISubscription> gtlcore$meSubscriptions = new ArrayList<>();

    @Override
    public boolean isRecipeOutputAlwaysMatch(GTRecipe recipe) {
        if (meOutPutWithFilter) return false;
        else if (meOutPutDual) return true;
        else if (meOutPutBus || recipe.getOutputContents(ItemRecipeCapability.CAP).isEmpty())
            return meOutPutHatch || recipe.getOutputContents(FluidRecipeCapability.CAP).isEmpty();
        else return false;
    }

    @Override
    public boolean itemOutPutAlwaysMatch() {
        return itemOutPutAlwaysMatch;
    }

    @Override
    public boolean fluidOutPutAlwaysMatch() {
        return fluidOutPutAlwaysMatch;
    }

    // ========================================
    // Recipe -> HandlePart Cache
    // ========================================

    @Override
    public @Nullable IRecipeHandlePart getActiveRecipeHandle(GTRecipe recipe) {
        final var handlers = recipeHandleMap.get(recipe);
        return handlers != null ? handlers.getActive() : null;
    }

    @Override
    public @NotNull Iterator<@NotNull IRecipeHandlePart> getAllCachedRecipeHandlesIter(GTRecipe recipe) {
        final var handlers = recipeHandleMap.get(recipe);
        return handlers != null ? handlers.getReverseIterator() : ObjectIterators.emptyIterator();
    }

    @Override
    public @NotNull ReferenceSet<@NotNull IRecipeHandlePart> getAllCachedRecipeHandles(GTRecipe recipe) {
        final var handlers = recipeHandleMap.get(recipe);
        return handlers != null ? handlers.getAll() : ReferenceSets.emptySet();
    }

    @Override
    public void tryAddAndActiveMERhp(MEPatternRecipeHandlePart part, GTRecipe recipe, int slot) {
        part.setLastRecipe2Slot(recipe, slot);
        tryAddAndActiveRhp(recipe, part);
    }

    @Override
    public void tryAddAndActiveRhp(GTRecipe recipe, IRecipeHandlePart part) {
        recipeHandleMap.computeIfAbsent(recipe, k -> new FirstFlagRecipePartSet()).addOrSetActive(part);
    }

    @Override
    public void sortMEOutput() {
        if (!meOutputRecipeHandleParts.isEmpty()) {
            meOutputRecipeHandleParts.sort(MEPatternRecipeHandlePart.COMPARATOR.reversed());

            boolean allHaveItemFilter = true;
            boolean allHaveFluidFilter = true;
            for (MEIORecipeHandlePart<?> meOutputRecipeHandlePart : meOutputRecipeHandleParts) {
                if (!meOutputRecipeHandlePart.hasItemFilter()) {
                    allHaveItemFilter = false;
                }
                if (!meOutputRecipeHandlePart.hasFluidFilter()) {
                    allHaveFluidFilter = false;
                }

                if (!allHaveItemFilter && !allHaveFluidFilter) {
                    break;
                }
            }

            meItemOutPutWithFilter = allHaveItemFilter;
            meFluidOutPutWithFilter = allHaveFluidFilter;
            meOutPutWithFilter = meItemOutPutWithFilter || meFluidOutPutWithFilter;
            itemOutPutAlwaysMatch = !meItemOutPutWithFilter && (meOutPutBus || meOutPutDual);
            fluidOutPutAlwaysMatch = !meFluidOutPutWithFilter && (meOutPutHatch || meOutPutDual);
        }
    }

    @Unique private final Map<RecipeHandlerList, RecipeHandlerGroup> gtlcore$nativeGroups = new IdentityHashMap<>();
    @Unique private boolean gtlcore$nativeBusDistinct, gtlcore$nativeColoredRoutes;

    @Override
    public boolean usesCoreRecipeRouting() {
        // A native bus can change its distinct/color setting without reforming the structure.
        if (isFormed() && gtlcore$nativeGroups.entrySet().stream().anyMatch(entry -> !entry.getValue().equals(entry.getKey().getGroup()))) upDate();
        // Keep native dyed/bypass groups for ordinary GT controllers. Core's global distinct mode
        // and custom pattern buffers require its own selected-bus/cache policy.
        return !emptyHandlePart() && (isDistinct || !mePatternRecipeHandleParts.isEmpty() || !meOutputRecipeHandleParts.isEmpty() ||
                (gtlcore$nativeBusDistinct && !gtlcore$nativeColoredRoutes));
    }

    @Override
    public boolean emptyRecipeHandlePart() {
        return normalCapabilities.isEmpty() && mePatternRecipeHandleParts.isEmpty() && sharedInputRecipeHandlePart == null;
    }

    @Override
    public boolean emptyHandlePart() {
        return normalCapabilities.isEmpty() && mePatternRecipeHandleParts.isEmpty() && meOutputRecipeHandleParts.isEmpty() && sharedInputRecipeHandlePart == null;
    }

    @Override
    public boolean hasCapabilityProxies() {
        // Native RecipeHelper checks this before it invokes RecipeRunner. Pattern proxies
        // have Core's slot-aware handlers rather than native flat inventory handlers.
        return !getCapabilitiesProxy().isEmpty() || !emptyHandlePart();
    }

    @Override
    @Nullable
    public RecipeHandlePart getSharedRecipeHandlePart() {
        return sharedInputRecipeHandlePart;
    }

    @Override
    public List<MEPatternRecipeHandlePart> getMEPatternRecipeHandleParts() {
        return mePatternRecipeHandleParts;
    }

    @Override
    public List<MEIORecipeHandlePart<?>> getMEOutputRecipeHandleParts() {
        return meOutputRecipeHandleParts;
    }

    @Override
    public @NotNull List<RecipeHandlePart> getNormalRecipeHandlePart(IO io) {
        return normalCapabilities.getOrDefault(io, Collections.emptyList());
    }

    @Override
    public void upDate() {
        gtlcore$clear();
        if (!isFormed()) return;
        var shared = new ObjectArrayList<IRecipeHandler<?>>();
        for (var part : getParts()) {
            if (part instanceof MaintenanceHatchPartMachine maintenance) maintenanceMachine = maintenance;
            if (part instanceof IDataAccessHatch data) dataAccessHatch = data;
            if (part instanceof IMEFilterIOPartMachine me) gtlcore$handleMEPart(me);
            else gtlcore$handleNormalPart(part, shared);
        }
        // Native controller traits (for example computation or a ghost circuit) also participate in IO.
        for (var trait : getTraitHolder().getTraitsByInterface(IRecipeHandlerTrait.class)) {
            if (trait.getHandlerIO().support(IO.IN)) shared.add(trait);
            if (trait.getHandlerIO().support(IO.OUT)) {
                normalCapabilities.computeIfAbsent(IO.OUT, $ -> new ObjectArrayList<>()).add(RecipeHandlePart.of(IO.OUT, List.of(trait)));
            }
        }
        if (!shared.isEmpty()) {
            sharedInputRecipeHandlePart = RecipeHandlePart.of(IO.IN, shared);
            if (isDistinct) {
                normalCapabilities.computeIfAbsent(IO.IN, $ -> new ObjectArrayList<>()).add(sharedInputRecipeHandlePart);
            } else {
                for (var part : getNormalRecipeHandlePart(IO.IN)) {
                    part.addSharedFluidHandlers(sharedInputRecipeHandlePart.getCapability(FluidRecipeCapability.CAP));
                }
            }
        }
        sortMEOutput();
        recipeLogic.updateTickSubscription();
    }

    @Override public @Nullable MaintenanceHatchPartMachine getMaintenanceMachine() { return maintenanceMachine; }
    @Override public @Nullable IDataAccessHatch getDataAccessHatch() { return dataAccessHatch; }

    @Unique
    private void gtlcore$handleMEPart(IMEFilterIOPartMachine machine) {
        var pair = machine.getMERecipeHandlerTraits();
        gtlcore$meSubscriptions.add(pair.left().addBufferChangedListener(recipeLogic::updateTickSubscription));
        gtlcore$meSubscriptions.add(pair.right().addBufferChangedListener(recipeLogic::updateTickSubscription));
        IO io = machine.getMETrait().getIO();
        if (machine instanceof IMEPatternPartMachine pattern) {
            var part = MEPatternRecipeHandlePart.of(pattern);
            part.restoreMachineCache(this::tryAddAndActiveRhp);
            mePatternRecipeHandleParts.add(part);
            if (io.support(IO.OUT)) {
                meOutputRecipeHandleParts.add(part);
                gtlcore$setAllMEOutputFlags();
            }
        } else if (io.support(IO.OUT)) {
            meOutputRecipeHandleParts.add(MEIORecipeHandlePart.of(machine));
            gtlcore$setAllMEOutputFlags();
        }
    }

    @Unique
    private void gtlcore$handleNormalPart(MultiblockPartMachine part, List<IRecipeHandler<?>> shared) {
        if (part instanceof MEOutputPartMachine) gtlcore$setAllMEOutputFlags();
        else if (part instanceof MEOutputBusPartMachine) meOutPutBus = true;
        else if (part instanceof MEOutputHatchPartMachine) meOutPutHatch = true;
        var inputs = new ObjectArrayList<IRecipeHandler<?>>();
        var outputs = new ObjectArrayList<IRecipeHandler<?>>();
        var fluidInputs = new ObjectArrayList<IRecipeHandler<?>>();
        boolean distinctPart = false;
        for (var list : part.getRecipeHandlers()) {
            gtlcore$nativeGroups.put(list, list.getGroup());
            if (list.getHandlerIO().support(IO.IN)) {
                gtlcore$nativeBusDistinct |= list.isDistinct();
                gtlcore$nativeColoredRoutes |= list.getGroup() instanceof RecipeHandlerGroupColor color && color.color() != -1;
            }
            distinctPart |= list.isDistinct();
            for (var handler : list.getHandlersFlat()) {
                if (list.getHandlerIO().support(IO.OUT)) outputs.add(handler);
                if (!list.getHandlerIO().support(IO.IN) || isRecipeInputHandlerExcluded(handler)) continue;
                if (handler.getCapability() == FluidRecipeCapability.CAP) fluidInputs.add(handler);
                else inputs.add(handler);
            }
        }
        if (!outputs.isEmpty()) {
            normalCapabilities.computeIfAbsent(IO.OUT, $ -> new ObjectArrayList<>()).add(RecipeHandlePart.of(IO.OUT, outputs));
        }
        if (isDistinct) {
            inputs.addAll(fluidInputs);
            if (!inputs.isEmpty()) normalCapabilities.computeIfAbsent(IO.IN, $ -> new ObjectArrayList<>()).add(RecipeHandlePart.of(IO.IN, inputs));
        } else if (distinctPart) {
            if (!inputs.isEmpty()) normalCapabilities.computeIfAbsent(IO.IN, $ -> new ObjectArrayList<>()).add(RecipeHandlePart.of(IO.IN, inputs));
            shared.addAll(fluidInputs);
        } else {
            shared.addAll(inputs);
            shared.addAll(fluidInputs);
        }
    }

    @Unique private void gtlcore$setAllMEOutputFlags() {
        meOutPutBus = meOutPutHatch = meOutPutDual = true;
    }
    @Unique private void gtlcore$clear() {
        gtlcore$meSubscriptions.forEach(ISubscription::unsubscribe);
        gtlcore$meSubscriptions.clear();
        meOutPutBus = meOutPutHatch = meOutPutDual = meOutPutWithFilter = false;
        meItemOutPutWithFilter = meFluidOutPutWithFilter = itemOutPutAlwaysMatch = fluidOutPutAlwaysMatch = false;
        maintenanceMachine = null;
        dataAccessHatch = null;
        sharedInputRecipeHandlePart = null;
        normalCapabilities.clear();
        gtlcore$nativeGroups.clear();
        gtlcore$nativeBusDistinct = gtlcore$nativeColoredRoutes = false;
        mePatternRecipeHandleParts.clear();
        meOutputRecipeHandleParts.clear();
        recipeHandleMap.clear();
    }
}
