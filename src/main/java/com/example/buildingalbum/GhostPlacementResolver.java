package com.example.buildingalbum;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/** 根据客户端当前准星、手持物和世界状态计算影子方块。 */
public final class GhostPlacementResolver {
    private GhostPlacementResolver() {
    }

    public static GhostPlacement resolve(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null || !(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        Hand hand = findBlockHand(player);
        if (hand == null) {
            return null;
        }

        ItemStack stack = player.getStackInHand(hand);
        BlockItem blockItem = (BlockItem) stack.getItem();

        // ItemPlacementContext 会使用命中面计算相邻坐标，并正确推导楼梯、半砖、原木等朝向。
        ItemPlacementContext placementContext = new ItemPlacementContext(player, hand, stack, hit);
        BlockPos previewPos = placementContext.getBlockPos();
        BlockState previewState = blockItem.getBlock().getPlacementState(placementContext);
        if (previewState == null || !world.getWorldBorder().contains(previewPos)) {
            return null;
        }

        BlockState existing = world.getBlockState(previewPos);
        if (!existing.canReplace(placementContext)) {
            return null;
        }

        // 与原版放置检查保持一致：玩家/实体碰撞时不显示“可放置”的误导预览。
        if (!world.canPlace(previewState, previewPos, ShapeContext.of(player))) {
            return null;
        }

        return new GhostPlacement(previewPos.toImmutable(), previewState, hit, hand);
    }

    private static Hand findBlockHand(ClientPlayerEntity player) {
        if (player.getMainHandStack().getItem() instanceof BlockItem) {
            return Hand.MAIN_HAND;
        }
        if (player.getOffHandStack().getItem() instanceof BlockItem) {
            return Hand.OFF_HAND;
        }
        return null;
    }
}
