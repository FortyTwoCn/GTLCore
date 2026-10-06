package org.gtlcore.gtlcore.api.machine.trait;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.NotNull;
import java.util.Arrays;
import java.util.List;

/** Core's shared pattern circuit configuration; excluded from searchable consumable inventory. */
public class NotifiableCircuitItemStackHandler extends NotifiableItemStackHandler {
    public NotifiableCircuitItemStackHandler() {
        super(1, IO.IN, IO.IN, slots -> new CustomItemStackHandler(slots) {
            @Override public int getSlotLimit(int slot) { return 1; }
        });
        setFilter(IntCircuitBehaviour::isIntegratedCircuit);
        shouldSearchContent(false);
    }

    /** Existing Core pattern owners can construct and attach the shared circuit in one call. */
    public NotifiableCircuitItemStackHandler(MetaMachine owner) {
        this();
        owner.attachTrait(this);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (slot != 0) throw new IndexOutOfBoundsException("Circuit slot " + slot);
        if (!IntCircuitBehaviour.isIntegratedCircuit(stack)) return stack;
        if (!simulate) storage.setStackInSlot(0, stack.copyWithCount(1));
        return ItemStack.EMPTY;
    }

    @Override
    public List<SizedIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedIngredient> left, boolean simulate) {
        if (!simulate || io != IO.IN) return left;
        var stored = storage.getStackInSlot(0);
        if (!IntCircuitBehaviour.isIntegratedCircuit(stored)) return left;
        for (var iterator = left.iterator(); iterator.hasNext();) {
            var predicate = LongIngredient.matchingIngredient(iterator.next());
            if (Arrays.stream(predicate.getItems()).anyMatch(IntCircuitBehaviour::isIntegratedCircuit) &&
                    predicate.test(stored)) {
                iterator.remove();
                break;
            }
        }
        return left;
    }
}
