package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.item.ConfigurationCopyBehavior;
import org.gtlcore.gtlcore.common.machine.trait.QuantumChestLongStorage;
import org.gtlcore.gtlcore.mixin.gtm.machine.QuantumChestMachineAccessor;
import org.gtlcore.gtlcore.mixin.gtm.machine.QuantumTankMachineAccessor;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.machine.storage.QuantumChestMachine;
import com.gregtechceu.gtceu.common.machine.storage.QuantumTankMachine;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.mojang.authlib.GameProfile;
import java.util.UUID;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeConfigurationGameTests {
    private static FakePlayer player(GameTestHelper helper, String name) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "config_" + name));
        player.getInventory().clearContent(); player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE)); return player;
    }
    private static MetaMachine place(GameTestHelper helper, int x, MachineEntry<MachineDefinition> definition) {
        var pos = new BlockPos(x, 2, 4); helper.setBlock(pos, definition.getBlock());
        var machine = (MetaMachine) helper.getBlockEntity(pos); machine.setFrontFacing(Direction.NORTH); return machine;
    }
    private static void use(FakePlayer player, MetaMachine machine, boolean copy) {
        player.setShiftKeyDown(copy);
        var context = new UseOnContext(player, InteractionHand.OFF_HAND,
                new BlockHitResult(machine.getBlockPos().getCenter(), Direction.UP, machine.getBlockPos(), false));
        ConfigurationCopyBehavior.INSTANCE.onItemUseFirst(player.getOffhandItem(), context);
    }
    private static void reloadItem(GameTestHelper helper, FakePlayer player) {
        var registries = helper.getLevel().registryAccess();
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.parse(registries, player.getOffhandItem().save(registries)).orElseThrow());
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void simpleMachineCopyPersistsOffhandSettingsAndClearsAnOldCircuit(GameTestHelper helper) {
        var player = player(helper, "simple"); var source = place(helper, 3, NativeTerminalFixtures.SIMPLE);
        var target = place(helper, 6, NativeTerminalFixtures.SIMPLE);
        var output = source.getTrait(AutoOutputTrait.class); output.setItemOutputDirection(Direction.WEST); output.setFluidOutputDirection(Direction.EAST);
        output.setAllowAutoOutputItems(true); output.setAllowAutoOutputFluids(false);
        output.setAllowItemInputFromOutputSide(true); output.setAllowFluidInputFromOutputSide(true);
        source.getTrait(ProgrammableCircuitSlotTrait.class).setCurrentCircuit(19);
        var unrelated = new CompoundTag(); unrelated.putString("unrelated", "preserved");
        player.getOffhandItem().set(DataComponents.CUSTOM_DATA, CustomData.of(unrelated));
        use(player, source, true); reloadItem(helper, player); use(player, target, false);
        var pasted = target.getTrait(AutoOutputTrait.class);
        helper.assertTrue(pasted.isAutoOutputItems() && !pasted.isAutoOutputFluids() && pasted.getItemOutputDirection() == Direction.WEST && pasted.getFluidOutputDirection() == Direction.EAST && pasted.allowsItemInputFromOutputSide() && pasted.allowsFluidInputFromOutputSide(), "Native output traits were not copied");
        helper.assertTrue(target.getTrait(ProgrammableCircuitSlotTrait.class).getCurrentCircuit() == 19 && player.getMainHandItem().is(Items.APPLE) && "preserved".equals(player.getOffhandItem().get(DataComponents.CUSTOM_DATA).copyTag().getString("unrelated")), "Copy changed the other hand, unrelated item data or circuit");
        source.getTrait(ProgrammableCircuitSlotTrait.class).storage.setStackInSlot(0, ItemStack.EMPTY);
        use(player, source, true); use(player, target, false);
        helper.assertTrue(target.getTrait(ProgrammableCircuitSlotTrait.class).storage.getStackInSlot(0).isEmpty(), "Zero circuit did not clear the previous target setting"); helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void quantumChestCopyKeepsComponentSensitiveGhostLocks(GameTestHelper helper) {
        var player = player(helper, "chest"); var source = (QuantumChestMachine) place(helper, 3, NativeTerminalFixtures.QUANTUM_CHEST);
        var target = (QuantumChestMachine) place(helper, 6, NativeTerminalFixtures.QUANTUM_CHEST);
        var lock = new ItemStack(Items.DIAMOND); lock.set(DataComponents.CUSTOM_NAME, Component.literal("Native locked diamond"));
        ((QuantumChestMachineAccessor) source).gtlcore$getLockedInventory().setStackInSlot(0, lock);
        source.setVoiding(true); source.autoOutput.setItemOutputDirection(Direction.WEST); source.autoOutput.setAllowAutoOutputItems(true);
        use(player, source, true); reloadItem(helper, player); use(player, target, false);
        helper.assertTrue(ItemStack.matches(lock, target.getLockedItem()) && target.isVoiding() && target.autoOutput.isAutoOutputItems() && target.autoOutput.getItemOutputDirection() == Direction.WEST, "Quantum item lock lost native components or output settings");
        helper.assertTrue(!target.getItemHandlerCap(null, false).insertItem(0, new ItemStack(Items.DIAMOND), true).isEmpty(), "Native ghost lock admitted a different component key");
        ((QuantumChestMachineAccessor) source).gtlcore$getLockedInventory().setStackInSlot(0, ItemStack.EMPTY);
        use(player, source, true); use(player, target, false); helper.assertTrue(!target.isLocked(), "Copied unlocked chest retained the previous ghost lock"); helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void quantumTankCopyKeepsNativeFluidComponentsAndClearsLocks(GameTestHelper helper) {
        var player = player(helper, "tank"); var source = (QuantumTankMachine) place(helper, 3, NativeTerminalFixtures.QUANTUM_TANK);
        var target = (QuantumTankMachine) place(helper, 6, NativeTerminalFixtures.QUANTUM_TANK);
        var lock = new FluidStack(Fluids.WATER, 1); lock.set(DataComponents.CUSTOM_NAME, Component.literal("Native locked water"));
        ((QuantumTankMachineAccessor) source).gtlcore$setLockedFluid(lock);
        source.setVoiding(true); source.autoOutput.setFluidOutputDirection(Direction.EAST); source.autoOutput.setAllowAutoOutputFluids(true);
        use(player, source, true); reloadItem(helper, player); use(player, target, false);
        helper.assertTrue(FluidStack.isSameFluidSameComponents(lock, target.getLockedFluid()) && target.isVoiding() && target.autoOutput.isAutoOutputFluids() && target.autoOutput.getFluidOutputDirection() == Direction.EAST, "Quantum fluid lock lost native components or output settings");
        ((QuantumTankMachineAccessor) source).gtlcore$setLocked(false); use(player, source, true); use(player, target, false);
        helper.assertTrue(!target.isLocked(), "Copied unlocked tank retained the previous fluid lock"); helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void maxQuantumChestVoidingSaturatesWithoutLosingNativeSaveData(GameTestHelper helper) {
        var chest = (QuantumChestMachine) place(helper, 4, NativeTerminalFixtures.QUANTUM_CHEST);
        var handler = chest.getItemHandlerCap(null, false); handler.insertItem(0, new ItemStack(Items.DIAMOND), false);
        var storage = (QuantumChestLongStorage) chest; storage.gtlcore$setStoredAmount(Long.MAX_VALUE - 3); chest.setVoiding(true);
        handler.insertItem(0, new ItemStack(Items.DIAMOND, 64), true);
        helper.assertTrue(chest.getStoredAmount() == Long.MAX_VALUE - 3, "Simulated voiding mutated long storage");
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.DIAMOND, 64), false).isEmpty() && chest.getStoredAmount() == Long.MAX_VALUE && storage.gtlcore$getStorageCapacity() == Long.MAX_VALUE, "Voiding overflowed MAX quantum storage");
        var tag = chest.saveWithFullMetadata(helper.getLevel().registryAccess()); var pos = chest.getBlockPos();
        helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos, NativeTerminalFixtures.QUANTUM_CHEST.getBlock().defaultBlockState(), 3);
        var restored = (QuantumChestMachine) helper.getLevel().getBlockEntity(pos); restored.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored.getStoredAmount() == Long.MAX_VALUE && ((QuantumChestLongStorage) restored).gtlcore$getStoredStack().is(Items.DIAMOND), "Native SaveField lost the exact long amount or template");
        restored.getItemHandlerCap(null, false).extractItem(0, 7, false); helper.assertTrue(restored.getStoredAmount() == Long.MAX_VALUE - 7, "Native extraction corrupted the restored long count"); helper.succeed();
    }
}
