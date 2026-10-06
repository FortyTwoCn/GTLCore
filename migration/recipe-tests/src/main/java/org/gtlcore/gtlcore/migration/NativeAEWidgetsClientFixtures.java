package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.client.gui.widget.NativeAEWidgetIngredients;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.integration.ae2.gui.*;
import com.gregtechceu.gtceu.integration.ae2.slot.*;
import appeng.api.stacks.*;
import brachy.modularui.api.IMuiScreen;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.integration.recipeviewer.handlers.IngredientProvider;
import brachy.modularui.screen.*;
import brachy.modularui.widget.WidgetTree;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import java.util.*;

/** Real native GT input menus and packets, plus physical native stack-display rendering and JEI lookup. */
@EventBusSubscriber(modid = "gtlcore_recipe_test")
public final class NativeAEWidgetsClientFixtures {
    private static final long LARGE = 9_007_199_254_740_993L;
    private static final int SLOT = 10;
    private static volatile BlockPos position;
    private static volatile boolean ready;
    private static volatile long serverAmount;
    private static UUID owner;
    private static boolean fluid;
    private static AEKey key(boolean isFluid) {
        var name = Component.literal("native AE widget component key");
        if (isFluid) {
            var stack = new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 1);
            stack.set(DataComponents.CUSTOM_NAME, name); return AEFluidKey.of(stack);
        }
        var stack = new ItemStack(Items.IRON_INGOT); stack.set(DataComponents.CUSTOM_NAME, name); return AEItemKey.of(stack);
    }
    private static IConfigurableSlotList slots(MetaMachine machine) {
        return fluid ? machine.getTrait(ExportOnlyAEFluidList.class) : machine.getTrait(ExportOnlyAEItemList.class);
    }
    private static void prepare(net.minecraft.server.MinecraftServer server, UUID id, boolean isFluid) {
        var player = server.getPlayerList().getPlayer(id); owner = id; fluid = isFluid; ready = false;
        position = player.blockPosition().offset(3, 0, 0);
        var entry = fluid ? GTAEMachines.FLUID_IMPORT_HATCH_ME : GTAEMachines.ITEM_IMPORT_BUS_ME;
        player.serverLevel().setBlockAndUpdate(position, entry.getBlock().defaultBlockState());
        var machine = (MetaMachine) player.serverLevel().getBlockEntity(position);
        ((IControllable) machine).setWorkingEnabled(false);
        var slot = slots(machine).getConfigurableSlot(SLOT);
        slot.setConfig(new GenericStack(key(fluid), LARGE));
        slot.setStock(new GenericStack(key(fluid), Long.MAX_VALUE));
        serverAmount = LARGE; ready = true;
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.aeWidgetsValidation.enabled") || owner == null || position == null) return;
        var player = event.getServer().getPlayerList().getPlayer(owner);
        if (player != null && player.serverLevel().getBlockEntity(position) instanceof MetaMachine machine) {
            var config = slots(machine).getConfigurableSlot(SLOT).getConfig();
            serverAmount = config == null ? -1 : config.amount();
        }
    }

    @EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Client {
        private static boolean loading, prepared, opened, capturing;
        private static int stage, phase, ticks;
        private static long started;
        @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!Boolean.getBoolean("gtlcore.aeWidgetsValidation.enabled")) return;
            var client = Minecraft.getInstance(); if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5)) throw new AssertionError("Native AE widgets timed out at " + stage + "/" + phase);
            if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                loading = true; client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Native AE widgets world did not load"); }); return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null || capturing) return;
            var server = client.getSingleplayerServer();
            if (!prepared) { prepared = true; var id = client.player.getUUID(); boolean next = stage == 1; server.execute(() -> prepare(server, id, next)); return; }
            var expectedBlock = fluid ? GTAEMachines.FLUID_IMPORT_HATCH_ME.getBlock() : GTAEMachines.ITEM_IMPORT_BUS_ME.getBlock();
            if (!opened && ready && client.level.getBlockState(position).is(expectedBlock)
                    && client.level.getBlockEntity(position) instanceof IMuiMachine) {
                opened = true; server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(owner);
                    ((IMuiMachine) player.serverLevel().getBlockEntity(position)).tryToOpenUI(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(position.getCenter(), Direction.NORTH, position, false));
                }); return;
            }
            if (!(client.screen instanceof IMuiScreen ui) || !ui.screen().getMainPanel().isValid() || ui.screen().getContext().getGraphics() == null || ++ticks < 20) return;
            var main = ui.screen().getMainPanel();
            if (stage == 2) {
                var slot = (AEStackDisplayWidget) WidgetTree.findFirstWithName(main, "nativeAEFluidDisplay");
                hover(client, ui, slot.getArea().x + 9, slot.getArea().y + 9);
                if (ticks < 30) return;
                checkProvider(slot, key(true), Long.MAX_VALUE);
                capture(client, "native-ae-stack-display.png", () -> {
                    LogManager.getLogger().info("GTL_NATIVE_AE_WIDGETS_CLIENT=PASS menus=2 long_components_s2c=true amount_popup_c2s_s2c=true scroll_c2s_s2c=true jei_item_fluid=true stock_hover=true native_stack_display=true fluid_units=true bounded=true");
                    client.setScreen(null); client.stop();
                }); return;
            }
            var widget = (AEConfigWidget) all(main).stream().filter(AEConfigWidget.class::isInstance).findFirst().orElseThrow();
            var sync = ui.screen().getSyncManager().getPanelSyncManager(main.getName()).findSyncHandler("ae_config_display", AEConfigSyncHandler.class);
            var config = sync.getClientConfig(SLOT);
            var stock = sync.getClientStock(SLOT);
            if (config == null || stock == null) return;
            if (!config.what().equals(key(fluid)) || !stock.what().equals(key(fluid)) || stock.amount() != Long.MAX_VALUE || sync.isAllowC2S())
                throw new AssertionError("Native AE S2C changed component keys, stock quantity or server ownership");
            double x = widget.getArea().x + 2 * 18 + 9, y = widget.getArea().y + 38 + 9;
            if (phase == 0) {
                if (config.amount() != LARGE) return;
                hover(client, ui, x, y); phase = 1; ticks = 0;
            } else if (phase == 1) {
                checkProvider(widget, key(fluid), LARGE);
                capture(client, "native-ae-input-" + (fluid ? "fluid" : "item") + "-config.png", () -> { click(client, ui, x, y); phase = 2; ticks = 0; });
            } else if (phase == 2) {
                var popup = ui.screen().getPanelManager().getOpenPanels().stream().filter(panel -> panel.getName().equals("ae_amount_editor")).findFirst().orElse(null);
                if (popup == null) return;
                bounded(client, popup);
                var field = (TextFieldWidget) all(popup).stream().filter(TextFieldWidget.class::isInstance).findFirst().orElseThrow();
                click(client, ui, field.getArea().x + 5, field.getArea().y + 9);
                if (!field.isFocused()) throw new AssertionError("Native popup text field did not receive focus");
                for (int i = 0; i < 32; i++) pressKey(client, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT);
                for (int i = 0; i < 32; i++) pressKey(client, org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE);
                for (char character : Long.toString(Long.MAX_VALUE).toCharArray()) {
                    if (!net.neoforged.neoforge.client.ClientHooks.onScreenCharTypedPre(client.screen, character, 0)
                            && !client.screen.charTyped(character, 0)) net.neoforged.neoforge.client.ClientHooks.onScreenCharTypedPost(client.screen, character, 0);
                }
                if (!field.getText().equals(Long.toString(Long.MAX_VALUE))) throw new AssertionError("Native text field lost typed long digits: " + field.getText());
                var button = all(popup).stream().filter(ButtonWidget.class::isInstance).toList().getLast();
                click(client, ui, button.getArea().x + 9, button.getArea().y + 9); phase = 3; ticks = 0;
            } else if (phase == 3 && serverAmount == Long.MAX_VALUE && config.amount() == Long.MAX_VALUE) {
                hover(client, ui, x, y);
                boolean handled = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Pre(client.screen, x, y, 0, -1)).isCanceled();
                if (!handled) handled = client.screen.mouseScrolled(x, y, 0, -1);
                if (!handled) throw new AssertionError("Native configuration rejected long quantity scroll");
                phase = 4; ticks = 0;
            } else if (phase == 3 && ticks == 60) {
                throw new AssertionError("Native popup submission did not update configuration: server=" + serverAmount + " client=" + config.amount());
            } else if (phase == 4 && serverAmount == Long.MAX_VALUE - 1 && config.amount() == Long.MAX_VALUE - 1) {
                hover(client, ui, x, y + 18); phase = 5; ticks = 0;
            } else if (phase == 5) {
                checkProvider(widget, key(fluid), Long.MAX_VALUE);
                capture(client, "native-ae-input-" + (fluid ? "fluid" : "item") + "-stock.png", () -> {
                    client.player.closeContainer(); phase = ticks = 0;
                    if (++stage == 2) {
                        var panel = new ModularPanel<>("native_ae_stack_displays").size(160, 90)
                                .child(new AEStackDisplayWidget(List.of(new GenericStack(key(false), LARGE)), 0).pos(40, 30).name("nativeAEItemDisplay"))
                                .child(new AEStackDisplayWidget(List.of(new GenericStack(key(true), Long.MAX_VALUE)), 0).pos(90, 30).name("nativeAEFluidDisplay"));
                        var screen = new ModularScreen("gtlcore_recipe_test", panel);
                        screen.getContext().setSettings(new UISettings()); client.setScreen(new ScreenWrapper(screen));
                    } else { prepared = opened = ready = false; }
                });
            }
        }
        private static List<IWidget> all(IWidget parent) { var list = new ArrayList<IWidget>(); list.add(parent); for (var child : parent.getChildren()) list.addAll(all(child)); return list; }
        private static void checkProvider(IWidget widget, AEKey key, long amount) {
            if (!(widget instanceof IngredientProvider<?> provider) || provider.getIngredients().isEmpty()) throw new AssertionError("Native widget lacks its recipe ingredient provider");
            var ingredient = provider.getIngredients().getStacks().getFirst();
            AEKey decoded = ingredient instanceof ItemStack item ? AEItemKey.of(item) : AEFluidKey.of((net.neoforged.neoforge.fluids.FluidStack) ingredient);
            if (!key.equals(decoded)) throw new AssertionError("Native ingredient provider lost item/fluid components");
            if (!(provider.ingredientOverride() instanceof mezz.jei.api.runtime.IClickableIngredient<?>)) throw new AssertionError("Native JEI did not expose the hovered item/fluid");
            if (widget instanceof AEConfigWidget config && config.ingredientClass() != ItemStack.class) throw new AssertionError("Ingredient hover changed the native ghost-item contract");
            if (key instanceof AEFluidKey && !NativeAEWidgetIngredients.amountText(new GenericStack(key, amount)).endsWith("B")) throw new AssertionError("Native fluid overlay lost bucket units");
        }
        private static void bounded(Minecraft client, IWidget widget) { var area = widget.getArea(); if (area.x < 0 || area.y < 0 || area.x + area.width > client.screen.width || area.y + area.height > client.screen.height) throw new AssertionError("Native AE popup exceeds screen"); }
        private static void hover(Minecraft client, IMuiScreen ui, double x, double y) {
            try {
                var callback = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class); callback.setAccessible(true);
                callback.invoke(client.mouseHandler, client.getWindow().getWindow(), x * client.getWindow().getScreenWidth() / client.screen.width, y * client.getWindow().getScreenHeight() / client.screen.height);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate();
        }
        private static void click(Minecraft client, IMuiScreen ui, double x, double y) {
            hover(client, ui, x, y); client.screen.afterMouseAction();
            boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(client.screen, x, y, 0);
            if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(client.screen, x, y, 0, client.screen.mouseClicked(x, y, 0));
            if (!handled) throw new AssertionError("Native AE click was rejected");
            if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(client.screen, x, y, 0)) net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(client.screen, x, y, 0, client.screen.mouseReleased(x, y, 0));
        }
        private static void pressKey(Minecraft client, int key) {
            if (!net.neoforged.neoforge.client.ClientHooks.onScreenKeyPressedPre(client.screen, key, 0, 0)
                    && !client.screen.keyPressed(key, 0, 0)) net.neoforged.neoforge.client.ClientHooks.onScreenKeyPressedPost(client.screen, key, 0, 0);
        }
        private static void capture(Minecraft client, String filename, Runnable next) { capturing = true; net.minecraft.client.Screenshot.grab(client.gameDirectory, filename, client.getMainRenderTarget(), message -> client.execute(() -> { capturing = false; next.run(); })); }
    }
}
