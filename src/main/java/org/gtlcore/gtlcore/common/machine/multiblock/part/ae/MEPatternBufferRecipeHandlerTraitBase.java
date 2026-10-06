package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.integration.ae2.AEOutputIngredients;
import org.gtlcore.gtlcore.integration.ae2.AEOutputBuffer;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.function.Function;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Shared ME item/fluid recipe handling for pattern buffer variants.
 */
public abstract class MEPatternBufferRecipeHandlerTraitBase extends MachineTrait {

    @Getter
    protected MEItemInputHandlerBase meItemHandler;

    @Getter
    protected MEFluidHandlerBase meFluidHandler;

    private final IO handlerIO;

    public MEPatternBufferRecipeHandlerTraitBase(MEPatternBufferPartMachineBase ioBuffer, IO io) {
        handlerIO = io;
    }

    @Override
    public void onTraitAttached() {
        super.onTraitAttached();
        meItemHandler = getMachine().attachTrait(createMEItemHandler(handlerIO));
        meFluidHandler = getMachine().attachTrait(createMEFluidHandler(handlerIO));
    }

    public void notifyInputListeners() {
        meItemHandler.notifyListeners();
        meFluidHandler.notifyListeners();
    }

    protected abstract MEItemInputHandlerBase createMEItemHandler(IO io);

    protected abstract MEFluidHandlerBase createMEFluidHandler(IO io);

    @Override
    public MEPatternBufferPartMachineBase getMachine() {
        return (MEPatternBufferPartMachineBase) super.getMachine();
    }

    protected abstract static class MEItemInputHandlerBase extends NotifiableMERecipeHandlerTrait<SizedIngredient, ItemStack> {

        @Getter
        private final IO io;

        @Getter
        @Setter
        private Object2LongMap<SizedIngredient> preparedMEHandleContents = new Object2LongOpenHashMap<>();

        @Setter
        private int preparedCircuitConfig = -1;

        public MEItemInputHandlerBase(MEPatternBufferPartMachineBase machine, IO io) {
            super();
            this.io = io;
        }

        public MEPatternBufferPartMachineBase getMachine() {
            return (MEPatternBufferPartMachineBase) super.getMachine();
        }

        @Override
        public RecipeCapability<SizedIngredient> getCapability() {
            return ItemRecipeCapability.CAP;
        }

        @Override
        public int[] getActiveSlots() {
            return getMachine().getActiveSlots();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Int2ObjectMap<List<ItemStack>> getActiveAndUnCachedSlotsLimitContentsMap() {
            var map = new Int2ObjectArrayMap<List<ItemStack>>();
            var machine = getMachine();
            var shared = (List<ItemStack>) (Object) machine.getSharedCatalystInventory().getContents();
            for (int slot : machine.getActiveAndUnCachedSlots()) {
                var inputs = machine.getInternalSlot(slot).getLimitItemStackInput();

                ItemStack circuitForRecipe = machine.getCircuitForRecipe(slot);
                if (!circuitForRecipe.isEmpty()) {
                    inputs.add(circuitForRecipe);
                }

                inputs.addAll(shared);
                map.put(slot, inputs);
            }
            return map;
        }

        @Override
        public Object2LongMap<ItemStack> getStackMapFromFirstAvailableSlot(IntCollection slots) {
            final var inventory = getMachine().getFirstActiveInternalSlot(slots);

            if (inventory != null) {
                Object2LongOpenHashMap<ItemStack> map = new Object2LongOpenHashMap<>();
                for (var entry : Object2LongMaps.fastIterable(inventory.getItemStackInputMap())) {
                    map.addTo(entry.getKey(), entry.getLongValue());
                }
                return map;
            }

            return Object2LongMaps.emptyMap();
        }

        @Override
        public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<SizedIngredient> left, boolean simulate, int trySlot) {
            var internalSlot = getMachine().getInternalSlotOrNull(trySlot);
            if (internalSlot != null && internalSlot.isItemActive(simulate)) {
                Object2LongMap<SizedIngredient> remaining = left;
                if (simulate) {
                    if (!internalSlot.testCatalystItemInternal(recipe)) return false;
                    if (!internalSlot.testVirtualItemInternal(recipe)) return false;
                    // Matching only. On the real pass these demands are already gone -- RecipeRunner drops chance == 0
                    // contents -- so there is nothing to strip and nothing this handler could avoid paying for.
                    remaining = internalSlot.stripVirtuallySuppliedItems(left);
                }
                return internalSlot.handleItemInternal(remaining, preparedCircuitConfig, simulate);
            } else return left.isEmpty() && preparedCircuitConfig < 0;
        }

        @Override
        public void prepareMEHandleContents(GTRecipe recipe, List<SizedIngredient> left, boolean simulate) {
            preparedCircuitConfig = -1;
            if (simulate) {
                getMachine().getSharedCircuitInventory().handleRecipeInner(IO.IN, recipe, left, true);

                getMachine().getSharedCatalystInventory().handleRecipeInner(IO.IN, recipe, left, true);

                // Circuit ingredients are captured separately from the prepared item map.
                setPreparedMEHandleContents(AEUtils.ingredientsMapWithOutCircuit(left, this::setPreparedCircuitConfig));
            } else {
                setPreparedMEHandleContents(AEUtils.ingredientsMap(left));
            }
        }

        @Override
        public List<SizedIngredient> meHandleRecipeOutput(List<?> contents, boolean simulate) {
            var copy = new ArrayList<SizedIngredient>(contents.size());
            for (var content : contents) copy.add(copyContent(content));
            return meHandleRecipeOutputInner(copy, simulate);
        }

        @Override
        public List<SizedIngredient> meHandleRecipeOutputInner(List<SizedIngredient> left, boolean simulate) {
            return acceptOutputs(getMachine(), left, ingredient -> AEOutputIngredients.item(ingredient, simulate), simulate);
        }
    }

    protected abstract static class MEFluidHandlerBase extends NotifiableMERecipeHandlerTrait<SizedFluidIngredient, FluidStack> {

        @Getter
        private final IO io;

        @Getter
        @Setter
        private Object2LongMap<SizedFluidIngredient> preparedMEHandleContents = new Object2LongOpenHashMap<>();

        public MEFluidHandlerBase(MEPatternBufferPartMachineBase machine, IO io) {
            super();
            this.io = io;
        }

        public MEPatternBufferPartMachineBase getMachine() {
            return (MEPatternBufferPartMachineBase) super.getMachine();
        }

        @Override
        public RecipeCapability<SizedFluidIngredient> getCapability() {
            return FluidRecipeCapability.CAP;
        }

        @Override
        public int[] getActiveSlots() {
            return getMachine().getActiveSlots();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Int2ObjectMap<List<FluidStack>> getActiveAndUnCachedSlotsLimitContentsMap() {
            var map = new Int2ObjectArrayMap<List<FluidStack>>();
            var machine = getMachine();
            var shared = (List<FluidStack>) (Object) machine.getSharedCatalystTank().getContents();
            for (int slot : machine.getActiveAndUnCachedSlots()) {
                var inputs = machine.getInternalSlot(slot).getLimitFluidStackInput();
                inputs.addAll(shared);
                map.put(slot, inputs);
            }
            return map;
        }

        @Override
        public Object2LongMap<FluidStack> getStackMapFromFirstAvailableSlot(IntCollection slots) {
            final var inventory = getMachine().getFirstActiveInternalSlot(slots);

            if (inventory != null) {
                Object2LongOpenHashMap<FluidStack> map = new Object2LongOpenHashMap<>();
                for (var entry : Object2LongMaps.fastIterable(inventory.getFluidStackInputMap())) {
                    map.addTo(entry.getKey(), entry.getLongValue());
                }
                return map;
            }

            return Object2LongMaps.emptyMap();
        }

        @Override
        public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<SizedFluidIngredient> left, boolean simulate, int trySlot) {
            var internalSlot = getMachine().getInternalSlotOrNull(trySlot);
            if (internalSlot != null && internalSlot.isFluidActive(simulate)) {
                Object2LongMap<SizedFluidIngredient> remaining = left;
                if (simulate) {
                    if (!internalSlot.testCatalystFluidInternal(recipe)) return false;
                    if (!internalSlot.testVirtualFluidInternal(recipe)) return false;
                    remaining = internalSlot.stripVirtuallySuppliedFluids(left);
                }
                return internalSlot.handleFluidInternal(remaining, simulate);
            } else return left.isEmpty();
        }

        @Override
        public void prepareMEHandleContents(GTRecipe recipe, List<SizedFluidIngredient> left, boolean simulate) {
            if (simulate) {
                getMachine().getSharedCatalystTank().handleRecipeInner(IO.IN, recipe, left, true);
            }
            setPreparedMEHandleContents(AEUtils.fluidIngredientsMap(left));
        }

        @Override
        public List<SizedFluidIngredient> meHandleRecipeOutput(List<?> contents, boolean simulate) {
            var copy = new ArrayList<SizedFluidIngredient>(contents.size());
            for (var content : contents) copy.add(copyContent(content));
            return meHandleRecipeOutputInner(copy, simulate);
        }

        @Override
        public List<SizedFluidIngredient> meHandleRecipeOutputInner(List<SizedFluidIngredient> left, boolean simulate) {
            return acceptOutputs(getMachine(), left, ingredient -> AEOutputIngredients.fluid(ingredient, simulate), simulate);
        }
    }

    private static <T> List<T> acceptOutputs(MEPatternBufferPartMachineBase machine, List<T> contents,
                                            Function<T, GenericStack> resolve, boolean simulate) {
        var accepted = new Object2LongOpenHashMap<AEKey>();
        try {
            for (T content : contents) {
                var output = resolve.apply(content);
                if (output != null) accepted.put(output.what(), Math.addExact(accepted.getLong(output.what()), output.amount()));
            }
            for (var entry : accepted.object2LongEntrySet()) Math.addExact(machine.buffer.getLong(entry.getKey()), entry.getLongValue());
        } catch (ArithmeticException overflow) { return contents; }
        if (!simulate && !accepted.isEmpty()) {
            for (var entry : accepted.object2LongEntrySet()) machine.buffer.put(entry.getKey(), Math.addExact(machine.buffer.getLong(entry.getKey()), entry.getLongValue()));
            machine.markAsChanged();
            machine.getMETrait().notifySelfIO();
        }
        return List.of();
    }
}
