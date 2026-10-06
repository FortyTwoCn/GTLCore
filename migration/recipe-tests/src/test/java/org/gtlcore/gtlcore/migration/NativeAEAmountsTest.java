package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.gui.NativeAEAmounts;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAEAmountsTest {
    @Test void decimalAndExpressionsRetainEveryItemBeyondDoublePrecision() {
        assertEquals(9_007_199_254_740_993L, NativeAEAmounts.parse("9007199254740993"));
        assertEquals(Long.MAX_VALUE, NativeAEAmounts.parse("9223372036854775807"));
        assertEquals(Long.MAX_VALUE, NativeAEAmounts.parse("9223372036854775807 / 3 * 3"));
        assertEquals(9_007_199_254_740_993L, NativeAEAmounts.parse("(9007199254740993 + 2) - 2"));
        assertEquals(250_002, NativeAEAmounts.parse(" 1.5K * 1000 / 6 + 2 "));
        assertEquals(1_250_000_000L, NativeAEAmounts.parse("1G + .25b"));
        assertEquals(1_000_002, NativeAEAmounts.parse("1e+6+2"));
        assertEquals(1, NativeAEAmounts.parse(".9"));
        assertEquals(1, NativeAEAmounts.parse("-3"));
        assertEquals(Long.MAX_VALUE, NativeAEAmounts.parse("9223372036854775807+1"));
    }
    @Test void malformedAndUnboundedExpressionsCannotChangeConfiguration() {
        for (String value : new String[]{"", " ", "NaN", "Infinity", "1/0", "1/", "1+", "1..2", "(1+2", "1)2", "1e100", "2q", "7;0", "1".repeat(257)})
            assertEquals(-1, NativeAEAmounts.parse(value), value);
        assertEquals(-1, NativeAEAmounts.parse(null));
    }
    @Test void wheelChangesSaturateWithoutOverflowOrIntegerTruncation() {
        assertEquals(9_007_199_254_740_994L, NativeAEAmounts.scroll(9_007_199_254_740_993L, true, false));
        assertEquals(Long.MAX_VALUE, NativeAEAmounts.scroll(Long.MAX_VALUE, true, false));
        assertEquals(Long.MAX_VALUE, NativeAEAmounts.scroll(Long.MAX_VALUE / 2 + 1, true, true));
        assertEquals(Long.MAX_VALUE / 2, NativeAEAmounts.scroll(Long.MAX_VALUE, false, true));
        assertEquals(1, NativeAEAmounts.scroll(1, false, false));
    }
}
