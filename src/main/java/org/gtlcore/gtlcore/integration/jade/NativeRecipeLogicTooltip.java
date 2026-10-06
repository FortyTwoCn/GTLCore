package org.gtlcore.gtlcore.integration.jade;

import org.gtlcore.gtlcore.api.machine.ISteamMachine;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeStatus;
import org.gtlcore.gtlcore.utils.NumberUtils;
import org.gtlcore.gtlcore.utils.TextUtil;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.steam.SimpleSteamMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.SteamParallelMultiblockMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Registry-aware Jade rows shared by the native provider and optional energy integrations. */
public final class NativeRecipeLogicTooltip {
    public static final String ALTERNATE_ENERGY = "gtlcore:alternate_energy";

    private NativeRecipeLogicTooltip() {}

    public static void writeStatus(CompoundTag data, RecipeLogic logic, HolderLookup.Provider registries) {
        if (logic instanceof IRecipeStatus status) {
            if (status.getRecipeStatus() != null && status.getRecipeStatus().reason() != null)
                data.putString("reason", Component.Serializer.toJson(status.getRecipeStatus().reason(), registries));
            if (status.getWorkingStatus() != null && status.getWorkingStatus().reason() != null)
                data.putString("work_reason", Component.Serializer.toJson(status.getWorkingStatus().reason(), registries));
        }
    }

    public static void writeAlternateEnergy(CompoundTag data, Component line, HolderLookup.Provider registries) {
        data.putString(ALTERNATE_ENERGY, Component.Serializer.toJson(line, registries));
    }

    public static double steamRate(BlockEntity entity) {
        if (entity instanceof ISteamMachine steam) return steam.getSteamPerEU();
        if (entity instanceof SimpleSteamMachine steam) return steam.getConversionRate();
        if (entity instanceof SteamParallelMultiblockMachine steam) return steam.getConversionRate();
        return 0;
    }

    public static List<Component> rows(CompoundTag data, double steamRate, HolderLookup.Provider registries) {
        var rows = new ArrayList<Component>();
        if (data.getBoolean("Working")) {
            var recipe = data.getCompound("Recipe");
            long amount = recipe.getLong("EUt");
            boolean input = recipe.getBoolean("isInput");
            if (amount > 0) {
                Component value;
                if (steamRate > 0 && Double.isFinite(steamRate)) {
                    long steam = (long) Math.ceil(amount * steamRate);
                    value = Component.literal(FormattingUtil.formatNumbers(steam) + " mB/t")
                            .withStyle(ChatFormatting.GREEN);
                } else {
                    long voltage = recipe.getLong("voltage");
                    if (voltage <= 0) voltage = GTValues.V[GTUtil.getTierByVoltage(amount)];
                    int tier = Math.min(GTUtil.getTierByVoltage(voltage), GTValues.VNF.length - 1);
                    var amps = FormattingUtil.DECIMAL_FORMAT_2F.format(BigDecimal.valueOf(amount)
                            .divide(BigDecimal.valueOf(voltage), 3, RoundingMode.DOWN));
                    value = Component.literal(NumberUtils.formatLong(amount)).withStyle(ChatFormatting.RED)
                            .append(Component.literal(" EU/t").withStyle(ChatFormatting.RESET))
                            .append(Component.literal(" (").withStyle(ChatFormatting.GREEN))
                            .append(Component.translatable("gtceu.top.electricity", amps, GTValues.VNF[tier])
                                    .withStyle(TextUtil.GTL_CORE$VC[Math.min(tier, TextUtil.GTL_CORE$VC.length - 1)]))
                            .append(Component.literal(")").withStyle(ChatFormatting.GREEN));
                }
                String key = input ? (steamRate > 0 ? "gtceu.jade.steam_consumption" : "gtceu.top.energy_consumption") :
                        "gtceu.top.energy_production";
                rows.add(Component.translatable(key).append(" ").append(value));
                long power = recipe.getLong("generatorPower");
                if (!input && power > 0 && power < amount)
                    rows.add(Component.translatable("gtceu.jade.generator.too_small").withStyle(ChatFormatting.RED));
            } else {
                var alternate = read(data, ALTERNATE_ENERGY, registries);
                if (alternate != null) rows.add(alternate);
            }
            appendReason(rows, read(data, "work_reason", registries));
        } else {
            var reason = read(data, "reason", registries);
            if (reason != null) appendReason(rows, reason);
            else if (data.contains("FailureReason", Tag.TAG_STRING)) {
                rows.add(data.getBoolean("Waiting") ?
                        Component.translatable("gtceu.recipe_logic.recipe_waiting").withStyle(ChatFormatting.YELLOW) :
                        Component.translatable("gtceu.recipe_logic.setup_fail").withStyle(ChatFormatting.RED));
                rows.add(Component.translatable(data.getString("FailureReason")));
            }
        }
        return List.copyOf(rows);
    }

    private static Component read(CompoundTag data, String key, HolderLookup.Provider registries) {
        if (!data.contains(key, Tag.TAG_STRING) || data.getString(key).isEmpty()) return null;
        return Component.Serializer.fromJson(data.getString(key), registries);
    }

    private static void appendReason(List<Component> rows, Component reason) {
        if (reason != null) rows.add(Component.translatable("gtceu.recipe.fail.reason", reason).withStyle(ChatFormatting.RED));
    }
}
