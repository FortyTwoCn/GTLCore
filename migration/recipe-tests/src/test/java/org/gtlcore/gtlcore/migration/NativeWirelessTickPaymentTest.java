package org.gtlcore.gtlcore.migration;

import com.gtladd.gtladditions.api.machine.IWirelessElectricMultiblockMachine;
import com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler;
import com.gtladd.gtladditions.api.recipe.*;
import com.gtladd.gtladditions.utils.RecipeCalculationHelper;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.*;
import static org.gtlcore.gtlcore.migration.NativeCoreRecipeRunnerIntegrationTest.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeWirelessTickPaymentTest {
    static class Account implements IWirelessNetworkEnergyHandler {
        BigInteger balance = BigInteger.ZERO;
        boolean online = true;
        @Override public boolean consumeEnergy(int amount) { return consumeEnergy(BigInteger.valueOf(amount)); }
        @Override public boolean consumeEnergy(long amount) { return consumeEnergy(BigInteger.valueOf(amount)); }
        @Override public boolean consumeEnergy(BigInteger amount) {
            if (!online || balance.add(amount).signum() < 0) return false;
            balance = balance.add(amount); return true;
        }
        @Override public BigInteger getMaxAvailableEnergy() { return balance; }
        @Override public boolean isOnline() { return online; }
    }
    static class WirelessHolder extends CoreHolder implements IWirelessElectricMultiblockMachine {
        IWirelessNetworkEnergyHandler account = new Account();
        @Override public IWirelessNetworkEnergyHandler getWirelessNetworkEnergyHandler() { return account; }
        @Override public void setWirelessNetworkEnergyHandler(IWirelessNetworkEnergyHandler value) { account = value; }
    }
    @Test void actualNativeTickDoesNotSpendFluidWithoutWirelessEnergyAndRefundsEnergyWhenFluidIsMissing() {
        var holder = new WirelessHolder(); var account = (Account) holder.account;
        var calls = new ArrayList<String>();
        var tank = holder.attachTrait(new com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank(1, 1000, IO.IN, IO.IN) {
            @Override public List<net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient> handleRecipeInner(
                    IO io, com.gregtechceu.gtceu.api.recipe.GTRecipe recipe,
                    List<net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient> left, boolean simulate) {
                calls.add(io + ":" + simulate + ":" + left.stream().map(i -> Integer.toString(i.amount())).toList());
                return super.handleRecipeInner(io, recipe, left, simulate);
            }
        });
        tank.getStorages()[0].setFluid(new FluidStack(Fluids.WATER, 15));
        holder.proxies.put(IO.IN, List.of(RecipeHandlerList.of(IO.IN, tank)));
        holder.flat.put(IO.IN, Map.of(FluidRecipeCapability.CAP, List.of(tank)));
        var builder = WirelessGTRecipeBuilder.Companion.ofRaw();
        builder.perTick(true).inputFluids(net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient.of(Fluids.WATER, 10)).duration(20);
        builder.setWirelessEut(BigInteger.valueOf(-7)); var recipe = builder.build();
        assertFalse(recipe.tickInputs.isEmpty());
        holder.getRecipeLogic().setupRecipe(recipe);
        assertNotNull(holder.getRecipeLogic().getLastRecipe());
        assertFalse(holder.getRecipeLogic().getLastUnrolledRecipe().tickInputs.isEmpty());
        var rolled = com.gregtechceu.gtceu.api.recipe.RecipeHelper.doTickPrerolls(recipe,
                holder.getRecipeLogic().getChanceCaches(), holder.getRecipeLogic().getLastUnrolledRecipe());
        var fluidInput = rolled.tickInputs.get(FluidRecipeCapability.CAP).getFirst();
        assertEquals(10, FluidRecipeCapability.CAP.of(fluidInput.content()).amount());
        assertEquals(fluidInput.maxChance(), fluidInput.chance());
        assertFalse(holder.getRecipeLogic().handleTickRecipe(recipe).isSuccess());
        assertEquals(15, tank.getStorages()[0].getFluidAmount()); assertEquals(BigInteger.ZERO, account.balance);
        account.balance = BigInteger.valueOf(100);
        assertTrue(holder.getRecipeLogic().handleTickRecipe(recipe).isSuccess());
        assertEquals(5, tank.getStorages()[0].getFluidAmount(), calls.toString()); assertEquals(BigInteger.valueOf(93), account.balance);
        assertFalse(holder.getRecipeLogic().handleTickRecipe(recipe).isSuccess());
        assertEquals(5, tank.getStorages()[0].getFluidAmount()); assertEquals(BigInteger.valueOf(93), account.balance);
    }
    @Test void nativeCallbackFailureAndExceptionsRefundBothConsumptionAndGeneration() {
        var holder = new WirelessHolder(); var account = (Account) holder.account;
        account.balance = BigInteger.valueOf(100);
        for (long signed : new long[]{-7, 7}) {
            var builder = WirelessGTRecipeBuilder.Companion.ofRaw(); builder.duration(20); builder.setWirelessEut(BigInteger.valueOf(signed));
            var recipe = builder.build();
            assertFalse(NativeWirelessRecipeTick.run(holder, recipe, () -> ActionResult.FAIL_NO_REASON).isSuccess());
            assertEquals(BigInteger.valueOf(100), account.balance);
            assertThrows(IllegalStateException.class, () -> NativeWirelessRecipeTick.run(holder, recipe, () -> { throw new IllegalStateException("native-io-failure"); }));
            assertEquals(BigInteger.valueOf(100), account.balance);
        }
    }
    @Test void nativeBatchChargesTheCompleteIntegerCostIncludingSmallAndNegativeRemainders() {
        for (BigInteger total : List.of(BigInteger.valueOf(9), BigInteger.valueOf(-9), BigInteger.valueOf(256), BigInteger.ONE.shiftLeft(100).add(BigInteger.valueOf(7)))) {
            var recipe = RecipeCalculationHelper.INSTANCE.buildWirelessRecipe(List.of(), List.of(), 20, total, com.gregtechceu.gtceu.common.data.GTRecipeTypes.DUMMY_RECIPES.value());
            assertTrue(recipe.hasTick());
            BigInteger charged = BigInteger.ZERO;
            for (int progress = 0; progress < 20; progress++) charged = charged.add(NativeWirelessRecipeState.tickAmount(recipe, progress, 20));
            assertEquals(total.negate(), charged);
            assertFalse(recipe.copyWithoutTicks().hasTick());
            var nativeCopy = recipe.copy(); assertEquals(total.negate(), NativeWirelessRecipeState.read(nativeCopy, NativeWirelessRecipeState.TOTAL_KEY));
        }
    }
    @Test void actualNativeTickKeepsWirelessIntegersBeyondLongWithoutDoubleOrIntConversion() {
        var holder = new WirelessHolder(); var account = (Account) holder.account;
        var tank = fluids(holder, IO.IN, 1000, 0);
        holder.proxies.put(IO.IN, List.of(RecipeHandlerList.of(IO.IN, tank)));
        holder.flat.put(IO.IN, Map.of(FluidRecipeCapability.CAP, List.of(tank)));
        var amount = BigInteger.ONE.shiftLeft(100).add(BigInteger.valueOf(13)); account.balance = amount.multiply(BigInteger.TWO);
        var builder = WirelessGTRecipeBuilder.Companion.ofRaw(); builder.duration(20); builder.setWirelessEut(amount.negate());
        var recipe = builder.build(); holder.getRecipeLogic().setupRecipe(recipe);
        assertNotNull(holder.getRecipeLogic().getLastRecipe());
        assertTrue(holder.getRecipeLogic().handleTickRecipe(recipe).isSuccess()); assertEquals(amount, account.balance);
    }
}
