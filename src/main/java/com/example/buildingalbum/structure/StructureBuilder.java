package com.example.buildingalbum.structure;

import net.minecraft.block.Block;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * 把结构真正写入世界（服务端执行）。
 * <p>本版为“免费粘贴”：直接 setBlockState 覆盖目标格，不消耗材料。
 */
public final class StructureBuilder {
    private StructureBuilder() {
    }

    /**
     * @return 实际成功放置的方块数量
     */
    public static int place(World world, Structure structure, BlockPos origin, BlockRotation rotation) {
        List<StructurePlacementMath.PlacedBlock> blocks = StructurePlacementMath.compute(
                structure, origin, rotation, world.getBottomY(), world.getTopY());

        int placed = 0;
        for (StructurePlacementMath.PlacedBlock block : blocks) {
            if (world.setBlockState(block.pos(), block.state(), Block.NOTIFY_ALL)) {
                placed++;
            }
        }
        return placed;
    }
}
