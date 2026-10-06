package org.gtlcore.gtlcore.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.steam.SteamEnergyRecipeHandler;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import java.util.*;

/** Core's 3^OC / 2 EU per mB, preserving exact steam requirements and a recipe's selected OC. */
public final class ExactSteamEnergyRecipeHandler extends SteamEnergyRecipeHandler {
    public static final String RECIPE_OC = "gtlcore:steam_overclocks";
    private int overclocks;
    private java.util.function.IntSupplier currentOverclocks;
    public ExactSteamEnergyRecipeHandler(NotifiableFluidTank tank, int overclocks) { super(tank, 2); setOverclocks(overclocks); }
    public ExactSteamEnergyRecipeHandler(NotifiableFluidTank tank, java.util.function.IntSupplier current) { this(tank, 0); currentOverclocks = current; }
    private java.util.function.Supplier<SteamEnergyConversion.Rate> customRate;
    /** Explicit conversion ratios let native add-on hatches share the same exact steam accounting. */
    public ExactSteamEnergyRecipeHandler(NotifiableFluidTank tank, java.util.function.Supplier<SteamEnergyConversion.Rate> rate) {
        this(tank, 0); customRate = java.util.Objects.requireNonNull(rate);
    }
    private SteamEnergyConversion.Rate rate(GTRecipe recipe) {
        if (customRate != null) return java.util.Objects.requireNonNull(customRate.get());
        int oc = recipe != null && recipe.data.contains(RECIPE_OC) ? recipe.data.getInt(RECIPE_OC) : currentOverclocks();
        return SteamEnergyConversion.Rate.overclocked(oc);
    }
    private int currentOverclocks() { return currentOverclocks == null ? overclocks : currentOverclocks.getAsInt(); }
    public void setOverclocks(int value) {
        if (value < 0 || value > 4) throw new IllegalArgumentException("Steam overclocks must be between 0 and 4");
        overclocks = value;
    }
    @Override public List<EnergyStack> handleRecipeInner(IO io, GTRecipe recipe, List<EnergyStack> left, boolean simulate) {
        if (io != IO.IN) return left;
        return SteamEnergyConversion.handle(getSteamTank(), rate(recipe), io, recipe, left, simulate);
    }
    @Override public List<Object> getContents() { return List.of(euAvailable()); }
    @Override public double getTotalContentAmount() { return euAvailable(); }
    private long euAvailable() { return SteamEnergyConversion.availableEU(getSteamTank(), rate(null)); }
}
