package org.gtlcore.gtlcore.api.recipe;

/** Extra Core headroom information on the immutable native overclock result. */
public interface IAdvancedOCResult {
    void gtlcore$setOverclockInfo(int baseOCLevel, double durationFactor, double voltageFactor, boolean available);
    int getBaseOCLevel();
    double getDurationFactor();
    double getVoltageFactor();
    boolean isSubTickOverclockAvailable();
}
