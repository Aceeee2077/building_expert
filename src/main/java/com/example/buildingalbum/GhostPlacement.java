package com.example.buildingalbum;

import net.minecraft.block.BlockState;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/** 一帧内使用的、不可变的放置预览数据。 */
public record GhostPlacement(
        BlockPos pos,
        BlockState state,
        BlockHitResult sourceHit,
        Hand hand
) {
}
