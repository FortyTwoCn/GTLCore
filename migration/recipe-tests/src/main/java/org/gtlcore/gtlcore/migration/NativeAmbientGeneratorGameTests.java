package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.generator.LightningRodMachine;
import org.gtlcore.gtlcore.common.machine.generator.MagicEnergyMachine;
import com.gregtechceu.gtceu.api.GTValues;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeAmbientGeneratorGameTests {
    private static void poweredRod(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.LIGHTNING_ROD.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP).setValue(LightningRodBlock.POWERED, true));
    }
    private static EndCrystal crystal(GameTestHelper helper, MagicEnergyMachine machine) {
        var pos = machine.getBlockPos();
        var crystal = new EndCrystal(helper.getLevel(), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        helper.getLevel().addFreshEntity(crystal); return crystal;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 120)
    public static void nativeCrystalTicksRestoreStoredEnergyAndResumeAfterEntityReplacement(GameTestHelper helper) {
        var relative = new BlockPos(4, 3, 4);
        helper.setBlock(relative, NativeAmbientGeneratorFixtures.MAGIC.getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(8, 3, 4), NativeAmbientGeneratorFixtures.ULV_MAGIC.getBlock().defaultBlockState());
        var holder = new MagicEnergyMachine[] { (MagicEnergyMachine) helper.getBlockEntity(relative) };
        var ulv = (MagicEnergyMachine) helper.getBlockEntity(new BlockPos(8, 3, 4));
        helper.assertTrue(ulv.energyContainer.getEnergyCapacity() == GTValues.V[GTValues.ULV] * 512 &&
                ulv.energyContainer.getOutputVoltage() == GTValues.V[GTValues.ULV] && ulv.energyContainer.getOutputAmperage() == 16,
                "Native ULV magic generator lost its original energy limits");
        long pulse = GTValues.V[GTValues.LV + 4];
        var crystals = new EndCrystal[] { crystal(helper, holder[0]) };
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(holder[0].energyContainer.getEnergyStored() > 0, "Native world ticks did not generate crystal energy"))
                .thenExecute(() -> {
                    var original = holder[0]; crystals[0].discard();
                    helper.assertTrue(original.energyContainer.getEnergyStored() == pulse && original.energyContainer.getEnergyCapacity() == GTValues.V[GTValues.LV] * 512,
                            "Magic generator lost its exact first pulse or capacity");
                    var level = helper.getLevel(); var pos = original.getBlockPos(); var state = original.getBlockState();
                    var saved = original.saveWithoutMetadata(level.registryAccess()); original.onUnload(); level.removeBlockEntity(pos);
                    var restored = (MagicEnergyMachine) NativeAmbientGeneratorFixtures.MAGIC.getBlockEntityType().create(pos, state);
                    level.setBlockEntity(restored); restored.loadAdditional(saved, level.registryAccess()); holder[0] = restored;
                    helper.assertTrue(restored.energyContainer.getEnergyStored() == pulse, "Native energy SaveField reload lost the stored pulse");
                }).thenIdle(25).thenExecute(() -> {
                    helper.assertTrue(holder[0].energyContainer.getEnergyStored() == pulse, "Removed crystal or unloaded subscription kept generating energy");
                    crystals[0] = crystal(helper, holder[0]);
                }).thenWaitUntil(() -> helper.assertTrue(holder[0].energyContainer.getEnergyStored() > pulse, "Recreated native generator did not resubscribe"))
                .thenExecute(() -> {
                    helper.assertTrue(holder[0].energyContainer.getEnergyStored() == pulse * 2, "Recreated generator duplicated subscriptions or lost exact pulse energy");
                    crystals[0].discard();
                }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativePoweredRodProducesExactQuarterChargeWithOriginalEmitterLimits(GameTestHelper helper) {
        var relative = new BlockPos(4, 3, 4); var rod = relative.above();
        helper.setBlock(relative, NativeAmbientGeneratorFixtures.LIGHTNING.getBlock().defaultBlockState());
        var machine = (LightningRodMachine) helper.getBlockEntity(relative);
        long charge = 48828L * 256;
        helper.assertTrue(machine.energyContainer.getEnergyCapacity() == charge && machine.energyContainer.getOutputVoltage() == GTValues.V[GTValues.HV] &&
                machine.energyContainer.getOutputAmperage() == 512 && machine.energyContainer.getInputVoltage() == 0,
                "Native lightning generator lost its capacity, voltage or emitter limits");
        helper.startSequence().thenWaitUntil(() -> {
            if (machine.energyContainer.getEnergyStored() == 0) poweredRod(helper, rod);
            helper.assertTrue(machine.energyContainer.getEnergyStored() > 0, "Native powered rod did not generate energy");
        }).thenExecute(() -> {
            helper.assertTrue(machine.energyContainer.getEnergyStored() == charge / 4, "Native lightning pulse did not produce exactly a quarter charge");
            helper.setBlock(rod, Blocks.AIR);
        }).thenIdle(20).thenExecute(() -> helper.assertTrue(machine.energyContainer.getEnergyStored() == charge / 4,
                "Removed rod kept generating energy")).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeOverchargedRodRequestsExplosionAndDestroysItsMachine(GameTestHelper helper) {
        var relative = new BlockPos(4, 3, 4); var rod = relative.above();
        helper.setBlock(relative, NativeAmbientGeneratorFixtures.LIGHTNING.getBlock().defaultBlockState());
        var machine = (LightningRodMachine) helper.getBlockEntity(relative);
        machine.energyContainer.changeEnergy(machine.energyContainer.getEnergyCapacity());
        var center = Vec3.atCenterOf(machine.getBlockPos()); var explosions = new AtomicInteger();
        Consumer<ExplosionEvent.Start> listener = event -> {
            if (event.getLevel() == helper.getLevel() && event.getExplosion().center().equals(center)) {
                event.setCanceled(true); explosions.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        helper.startSequence().thenWaitUntil(() -> {
            if (explosions.get() == 0) poweredRod(helper, rod);
            helper.assertTrue(explosions.get() > 0, "Overcharged native rod did not request an actual explosion");
        }).thenExecute(() -> {
            NeoForge.EVENT_BUS.unregister(listener);
            helper.assertTrue(helper.getLevel().getBlockState(machine.getBlockPos()).isAir() && explosions.get() == 1,
                    "Native overcharge did not destroy the machine once before its explosion");
        }).thenSucceed();
    }
}
