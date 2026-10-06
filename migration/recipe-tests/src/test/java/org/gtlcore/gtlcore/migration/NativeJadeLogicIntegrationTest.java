package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.jade.NativeRecipeLogicTooltip;
import com.gtladd.gtladditions.integration.jade.NativeWirelessEnergyTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;

class NativeJadeLogicIntegrationTest {
    private static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static String key(Component component) { return ((TranslatableContents) component.getContents()).getKey(); }
    private static CompoundTag energy(long amount, boolean input) {
        var data = new CompoundTag(); data.putBoolean("Working", true);
        var recipe = new CompoundTag(); recipe.putLong("EUt", amount); recipe.putBoolean("isInput", input);
        recipe.putLong("voltage", 128); data.put("Recipe", recipe); return data;
    }

    @Test void nativeVoltageLongFormattingSteamRoundingAndGeneratorWarningsAreRetained() {
        var data = energy(32, true);
        var row = NativeRecipeLogicTooltip.rows(data, 0, REGISTRIES).getFirst();
        assertEquals("gtceu.top.energy_consumption", key(row));
        var electricity = (TranslatableContents) row.getSiblings().get(1).getSiblings().get(2).getContents();
        assertEquals("0.25", electricity.getArgs()[0]);
        assertTrue(row.getString().contains("32 EU/t"));
        assertTrue(NativeRecipeLogicTooltip.rows(energy(3, true), 0.5, REGISTRIES).getFirst().getString().contains("2 mB/t"));
        data = energy(Long.MAX_VALUE, false); data.getCompound("Recipe").putLong("voltage", Long.MAX_VALUE);
        data.getCompound("Recipe").putLong("generatorPower", 1);
        var rows = NativeRecipeLogicTooltip.rows(data, 0, REGISTRIES);
        assertEquals(2, rows.size()); assertTrue(rows.getFirst().getString().contains("9.22E EU/t"));
        assertEquals("gtceu.jade.generator.too_small", key(rows.getLast()));
    }

    @Test void nativeWaitingFallbackAndStyledCoreWorkingReasonRoundTrip() {
        var data = new CompoundTag(); data.putString("FailureReason", "gtceu.recipe.fail.no.enough.eu.in");
        data.putBoolean("Waiting", true);
        var rows = NativeRecipeLogicTooltip.rows(data, 0, REGISTRIES);
        assertEquals("gtceu.recipe_logic.recipe_waiting", key(rows.getFirst()));
        assertEquals("gtceu.recipe.fail.no.enough.eu.in", key(rows.getLast()));
        var reason = Component.literal("无线 ⚡ / 气体不足").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        data.putString("reason", Component.Serializer.toJson(reason, REGISTRIES));
        rows = NativeRecipeLogicTooltip.rows(data, 0, REGISTRIES);
        assertEquals(1, rows.size());
        assertEquals(reason, ((TranslatableContents) rows.getFirst().getContents()).getArgs()[0]);
        data = energy(0, false); data.putString("work_reason", Component.Serializer.toJson(reason, REGISTRIES));
        assertEquals(reason, ((TranslatableContents) NativeRecipeLogicTooltip.rows(data, 0, REGISTRIES).getFirst().getContents()).getArgs()[0]);
    }

    @Test void arbitraryPrecisionWirelessDirectionsAndCoreReasonsComposeWithoutInfinity() {
        var amount = BigInteger.TEN.pow(400).add(BigInteger.ONE);
        for (var signed : new BigInteger[] {amount, amount.negate()}) {
            var line = NativeWirelessEnergyTooltip.energyLine(signed);
            assertEquals(signed.signum() < 0 ? "gtceu.top.energy_consumption" : "gtceu.top.energy_production", key(line));
            assertTrue(line.getString().contains("10e399 EU/t"));
            assertFalse(line.getString().contains("∞")); assertFalse(line.getString().contains("NaN"));
            var data = energy(0, true); NativeRecipeLogicTooltip.writeAlternateEnergy(data, line, REGISTRIES);
            data.putString("work_reason", Component.Serializer.toJson(Component.literal("insufficient output space"), REGISTRIES));
            var rows = NativeRecipeLogicTooltip.rows(data, 0, REGISTRIES);
            assertEquals(2, rows.size()); assertEquals(line, rows.getFirst());
            assertEquals("gtceu.recipe.fail.reason", key(rows.getLast()));
        }
        assertThrows(IllegalArgumentException.class, () -> NativeWirelessEnergyTooltip.energyLine(BigInteger.ZERO));
    }
}
