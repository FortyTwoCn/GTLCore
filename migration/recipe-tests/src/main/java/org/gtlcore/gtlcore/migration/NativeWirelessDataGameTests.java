package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.WirelessOpticalDataHatchMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.DataBankMachine;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeWirelessDataGameTests {
    private static WorkableElectricMultiblockMachine form(GameTestHelper helper, MachineEntry<MultiblockMachineDefinition> entry, BlockPos origin) {
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap(Map.of(0, 1)))
                .populate(blocks, entry.get().getStructurePatterns().get("main").get(), null, Direction.NORTH, Direction.UP, false);
        BlockPos controller = null;
        for (var block : blocks.entrySet()) {
            var pos = origin.offset(block.getKey()); helper.setBlock(pos, block.getValue().getBlockState());
            if (block.getValue().getBlockState().is(entry.getBlock())) controller = pos;
        }
        var machine = (WorkableElectricMultiblockMachine) helper.getBlockEntity(Objects.requireNonNull(controller));
        machine.checkAndFormStructure(); helper.assertTrue(machine.isFormed(), "Native wireless data fixture did not form: " + entry.getId());
        return machine;
    }
    private static WirelessOpticalDataHatchMachine hatch(WorkableElectricMultiblockMachine controller, boolean transmitter) {
        return controller.getParts().stream().filter(WirelessOpticalDataHatchMachine.class::isInstance)
                .map(WirelessOpticalDataHatchMachine.class::cast).filter(part -> part.isTransmitter() == transmitter).findFirst().orElseThrow();
    }
    private static void click(Player player, WirelessOpticalDataHatchMachine machine) {
        var hit = new BlockHitResult(Vec3.atCenterOf(machine.getBlockPos()), Direction.NORTH, machine.getBlockPos(), false);
        machine.onUse(new ExtendedUseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }
    private static ItemStack stick(Player player) {
        var stack = GTItems.TOOL_DATA_STICK.asStack();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Retained research stick"));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("unrelated", "retained"));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack); return stack;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void bothBindingOrdersKeepComponentsAndRestoreNativeLinkCoordinates(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 3, 4), NativeWirelessDataFixtures.TX.getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(6, 3, 4), NativeWirelessDataFixtures.RX.getBlock().defaultBlockState());
        var tx = (WirelessOpticalDataHatchMachine) helper.getBlockEntity(new BlockPos(4, 3, 4));
        var rx = (WirelessOpticalDataHatchMachine) helper.getBlockEntity(new BlockPos(6, 3, 4));
        var player = helper.makeMockPlayer(GameType.SURVIVAL); var stack = stick(player);
        click(player, tx); helper.assertTrue(!tx.bind(), "Selecting a transmitter falsely reported a completed binding");
        click(player, rx);
        helper.assertTrue(rx.getBlockPos().equals(tx.getReceiverPos()) && tx.getBlockPos().equals(rx.getTransmitterPos()), "Transmitter-first binding stored a local rather than remote coordinate");
        var tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        helper.assertTrue(tag.getString("unrelated").equals("retained") && !tag.contains("transmitterPos") && !tag.contains("receiverPos") && stack.has(DataComponents.CUSTOM_NAME), "Binding removed unrelated stick components or kept completed endpoint selection");
        var level = helper.getLevel(); var saved = rx.saveWithoutMetadata(level.registryAccess());
        var pos = rx.getBlockPos(); var state = rx.getBlockState(); level.removeBlockEntity(pos);
        var restored = (WirelessOpticalDataHatchMachine) NativeWirelessDataFixtures.RX.getBlockEntityType().create(pos, state);
        level.setBlockEntity(restored); restored.loadAdditional(saved, level.registryAccess());
        helper.assertTrue(restored.bind() && tx.getBlockPos().equals(restored.getTransmitterPos()), "Native SaveField reload lost the receiver binding");
        tx.setReceiverPos(null); restored.setTransmitterPos(null); stick(player);
        click(player, restored); helper.assertTrue(!restored.bind(), "Selecting a receiver falsely reported a completed binding");
        click(player, tx);
        helper.assertTrue(restored.getBlockPos().equals(tx.getReceiverPos()) && tx.getBlockPos().equals(restored.getTransmitterPos()), "Receiver-first binding stored the transmitter's own coordinate");
        try {
            var provider = new org.gtlcore.gtlcore.integration.jade.provider.WirelessOpticalDataHatchProvide();
            var writer = provider.getClass().getDeclaredMethod("write", CompoundTag.class, org.gtlcore.gtlcore.api.capability.BindCapability.class);
            writer.setAccessible(true); var data = new CompoundTag(); writer.invoke(provider, data, tx);
            helper.assertTrue(data.getBoolean("is_bind") && data.getString("pos").equals(restored.getBlockPos().toShortString()), "Native Jade provider lost the real linked endpoint");
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void poweredNativeDataBankRoutesResearchAndRefusesUnloadedOrOtherDimensionEndpoints(GameTestHelper helper) {
        var bank = (DataBankMachine) form(helper, NativeWirelessDataFixtures.BANK, new BlockPos(4, 3, 4));
        var consumer = form(helper, NativeWirelessDataFixtures.RECEIVING, new BlockPos(4, 3, 9));
        var tx = hatch(bank, true); var rx = hatch(consumer, false);
        var player = helper.makeMockPlayer(GameType.SURVIVAL); stick(player); click(player, tx); click(player, rx);
        var recipe = GTRecipeTypes.DUMMY_RECIPES.recipeBuilder("native_wireless_research_route").duration(100).build();
        var power = bank.getTraitsFromParts(NotifiableEnergyContainer.class).getFirst();
        helper.assertTrue(!rx.isRecipeAvailable(recipe), "Unpowered data bank supplied research");
        power.changeEnergy(power.getEnergyCapacity()); long before = power.getEnergyStored(); bank.tick();
        helper.assertTrue(power.getEnergyStored() == before - bank.getEnergyUsage() && rx.isRecipeAvailable(recipe), "Powered native data bank did not consume exact energy and expose its physical data hatch");
        power.changeEnergy(-power.getEnergyStored()); bank.tick();
        helper.assertTrue(!rx.isRecipeAvailable(recipe), "Power loss left wireless research available");
        power.changeEnergy(power.getEnergyCapacity()); bank.tick();
        helper.assertTrue(rx.isRecipeAvailable(recipe), "Native data bank power recovery did not restore research");
        tx.setReceiverPos(null); rx.setTransmitterPos(null);
        var stack = stick(player);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            var remote = new CompoundTag(); remote.putInt("x", rx.getBlockPos().getX()); remote.putInt("y", rx.getBlockPos().getY()); remote.putInt("z", rx.getBlockPos().getZ());
            tag.put("receiverPos", remote); tag.putString("receiverDimension", "minecraft:the_nether");
        });
        click(player, tx); helper.assertTrue(!tx.bind() && !rx.bind(), "Different-dimension selection bound an unrelated local hatch");
        var far = new BlockPos(10_000_000, 64, 10_000_000);
        helper.assertTrue(!helper.getLevel().hasChunkAt(far), "Remote test chunk was already loaded");
        rx.setTransmitterPos(far); helper.assertTrue(!rx.isRecipeAvailable(recipe) && !helper.getLevel().hasChunkAt(far), "Research lookup forced an unloaded remote chunk");
        helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeSeenSetTerminatesPoweredWirelessResearchCycles(GameTestHelper helper) {
        var first = (DataBankMachine) form(helper, NativeWirelessDataFixtures.CHAIN, new BlockPos(4, 3, 4));
        var second = (DataBankMachine) form(helper, NativeWirelessDataFixtures.CHAIN, new BlockPos(4, 3, 9));
        var firstRx = hatch(first, false); var secondRx = hatch(second, false);
        firstRx.setTransmitterPos(hatch(second, true).getBlockPos()); secondRx.setTransmitterPos(hatch(first, true).getBlockPos());
        for (var bank : List.of(first, second)) {
            var power = bank.getTraitsFromParts(NotifiableEnergyContainer.class).getFirst(); power.changeEnergy(power.getEnergyCapacity()); bank.tick();
            helper.assertTrue(bank.getRecipeLogic().isWorking(), "Cyclic data bank did not receive physical native power");
        }
        var recipe = GTRecipeTypes.DUMMY_RECIPES.recipeBuilder("native_wireless_research_cycle").duration(100).build();
        helper.assertTrue(!firstRx.isRecipeAvailable(recipe), "Research cycle falsely supplied an unresearched recipe");
        first.invalidateStructure("main"); helper.assertTrue(!firstRx.isRecipeAvailable(recipe), "Invalidated data bank retained a research route");
        helper.succeed();
    }
}
