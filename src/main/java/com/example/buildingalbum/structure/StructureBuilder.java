package com.example.buildingalbum.structure;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 把结构真正写入世界（服务端执行）。
 * <p>本版为“免费粘贴”：直接 setBlockState 覆盖目标格，不消耗材料。
 * 同时记录每个被覆盖格的旧状态，供 {@code /buildingalbum undo} 恢复。
 */
public final class StructureBuilder {
    private StructureBuilder() {
    }

    /**
     * @return 实际变化的方块数量，以及用于撤销的旧状态列表
     */
    public static Result place(World world, Structure structure, BlockPos origin, BlockRotation rotation) {
        List<StructurePlacementMath.PlacedBlock> blocks = StructurePlacementMath.compute(
                structure, origin, rotation, world.getBottomY(), world.getTopY());

        List<BuildRecord.BlockChange> changes = new ArrayList<>();
        int placed = 0;

        for (StructurePlacementMath.PlacedBlock block : blocks) {
            BlockState previous = world.getBlockState(block.pos());

            if (world.setBlockState(block.pos(), block.state(), Block.NOTIFY_ALL)) {
                placed++;

                if (!previous.equals(block.state())) {
                    changes.add(new BuildRecord.BlockChange(block.pos().toImmutable(), previous));
                }
            }
        }

        return new Result(placed, List.copyOf(changes));
    }

    public record Result(int placed, List<BuildRecord.BlockChange> changes) {
    }
}
