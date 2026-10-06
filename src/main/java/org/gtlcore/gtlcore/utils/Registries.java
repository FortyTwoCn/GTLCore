package org.gtlcore.gtlcore.utils;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Objects;

public class Registries {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static Item getItem(String s) {
        Item i = BuiltInRegistries.ITEM.get(ResourceLocation.parse(s));
        if (i == Items.AIR) {
            LOGGER.atError().log("未找到ID为{}的物品", s);
            return Items.BARRIER;
        }
        return i;
    }

    public static ItemStack getItemStack(String s) {
        return getItemStack(s, 1);
    }

    public static ItemStack getItemStack(String s, int a) {
        return new ItemStack(getItem(s), a);
    }

    public static Block getBlock(String s) {
        Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(s));
        if (b == Blocks.AIR) {
            LOGGER.atError().log("未找到ID为{}的方块", s);
            return Blocks.BARRIER;
        }
        return b;
    }

    public static Fluid getFluid(String s) {
        return getFluid(ResourceLocation.parse(s));
    }

    public static Fluid getFluid(ResourceLocation resourceLocation) {
        Fluid f = BuiltInRegistries.FLUID.get(resourceLocation);
        if (f == Fluids.EMPTY) {
            LOGGER.atError().log("未找到ID为{}的流体", resourceLocation.toString());
            return Fluids.WATER;
        }
        return f;
    }

    public static ResourceKey<Level> getDimension(String s) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(s));
    }

    public static RecipeManager getRecipeManager() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && (FMLEnvironment.dist == Dist.DEDICATED_SERVER || server.isSameThread())) {
            return server.getRecipeManager();
        } else {
            if (FMLEnvironment.dist != Dist.CLIENT) throw new IllegalStateException("No running server recipe manager");
            return ClientRecipes.getRecipeManager();
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientRecipes {
        private static RecipeManager getRecipeManager() {
            return Objects.requireNonNull(Minecraft.getInstance().getConnection(), "No client connection").getRecipeManager();
        }
    }

    public static ResourceLocation getResourceKey(ItemStack itemStack) {
        return BuiltInRegistries.ITEM.getKey(itemStack.getItem());
    }

    public static ResourceLocation getResourceKey(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    public static ResourceLocation getResourceKey(FluidStack fluidStack) {
        return BuiltInRegistries.FLUID.getKey(fluidStack.getFluid());
    }

    public static ResourceLocation getResourceKey(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid);
    }

    public static ResourceLocation getResourceKey(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    public static String getItemId(ItemStack itemStack) {
        return getResourceKey(itemStack).toString();
    }

    public static String getItemId(Item item) {
        return getResourceKey(item).toString();
    }

    public static String getFluidId(FluidStack fluidStack) {
        return getResourceKey(fluidStack).toString();
    }

    public static String getBlockId(Block block) {
        return getResourceKey(block).toString();
    }
}
