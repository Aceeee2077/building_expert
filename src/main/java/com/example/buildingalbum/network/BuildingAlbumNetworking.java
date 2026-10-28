package com.example.buildingalbum.network;

import com.example.buildingalbum.BuildingAlbum;
import com.example.buildingalbum.server.BlueprintRotation;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 蓝图旋转的服务端/客户端同步。
 * <p>1.20.1 的 Fabric 网络 API 使用 {@link Identifier} 通道：
 * <ul>
 *   <li>C2S {@code building_album:rotate_request}：玩家按 R，请求顺时针转 90°；</li>
 *   <li>S2C {@code building_album:rotate_sync}：服务端返回权威偏移（0–3）。</li>
 * </ul>
 */
public final class BuildingAlbumNetworking {
    public static final Identifier ROTATE_REQUEST =
            Identifier.of(BuildingAlbum.MOD_ID, "rotate_request");
    public static final Identifier ROTATE_SYNC =
            Identifier.of(BuildingAlbum.MOD_ID, "rotate_sync");

    private BuildingAlbumNetworking() {
    }

    /** 在公共入口注册：收到旋转请求后由服务端递增并回传。 */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(
                ROTATE_REQUEST,
                (server, player, handler, buf, responseSender) -> server.execute(() ->
                        sendRotation(player, BlueprintRotation.rotate(player.getUuid()))));
    }

    public static void sendRotation(ServerPlayerEntity player, int offset) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(offset);
        ServerPlayNetworking.send(player, ROTATE_SYNC, buf);
    }
}
