package com.example.buildingalbum.structure;

import com.example.buildingalbum.item.BlueprintItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据玩家手持的蓝图与准星命中位置，把结构数据换算为世界中的预览放置。
 * <p>坐标换算完全复用 {@link StructurePlacementMath}（与服务端建造一致），
 * 这里只额外补充客户端需要的逐方块光照。结果按（蓝图、锚点、旋转）缓存。
 */
public final class StructurePlacementResolver {
    private StructurePlacementResolver() {
    }

    private static StructurePlacement cached;
    private static BlueprintItem cachedItem;
    private static BlockPos cachedAnchor;
    private static BlockRotation cachedRotation;

    public static StructurePlacement resolve(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            return null;
        }
        if (!(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        Hand hand = findBlueprintHand(player);
        if (hand == null) {
            return null;
        }
        BlueprintItem item = (BlueprintItem) player.getStackInHand(hand).getItem();
        Structure structure = item.getStructure();

        BlockPos anchor = StructurePlacementMath.anchorOf(hit.getBlockPos(), hit.getSide());
        BlockRotation rotation = StructurePlacementMath.rotationFor(player.getHorizontalFacing());

        if (cached != null && cachedItem == item
                && cachedAnchor.equals(anchor) && cachedRotation == rotation) {
            return cached;
        }

        StructurePlacement placement = computePlacement(world, structure, anchor, rotation);
        cached = placement;
        cachedItem = item;
        cachedAnchor = anchor;
        cachedRotation = rotation;
        return placement;
    }

    private static StructurePlacement computePlacement(
            ClientWorld world, Structure structure, BlockPos anchor, BlockRotation rotation) {
        List<StructurePlacementMath.PlacedBlock> placed =
                StructurePlacementMath.compute(structure, anchor, rotation, world.getBottomY(), world.getTopY());

        List<StructurePlacement.Entry> entries = new ArrayList<>(placed.size());
        for (StructurePlacementMath.PlacedBlock block : placed) {
            int light = WorldRenderer.getLightmapCoordinates(world, block.pos());
            entries.add(new StructurePlacement.Entry(block.pos(), block.state(), light));
        }
        return new StructurePlacement(structure, anchor, rotation, List.copyOf(entries));
    }

    private static Hand findBlueprintHand(ClientPlayerEntity player) {
        if (player.getMainHandStack().getItem() instanceof BlueprintItem) {
            return Hand.MAIN_HAND;
        }
        if (player.getOffHandStack().getItem() instanceof BlueprintItem) {
            return Hand.OFF_HAND;
        }
        return null;
    }
}
