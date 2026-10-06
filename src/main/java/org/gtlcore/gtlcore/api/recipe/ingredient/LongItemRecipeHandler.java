package org.gtlcore.gtlcore.api.recipe.ingredient;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.item.component.ISpoilableItem;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import dev.latvian.mods.kubejs.recipe.ingredientaction.IngredientActionHolder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Native GT item transactions with long residual quantities and one inventory notification. */
public final class LongItemRecipeHandler {
    private LongItemRecipeHandler() {}

    public static void recordConsumed(@Nullable MetaMachine machine, @Nullable GTRecipe recipe, SizedIngredient ingredient) {
        if (machine instanceof MultiblockPartMachine part) {
            for (var controller : part.getControllers()) {
                var logic = controller.getTrait(RecipeLogic.class);
                if (logic != null && logic.getStartingRecipe() == recipe)
                    logic.getConsumedInputs().addConsumedInput(ItemRecipeCapability.CAP, ingredient);
            }
        } else if (machine != null) machine.getTraitOptional(RecipeLogic.class).map(RecipeLogic::getConsumedInputs)
                .ifPresent(inputs -> inputs.addConsumedInput(ItemRecipeCapability.CAP, ingredient));
    }

    public static List<SizedIngredient> handle(IO io, @Nullable GTRecipe recipe, List<SizedIngredient> left,
                                               boolean simulate, IO handlerIO, CustomItemStackHandler storage,
                                               @Nullable MetaMachine machine) {
        if (io != handlerIO || (io != IO.IN && io != IO.OUT)) return left;
        Runnable listener = storage.getOnContentsChanged();
        storage.setOnContentsChanged(() -> {});
        boolean changed = false;
        ItemStack[] visited = new ItemStack[storage.getSlots()];
        try {
            for (var it = left.listIterator(); it.hasNext();) {
                var sized = it.next();
                var matching = LongIngredient.matchingIngredient(sized);
                var custom = matching.getCustomIngredient();
                if (!(custom instanceof IntProviderIngredient) && matching.hasNoItems()) {
                    it.remove();
                    continue;
                }

                ItemStack[] items;
                long amount;
                if (custom instanceof IntProviderIngredient provider && simulate) {
                    items = new ItemStack[] { provider.getMaxSizeStack() };
                    amount = provider.getMaxRoll();
                } else {
                    items = sized.getItems();
                    if (items.length == 0 || items[0].isEmpty()) {
                        it.remove();
                        continue;
                    }
                    amount = LongIngredient.amount(sized);
                }

                for (int slot = 0; slot < storage.getSlots(); slot++) {
                    ItemStack current = visited[slot] == null ? storage.getStackInSlot(slot) : visited[slot];
                    int count = current.getCount();
                    if (io == IO.IN) {
                        if (current.isEmpty() || !matching.test(current)) continue;
                        ItemStack extracted = actioned(storage, slot, recipe);
                        if (extracted == null) {
                            extracted = storage.extractItem(slot, (int) Math.min(count, amount), simulate);
                        }
                        if (!extracted.isEmpty()) {
                            changed = true;
                            visited[slot] = current.copyWithCount(count - extracted.getCount());
                            if (!simulate) {
                                var consumed = extracted.copy();
                                ISpoilableItem spoilable = GTCapabilityHelper.getSpoilable(consumed);
                                if (spoilable != null) spoilable.freezeSpoiling();
                                if (machine instanceof MultiblockPartMachine part) {
                                    for (MultiblockControllerMachine controller : part.getControllers()) {
                                        RecipeLogic logic = controller.getTrait(RecipeLogic.class);
                                        if (logic != null && logic.getStartingRecipe() == recipe) {
                                            logic.getConsumedInputs().addConsumedInput(ItemRecipeCapability.CAP,
                                                    new SizedIngredient(net.minecraft.world.item.crafting.Ingredient.of(consumed), 1));
                                        }
                                    }
                                } else if (machine != null) machine.getTraitOptional(RecipeLogic.class)
                                        .map(RecipeLogic::getConsumedInputs)
                                        .ifPresent(inputs -> inputs.addConsumedInput(ItemRecipeCapability.CAP,
                                                new SizedIngredient(net.minecraft.world.item.crafting.Ingredient.of(consumed), 1)));
                            }
                            amount -= extracted.getCount();
                        }
                    } else {
                        int request = (int) Math.min(Integer.MAX_VALUE, amount);
                        ItemStack output = items[0].copyWithCount(request);
                        if (recipe != null) recipe.mutateOutput(output);
                        if (visited[slot] != null && !current.isEmpty() &&
                                !ItemStack.isSameItemSameComponents(current, output)) continue;
                        int free = Math.min(output.getMaxStackSize(), storage.getSlotLimit(slot)) - count;
                        if (free <= 0) continue;
                        int offered = (int) Math.min(free, amount);
                        output.setCount(offered);
                        ItemStack remainder = actioned(storage, slot, recipe);
                        if (remainder == null) remainder = storage.insertItem(slot, output, simulate);
                        int inserted = offered - remainder.getCount();
                        if (inserted > 0) {
                            changed = true;
                            visited[slot] = output.copyWithCount(count + inserted);
                            amount -= inserted;
                        }
                    }
                    if (amount <= 0) break;
                }

                if (amount <= 0) {
                    it.remove();
                } else if (custom instanceof IntProviderIngredient provider) {
                    it.set(new SizedIngredient(provider.getInner(), (int) amount));
                } else {
                    it.set(LongIngredient.withAmount(sized, amount));
                }
            }
            return left;
        } finally {
            storage.setOnContentsChanged(listener);
            if (changed && !simulate) listener.run();
        }
    }

    @SuppressWarnings("unchecked")
    private static @Nullable ItemStack actioned(CustomItemStackHandler storage, int slot, @Nullable GTRecipe recipe) {
        if (recipe == null || !GTCEu.Mods.isKubeJSLoaded()) return null;
        var stack = NotifiableItemStackHandler.KJSCallWrapper.applyIngredientAction(storage, slot,
                (List<IngredientActionHolder>) recipe.ingredientActions);
        return stack.isEmpty() ? null : stack;
    }
}
