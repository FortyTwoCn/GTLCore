package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.NeutronAcceleratorPartMachine;
import org.gtlcore.gtlcore.integration.gtmt.NativeWirelessMonitorRows;
import org.gtlcore.gtlcore.integration.gtmt.WirelessEnergyMonitorSnapshot;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.hepdd.gtmthings.api.misc.ITransferData;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import com.hepdd.gtmthings.common.block.machine.electric.WirelessEnergyMonitor;
import com.hepdd.gtmthings.common.cover.WirelessEnergyReceiveCover;
import com.hepdd.gtmthings.config.ConfigHolder;
import com.hepdd.gtmthings.data.CustomItems;
import com.hepdd.gtmthings.data.GTMTCovers;
import com.hepdd.gtmthings.data.WirelessMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.UUID;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeWirelessEnergyGameTests {
    private static NeutronAcceleratorPartMachine accelerator(GameTestHelper helper) {
        var pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, NativeMachineFixtures.NEUTRON_ACCELERATOR.getBlock());
        var machine = (NeutronAcceleratorPartMachine) helper.getBlockEntity(pos);
        machine.setFrontFacing(Direction.NORTH);
        machine.setOwnerUUID(UUID.randomUUID());
        return machine;
    }

    private static WirelessEnergyReceiveCover attach(GameTestHelper helper, NeutronAcceleratorPartMachine machine) {
        var item = CustomItems.WIRELESS_ENERGY_RECEIVE_COVER_HV.asStack();
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Native neutron wireless cover"));
        helper.assertTrue(machine.getCoverContainer().placeCoverOnSide(Direction.NORTH, item,
                GTMTCovers.WIRELESS_ENERGY_RECEIVE[GTValues.HV - 1].value(), null), "Native wireless interface did not allow the neutron accelerator");
        return (WirelessEnergyReceiveCover) machine.getCoverContainer().getCoverAtSide(Direction.NORTH);
    }

    private static void tick(WirelessEnergyReceiveCover cover) {
        try {
            var method = WirelessEnergyReceiveCover.class.getDeclaredMethod("updateEnergy");
            method.setAccessible(true);
            method.invoke(cover);
        } catch (ReflectiveOperationException exception) { throw new AssertionError("Native wireless tick failed", exception); }
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeNeutronWirelessChargesOnlyAcceptedEnergyAndRecordsTheActualRateLimitedTransfer(GameTestHelper helper) {
        var machine = accelerator(helper);
        var cover = attach(helper, machine);
        var account = cover.getWirelessEnergyContainer();
        boolean oldRateMode = ConfigHolder.INSTANCE.isWirelessRateEnable;
        try {
            ConfigHolder.INSTANCE.isWirelessRateEnable = true;
            account.setStorage(BigInteger.valueOf(1000)); account.setRate(13);
            machine.energyContainer.setEnergyStored(machine.energyContainer.getEnergyCapacity() - 20);
            long before = machine.energyContainer.getEnergyStored();
            tick(cover);
            helper.assertTrue(machine.energyContainer.getEnergyStored() == before + 13 && account.getStorage().equals(BigInteger.valueOf(987)),
                    "Native wireless cover charged requested voltage instead of the thirteen accepted EU");
            var recorded = WirelessEnergyMonitorSnapshot.drain().entries().stream()
                    .filter(entry -> entry.getKey().getSecond() == machine).findFirst().orElseThrow();
            helper.assertTrue(recorded.getValue() == -13, "Core monitor recorded requested energy instead of the native transfer result");
            tick(cover);
            helper.assertTrue(machine.energyContainer.getEnergyStored() == before + 13 && account.getStorage().equals(BigInteger.valueOf(987)),
                    "Rejected second packet in one tick debited the wireless account");
            machine.setWorkingEnabled(false);
            tick(cover);
            helper.assertTrue(account.getStorage().equals(BigInteger.valueOf(987)), "Disabled neutron accelerator still charged wirelessly");
            helper.assertTrue(!machine.energyContainer.inputsEnergy(Direction.EAST), "Native wireless support opened an external side input");
        } finally { ConfigHolder.INSTANCE.isWirelessRateEnable = oldRateMode; }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeNeutronWirelessSubscriptionStopsWhileDisabledRestoresOnNewWorldReloadAndStopsOnCoverRemoval(GameTestHelper helper) {
        var machine = accelerator(helper);
        machine.setWorkingEnabled(false);
        var cover = attach(helper, machine);
        var account = cover.getWirelessEnergyContainer();
        var originalStorage = BigInteger.valueOf(100_000);
        account.setStorage(originalStorage);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(machine.energyContainer.getEnergyStored() == 0 && account.getStorage().equals(originalStorage),
                    "Native cover subscription charged a disabled accelerator");
            machine.setWorkingEnabled(true);
            helper.runAfterDelay(3, () -> {
                long capacity = machine.energyContainer.getEnergyCapacity();
                helper.assertTrue(machine.energyContainer.getEnergyStored() == capacity &&
                        account.getStorage().equals(originalStorage.subtract(BigInteger.valueOf(capacity))), "Native wireless ticks did not fill the original neutron capacity exactly");
                var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
                var copy = (NeutronAcceleratorPartMachine) NativeMachineFixtures.NEUTRON_ACCELERATOR.getBlockEntityType()
                        .create(machine.getBlockPos(), machine.getBlockState());
                helper.getLevel().removeBlockEntity(machine.getBlockPos());
                copy.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(copy);
                helper.assertTrue(copy.getOwnerUUID().equals(machine.getOwnerUUID()) &&
                        cover.getPickItem().get(DataComponents.CUSTOM_NAME).equals(copy.getCoverContainer().getCoverAtSide(Direction.NORTH)
                                .getPickItem().get(DataComponents.CUSTOM_NAME)), "Native wireless cover reload lost ownership or item components");
                copy.energyContainer.setEnergyStored(0);
                helper.runAfterDelay(3, () -> {
                    helper.assertTrue(copy.energyContainer.getEnergyStored() == capacity &&
                            account.getStorage().equals(originalStorage.subtract(BigInteger.valueOf(capacity * 2))), "Recreated native wireless cover failed to resume charging");
                    helper.assertTrue(copy.getCoverContainer().removeCover(false, Direction.NORTH, null), "Native wireless cover removal failed");
                    copy.energyContainer.setEnergyStored(0);
                    var afterRemoval = account.getStorage();
                    helper.runAfterDelay(3, () -> {
                        helper.assertTrue(copy.energyContainer.getEnergyStored() == 0 && account.getStorage().equals(afterRemoval),
                                "Removed wireless cover retained its subscription");
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeWirelessMonitorBoundsSharedSnapshotsAndInvalidatesItsCacheWhenTheOwnerOrScopeChanges(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 1), WirelessMachines.WIRELESS_ENERGY_MONITOR.getBlock());
        var monitor = (WirelessEnergyMonitor) helper.getBlockEntity(new BlockPos(1, 2, 1));
        var owner = UUID.randomUUID(); var other = UUID.randomUUID(); monitor.setOwnerUUID(owner);
        var ownAccount = monitor.getWirelessEnergyContainer(); ownAccount.setStorage(BigInteger.TEN.pow(80));
        var otherAccount = WirelessEnergyContainer.getOrCreateContainer(other); otherAccount.setStorage(BigInteger.valueOf(987654321));
        var snapshot = new LinkedHashMap<MetaMachine, ITransferData>();
        for (int index = 0; index < 80; index++) {
            var pos = new BlockPos(2 + index % 10, 2, 2 + index / 10);
            helper.setBlock(pos, GTMachines.HULL[GTValues.HV].getBlock());
            var source = (MetaMachine) helper.getBlockEntity(pos);
            var account = index < 70 ? ownAccount : otherAccount;
            account.addEnergy(1000 - index, source);
            snapshot.put(source, WirelessEnergyContainer.TRANSFER_DATA.get(source));
        }
        var oldSnapshot = WirelessEnergyContainer.TRANSFER_SNAPSHOT;
        WirelessEnergyContainer.TRANSFER_SNAPSHOT = snapshot;
        try {
            var own = NativeWirelessMonitorRows.transfers(snapshot, ownAccount.getUuid(), false);
            helper.assertTrue(own.entries().size() == 64 && own.hidden() == 6 && own.entries().getFirst().Throughput() == 931,
                    "Native team monitor did not retain the smallest sixty-four sorted transfers");
            var all = NativeWirelessMonitorRows.transfers(snapshot, ownAccount.getUuid(), true);
            helper.assertTrue(all.entries().size() == 64 && all.hidden() == 16 && all.entries().getFirst().Throughput() == 921,
                    "Native all-account monitor exceeded its bounded list or lost its hidden count");
            var rows = monitor.getDisplayText(false, 220);
            for (var row : rows) helper.assertTrue(Component.Serializer.fromJson(
                    Component.Serializer.toJson(row, helper.getLevel().registryAccess()), helper.getLevel().registryAccess()) != null,
                    "Native wireless monitor produced a component that cannot cross its registry-aware UI channel");
            helper.assertTrue(rows.stream().anyMatch(row -> row.getContents() instanceof TranslatableContents text &&
                    text.getKey().endsWith("hidden_entries")), "Actual native monitor default-method override did not show its overflow row");
            ownAccount.setStorage(BigInteger.ZERO);
            helper.assertTrue(monitor.getDisplayText(false, 220) == rows, "Native monitor rebuilt a cached list on every UI poll");
            helper.assertTrue(monitor.getDisplayText(true, 220) != rows, "Native synchronized scope reused a stale team list");
            monitor.setOwnerUUID(other);
            var otherRows = monitor.getDisplayText(false, 220);
            helper.assertTrue(otherRows != rows && monitor.getWirelessEnergyContainer() == otherAccount &&
                    otherRows.stream().noneMatch(row -> row.getContents() instanceof TranslatableContents text && text.getKey().endsWith("hidden_entries")),
                    "Native owner change leaked the previous team's list or account");
            helper.assertTrue(snapshot.size() == 80 && oldSnapshot != snapshot, "Reading one native monitor drained the shared snapshot");
            helper.succeed();
        } finally { WirelessEnergyContainer.TRANSFER_SNAPSHOT = oldSnapshot; }
    }
}
