package com.example.buildingalbum.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 极简 JSON 配置。文件位于 .minecraft/config/building-album.json。
 * 手工修改后需要重启游戏；快捷键开关会立即保存 enabled。
 */
public final class GhostConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("building-album.json");

    public boolean enabled = true;
    public float opacity = 0.38F;
    public float edgeOpacity = 0.85F;

    public static GhostConfig load() {
        if (Files.isRegularFile(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                GhostConfig loaded = GSON.fromJson(reader, GhostConfig.class);
                if (loaded != null) {
                    loaded.sanitize();
                    return loaded;
                }
            } catch (IOException | RuntimeException exception) {
                System.err.println("[Building Album] Cannot read config: " + exception.getMessage());
            }
        }

        GhostConfig defaults = new GhostConfig();
        defaults.save();
        return defaults;
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            System.err.println("[Building Album] Cannot save config: " + exception.getMessage());
        }
    }

    private void sanitize() {
        // 完全透明时很难确认位置；过高则容易与真实方块混淆。
        opacity = clamp(opacity, 0.05F, 0.80F);
        edgeOpacity = clamp(edgeOpacity, 0.10F, 1.00F);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
