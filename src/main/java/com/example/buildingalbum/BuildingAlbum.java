package com.example.buildingalbum;

import com.example.buildingalbum.item.BlueprintItem;
import com.example.buildingalbum.structure.Structure;
import com.example.buildingalbum.structure.StructureLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 建筑影册 —— 双端公共入口。
 * 负责注册「建筑蓝图」创造标签页与蓝图物品：
 * <ol>
 *   <li>内置结构（模组资源 assets/building_album/blueprints）；</li>
 *   <li>用户蓝图：扫描 config/building_album/blueprints/*.json，
 *       放入文件即自动注册，无需改代码。</li>
 * </ol>
 * 注意：用户蓝图需要客户端与服务端都存在同一份文件，注册表才会一致。
 */
public final class BuildingAlbum implements ModInitializer {
    public static final String MOD_ID = "building_album";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 蓝图结构名 -> 蓝图物品（按注册顺序）。 */
    private static final Map<String, BlueprintItem> BLUEPRINTS = new LinkedHashMap<>();
    private static ItemGroup blueprintGroup;

    /** 每张内置蓝图：[结构名, 资源内文件名]。 */
    private static final String[][] BUILTIN_BLUEPRINTS = {
            {"cabin", "cabin.json"},
            {"watchtower", "watchtower.json"},
            {"altar", "altar.json"},
            {"fountain", "fountain.json"},
            {"obelisk", "obelisk.json"},
            {"pavilion", "pavilion.json"},
            {"campsite", "campsite.json"},
    };

    @Override
    public void onInitialize() {
        // 内置蓝图
        for (String[] builtin : BUILTIN_BLUEPRINTS) {
            try {
                Structure structure = StructureLoader.load(MOD_ID, builtin[0], builtin[1]);
                registerBlueprint(builtin[0], structure, null);
            } catch (IOException exception) {
                throw new IllegalStateException("[Building Album] cannot load builtin blueprint '" + builtin[1] + "'", exception);
            }
        }

        // 用户自定义蓝图文件夹（自动创建）
        Path userDir = userBlueprintsDir();
        List<String> loaded = new ArrayList<>();
        try {
            Files.createDirectories(userDir);
            try (Stream<Path> files = Files.list(userDir)) {
                List<Path> jsons = files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
                for (Path file : jsons) {
                    String rawName = fileNameWithoutExtension(file.getFileName().toString());
                    String name = sanitizeName(rawName);
                    if (name.isEmpty()) {
                        LOGGER.warn("[Building Album] Skip blueprint '{}': bad file name", rawName);
                        continue;
                    }
                    if (BLUEPRINTS.containsKey(name)) {
                        LOGGER.warn("[Building Album] Skip user blueprint '{}': id already taken", name);
                        continue;
                    }
                    try {
                        Structure structure = StructureLoader.loadFromPath(name, file);
                        Text prettyName = Text.literal(prettify(rawName));
                        registerBlueprint(name, structure, prettyName);
                        loaded.add(name);
                    } catch (IOException | RuntimeException exception) {
                        LOGGER.error("[Building Album] Skip user blueprint '{}': {}", file.getFileName(), exception.getMessage());
                    }
                }
            }
        } catch (IOException exception) {
            LOGGER.error("[Building Album] cannot scan user blueprints at {}", userDir, exception);
        }
        if (!loaded.isEmpty()) {
            LOGGER.info("[Building Album] Loaded {} user blueprint(s) from {}: {}", loaded.size(), userDir, loaded);
        }

        registerItemGroup();
        LOGGER.info("[Building Album] Registered {} blueprint(s): {}", BLUEPRINTS.size(), BLUEPRINTS.keySet());
    }

    private static void registerBlueprint(String name, Structure structure, Text displayName) {
        Identifier itemId = Identifier.of(MOD_ID, "blueprint_" + name);
        BlueprintItem item = new BlueprintItem(new Item.Settings().maxCount(1), structure, displayName);
        Registry.register(Registries.ITEM, itemId, item);
        BLUEPRINTS.put(name, item);
    }

    private static Path userBlueprintsDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("blueprints");
    }

    private static String fileNameWithoutExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** 文件名 -> 合法物品 id 段（仅小写字母/数字/下划线/横线/点）。 */
    private static String sanitizeName(String rawName) {
        String cleaned = rawName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_");
        return cleaned.replaceAll("^_+|_+$", "");
    }

    /** 文件名 -> 展示名（如 my_house -> "My House"）。 */
    private static String prettify(String rawName) {
        String[] words = rawName.split("[_\\-.\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1));
        }
        return sb.length() == 0 ? rawName : sb.toString();
    }

    private static void registerItemGroup() {
        Identifier groupId = Identifier.of(MOD_ID, "blueprints");
        RegistryKey<ItemGroup> groupKey = RegistryKey.of(RegistryKeys.ITEM_GROUP, groupId);

        blueprintGroup = FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.building_album.blueprints"))
                .icon(() -> new ItemStack(firstBlueprint()))
                .build();
        Registry.register(Registries.ITEM_GROUP, groupKey, blueprintGroup);

        ItemGroupEvents.modifyEntriesEvent(groupKey).register(entries -> {
            for (BlueprintItem item : BLUEPRINTS.values()) {
                entries.add(new ItemStack(item));
            }
        });
    }

    private static BlueprintItem firstBlueprint() {
        return BLUEPRINTS.values().iterator().next();
    }

    /** 供客户端查询：结构名 -> 蓝图物品。 */
    public static Map<String, BlueprintItem> getBlueprints() {
        return Map.copyOf(BLUEPRINTS);
    }

    /** 是否为内置蓝图（内置蓝图有独立物品模型/语言条目；用户蓝图共享通用模型）。 */
    public static boolean isBuiltinBlueprint(String name) {
        for (String[] builtin : BUILTIN_BLUEPRINTS) {
            if (builtin[0].equals(name)) {
                return true;
            }
        }
        return false;
    }
}
