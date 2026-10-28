package com.example.buildingalbum.client;

/**
 * 客户端缓存的蓝图旋转偏移，由服务端 S2C 同步包更新。
 * 预览渲染每帧读取该值；进入/退出世界时重置为 0。
 */
public final class ClientRotation {
    private static int offset = 0;

    private ClientRotation() {
    }

    public static int get() {
        return offset;
    }

    public static void set(int value) {
        offset = Math.floorMod(value, 4);
    }

    public static void reset() {
        offset = 0;
    }
}
