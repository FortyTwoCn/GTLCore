package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.api.recipe.RecipeChancePreview;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import org.gtlcore.gtlcore.api.recipe.NativeRecipeState;
import org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeRunner;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.Map;

/** Keeps native handler groups, simulation, NC semantics and output voiding around long chance rolls. */
@Mixin(RecipeRunner.class)
public abstract class NativeRecipeRunnerMixin {
    @Shadow @Final private GTRecipe recipe;
    @Shadow @Final private IO io;
    @Shadow @Final private boolean isTick;
    @Shadow @Final private Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches;
    @Shadow @Final private boolean simulated;
    @Unique private IRecipeCapabilityHolder gtlcore$holder;
    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$rememberHolder(GTRecipe recipe, IO io, boolean isTick, IRecipeCapabilityHolder holder,
                                       Map<RecipeCapability<?>, Object2IntMap<?>> caches, boolean simulated, CallbackInfo ci) {
        gtlcore$holder = holder;
    }
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$coreRoutes(Map<RecipeCapability<?>, List<Content>> contents,
                                   CallbackInfoReturnable<ActionResult> cir) {
        if (!RecipeRunnerHelper.usesCoreRoutes(gtlcore$holder, contents, isTick)) return;
        var result = RecipeRunnerHelper.handleRecipe(io, gtlcore$holder, contents, chanceCaches, isTick, recipe, simulated);
        cir.setReturnValue(result.isSuccess() ? ActionResult.SUCCESS : ActionResult.fail(result.reason(), null, io));
    }
    @ModifyVariable(method = "fillContentMatchList", at = @At("HEAD"), argsOnly = true, ordinal = 0,
            remap = false, require = 1)
    private Map<RecipeCapability<?>, List<Content>> gtlcore$previewChanceOutputs(Map<RecipeCapability<?>, List<Content>> entries) {
        return simulated && io == IO.OUT ? RecipeChancePreview.output(recipe, gtlcore$holder, entries, chanceCaches, isTick) : entries;
    }
    @Redirect(method = "fillContentMatchList", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/api/recipe/chance/logic/ChanceLogic;roll(Lcom/gregtechceu/gtceu/api/capability/recipe/RecipeCapability;Ljava/util/List;Lit/unimi/dsi/fastutil/objects/Object2IntMap;I)Ljava/util/List;"),
            remap = false, require = 1)
    private List<Content> gtlcore$longChanceRuns(ChanceLogic logic, RecipeCapability<?> cap, List<Content> entries,
                                               Object2IntMap<?> cache, int nativeRuns) {
        long runs = NativeRecipeState.realParallels(recipe);
        if (logic == ChanceLogic.OR.value() || logic instanceof LongChanceLogic) {
            int recipeTier = IGTRecipe.of(recipe).getEuTier();
            int machineTier = gtlcore$holder instanceof IOverclockMachine overclock ? overclock.getOverclockTier() : recipeTier;
            return LongChanceLogic.OR.roll(cap, entries, cache, runs, recipeTier, machineTier);
        }
        if (logic == ChanceLogic.NONE.value()) return List.of();
        if (runs > Integer.MAX_VALUE) throw new IllegalArgumentException("Chance logic " + logic + " does not support long run counts");
        return logic.roll(cap, entries, cache, Math.toIntExact(runs));
    }
}
