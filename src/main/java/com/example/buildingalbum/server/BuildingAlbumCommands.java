package com.example.buildingalbum.server;

import com.example.buildingalbum.BuildingAlbum;
import com.example.buildingalbum.item.BlueprintItem;
import com.example.buildingalbum.structure.Structure;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.List;

/**
 * {@code /buildingalbum} 命令树：
 * <ul>
 *   <li>{@code undo [次数]}：撤销自己最近的蓝图建造（仅玩家）；</li>
 *   <li>{@code list}：列出当前已注册的蓝图及其尺寸（玩家与控制台均可）。</li>
 * </ul>
 */
public final class BuildingAlbumCommands {
    private BuildingAlbumCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("buildingalbum")
                        .then(CommandManager.literal("undo")
                                .executes(context -> undo(context.getSource(), 1))
                                .then(CommandManager.argument(
                                                "steps", IntegerArgumentType.integer(1, BuildHistory.MAX_BUILDS))
                                        .executes(context -> undo(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "steps")))))
                        .then(CommandManager.literal("list")
                                .executes(context -> list(context.getSource())))));
    }

    private static int undo(ServerCommandSource source, int steps) {
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.translatable("command.building_album.undo.players_only"));
            return 0;
        }

        BuildHistory.UndoResult result = BuildHistory.undo(source.getServer(), player, steps);

        if (result.builds() == 0) {
            source.sendError(Text.translatable("command.building_album.undo.empty"));
            return 0;
        }

        source.sendFeedback(() -> Text.translatable(
                "command.building_album.undo.success",
                result.builds(),
                result.blocks(),
                result.remainingBuilds()), false);
        return result.blocks();
    }

    /** 按结构 id 排序输出，保证每次结果顺序稳定（{@code Map.copyOf} 不保证顺序）。 */
    private static int list(ServerCommandSource source) {
        List<BlueprintItem> items = BuildingAlbum.getBlueprints().values().stream()
                .sorted(Comparator.comparing(item -> item.getStructure().id()))
                .toList();

        source.sendFeedback(() -> Text.translatable(
                "command.building_album.list.header", items.size()), false);

        for (BlueprintItem item : items) {
            Structure structure = item.getStructure();
            BlockPos size = structure.size();
            source.sendFeedback(() -> Text.translatable(
                    "command.building_album.list.entry",
                    structure.id(), size.getX(), size.getY(), size.getZ()), false);
        }
        return items.size();
    }
}
