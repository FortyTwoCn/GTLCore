package org.gtlcore.gtlcore.utils;

import org.gtlcore.gtlcore.api.recipe.RecipeResult;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;

import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

import static org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper.*;

public class MachineUtil {

    public static final BlockPos[] EMPTY_POS_ARRAY = new BlockPos[0];

    private static final String LEGGINGS_SUFFIX = "leggings";
    private static final String PANTS_SUFFIX = "pants";

    private static final List<Map<EquipmentSlot, ResourceLocation>> NO_CLIP_ARMOR_SETS = List.of(
            createArmorSet("kubejs:fermium_", LEGGINGS_SUFFIX),
            createArmorSet("kubejs:space_fermium_", LEGGINGS_SUFFIX),
            createArmorSet("avaritia:infinity_", PANTS_SUFFIX),
            createArmorSet("kubejs:magnetohydrodynamicallyconstrainedstarmatter_", LEGGINGS_SUFFIX));

    private MachineUtil() {
        throw new IllegalAccessError();
    }

    public static BlockPos getOffsetPos(int a, int b, Direction facing, BlockPos pos) {
        int x = 0, z = 0;
        switch (facing) {
            case NORTH -> z = a;
            case SOUTH -> z = -a;
            case WEST -> x = a;
            case EAST -> x = -a;
        }
        return pos.offset(x, b, z);
    }

    public static boolean inputItem(WorkableMultiblockMachine machine, ItemStack item) {
        GTRecipe recipe = GTRecipeTypes.DUMMY_RECIPES.recipeBuilder(Registries.getResourceKey(item)).inputItems(item).build();
        if (matchRecipeInputNocache(machine, recipe)) {
            return handleRecipeInputNocache(machine, recipe);
        } else RecipeResult.of(machine, RecipeResult.fail(Component.translatable("gtceu.recipe.fail.no.input.item", item.getDisplayName())));
        return false;
    }

    public static boolean outputItem(WorkableMultiblockMachine machine, ItemStack item) {
        if (!item.isEmpty()) {
            GTRecipe recipe = GTRecipeTypes.DUMMY_RECIPES.recipeBuilder(Registries.getResourceKey(item)).outputItems(item).build();
            if (matchRecipeOutput(machine, recipe)) {
                return handleRecipeOutput(machine, recipe);
            }
        }
        return false;
    }

    public static boolean notConsumableItem(WorkableMultiblockMachine machine, ItemStack item) {
        return matchRecipeInputNocache(machine, GTRecipeTypes.DUMMY_RECIPES.recipeBuilder(Registries.getResourceKey(item)).inputItems(item).build());
    }

    public static boolean notConsumableCircuit(WorkableMultiblockMachine machine, int configuration) {
        return matchRecipeInputNocache(machine, GTRecipeTypes.DUMMY_RECIPES.recipeBuilder(GTCEu.id(String.valueOf(configuration)))
                .inputItems(IntCircuitIngredient.circuit(configuration)).build());
    }

    public static boolean inputFluid(WorkableMultiblockMachine machine, FluidStack fluid) {
        GTRecipe recipe = GTRecipeTypes.DUMMY_RECIPES.recipeBuilder(Registries.getResourceKey(fluid)).inputFluids(fluid).build();
        if (matchRecipeInputNocache(machine, recipe)) {
            return handleRecipeInputNocache(machine, recipe);
        } else RecipeResult.of(machine, RecipeResult.fail(Component.translatable("gtceu.recipe.fail.no.input.fluid", fluid.getHoverName())));
        return false;
    }

    /** Keep auxiliary-fluid costs in long without truncating the native FluidStack's display quantity. */
    public static boolean inputFluid(WorkableMultiblockMachine machine, FluidStack fluid, long amount) {
        if (amount <= 0 || fluid.isEmpty()) return false;
        var ingredient = org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient.create(fluid, amount);
        var recipe = com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder.ofRaw()
                .input(com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability.CAP, ingredient).build();
        if (matchRecipeInputNocache(machine, recipe)) return handleRecipeInputNocache(machine, recipe);
        RecipeResult.of(machine, RecipeResult.fail(Component.translatable("gtceu.recipe.fail.no.input.fluid", fluid.getHoverName())));
        return false;
    }

    public static boolean outputFluid(WorkableMultiblockMachine machine, FluidStack fluid) {
        if (!fluid.isEmpty()) {
            GTRecipe recipe = GTRecipeTypes.DUMMY_RECIPES.recipeBuilder(Registries.getResourceKey(fluid)).outputFluids(fluid).build();
            if (matchRecipeOutput(machine, recipe)) {
                return handleRecipeOutput(machine, recipe);
            }
        }
        return false;
    }

    public static boolean inputEU(WorkableMultiblockMachine machine, long eu) {
        if (eu < 0) return false;
        if (eu == 0) return true;
        GTRecipe recipe = GTRecipeTypes.DUMMY_RECIPES.recipeBuilder(GTCEu.id(String.valueOf(eu))).EUt(eu).build();
        if (RecipeHelper.matchTickRecipe(machine, recipe).isSuccess()) {
            return RecipeHelper.handleTickRecipeIO(machine, recipe, IO.IN, machine.getRecipeLogic().getChanceCaches()).isSuccess();
        }
        return false;
    }

    /**
     * 通知部件的所有控制器重新检查配方逻辑订阅。
     * 用于库存类（stocking）输入部件的暂停切换：其"库存"即 ME 网络，
     * 暂停/恢复不会产生本地库存变化通知，需主动唤醒可能已退订休眠的控制器。
     */
    public static void notifyControllersRecipeLogic(MultiblockPartMachine part) {
        for (MultiblockControllerMachine controller : part.getControllers()) {
            if (controller instanceof IRecipeLogicMachine machine) {
                machine.getRecipeLogic().updateTickSubscription();
            }
        }
    }

    public static void createItemEntity(ServerLevel level, double x, double y, double z, ItemStack itemStack) {
        final var newItem = new ItemEntity(level, x, y, z, itemStack);
        newItem.setDeltaMovement(0.0, 0.2, 0.0);
        newItem.setPickUpDelay(10);
        level.addFreshEntity(newItem);
    }

    public static boolean hasFullArmorSet(ServerPlayer player) {
        return hasArmorSet(player, NO_CLIP_ARMOR_SETS.get(3));
    }

    public static boolean hasNoClipArmorSet(Player player) {
        return NO_CLIP_ARMOR_SETS.stream().anyMatch(armorSet -> hasArmorSet(player, armorSet));
    }

    private static boolean hasArmorSet(Player player, Map<EquipmentSlot, ResourceLocation> armorSet) {
        return armorSet.entrySet().stream().allMatch(entry -> entry.getValue().equals(
                Registries.getResourceKey(player.getItemBySlot(entry.getKey()))));
    }

    private static Map<EquipmentSlot, ResourceLocation> createArmorSet(String prefix, String legsSuffix) {
        return Map.of(
                EquipmentSlot.FEET, ResourceLocation.parse(prefix + "boots"),
                EquipmentSlot.LEGS, ResourceLocation.parse(prefix + legsSuffix),
                EquipmentSlot.CHEST, ResourceLocation.parse(prefix + "chestplate"),
                EquipmentSlot.HEAD, ResourceLocation.parse(prefix + "helmet"));
    }
}
