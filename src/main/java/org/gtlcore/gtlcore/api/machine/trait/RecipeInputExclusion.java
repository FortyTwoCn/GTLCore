package org.gtlcore.gtlcore.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;

/** Filter controller views without removing a physical part's handlers or its input grouping. */
public final class RecipeInputExclusion {
    private RecipeInputExclusion() {}
    public static void exclude(IRecipeCapabilityHolder holder, IRecipeHandler<?> excluded) {
        var lists = holder.getCapabilitiesProxy().get(IO.IN);
        if (lists != null) for (var it = lists.listIterator(); it.hasNext();) {
            var list = it.next();
            if (!list.getHandlersFlat().contains(excluded)) continue;
            var handlers = list.getHandlersFlat().stream().filter(handler -> handler != excluded).toList();
            if (handlers.isEmpty()) it.remove();
            else {
                var filtered = RecipeHandlerList.of(list.getHandlerIO(), list.getColor(), handlers);
                filtered.setGroup(list.getGroup());
                it.set(filtered);
            }
        }
        var flat = holder.getCapabilitiesFlat().get(IO.IN);
        if (flat != null && flat.get(excluded.getCapability()) != null) flat.get(excluded.getCapability()).remove(excluded);
    }
}
