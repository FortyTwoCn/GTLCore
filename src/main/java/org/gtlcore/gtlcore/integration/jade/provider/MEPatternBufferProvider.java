package org.gtlcore.gtlcore.integration.jade.provider;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.integration.ae2.MEFluidUnits;

import org.gtlcore.gtlcore.api.GTLConstants;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import com.gregtechceu.gtceu.integration.jade.GTElementHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.google.common.primitives.Ints;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.fluid.JadeFluidObject;

import static net.minecraft.network.chat.ComponentUtils.wrapInSquareBrackets;

public class MEPatternBufferProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        if (blockAccessor.getBlockEntity() instanceof MetaMachine blockEntity) {
            if (blockEntity instanceof MEPatternBufferPartMachineBase) {
                CompoundTag serverData = blockAccessor.getServerData();
                readBufferContents(iTooltip, serverData, blockAccessor.getLevel().registryAccess());
            }
        }
    }

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof MetaMachine blockEntity) {
            if (blockEntity instanceof MEPatternBufferPartMachineBase buffer) {
                putTag(compoundTag, buffer);
            }
        }
    }

    public static void readBufferContents(ITooltip iTooltip, CompoundTag serverData, HolderLookup.Provider registries) {
        if (serverData.contains("proxies", Tag.TAG_INT)) {
            iTooltip.add(Component.translatable("gtceu.top.proxies_bound", serverData.getInt("proxies"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        ListTag itemTags = serverData.getList("items", Tag.TAG_COMPOUND);
        ListTag fluidTags = serverData.getList("fluids", Tag.TAG_COMPOUND);

        for (Tag t : itemTags) {
            if (!(t instanceof CompoundTag itemTag)) continue;
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemTag.getString("item")));
            var count = itemTag.getLong("real");
            if (item != null && count > 0) {
                var stack = new ItemStack(item);
                iTooltip.add(snownee.jade.api.ui.IElementHelper.get().smallItem(new ItemStack(item)));
                Component text = Component.literal(" ")
                        .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.DARK_PURPLE))
                        .append(Component.literal(" × ").withStyle(ChatFormatting.WHITE))
                        .append(stack.getHoverName().copy().withStyle(ChatFormatting.GOLD));
                iTooltip.append(text);
            }
        }
        for (Tag t : fluidTags) {
            if (!(t instanceof CompoundTag fluidTag)) continue;
            Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(fluidTag.getString("fluid")));
            var amount = fluidTag.getLong("real");
            if (fluid != null && amount > 0) {
                iTooltip.add(GTElementHelper.smallFluid(JadeFluidObject.of(fluid)));
                Component text = Component.literal(" ")
                        .append(MEFluidUnits.formatDisplayAmount(amount))
                        .withStyle(ChatFormatting.DARK_PURPLE)
                        .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
                        .append(fluid.getFluidType().getDescription().copy().withStyle(ChatFormatting.DARK_AQUA));
                iTooltip.append(text);
            }
        }

        // Display pending refunds
        ListTag pending = serverData.getList("pending", Tag.TAG_COMPOUND);
        if (!pending.isEmpty()) {
            iTooltip.add(Component.translatable("gtceu.top.pending_refunds")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            for (var tag : pending) {
                if (!(tag instanceof CompoundTag keyTag) || keyTag.isEmpty()) continue;
                var key = AEKey.fromTagGeneric(registries, keyTag.getCompound("key"));
                if (key instanceof AEItemKey itemKey) {
                    var stack = itemKey.toStack();
                    long count = keyTag.getLong("count");
                    iTooltip.add(snownee.jade.api.ui.IElementHelper.get().smallItem(stack));
                    Component text = Component.literal(" ")
                            .append(String.valueOf(count))
                            .append(" × ")
                            .append(wrapInSquareBrackets(stack.getHoverName()).withStyle(ChatFormatting.WHITE))
                            .withStyle(ChatFormatting.WHITE);
                    iTooltip.append(text);
                } else if (key instanceof AEFluidKey fluidKey) {
                    long count = keyTag.getLong("count");
                    var fluid = fluidKey.toStack(Ints.saturatedCast(count));
                    iTooltip.add(GTElementHelper.smallFluid(JadeFluidObject.of(fluid.getFluid(), count, fluid.getComponentsPatch())));
                    Component text = Component.literal(" ")
                            .append(MEFluidUnits.formatDisplayAmount(count))
                            .append(" ")
                            .append(wrapInSquareBrackets(fluidKey.getDisplayName()).withStyle(ChatFormatting.WHITE))
                            .withStyle(ChatFormatting.WHITE);
                    iTooltip.append(text);
                }
            }
        }
    }

    public static void putTag(CompoundTag compoundTag, MEPatternBufferPartMachineBase buffer) {
        compoundTag.putInt("proxies", buffer.getProxies().size());
        var merged = buffer.getMergedInternalSlot();
        var items = merged.left();
        var fluids = merged.right();

        ListTag itemTags = new ListTag();
        for (var it = items.object2LongEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            Item item = entry.getKey();
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key != null) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putString("item", key.toString());
                itemTag.putLong("real", entry.getLongValue());
                itemTags.add(itemTag);
            }
        }
        compoundTag.put("items", itemTags);

        ListTag fluidTags = new ListTag();
        for (var it = fluids.object2LongEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            Fluid fluid = entry.getKey();
            ResourceLocation key = BuiltInRegistries.FLUID.getKey(fluid);
            if (key != null) {
                CompoundTag fluidTag = new CompoundTag();
                fluidTag.putString("fluid", key.toString());
                fluidTag.putLong("real", entry.getLongValue());
                fluidTags.add(fluidTag);
            }
        }
        compoundTag.put("fluids", fluidTags);

        // Add pending refund data
        ListTag listTag = new ListTag();
        for (var entry : it.unimi.dsi.fastutil.objects.Object2LongMaps.fastIterable(buffer.getBuffer())) {
            var count = entry.getLongValue();
            if (count > 0) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.put("key", entry.getKey().toTagGeneric(buffer.getLevel().registryAccess()));
                itemTag.putLong("count", count);
                listTag.add(itemTag);
            }
        }
        compoundTag.put("pending", listTag);
    }

    @Override
    public ResourceLocation getUid() {
        return GTLConstants.id("me_pattern_buffer");
    }
}
