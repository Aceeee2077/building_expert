package com.example.buildingalbum.render;

import com.example.buildingalbum.BuildingAlbumClient;
import com.example.buildingalbum.GhostPlacement;
import com.example.buildingalbum.GhostPlacementResolver;
import com.example.buildingalbum.config.GhostConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** 使用 BlockRenderManager 绘制半透明模型，并使用 WorldRenderer 绘制边框。 */
public final class GhostBlockRenderer {
    private GhostBlockRenderer() {
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        GhostConfig config = BuildingAlbumClient.getConfig();
        if (!config.enabled || client.world == null) {
            return;
        }

        GhostPlacement ghost = GhostPlacementResolver.resolve(client);
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (ghost == null || matrices == null || consumers == null) {
            return;
        }

        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        BlockPos pos = ghost.pos();
        matrices.push();
        matrices.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);

        BlockRenderManager blockRenderer = client.getBlockRenderManager();
        int light = WorldRenderer.getLightmapCoordinates(client.world, pos);

        // 无论方块原本属于何种渲染层，都把预览顶点送入半透明层。
        VertexConsumer translucent = new AlphaVertexConsumer(
                consumers.getBuffer(RenderLayer.getTranslucent()), config.opacity);
        VertexConsumerProvider translucentOnly = ignoredLayer -> translucent;
        blockRenderer.renderBlockAsEntity(
                ghost.state(), matrices, translucentOnly, light, OverlayTexture.DEFAULT_UV);

        // 稍微向外扩 1/1000 方块，避免边线与方块表面发生 z-fighting。
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        float edge = config.edgeOpacity;
        WorldRenderer.drawBox(matrices, lines,
                -0.001D, -0.001D, -0.001D,
                1.001D, 1.001D, 1.001D,
                0.35F, 0.90F, 1.00F, edge);

        matrices.pop();
    }
}
