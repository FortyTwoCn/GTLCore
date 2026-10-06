package org.gtlcore.gtlcore.api.pattern;

import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.PredicateContext;
import com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Preserve Core's overlapping limited alternatives using native predicate identities and count checks. */
public final class NativeLimitedPredicateMatches {
    private NativeLimitedPredicateMatches() {}

    public static boolean appliesTo(MultiPredicate owner) {
        // Native AND/XOR and mixed logical trees retain their own selection and minimum-count rules.
        return owner.isRoot() && owner.isOr() && owner.isLeaf() && owner.predicates().stream().anyMatch(
                NativeLimitedPredicateMatches::limited);
    }

    private static boolean limited(BasePredicate predicate) {
        return predicate.getMinCount() >= 0 || predicate.getMaxCount() >= 0 ||
                predicate.getMinSliceCount() >= 0 || predicate.getMaxSliceCount() >= 0;
    }

    public static @Nullable BasePredicate atPosition(MultiPredicate owner, PredicateContext context) {
        context.setStage(PredicateContext.PredicateStage.INTERNAL);
        var matches = new ArrayList<BasePredicate>();
        for (var predicate : owner.predicates())
            if (limited(predicate) && predicate.test(context)) matches.add(predicate);
        if (matches.size() == 1) return matches.getFirst();
        if (!matches.isEmpty()) return new Matches(owner, matches);
        for (var predicate : owner.predicates())
            if (!limited(predicate) && predicate.test(context)) return predicate;
        return null;
    }

    private static final class Matches extends BasePredicate {
        private final List<BasePredicate> matches;

        private Matches(MultiPredicate owner, List<BasePredicate> matches) {
            this.matches = List.copyOf(matches);
            setParent(owner);
        }

        @Override public boolean checkMaxCount(PredicateContext context) {
            boolean accepted = true;
            // Do not short-circuit: every matching limit must count this position and report its own errors.
            for (var predicate : matches) accepted &= predicate.checkMaxCount(context);
            return accepted;
        }

        @Override public boolean test(PredicateContext context) {
            boolean accepted = true;
            for (var predicate : matches) accepted &= predicate.test(context);
            return accepted;
        }

        @Override public void onError(PredicateContext context) {
            matches.forEach(predicate -> predicate.onError(context));
        }

        @Override public List<Component> getRecipeViewerTooltips(MultiPredicate root) {
            return matches.stream().flatMap(predicate -> predicate.getRecipeViewerTooltips(root).stream()).distinct().toList();
        }

        @Override public BasePredicate copy() {
            var copies = matches.stream().map(predicate -> {
                var copy = predicate.copy();
                copy.setParent(predicate.getParent());
                return copy;
            }).toList();
            var copy = new Matches(getParent(), copies);
            copyTo(copy);
            return copy;
        }

        @Override public List<BlockInfo> computeCandidates() {
            return matches.stream().flatMap(predicate -> predicate.getCandidates().stream()).distinct().toList();
        }

        @Override public String getTypeName() { return "GTL overlapping limited alternatives"; }
    }
}
