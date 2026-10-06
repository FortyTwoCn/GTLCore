package org.gtlcore.gtlcore.integration.ae2.emitter;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractDisplayPart;

public final class EmitterManagerTerminalPart extends AbstractDisplayPart {

    private static final ResourceLocation MODEL_OFF = GTLCore.id("part/emitter_manager_terminal_off");
    private static final ResourceLocation MODEL_ON = GTLCore.id("part/emitter_manager_terminal_on");
    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    private static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    public EmitterManagerTerminalPart(IPartItem<?> partItem) {
        super(partItem, true);
    }

    public static void registerModels() {
        PartModels.registerModels(MODEL_OFF, MODEL_ON);
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!super.onUseWithoutItem(player, pos)) openMenu(player);
        return true;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos) {
        if (!super.onUseItemOn(heldItem, player, hand, pos)) openMenu(player);
        return true;
    }

    private void openMenu(Player player) {
        if (!isClientSide() &&
                player instanceof ServerPlayer serverPlayer) {
            EmitterManagerTerminalMenu.open(serverPlayer, this);
        }
    }

    @Override
    public IPartModel getStaticModels() {
        return selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }
}
