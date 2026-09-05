package com.example.buildingalbum.client;

import com.example.buildingalbum.BuildingAlbum;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.util.Identifier;

/**
 * 用户自建蓝图物品没有同名模型文件（形如 assets/.../models/item/blueprint_<name>.json），
 * 这里在模型加载阶段把缺失的用户蓝图模型统一替换为通用蓝图模型
 * {@code building_album:item/blueprint_user}；内置蓝图保留各自图标。
 */
public final class BlueprintModelPlugin {
    private BlueprintModelPlugin() {
    }

    public static void register() {
        ModelLoadingPlugin.register(context -> context.resolveModel().register(resolverContext -> {
            Identifier id = resolverContext.id();
            if (!BuildingAlbum.MOD_ID.equals(id.getNamespace())) {
                return null;
            }
            String path = id.getPath();
            if (!path.startsWith("item/blueprint_")) {
                return null;
            }
            String name = path.substring("item/blueprint_".length());
            if (BuildingAlbum.isBuiltinBlueprint(name)) {
                return null; // 内置蓝图：走各自模型文件
            }
            return resolverContext.getOrLoadModel(Identifier.of(BuildingAlbum.MOD_ID, "item/blueprint_user"));
        }));
    }
}
