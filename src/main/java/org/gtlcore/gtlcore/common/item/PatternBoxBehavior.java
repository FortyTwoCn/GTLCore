package org.gtlcore.gtlcore.common.item;

import org.gtlcore.gtlcore.config.ConfigHolder;

import com.gregtechceu.gtceu.api.mui.IItemUIHolder;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import org.gtlcore.gtlcore.integration.ae2.gui.MolecularPatternPagination;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.factory.PlayerInventoryUIFactory;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.SlotGroupWidget;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IPart;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixBase;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores encoded patterns and moves them to or from AE pattern containers.
 */
public class PatternBoxBehavior implements IItemUIHolder {

    public static final PatternBoxBehavior INSTANCE = new PatternBoxBehavior();

    private static final String INV_TAG = "PatternInv";

    private static final int SLOTS_PER_PAGE = 36;
    private static final int MAX_SLOTS = 360;

    public static int getPageCount() {
        return Math.clamp(ConfigHolder.INSTANCE.patternBoxPages, 1, 10);
    }

    public static int getSlotCount() {
        return getPageCount() * SLOTS_PER_PAGE;
    }

    /** The native registry codec preserves pattern components and previously allocated pages. */
    public static CustomItemStackHandler getInventory(ItemStack pouch, HolderLookup.Provider registries) {
        var saved = pouch.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(INV_TAG);
        int savedSize = saved.contains("Size") ? saved.getInt("Size") : 0;
        if (savedSize < 0 || savedSize > MAX_SLOTS) throw new IllegalArgumentException("Invalid pattern box inventory size");
        var inventory = new CustomItemStackHandler(Math.max(getSlotCount(), savedSize));
        inventory.setFilter(stack -> stack.isEmpty() || PatternDetailsHelper.isEncodedPattern(stack));
        if (savedSize > 0) inventory.deserializeNBT(registries, saved);
        return inventory;
    }

    public static void saveInventory(ItemStack pouch, CustomItemStackHandler inventory, HolderLookup.Provider registries) {
        var tag = pouch.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put(INV_TAG, inventory.serializeNBT(registries));
        pouch.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public ModularPanel<?> buildUI(PlayerInventoryGuiData<?> data, PanelSyncManager sync, UISettings settings) {
        var pouch = data.getUsedItemStack();
        var registries = data.getPlayer().registryAccess();
        var inventory = getInventory(pouch, registries);
        inventory.setOnContentsChanged(() -> {
            saveInventory(pouch, inventory, registries);
            data.setUsedItemStack(pouch);
        });
        var pages = MolecularPatternPagination.build(inventory, inventory.getSlots(), sync, slot -> {}, 4);
        pages.resizer().left(7).top(20);
        return ModularPanel.defaultPanel("patternBox", 176, 194).background(GTGuiTextures.BACKGROUND)
                .child(Text.lang("item.gtlcore.pattern_box").asWidget().pos(7, 6))
                .child(pages)
                .child(SlotGroupWidget.playerInventory(false, (index, slot) -> slot.background(GTGuiTextures.SLOT)).pos(7, 112).disableSortButtons());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(ItemStack stack, Level level, Player player, InteractionHand usedHand) {
        if (player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) PlayerInventoryUIFactory.INSTANCE.openFromHand(player, usedHand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean sneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return true;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack itemStack, UseOnContext context) {
        InteractionResult result = handleBlockUse(itemStack, context);
        return result == InteractionResult.PASS ? InteractionResult.PASS : InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return handleBlockUse(context.getItemInHand(), context);
    }

    public static InteractionResult handleBlockUse(ItemStack itemStack, UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return isPotentialPatternTarget(context) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }

        InternalInventory providerInv = resolveProviderInventory(context);
        if (providerInv == null) {
            return InteractionResult.PASS;
        }

        CustomItemStackHandler pouchInv = getInventory(itemStack, context.getLevel().registryAccess());
        boolean changed;
        if (serverPlayer.isShiftKeyDown()) {
            // Sneak right-click: box -> target container.
            changed = movePouchToProvider(pouchInv, providerInv);
            if (changed) {
                saveInventory(itemStack, pouchInv, context.getLevel().registryAccess());
                serverPlayer.displayClientMessage(Component.translatable("message.gtlcore.pattern_box_inserted"), true);
            } else {
                serverPlayer.displayClientMessage(Component.translatable("message.gtlcore.pattern_box_insert_failed"), true);
            }
        } else {
            // Right-click: target container -> box.
            changed = moveProviderToPouch(providerInv, pouchInv);
            if (changed) {
                saveInventory(itemStack, pouchInv, context.getLevel().registryAccess());
                serverPlayer.displayClientMessage(Component.translatable("message.gtlcore.pattern_box_extracted"), true);
            } else {
                serverPlayer.displayClientMessage(Component.translatable("message.gtlcore.pattern_box_extract_failed"), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Resolves AE pattern providers, pattern buffers, and multiblock pattern containers.
     */
    @Nullable
    public static InternalInventory resolveProviderInventory(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity tile = level.getBlockEntity(pos);

        InternalInventory assemblerMatrixInventory = resolveAssemblerMatrixInventory(tile, level);
        if (assemblerMatrixInventory != null) {
            return assemblerMatrixInventory;
        }

        MetaMachine metaMachine = MetaMachine.getMachine(level, pos);
        if (metaMachine instanceof PatternContainer patternContainer) {
            return patternContainer.getTerminalPatternInventory();
        }
        if (metaMachine instanceof MultiblockControllerMachine controller) {
            InternalInventory inventory = resolvePatternContainerInventory(controller);
            if (inventory != null) {
                return inventory;
            }
        }
        if (metaMachine instanceof MultiblockPartMachine part) {
            InternalInventory inventory = resolvePatternContainerInventory(part);
            if (inventory != null) {
                return inventory;
            }
        }

        if (tile instanceof CableBusBlockEntity cable) {
            Vec3 hitVec = context.getClickLocation();
            Vec3 hitInBlock = new Vec3(hitVec.x - pos.getX(), hitVec.y - pos.getY(), hitVec.z - pos.getZ());
            IPart part = cable.getCableBus().selectPartLocal(hitInBlock).part;
            if (part instanceof PatternProviderLogicHost providerPart) {
                return providerPart.getLogic().getPatternInv();
            }
            return null;
        }
        if (tile instanceof PatternProviderLogicHost providerBlock) {
            return providerBlock.getLogic().getPatternInv();
        }
        return null;
    }

    private static boolean isPotentialPatternTarget(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity tile = level.getBlockEntity(pos);
        MetaMachine metaMachine = MetaMachine.getMachine(level, pos);
        if (tile instanceof CableBusBlockEntity || tile instanceof PatternProviderLogicHost) {
            return true;
        }
        if (isAssemblerMatrix(tile)) {
            return true;
        }
        if (metaMachine instanceof PatternContainer ||
                metaMachine instanceof MultiblockControllerMachine ||
                metaMachine instanceof MultiblockPartMachine) {
            return true;
        }
        return false;
    }

    @Nullable
    private static InternalInventory resolveAssemblerMatrixInventory(@Nullable BlockEntity tile, Level level) {
        if (tile instanceof TileAssemblerMatrixPattern patternTile) {
            return createAssemblerMatrixPatternInventory(patternTile, level);
        }
        if (!(tile instanceof TileAssemblerMatrixBase matrixBase)) {
            return null;
        }
        ClusterAssemblerMatrix cluster = matrixBase.getCluster();
        if (cluster == null || cluster.isDestroyed()) {
            return null;
        }
        List<InternalInventory> inventories = new ArrayList<>();
        for (TileAssemblerMatrixPattern patternTile : cluster.getPatterns()) {
            if (patternTile != null && patternTile.isValid()) {
                inventories.add(createAssemblerMatrixPatternInventory(patternTile, level));
            }
        }
        if (inventories.isEmpty()) {
            return null;
        }
        return inventories.size() == 1 ? inventories.get(0) :
                new CombinedInternalInventory(inventories.toArray(InternalInventory[]::new));
    }

    private static InternalInventory createAssemblerMatrixPatternInventory(TileAssemblerMatrixPattern patternTile, Level level) {
        return new FilteredInternalInventory(patternTile.getTerminalPatternInventory(), new IAEItemFilter() {

            @Override
            public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                return stack.getItem() instanceof EncodedPatternItem &&
                        PatternDetailsHelper.decodePattern(stack, level) instanceof IMolecularAssemblerSupportedPattern;
            }
        });
    }

    private static boolean isAssemblerMatrix(@Nullable BlockEntity tile) {
        return tile instanceof TileAssemblerMatrixBase;
    }

    @Nullable
    private static InternalInventory resolvePatternContainerInventory(MultiblockControllerMachine controller) {
        if (!controller.isFormed()) {
            return null;
        }
        return resolvePatternContainerInventory(controller.getParts());
    }

    @Nullable
    private static InternalInventory resolvePatternContainerInventory(MultiblockPartMachine part) {
        if (!part.isFormed()) {
            return null;
        }
        for (MultiblockControllerMachine controller : part.getControllers()) {
            InternalInventory inventory = resolvePatternContainerInventory(controller);
            if (inventory != null) {
                return inventory;
            }
        }
        return null;
    }

    @Nullable
    private static InternalInventory resolvePatternContainerInventory(Iterable<MultiblockPartMachine> parts) {
        for (MultiblockPartMachine part : parts) {
            if (part instanceof PatternContainer patternContainer) {
                return patternContainer.getTerminalPatternInventory();
            }
        }
        return null;
    }

    /**
     * Moves encoded patterns from the target container into the box.
     */
    public static boolean moveProviderToPouch(InternalInventory providerInv, CustomItemStackHandler pouchInv) {
        boolean changed = false;
        for (int slot = 0; slot < providerInv.size(); slot++) {
            ItemStack stack = providerInv.getStackInSlot(slot);
            if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                continue;
            }
            int pouchSlot = findEmptyPouchSlot(pouchInv);
            if (pouchSlot < 0) {
                break;
            }
            ItemStack extracted = providerInv.extractItem(slot, stack.getCount(), false);
            if (extracted.isEmpty()) {
                continue;
            }
            pouchInv.setStackInSlot(pouchSlot, extracted);
            changed = true;
        }
        return changed;
    }

    /**
     * Moves compatible patterns from the box into the target container.
     */
    public static boolean movePouchToProvider(CustomItemStackHandler pouchInv, InternalInventory providerInv) {
        boolean changed = false;
        for (int slot = 0; slot < pouchInv.getSlots(); slot++) {
            ItemStack stack = pouchInv.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int providerSlot = findInsertableProviderSlot(providerInv, stack);
            if (providerSlot < 0) {
                continue;
            }
            ItemStack remainder = providerInv.insertItem(providerSlot, stack.copy(), false);
            if (remainder.getCount() < stack.getCount()) {
                pouchInv.setStackInSlot(slot, remainder);
                changed = true;
            }
        }
        return changed;
    }

    private static int findEmptyPouchSlot(CustomItemStackHandler pouchInv) {
        for (int slot = 0; slot < pouchInv.getSlots(); slot++) {
            if (pouchInv.getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static int findInsertableProviderSlot(InternalInventory providerInv, ItemStack stack) {
        for (int slot = 0; slot < providerInv.size(); slot++) {
            if (providerInv.isItemValid(slot, stack) &&
                    providerInv.insertItem(slot, stack.copy(), true).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }
}
