package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.gtmt.NativeCreativeEnergyUI;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeCreativeEnergyInputTest {
    @Test void creativeVoltageInputRetainsEveryDigitAboveTheDoublePrecisionBoundary() {
        assertEquals("9007199254740993", NativeCreativeEnergyUI.validateVoltage("9007199254740993", 8));
        assertEquals("9223372036854775807", NativeCreativeEnergyUI.validateVoltage("9223372036854775807", 8192));
        assertEquals("8192", NativeCreativeEnergyUI.validateVoltage("0008192", 8192));
    }
    @Test void creativeVoltageInputRejectsOverflowMalformedPacketsAndTheWrongLaserTier() {
        for (String value : new String[] {"", "-1", "7", "1e20", "NaN", "9223372036854775808", "999999999999999999999", " 8192"})
            assertEquals("", NativeCreativeEnergyUI.validateVoltage(value, 8));
        assertEquals("", NativeCreativeEnergyUI.validateVoltage("8191", 8192));
        assertEquals("", NativeCreativeEnergyUI.validateVoltage(null, 8));
    }
}
