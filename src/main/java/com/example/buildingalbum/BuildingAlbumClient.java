package com.example.buildingalbum;

import com.example.buildingalbum.client.BlueprintModelPlugin;
import com.example.buildingalbum.client.ClientRotation;
import com.example.buildingalbum.config.GhostConfig;
import com.example.buildingalbum.network.BuildingAlbumNetworking;
import com.example.buildingalbum.render.GhostBlockRenderer;
import com.example.buildingalbum.render.StructurePreviewRenderer;
import com.example.buildingalbum.structure.StructurePlacement;
import com.example.buildingalbum.structure.StructurePlacementResolver;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/** 建筑影册的纯客户端入口。 */
public final class BuildingAlbumClient implements ClientModInitializer {
    public static final String MOD_ID = "building_album";

    private static GhostConfig config;
    private static KeyBinding toggleKey;
    private static KeyBinding rotateKey;

    /** 上一次通过动作栏提示的覆盖数量；-1 表示当前没有覆盖。 */
    private static int lastOverlapHint = -1;
    /** 覆盖提示的冷却（tick），避免瞄准移动时每 tick 刷屏。 */
    private static int overlapHintCooldown;

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

        rotateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.building_album.rotate",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.building_album"
        ));

        // 服务端返回权威旋转偏移；预览与真实建造都以它为准。
        ClientPlayNetworking.registerGlobalReceiver(
                BuildingAlbumNetworking.ROTATE_SYNC,
                (client, handler, buf, responseSender) -> {
                    int offset = buf.readByte();
                    client.execute(() -> {
                        ClientRotation.set(offset);

                        if (client.player != null) {
                            client.player.sendMessage(Text.translatable(
                                    "message.building_album.rotation", offset * 90), true);
                        }
                    });
                });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientRotation.reset();
            StructurePlacementResolver.clearCache();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientRotation.reset();
            StructurePlacementResolver.clearCache();
        });

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

        while (rotateKey.wasPressed()) {
            requestRotation(client);
        }

        updateOverlapHint(client);
    }

    /**
     * 预览会覆盖已有方块时，在动作栏提示数量（外框同时变红）。
     * 只在数量变化时提示，并保留 10 tick 冷却，避免连续刷屏。
     */
    private static void updateOverlapHint(MinecraftClient client) {
        if (overlapHintCooldown > 0) {
            overlapHintCooldown--;
        }

        if (!config.enabled || client.player == null) {
            lastOverlapHint = -1;
            return;
        }

        StructurePlacement placement = StructurePlacementResolver.resolve(client);
        int overwrites = placement == null ? 0 : placement.overwrites();
        if (overwrites == 0) {
            lastOverlapHint = -1;
            return;
        }

        if (overwrites == lastOverlapHint || overlapHintCooldown > 0) {
            return;
        }

        lastOverlapHint = overwrites;
        overlapHintCooldown = 10;
        client.player.sendMessage(Text.translatable(
                "message.building_album.overlap", overwrites).formatted(Formatting.YELLOW), true);
    }

    /** 本地先转 90° 让预览立即响应，再请服务端确认最终角度。 */
    private static void requestRotation(MinecraftClient client) {
        if (client.getNetworkHandler() == null) {
            return;
        }

        try {
            if (!ClientPlayNetworking.canSend(BuildingAlbumNetworking.ROTATE_REQUEST)) {
                return;
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return;
        }

        ClientRotation.set(ClientRotation.get() + 1);
        ClientPlayNetworking.send(BuildingAlbumNetworking.ROTATE_REQUEST, PacketByteBufs.empty());
    }

    public static GhostConfig getConfig() {
        return config;
    }
}
