package org.gtlcore.gtlcore.client.preview;

import org.gtlcore.gtlcore.api.gui.preview.NativePreviewGeometry;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.client.renderer.PatternPreviewRenderer;
import com.gregtechceu.gtceu.client.mui.schema.MutableSchema;
import brachy.modularui.drawable.schema.RenderFilter;

import com.lowdragmc.lowdraglib.utils.virtuallevel.TrackedDummyWorld;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

/** Main-thread placement transform and entity setup; no real world is written. */
public final class WorldPreview {

    private static BlockPos center;
    private static Direction front;
    private static Direction up;
    private static Object clientWorld;
    private static int layer = -1;
    private static int next;
    private static int duration;
    private static int remaining = -1;
    private static SparsePreviewShape shape;
    private static TrackedDummyWorld dummy;
    private static MutableSchema source;
    private static PreviewSnapshot preparing;
    private static PreviewMesh mesh;
    private static PreviewScenes.Key key;

    private WorldPreview() {}

    public static boolean show(BlockPos origin, MutableSchema schema, RenderFilter filter, int ticks) {
        if (!PreviewSettings.enabled() || schema.getBlocks().size() < PreviewSettings.minPositions()) return false;
        var states = new java.util.LinkedHashMap<BlockPos, BlockState>();
        schema.getBlocks().forEach((packed, state) -> {
            var pos = BlockPos.of(packed); if (filter.shouldRender(pos, state)) states.put(pos, state);
        });
        var machineState = schema.getLevel().getBlockState(schema.getControllerPos());
        if (!(machineState.getBlock() instanceof MetaMachineBlock block) ||
                !(block.getDefinition() instanceof com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition definition)) return false;
        PatternPreviewRenderer.INSTANCE.dispose();
        if (states.isEmpty()) return true;
        var geometry = new SparsePreviewShape(NativePreviewGeometry.fromStates(states, schema.getControllerPos()), definition);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (var pos : states.keySet()) { minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ()); }
        var minimum = new BlockPos(minX, minY, minZ);
        center = origin.offset(minimum); clientWorld = Minecraft.getInstance().level; shape = geometry; source = schema;
        front = Direction.NORTH; up = Direction.UP; duration = ticks; remaining = -1; next = 0; layer = -1;
        key = new PreviewScenes.Key(PreviewScenes.generation(), new Anchor(clientWorld, center, front, up, false),
                definition.getId(), states.hashCode(), layer, false);
        mesh = PreviewScenes.take(key);
        if (mesh == null) { dummy = new TrackedDummyWorld(); preparing = new PreviewSnapshot(); }
        return true;
    }

    public static boolean active() {
        return center != null;
    }

    public static boolean ready() { return mesh != null && mesh.complete(); }
    public static int positionCount() { return shape == null ? 0 : shape.positions.length; }

    public static void render(PoseStack pose, Camera camera, float partialTicks, org.joml.Matrix4f modelView) {
        if (!active()) return;
        if (clientWorld != Minecraft.getInstance().level) {
            clear();
            return;
        }
        if (preparing != null) {
            try (var budget = PreviewFrameBudget.slice()) {
                while (next < shape.positions.length && budget.available()) {
                    int index = next++;
                    var local = BlockPos.of(shape.positions[index]);
                    if (layer >= 0 && local.getY() != layer) continue;
                    var pos = center.offset(local);
                    var info = SparsePreviewShape.copy(shape.blocks[index]);
                    var state = info.getBlockState(); dummy.addBlock(pos, info);
                    var entity = info.hasBlockEntity() ? info.getBlockEntity(dummy.registryAccess(), dummy, pos) : null;
                    if (entity != null) {
                        var originalPos = local.subtract(shape.controller).offset(source.getControllerPos());
                        var original = source.getLevel().getBlockEntity(originalPos);
                        if (original != null) entity.loadWithComponents(original.saveWithoutMetadata(source.getLevel().registryAccess()), dummy.registryAccess());
                        dummy.setInnerBlockEntity(entity);
                    }
                    if (!local.equals(shape.controller)) preparing.add(pos, state, entity);
                }
            }
            if (next == shape.positions.length) {
                mesh = new PreviewMesh(preparing, true);
                preparing = null;
                dummy = null;
            }
        }
        if (mesh == null) return;
        mesh.update();
        if (mesh.failed()) {
            Minecraft.getInstance().gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable(
                    "gui.gtlcore.preview.failed"), false);
            clear();
            return;
        }
        if (remaining < 0 && mesh.complete()) remaining = duration;
        pose.pushPose();
        var eye = camera.getPosition();
        pose.translate(-eye.x, -eye.y, -eye.z);
        float fog = RenderSystem.getShaderFogStart();
        try {
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            mesh.draw(new org.joml.Matrix4f(modelView).translate((float) -eye.x, (float) -eye.y, (float) -eye.z), partialTicks, pose);
        } finally {
            RenderSystem.setShaderFogStart(fog);
            pose.popPose();
        }
    }

    public static void tick() {
        if (remaining > 0 && --remaining == 0) PatternPreviewRenderer.INSTANCE.dispose();
    }

    public static void remove(BlockPos pos) {
        if (pos.equals(center)) clear();
    }

    public static void clear() {
        if (mesh != null) PreviewScenes.release(key, mesh);
        mesh = null;
        key = null;
        shape = null;
        dummy = null;
        source = null;
        preparing = null;
        center = null;
        clientWorld = null;
        remaining = -1;
        layer = -1;
    }

    private record Anchor(Object level, BlockPos pos, Direction front, Direction up, boolean flipped) {}
}
