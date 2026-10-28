package com.example.buildingalbum.server;

import com.example.buildingalbum.BuildingAlbum;
import com.example.buildingalbum.item.BlueprintItem;
import com.example.buildingalbum.structure.Structure;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * {@code /buildingalbum} 命令树：
 * <ul>
 *   <li>{@code undo [次数]}：撤销自己最近的蓝图建造（仅玩家）；</li>
 *   <li>{@code list}：列出当前已注册的蓝图及其尺寸（玩家与控制台均可）；</li>
 *   <li>{@code pos1} / {@code pos2}：把准星指向的方块设为选区角点（仅玩家）；</li>
 *   <li>{@code save <名称>}：把选区导出为用户蓝图 JSON（需要管理员权限）。</li>
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
                                .executes(context -> list(context.getSource())))
                        .then(CommandManager.literal("pos1")
                                .executes(context -> setCorner(context.getSource(), true)))
                        .then(CommandManager.literal("pos2")
                                .executes(context -> setCorner(context.getSource(), false)))
                        .then(CommandManager.literal("save")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .executes(context -> save(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")))))));
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

    /** 把准星指向的方块记录为选区角点（服务端射线检测，最远 64 格）。 */
    private static int setCorner(ServerCommandSource source, boolean first) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.translatable("command.building_album.pos.players_only"));
            return 0;
        }

        HitResult hit = player.raycast(64.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendError(Text.translatable("command.building_album.pos.no_target"));
            return 0;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlueprintSelection.setCorner(
                player.getUuid(), first, player.getWorld().getRegistryKey(), pos);

        source.sendFeedback(() -> Text.translatable(
                "command.building_album.pos.set",
                first ? 1 : 2, pos.getX(), pos.getY(), pos.getZ()), false);

        boolean complete = BlueprintSelection.get(player.getUuid())
                .filter(BlueprintSelection.Selection::complete)
                .isPresent();
        if (complete) {
            source.sendFeedback(() -> Text.translatable(
                    "command.building_album.pos.ready"), false);
        }
        return 1;
    }

    /** 把选区导出成用户蓝图文件；写入前做重名、体积、空选区与可解析性校验。 */
    private static int save(ServerCommandSource source, String rawName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.translatable("command.building_album.save.players_only"));
            return 0;
        }

        Optional<BlueprintSelection.Selection> selected = BlueprintSelection.get(player.getUuid());
        if (selected.isEmpty() || !selected.get().complete()) {
            source.sendError(Text.translatable("command.building_album.save.no_selection"));
            return 0;
        }
        BlueprintSelection.Selection selection = selected.get();
        BlockPos first = selection.first().orElseThrow();
        BlockPos second = selection.second().orElseThrow();

        String name = BlueprintCapture.sanitizeName(rawName);
        if (name.isEmpty()) {
            source.sendError(Text.translatable("command.building_album.save.invalid_name"));
            return 0;
        }
        if (BlueprintCapture.isNameTaken(name)) {
            source.sendError(Text.translatable("command.building_album.save.name_taken", name));
            return 0;
        }

        Path file = BlueprintCapture.fileFor(name);
        if (Files.exists(file)) {
            source.sendError(Text.translatable(
                    "command.building_album.save.exists", file.toString()));
            return 0;
        }

        long volume = BlueprintCapture.volume(first, second);
        if (volume > BlueprintCapture.MAX_VOLUME) {
            source.sendError(Text.translatable(
                    "command.building_album.save.too_large", volume, BlueprintCapture.MAX_VOLUME));
            return 0;
        }

        ServerWorld world = source.getServer().getWorld(selection.dimension());
        if (world == null) {
            source.sendError(Text.translatable("command.building_album.save.dimension_missing"));
            return 0;
        }

        try {
            BlueprintCapture.Capture capture = BlueprintCapture.write(world, first, second, name);
            if (capture.blocks() == 0) {
                source.sendError(Text.translatable("command.building_album.save.empty"));
                return 0;
            }

            BlueprintSelection.forget(player.getUuid());
            BlockPos size = capture.size();
            source.sendFeedback(() -> Text.translatable(
                    "command.building_album.save.success",
                    name, capture.blocks(), size.getX(), size.getY(), size.getZ()), false);
            source.sendFeedback(() -> Text.translatable(
                    "command.building_album.save.path", capture.file().toString()), false);
            return capture.blocks();
        } catch (IOException exception) {
            BuildingAlbum.LOGGER.error("[Building Album] cannot save blueprint '{}'", name, exception);
            source.sendError(Text.translatable(
                    "command.building_album.save.failed", String.valueOf(exception.getMessage())));
            return 0;
        }
    }
}
