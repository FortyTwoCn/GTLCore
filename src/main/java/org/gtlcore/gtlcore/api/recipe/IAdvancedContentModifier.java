package org.gtlcore.gtlcore.api.recipe;

import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;

import java.math.BigDecimal;
import java.math.BigInteger;

public interface IAdvancedContentModifier {

    void setDivision(long numerator, long denominator);

    boolean gtlcore$hasDivision();

    long gtlcore$getNumerator();

    long gtlcore$getDenominator();

    static long applyLong(ContentModifier modifier, long value) {
        BigInteger result;
        if ((Object) modifier instanceof IAdvancedContentModifier precise && precise.gtlcore$hasDivision()) {
            result = BigInteger.valueOf(value).multiply(BigInteger.valueOf(precise.gtlcore$getNumerator()))
                    .divide(BigInteger.valueOf(precise.gtlcore$getDenominator()));
        } else {
            if (!Double.isFinite(modifier.multiplier()) || !Double.isFinite(modifier.addition())) {
                throw new IllegalArgumentException("Recipe quantity modifiers must be finite");
            }
            result = BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(modifier.multiplier()))
                    .add(BigDecimal.valueOf(modifier.addition())).toBigInteger();
        }
        return result.max(BigInteger.valueOf(Long.MIN_VALUE)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    static ContentModifier preciseDivision(long numerator, long denominator) {
        var modifier = new ContentModifier(0, 0);
        ((IAdvancedContentModifier) (Object) modifier).setDivision(numerator, denominator);
        return modifier;
    }

    static ContentModifier preciseMultiplier(long multiplier) {
        var modifier = new ContentModifier(multiplier, 0);
        ((IAdvancedContentModifier) (Object) modifier).setDivision(multiplier, 1);
        return modifier;
    }
}
