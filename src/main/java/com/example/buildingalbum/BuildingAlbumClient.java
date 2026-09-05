package com.example.buildingalbum;

import com.example.buildingalbum.client.BlueprintModelPlugin;
import com.example.buildingalbum.config.GhostConfig;
import com.example.buildingalbum.render.GhostBlockRenderer;
import com.example.buildingalbum.render.StructurePreviewRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** 建筑影册的纯客户端入口。 */
public final class BuildingAlbumClient implements ClientModInitializer {
    public static final String MOD_ID = "building_album";

    private static GhostConfig config;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        BlueprintModelPlugin.register();
        config = GhostConfig.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.building_album.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.building_album"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(BuildingAlbumClient::onEndTick);
        // 此阶段拥有实体缓冲区，半透明模型和线框会随本帧一起正确提交。
        // 手持蓝图 → 渲染整栋结构预览；否则退回原单方块放置预览。
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (StructurePreviewRenderer.render(context)) {
                return;
            }
            GhostBlockRenderer.render(context);
        });
    }

    private static void onEndTick(MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            config.enabled = !config.enabled;
            config.save();
            if (client.player != null) {
                client.player.sendMessage(Text.translatable(config.enabled
                        ? "message.building_album.enabled"
                        : "message.building_album.disabled"), true);
            }
        }
    }

    public static GhostConfig getConfig() {
        return config;
    }
}
