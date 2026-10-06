package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.common.data.GTLCreativeModeTabs;
import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberManagerTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberManagerTerminalPart;
import org.gtlcore.gtlcore.integration.ae2.chamber.WirelessMEChamberManagerMenuHost;
import org.gtlcore.gtlcore.integration.ae2.chamber.WirelessMEChamberManagerTerminalItem;
import org.gtlcore.gtlcore.integration.ae2.emitter.EmitterManagerTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.emitter.EmitterManagerTerminalPart;
import org.gtlcore.gtlcore.integration.ae2.emitter.WirelessEmitterManagerMenuHost;
import org.gtlcore.gtlcore.integration.ae2.emitter.WirelessEmitterManagerTerminalItem;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadSelectionMenu;
import org.gtlcore.gtlcore.integration.ae2.patternrelay.PatternRelayItem;
import org.gtlcore.gtlcore.integration.ae2.patternrelay.PatternRelayPart;
import org.gtlcore.gtlcore.integration.ae2.tag.TagViewCellItem;
import org.gtlcore.gtlcore.integration.ae2.tag.TagViewCellMenu;
import org.gtlcore.gtlcore.integration.ae2.throughput.METhroughputMonitorPart;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputMonitorTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputMonitorTerminalPart;
import org.gtlcore.gtlcore.integration.ae2.throughput.WirelessThroughputMonitorMenuHost;
import org.gtlcore.gtlcore.integration.ae2.throughput.WirelessThroughputMonitorTerminalItem;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import appeng.api.features.GridLinkables;
import appeng.items.parts.PartItem;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.MenuOpener;
import de.mari_023.ae2wtlib.api.registration.AddTerminalEvent;
import de.mari_023.ae2wtlib.api.gui.Icon;
import com.google.common.base.Suppliers;
import java.util.function.Supplier;

public final class GTLWirelessAeContent {

    private static final Supplier<WirelessThroughputMonitorTerminalItem> THROUGHPUT_TERMINAL_ITEM =
            Suppliers.memoize(WirelessThroughputMonitorTerminalItem::new);
    private static final Supplier<WirelessEmitterManagerTerminalItem> EMITTER_TERMINAL_ITEM =
            Suppliers.memoize(WirelessEmitterManagerTerminalItem::new);
    private static final Supplier<WirelessMEChamberManagerTerminalItem> CHAMBER_TERMINAL_ITEM =
            Suppliers.memoize(WirelessMEChamberManagerTerminalItem::new);

    private static final DeferredRegister<MachineDefinition> MACHINE_DEFINITIONS = DeferredRegister.create(GTRegistries.MACHINES, GTLCore.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, GTLCore.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, GTLCore.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, GTLCore.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(BuiltInRegistries.MENU, GTLCore.MOD_ID);
    private static final MenuType<ThroughputMonitorTerminalMenu> WIRELESS_THROUGHPUT_MONITOR_TERMINAL_MENU_TYPE = IMenuTypeExtension.create(ThroughputMonitorTerminalMenu::createWirelessClientMenu);
    private static final MenuType<EmitterManagerTerminalMenu> WIRELESS_EMITTER_MANAGER_TERMINAL_MENU_TYPE = IMenuTypeExtension.create(EmitterManagerTerminalMenu::createWirelessClientMenu);
    private static final MenuType<MEChamberManagerTerminalMenu> WIRELESS_ME_CHAMBER_MANAGER_TERMINAL_MENU_TYPE = IMenuTypeExtension.create(MEChamberManagerTerminalMenu::createWirelessClientMenu);
    private static final MenuType<TagViewCellMenu> TAG_VIEW_CELL_MENU_TYPE = IMenuTypeExtension.create(TagViewCellMenu::createClientMenu);

    public static final DeferredHolder<Block, WirelessNetworkCoreBlock> WIRELESS_NETWORK_CORE = BLOCKS.register(
            "wireless_network_core",
            WirelessNetworkCoreBlock::new);

    public static final DeferredHolder<Item, MetaMachineItem> WIRELESS_NETWORK_CORE_ITEM = ITEMS.register(
            "wireless_network_core",
            () -> new MetaMachineItem(WIRELESS_NETWORK_CORE.get(), new Item.Properties()));

    public static final DeferredHolder<MachineDefinition, MachineDefinition> WIRELESS_NETWORK_CORE_DEFINITION = MACHINE_DEFINITIONS.register(
            "wireless_network_core", WirelessNetworkCoreBlockEntity::createDefinition);

    public static final DeferredHolder<Block, Block> WIRELESS_NETWORK_BOOKMARK = BLOCKS.register(
            "wireless_network_bookmark",
            () -> new WirelessNetworkBookmarkBlock());

    public static final DeferredHolder<Item, Item> WIRELESS_NETWORK_BOOKMARK_ITEM = ITEMS.register(
            "wireless_network_bookmark",
            () -> new BlockItem(WIRELESS_NETWORK_BOOKMARK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, PatternRelayItem> PATTERN_RELAY = ITEMS.register(
            "me_pattern_relay",
            () -> new PatternRelayItem(new Item.Properties()));

    public static final DeferredHolder<Item, WirelessNetworkBindingToolItem> WIRELESS_NETWORK_BINDING_TOOL = ITEMS.register(
            "wireless_network_binding_tool",
            WirelessNetworkBindingToolItem::new);

    public static final DeferredHolder<Item, PartItem<METhroughputMonitorPart>> THROUGHPUT_MONITOR = ITEMS.register(
            "throughput_monitor",
            () -> new PartItem<>(
                    new Item.Properties(),
                    METhroughputMonitorPart.class,
                    METhroughputMonitorPart::new));

    public static final DeferredHolder<Item, Item> THROUGHPUT_MONITOR_CONFIGURATOR = ITEMS.register(
            "throughput_monitor_configurator",
            () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, TagViewCellItem> TAG_VIEW_CELL = ITEMS.register(
            "tag_view_cell",
            TagViewCellItem::new);

    public static final DeferredHolder<Item, PartItem<ThroughputMonitorTerminalPart>> THROUGHPUT_MONITOR_TERMINAL = ITEMS.register(
            "throughput_monitor_terminal",
            () -> new PartItem<>(
                    new Item.Properties(),
                    ThroughputMonitorTerminalPart.class,
                    ThroughputMonitorTerminalPart::new));

    public static final DeferredHolder<Item, PartItem<EmitterManagerTerminalPart>> EMITTER_MANAGER_TERMINAL = ITEMS.register(
            "emitter_manager_terminal",
            () -> new PartItem<>(
                    new Item.Properties(),
                    EmitterManagerTerminalPart.class,
                    EmitterManagerTerminalPart::new));

    public static final DeferredHolder<Item, PartItem<MEChamberManagerTerminalPart>> ME_CHAMBER_MANAGER_TERMINAL = ITEMS.register(
            "me_chamber_manager_terminal",
            () -> new PartItem<>(
                    new Item.Properties(),
                    MEChamberManagerTerminalPart.class,
                    MEChamberManagerTerminalPart::new));

    public static final DeferredHolder<Item, WirelessThroughputMonitorTerminalItem> WIRELESS_THROUGHPUT_MONITOR_TERMINAL = ITEMS.register(
            "wireless_throughput_monitor_terminal",
            THROUGHPUT_TERMINAL_ITEM);

    public static final DeferredHolder<Item, WirelessEmitterManagerTerminalItem> WIRELESS_EMITTER_MANAGER_TERMINAL = ITEMS.register(
            "wireless_emitter_manager_terminal",
            EMITTER_TERMINAL_ITEM);

    public static final DeferredHolder<Item, WirelessMEChamberManagerTerminalItem> WIRELESS_ME_CHAMBER_MANAGER_TERMINAL = ITEMS.register(
            "wireless_me_chamber_manager_terminal",
            CHAMBER_TERMINAL_ITEM);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessNetworkCoreBlockEntity>> WIRELESS_NETWORK_CORE_BE = BLOCK_ENTITY_TYPES.register(
            "wireless_network_core",
            () -> BlockEntityType.Builder.of(
                    WirelessNetworkCoreBlockEntity::new,
                    WIRELESS_NETWORK_CORE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessNetworkBookmarkBlockEntity>> WIRELESS_NETWORK_BOOKMARK_BE = BLOCK_ENTITY_TYPES.register(
            "wireless_network_bookmark",
            () -> BlockEntityType.Builder.of(
                    WirelessNetworkBookmarkBlockEntity::new,
                    WIRELESS_NETWORK_BOOKMARK.get())
                    .build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<WirelessNetworkCoreMenu>> WIRELESS_NETWORK_CORE_MENU = MENU_TYPES.register(
            "wireless_network_core",
            () -> IMenuTypeExtension.create(WirelessNetworkCoreMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<WirelessNetworkBookmarkMenu>> WIRELESS_NETWORK_BOOKMARK_MENU = MENU_TYPES.register(
            "wireless_network_bookmark",
            () -> IMenuTypeExtension.create(WirelessNetworkBookmarkMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<WirelessAeTargetMenu>> WIRELESS_AE_TARGET_MENU = MENU_TYPES.register(
            "wireless_ae_target",
            () -> IMenuTypeExtension.create(WirelessAeTargetMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<PatternQuickUploadSelectionMenu>> PATTERN_QUICK_UPLOAD_SELECTION_MENU = MENU_TYPES.register(
            "pattern_quick_upload_selection",
            () -> IMenuTypeExtension.create(PatternQuickUploadSelectionMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ThroughputMonitorTerminalMenu>> THROUGHPUT_MONITOR_TERMINAL_MENU = MENU_TYPES.register(
            "throughput_monitor_terminal",
            () -> IMenuTypeExtension.create(ThroughputMonitorTerminalMenu::createWiredClientMenu));

    public static final DeferredHolder<MenuType<?>, MenuType<ThroughputMonitorTerminalMenu>> WIRELESS_THROUGHPUT_MONITOR_TERMINAL_MENU = MENU_TYPES.register(
            "wireless_throughput_monitor_terminal",
            () -> WIRELESS_THROUGHPUT_MONITOR_TERMINAL_MENU_TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<EmitterManagerTerminalMenu>> EMITTER_MANAGER_TERMINAL_MENU = MENU_TYPES.register(
            "emitter_manager_terminal",
            () -> IMenuTypeExtension.create(EmitterManagerTerminalMenu::createWiredClientMenu));

    public static final DeferredHolder<MenuType<?>, MenuType<EmitterManagerTerminalMenu>> WIRELESS_EMITTER_MANAGER_TERMINAL_MENU = MENU_TYPES.register(
            "wireless_emitter_manager_terminal",
            () -> WIRELESS_EMITTER_MANAGER_TERMINAL_MENU_TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<MEChamberManagerTerminalMenu>> ME_CHAMBER_MANAGER_TERMINAL_MENU = MENU_TYPES.register(
            "me_chamber_manager_terminal",
            () -> IMenuTypeExtension.create(MEChamberManagerTerminalMenu::createWiredClientMenu));

    public static final DeferredHolder<MenuType<?>, MenuType<MEChamberManagerTerminalMenu>> WIRELESS_ME_CHAMBER_MANAGER_TERMINAL_MENU = MENU_TYPES.register(
            "wireless_me_chamber_manager_terminal",
            () -> WIRELESS_ME_CHAMBER_MANAGER_TERMINAL_MENU_TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<TagViewCellMenu>> TAG_VIEW_CELL_MENU = MENU_TYPES.register(
            "tag_view_cell",
            () -> TAG_VIEW_CELL_MENU_TYPE);

    private GTLWirelessAeContent() {}

    public static void register(IEventBus modBus) {
        AddTerminalEvent.register(GTLWirelessAeContent::registerTerminals);
        PatternRelayPart.registerModels();
        ThroughputMonitorTerminalPart.registerModels();
        EmitterManagerTerminalPart.registerModels();
        MEChamberManagerTerminalPart.registerModels();
        MACHINE_DEFINITIONS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        modBus.addListener(GTLWirelessAeContent::onRegisterEvent);
        modBus.addListener(GTLWirelessAeContent::addCreativeTabItems);
        modBus.addListener((RegisterCapabilitiesEvent event) -> WIRELESS_NETWORK_CORE.get().attachCapabilities(event));
    }

    private static void onRegisterEvent(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }

        WirelessThroughputMonitorTerminalItem throughputItem = WIRELESS_THROUGHPUT_MONITOR_TERMINAL.get();
        GridLinkables.register(throughputItem, WirelessTerminalItem.LINKABLE_HANDLER);
        MenuOpener.addOpener(
                WIRELESS_THROUGHPUT_MONITOR_TERMINAL_MENU_TYPE,
                ThroughputMonitorTerminalMenu::openWireless);
        WirelessEmitterManagerTerminalItem emitterItem = WIRELESS_EMITTER_MANAGER_TERMINAL.get();
        GridLinkables.register(emitterItem, WirelessTerminalItem.LINKABLE_HANDLER);
        MenuOpener.addOpener(
                WIRELESS_EMITTER_MANAGER_TERMINAL_MENU_TYPE,
                EmitterManagerTerminalMenu::openWireless);
        WirelessMEChamberManagerTerminalItem chamberItem = WIRELESS_ME_CHAMBER_MANAGER_TERMINAL.get();
        GridLinkables.register(chamberItem, WirelessTerminalItem.LINKABLE_HANDLER);
        MenuOpener.addOpener(
                WIRELESS_ME_CHAMBER_MANAGER_TERMINAL_MENU_TYPE,
                MEChamberManagerTerminalMenu::openWireless);
        MenuOpener.addOpener(TAG_VIEW_CELL_MENU_TYPE, TagViewCellMenu::open);
    }

    private static void registerTerminals(AddTerminalEvent event) {
        // Both registrations share one instance, first created during the native ITEM registry event.
        // AE2WTLib's callback can run before Core's DeferredRegister, while mod construction is still frozen.
        event.builder(WirelessThroughputMonitorTerminalItem.TERMINAL_NAME,
                        WirelessThroughputMonitorMenuHost::new,
                        WIRELESS_THROUGHPUT_MONITOR_TERMINAL_MENU_TYPE,
                        THROUGHPUT_TERMINAL_ITEM.get(), Icon.PATTERN_ACCESS)
                .hotkeyName(WirelessThroughputMonitorTerminalItem.HOTKEY_NAME)
                .translationKey("item.gtlcore.wireless_throughput_monitor_terminal")
                .addTerminal();
        event.builder(WirelessEmitterManagerTerminalItem.TERMINAL_NAME,
                        WirelessEmitterManagerMenuHost::new,
                        WIRELESS_EMITTER_MANAGER_TERMINAL_MENU_TYPE,
                        EMITTER_TERMINAL_ITEM.get(), Icon.PATTERN_ENCODING)
                .hotkeyName(WirelessEmitterManagerTerminalItem.HOTKEY_NAME)
                .translationKey("item.gtlcore.wireless_emitter_manager_terminal")
                .addTerminal();
        event.builder(WirelessMEChamberManagerTerminalItem.TERMINAL_NAME,
                        WirelessMEChamberManagerMenuHost::new,
                        WIRELESS_ME_CHAMBER_MANAGER_TERMINAL_MENU_TYPE,
                        CHAMBER_TERMINAL_ITEM.get(), Icon.CRAFTING)
                .hotkeyName(WirelessMEChamberManagerTerminalItem.HOTKEY_NAME)
                .translationKey("item.gtlcore.wireless_me_chamber_manager_terminal")
                .addTerminal();
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.FUNCTIONAL_BLOCKS.equals(event.getTabKey()) || GTLCreativeModeTabs.GTL_CORE.getKey().equals(event.getTabKey())) {
            event.accept(WIRELESS_NETWORK_CORE_ITEM.get());
            event.accept(WIRELESS_NETWORK_BOOKMARK_ITEM.get());
            event.accept(WIRELESS_NETWORK_BINDING_TOOL.get());
            event.accept(PATTERN_RELAY.get());
            event.accept(THROUGHPUT_MONITOR.get());
            event.accept(THROUGHPUT_MONITOR_CONFIGURATOR.get());
            event.accept(TAG_VIEW_CELL.get());
            event.accept(THROUGHPUT_MONITOR_TERMINAL.get());
            event.accept(EMITTER_MANAGER_TERMINAL.get());
            event.accept(ME_CHAMBER_MANAGER_TERMINAL.get());
            event.accept(WIRELESS_THROUGHPUT_MONITOR_TERMINAL.get());
            event.accept(WIRELESS_EMITTER_MANAGER_TERMINAL.get());
            event.accept(WIRELESS_ME_CHAMBER_MANAGER_TERMINAL.get());
        }
    }
}
