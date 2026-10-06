package org.gtlcore.gtlcore.integration.ae2.gui;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;

/** Exact long quantities, including expressions accepted by the native AE configuration popup. */
public final class NativeAEAmounts {
    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);
    public static final int MAX_TEXT_LENGTH = 256;
    private NativeAEAmounts() {}

    /** Invalid input is rejected; valid expressions are truncated and bounded to the positive range. */
    public static long parse(String text) {
        if (text == null || text.isBlank() || text.length() > MAX_TEXT_LENGTH) return -1;
        try {
            var parser = new Parser(text.toLowerCase(Locale.ROOT));
            var value = parser.expression(0);
            parser.whitespace();
            if (parser.cursor != parser.text.length()) return -1;
            BigInteger whole = value.numerator.divide(value.denominator);
            if (whole.signum() <= 0) return 1;
            return whole.compareTo(MAX) > 0 ? Long.MAX_VALUE : whole.longValueExact();
        } catch (IllegalArgumentException | ArithmeticException exception) { return -1; }
    }
    public static long scroll(long current, boolean up, boolean control) {
        current = Math.max(1, current);
        if (control) return up ? current > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : current * 2 : Math.max(1, current / 2);
        return up ? current == Long.MAX_VALUE ? current : current + 1 : Math.max(1, current - 1);
    }

    // Rational arithmetic avoids losing the last item when a large quantity is divided and multiplied again.
    private record Fraction(BigInteger numerator, BigInteger denominator) {
        private Fraction {
            if (denominator.signum() == 0) throw new ArithmeticException("Division by zero");
            if (denominator.signum() < 0) { numerator = numerator.negate(); denominator = denominator.negate(); }
            var divisor = numerator.gcd(denominator);
            numerator = numerator.divide(divisor); denominator = denominator.divide(divisor);
        }
        private Fraction add(Fraction other, boolean subtract) {
            var right = other.numerator.multiply(denominator);
            return new Fraction(numerator.multiply(other.denominator).add(subtract ? right.negate() : right),
                    denominator.multiply(other.denominator));
        }
        private Fraction multiply(Fraction other, boolean divide) {
            return divide ? new Fraction(numerator.multiply(other.denominator), denominator.multiply(other.numerator)) :
                    new Fraction(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
        }
        private Fraction negate() { return new Fraction(numerator.negate(), denominator); }
    }
    private static final class Parser {
        private final String text;
        private int cursor;
        private Parser(String text) { this.text = text; }
        private void whitespace() { while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) cursor++; }
        private boolean take(char character) {
            whitespace();
            if (cursor < text.length() && text.charAt(cursor) == character) { cursor++; return true; }
            return false;
        }
        private Fraction expression(int depth) {
            var value = term(depth);
            while (true) {
                if (take('+')) value = value.add(term(depth), false);
                else if (take('-')) value = value.add(term(depth), true);
                else return value;
            }
        }
        private Fraction term(int depth) {
            var value = factor(depth);
            while (true) {
                if (take('*')) value = value.multiply(factor(depth), false);
                else if (take('/')) value = value.multiply(factor(depth), true);
                else return value;
            }
        }
        private Fraction factor(int depth) {
            if (depth > 32) throw new IllegalArgumentException("Expression nesting");
            if (take('+')) return factor(depth + 1);
            if (take('-')) return factor(depth + 1).negate();
            if (take('(')) {
                var value = expression(depth + 1);
                if (!take(')')) throw new IllegalArgumentException("Missing closing parenthesis");
                return value;
            }
            whitespace();
            int start = cursor;
            while (cursor < text.length() && (Character.isDigit(text.charAt(cursor)) || text.charAt(cursor) == '.')) cursor++;
            if (start == cursor) throw new IllegalArgumentException("Missing quantity");
            if (cursor < text.length() && text.charAt(cursor) == 'e') {
                cursor++;
                if (cursor < text.length() && (text.charAt(cursor) == '+' || text.charAt(cursor) == '-')) cursor++;
                int exponentStart = cursor;
                while (cursor < text.length() && Character.isDigit(text.charAt(cursor))) cursor++;
                if (exponentStart == cursor || cursor - exponentStart > 2 ||
                        Integer.parseInt(text.substring(exponentStart, cursor)) > 18)
                    throw new IllegalArgumentException("Quantity exponent");
            }
            var decimal = new BigDecimal(text.substring(start, cursor));
            int multiplier = 0;
            if (cursor < text.length()) {
                multiplier = switch (text.charAt(cursor)) { case 'k' -> 3; case 'm' -> 6; case 'g', 'b' -> 9; default -> 0; };
                if (multiplier != 0) cursor++;
            }
            decimal = decimal.scaleByPowerOfTen(multiplier);
            return decimal.scale() < 0 ? new Fraction(decimal.unscaledValue().multiply(BigInteger.TEN.pow(-decimal.scale())), BigInteger.ONE) :
                    new Fraction(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale()));
        }
    }
}
