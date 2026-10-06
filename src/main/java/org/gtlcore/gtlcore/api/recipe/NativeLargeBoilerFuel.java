package org.gtlcore.gtlcore.api.recipe;

import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;

/** Core thermal fuel scaling; the native boiler recipe logic owns throttle scaling. */
public final class NativeLargeBoilerFuel {
    private NativeLargeBoilerFuel() {}

    public static ModifierFunction thermalModifier(int maxTemperature) {
        if (maxTemperature <= 0) return ModifierFunction.cancel(ModifierFunction.DEFAULT_FAILURE);
        return source -> {
            if (source.duration <= 0) return null;
            long scaledTicks = (long) source.duration * 6400;
            var copy = scaledTicks < maxTemperature ?
                    source.copy(IAdvancedContentModifier.preciseDivision(maxTemperature, scaledTicks), false) : source.copy();
            copy.duration = (int) Math.clamp(scaledTicks / maxTemperature, 1, Integer.MAX_VALUE);
            return copy;
        };
    }

    public static int throttle(int value) { return Math.clamp(value, 25, 100); }
    public static int burnTicks(int baseTicks, int throttle) {
        int rate = throttle(throttle);
        return (int) Math.clamp(((long) baseTicks * 100 + rate / 2) / rate, 1, Integer.MAX_VALUE);
    }
    public static int progress(int progress, int previousThrottle, int newThrottle, int duration) {
        int rate = throttle(newThrottle);
        return (int) Math.clamp(((long) progress * throttle(previousThrottle) + rate / 2) / rate, 0, duration);
    }
}
