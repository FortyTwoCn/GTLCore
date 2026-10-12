package org.gtlcore.gtlcore.api.pattern;

import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.PredicateContext;
import com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Resolves the public native match API once; both GT 8 return contracts retain their own matching rules. */
public final class NativePredicateMatch {
    private static final MethodHandle MATCH = resolve();

    private NativePredicateMatch() {}

    private static MethodHandle resolve() {
        try {
            var method = MultiPredicate.class.getMethod("getPredicateAtPos", PredicateContext.class);
            var lookup = MethodHandles.publicLookup();
            var match = lookup.unreflect(method);
            if (method.getReturnType() == BasePredicate.class) {
                return match;
            } else if (method.getReturnType().getName().equals("com.gregtechceu.gtceu.api.multiblock.PredicateResult")) {
                var result = lookup.findVirtual(method.getReturnType(), "match", MethodType.methodType(BasePredicate.class));
                return MethodHandles.filterReturnValue(match, result);
            } else {
                throw new IllegalStateException("Unsupported native predicate match result: " + method.getReturnType());
            }
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    public static boolean test(MultiPredicate predicate, PredicateContext context) {
        return atPosition(predicate, context) != null;
    }

    public static BasePredicate atPosition(MultiPredicate predicate, PredicateContext context) {
        try {
            return (BasePredicate) MATCH.invokeExact(predicate, context);
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Native predicate match failed", error);
        }
    }
}
