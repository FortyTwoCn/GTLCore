package org.gtlcore.gtlcore.mixin.gtm.cover;

import org.gtlcore.gtlcore.utils.TagExprFilter;

import com.gregtechceu.gtceu.api.cover.filter.TagFilter;

import net.minecraft.tags.TagKey;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Native GT shares one tag filter implementation for item and fluid stacks. */
@Mixin(TagFilter.class)
public abstract class TagFilterMixin<T, S> {
    @Shadow(remap = false) protected String filterString;
    @Shadow(remap = false) @Final private Function<T, Stream<TagKey<S>>> tagsSupplier;
    @Unique private final TagExprFilter.CachedExpression gtlcore$expression = new TagExprFilter.CachedExpression();

    @Inject(method = "testTagExpr", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$matchOriginalTagSyntax(T stack, CallbackInfoReturnable<Boolean> cir) {
        var tags = tagsSupplier.apply(stack).map(TagKey::location).map(Object::toString).collect(Collectors.toSet());
        // Preserve Core's namespace-independent unqualified patterns. Native GT still
        // owns its per-item/fluid result cache, update callbacks, component codec and UI.
        cir.setReturnValue(gtlcore$expression.matches(filterString, tags));
    }
}
