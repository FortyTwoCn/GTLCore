package org.gtlcore.gtlcore.integration.jade.provider;

import org.gtlcore.gtlcore.common.machine.trait.QuantumChestLongStorage;
import org.gtlcore.gtlcore.utils.NumberUtils;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;

import java.util.Collections;
import java.util.List;

public enum QuantumChestItemStorageProvider implements IServerExtensionProvider<ItemStack>, IClientExtensionProvider<ItemStack, ItemView> {

    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("gtlcore", "quantum_chest_item_storage");
    private static final String LONG_AMOUNT_TAG = "GTLCoreQuantumChestLongAmount";
    private static final int PRIORITY = 10;

    @Override
    public List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor block) || !(block.getBlockEntity() instanceof QuantumChestLongStorage storage)) return null;

        ItemStack stack = storage.gtlcore$getStoredStack();
        long amount = storage.gtlcore$getStoredAmount();
        if (stack.isEmpty() || amount <= 0L) return Collections.emptyList();

        stack = stack.copy();
        stack.setCount(1);
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putLong(LONG_AMOUNT_TAG, amount); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return List.of(new ViewGroup<>(List.of(stack)));
    }

    @Override
    public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> groups) {
        return ClientViewGroup.map(groups, QuantumChestItemStorageProvider::createItemView, null);
    }

    private static ItemView createItemView(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        long amount = tag.getLong(LONG_AMOUNT_TAG);
        var displayStack = stack.copy(); tag.remove(LONG_AMOUNT_TAG);
        if (tag.isEmpty()) displayStack.remove(DataComponents.CUSTOM_DATA);
        else displayStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return new ItemView(displayStack).amountText(NumberUtils.formatLong(amount));
    }

    @Override public ResourceLocation getUid() { return UID; }
    @Override public int getDefaultPriority() { return PRIORITY; }
}
