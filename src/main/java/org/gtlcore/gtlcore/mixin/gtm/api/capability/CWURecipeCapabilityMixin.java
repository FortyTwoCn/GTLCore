package org.gtlcore.gtlcore.mixin.gtm.api.capability;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableComputationContainer;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.IContentSerializer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Native parallel previews use currently available CWU without allocating or paying provider energy. */
@Mixin(CWURecipeCapability.class)
public abstract class CWURecipeCapabilityMixin extends RecipeCapability<Integer> {
    protected CWURecipeCapabilityMixin(ResourceLocation id, int color, boolean render, int order, IContentSerializer<Integer> serializer) {
        super(id, color, render, order, serializer);
    }

    @Override
    public int getMaxParallelByInput(IRecipeCapabilityHolder holder, GTRecipe recipe, int limit, boolean tick) {
        var contents = (tick ? recipe.tickInputs : recipe.inputs).get(CWURecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) return Integer.MAX_VALUE;
        long required = 0;
        for (var content : contents) required += Math.max(0, CWURecipeCapability.CAP.of(content.content()));
        if (required == 0) return Integer.MAX_VALUE;
        if (limit <= 0) return 0;
        Set<IRecipeHandler<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IOpticalComputationProvider> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        long available = 0;
        for (var handler : holder.getCapabilitiesFlat(IO.IN, CWURecipeCapability.CAP)) {
            if (!visited.add(handler) || !(handler instanceof NotifiableComputationContainer container) || container.getHandlerIO() != IO.IN) continue;
            available += Math.max(0, container.requestCWUt(Integer.MAX_VALUE, true, seen));
        }
        return (int) Math.min(limit, available / required);
    }
}
