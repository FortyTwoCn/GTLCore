package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.utils.MachineIO;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;

import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.data.GTBlocks;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.InteractionSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.ButtonWidget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import appeng.util.Platform;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SlaughterhouseMachine extends WorkableElectricMultiblockMachine {

    // Preserve the field name: true selects direct loot, false selects live spawning.
    @SaveField
    private boolean isSpawn;
    private final String[] mobList1 = ConfigHolder.INSTANCE.mobList1;
    private final String[] mobList2 = ConfigHolder.INSTANCE.mobList2;

    public SlaughterhouseMachine(BlockEntityCreationInfo holder) {
        super(holder);
    }

    public boolean isSimulatingLoot() {
        return isSpawn;
    }

    public void setSimulatingLoot(boolean value) {
        if (isSpawn != value) {
            isSpawn = value;
            setChanged();
        }
    }

    private DamageSource slaughterDamage(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.GENERIC_KILL), Platform.getFakePlayer(level, null));
    }

    private void collectDrops(ServerLevel level, BlockPos pos, DamageSource source) {
        for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(
                pos.getX() - 3, pos.getY() - 1, pos.getZ() - 3,
                pos.getX() + 3, pos.getY() + 6, pos.getZ() + 3))) {
            if (entity instanceof Player) continue;
            if (entity instanceof ItemEntity item) {
                // The output helper commits only after the entire stack fits.
                if (MachineIO.outputItem(this, item.getItem().copy())) item.discard();
            } else if (entity instanceof LivingEntity living) {
                living.hurt(source, 10000);
                if (living.isDeadOrDying()) living.kill();
            } else {
                entity.kill();
            }
        }
    }

    @Override
    public void afterWorking() {
        super.afterWorking();
        if (isRemoved() || !isFormed() || !(getLevel() instanceof ServerLevel level)) return;
        final BlockPos pos = getBlockPos();
        BlockPos[][] coordinates = new BlockPos[][] {
                new BlockPos[] { pos.offset(1, 0, 0), pos.offset(3, 1, 0) },
                new BlockPos[] { pos.offset(-1, 0, 0), pos.offset(-3, 1, 0) },
                new BlockPos[] { pos.offset(0, 0, 1), pos.offset(0, 1, 3) },
                new BlockPos[] { pos.offset(0, 0, -1), pos.offset(0, 1, -3) } };
        for (BlockPos[] blockPos : coordinates) {
            if (level.hasChunkAt(blockPos[0]) && level.hasChunkAt(blockPos[1]) &&
                    level.getBlockState(blockPos[0]).is(GTBlocks.CASING_STEEL_GEARBOX.get())) {
                BlockPos mobPos = blockPos[1];
                final String[] mobList = MachineIO.notConsumableCircuit(this, 1) ? this.mobList1 : this.mobList2;
                var damage = slaughterDamage(level);
                collectDrops(level, mobPos, damage);
                if (mobList.length == 0) continue;
                for (int amount = 0; amount <= (getTier() - 2) * 8; amount++) {
                    var id = ResourceLocation.tryParse(mobList[level.random.nextInt(mobList.length)]);
                    var type = id == null ? null : EntityType.byString(id.toString()).orElse(null);
                    if (type == null) continue;
                    if (!isSpawn) {
                        var entity = type.spawn(level, mobPos, MobSpawnType.MOB_SUMMONED);
                        if (entity != null) entity.setYRot(level.random.nextFloat() * 360F);
                    } else {
                        simulateLoot(level, mobPos, type, damage);
                    }
                }
            }
        }
    }

    private void simulateLoot(ServerLevel level, BlockPos pos, EntityType<?> type, DamageSource damage) {
        var entity = type.create(level);
        if (entity == null) return;
        entity.setPos(Vec3.atBottomCenterOf(pos));
        var player = Platform.getFakePlayer(level, null);
        var params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, damage)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, damage.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, damage.getDirectEntity())
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .withLuck(player.getLuck()).create(LootContextParamSets.ENTITY);
        var table = level.getServer().reloadableRegistries().getLootTable(type.getDefaultLootTable());
        for (ItemStack stack : table.getRandomItems(params)) {
            if (!stack.isEmpty() && !MachineIO.outputItem(this, stack.copy())) {
                var drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, stack.copy());
                level.addFreshEntity(drop);
            }
        }
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager sync) {
        var widgets = new ArrayList<>(super.getWidgetsForDisplay(sync));
        var formed = sync.getOrCreateSyncHandler("slaughterhouseFormed", BooleanSyncValue.class,
                () -> new BooleanSyncValue(this::isFormed));
        var mode = sync.getOrCreateSyncHandler("slaughterhouseMode", BooleanSyncValue.class,
                () -> new BooleanSyncValue(this::isSimulatingLoot));
        var toggle = sync.getOrCreateSyncHandler("slaughterhouseToggle", InteractionSyncHandler.class,
                () -> new InteractionSyncHandler().setOnMousePressed(mouse -> {
                    if (mouse.isLeftMouseButton() && !sync.isClient() && !isRemoved() && isFormed()) setSimulatingLoot(!isSpawn);
                }));
        widgets.add(new ButtonWidget<>().name("slaughterhouseToggle").size(150, 18)
                .overlay(Text.dynamic(() -> Component.translatable("gtceu.machine.slaughterhouse.is_spawn")
                        .append(Component.translatable(mode.getBoolValue() ? "gtceu.machine.off" : "gtceu.machine.on"))))
                .syncHandler(toggle).setEnabledIf(widget -> formed.getBoolValue()));
        return widgets;
    }
}
