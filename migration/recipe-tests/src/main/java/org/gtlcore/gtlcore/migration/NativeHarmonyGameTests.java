package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.electric.HarmonyMachine;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.*;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import java.math.BigInteger;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeHarmonyGameTests {
    private static HarmonyMachine build(GameTestHelper helper, int circuit) {
        var pos = new BlockPos(3, 3, 3); helper.setBlock(pos.east(), Blocks.IRON_BLOCK); helper.setBlock(pos.west(), Blocks.IRON_BLOCK);
        helper.setBlock(pos, NativeHarmonyFixtures.HARMONY.getBlock());
        var machine = (HarmonyMachine) helper.getBlockEntity(pos); machine.setFrontFacing(Direction.NORTH); machine.setWorkingEnabled(false);
        var player = FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "HarmonyPlacement"));
        NativeHarmonyFixtures.HARMONY.getBlock().setPlacedBy(helper.getLevel(), machine.getBlockPos(), machine.getBlockState(), player,
                new ItemStack(NativeHarmonyFixtures.HARMONY.getBlock()));
        helper.assertTrue(player.getUUID().equals(machine.getWirelessOwner()), "Native placement owner was not used for the harmony wireless account");
        machine.getTrait(ProgrammableCircuitSlotTrait.class).setCurrentCircuit(circuit);
        machine.checkAndFormStructure(); helper.assertTrue(machine.isFormed(), "Native harmony controller did not form");
        var fluids = machine.getTrait(NotifiableFluidTank.class).getStorages();
        fluids[0].setFluid(GTMaterials.Hydrogen.getFluid(1_200_000_000)); fluids[1].setFluid(GTMaterials.Helium.getFluid(1_200_000_000));
        return machine;
    }
    private static GameTestSequence stocked(GameTestHelper helper, HarmonyMachine machine) {
        return helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(machine.getStoredHydrogen() == 1_200_000_000L && machine.getStoredHelium() == 1_200_000_000L,
                    "Native subscribed startup tick did not move both physical gases into stored long quantities");
        });
    }
    private static NotifiableItemStackHandler items(HarmonyMachine machine, IO io) {
        return machine.getTraits(NotifiableItemStackHandler.class).stream().filter(value -> value.getHandlerIO() == io).findFirst().orElseThrow();
    }

    @GameTest(template = "empty", batch = "gtl_native_harmony", timeoutTicks = 400)
    public static void nativeSearchAndRejectedStartsKeepGasesAndWirelessEnergyAndAcceptedCycleChargesOnce(GameTestHelper helper) {
        var machine = build(helper, 4); var account = WirelessEnergyContainer.getOrCreateContainer(machine.getWirelessOwner());
        var stored = BigInteger.TEN.pow(25); account.setStorage(stored);
        stocked(helper, machine).thenExecute(() -> {
            long cost = machine.getStartupEnergy(); helper.assertTrue(cost == 2_702_159_775_164_006_400L, "Native circuit four startup energy changed");
            var builder = GTLRecipeTypes.COSMOS_SIMULATION_RECIPES.recipeBuilder("native_harmony_start")
                    .inputItems(Items.IRON_INGOT).outputItems(Items.GOLD_INGOT).duration(4800); builder.tickInput.clear();
            var recipe = builder.build();
            for (int i = 0; i < 3; i++) {
                var modified = HarmonyMachine.recipeModifier(machine, recipe).apply(recipe);
                helper.assertTrue(modified != null && modified.duration == 300 && recipe.duration == 4800,
                        "Native harmony modifier changed its source or the original circuit duration");
            }
            helper.assertTrue(!machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe) && account.getStorage().equals(stored)
                    && machine.getStoredHydrogen() == 1_200_000_000L && machine.getStoredHelium() == 1_200_000_000L,
                    "Native recipe search or rejected input consumed startup gases/energy");
            items(machine, IO.IN).storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            account.setStorage(BigInteger.valueOf(cost - 1));
            helper.assertTrue(!machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe) && account.getStorage().equals(BigInteger.valueOf(cost - 1))
                    && items(machine, IO.IN).getStackInSlot(0).getCount() == 2 && machine.getStoredHydrogen() == 1_200_000_000L,
                    "Insufficient wireless startup energy partially consumed inputs or gas");
            var config = com.hepdd.gtmthings.config.ConfigHolder.INSTANCE; boolean oldRatePolicy = config.isWirelessRateEnable;
            account.setStorage(stored);
            items(machine, IO.OUT).storage.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 64));
            helper.assertTrue(!machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe) && account.getStorage().equals(stored)
                    && items(machine, IO.IN).getStackInSlot(0).getCount() == 2 && machine.getStoredHydrogen() == 1_200_000_000L,
                    "Blocked native recipe output consumed startup energy, gas or input");
            items(machine, IO.OUT).storage.setStackInSlot(0, ItemStack.EMPTY);
            try {
                config.isWirelessRateEnable = true; account.setRate(cost - 1);
                helper.assertTrue(!machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe) && account.getStorage().equals(stored)
                        && machine.getStoredHelium() == 1_200_000_000L, "Native wireless rate limit partially withdrew startup energy or gas");
                account.setRate(Long.MAX_VALUE);
                helper.assertTrue(machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe), "Funded native harmony recipe failed to start");
            } finally { config.isWirelessRateEnable = oldRatePolicy; }
            var logic = machine.getRecipeLogic(); logic.setSuspendAfterFinish(true);
            for (int tick = 0; tick < 300; tick++) logic.serverTick();
            helper.assertTrue(items(machine, IO.OUT).getStackInSlot(0).is(Items.GOLD_INGOT) && items(machine, IO.IN).getStackInSlot(0).getCount() == 1
                    && logic.getStatus() == RecipeLogic.Status.SUSPEND && account.getStorage().equals(stored.subtract(BigInteger.valueOf(cost)))
                    && machine.getStoredHydrogen() == 176_000_000L && machine.getStoredHelium() == 176_000_000L,
                    "Native harmony cycle changed output, exact startup energy/gas cost or pause state");
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_harmony", timeoutTicks = 400)
    public static void nativePlacementOwnerStickBindingAndSaveReloadPreserveBothLongStoresAndStopAfterInvalidation(GameTestHelper helper) {
        var machine = build(helper, 1);
        var fluids = machine.getTrait(NotifiableFluidTank.class).getStorages();
        stocked(helper, machine).thenExecute(() -> {
            helper.assertTrue(machine.getStartupEnergy() == 5_277_655_810_867_200L, "Native circuit one startup energy changed");
            var player = FakePlayerFactory.getMinecraft(helper.getLevel()); var stick = GTItems.TOOL_DATA_STICK.asStack();
            stick.set(DataComponents.CUSTOM_NAME, Component.literal("Harmony account binding")); player.setItemInHand(InteractionHand.MAIN_HAND, stick);
            var hit = new BlockHitResult(machine.getBlockPos().getCenter(), Direction.NORTH, machine.getBlockPos(), false);
            try {
                helper.assertTrue(machine.onUseWithItem(new ExtendedUseOnContext(player, InteractionHand.MAIN_HAND, hit)) == InteractionResult.CONSUME
                        && machine.getWirelessOwner().equals(player.getUUID()) && stick.has(DataComponents.CUSTOM_NAME),
                        "Native data stick did not bind the player account or lost its components");
            } finally { player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); }
            var sync = new brachy.modularui.value.sync.ModularSyncManager(false);
            var manager = new brachy.modularui.value.sync.PanelSyncManager(sync, true);
            var settings = new brachy.modularui.screen.UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
            player.setPos(machine.getBlockPos().getCenter());
            var panel = com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory.INSTANCE.createPanel(
                    new brachy.modularui.factory.PosGuiData(player, machine.getBlockPos()), manager, settings);
            brachy.modularui.widget.WidgetTree.collectSyncValues(manager, panel);
            var text = manager.findSyncHandler("harmonyText", 0, brachy.modularui.value.sync.StringSyncValue.class);
            text.updateCacheFromSource(true);
            helper.assertTrue(text.getStringValue().contains("tooltip.gtlcore.hydrogen_storage")
                    && text.getStringValue().contains(com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(1_200_000_000L)),
                    "Actual native harmony menu lost its server-owned gas display serialization");
            var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
            var copy = (HarmonyMachine) NativeHarmonyFixtures.HARMONY.getBlockEntityType().create(machine.getBlockPos(), machine.getBlockState());
            copy.loadAdditional(saved, helper.getLevel().registryAccess());
            helper.assertTrue(copy.getStoredHydrogen() == 1_200_000_000L && copy.getStoredHelium() == 1_200_000_000L
                    && copy.getWirelessOwner().equals(player.getUUID()) && copy.getStartupEnergy() == machine.getStartupEnergy(),
                    "Native harmony SaveField round trip lost stores, overclock or owner");
            saved.putLong("hydrogen", 9_007_199_254_740_993L); saved.putLong("helium", Long.MAX_VALUE - 5);
            copy.loadAdditional(saved, helper.getLevel().registryAccess());
            var large = copy.saveWithoutMetadata(helper.getLevel().registryAccess());
            helper.assertTrue(copy.getStoredHydrogen() == 9_007_199_254_740_993L && copy.getStoredHelium() == Long.MAX_VALUE - 5
                    && large.getLong("hydrogen") == 9_007_199_254_740_993L && large.getLong("helium") == Long.MAX_VALUE - 5,
                    "Native harmony persistence truncated stored gas above int/double precision");
            helper.setBlock(new BlockPos(4, 3, 3), Blocks.AIR); machine.invalidateStructure("main");
            fluids[0].setFluid(GTMaterials.Hydrogen.getFluid(100_000_000)); fluids[1].setFluid(GTMaterials.Helium.getFluid(100_000_000));
        }).thenExecuteAfter(25, () -> {
            helper.assertTrue(machine.getStoredHydrogen() == 1_200_000_000L && fluids[0].getFluidAmount() == 100_000_000
                    && fluids[1].getFluidAmount() == 100_000_000, "Invalidated native harmony continued consuming startup gas");
        }).thenSucceed();
    }
}
