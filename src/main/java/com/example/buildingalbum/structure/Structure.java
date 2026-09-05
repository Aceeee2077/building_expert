package com.example.buildingalbum.structure;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * 一份蓝图对应的建筑结构。
 * <p>作者坐标系：X 右、Y 上、Z 前（结构默认的“正面”在 +Z 一侧），
 * 所有 {@link Entry#pos()} 均为相对坐标（最小角为 0,0,0），
 * {@link #size()} 表示结构整体尺寸（用于绘制外框线）。
 */
public record Structure(
        String id,
        BlockPos size,
        List<Entry> blocks
) {
    /** 结构中的一个方块：相对坐标 + 该坐标处的方块状态。 */
    public record Entry(BlockPos pos, BlockState state) {
    }
}
