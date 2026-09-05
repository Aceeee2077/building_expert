package com.example.buildingalbum.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从模组资源 {@code assets/<modId>/blueprints/<file>.json} 加载结构。
 * <p>JSON 格式：
 * <pre>
 * {
 *   "size": {"x": 7, "y": 6, "z": 7},
 *   "blocks": [
 *     {"x": 0, "y": 0, "z": 0, "block": "minecraft:oak_planks"},
 *     {"x": 0, "y": 1, "z": 0, "block": "minecraft:oak_log", "properties": {"axis": "x"}}
 *   ]
 * }
 * </pre>
 * 方块状态解析只依赖静态注册表（Registries.BLOCK），客户端/服务端均可安全调用。
 */
public final class StructureLoader {
    private StructureLoader() {
    }

    /**
     * 读取并解析一个结构文件。
     *
     * @param structureId 结构 id（如 "cabin"），仅用于日志/标识
     * @param file        资源路径下的文件名（如 "cabin.json"）
     */
    public static Structure load(String modId, String structureId, String file) throws IOException {
        String resource = "/assets/" + modId + "/blueprints/" + file;
        try (InputStream stream = StructureLoader.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("blueprint resource not found: " + resource);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return parse(structureId, JsonParser.parseReader(reader).getAsJsonObject());
            }
        }
    }

    /** 从文件系统中的 JSON 文件加载（用于用户蓝图文件夹）。 */
    public static Structure loadFromPath(String structureId, Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(structureId, JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    /** 从 JSON 字符串加载（测试 / 转换工具用）。 */
    public static Structure loadFromJson(String structureId, String json) throws IOException {
        return parse(structureId, JsonParser.parseString(json).getAsJsonObject());
    }

    private static Structure parse(String structureId, JsonObject root) throws IOException {
        JsonObject sizeJson = root.getAsJsonObject("size");
        BlockPos size = new BlockPos(
                sizeJson.get("x").getAsInt(),
                sizeJson.get("y").getAsInt(),
                sizeJson.get("z").getAsInt());

        List<Structure.Entry> blocks = new ArrayList<>();
        JsonArray blockArray = root.getAsJsonArray("blocks");
        for (JsonElement element : blockArray) {
            JsonObject entry = element.getAsJsonObject();
            int x = entry.get("x").getAsInt();
            int y = entry.get("y").getAsInt();
            int z = entry.get("z").getAsInt();
            String blockId = entry.get("block").getAsString();
            BlockState state = parseBlockState(blockId, entry.getAsJsonObject("properties"));
            blocks.add(new Structure.Entry(new BlockPos(x, y, z), state));
        }
        return new Structure(structureId, size, List.copyOf(blocks));
    }

    private static BlockState parseBlockState(String blockId, JsonObject properties) throws IOException {
        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            throw new IOException("unknown block in blueprint: " + blockId);
        }
        Block block = Registries.BLOCK.get(identifier);
        BlockState state = block.getDefaultState();

        if (properties != null) {
            for (Map.Entry<String, JsonElement> propertyEntry : properties.entrySet()) {
                state = applyProperty(state, propertyEntry.getKey(), propertyEntry.getValue().getAsString());
            }
        }
        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState applyProperty(BlockState state, String name, String value) throws IOException {
        Property property = state.getBlock().getStateManager().getProperty(name);
        if (property == null) {
            throw new IOException("unknown property '" + name + "' on " + state.getBlock());
        }
        Optional parsed = property.parse(value);
        if (parsed.isEmpty()) {
            throw new IOException("invalid value '" + value + "' for property '" + name + "'");
        }
        return state.with(property, (Comparable) parsed.get());
    }
}
