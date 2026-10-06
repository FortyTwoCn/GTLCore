package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;

import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanel;
import brachy.modularui.api.IMuiScreen;
import brachy.modularui.api.IPanelHandler;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.drawable.UITexture;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularContainerMenu;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import brachy.modularui.widgets.ButtonWidget;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class WirelessAeScreenHooks {

    private static final long RECENT_CLICK_MILLIS = 60000L;
    private static final long REQUEST_RETRY_MILLIS = 1000L;
    private static final long CLICK_REFRESH_DELAY_MILLIS = 250L;
    private static final int UI_REFRESH_TICKS = 20;
    private static int ticksUntilUiRefresh;
    private static final int WIRELESS_PAGE_WIDTH = 168;
    private static final int WIRELESS_PAGE_HEIGHT = 158;
    private static final int PANEL_MARGIN = 6;
    private static final int WIRELESS_HEADER_HEIGHT = 24;
    private static final int NETWORK_ROW_HEIGHT = 24;
    private static final int WIRELESS_SCROLLBAR_WIDTH = 6;
    private static final int WIRELESS_SCROLLBAR_GAP = 4;
    private static final ResourceLocation WIRELESS_TAB_TEXTURE = ResourceLocation.fromNamespaceAndPath("gtlcore", "textures/gui/wireless/tab_wireless_selected.png");

    private static final String[] POSITION_METHODS = {
            "getBlockPos",
            "getPos",
            "pos"
    };
    private static final String[] TARGET_METHODS = {
            "getGuiData",
            "getBlockEntity",
            "getTarget",
            "getModularUI",
            "getModularUIContainer",
            "getHolder",
            "getLocator",
            "getHost",
            "getPartHost",
            "getMenuHost",
            "getLogic",
            "getMachine",
            "getMetaMachine"
    };
    private static final String[] TARGET_FIELDS = {
            "modularUI",
            "holder",
            "blockEntity",
            "target",
            "host",
            "partHost",
            "locator",
            "logic",
            "machine",
            "metaMachine"
    };

    private static BlockPos recentClickedPos;
    private static Direction recentClickedSide;
    private static Vec3 recentClickedLocation;
    private static long recentClickedAtMillis;
    private static final Map<ReflectionKey, Optional<Method>> METHOD_CACHE = new HashMap<>();
    private static final Map<ReflectionKey, Optional<Field>> FIELD_CACHE = new HashMap<>();
    private static Screen cachedScreen;
    private static BlockPos cachedTargetPos;
    private static Screen cachedPositionScreen;
    private static AbstractContainerMenu cachedPositionMenu;
    private static List<BlockPos> cachedStaticTargetPositions = List.of();
    private static Screen embeddedScreen;
    private static BlockPos embeddedTargetPos;
    private static TargetHit embeddedTargetHit;
    private static List<WirelessAePackets.TargetNetworkEntry> embeddedEntries = List.of();
    private static WirelessAePackets.TargetNetworkEntry embeddedConnectedEntry;
    private static WirelessAePackets.TargetNetworkEntry embeddedDisconnectableEntry;
    private static boolean embeddedHasData;
    private static boolean embeddedLoading;
    private static int embeddedScrollOffset;
    private static boolean embeddedDraggingScrollbar;
    private static long embeddedRequestAtMillis;
    private static long embeddedRefreshAfterMillis;
    private static Screen installedScreen;
    private static MachineUIPanel installedPanel;
    private static BlockPos installedTargetPos;

    private WirelessAeScreenHooks() {}

    static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(WirelessAeScreenHooks::onRightClickBlock);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(WirelessAeScreenHooks::onLeftClickEmpty);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(WirelessAeScreenHooks::onScreenInit);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(WirelessAeScreenHooks::onClientTick);
    }

    private static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof org.gtlcore.gtlcore.integration.ae2.wireless.WirelessNetworkBindingToolItem) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new WirelessAePackets.ClearBindingToolPacket());
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        recentClickedPos = event.getPos();
        BlockHitResult hitResult = event.getHitVec();
        recentClickedSide = hitResult == null ? event.getFace() : hitResult.getDirection();
        recentClickedLocation = hitResult == null ? null : hitResult.getLocation();
        recentClickedAtMillis = System.currentTimeMillis();
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        installWirelessUiForScreen(event.getScreen());
        ticksUntilUiRefresh = UI_REFRESH_TICKS;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Screen screen = Minecraft.getInstance().screen;
        closeEmbeddedPanelIfScreenChanged(screen);
        if (!(screen instanceof IMuiScreen) || !(screen instanceof AbstractContainerScreen<?>)) {
            ticksUntilUiRefresh = 0;
            return;
        }
        // Retry delayed widgets/target synchronization and repair rebuilt tabs outside rendering.
        if (--ticksUntilUiRefresh <= 0) {
            ticksUntilUiRefresh = UI_REFRESH_TICKS;
            installWirelessUiForScreen(screen);
        }
    }

    private static void installWirelessUiForScreen(Screen screen) {
        closeEmbeddedPanelIfScreenChanged(screen);
        if (!(screen instanceof IMuiScreen modularScreen) || !(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        BlockPos targetPos = findAcceptedTargetPos(screen, containerScreen, level);
        if (targetPos != null) {
            installNativeWirelessWidgets(screen, modularScreen, targetPos);
        }
    }

    private static BlockPos findAcceptedTargetPos(Screen screen, AbstractContainerScreen<?> containerScreen,
                                                  Level level) {
        if (cachedScreen != screen) {
            cachedScreen = screen;
            cachedTargetPos = null;
            cachedPositionScreen = null;
            cachedPositionMenu = null;
            cachedStaticTargetPositions = List.of();
        }

        if (cachedTargetPos != null && shouldOfferWirelessTab(level, cachedTargetPos)) {
            return cachedTargetPos;
        }

        for (BlockPos candidate : findScreenTargetPositions(screen, containerScreen)) {
            if (shouldOfferWirelessTab(level, candidate)) {
                cachedTargetPos = candidate;
                return candidate;
            }
        }

        cachedTargetPos = null;
        return null;
    }

    private static List<BlockPos> findScreenTargetPositions(Screen screen, AbstractContainerScreen<?> containerScreen) {
        List<BlockPos> candidates = new ArrayList<>();
        if (containerScreen.getMenu() instanceof ModularContainerMenu menu && menu.getGuiData() instanceof PosGuiData data) {
            addCandidate(candidates, data.getBlockPos());
        }
        addCandidate(candidates, getRecentClickedPos());
        addCandidate(candidates, getCrosshairBlockPos());

        AbstractContainerMenu menu = containerScreen.getMenu();
        for (BlockPos pos : getStaticTargetPositions(screen, menu)) {
            addCandidate(candidates, pos);
        }
        return candidates;
    }

    private static List<BlockPos> getStaticTargetPositions(Screen screen, AbstractContainerMenu menu) {
        if (cachedPositionScreen == screen && cachedPositionMenu == menu) {
            return cachedStaticTargetPositions;
        }

        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : findTargetPositions(menu)) {
            addCandidate(positions, pos);
        }
        for (BlockPos pos : findTargetPositions(screen)) {
            addCandidate(positions, pos);
        }
        cachedPositionScreen = screen;
        cachedPositionMenu = menu;
        cachedStaticTargetPositions = List.copyOf(positions);
        return cachedStaticTargetPositions;
    }

    private static void installNativeWirelessWidgets(Screen screen, IMuiScreen ui, BlockPos targetPos) {
        if (!(ui.screen().getMainPanel() instanceof MachineUIPanel panel)) return;
        if (installedScreen == screen && installedPanel == panel && targetPos.equals(installedTargetPos)) return;
        var sidebar = panel.getRightConfiguratorPanel();
        if (sidebar.getChildren().stream().anyMatch(child -> "gtlcoreWirelessNetworksButton".equals(child.getName()))) return;
        var position = targetPos.immutable();
        var popup = IPanelHandler.simple(panel, (parent, player) -> {
            var networks = new ModularPanel<>("gtlcoreWirelessNetworks")
                .size(WIRELESS_PAGE_WIDTH + 12, WIRELESS_PAGE_HEIGHT + WIRELESS_HEADER_HEIGHT + 6)
                .disablePanelsBelow(true)
                .child(brachy.modularui.api.drawable.Text.lang("screen.gtlcore.wireless_target")
                        .asWidget().pos(6, 6).width(WIRELESS_PAGE_WIDTH - 18))
                .child(new WirelessNetworkPageWidget(screen, position).pos(6, WIRELESS_HEADER_HEIGHT))
                .onOpenAction(opened -> openEmbeddedPanel(screen, position))
                .onCloseAction(closed -> closeEmbeddedPanel());
            networks.child(new ButtonWidget<>().name("gtlcoreWirelessNetworksClose").right(4).top(4).size(16)
                    .overlay(brachy.modularui.api.drawable.Text.str("x"))
                    .onMousePressed((context, button) -> {
                        if (button != 0) return false;
                        networks.closeIfOpen(); return true;
                    }));
            return networks;
        }, true);
        sidebar.child(new ButtonWidget<>().name("gtlcoreWirelessNetworksButton").size(18)
                .overlay(UITexture.fullImage(WIRELESS_TAB_TEXTURE))
                .tooltip(tooltip -> tooltip.addLine(Component.translatable("tooltip.gtlcore.wireless_target.entry")))
                .onMousePressed((context, button) -> {
                    if (button != 0) return false;
                    popup.togglePanel(); return true;
                }));
        sidebar.scheduleResize();
        installedScreen = screen;
        installedPanel = panel;
        installedTargetPos = position;
    }

    private static boolean isEmbeddedPanelActive(Screen screen, BlockPos targetPos) {
        return embeddedScreen == screen && embeddedTargetPos != null && embeddedTargetPos.equals(targetPos);
    }

    private static void openEmbeddedPanel(Screen screen, BlockPos targetPos) {
        embeddedScreen = screen;
        embeddedTargetPos = targetPos.immutable();
        embeddedTargetHit = findTargetHit(embeddedTargetPos);
        embeddedEntries = List.of();
        embeddedConnectedEntry = null;
        embeddedDisconnectableEntry = null;
        embeddedHasData = false;
        embeddedLoading = false;
        embeddedScrollOffset = 0;
        embeddedDraggingScrollbar = false;
        embeddedRefreshAfterMillis = 0L;
        requestEmbeddedData(embeddedTargetPos);
    }

    private static void closeEmbeddedPanel() {
        embeddedScreen = null;
        embeddedTargetPos = null;
        embeddedTargetHit = null;
        embeddedEntries = List.of();
        embeddedConnectedEntry = null;
        embeddedDisconnectableEntry = null;
        embeddedHasData = false;
        embeddedLoading = false;
        embeddedScrollOffset = 0;
        embeddedDraggingScrollbar = false;
        embeddedRequestAtMillis = 0L;
        embeddedRefreshAfterMillis = 0L;
    }

    private static void closeEmbeddedPanelIfScreenChanged(Screen screen) {
        if (cachedScreen != screen) {
            cachedScreen = null;
            cachedTargetPos = null;
            cachedPositionScreen = null;
            cachedPositionMenu = null;
            cachedStaticTargetPositions = List.of();
        }
        if (embeddedScreen != null && embeddedScreen != screen) {
            closeEmbeddedPanel();
        }
        if (installedScreen != null && installedScreen != screen) {
            installedScreen = null;
            installedPanel = null;
            installedTargetPos = null;
        }
    }

    private static void ensureEmbeddedData(BlockPos targetPos) {
        long now = System.currentTimeMillis();
        if (embeddedRefreshAfterMillis > 0L && now >= embeddedRefreshAfterMillis) {
            embeddedRefreshAfterMillis = 0L;
            requestEmbeddedData(targetPos);
            return;
        }
        if (!embeddedHasData && (!embeddedLoading || now - embeddedRequestAtMillis > REQUEST_RETRY_MILLIS)) {
            requestEmbeddedData(targetPos);
        }
    }

    private static void requestEmbeddedData(BlockPos targetPos) {
        embeddedLoading = true;
        embeddedRequestAtMillis = System.currentTimeMillis();
        TargetHit hit = getEmbeddedTargetHit(targetPos);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new WirelessAePackets.RequestTargetNetworksPacket(targetPos, hit.side(), hit.location()));
    }

    static void receiveTargetNetworks(BlockPos targetPos, List<WirelessAePackets.TargetNetworkEntry> entries) {
        Minecraft.getInstance().execute(() -> {
            if (embeddedTargetPos == null || !embeddedTargetPos.equals(targetPos)) {
                return;
            }
            embeddedEntries = List.copyOf(entries);
            updateEmbeddedEntryCache();
            embeddedHasData = true;
            embeddedLoading = false;
            embeddedRefreshAfterMillis = 0L;
        });
    }

    private static void drawWirelessPage(GuiGraphics graphics, int x, int y, int width, int height,
                                         int mouseX, int mouseY) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        WirelessAeStyle.drawInsetPanel(
                graphics,
                x + PANEL_MARGIN,
                y + WIRELESS_HEADER_HEIGHT,
                width - PANEL_MARGIN * 2,
                height - WIRELESS_HEADER_HEIGHT - PANEL_MARGIN);

        graphics.drawString(
                font,
                Component.translatable("screen.gtlcore.wireless_target"),
                x + 8,
                y + 8,
                WirelessAeStyle.TEXT,
                false);

        int contentX = x + PANEL_MARGIN + 6;
        int contentY = y + WIRELESS_HEADER_HEIGHT + 8;
        int contentWidth = width - (PANEL_MARGIN + 6) * 2;
        WirelessAePackets.TargetNetworkEntry connected = getConnectedEntry();
        WirelessAePackets.TargetNetworkEntry disconnectable = getDisconnectableEntry();

        WirelessAeStyle.drawStatusLight(graphics, contentX, contentY, connected != null);
        WirelessAeStyle.drawTrimmedString(
                graphics,
                font,
                connected == null ? Component.translatable("label.gtlcore.wireless_target.current_disconnected") : Component.translatable("label.gtlcore.wireless_target.current_connected", connected.name()),
                contentX + 14,
                contentY + 1,
                contentWidth - 14,
                connected == null ? WirelessAeStyle.MUTED_TEXT : WirelessAeStyle.ONLINE_TEXT);
        int listY = contentY + 18;

        if (!embeddedHasData) {
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    font,
                    Component.translatable("label.gtlcore.wireless_target.loading"),
                    contentX,
                    listY,
                    contentWidth,
                    WirelessAeStyle.MUTED_TEXT);
            return;
        }

        if (embeddedEntries.isEmpty()) {
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    font,
                    Component.translatable("label.gtlcore.wireless_target.no_networks"),
                    contentX,
                    listY,
                    contentWidth,
                    WirelessAeStyle.WARNING_TEXT);
            return;
        }

        int bottomReserve = disconnectable == null ? 8 : 36;
        int maxRows = Math.max(1, (y + height - bottomReserve - listY) / NETWORK_ROW_HEIGHT);
        embeddedScrollOffset = WirelessAeStyle.clampScrollOffset(embeddedScrollOffset, embeddedEntries.size(), maxRows);
        boolean hasScrollbar = WirelessAeStyle.needsScrollbar(embeddedEntries.size(), maxRows);
        int rowWidth = contentWidth - (hasScrollbar ? WIRELESS_SCROLLBAR_WIDTH + WIRELESS_SCROLLBAR_GAP : 0);
        int rows = Math.min(maxRows, Math.max(0, embeddedEntries.size() - embeddedScrollOffset));
        boolean lockedByCableConnection = isLockedByCableConnection();
        for (int i = 0; i < rows; i++) {
            WirelessAePackets.TargetNetworkEntry entry = embeddedEntries.get(embeddedScrollOffset + i);
            int rowY = listY + i * NETWORK_ROW_HEIGHT;
            boolean hovered = !lockedByCableConnection && !entry.connected() && isInsideRect(mouseX, mouseY, contentX, rowY, rowWidth, 20);
            drawNetworkRow(graphics, contentX, rowY, rowWidth, entry, hovered);
        }

        if (hasScrollbar) {
            WirelessAeStyle.drawScrollbar(
                    graphics,
                    contentX + contentWidth - WIRELESS_SCROLLBAR_WIDTH,
                    listY,
                    maxRows * NETWORK_ROW_HEIGHT - 4,
                    embeddedEntries.size(),
                    maxRows,
                    embeddedScrollOffset);
        }

        if (disconnectable != null) {
            int disconnectY = y + height - 30;
            WirelessAeStyle.drawSeparator(graphics, contentX, disconnectY - 5, contentWidth);
            boolean hovered = isInsideRect(mouseX, mouseY, contentX, disconnectY, contentWidth, 20);
            drawDisconnectRow(graphics, contentX, disconnectY, contentWidth, hovered);
        }
    }

    private static void drawNetworkRow(GuiGraphics graphics, int x, int y, int width,
                                       WirelessAePackets.TargetNetworkEntry entry, boolean hovered) {
        WirelessAeStyle.drawButtonBackground(graphics, x, y, width, 20,
                true, entry.connected(), false, hovered);

        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        int textX = x + 10;
        int checkSpace = entry.connected() ? 24 : 0;
        WirelessAeStyle.drawTrimmedString(
                graphics,
                font,
                Component.literal(entry.name()),
                textX,
                y + 5,
                Math.max(8, width - 20 - checkSpace),
                WirelessAeStyle.TEXT);
    }

    private static void drawDisconnectRow(GuiGraphics graphics, int x, int y, int width, boolean hovered) {
        WirelessAeStyle.drawButtonBackground(graphics, x, y, width, 20,
                true, false, true, hovered);
        WirelessAeStyle.drawTrimmedString(
                graphics,
                Minecraft.getInstance().font,
                Component.translatable("button.gtlcore.wireless_target.disconnect"),
                x + 8,
                y + 5,
                width - 16,
                WirelessAeStyle.WARNING_TEXT);
    }

    private static void handleWirelessPageClick(BlockPos targetPos, int x, int y, int width, int height,
                                                double mouseX, double mouseY) {
        if (!isInsideRect(mouseX, mouseY, x, y, width, height) || !embeddedHasData) {
            return;
        }

        int contentX = x + PANEL_MARGIN + 6;
        int contentY = y + WIRELESS_HEADER_HEIGHT + 8;
        int contentWidth = width - (PANEL_MARGIN + 6) * 2;
        int listY = contentY + 18;
        WirelessAePackets.TargetNetworkEntry connected = getConnectedEntry();
        WirelessAePackets.TargetNetworkEntry disconnectable = getDisconnectableEntry();
        int bottomReserve = disconnectable == null ? 8 : 36;
        int maxRows = Math.max(1, (y + height - bottomReserve - listY) / NETWORK_ROW_HEIGHT);
        embeddedScrollOffset = WirelessAeStyle.clampScrollOffset(embeddedScrollOffset, embeddedEntries.size(), maxRows);
        boolean hasScrollbar = WirelessAeStyle.needsScrollbar(embeddedEntries.size(), maxRows);
        if (hasScrollbar && isInsideRect(mouseX, mouseY, contentX + contentWidth - WIRELESS_SCROLLBAR_WIDTH, listY, WIRELESS_SCROLLBAR_WIDTH, maxRows * NETWORK_ROW_HEIGHT - 4)) {
            embeddedDraggingScrollbar = true;
            updateEmbeddedScrollOffsetFromMouse(mouseY, listY, maxRows * NETWORK_ROW_HEIGHT - 4, maxRows);
            return;
        }

        int rowWidth = contentWidth - (hasScrollbar ? WIRELESS_SCROLLBAR_WIDTH + WIRELESS_SCROLLBAR_GAP : 0);
        int rows = Math.min(maxRows, Math.max(0, embeddedEntries.size() - embeddedScrollOffset));
        boolean lockedByCableConnection = isLockedByCableConnection();
        for (int i = 0; i < rows; i++) {
            int rowY = listY + i * NETWORK_ROW_HEIGHT;
            if (isInsideRect(mouseX, mouseY, contentX, rowY, rowWidth, 20)) {
                if (lockedByCableConnection) {
                    return;
                }
                connectEmbeddedEntry(targetPos, embeddedEntries.get(embeddedScrollOffset + i));
                return;
            }
        }

        if (disconnectable != null) {
            int disconnectY = y + height - 30;
            if (isInsideRect(mouseX, mouseY, contentX, disconnectY, contentWidth, 20)) {
                disconnectEmbeddedEntry(targetPos, disconnectable);
            }
        }
    }

    private static boolean scrollEmbedded(double wheelDelta, int x, int y, int width, int height,
                                          double mouseX, double mouseY) {
        if (!embeddedHasData || embeddedEntries.isEmpty() || !isInsideRect(mouseX, mouseY, x, y, width, height)) {
            return false;
        }

        int listY = y + WIRELESS_HEADER_HEIGHT + 8 + 18;
        WirelessAePackets.TargetNetworkEntry disconnectable = getDisconnectableEntry();
        int bottomReserve = disconnectable == null ? 8 : 36;
        int visibleRows = Math.max(1, (y + height - bottomReserve - listY) / NETWORK_ROW_HEIGHT);
        int nextOffset = WirelessAeStyle.clampScrollOffset(
                embeddedScrollOffset - (int) Math.signum(wheelDelta),
                embeddedEntries.size(),
                visibleRows);
        if (nextOffset == embeddedScrollOffset) {
            return false;
        }
        embeddedScrollOffset = nextOffset;
        return true;
    }

    private static boolean updateEmbeddedScrollbarDrag(double mouseY, int x, int y, int width, int height) {
        if (!embeddedDraggingScrollbar) {
            return false;
        }
        int listY = y + WIRELESS_HEADER_HEIGHT + 8 + 18;
        WirelessAePackets.TargetNetworkEntry disconnectable = getDisconnectableEntry();
        int bottomReserve = disconnectable == null ? 8 : 36;
        int visibleRows = Math.max(1, (y + height - bottomReserve - listY) / NETWORK_ROW_HEIGHT);
        updateEmbeddedScrollOffsetFromMouse(mouseY, listY, visibleRows * NETWORK_ROW_HEIGHT - 4, visibleRows);
        return true;
    }

    private static void updateEmbeddedScrollOffsetFromMouse(double mouseY, int listY, int scrollbarHeight,
                                                            int visibleRows) {
        embeddedScrollOffset = WirelessAeStyle.scrollbarOffsetFromMouse(
                mouseY,
                listY,
                scrollbarHeight,
                embeddedEntries.size(),
                visibleRows);
    }

    private static void connectEmbeddedEntry(BlockPos targetPos, WirelessAePackets.TargetNetworkEntry entry) {
        if (!entry.connected()) {
            TargetHit hit = getEmbeddedTargetHit(targetPos);
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new WirelessAePackets.ConnectTargetPacket(
                            targetPos,
                            hit.side(),
                            hit.location(),
                            entry.frequency(),
                            false));
            markEmbeddedConnection(entry.frequency());
            scheduleEmbeddedRefresh();
        }
    }

    private static void disconnectEmbeddedEntry(BlockPos targetPos, WirelessAePackets.TargetNetworkEntry entry) {
        TargetHit hit = getEmbeddedTargetHit(targetPos);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new WirelessAePackets.ConnectTargetPacket(
                        targetPos,
                        hit.side(),
                        hit.location(),
                        entry.frequency(),
                        true));
        markEmbeddedConnection(null);
        scheduleEmbeddedRefresh();
    }

    private static void markEmbeddedConnection(java.util.UUID frequency) {
        List<WirelessAePackets.TargetNetworkEntry> updated = new ArrayList<>(embeddedEntries.size());
        for (WirelessAePackets.TargetNetworkEntry entry : embeddedEntries) {
            updated.add(new WirelessAePackets.TargetNetworkEntry(
                    entry.frequency(),
                    entry.name(),
                    entry.frequency().equals(frequency),
                    entry.frequency().equals(frequency)));
        }
        embeddedEntries = List.copyOf(updated);
        updateEmbeddedEntryCache();
        embeddedHasData = true;
    }

    private static void scheduleEmbeddedRefresh() {
        embeddedLoading = false;
        embeddedRefreshAfterMillis = System.currentTimeMillis() + CLICK_REFRESH_DELAY_MILLIS;
    }

    private static WirelessAePackets.TargetNetworkEntry getConnectedEntry() {
        return embeddedConnectedEntry;
    }

    private static WirelessAePackets.TargetNetworkEntry getDisconnectableEntry() {
        return embeddedDisconnectableEntry;
    }

    private static void updateEmbeddedEntryCache() {
        embeddedConnectedEntry = null;
        embeddedDisconnectableEntry = null;
        for (WirelessAePackets.TargetNetworkEntry entry : embeddedEntries) {
            if (entry.connected() && embeddedConnectedEntry == null) {
                embeddedConnectedEntry = entry;
            }
            if (entry.disconnectable() && embeddedDisconnectableEntry == null) {
                embeddedDisconnectableEntry = entry;
            }
            if (embeddedConnectedEntry != null && embeddedDisconnectableEntry != null) {
                return;
            }
        }
    }

    private static boolean isLockedByCableConnection() {
        return getConnectedEntry() != null && getDisconnectableEntry() == null;
    }

    private static boolean isInsideRect(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private static BlockPos getRecentClickedPos() {
        if (recentClickedPos == null || System.currentTimeMillis() - recentClickedAtMillis > RECENT_CLICK_MILLIS) {
            return null;
        }
        return recentClickedPos;
    }

    private static TargetHit getEmbeddedTargetHit(BlockPos targetPos) {
        if (embeddedTargetHit != null && embeddedTargetPos != null && embeddedTargetPos.equals(targetPos)) {
            return embeddedTargetHit;
        }
        TargetHit hit = findTargetHit(targetPos);
        return hit == null ? TargetHit.EMPTY : hit;
    }

    private static TargetHit findTargetHit(BlockPos targetPos) {
        if (targetPos == null) {
            return TargetHit.EMPTY;
        }

        if (Minecraft.getInstance().hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && targetPos.equals(hit.getBlockPos())) {
            return new TargetHit(hit.getDirection(), hit.getLocation());
        }

        if (targetPos.equals(recentClickedPos) && System.currentTimeMillis() - recentClickedAtMillis <= RECENT_CLICK_MILLIS) {
            return new TargetHit(recentClickedSide, recentClickedLocation);
        }
        return TargetHit.EMPTY;
    }

    private static BlockPos getCrosshairBlockPos() {
        if (Minecraft.getInstance().hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getBlockPos();
        }
        return null;
    }

    private static boolean shouldOfferWirelessTab(Level level, BlockPos pos) {
        return WirelessAeNetworkRuntime.canOpenWirelessTargetMenu(level, pos);
    }

    private static List<BlockPos> findTargetPositions(Object target) {
        List<BlockPos> positions = new ArrayList<>();
        collectTargetPositions(target, positions, java.util.Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        return positions;
    }

    private static void collectTargetPositions(Object target, List<BlockPos> positions, Set<Object> visited,
                                               int depth) {
        if (target == null || depth > 5 || !visited.add(target)) {
            return;
        }

        if (target instanceof BlockPos pos) {
            addCandidate(positions, pos);
            return;
        }
        if (target instanceof BlockEntity blockEntity) {
            addCandidate(positions, blockEntity.getBlockPos());
        }
        if (target instanceof Optional<?> optional) {
            optional.ifPresent(value -> collectTargetPositions(value, positions, visited, depth + 1));
            return;
        }

        for (String methodName : POSITION_METHODS) {
            Object result = invokeNoArg(target, methodName);
            if (result instanceof BlockPos pos) {
                addCandidate(positions, pos);
            }
        }

        for (String methodName : TARGET_METHODS) {
            collectTargetPositions(invokeNoArg(target, methodName), positions, visited, depth + 1);
        }

        for (String fieldName : TARGET_FIELDS) {
            collectTargetPositions(readField(target, fieldName), positions, visited, depth + 1);
        }
    }

    private static void addCandidate(List<BlockPos> candidates, BlockPos pos) {
        if (pos == null) {
            return;
        }
        BlockPos immutable = pos.immutable();
        for (BlockPos candidate : candidates) {
            if (candidate.equals(immutable)) {
                return;
            }
        }
        candidates.add(immutable);
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        Method method = findNoArgMethod(target.getClass(), methodName);
        if (method == null) {
            return null;
        }

        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Method findNoArgMethod(Class<?> type, String methodName) {
        return METHOD_CACHE.computeIfAbsent(
                new ReflectionKey(type, methodName),
                WirelessAeScreenHooks::findNoArgMethodUncached)
                .orElse(null);
    }

    private static Optional<Method> findNoArgMethodUncached(ReflectionKey key) {
        Class<?> type = key.type();
        String methodName = key.name();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                if (method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    return Optional.of(method);
                }
            } catch (NoSuchMethodException ignored) {
                // Try parent class.
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }

        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }

        try {
            return field.get(target);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String fieldName) {
        return FIELD_CACHE.computeIfAbsent(
                new ReflectionKey(type, fieldName),
                WirelessAeScreenHooks::findFieldUncached)
                .orElse(null);
    }

    private static Optional<Field> findFieldUncached(ReflectionKey key) {
        Class<?> type = key.type();
        String fieldName = key.name();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException ignored) {
                // Try parent class.
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private record ReflectionKey(Class<?> type, String name) {}

    private static final class WirelessNetworkPageWidget extends Widget<WirelessNetworkPageWidget> implements Interactable {
        private final Screen screen;
        private final BlockPos targetPos;

        private WirelessNetworkPageWidget(Screen screen, BlockPos targetPos) {
            this.screen = screen;
            this.targetPos = targetPos.immutable();
            name("gtlcoreWirelessNetworksPage");
            size(WIRELESS_PAGE_WIDTH, WIRELESS_PAGE_HEIGHT);
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            if (Minecraft.getInstance().screen != screen) return;
            ensureEmbeddedData(targetPos);
            drawWirelessPage(context.getGraphics(), 0, 0, getArea().width, getArea().height,
                    context.getMouseX(), context.getMouseY());
        }

        @Override
        public Result onMousePressed(int button) {
            if (button != 0 || Minecraft.getInstance().screen != screen) return Result.IGNORE;
            handleWirelessPageClick(targetPos, 0, 0, getArea().width, getArea().height,
                    getContext().getMouseX(), getContext().getMouseY());
            Interactable.playButtonClickSound();
            return Result.SUCCESS;
        }

        @Override
        public void onMouseDrag(int button, double dragX, double dragY) {
            updateEmbeddedScrollbarDrag(getContext().getMouseY(), 0, 0, getArea().width, getArea().height);
        }

        @Override
        public boolean onMouseReleased(int button) {
            embeddedDraggingScrollbar = false;
            return true;
        }

        @Override
        public boolean onMouseScrolled(double horizontalDelta, double delta) {
            scrollEmbedded(delta, 0, 0, getArea().width, getArea().height,
                    getContext().getMouseX(), getContext().getMouseY());
            return true;
        }
    }

    private record TargetHit(Direction side, Vec3 location) {

        private static final TargetHit EMPTY = new TargetHit(null, null);
    }

}
