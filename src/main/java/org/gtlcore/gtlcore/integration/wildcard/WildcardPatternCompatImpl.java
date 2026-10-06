package org.gtlcore.gtlcore.integration.wildcard;

import org.gtlcore.gtlcore.common.data.GTLRegistryTooltips;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;

import java.util.List;

import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

/**
 * Actual implementation of wildcard pattern integration.
 * This class will only be loaded when wildcard_pattern mod is present.
 */
public class WildcardPatternCompatImpl {

    public static MachineEntry<MachineDefinition> ME_WILDCARD_PATTERN_BUFFER;
    public static MachineEntry<MachineDefinition> ME_STOCKING_WILDCARD_PATTERN_BUFFER;

    static void init() {
        // Initialization logic if needed
    }

    static void registerMachines() {
        ME_WILDCARD_PATTERN_BUFFER = REGISTRATE
                .machine("me_wildcard_pattern_buffer", holder -> new MEWildcardPatternBufferPartMachine(holder, IO.BOTH))
                .langValue("ME Wildcard Pattern Buffer")
                .tier(GTValues.UHV)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_ITEMS, PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_ITEMS,
                        PartAbility.EXPORT_FLUIDS)
                .colorOverlayTieredHullModel(com.gregtechceu.gtceu.GTCEu.id("block/overlay/appeng/me_buffer_hatch"))
                .tooltips(
                        Component.translatable("block.gtceu.pattern_buffer.desc.0"),
                        Component.translatable("gtceu.machine.me_wildcard_pattern_buffer.desc.0"),
                        Component.translatable("tooltip.gtlcore.wireless_bookmark.quick_connect"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.3"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.5"),
                        Component.translatable("block.gtceu.pattern_buffer.desc.2"),
                        Component.translatable("gtceu.universal.enabled"))
                .tooltipBuilder(GTLRegistryTooltips.ADD)
                .register();

        ME_STOCKING_WILDCARD_PATTERN_BUFFER = REGISTRATE
                .machine("me_stocking_wildcard_pattern_buffer", holder -> new MEStockingWildcardPatternBufferPartMachine(holder, IO.BOTH))
                .langValue("ME Stocking Wildcard Pattern Buffer")
                .tier(GTValues.UIV)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_ITEMS, PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_ITEMS, PartAbility.EXPORT_FLUIDS)
                .colorOverlayTieredHullModel(com.gregtechceu.gtceu.GTCEu.id("block/overlay/appeng/me_buffer_hatch"))
                .tooltips(
                        Component.translatable("block.gtceu.pattern_buffer.desc.0"),
                        Component.translatable("gtceu.machine.me_wildcard_pattern_buffer.desc.0"),
                        Component.translatable("gtlcore.machine.me_stocking_pattern_buffer.desc.0"),
                        Component.translatable("gtlcore.machine.me_stocking_pattern_buffer.desc.1"),
                        Component.translatable("tooltip.gtlcore.wireless_bookmark.quick_connect"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.3"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.5"),
                        Component.translatable("block.gtceu.pattern_buffer.desc.2"),
                        Component.translatable("gtceu.universal.enabled"))
                .tooltipBuilder(GTLRegistryTooltips.ADD)
                .register();
    }

    public static MachineDefinition getWildcardPatternBufferDefinition() {
        return ME_WILDCARD_PATTERN_BUFFER.get();
    }

    public static MachineDefinition getStockingWildcardPatternBufferDefinition() {
        return ME_STOCKING_WILDCARD_PATTERN_BUFFER.get();
    }

    /**
     * Check if the given ItemStack is a wildcard pattern.
     *
     * @param stack the ItemStack to check
     * @return true if the stack is a wildcard pattern
     */
    static boolean isWildcardPattern(ItemStack stack) {
        return NativeWildcardPatternDecoder.isWildcardPattern(stack);
    }

    /**
     * Expand a wildcard pattern into all its possible pattern details.
     *
     * @param wildcardStack the wildcard pattern ItemStack
     * @param level         the level for pattern decoding
     * @return list of expanded pattern details, or empty list if not a valid wildcard pattern
     */
    static List<IPatternDetails> expandPatterns(ItemStack wildcardStack, Level level) {
        if (!isWildcardPattern(wildcardStack) || level == null) {
            return List.of();
        }

        return NativeWildcardPatternDecoder.expand(wildcardStack, level);
    }
}
