package com.example.buildingalbum.server;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 每位玩家的蓝图选区（两个角点 + 维度）。
 * <p>选区只保存在服务端内存中；切换维度会清空两个角，避免跨维度选区。
 */
public final class BlueprintSelection {
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

    private BlueprintSelection() {
    }

    /** 选区快照；角点未设置时为空。 */
    public record Selection(
            RegistryKey<World> dimension,
            Optional<BlockPos> first,
            Optional<BlockPos> second
    ) {
        public boolean complete() {
            return first.isPresent() && second.isPresent();
        }
    }

    /**
     * 设置一个角点。
     *
     * @param first true 表示 pos1（第一个角），false 表示 pos2
     */
    public static void setCorner(
            UUID playerId, boolean first, RegistryKey<World> dimension, BlockPos pos) {
        Selection current = SELECTIONS.get(playerId);
        if (current == null || !current.dimension().equals(dimension)) {
            current = new Selection(dimension, Optional.empty(), Optional.empty());
        }

        SELECTIONS.put(playerId, first
                ? new Selection(dimension, Optional.of(pos.toImmutable()), current.second())
                : new Selection(dimension, current.first(), Optional.of(pos.toImmutable())));
    }

    public static Optional<Selection> get(UUID playerId) {
        return Optional.ofNullable(SELECTIONS.get(playerId));
    }

    /** 保存成功或玩家离线时清空选区，避免残留状态。 */
    public static void forget(UUID playerId) {
        SELECTIONS.remove(playerId);
    }
}
