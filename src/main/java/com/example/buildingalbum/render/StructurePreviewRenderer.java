package com.example.buildingalbum.render;

import com.example.buildingalbum.BuildingAlbumClient;
import com.example.buildingalbum.config.GhostConfig;
import com.example.buildingalbum.structure.StructurePlacement;
import com.example.buildingalbum.structure.StructurePlacementResolver;
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

/**
 * 蓝图预览渲染：把整座结构的所有方块以半透明形式画进当前帧，
 * 并绘制结构整体外框线。
 */
public final class StructurePreviewRenderer {
    private StructurePreviewRenderer() {
    }

    /**
     * @return true 表示当前手持蓝图且渲染了结构预览（调用方无需再走单方块预览）
     */
    public static boolean render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        GhostConfig config = BuildingAlbumClient.getConfig();
        if (!config.enabled || client.world == null) {
            return false;
        }

        StructurePlacement placement = StructurePlacementResolver.resolve(client);
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (placement == null || matrices == null || consumers == null) {
            return false;
        }

        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        BlockPos origin = placement.origin();

        BlockRenderManager blockRenderer = client.getBlockRenderManager();

        // 所有方块都送入同一半透明缓冲（统一乘以配置透明度）。
        VertexConsumer translucent = new AlphaVertexConsumer(
                consumers.getBuffer(RenderLayer.getTranslucent()), config.opacity);
        VertexConsumerProvider translucentOnly = ignoredLayer -> translucent;

        matrices.push();
        matrices.translate(origin.getX() - cameraPos.x, origin.getY() - cameraPos.y, origin.getZ() - cameraPos.z);

        for (StructurePlacement.Entry entry : placement.entries()) {
            BlockPos relative = entry.pos().subtract(origin);
            matrices.push();
            matrices.translate(relative.getX(), relative.getY(), relative.getZ());
            blockRenderer.renderBlockAsEntity(
                    entry.state(), matrices, translucentOnly, entry.light(), OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }

        // 整体外框（稍微外扩避免 z-fighting）。
        BlockPos size = placement.structure().size();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        float edge = config.edgeOpacity;
        WorldRenderer.drawBox(matrices, lines,
                -0.003D, -0.003D, -0.003D,
                size.getX() + 0.003D, size.getY() + 0.003D, size.getZ() + 0.003D,
                0.35F, 0.90F, 1.00F, edge);

        matrices.pop();
        return true;
    }
}
