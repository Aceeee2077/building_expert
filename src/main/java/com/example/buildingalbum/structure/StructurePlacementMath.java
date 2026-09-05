package com.example.buildingalbum.structure;

import net.minecraft.block.BlockState;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构与世界之间的坐标换算（双端共用）：
 * 客户端用它生成预览，服务端用它执行建造，保证两边结果完全一致。
 */
public final class StructurePlacementMath {
    private StructurePlacementMath() {
    }

    /** 已换算到世界坐标的一个方块。 */
    public record PlacedBlock(BlockPos pos, BlockState state) {
    }

    /**
     * 把结构按 rotation 旋转并平移到 origin，返回所有世界方块。
     *
     * @param minY         世界可放置的最低 Y（含）
     * @param maxYExclusive 世界最高 Y（不含）
     */
    public static List<PlacedBlock> compute(
            Structure structure, BlockPos origin, BlockRotation rotation, int minY, int maxYExclusive) {
        List<PlacedBlock> result = new ArrayList<>(structure.blocks().size());
        for (Structure.Entry entry : structure.blocks()) {
            BlockPos rotatedOffset = entry.pos().rotate(rotation);
            BlockPos worldPos = origin.add(rotatedOffset);
            if (worldPos.getY() < minY || worldPos.getY() >= maxYExclusive) {
                continue;
            }
            BlockState state = entry.state().rotate(rotation);
            result.add(new PlacedBlock(worldPos, state));
        }
        return List.copyOf(result);
    }

    /** 结构与预览/建造共用的朝向规则：结构默认 +Z 为正面，让正面朝向玩家。 */
    public static BlockRotation rotationFor(Direction facing) {
        return switch (facing) {
            case NORTH -> BlockRotation.NONE;
            case EAST -> BlockRotation.CLOCKWISE_90;
            case SOUTH -> BlockRotation.CLOCKWISE_180;
            case WEST -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    /** 命中方块旁的相邻可放置格（与放置预览一致）。 */
    public static BlockPos anchorOf(BlockPos hitPos, Direction hitSide) {
        return hitPos.offset(hitSide);
    }
}
