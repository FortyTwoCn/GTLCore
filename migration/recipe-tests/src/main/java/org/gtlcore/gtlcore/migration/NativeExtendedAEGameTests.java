package org.gtlcore.gtlcore.migration;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocators;
import com.glodblock.github.extendedae.common.EAESingletons;
import com.glodblock.github.extendedae.common.items.ItemInfinityCell;
import com.glodblock.github.extendedae.common.me.itemhost.HostPatternModifier;
import com.glodblock.github.extendedae.container.ContainerPatternModifier;
import com.glodblock.github.glodium.network.packet.sync.Paras;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeExtendedAEGameTests {
    private static ContainerPatternModifier menu(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EAESingletons.PATTERN_MODIFIER));
        var host = new HostPatternModifier(EAESingletons.PATTERN_MODIFIER, player, MenuLocators.forHand(player, InteractionHand.MAIN_HAND));
        return new ContainerPatternModifier(1, player.getInventory(), host);
    }
    private static AEProcessingPattern pattern(ItemStack stack) { return new AEProcessingPattern(AEItemKey.of(stack)); }
    private static void action(ContainerPatternModifier menu, String action, Object... arguments) { menu.getActionMap().get(action).accept(new Paras(arguments)); }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativePatternModifierScopesRejectsInvalidActionsAndPersistsItsActualHost(GameTestHelper helper) {
        var menu = menu(helper); var slot = menu.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst();
        var input = AEItemKey.of(Items.IRON_INGOT); var output = AEItemKey.of(Items.GOLD_INGOT);
        var source = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(input, 6)), List.of(new GenericStack(output, 2))).copyWithCount(2);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("native scoped pattern")); slot.set(source);
        action(menu, "modify", 3, false, 1);
        helper.assertTrue(pattern(slot.getItem()).getSparseInputs().getFirst().amount() == 18 && pattern(slot.getItem()).getOutputs().getFirst().amount() == 2,
                "Native input-only pattern action changed the outputs");
        action(menu, "modify", 3, true, 1); action(menu, "modify", 5, false, 2);
        helper.assertTrue(pattern(slot.getItem()).getSparseInputs().getFirst().amount() == 6 && pattern(slot.getItem()).getOutputs().getFirst().amount() == 10,
                "Native exact division or output-only action changed the wrong side");
        var before = slot.getItem().copy();
        action(menu, "modify", Integer.MAX_VALUE, false); action(menu, "modify", 2, false, 99); action(menu, "modify", "invalid", false); action(menu, "gtlReplace");
        helper.assertTrue(ItemStack.matches(before, slot.getItem()), "Rejected native actions mutated the physical pattern");
        helper.assertTrue(slot.getItem().getCount() == 2 && slot.getItem().get(DataComponents.CUSTOM_NAME).equals(source.get(DataComponents.CUSTOM_NAME)),
                "Native host lost physical count or name after re-encoding");
        var player = menu.getPlayer();
        var restored = new HostPatternModifier(EAESingletons.PATTERN_MODIFIER, player, MenuLocators.forHand(player, InteractionHand.MAIN_HAND));
        helper.assertTrue(ItemStack.matches(slot.getItem(), restored.getInventoryByName("patternInv").getStackInSlot(0)),
                "Actual ExtendedAE item-host components did not restore the modified pattern");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativePatternModifierReplacesLongAmountsAndKeepsForcedInsertDeleteAndSwap(GameTestHelper helper) {
        var menu = menu(helper); var slot = menu.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst();
        var iron = AEItemKey.of(Items.IRON_INGOT); var gold = AEItemKey.of(Items.GOLD_INGOT); var nugget = AEItemKey.of(Items.IRON_NUGGET);
        slot.set(PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(iron, 9_007_199_254_740_993L)), List.of(new GenericStack(nugget, 2))));
        menu.replaceTarget.set(GenericStack.wrapInItemStack(new GenericStack(iron, 3)));
        menu.replaceWith.set(GenericStack.wrapInItemStack(new GenericStack(gold, 2)));
        action(menu, "gtlReplace", false);
        helper.assertTrue(pattern(slot.getItem()).getSparseInputs().getFirst().equals(new GenericStack(gold, 6_004_799_503_160_662L)),
                "Native replacement lost a long ratio or changed the output component key");
        action(menu, "swapReplace");
        helper.assertTrue(GenericStack.fromItemStack(menu.replaceTarget.getItem()).what().equals(gold)
                && GenericStack.fromItemStack(menu.replaceWith.getItem()).what().equals(iron), "Native swap action failed");
        menu.replaceTarget.set(ItemStack.EMPTY); menu.replaceWith.set(GenericStack.wrapInItemStack(new GenericStack(iron, 5)));
        action(menu, "gtlReplace", true);
        helper.assertTrue(pattern(slot.getItem()).getSparseInputs().size() == 2, "Native forced insertion was lost");
        menu.replaceTarget.set(GenericStack.wrapInItemStack(new GenericStack(iron, 1))); menu.replaceWith.set(ItemStack.EMPTY);
        action(menu, "gtlReplace", true);
        helper.assertTrue(pattern(slot.getItem()).getSparseInputs().size() == 1 && pattern(slot.getItem()).getOutputs().getFirst().what().equals(nugget),
                "Native forced deletion removed unrelated pattern data");
        helper.assertTrue(ItemInfinityCell.getAsIntMax(gold) == Long.MAX_VALUE, "Native ExtendedAE infinity-cell request limit was not migrated");
        helper.succeed();
    }
}
