package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.storage.*;
import org.gtlcore.gtlcore.utils.StorageManager;
import org.gtlcore.gtlcore.utils.datastructure.Int128;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.*;
import java.math.BigInteger;
import java.util.UUID;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeCellGameTests {
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void actualNativeChestSelectsGeneralMenusForBothInfinityCells(GameTestHelper helper) {
        var pos = new net.minecraft.core.BlockPos(1, 1, 1);
        helper.setBlock(pos, appeng.core.definitions.AEBlocks.ME_CHEST.block());
        var chest = (appeng.blockentity.storage.MEChestBlockEntity) helper.getBlockEntity(pos);
        // Exercise native ServerPlayer.openMenu without logging a synthetic connection into other mods.
        // Packets go to NeoForge's test-owned fake-player sink; this is a server menu test.
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "NativeChestFixture"), net.minecraft.server.level.ClientInformation.createDefault());
        player.connection = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()).connection;
        helper.runAfterDelay(2, () -> {
            for (var cell : new net.minecraft.world.item.ItemStack[] {NativeCellFixtures.NORMAL.asStack(), NativeCellFixtures.FAST.asStack()}) {
                chest.setCell(cell);
                helper.assertTrue(chest.openGui(player) && player.containerMenu.getType() == appeng.menu.me.common.MEStorageMenu.TYPE,
                        "Actual native chest did not open the general terminal for an infinity cell");
                helper.assertTrue(((appeng.menu.me.common.MEStorageMenu) player.containerMenu).canConfigureTypeFilter(),
                        "Infinity chest menu lost its native type filter");
                player.closeContainer();
            }
            chest.setCell(appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
            helper.assertTrue(chest.openGui(player) && player.containerMenu.getType() == appeng.menu.me.items.BasicCellChestMenu.TYPE,
                    "Native ordinary cell no longer uses its original basic chest menu");
            player.closeContainer();
            helper.succeed();
        });
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void vanillaCreativeCloneKeepsComponentsAndCreatesIndependentExactCellStorage(GameTestHelper helper) {
        var level = helper.getLevel(); var registries = level.registryAccess();
        var manager = StorageManager.getInstance(level.getServer());
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var key = AEItemKey.of(Items.IRON_INGOT);
        var exact = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(8192));
        for (boolean fast : new boolean[] { false, true }) {
            var source = fast ? NativeCellFixtures.FAST.asStack() : NativeCellFixtures.NORMAL.asStack();
            var sourceId = UUID.randomUUID();
            CustomData.update(DataComponents.CUSTOM_DATA, source, tag -> {
                tag.putUUID("diskuuid", sourceId); tag.putString("FuzzyMode", "PERCENT_99");
            });
            source.set(DataComponents.CUSTOM_NAME, Component.literal("independent native cell"));
            var data = new InfinityCellDataStorage(fast);
            data.stackKeys.add(key.toTagGeneric(registries));
            var int128 = Int128.fromBigInteger(exact);
            data.amounts.add(fast ? new LongArrayTag(new long[] { int128.getHigh(), int128.getLow() }) : StringTag.valueOf(exact.toString()));
            data.totalAmount = exact.doubleValue();
            manager.updateDisk(sourceId, data);
            var sourceTag = source.save(registries);
            var menu = ChestMenu.threeRows(1, player.getInventory(), new SimpleContainer(27));
            menu.getSlot(0).set(source);
            player.getAbilities().instabuild = false;
            menu.clicked(0, 2, ClickType.CLONE, player);
            helper.assertTrue(menu.getCarried().isEmpty(), "Survival player cloned an infinity cell");
            player.getAbilities().instabuild = true;
            menu.clicked(0, 2, ClickType.CLONE, player);
            var clone = menu.getCarried(); var cloneId = CellItemData.getDiskUUID(clone);
            helper.assertTrue(!clone.isEmpty() && clone.getItem() == source.getItem() && cloneId != null && !cloneId.equals(sourceId),
                    "Actual vanilla creative clone did not assign an independent cell identity");
            helper.assertTrue(clone.get(DataComponents.CUSTOM_NAME).equals(source.get(DataComponents.CUSTOM_NAME)) &&
                    clone.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("FuzzyMode").equals("PERCENT_99") &&
                    CellItemData.getStoredCount(clone) == exact.doubleValue() && source.save(registries).equals(sourceTag),
                    "Cloning lost native settings/count components or changed the original cell");
            var copy = manager.getOrCreateDisk(cloneId, fast);
            helper.assertTrue(copy.stackKeys.equals(data.stackKeys) && copy.amounts.equals(data.amounts) &&
                    copy.stackKeys != data.stackKeys && copy.amounts != data.amounts, "Clone shared mutable persisted keys or amounts");
            var inventory = fast ? (PreciseStorageAmount) new FastInfinityCellInventory(clone, null) :
                    new InfinityCellInventory(AEKeyType.items(), clone, null);
            helper.assertTrue(inventory.getExactStoredAmount(key).equals(exact), "Cloned cell lost a quantity above long/double precision");
            var saved = manager.save(new CompoundTag(), registries);
            var restored = StorageManager.readNbt(saved);
            helper.assertTrue(restored.getOrCreateDisk(cloneId, fast).amounts.equals(data.amounts), "Native SavedData reload changed cloned quantities");
            copy.amounts.clear();
            helper.assertTrue(data.amounts.size() == 1 && data.stackKeys.size() == 1, "Changing the clone changed the source disk");
            manager.removeDisk(sourceId); manager.removeDisk(cloneId);
        }
        helper.succeed();
    }
}
