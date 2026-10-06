package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.utils.datastructure.Int128;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Decimal text is used by wireless energy persistence and machine statistics. */
class NativeInt128FormattingTest {
    @Test void formatsSignedBoundariesWithoutPaddingOrSignWrap() {
        BigInteger[] values = { BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE.negate(),
                BigInteger.valueOf(Long.MAX_VALUE), BigInteger.valueOf(Long.MIN_VALUE),
                BigInteger.ONE.shiftLeft(63), BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE),
                BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE), BigInteger.ONE.shiftLeft(127).negate() };
        for (var value : values) {
            var number = Int128.fromBigInteger(value);
            assertEquals(value.toString(), number.toString());
            assertEquals(value, Int128.fromString(number.toString()).toBigInteger());
        }
    }

    @Test void decimalRoundTripsMatchBigIntegerForSigned128BitEnergyValues() {
        var random = new Random(0x128202621L);
        for (int n = 0; n < 2048; n++) {
            var number = new Int128(random.nextLong(), random.nextLong());
            var expected = number.toBigInteger();
            assertEquals(expected.toString(), number.toString());
            assertEquals(expected, Int128.fromString(number.toString()).toBigInteger());
        }
    }
}
