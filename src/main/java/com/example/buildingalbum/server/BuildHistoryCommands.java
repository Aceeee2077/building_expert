package com.example.buildingalbum.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * {@code /buildingalbum undo [次数]}：撤销自己最近的蓝图建造。
 * 只允许玩家执行，且每位玩家的历史互相隔离。
 */
public final class BuildHistoryCommands {
    private BuildHistoryCommands() {
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
                                                IntegerArgumentType.getInteger(context, "steps")))))));
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
}
