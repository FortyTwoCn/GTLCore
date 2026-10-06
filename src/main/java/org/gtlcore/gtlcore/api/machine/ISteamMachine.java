package org.gtlcore.gtlcore.api.machine;

public interface ISteamMachine {

    /** Millibuckets consumed per EU. Native GT's getConversionRate uses this unit. */
    double getSteamPerEU();
}
