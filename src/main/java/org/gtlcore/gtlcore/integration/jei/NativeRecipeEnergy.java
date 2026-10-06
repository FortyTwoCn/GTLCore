package org.gtlcore.gtlcore.integration.jei;

import org.gtlcore.gtlcore.utils.NumberUtils;
import org.gtlcore.gtlcore.utils.TextUtil;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.utils.GTUtil;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.screen.RichTooltip;
import brachy.modularui.widget.WidgetTree;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.layout.Flow;
import net.minecraft.network.chat.Component;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Exact energy arithmetic shared by the native recipe viewer, including computation recipes. */
public final class NativeRecipeEnergy {
    private NativeRecipeEnergy() {}

    public record Summary(BigInteger perTick, BigInteger total, boolean maximum, boolean input,
                          int tier, BigDecimal amperage) {}

    public static Summary summarize(GTRecipe recipe) {
        var energy = RecipeHelper.getRealEUtWithIO(recipe);
        var stack = energy.stack();
        var perTick = BigInteger.valueOf(stack.voltage()).multiply(BigInteger.valueOf(stack.amperage()));
        var total = perTick.multiply(BigInteger.valueOf(Math.max(0, recipe.duration)));
        boolean maximum = recipe.data.getBoolean("duration_is_total_cwu") && recipe.tickInputs.containsKey(CWURecipeCapability.CAP);
        if (maximum) {
            var cwu = BigInteger.ZERO;
            for (var content : recipe.getTickInputContents(CWURecipeCapability.CAP))
                cwu = cwu.add(BigInteger.valueOf(Math.max(0, CWURecipeCapability.CAP.of(content.content()))));
            total = total.divide(cwu.max(BigInteger.ONE));
        }
        int tier = Math.min(GTUtil.getTierByVoltage(stack.voltage()), GTValues.V.length - 1);
        var amps = new BigDecimal(perTick).divide(BigDecimal.valueOf(GTValues.V[tier]), 2, RoundingMode.DOWN);
        return new Summary(perTick, total, maximum, energy.io() == IO.IN, tier, amps);
    }

    public static String compact(BigInteger amount) {
        int unit = 0;
        var divisor = BigInteger.ONE;
        while (amount.abs().compareTo(divisor.multiply(BigInteger.valueOf(1000))) >= 0 && unit < NumberUtils.UNITS.length - 1) {
            divisor = divisor.multiply(BigInteger.valueOf(1000)); unit++;
        }
        return new BigDecimal(amount).divide(new BigDecimal(divisor), 2, RoundingMode.DOWN)
                .stripTrailingZeros().toPlainString() + NumberUtils.UNITS[unit];
    }

    public static void fill(Flow flow, GTRecipe recipe) {
        var summary = summarize(recipe);
        if (summary.perTick().signum() <= 0) return;
        var total = Text.lang(summary.maximum() ? "gtceu.recipe.max_eu" : "gtceu.recipe.total", compact(summary.total()));
        var totalTooltip = new RichTooltip().addLine(Component.literal(summary.total() + " EU"));
        var existingTotal = WidgetTree.findFirstWithNameNullable(flow, "max_eu");
        if (existingTotal instanceof TextWidget<?> text) text.value(total).tooltip(totalTooltip);
        else flow.child(total.asWidget().name("max_eu").tooltip(totalTooltip));

        var rate = Text.lang(summary.input() ? "gtceu.recipe.eu" : "gtceu.recipe.eu_inverted",
                summary.amperage().stripTrailingZeros().toPlainString(), GTValues.VN[summary.tier()])
                .withStyle(TextUtil.GTL_CORE$VC[Math.min(summary.tier(), TextUtil.GTL_CORE$VC.length - 1)]);
        var tooltip = new RichTooltip().addLine(Text.lang("gtceu.recipe.eu.total", summary.perTick().toString()));
        var existingRate = WidgetTree.findFirstWithNameNullable(flow, "eu");
        if (existingRate instanceof TextWidget<?> text) text.value(rate).tooltip(tooltip);
        else flow.child(rate.asWidget().name("eu").marginBottom(1).tooltip(tooltip));
    }
}
