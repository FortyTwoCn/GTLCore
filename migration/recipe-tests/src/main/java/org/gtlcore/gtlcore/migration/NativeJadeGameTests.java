package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.integration.jade.NativeRecipeOutputRows;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.integration.jade.provider.RecipeOutputProvider;
import com.gregtechceu.gtceu.integration.jade.provider.RecipeLogicProvider;
import com.gregtechceu.gtceu.api.machine.steam.SimpleSteamMachine;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeStatus;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import com.gtladd.gtladditions.api.recipe.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeJadeGameTests {
    private static BlockAccessor accessor(GameTestHelper helper, BlockEntity entity, CompoundTag serverData) {
        return (BlockAccessor) Proxy.newProxyInstance(BlockAccessor.class.getClassLoader(), new Class<?>[] {BlockAccessor.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockEntity" -> entity;
                    case "getLevel" -> helper.getLevel();
                    case "getPlayer" -> net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
                    case "getServerData" -> serverData;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static List<Component> tooltip(RecipeLogicProvider provider, BlockAccessor accessor) {
        var rows = new ArrayList<Component>();
        var tooltip = (ITooltip) Proxy.newProxyInstance(ITooltip.class.getClassLoader(), new Class<?>[] {ITooltip.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("add") && args.length == 1 && args[0] instanceof Component component) {
                        rows.add(component); return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        provider.appendTooltip(tooltip, accessor, null); return rows;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeJadeStatusHooksPreserveStyledReasonsAndRealSteamMachines(GameTestHelper helper) {
        var provider = new RecipeLogicProvider();
        var pressure = List.of(NativeMachineFixtures.LOW_PRESSURE, NativeMachineFixtures.HIGH_PRESSURE);
        for (int i = 0; i < pressure.size(); i++) {
            var pos = new BlockPos(4 + i * 2, 3, 4); helper.setBlock(pos, pressure.get(i).getBlock());
            var machine = (SimpleSteamMachine) helper.getBlockEntity(pos); var logic = machine.getRecipeLogic();
            logic.setWorkingEnabled(false);
            var reason = Component.literal("气体不足 ⚡ " + i).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            ((IRecipeStatus) logic).setRecipeStatus(RecipeResult.fail(reason));
            ((IRecipeStatus) logic).setWorkingStatus(RecipeResult.fail(reason));
            var serverData = new CompoundTag(); var accessor = accessor(helper, machine, serverData);
            provider.appendServerData(serverData, accessor); var data = serverData.getCompound(provider.getUid().toString());
            helper.assertTrue(reason.equals(Component.Serializer.fromJson(data.getString("reason"), helper.getLevel().registryAccess())) &&
                    reason.equals(Component.Serializer.fromJson(data.getString("work_reason"), helper.getLevel().registryAccess())),
                    "Native provider status injection lost styles or one of the reasons");
            var rows = tooltip(provider, accessor);
            helper.assertTrue(rows.size() == 1 && reason.equals(((TranslatableContents) rows.getFirst().getContents()).getArgs()[0]),
                    "Actual native Jade tooltip hook lost the Core failure reason");
            data.putBoolean("Working", true); var recipe = new CompoundTag(); recipe.putLong("EUt", 3);
            recipe.putBoolean("isInput", true); data.put("Recipe", recipe);
            rows = tooltip(provider, accessor);
            long steam = (long) Math.ceil(3 * machine.getConversionRate());
            helper.assertTrue(rows.size() == 2 && rows.getFirst().getString().contains(steam + " mB/t"),
                    "Native Jade hook used a removed block entity wrapper or lost working reasons/steam rate");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void bothNativeProviderMixinsComposeWirelessEnergyAndNativeRecipeSaveFields(GameTestHelper helper) {
        var pos = new BlockPos(4, 3, 4); helper.setBlock(pos, NativeMachineFixtures.LOW_PRESSURE.getBlock());
        var machine = (SimpleSteamMachine) helper.getBlockEntity(pos);
        helper.runAfterDelay(2, () -> {
        var amount = BigInteger.TEN.pow(60).add(BigInteger.valueOf(9_007_199_254_740_993L)).negate();
        var builder = new WirelessGTRecipeBuilder(com.gregtechceu.gtceu.GTCEu.id("native_jade_wireless_world"), GTRecipeTypes.FURNACE_RECIPES.value());
        builder.setWirelessEut(amount).duration(1000);
        var recipe = builder.build(); machine.getRecipeLogic().setupRecipe(recipe);
        helper.assertTrue(machine.getRecipeLogic().isWorking(), "Native logic did not start the controlled wireless display fixture: " +
                machine.getRecipeLogic().getStatus() + " / " + ((IRecipeStatus) machine.getRecipeLogic()).getRecipeStatus());
        var reason = Component.literal("输出空间不足").withStyle(ChatFormatting.YELLOW);
        ((IRecipeStatus) machine.getRecipeLogic()).setWorkingStatus(RecipeResult.fail(reason));
        var provider = new RecipeLogicProvider(); var serverData = new CompoundTag(); var accessor = accessor(helper, machine, serverData);
        provider.appendServerData(serverData, accessor); var data = serverData.getCompound(provider.getUid().toString());
        helper.assertTrue(amount.equals(new BigInteger(data.getByteArray("wirelessTickInputs"))), "Actual Additions native Jade injection lost the signed BigInteger");
        var rows = tooltip(provider, accessor);
        helper.assertTrue(rows.size() == 2 && ((TranslatableContents) rows.getFirst().getContents()).getKey().equals("gtceu.top.energy_consumption") &&
                reason.equals(((TranslatableContents) rows.getLast().getContents()).getArgs()[0]),
                "Core/Additions hooks replaced each other or lost a wireless failure reason");
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        var state = helper.getBlockState(pos); helper.getLevel().removeBlockEntity(helper.absolutePos(pos));
        var restored = (SimpleSteamMachine) NativeMachineFixtures.LOW_PRESSURE.getBlockEntityType().create(helper.absolutePos(pos), state);
        helper.getLevel().setBlockEntity(restored); restored.loadAdditional(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.getRecipeLogic().getLastRecipe() instanceof IWirelessGTRecipe wireless && amount.equals(wireless.getWirelessEuTickInputs()),
                "Native SaveField recipe transformer lost the wireless subtype or amount");
        helper.succeed();
        });
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeProviderSeparatesLongRowsAndPreservesOrdinaryOutputCodecs(GameTestHelper helper) {
        var machine = NativeBedrockDrillingGameTests.form(helper);
        helper.runAfterDelay(2, () -> {
            var level = helper.getLevel();
            level.setBlockAndUpdate(machine.getBlockPos().below(9), Blocks.BEDROCK.defaultBlockState());
            var item = Items.IRON_INGOT.getDefaultInstance();
            item.set(DataComponents.CUSTOM_NAME, Component.literal("Jade long output"));
            var fluid = new FluidStack(Fluids.WATER, 1);
            fluid.set(DataComponents.CUSTOM_NAME, Component.literal("Jade long coolant"));
            // Start native logic only to inspect the provider, without claiming a full physical long-output cycle.
            var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_jade_provider_world")
                    .outputItems(LongIngredient.create(item, 9_007_199_254_740_993L))
                    .outputItems(Items.GOLD_INGOT, 3)
                    .outputFluids(LongFluidIngredient.create(fluid, 9_007_199_254_741_001L))
                    .outputFluids(new FluidStack(Fluids.LAVA, 250)).EUt(0).duration(1000).build();
            var logic = machine.getRecipeLogic(); logic.setupRecipe(recipe);
            helper.assertTrue(logic.isWorking(), "Native recipe logic did not start for Jade serialization");
            try {
                var writer = RecipeOutputProvider.class.getDeclaredMethod("write", RecipeLogic.class);
                writer.setAccessible(true);
                var data = (CompoundTag) writer.invoke(new RecipeOutputProvider(), logic);
                var items = NativeRecipeOutputRows.readItems(data, level.registryAccess());
                var fluids = NativeRecipeOutputRows.readFluids(data, level.registryAccess());
                helper.assertTrue(items.size() == 1 && items.getFirst().amount() == 9_007_199_254_740_993L &&
                        ItemStack.isSameItemSameComponents(item, items.getFirst().stack()), "Jade item row lost long precision or components");
                helper.assertTrue(fluids.size() == 1 && fluids.getFirst().amount() == 9_007_199_254_741_001L &&
                        fluid.getComponentsPatch().equals(fluids.getFirst().stack().getComponentsPatch()), "Jade fluid row lost long precision or components");
                var ordinaryItems = data.getList("OutputItems", Tag.TAG_COMPOUND);
                var ordinaryFluids = data.getList("OutputFluids", Tag.TAG_COMPOUND);
                var gold = ItemStack.parseOptional(level.registryAccess(), ordinaryItems.getCompound(0));
                var lava = FluidStack.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), ordinaryFluids.getCompound(0)).getOrThrow();
                helper.assertTrue(ordinaryItems.size() == 1 && gold.is(Items.GOLD_INGOT) && gold.getCount() == 3,
                        "Native Jade item writer received long entries or lost an ordinary output");
                helper.assertTrue(ordinaryFluids.size() == 1 && lava.getFluid() == Fluids.LAVA && lava.getAmount() == 250,
                        "Native Jade fluid writer received long entries or lost an ordinary output");
                logic.interruptRecipe(); helper.succeed();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot inspect actual native Jade provider", exception);
            }
        });
    }
}
