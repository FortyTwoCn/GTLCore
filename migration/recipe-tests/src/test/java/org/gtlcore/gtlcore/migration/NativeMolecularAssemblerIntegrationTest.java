package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.multiblock.MolecularAssemblerMultiblockMachine;
import org.gtlcore.gtlcore.api.machine.trait.AECraft.MolecularAssemblerOutputStorage;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MECraftHandler;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Production controller/logic/queue/storage and Additions' actual strict infinity-mode mixin. */
class NativeMolecularAssemblerIntegrationTest {
    private static NativeMolecularFixtures.OutputPart output() {
        return (NativeMolecularFixtures.OutputPart) NativeMolecularFixtures.IO.getBlockEntityType().create(BlockPos.ZERO,
                NativeMolecularFixtures.IO.getBlock().defaultBlockState());
    }

    private static AEItemKey key() {
        var stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("component-bearing craft output"));
        return AEItemKey.of(stack);
    }

    private static long amount(GTRecipe recipe, int index) {
        return LongIngredient.amount(ItemRecipeCapability.CAP.of(recipe.getOutputContents(ItemRecipeCapability.CAP).get(index).content()));
    }

    private static final class Host extends MolecularAssemblerMultiblockMachine {
        boolean available = true, allowBefore = true, allowTick = true;
        int beforeCalls, afterCalls;
        Host(NativeMolecularFixtures.OutputPart output) {
            super(new BlockEntityCreationInfo(NativeMolecularFixtures.ASSEMBLER.getBlockEntityType(), BlockPos.ZERO,
                    NativeMolecularFixtures.ASSEMBLER.getBlock().defaultBlockState()));
            molecularAssemblerHandler = output.handler; maxParallel = 8; tickDuration = 3;
        }
        @Override public boolean isRecipeLogicAvailable() { return available && molecularAssemblerHandler != null; }
        @Override public boolean beforeWorking(GTRecipe recipe) { beforeCalls++; return allowBefore && super.beforeWorking(recipe); }
        @Override public boolean onWorking() { return allowTick && super.onWorking(); }
        @Override public void afterWorking() { afterCalls++; super.afterWorking(); }
    }

    @Test void nativeTraitAttachesOnceAndRejectsUnrelatedOwners() {
        var part = output();
        assertSame(part, part.handler.getMachine());
        assertEquals(1, part.getTraits(MECraftHandler.class).size());
        var unattached = new MECraftHandler();
        assertThrows(IllegalStateException.class, unattached::getMachine);
        var other = new Host(part);
        assertThrows(IllegalArgumentException.class, () -> other.attachTrait(unattached));
        assertThrows(IllegalStateException.class, () -> part.attachTrait(part.handler));
    }

    @Test void nativeQueuedRecipeRetainsComponentsAndExactLongOutputs() {
        var part = output();
        long unit = 9_007_199_254_740_993L;
        var queued = new GenericStack(key(), unit);
        part.getOutputItems().put(queued, 5);
        var pending = part.handler.extractGTRecipe(2, 3);
        assertNotNull(pending); assertEquals(3, pending.duration); assertEquals(unit * 2, amount(pending, 0));
        assertEquals(3, part.getOutputItems().getLong(queued));
        var content = ItemRecipeCapability.CAP.of(pending.getOutputContents(ItemRecipeCapability.CAP).getFirst().content());
        assertTrue(LongIngredient.matchingIngredient(content).test(key().toStack()));
        assertFalse(LongIngredient.matchingIngredient(content).test(new ItemStack(Items.IRON_INGOT)));
        assertTrue(part.handler.tryHandleRecipeOutput(pending));
        assertEquals(unit * 2, part.getBuffer().getLong(key()));
        assertEquals(1, part.notifications);
    }

    @Test void oversizedCraftBatchesRemainQueuedWithoutWrappingTheirProducts() {
        var part = output();
        var queued = new GenericStack(key(), Long.MAX_VALUE / 2 + 1);
        part.getOutputItems().put(queued, 2);
        var pending = part.handler.extractGTRecipe(2, 3);
        assertNotNull(pending); assertEquals(queued.amount(), amount(pending, 0));
        assertEquals(1, part.getOutputItems().getLong(queued));
        assertTrue(part.handler.tryHandleRecipeOutput(pending));
        var next = part.handler.extractGTRecipe(1, 3);
        assertNotNull(next); assertFalse(part.handler.tryHandleRecipeOutput(next));
        assertEquals(queued.amount(), part.getBuffer().getLong(key()));
    }

    @Test void outputTransactionPreflightsAllKeysBeforeUpdatingAnyOfThem() {
        var part = output(); var diamond = AEItemKey.of(Items.DIAMOND);
        part.getOutputItems().put(new GenericStack(key(), 4), 1);
        part.getOutputItems().put(new GenericStack(diamond, 4), 1);
        var pending = part.handler.extractGTRecipe(2, 3);
        part.getBuffer().put(diamond, Long.MAX_VALUE - 1);
        assertFalse(part.handler.tryHandleRecipeOutput(pending));
        assertEquals(0, part.getBuffer().getLong(key()));
        assertEquals(Long.MAX_VALUE - 1, part.getBuffer().getLong(diamond));
        assertEquals(0, part.notifications);
        part.getBuffer().put(diamond, 1);
        assertTrue(part.handler.tryHandleRecipeOutput(pending));
        assertEquals(4, part.getBuffer().getLong(key())); assertEquals(5, part.getBuffer().getLong(diamond));
    }

    @Test void additionsInfinityMixinDrainsDistinctKeysInOneTickWithoutAGlobalLongSum() {
        var part = output();
        part.getOutputItems().put(new GenericStack(key(), 1), Long.MAX_VALUE);
        part.getOutputItems().put(new GenericStack(AEItemKey.of(Items.DIAMOND), 1), Long.MAX_VALUE);
        var pending = part.handler.extractGTRecipe(Long.MIN_VALUE, 40);
        assertNotNull(pending, "Additions' required mixin must handle its infinity sentinel");
        assertEquals(1, pending.duration); assertEquals(2, pending.getOutputContents(ItemRecipeCapability.CAP).size());
        assertEquals(Long.MAX_VALUE, amount(pending, 0)); assertEquals(Long.MAX_VALUE, amount(pending, 1));
        assertTrue(part.getOutputItems().isEmpty());
        assertTrue(part.handler.tryHandleRecipeOutput(pending));
        assertEquals(Long.MAX_VALUE, part.getBuffer().getLong(key()));
    }

    @Test void nativePartPersistenceKeepsQueueOrderComponentsAndBothLongCounts() {
        var part = output(); var first = new GenericStack(key(), 9_007_199_254_740_993L);
        var second = new GenericStack(AEItemKey.of(Items.DIAMOND), 7);
        part.getOutputItems().put(first, 3); part.getOutputItems().put(second, 2);
        part.getBuffer().put(key(), 9_007_199_254_740_995L);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var saved = part.saveWithoutMetadata(registries);
        var copy = output(); copy.loadAdditional(saved, registries);
        assertEquals(part.getOutputItems(), copy.getOutputItems()); assertEquals(part.getBuffer(), copy.getBuffer());
        assertEquals(List.of(first, second), List.copyOf(copy.getOutputItems().keySet()));
        var pending = copy.handler.extractGTRecipe(1, 3);
        assertEquals(first.amount(), amount(pending, 0)); assertEquals(2, copy.getOutputItems().getLong(first));
        assertEquals(3, part.getOutputItems().getLong(first));
    }

    @Test void failedStorageDecodeCannotReplaceOnlyOneOfTheTwoMaps() {
        var part = output(); var queued = new GenericStack(key(), 3);
        part.getOutputItems().put(queued, 2); part.getBuffer().put(key(), 7);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var tag = new CompoundTag(); MolecularAssemblerOutputStorage.save(tag, registries, part);
        tag.putString("outputItems", "invalid queue");
        assertThrows(IllegalArgumentException.class, () -> MolecularAssemblerOutputStorage.load(tag, registries, part));
        assertEquals(2, part.getOutputItems().getLong(queued)); assertEquals(7, part.getBuffer().getLong(key()));
    }

    @Test void beforeAndWorkingCallbacksBlockProgressWithoutLosingTheExtractedCraft() {
        var part = output(); part.getOutputItems().put(new GenericStack(key(), 3), 8);
        var host = new Host(part); host.allowBefore = false;
        var logic = host.getRecipeLogic(); logic.findAndHandleRecipe();
        var pending = logic.getLastRecipe(); assertNotNull(pending);
        assertTrue(logic.isWaiting()); assertTrue(part.getOutputItems().isEmpty());
        logic.serverTick(); assertEquals(0, logic.getProgress()); assertSame(pending, logic.getLastRecipe());
        host.allowBefore = true; host.allowTick = false; logic.serverTick(); assertEquals(0, logic.getProgress());
        host.allowTick = true; logic.serverTick(); assertEquals(1, logic.getProgress());
        int before = host.beforeCalls; logic.setSuspendAfterFinish(true); logic.serverTick(); logic.serverTick();
        assertEquals(before, host.beforeCalls, "beforeWorking must only be retried until it accepts the pending craft");
        assertEquals(24, part.getBuffer().getLong(key())); assertEquals(1, host.afterCalls);
        assertTrue(logic.isSuspend()); assertNull(logic.getLastRecipe());
        logic.onRecipeFinish(); assertEquals(24, part.getBuffer().getLong(key()));
    }

    @Test void outputBlockingAndStructureResetRetainExactlyOnePendingRecipe() {
        var part = output(); part.getOutputItems().put(new GenericStack(key(), 3), 8);
        var host = new Host(part); var logic = host.getRecipeLogic(); logic.findAndHandleRecipe();
        var pending = logic.getLastRecipe(); part.getBuffer().put(key(), Long.MAX_VALUE - 1);
        for (int i = 0; i < 5; i++) logic.serverTick();
        assertTrue(logic.isWaiting()); assertSame(pending, logic.getLastRecipe()); assertEquals(3, logic.getProgress());
        host.available = false; logic.resetRecipeLogic(); logic.serverTick();
        assertSame(pending, logic.getLastRecipe()); assertEquals(0, host.afterCalls);
        host.available = true; part.getBuffer().put(key(), 1); logic.serverTick();
        assertEquals(25, part.getBuffer().getLong(key())); assertNull(logic.getLastRecipe()); assertEquals(1, host.afterCalls);
        logic.serverTick(); assertEquals(25, part.getBuffer().getLong(key()));
    }

    @Test void nativeSaveFieldsRestorePendingOutputProgressAndDeferredSuspend() {
        var part = output(); part.getOutputItems().put(new GenericStack(key(), 3), 16);
        var host = new Host(part); var logic = host.getRecipeLogic(); logic.findAndHandleRecipe(); logic.serverTick();
        logic.setSuspendAfterFinish(true);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var saved = host.saveWithoutMetadata(registries);
        var copy = new Host(part); copy.loadAdditional(saved, registries);
        var resumed = copy.getRecipeLogic(); assertEquals(1, resumed.getProgress()); assertEquals(3, resumed.getDuration());
        assertNotNull(resumed.getLastRecipe()); assertEquals(24, amount(resumed.getLastRecipe(), 0));
        resumed.serverTick(); resumed.serverTick();
        assertEquals(24, part.getBuffer().getLong(key())); assertTrue(resumed.isSuspend()); assertNull(resumed.getLastRecipe());
        assertEquals(8, part.getOutputItems().values().longStream().sum());
        resumed.setWorkingEnabled(true); resumed.serverTick(); assertNotNull(resumed.getLastRecipe());
        assertTrue(part.getOutputItems().isEmpty());
    }

    @Test void nativeChangedListenersMayUnsubscribeDuringNotificationAndAreClearedOnUnload() {
        var part = output(); var calls = new AtomicInteger();
        var second = part.handler.addChangedListener(calls::incrementAndGet);
        part.handler.addChangedListener(second::unsubscribe);
        part.handler.notifyListeners(); assertEquals(1, calls.get());
        part.handler.notifyListeners(); assertEquals(1, calls.get());
        part.handler.onMachineUnload(); part.handler.notifyListeners(); assertEquals(1, calls.get());
    }

    @Test void explicitInterruptionSuspendsAndResumesTheSamePendingCraft() {
        var part = output(); part.getOutputItems().put(new GenericStack(key(), 3), 8);
        var host = new Host(part); var logic = host.getRecipeLogic();
        logic.findAndHandleRecipe(); logic.serverTick(); var pending = logic.getLastRecipe();
        logic.interruptRecipe();
        for (int i = 0; i < 5; i++) logic.serverTick();
        assertTrue(logic.isSuspend()); assertSame(pending, logic.getLastRecipe());
        assertEquals(1, logic.getProgress()); assertTrue(part.getBuffer().isEmpty());
        logic.setWorkingEnabled(true); logic.serverTick(); logic.serverTick();
        assertEquals(24, part.getBuffer().getLong(key())); assertNull(logic.getLastRecipe());
        assertEquals(1, host.afterCalls);
    }
}
