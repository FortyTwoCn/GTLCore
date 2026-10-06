package org.gtlcore.gtlcore.client.gui;

import org.gtlcore.gtlcore.api.GTLConstants;
import appeng.api.stacks.GenericStack;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import com.glodblock.github.extendedae.client.gui.GuiPatternModifier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import java.util.ArrayList;

/** Native screen/tooltip events are registered independently of unrelated client integrations. */
@EventBusSubscriber(modid = GTLConstants.MOD_ID, value = Dist.CLIENT)
public final class NativePatternModifierClientEvents {
    /**
     * 样板修改器材料替换页中键打开数量编辑界面（仿 AE2 样板编码终端行为）。
     */
    @SubscribeEvent
    public static void onPatternModifierReplaceAmountMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof GuiPatternModifier screen) || screen.getMenu().page != 1 ||
                !Minecraft.getInstance().options.keyPickItem.matchesMouse(event.getButton())) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.hasItem() ||
                (slot != screen.getMenu().replaceTarget && slot != screen.getMenu().replaceWith)) {
            return;
        }
        ((IPatternModifierScreen) screen).gtlcore$openReplaceAmountScreen(slot);
        event.setCanceled(true);
    }

    /**
     * 样板修改器材料替换槽 tooltip 追加数量与中键设置提示。
     */
    @SubscribeEvent
    public static void onPatternModifierReplaceSlotTooltip(ItemTooltipEvent event) {
        if (!(Minecraft.getInstance().screen instanceof GuiPatternModifier screen) || screen.getMenu().page != 1) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || (slot != screen.getMenu().replaceTarget && slot != screen.getMenu().replaceWith)) {
            return;
        }
        var unwrapped = GenericStack.fromItemStack(event.getItemStack());
        if (unwrapped != null) {
            event.getToolTip().add(Tooltips.getAmountTooltip(ButtonToolTips.Amount, unwrapped));
        }
        event.getToolTip().add(Tooltips.getSetAmountTooltip());
    }

    /**
     * 移除 InvTweaks 添加到样板修改器数量编辑子屏上的排序按钮（低优先级保证在 InvTweaks 之后执行）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSetReplaceAmountScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof SetReplaceAmountScreen)) {
            return;
        }
        for (var listener : new ArrayList<>(event.getListenersList())) {
            if (listener.getClass().getName().startsWith("invtweaks.gui.InvTweaksButton")) {
                event.removeListener(listener);
            }
        }
    }

}
