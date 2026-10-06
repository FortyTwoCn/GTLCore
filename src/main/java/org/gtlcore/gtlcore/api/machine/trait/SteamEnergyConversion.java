package org.gtlcore.gtlcore.api.machine.trait;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Exact steam/EU conversion shared by native handlers and Core's adjustable steam machines. */
public final class SteamEnergyConversion {
    private SteamEnergyConversion() {}

    public record Rate(BigInteger steam, BigInteger eu) {
        public Rate {
            if (steam.signum() <= 0 || eu.signum() <= 0) throw new IllegalArgumentException("Steam rate must be positive");
        }
        public static Rate of(double steamPerEU) {
            if (!Double.isFinite(steamPerEU) || steamPerEU <= 0) throw new IllegalArgumentException("Steam rate must be finite and positive");
            var decimal = BigDecimal.valueOf(steamPerEU).stripTrailingZeros();
            return decimal.scale() < 0 ? new Rate(decimal.unscaledValue().multiply(BigInteger.TEN.pow(-decimal.scale())), BigInteger.ONE) :
                    new Rate(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale()));
        }
        public static Rate overclocked(int oc) {
            if (oc < 0 || oc > 4) throw new IllegalArgumentException("Steam overclocks must be between 0 and 4");
            return new Rate(BigInteger.TWO, BigInteger.valueOf(3).pow(oc));
        }
    }

    public static List<EnergyStack> handle(NotifiableFluidTank tank, Rate rate, IO io, GTRecipe recipe,
                                            List<EnergyStack> left, boolean simulate) {
        if (io != IO.IN && io != IO.OUT) return left;
        for (var it = left.listIterator(); it.hasNext();) {
            long requiredEU = it.next().getTotalEU();
            if (requiredEU == 0) { it.remove(); continue; }
            var requested = BigInteger.valueOf(requiredEU).multiply(rate.steam()).add(rate.eu().subtract(BigInteger.ONE)).divide(rate.eu());
            // A request too large to represent cannot be fulfilled by a long-sized fluid handler.
            if (requested.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) continue;
            long amount = requested.longValueExact();
            var steam = io == IO.IN ? LongFluidIngredient.create(GTMaterials.Steam.getFluidTag(), amount) :
                    LongFluidIngredient.create(GTMaterials.Steam.getFluid(), amount);
            var residual = tank.handleRecipeInner(io, recipe, new ArrayList<>(List.of(steam)), simulate);
            if (residual.isEmpty()) { it.remove(); continue; }
            long unhandled = LongFluidIngredient.amount(residual.getFirst());
            var delivered = BigInteger.valueOf(amount - unhandled).multiply(rate.eu()).divide(rate.steam());
            long remaining = BigInteger.valueOf(requiredEU).subtract(delivered).max(BigInteger.ZERO).longValueExact();
            if (remaining == 0) it.remove(); else it.set(new EnergyStack(remaining));
        }
        return left;
    }

    public static long availableEU(NotifiableFluidTank tank, Rate rate) {
        var steam = BigInteger.ZERO;
        for (int i = 0; i < tank.getTanks(); i++) {
            var stack = tank.getFluidInTank(i);
            if (!stack.isEmpty() && stack.getFluid().is(GTMaterials.Steam.getFluidTag())) steam = steam.add(BigInteger.valueOf(stack.getAmount()));
        }
        return steam.multiply(rate.eu()).divide(rate.steam()).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }
}
