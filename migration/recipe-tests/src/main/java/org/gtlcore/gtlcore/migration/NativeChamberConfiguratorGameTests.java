package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberConfigurator;
import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberConfigurator.Kind;
import org.gtlcore.gtlcore.common.machine.multiblock.part.TagFilterMEStockBusPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.TagFilterMEStockHatchPartMachine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import io.netty.buffer.Unpooled;
import java.util.function.Consumer;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeChamberConfiguratorGameTests {
    private static void command(MetaMachine machine, Kind kind, Consumer<FriendlyByteBuf> write) {
        action(machine, kind, MEChamberConfigurator.ROOT_ACTION_ID, write);
    }
    private static void action(MetaMachine machine, Kind kind, int id, Consumer<FriendlyByteBuf> write) {
        var payload = new FriendlyByteBuf(Unpooled.buffer());
        try {
            write.accept(payload);
            MEChamberConfigurator.handleClientAction(machine, kind, id, payload);
        } finally {
            payload.release();
        }
    }
    private static void rejected(GameTestHelper helper, MetaMachine machine, Kind kind, int id,
                                 Consumer<FriendlyByteBuf> write) {
        boolean failed = false;
        try { action(machine, kind, id, write); }
        catch (IllegalArgumentException | IndexOutOfBoundsException expected) { failed = true; }
        helper.assertTrue(failed, "Malformed remote configuration was accepted");
    }
    private static ItemStack circuit(MetaMachine machine) {
        return machine.getTraitOrThrow(ProgrammableCircuitSlotTrait.class).storage.getStackInSlot(0);
    }
    private static MetaMachine reload(GameTestHelper helper, MetaMachine machine) {
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        var restored = (MetaMachine) machine.getType().create(machine.getBlockPos(), machine.getBlockState());
        helper.getLevel().removeBlockEntity(machine.getBlockPos());
        restored.loadAdditional(saved, helper.getLevel().registryAccess());
        helper.getLevel().setBlockEntity(restored);
        return restored;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks")
    public static void nativeChamberCircuitCommandsEditSeparateSlotsAndPersistOnBothOriginalGTHatches(GameTestHelper helper) {
        for (boolean fluid : new boolean[]{false, true}) {
            var position = new BlockPos(fluid ? 6 : 4, 4, 4);
            helper.setBlock(position, (fluid ? GTMachines.FLUID_IMPORT_HATCH[GTValues.LV] :
                    GTMachines.ITEM_IMPORT_BUS[GTValues.LV]).getBlock());
            var machine = (MetaMachine) helper.getBlockEntity(position);
            var input = new ItemStack(Items.IRON_INGOT, 7);
            input.set(DataComponents.CUSTOM_NAME, Component.literal("physical input survives remote configuration"));
            if (machine instanceof ItemBusPartMachine bus) bus.getInventory().setStackInSlot(0, input);
            command(machine, Kind.CIRCUIT, b -> b.writeVarInt(0));
            helper.assertTrue(!circuit(machine).isEmpty() && IntCircuitBehaviour.getCircuitConfiguration(circuit(machine)) == 0,
                    "Configured circuit zero became an empty circuit slot");
            command(machine, Kind.CIRCUIT, b -> b.writeVarInt(32));
            var expected = circuit(machine).copy();
            rejected(helper, machine, Kind.CIRCUIT, 1, b -> b.writeVarInt(33));
            rejected(helper, machine, Kind.CIRCUIT, 1, b -> b.writeVarInt(-2));
            rejected(helper, machine, Kind.CIRCUIT, 1, b -> b.writeVarInt(0).writeByte(1));
            rejected(helper, machine, Kind.CIRCUIT, 1, b -> b.writeByte(0x80));
            rejected(helper, machine, Kind.CIRCUIT, 1, b -> b.writeBytes(new byte[]{-1, -1, -1, -1, -1, -1}));
            rejected(helper, machine, Kind.CIRCUIT, 2, b -> b.writeVarInt(0));
            rejected(helper, machine, Kind.CIRCUIT, 1, b -> b.writeZero(4097));
            rejected(helper, machine, Kind.TAG_FILTER, 1, b -> b.writeBoolean(true).writeUtf("c:ingots/*"));
            helper.assertTrue(ItemStack.matches(expected, circuit(machine)), "Rejected payload mutated the actual circuit");
            var restored = reload(helper, machine);
            helper.assertTrue(ItemStack.matches(expected, circuit(restored)), "Native circuit trait save/load lost remote selection");
            if (restored instanceof ItemBusPartMachine bus) helper.assertTrue(ItemStack.matches(input, bus.getInventory().getStackInSlot(0)),
                    "Remote circuit configuration or reload replaced physical input components");
            command(restored, Kind.CIRCUIT, b -> b.writeVarInt(-1));
            helper.assertTrue(circuit(restored).isEmpty() && circuit(reload(helper, restored)).isEmpty(),
                    "Clearing a remote circuit did not persist as an empty slot");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks")
    public static void nativeChamberTagAndRefreshCommandsValidateBeforeMutationAndRestoreBothProductionStockParts(GameTestHelper helper) {
        for (boolean fluid : new boolean[]{false, true}) {
            var position = new BlockPos(fluid ? 6 : 4, 4, 4);
            helper.setBlock(position, (fluid ? NativeAeGridFixtures.TAG_STOCK_FLUID : NativeAeGridFixtures.TAG_STOCK_ITEM).getBlock());
            var machine = (MetaMachine) helper.getBlockEntity(position);
            command(machine, Kind.SYNC_OFFSET, b -> b.writeVarInt(0));
            command(machine, Kind.SYNC_OFFSET, b -> b.writeVarInt(Integer.MAX_VALUE));
            command(machine, Kind.TAG_FILTER, b -> b.writeBoolean(true).writeUtf("c:ingots/* & !c:ingots/gold"));
            String blacklist = "过滤".repeat(128);
            command(machine, Kind.TAG_FILTER, b -> b.writeBoolean(false).writeUtf(blacklist));
            rejected(helper, machine, Kind.SYNC_OFFSET, 1, b -> b.writeVarInt(-1));
            rejected(helper, machine, Kind.SYNC_OFFSET, 1, b -> b.writeVarInt(1).writeByte(0));
            rejected(helper, machine, Kind.SYNC_OFFSET, 1, b -> b.writeByte(0x80));
            rejected(helper, machine, Kind.TAG_FILTER, 1, b -> b.writeBoolean(true).writeUtf("a".repeat(257)));
            rejected(helper, machine, Kind.TAG_FILTER, 1, b -> b.writeBoolean(true).writeUtf("changed").writeByte(1));
            rejected(helper, machine, Kind.TAG_FILTER, 1, b -> b.writeBoolean(false).writeVarInt(8));
            rejected(helper, machine, Kind.TAG_FILTER, 1, b -> {});
            checkSettings(helper, machine, blacklist);
            var restored = reload(helper, machine);
            checkSettings(helper, restored, blacklist);
            command(restored, Kind.TAG_FILTER, b -> b.writeBoolean(false).writeUtf(""));
            String empty = restored instanceof TagFilterMEStockBusPartMachine bus ? bus.getTagBlack() :
                    ((TagFilterMEStockHatchPartMachine) restored).getTagBlack();
            helper.assertTrue(empty.isEmpty(), "A remote blacklist could not be cleared");
        }
        helper.succeed();
    }

    private static void checkSettings(GameTestHelper helper, MetaMachine machine, String blacklist) {
        String white, black; int interval;
        if (machine instanceof TagFilterMEStockBusPartMachine bus) {
            white = bus.getTagWhite(); black = bus.getTagBlack(); interval = bus.getMEUpdateInterval();
        } else {
            var hatch = (TagFilterMEStockHatchPartMachine) machine;
            white = hatch.getTagWhite(); black = hatch.getTagBlack(); interval = hatch.getMEUpdateInterval();
        }
        helper.assertTrue(white.equals("c:ingots/* & !c:ingots/gold") && black.equals(blacklist) && interval == Integer.MAX_VALUE,
                "Invalid payload or native save/load corrupted authoritative tags or refresh interval");
    }
}
