package com.example.buildingalbum.structure;

import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * 一次蓝图建造留下的撤销记录。
 * <p>只记录“被覆盖前”的方块状态；空气也会被记录，这样撤销时
 * 可以把建出来的方块重新变回空气。
 * <p>方块实体（箱子内容、告示牌文字等）不在记录范围内，撤销只恢复方块状态。
 */
public record BuildRecord(
        RegistryKey<World> dimension,
        String structureId,
        List<BlockChange> changes
) {
    /** 一个位置在建造前的方块状态。 */
    public record BlockChange(BlockPos pos, BlockState previousState) {
    }
}
