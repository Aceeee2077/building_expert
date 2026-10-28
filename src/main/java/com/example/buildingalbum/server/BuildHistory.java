package com.example.buildingalbum.server;

import com.example.buildingalbum.structure.BuildRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.Block;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每位玩家最近若干次建造的撤销记录（仅服务端内存，重启后清空）。
 * <p>所有方法都在服务端主线程调用（物品使用与命令执行），因此无需额外同步。
 */
public final class BuildHistory {
    /** 每位玩家最多保留的建造次数。 */
    public static final int MAX_BUILDS = 8;

    private static final Map<UUID, Deque<BuildRecord>> HISTORY = new HashMap<>();

    private BuildHistory() {
    }

    public static void record(ServerPlayerEntity player, BuildRecord record) {
        if (record.changes().isEmpty()) {
            return;
        }

        Deque<BuildRecord> history = HISTORY.computeIfAbsent(
                player.getUuid(), ignored -> new ArrayDeque<>());
        history.addFirst(record);

        while (history.size() > MAX_BUILDS) {
            history.removeLast();
        }
    }

    /**
     * 撤销最近 {@code steps} 次建造，按“后建先撤”的顺序恢复方块。
     * 维度已经不存在时跳过该记录。
     */
    public static UndoResult undo(MinecraftServer server, ServerPlayerEntity player, int steps) {
        Deque<BuildRecord> history = HISTORY.get(player.getUuid());

        if (history == null || history.isEmpty()) {
            return new UndoResult(0, 0, 0);
        }

        int builds = 0;
        int blocks = 0;
        int requested = Math.max(1, Math.min(steps, MAX_BUILDS));

        for (int i = 0; i < requested && !history.isEmpty(); i++) {
            BuildRecord record = history.removeFirst();
            ServerWorld world = server.getWorld(record.dimension());

            if (world == null) {
                // 维度已被移除，记录没有恢复目标，直接丢弃。
                continue;
            }

            for (BuildRecord.BlockChange change : record.changes()) {
                world.setBlockState(change.pos(), change.previousState(), Block.NOTIFY_ALL);
            }

            builds++;
            blocks += record.changes().size();
        }

        int remaining = history.size();

        if (history.isEmpty()) {
            HISTORY.remove(player.getUuid());
        }

        return new UndoResult(builds, blocks, remaining);
    }

    public static int remaining(UUID playerId) {
        Deque<BuildRecord> history = HISTORY.get(playerId);
        return history == null ? 0 : history.size();
    }

    /** 玩家退出时释放记录，避免长期运行的服务器积累内存。 */
    public static void forget(UUID playerId) {
        HISTORY.remove(playerId);
    }

    public record UndoResult(int builds, int blocks, int remainingBuilds) {
    }
}
