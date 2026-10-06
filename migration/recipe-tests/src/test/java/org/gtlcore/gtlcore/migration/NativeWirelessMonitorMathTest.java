package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.gtmt.NativeWirelessMonitorRows;
import com.gregtechceu.gtceu.api.GTValues;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;

class NativeWirelessMonitorMathTest {
    @Test void nativeStatisticsKeepArbitraryPrecisionForBothPowerDirections() {
        var huge = BigDecimal.TEN.pow(400);
        assertEquals(GTValues.VNF.length - 1, NativeWirelessMonitorRows.tier(huge));
        assertEquals(NativeWirelessMonitorRows.tier(huge), NativeWirelessMonitorRows.tier(huge.negate()));
        assertEquals(NativeWirelessMonitorRows.amperage(huge), NativeWirelessMonitorRows.amperage(huge.negate()));
        assertTrue(new BigDecimal(NativeWirelessMonitorRows.amperage(huge)).compareTo(BigDecimal.TEN.pow(380)) > 0);
        assertFalse(NativeWirelessMonitorRows.formatDecimal(huge).contains("∞"));
        assertFalse(NativeWirelessMonitorRows.formatDecimal(huge.negate()).contains("∞"));
        assertTrue(NativeWirelessMonitorRows.formatDecimal(huge.negate()).startsWith("-"));
    }
    @Test void nativeTimeEstimatesClampNegativeValuesAndUnboundedWirelessStorage() {
        var forever = (TranslatableContents) NativeWirelessMonitorRows.timeText(BigInteger.TEN.pow(400)).getContents();
        assertEquals("gtceu.multiblock.power_substation.time_forever", forever.getKey());
        var zero = (TranslatableContents) NativeWirelessMonitorRows.timeText(BigInteger.valueOf(-10)).getContents();
        assertEquals("gtceu.multiblock.power_substation.time_seconds", zero.getKey());
        assertEquals("0", zero.getArgs()[0]);
    }
}
