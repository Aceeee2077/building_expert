package com.example.buildingalbum.server;

import com.example.buildingalbum.BuildingAlbum;
import com.example.buildingalbum.structure.Structure;
import com.example.buildingalbum.structure.StructureLoader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Locale;

/**
 * 把世界中的一块长方体区域导出为用户蓝图 JSON（写入 config/building_album/blueprints）。
 * <p>只保存非空气方块；空气格会被跳过（蓝图是“叠加式”的，不会清除目标位置的空气）。
 * 方块实体内容（箱子、告示牌文字等）不会保存。
 * <p>导出的文件与手工编写的用户蓝图格式完全一致，重启游戏/服务器后自动注册为蓝图物品。
 */
public final class BlueprintCapture {
    /** 单次导出的方块数上限，避免误选超大数据导致卡顿/内存暴涨。 */
    public static final int MAX_VOLUME = 32 * 32 * 32;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BlueprintCapture() {
    }

    /** 导出结果：blocks 为 0 时不会写文件，file 为 null。 */
    public record Capture(int blocks, BlockPos size, Path file) {
    }

    /** 蓝图 JSON 的保存路径（固定目录，文件名由蓝图 id 决定）。 */
    public static Path fileFor(String blueprintId) {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(BuildingAlbum.MOD_ID)
                .resolve("blueprints")
                .resolve(blueprintId + ".json");
    }

    /** 是否与已注册蓝图（内置或启动时扫描到的用户蓝图）重名。 */
    public static boolean isNameTaken(String blueprintId) {
        return BuildingAlbum.getBlueprints().containsKey(blueprintId);
    }

    /** 规范化名称：小写，仅保留 a-z0-9._-；为空表示名称无效。 */
    public static String sanitizeName(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return cleaned.replaceAll("^_+|_+$", "");
    }

    /** 两个角点围成的体积（含两端）。 */
    public static long volume(BlockPos a, BlockPos b) {
        return (long) (Math.abs(a.getX() - b.getX()) + 1)
                * (Math.abs(a.getY() - b.getY()) + 1)
                * (Math.abs(a.getZ() - b.getZ()) + 1);
    }

    /**
     * 读取区域并写出蓝图文件。
     *
     * @throws IOException 读写失败，或生成的 JSON 无法被 {@link StructureLoader} 解析
     */
    public static Capture write(World world, BlockPos cornerA, BlockPos cornerB, String blueprintId)
            throws IOException {
        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int minY = Math.min(cornerA.getY(), cornerB.getY());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int maxY = Math.max(cornerA.getY(), cornerB.getY());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());

        BlockPos size = new BlockPos(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        JsonArray blocks = new JsonArray();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    cursor.set(x, y, z);
                    BlockState state = world.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    blocks.add(toJson(state, x - minX, y - minY, z - minZ));
                }
            }
        }

        if (blocks.isEmpty()) {
            return new Capture(0, size, null);
        }

        JsonObject sizeJson = new JsonObject();
        sizeJson.addProperty("x", size.getX());
        sizeJson.addProperty("y", size.getY());
        sizeJson.addProperty("z", size.getZ());

        JsonObject root = new JsonObject();
        root.add("size", sizeJson);
        root.add("blocks", blocks);

        String json = GSON.toJson(root);
        // 先按加载器解析一遍，确保写出的文件一定能在下次启动时被加载。
        Structure parsed = StructureLoader.loadFromJson(blueprintId, json);
        if (parsed.blocks().size() != blocks.size()) {
            throw new IOException("captured blueprint failed round-trip validation");
        }

        Path file = fileFor(blueprintId);
        Files.createDirectories(file.getParent());
        Files.writeString(file, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return new Capture(blocks.size(), size, file);
    }

    private static JsonObject toJson(BlockState state, int x, int y, int z) {
        JsonObject entry = new JsonObject();
        entry.addProperty("x", x);
        entry.addProperty("y", y);
        entry.addProperty("z", z);
        entry.addProperty("block", Registries.BLOCK.getId(state.getBlock()).toString());

        JsonObject properties = new JsonObject();
        Collection<Property<?>> stateProperties = state.getProperties();
        for (Property<?> property : stateProperties) {
            properties.addProperty(property.getName(), propertyValue(state, property));
        }
        if (properties.size() > 0) {
            entry.add("properties", properties);
        }
        return entry;
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.name(state.get(property));
    }
}
