package com.example.buildingalbum.structure;

import net.minecraft.block.BlockState;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * 一次解析好的结构放置预览：世界中的每个方块位置/状态 + 光照，
 * 供渲染器每帧直接使用（由 {@link StructurePlacementResolver} 缓存）。
 * <p>{@code overwrites} 表示有多少个非空气方块会被这次建造覆盖，用于预览警示。
 */
public record StructurePlacement(
        Structure structure,
        BlockPos origin,
        BlockRotation rotation,
        List<Entry> entries,
        int overwrites
) {
    /** 已换算到世界坐标的方块入口。 */
    public record Entry(BlockPos pos, BlockState state, int light) {
    }
}
