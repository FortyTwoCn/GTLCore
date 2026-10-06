package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.capability.IInt128EnergyContainer;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Real native energy traits with the production statistics mixin. */
class NativeEnergyStatisticsIntegrationTest {
    private static NotifiableEnergyContainer container() throws Exception {
        var definition = NativeMachineFixtures.STORAGE.get();
        var owner = new StorageMachine(new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.getBlock().defaultBlockState()), 1);
        var offset = MetaMachine.class.getDeclaredField("offset"); offset.setAccessible(true); offset.setLong(owner, 0);
        return owner.attachTrait(new NotifiableEnergyContainer(Long.MAX_VALUE, 32, 1, 0, 0));
    }
    @Test void repeatedLongTransfersRetainExactPositiveTotalsBeyondSignedLong() throws Exception {
        var nativeEnergy = container(); var precise = (IInt128EnergyContainer) nativeEnergy;
        for (int n = 0; n < 3; n++) { nativeEnergy.setEnergyStored(Long.MAX_VALUE); nativeEnergy.setEnergyStored(0); }
        nativeEnergy.updateTick(); String expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3)).toString();
        assertEquals(expected, precise.getInt128InputPerSec().toString());
        assertEquals(expected, precise.getInt128OutputPerSec().toString());
        assertEquals(Long.MAX_VALUE, nativeEnergy.getInputPerSec()); assertEquals(Long.MAX_VALUE, nativeEnergy.getOutputPerSec());
        precise.getInt128InputPerSec().set(0, 0);
        assertEquals(expected, precise.getInt128InputPerSec().toString());
        nativeEnergy.updateTick(); assertEquals("0", precise.getInt128InputPerSec().toString()); assertEquals("0", precise.getInt128OutputPerSec().toString());
    }
    @Test void minimumLongDeltaDoesNotWrapWhileConvertingOutputToPositiveMagnitude() throws Exception {
        var nativeEnergy = container(); var precise = (IInt128EnergyContainer) nativeEnergy;
        precise.addEnergyPerSec(Long.MIN_VALUE); nativeEnergy.updateTick();
        assertEquals(BigInteger.ONE.shiftLeft(63).toString(), precise.getInt128OutputPerSec().toString());
        assertEquals(Long.MAX_VALUE, nativeEnergy.getOutputPerSec()); assertEquals(0, nativeEnergy.getInputPerSec());
    }
    @Test void realStoredEnergyAndNativeChangeListenersRemainConsistent() throws Exception {
        var nativeEnergy = container(); var precise = (IInt128EnergyContainer) nativeEnergy;
        var changes = new AtomicInteger(); nativeEnergy.addChangedListener(changes::incrementAndGet);
        nativeEnergy.setEnergyStored(1); nativeEnergy.setEnergyStored(1); nativeEnergy.setEnergyStored(0);
        assertEquals(2, changes.get()); assertEquals("0", precise.getInt128EnergyStored().toString());
        assertEquals(Long.toString(Long.MAX_VALUE), precise.getInt128EnergyCapacity().toString());
        nativeEnergy.updateTick(); assertEquals(1, nativeEnergy.getInputPerSec()); assertEquals(1, nativeEnergy.getOutputPerSec());
    }
}
