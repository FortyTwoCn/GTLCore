package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.gtmt.InfinityEnergyContainer;
import org.gtlcore.gtlcore.integration.gtmt.InfinityLaserContainer;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.hepdd.gtmthings.data.CreativeMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeCreativeEnergyGameTests {
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeOriginalCreativeEnergyAndLaserHatchesSupplyRecipesAtMaximumVoltageWithoutDraining(GameTestHelper helper) {
        var entries = List.of(CreativeMachines.CREATIVE_ENERGY_INPUT_HATCH, CreativeMachines.CREATIVE_LASER_INPUT_HATCH);
        for (int index = 0; index < entries.size(); index++) {
            var pos = new BlockPos(2 + index * 2, 2, 3); helper.setBlock(pos, entries.get(index).getBlock());
            var machine = (MetaMachine) helper.getBlockEntity(pos);
            var container = machine.getTrait(NotifiableEnergyContainer.class);
            helper.assertTrue((index == 0 ? container instanceof InfinityEnergyContainer : container instanceof InfinityLaserContainer) &&
                    container.getInputVoltage() == Long.MAX_VALUE && container.getEnergyCapacity() == Long.MAX_VALUE && container.getEnergyStored() == Long.MAX_VALUE,
                    "Original native creative hatch lost the Core trait, voltage or saturated capacity");
            var recipe = GTRecipeTypes.FURNACE_RECIPES.recipeBuilder("native_creative_energy_" + index).EUt(Long.MAX_VALUE, 256).duration(4).build();
            for (boolean simulate : new boolean[] {true, false, false}) {
                var remaining = new ArrayList<>(List.of(new EnergyStack(Long.MAX_VALUE, 256)));
                helper.assertTrue(container.handleRecipeInner(IO.IN, recipe, remaining, simulate).isEmpty() && container.getEnergyStored() == Long.MAX_VALUE,
                        "Native creative EnergyStack recipe handling drained or rejected its unlimited supply");
            }
            helper.assertTrue(container.changeEnergy(Long.MIN_VALUE) == -Long.MAX_VALUE && container.getEnergyStored() == Long.MAX_VALUE,
                    "Native creative signed-long drain overflowed or emptied its supply");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeCreativeHatchesRestoreExactOddLongVoltageAndRecomputeDerivedCapacityWithZeroAmperageDisablingSupply(GameTestHelper helper) {
        var entries = List.of(CreativeMachines.CREATIVE_ENERGY_INPUT_HATCH, CreativeMachines.CREATIVE_LASER_INPUT_HATCH);
        long voltage = 9_007_199_254_740_993L;
        for (int index = 0; index < entries.size(); index++) {
            var pos = new BlockPos(2 + index * 2, 2, 3); helper.setBlock(pos, entries.get(index).getBlock());
            var machine = (MetaMachine) helper.getBlockEntity(pos);
            var tag = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
            tag.putLong("voltage", voltage); tag.putInt("amps", 2); tag.putLong("maxEnergy", 1); tag.putInt("setTier", 0);
            machine.loadAdditional(tag, helper.getLevel().registryAccess());
            var container = machine.getTrait(NotifiableEnergyContainer.class);
            helper.assertTrue(container.getInputVoltage() == voltage && container.getEnergyCapacity() == voltage * 2 &&
                    container.getEnergyStored() == voltage * 2, "Native reload rounded the voltage or trusted stale derived capacity");
            var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
            var restored = (MetaMachine) entries.get(index).getBlockEntityType().create(machine.getBlockPos(), machine.getBlockState());
            helper.getLevel().removeBlockEntity(machine.getBlockPos());
            restored.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(restored);
            container = restored.getTrait(NotifiableEnergyContainer.class);
            helper.assertTrue(container.getInputVoltage() == voltage && container.getEnergyCapacity() == voltage * 2,
                    "Recreated original native creative hatch lost exact settings");
            saved.putInt("amps", 0); restored.loadAdditional(saved, helper.getLevel().registryAccess());
            container = restored.getTrait(NotifiableEnergyContainer.class);
            var recipe = GTRecipeTypes.FURNACE_RECIPES.recipeBuilder("native_creative_zero_" + index).EUt(32).duration(4).build();
            helper.assertTrue(container.getEnergyStored() == 0 && container.getEnergyCapacity() == 0 &&
                    !container.handleRecipeInner(IO.IN, recipe, new ArrayList<>(List.of(new EnergyStack(32))), false).isEmpty(),
                    "Zero-amperage native creative hatch continued supplying recipes");
        }
        helper.succeed();
    }
}
