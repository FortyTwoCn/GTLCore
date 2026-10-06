package org.gtlcore.gtlcore.api.gui;

import org.gtlcore.gtlcore.api.gui.preview.PreviewBuildTiming;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineHost;
import org.gtlcore.gtlcore.api.pattern.PreviewMatcherTiming;
import org.gtlcore.gtlcore.client.preview.PreviewControls;
import org.gtlcore.gtlcore.client.preview.PreviewHeight;
import org.gtlcore.gtlcore.client.preview.PreviewPicking;
import org.gtlcore.gtlcore.client.preview.PreviewScenes;
import org.gtlcore.gtlcore.client.preview.PreviewSettings;
import org.gtlcore.gtlcore.client.preview.SparsePreviewShape;
import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.data.RotationState;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import org.gtlcore.gtlcore.api.gui.preview.NativePreviewGeometry;
import org.gtlcore.gtlcore.api.gui.preview.NativePreviewShapes;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib.client.utils.RenderUtils;
import com.lowdragmc.lowdraglib.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.texture.*;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.integration.jei.IngredientIO;
import com.lowdragmc.lowdraglib.utils.*;
import com.lowdragmc.lowdraglib.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib.utils.data.BlockPosFace;
import com.lowdragmc.lowdraglib.utils.data.ItemStackKey;
import com.lowdragmc.lowdraglib.misc.CycleItemStackHandler;
import com.gregtechceu.gtceu.api.multiblock.PredicateContext;
import com.lowdragmc.lowdraglib.utils.virtuallevel.TrackedDummyWorld;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 部分代码参考自GTM
 * &#064;line <a href="https://github.com/GregTechCEu/GregTech-Modern">...</a>
 */
@SuppressWarnings("deprecation")
@OnlyIn(Dist.CLIENT)
public class PatternPreviewWidget extends WidgetGroup {

    private static TrackedDummyWorld LEVEL;
    private static final int REGION_SIZE = 512;
    private static int LAST_OFFSET_INDEX = 0;
    private static final Map<MultiblockMachineDefinition, Map<Integer, MBPattern>> CACHE = new Object2ObjectOpenHashMap<>();
    private final SceneWidget sceneWidget;
    private DraggableScrollableWidgetGroup scrollableWidgetGroup;
    private final MultiblockMachineDefinition controllerDefinition;
    private final List<NativePreviewGeometry> shapes;
    private final Map<Integer, MBPattern> patterns;
    private int index;
    private int layer;
    private final List<SlotWidget> slotWidgets;
    private final List<SlotWidget> candidates;
    private boolean showModules = false;
    private PreviewControls previewControls;
    private final Map<BlockState, BlockInfo> plainBlocks = new IdentityHashMap<>();

    protected PatternPreviewWidget(MultiblockMachineDefinition controllerDefinition) {
        super(0, 0, 160, 160);
        try {
            setClientSideWidget();
            this.controllerDefinition = controllerDefinition;
            layer = -1;
            slotWidgets = new ObjectArrayList<>();
            candidates = new ObjectArrayList<>();

            addWidget(sceneWidget = new SceneWidget(3, 3, 150, 150, LEVEL) {

                @Override
                public void renderBlockOverLay(WorldSceneRenderer renderer) {
                    PoseStack poseStack = new PoseStack();
                    hoverPosFace = null;
                    hoverItem = null;
                    if (isMouseOverElement(currentMouseX, currentMouseY)) {
                        BlockHitResult hit = renderer.getLastTraceResult();
                        if (hit != null) {
                            if (core.contains(hit.getBlockPos())) {
                                hoverPosFace = new BlockPosFace(hit.getBlockPos(), hit.getDirection());
                            } else if (!useOrtho) {
                                Vector3f hitPos = hit.getLocation().toVector3f();
                                Level world = renderer.world;
                                Vec3 eyePos = new Vec3(renderer.getEyePos());
                                hitPos.mul(2); // Double view range to ensure pos can be seen.
                                Vec3 endPos = new Vec3((hitPos.x - eyePos.x), (hitPos.y - eyePos.y), (hitPos.z - eyePos.z));
                                double min = Float.MAX_VALUE;
                                for (var positions = PreviewPicking.candidates(renderer, core); positions.hasNext();) {
                                    BlockPos pos = positions.next();
                                    BlockState blockState = world.getBlockState(pos);
                                    if (blockState.getBlock() == Blocks.AIR) {
                                        continue;
                                    }
                                    hit = world.clipWithInteractionOverride(eyePos, endPos, pos,
                                            blockState.getShape(world, pos), blockState);
                                    if (hit != null && hit.getType() != HitResult.Type.MISS) {
                                        double dist = eyePos.distanceToSqr(hit.getLocation());
                                        if (dist < min) {
                                            min = dist;
                                            hoverPosFace = new BlockPosFace(hit.getBlockPos(), hit.getDirection());
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (hoverPosFace != null) {
                        var state = getDummyWorld().getBlockState(hoverPosFace.pos());
                        hoverItem = state.getCloneItemStack(new BlockHitResult(Vec3.atCenterOf(hoverPosFace.pos()), hoverPosFace.facing(), hoverPosFace.pos(), true), getDummyWorld(), hoverPosFace.pos(), Minecraft.getInstance().player);
                    }
                    BlockPosFace tmp = dragging ? clickPosFace : hoverPosFace;
                    if (selectedPosFace != null || tmp != null) {
                        if (selectedPosFace != null && renderFacing) {
                            drawFacingBorder(poseStack, selectedPosFace, 0xff00ff00);
                        }
                        if (tmp != null && !tmp.equals(selectedPosFace) && renderFacing) {
                            drawFacingBorder(poseStack, tmp, 0xffffffff);
                        }
                    }
                    if (selectedPosFace != null && renderSelect) {
                        RenderUtils.renderBlockOverLay(poseStack, selectedPosFace.pos(), 0.6f, 0, 0, 1.03f);
                    }

                    if (this.afterWorldRender != null) {
                        this.afterWorldRender.accept(this);
                    }
                }
            }
                    .setOnSelected(this::onPosSelected)
                    .setRenderFacing(false)
                    .setRenderFacing(false));

            refreshScrollableWidget();

            if (ConfigHolder.INSTANCE.client.useVBO) {
                if (!RenderSystem.isOnRenderThread()) {
                    RenderSystem.recordRenderCall(sceneWidget::useCacheBuffer);
                } else {
                    sceneWidget.useCacheBuffer();
                }
            }

            addWidget(new ImageWidget(3, 3, 160, 10,
                    new TextTexture(controllerDefinition.getDescriptionId(), -1)
                            .setType(TextTexture.TextType.ROLL)
                            .setWidth(170)
                            .setDropShadow(true)));

            this.shapes = NativePreviewShapes.get(controllerDefinition);
            this.patterns = CACHE.computeIfAbsent(controllerDefinition, ignored -> new Object2ObjectOpenHashMap<>());

            addWidget(new ButtonWidget(138, 30, 18, 18, new GuiTextureGroup(
                    ColorPattern.T_GRAY.rectTexture(),
                    liveText(() -> "P:" + index)),
                    (x) -> setPage((index + 1 >= shapes.size()) ? 0 : index + 1, x))
                    .setHoverBorderTexture(1, -1));

            addWidget(new ButtonWidget(138, 50, 18, 18, new GuiTextureGroup(
                    ColorPattern.T_GRAY.rectTexture(),
                    liveText(() -> layer >= 0 ? "L:" + layer : "ALL")),
                    this::updateLayer)
                    .setHoverBorderTexture(1, -1));

            if (!this.shapes.isEmpty() && getPattern(0).hasModule) {
                addWidget(new ButtonWidget(138, 70, 18, 18, new GuiTextureGroup(
                        ColorPattern.T_GRAY.rectTexture(),
                        liveText(() -> showModules ? "M:ON" : "M:OFF")),
                        (x) -> toggleModules())
                        .setHoverBorderTexture(1, -1)
                        .setHoverTooltips(Component.translatable("gui.gtlcore.module.show")));
            }
            setPage(0, null);
            previewControls = new PreviewControls(this, sceneWidget, this::controllerHeight, this::dimensions);
        } catch (Exception e) {
            throw new IllegalStateException("The jei preview creation for the Multi Block Machine [" + controllerDefinition.getId().toString() + "] failed! ", e);
        }
    }

    public PreviewControls getPreviewControls() {
        return previewControls;
    }

    private int controllerHeight() {
        if (index < 0 || index >= shapes.size()) return -1;
        var pattern = getPattern(index);
        return pattern.controllerBase == null ? -1 :
                pattern.bounds.aboveBottom(pattern.controllerBase.getBlockPos().getY(), showModules);
    }

    private PreviewHeight.Dimensions dimensions() {
        return index < 0 || index >= shapes.size() ? PreviewHeight.Dimensions.UNKNOWN :
                getPattern(index).bounds.dimensions(showModules);
    }

    public static void clearSharedPreviewState() {
        CACHE.clear();
        LEVEL = null;
        LAST_OFFSET_INDEX = 0;
    }

    private void updateLayer(ClickData cd) {
        if (index < 0 || index >= shapes.size()) return;
        var pattern = getPattern(index);
        if (layer + 1 >= -1 && layer + 1 <= pattern.maxY - pattern.minY && !cd.isShiftClick) {
            layer += 1;
            if (pattern.controllerBase.isFormed()) {
                onFormedSwitch(false);
            }
        } else {
            layer = -1;
            if (!pattern.controllerBase.isFormed()) {
                onFormedSwitch(true);
            }
        }
        setupScene(pattern);
    }

    private void setupScene(MBPattern pattern) {
        PreviewScenes.configure(sceneWidget, controllerDefinition, index, layer, showModules);
        var stream = pattern.blockMap.keySet().stream()
                .filter(pos -> (layer == -1 || layer + pattern.minY == pos.getY()) && (showModules || !pattern.moduleOnlyBlocks.contains(pos)));
        if (pattern.controllerBase.isFormed()) stream = stream.filter(pos -> !hiddenWhenFormed(pos, pattern.predicateMap.get(pos)));
        sceneWidget.setRenderedCore(stream.toList(), null);
    }

    public static PatternPreviewWidget getPatternWidget(MultiblockMachineDefinition controllerDefinition) {
        if (LEVEL == null) {
            if (Minecraft.getInstance().level == null) {
                GTCEu.LOGGER.error("Try to init pattern previews before level load");
                throw new IllegalStateException();
            }
            LEVEL = new TrackedDummyWorld();
        }
        return new PatternPreviewWidget(controllerDefinition);
    }

    public void setPage(int index, ClickData x) {
        if (x != null) {
            if (x.isShiftClick) index = 0;
            else if (x.isCtrlClick) index = shapes.size() - 1;
        }
        if (index >= shapes.size() || index < 0) return;
        this.index = index;
        this.layer = -1;
        MBPattern pattern = getPattern(index);
        setupScene(pattern);

        refreshScrollableWidget();
        slotWidgets.clear();
        var itemHandler = new CycleItemStackHandler(pattern.parts);
        for (int i = 0; i < pattern.parts.size(); i++) {
            final var itemStack = pattern.parts.get(i).get(0);
            final var widget = new SlotWidget(itemHandler, i, 4 + i * 18, 0, false, false)
                    .setBackgroundTexture(ColorPattern.T_GRAY.rectTexture())
                    .setIngredientIO(IngredientIO.INPUT)
                    .setOnAddedTooltips((w, tips) -> tips.add(Component.translatable("gtceu.machine.quantum_chest.items_stored")
                            .withStyle(ChatFormatting.DARK_AQUA)
                            .append(Component.literal(String.valueOf(itemStack.getCount())))));
            slotWidgets.add(widget);
            scrollableWidgetGroup.addWidget(widget);
        }
        if (previewControls != null) previewControls.layout();
    }

    /** Live slots include replacement material strips and block-predicate candidates after every page change. */
    public List<SlotWidget> getIngredientSlots() {
        var slots = new ArrayList<SlotWidget>(slotWidgets.size() + candidates.size());
        slots.addAll(slotWidgets);
        slots.addAll(candidates);
        return List.copyOf(slots);
    }

    public int getPageIndex() { return index; }
    public int getLayerIndex() { return layer; }

    private static TextTexture liveText(java.util.function.Supplier<String> textSupplier) {
        // LDLib's default supplier is gated on level game time, which freezes in a paused recipe viewer.
        return new TextTexture(textSupplier.get()) {
            private String previous;
            @Override public void updateTick() {
                String value = textSupplier.get();
                if (!value.equals(previous)) { updateText(value); previous = value; }
            }
        };
    }

    private void refreshScrollableWidget() {
        if (scrollableWidgetGroup != null) removeWidget(scrollableWidgetGroup);
        var result = new DraggableScrollableWidgetGroup(3, 132, 154, 22)
                .setXScrollBarHeight(4)
                .setXBarStyle(new ResourceTexture("gtceu:textures/gui/widget/slider_background.png"), new ResourceTexture("gtceu:textures/gui/widget/button.png"))
                .setScrollable(true)
                .setDraggable(true);
        result.setScrollWheelDirection(DraggableScrollableWidgetGroup.ScrollWheelDirection.HORIZONTAL);
        result.setScrollYOffset(0);
        scrollableWidgetGroup = result;
        addWidget(scrollableWidgetGroup);
    }

    private void toggleModules() {
        showModules = !showModules;
        setPage(index, null);
    }

    private void onFormedSwitch(boolean isFormed) {
        var pattern = getPattern(index);
        var controllerBase = pattern.controllerBase;
        if (isFormed) {
            this.layer = -1;
            loadControllerFormed(pattern.blockMap.keySet(), controllerBase);
        } else {
            sceneWidget.setRenderedCore(pattern.blockMap.keySet(), null);
            controllerBase.invalidateStructure("main");
        }
    }

    private void onPosSelected(BlockPos pos, Direction facing) {
        if (index >= shapes.size() || index < 0) return;

        var pattern = getPattern(index);
        var predicate = pattern.predicateMap.get(pos);

        if (predicate != null) {
            var predicates = new ObjectOpenHashSet<BasePredicate>(predicate.expand());
            for (SlotWidget candidate : candidates) removeWidget(candidate);
            candidates.clear();
            List<List<ItemStack>> candidateStacks = new ObjectArrayList<>();
            List<List<Component>> predicateTips = new ObjectArrayList<>();
            for (var basePredicate : predicates) {
                var itemStacks = basePredicate.getCandidates().stream().map(com.gregtechceu.gtceu.api.multiblock.util.BlockInfo::getItemStackForm)
                        .filter(stack -> !stack.isEmpty()).toList();
                if (!itemStacks.isEmpty()) {
                    candidateStacks.add(itemStacks);
                    predicateTips.add(basePredicate.getRecipeViewerTooltips(predicate));
                }
            }

            CycleItemStackHandler itemHandler = new CycleItemStackHandler(candidateStacks);
            int maxCol = Math.max(1, (120 - 35) / 18);
            for (int i = 0; i < candidateStacks.size(); i++) {
                int finalI = i;
                final var widget = new SlotWidget(itemHandler, i, 3 + (i / maxCol) * 18, 3 + (i % maxCol) * 18, false,
                        false)
                        .setIngredientIO(IngredientIO.INPUT)
                        .setBackgroundTexture(new ColorRectTexture(0x4fffffff))
                        .setOnAddedTooltips((slot, list) -> list.addAll(predicateTips.get(finalI)));
                candidates.add(widget);
                addWidget(widget);
            }
        }
        if (previewControls != null) previewControls.layout();
    }

    public static BlockPos locateNextRegion() {
        int currentIndex = LAST_OFFSET_INDEX++;

        // Origin coordinates scaled back to the offset value, from global
        int x = 0, z = 0;
        if (currentIndex > 0) {
            int v = (int) (Mth.sqrt(currentIndex + 0.25f) - 0.5f);
            int nextV = v + 1;
            int spiralBaseIndex = v * nextV;
            // this is 1 or -1 depending on if v is odd or even
            int flipFlop = (v & 1) * 2 - 1;

            int offset = flipFlop * nextV / 2;
            x += offset;
            z += offset;

            int cornerIndex = spiralBaseIndex + nextV;
            if (currentIndex < cornerIndex) {
                x -= flipFlop * (currentIndex - spiralBaseIndex + 1);
            } else {
                x -= flipFlop * nextV;
                z -= flipFlop * (currentIndex - cornerIndex + 1);
            }
        }
        return new BlockPos(x * REGION_SIZE, 50, z * REGION_SIZE);
    }

    @Override
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        RenderSystem.enableBlend();
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        if (previewControls != null) previewControls.drawInfo(graphics);
    }

    private BlockInfo reusableBlockInfo(BlockState state) {
        if (!PreviewSettings.enabled() || state.getBlock() instanceof EntityBlock) {
            return BlockInfo.fromBlockState(state);
        }
        return plainBlocks.computeIfAbsent(state, BlockInfo::fromBlockState);
    }

    private MBPattern getPattern(int page) {
        return patterns.computeIfAbsent(page, requested -> initializePattern(shapes.get(requested)));
    }

    private MBPattern initializePattern(NativePreviewGeometry geometry) {
        var offset = locateNextRegion();
        Map<BlockPos, BlockInfo> blockMap = new Object2ObjectOpenHashMap<>();
        geometry.states().forEach((pos, state) -> blockMap.put(offset.offset(pos), reusableBlockInfo(state)));
        LEVEL.addBlocks(blockMap);
        BlockPos controllerPos = offset.offset(geometry.controllerPos());
        var entity = LEVEL.getBlockEntity(controllerPos);
        if (!(entity instanceof MultiblockControllerMachine controller)) {
            throw new IllegalStateException("Native preview did not create its controller: " + controllerDefinition.getId());
        }
        LEVEL.setInnerBlockEntity(controller);
        loadControllerFormed(blockMap.keySet(), controller);
        Map<BlockPos, MultiPredicate> predicateMap = new Object2ObjectOpenHashMap<>();
        geometry.predicates().forEach((pos, predicate) -> predicateMap.put(offset.offset(pos), predicate));
        var hostBlockMap = new Object2ObjectOpenHashMap<>(blockMap);
        var moduleOnlyBlocks = new ObjectOpenHashSet<BlockPos>();
        if (controller instanceof IModularMachineHost<?> host) {
            initializeModules(host, controllerPos, blockMap, hostBlockMap, moduleOnlyBlocks, predicateMap);
        }
        var parts = gatherBlockDrops(hostBlockMap).values().stream()
                .sorted((one, two) -> {
                    if (one.isController != two.isController) return one.isController ? -1 : 1;
                    if (one.isTile != two.isTile) return one.isTile ? -1 : 1;
                    int blockOrder = Integer.compare(two.blockId, one.blockId);
                    return blockOrder != 0 ? blockOrder : Integer.compare(two.amount, one.amount);
                }).map(PartInfo::getItemStack).filter(list -> !list.isEmpty()).toList();
        return new MBPattern(blockMap, moduleOnlyBlocks, parts, predicateMap, controller);
    }

    private void loadControllerFormed(Collection<BlockPos> positions, MultiblockControllerMachine controller) {
        try (var operation = PreviewBuildTiming.begin(false)) {
            var state = controller.getDefaultPatternState();
            var pattern = controller.getDefinition().getStructurePatterns().get("main").get();
            var timing = PreviewMatcherTiming.begin(PreviewBuildTiming.identity(controller, false), state);
            boolean matched;
            try (timing) {
                matched = pattern.checkPatternAt(LEVEL, state, controller.getBlockPos(), controller.getFrontFacing(), controller.getUpwardsFacing(), false);
                timing.result(matched);
            } finally { PreviewBuildTiming.recordMatcher(timing.elapsedNanos()); }
            if (matched) PreviewBuildTiming.formController(controller);
            if (controller.isFormed()) sceneWidget.setRenderedCore(positions, null);
        }
    }

    private boolean hiddenWhenFormed(BlockPos pos, @Nullable MultiPredicate predicate) {
        if (predicate == null) return false;
        var context = new PredicateContext(null); context.updateLevel(LEVEL); context.updatePos(pos);
        predicate.resetLogic(); var actual = predicate.getPredicateAtPos(context);
        return actual != null && actual.isDisableRenderFormed();
    }

    private void initializeModules(IModularMachineHost<?> host, BlockPos hostPos,
                                   Map<BlockPos, BlockInfo> blocks, Map<BlockPos, BlockInfo> hostBlocks,
                                   Set<BlockPos> moduleOnlyBlocks, Map<BlockPos, MultiPredicate> predicates) {
        var hostController = (MultiblockControllerMachine) host;
        for (var module : host.getModulesForRendering()) {
            var pages = NativePreviewShapes.get(module.moduleDefinition());
            if (pages.isEmpty()) continue;
            var geometry = pages.getFirst();
            var moduleFront = module.calculateModuleFront(hostController.getFrontFacing(), hostController.getUpwardsFacing());
            var moduleUp = module.calculateModuleUp(hostController.getFrontFacing(), hostController.getUpwardsFacing());
            var origin = hostPos.offset(module.calculateModuleOffset(hostController.getFrontFacing(), hostController.getUpwardsFacing()));
            var moduleBlocks = new Object2ObjectOpenHashMap<BlockPos, BlockInfo>();
            for (var entry : geometry.states().entrySet()) {
                var local = entry.getKey().subtract(geometry.controllerPos());
                var pos = origin.offset(ModuleRenderInfo.applyGTTransform(local, moduleFront, moduleUp));
                var state = entry.getValue();
                if (state.getBlock() instanceof MetaMachineBlock machine) {
                    var rotation = machine.getRotationState();
                    if (rotation.property != null && state.hasProperty(rotation.property)) {
                        var face = local.equals(BlockPos.ZERO) ? moduleFront : ModuleRenderInfo.calculateBlockFacing(state.getValue(rotation.property), moduleFront);
                        if (rotation.test(face)) state = state.setValue(rotation.property, face);
                    }
                    if (state.hasProperty(com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties.UPWARDS_FACING)) {
                        state = state.setValue(com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties.UPWARDS_FACING, moduleUp);
                    }
                }
                var info = BlockInfo.fromBlockState(state); moduleBlocks.put(pos, info);
                if (!hostBlocks.containsKey(pos)) { moduleOnlyBlocks.add(pos); blocks.put(pos, info); }
                var predicate = geometry.predicates().get(entry.getKey());
                if (predicate != null) predicates.putIfAbsent(pos, predicate);
            }
            LEVEL.addBlocks(moduleBlocks);
            if (LEVEL.getBlockEntity(origin) instanceof MultiblockControllerMachine controller) {
                LEVEL.setInnerBlockEntity(controller);
                try (var operation = PreviewBuildTiming.begin(true)) {
                    var state = controller.getDefaultPatternState();
                    var pattern = controller.getDefinition().getStructurePatterns().get("main").get();
                    var timing = PreviewMatcherTiming.begin(PreviewBuildTiming.identity(controller, true), state);
                    boolean matched;
                    try (timing) {
                        matched = pattern.checkPatternAt(LEVEL, state, origin, moduleFront, moduleUp, false);
                        timing.result(matched);
                    } finally { PreviewBuildTiming.recordMatcher(timing.elapsedNanos()); }
                    if (matched) PreviewBuildTiming.formController(controller);
                }
            }
            // Restore shared host positions after the module matcher has inspected its complete view.
            hostBlocks.forEach(LEVEL::addBlock);
        }
    }

    private Map<ItemStackKey, PartInfo> gatherBlockDrops(Map<BlockPos, BlockInfo> blocks) {
        Map<ItemStackKey, PartInfo> partsMap = new Object2ObjectOpenHashMap<>();
        for (Map.Entry<BlockPos, BlockInfo> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState blockState = LEVEL.getBlockState(pos);
            ItemStack itemStack = blockState.getCloneItemStack(new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, true), LEVEL, pos, Minecraft.getInstance().player);

            if (itemStack.isEmpty() && !blockState.getFluidState().isEmpty()) {
                Fluid fluid = blockState.getFluidState().getType();
                itemStack = fluid.getBucket().getDefaultInstance();
            }

            ItemStackKey itemStackKey = ItemStackKey.of(itemStack);
            partsMap.computeIfAbsent(itemStackKey, key -> new PartInfo(key, entry.getValue())).amount++;
        }
        return partsMap;
    }

    private static class PartInfo {

        final ItemStackKey itemStackKey;
        boolean isController = false;
        boolean isTile;
        final int blockId;
        int amount = 0;

        PartInfo(final ItemStackKey itemStackKey, final BlockInfo blockInfo) {
            this.itemStackKey = itemStackKey;
            this.blockId = Block.getId(blockInfo.getBlockState());
            this.isTile = blockInfo.hasBlockEntity();

            if (blockInfo.getBlockState().getBlock() instanceof MetaMachineBlock block) {
                if (block.definition instanceof MultiblockMachineDefinition)
                    this.isController = true;
            }
        }

        public List<ItemStack> getItemStack() {
            return Arrays.stream(itemStackKey.getItemStack())
                    .map(itemStack -> {
                        var item = itemStack.copy();
                        item.setCount(amount);
                        return item;
                    }).filter(item -> !item.isEmpty()).toList();
        }
    }

    private static class MBPattern {

        @NotNull
        final List<List<ItemStack>> parts;
        @NotNull
        final Map<BlockPos, MultiPredicate> predicateMap;
        @NotNull
        final Map<BlockPos, BlockInfo> blockMap;
        @NotNull
        final Set<BlockPos> moduleOnlyBlocks;
        @NotNull
        final MultiblockControllerMachine controllerBase;
        final int maxY, minY;
        final boolean hasModule;
        final PreviewHeight bounds = new PreviewHeight();

        public MBPattern(@NotNull Map<BlockPos, BlockInfo> blockMap, @NotNull Set<BlockPos> moduleOnlyBlocks, @NotNull List<List<ItemStack>> parts,
                         @NotNull Map<BlockPos, MultiPredicate> predicateMap,
                         @NotNull MultiblockControllerMachine controllerBase) {
            this.parts = parts;
            this.blockMap = blockMap;
            this.predicateMap = predicateMap;
            this.controllerBase = controllerBase;
            this.moduleOnlyBlocks = moduleOnlyBlocks;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (BlockPos pos : blockMap.keySet()) {
                min = Math.min(min, pos.getY());
                max = Math.max(max, pos.getY());
            }
            minY = min;
            maxY = max;
            hasModule = controllerBase instanceof IModularMachineHost<?>;
            blockMap.forEach((pos, info) -> {
                if (info != null && !info.getBlockState().isAir()) {
                    bounds.include(pos.getX(), pos.getY(), pos.getZ(), moduleOnlyBlocks.contains(pos));
                }
            });
        }
    }
}
