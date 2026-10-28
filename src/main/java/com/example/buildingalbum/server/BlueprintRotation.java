package com.example.buildingalbum.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每位玩家当前的蓝图旋转偏移（0–3 个 90°）。
 * <p>由服务端持有并同步给客户端，保证预览与真实建造使用同一角度。
 * 玩家退出时清除。
 */
public final class BlueprintRotation {
    private static final Map<UUID, Integer> OFFSETS = new HashMap<>();

    private BlueprintRotation() {
    }

    /** 0 = 不额外旋转，1 = 顺时针 90°，以此类推。 */
    public static int get(UUID playerId) {
        return OFFSETS.getOrDefault(playerId, 0);
    }

    /** 顺时针旋转 90°，返回新偏移。 */
    public static int rotate(UUID playerId) {
        int next = Math.floorMod(get(playerId) + 1, 4);
        OFFSETS.put(playerId, next);
        return next;
    }

    public static void forget(UUID playerId) {
        OFFSETS.remove(playerId);
    }
}
