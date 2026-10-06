package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.client.gui.*;
import org.gtlcore.gtlcore.mixin.ae2.gui.NumberEntryWidgetAccessor;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.menu.*;
import appeng.menu.locator.MenuLocators;
import appeng.client.gui.widgets.NumberEntryWidget;
import com.glodblock.github.extendedae.common.EAESingletons;
import com.glodblock.github.extendedae.common.me.itemhost.HostPatternModifier;
import com.glodblock.github.extendedae.container.ContainerPatternModifier;
import com.glodblock.github.extendedae.client.gui.GuiPatternModifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import java.util.*;

/** Original ExtendedAE item, host, menu, screen and registered native packets on an integrated client. */
@EventBusSubscriber(modid = "gtlcore_recipe_test")
public final class NativeExtendedAEClientFixtures {
    private static volatile boolean ready;
    private static volatile long inputAmount, outputAmount, replacementAmount;
    private static UUID owner;
    private static void prepare(net.minecraft.server.MinecraftServer server, UUID id) {
        owner = id; var player = server.getPlayerList().getPlayer(id);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EAESingletons.PATTERN_MODIFIER));
        var host = new HostPatternModifier(EAESingletons.PATTERN_MODIFIER, player, MenuLocators.forHand(player, InteractionHand.MAIN_HAND));
        var pattern = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 6)),
                List.of(new GenericStack(AEItemKey.of(Items.IRON_NUGGET), 2)));
        pattern.set(DataComponents.CUSTOM_NAME, Component.literal("native ExtendedAE pattern"));
        host.getInventoryByName("patternInv").setItemDirect(0, pattern);
        host.getInventoryByName("replaceInv").setItemDirect(0, GenericStack.wrapInItemStack(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 3)));
        host.getInventoryByName("replaceInv").setItemDirect(1, GenericStack.wrapInItemStack(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 2)));
        player.inventoryMenu.broadcastChanges(); ready = true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.extendedAEValidation.enabled") || owner == null) return;
        var player = event.getServer().getPlayerList().getPlayer(owner);
        if (player != null && player.containerMenu instanceof ContainerPatternModifier menu) {
            var stack = menu.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().getItem();
            var pattern = new AEProcessingPattern(AEItemKey.of(stack));
            inputAmount = pattern.getSparseInputs().getFirst().amount(); outputAmount = pattern.getOutputs().getFirst().amount();
            var replacement = GenericStack.fromItemStack(menu.replaceWith.getItem()); replacementAmount = replacement == null ? -1 : replacement.amount();
        }
    }
    @EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Client {
        private static boolean loading, prepared, opened, capturing;
        private static int phase, ticks;
        private static long started;
        @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!Boolean.getBoolean("gtlcore.extendedAEValidation.enabled")) return;
            var client = Minecraft.getInstance(); if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5)) throw new AssertionError("Native ExtendedAE client timed out at " + phase);
            if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                loading = true; client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Native ExtendedAE world did not load"); }); return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null || capturing) return;
            var server = client.getSingleplayerServer();
            if (!prepared) {
                prepared = true; PatternModifierClientState.get().scope = 0;
                var id = client.player.getUUID(); server.execute(() -> prepare(server, id)); return;
            }
            if (!opened && ready && client.player.getMainHandItem().is(EAESingletons.PATTERN_MODIFIER)) {
                opened = true; server.execute(() -> MenuOpener.open(ContainerPatternModifier.TYPE, server.getPlayerList().getPlayer(owner), MenuLocators.forHand(server.getPlayerList().getPlayer(owner), InteractionHand.MAIN_HAND))); return;
            }
            if (++ticks < 20) return;
            if (phase == 3 && client.screen instanceof SetReplaceAmountScreen popup) {
                var entry = amount(popup); var field = ((NumberEntryWidgetAccessor) entry).getTextField();
                click(client, field.getX() + 5, field.getY() + 8, 0);
                for (int i = 0; i < 32; i++) key(client, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT);
                for (int i = 0; i < 32; i++) key(client, org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE);
                for (char c : Long.toString(Long.MAX_VALUE).toCharArray())
                    if (!net.neoforged.neoforge.client.ClientHooks.onScreenCharTypedPre(client.screen, c, 0) && !client.screen.charTyped(c, 0))
                        net.neoforged.neoforge.client.ClientHooks.onScreenCharTypedPost(client.screen, c, 0);
                if (entry.getLongValue().orElse(-1) != Long.MAX_VALUE) throw new AssertionError("Native ExtendedAE amount popup lost typed long digits: " + field.getValue());
                phase = 31; ticks = 0; return;
            }
            if (phase == 31 && client.screen instanceof SetReplaceAmountScreen popup) {
                if (amount(popup).getLongValue().orElse(-1) != Long.MAX_VALUE) throw new AssertionError("Native ExtendedAE amount changed before capture");
                bounded(client);
                capture(client, "native-extendedae-amount.png", () -> { click(client, find(client, button -> button.getMessage().getString().equals(appeng.core.localization.GuiText.Set.text().getString()))); phase = 4; ticks = 0; }); return;
            }
            if (!(client.screen instanceof GuiPatternModifier screen)) return;
            var menu = screen.getMenu(); var physical = menu.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().getItem();
            if (physical.isEmpty()) return;
            var pattern = new AEProcessingPattern(AEItemKey.of(physical));
            if (ticks == 80 && phase != 0 && phase != 3) {
                throw new AssertionError("Native ExtendedAE action stalled at " + phase + ": serverInput=" + inputAmount
                        + " clientInput=" + pattern.getSparseInputs().getFirst().amount() + " output=" + outputAmount
                        + " replace=" + replacementAmount + " page=" + menu.page);
            }
            if (phase == 0) {
                var scope = find(client, button -> contains(button.getMessage(), "gui.gtlcore.pattern_modify_scope."));
                if (!contains(scope.getMessage(), "gui.gtlcore.pattern_modify_scope.inputs")) { click(client, scope); ticks = 0; return; }
                click(client, find(client, button -> button instanceof ModifyIconButton icon && icon.getTooltipMessage().stream().anyMatch(text -> contains(text, "gui.gtlcore.pattern_recipe_multiply_3"))));
                phase = 1; ticks = 0;
            } else if (phase == 1 && inputAmount == 18 && outputAmount == 2 && pattern.getSparseInputs().getFirst().amount() == 18) {
                bounded(client); capture(client, "native-extendedae-scoped.png", () -> { click(client, find(client, button -> contains(button.getMessage(), "gui.extendedae.pattern_modifier.change"))); phase = 2; ticks = 0; });
            } else if (phase == 2 && menu.page == 1 && menu.replaceWith.hasItem()) {
                bounded(client); hover(client, screen.getGuiLeft() + menu.replaceWith.x + 8, screen.getGuiTop() + menu.replaceWith.y + 8);
                if (screen.getSlotUnderMouse() != menu.replaceWith) return;
                capture(client, "native-extendedae-replace.png", () -> { click(client, screen.getGuiLeft() + menu.replaceWith.x + 8, screen.getGuiTop() + menu.replaceWith.y + 8, 2); phase = 3; ticks = 0; });
            } else if (phase == 4 && replacementAmount == Long.MAX_VALUE && GenericStack.fromItemStack(menu.replaceWith.getItem()).amount() == Long.MAX_VALUE) {
                click(client, find(client, button -> contains(button.getMessage(), "gui.extendedae.pattern_modifier.replace_button"))); phase = 5; ticks = 0;
            } else if (phase == 5 && inputAmount == Long.MAX_VALUE && pattern.getSparseInputs().getFirst().amount() == Long.MAX_VALUE) {
                if (!pattern.getSparseInputs().getFirst().what().equals(AEItemKey.of(Items.GOLD_INGOT)) || outputAmount != 2
                        || !physical.get(DataComponents.CUSTOM_NAME).equals(Component.literal("native ExtendedAE pattern")))
                    throw new AssertionError("Native physical replacement lost keys, outputs or item metadata");
                capture(client, "native-extendedae-replaced.png", () -> {
                    LogManager.getLogger().info("GTL_NATIVE_EXTENDEDAE_CLIENT=PASS original_host=true scope_c2s_s2c=true page_c2s_s2c=true middle_click_popup=true typed_long_c2s_s2c=true replace_c2s_s2c=true metadata=true bounded=true");
                    client.player.closeContainer(); client.stop();
                });
            }
        }
        private static NumberEntryWidget amount(SetReplaceAmountScreen screen) {
            try { var field = SetReplaceAmountScreen.class.getDeclaredField("amount"); field.setAccessible(true); return (NumberEntryWidget) field.get(screen); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
        private static boolean contains(Component text, String key) { return text.getContents() instanceof TranslatableContents value && value.getKey().startsWith(key) || text.getSiblings().stream().anyMatch(child -> contains(child, key)); }
        private static Button find(Minecraft client, java.util.function.Predicate<Button> predicate) { return client.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button -> button.visible && predicate.test(button)).findFirst().orElseThrow(() -> new AssertionError("Native ExtendedAE button is absent at phase " + phase)); }
        private static void bounded(Minecraft client) { for (var child : client.screen.children()) if (child instanceof Button button && button.visible && (button.getX() < 0 || button.getY() < 0 || button.getX() + button.getWidth() > client.screen.width || button.getY() + button.getHeight() > client.screen.height)) throw new AssertionError("Native ExtendedAE control exceeds the screen"); }
        private static void click(Minecraft client, Button button) { click(client, button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0, 0); }
        private static void hover(Minecraft client, double x, double y) {
            try { var method = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class); method.setAccessible(true);
                method.invoke(client.mouseHandler, client.getWindow().getWindow(), x * client.getWindow().getScreenWidth() / client.screen.width, y * client.getWindow().getScreenHeight() / client.screen.height); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
        private static void click(Minecraft client, double x, double y, int button) {
            hover(client, x, y); client.screen.afterMouseAction();
            boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(client.screen, x, y, button);
            if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(client.screen, x, y, button, client.screen.mouseClicked(x, y, button));
            if (!handled) throw new AssertionError("Native ExtendedAE physical click was rejected at " + phase);
            if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(client.screen, x, y, button)) net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(client.screen, x, y, button, client.screen.mouseReleased(x, y, button));
        }
        private static void key(Minecraft client, int key) { if (!net.neoforged.neoforge.client.ClientHooks.onScreenKeyPressedPre(client.screen, key, 0, 0) && !client.screen.keyPressed(key, 0, 0)) net.neoforged.neoforge.client.ClientHooks.onScreenKeyPressedPost(client.screen, key, 0, 0); }
        private static void capture(Minecraft client, String name, Runnable next) { capturing = true; net.minecraft.client.Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), text -> client.execute(() -> { capturing = false; next.run(); })); }
    }
}
