# 建筑影册（Building Album）

一个 **Fabric 1.20.1** 模组。创造模式物品栏新增 **“建筑蓝图”（Structure Blueprint）** 标签页，内含多张蓝图；手持蓝图并瞄准地面/方块面时，会以半透明方式预览整栋建筑，**右键即可把建筑真实放置**到世界中。

> 兼容保留：手持普通方块时仍显示原版式的单方块放置预览（青色边框 + 半透明影子方块）。按 `G` 可开关所有预览。

## 行为说明

- 蓝图物品在“建筑蓝图”标签页中按顺序展示；每种蓝图对应一份结构 JSON。
- 手持蓝图瞄准方块表面时显示整栋建筑预览：方块本体半透明、外框青色；建筑会按玩家朝向做 90° 旋转，默认把结构的“正面”（JSON 的 +Z 一侧）朝向玩家。
- 预览与实际建造使用同一套坐标换算，预览显示在哪里、右键后建筑就出现在哪里。
- 建造为**免费放置**（当前版本不消耗材料、不校验背包），服务端执行，可直接覆盖目标格。
- 手持普通 `BlockItem` 时的单方块影子预览逻辑保持不变。

## 蓝图来源

模组启动时按以下顺序注册蓝图：

1. **内置蓝图**（打包在模组资源内）：小屋、瞭望塔、祭坛、喷泉、方尖碑、凉亭、营地。
2. **用户蓝图**：扫描配置文件目录下的 `building_album/blueprints/*.json`（开发环境为 `run/config/building_album/blueprints/`，正式安装为 `.minecraft/config/building_album/blueprints/`），**放入文件即自动注册成蓝图并加入创造栏**，无需改代码。

### 用户蓝图 JSON 格式

```json
{
  "size": {"x": 5, "y": 4, "z": 5},
  "blocks": [
    {"x": 0, "y": 0, "z": 0, "block": "minecraft:oak_planks"},
    {"x": 0, "y": 1, "z": 0, "block": "minecraft:oak_log", "properties": {"axis": "y"}},
    {"x": 2, "y": 1, "z": 4, "block": "minecraft:glass"}
  ]
}
```

- 坐标是相对坐标，`0,0,0` 为最小角，可写的范围 `x ∈ [0, size.x)`、`y ∈ [0, size.y)`、`z ∈ [0, size.z)`。
- `block` 为方块注册 id（支持任何已加载的方块，包括其他模组方块）；`properties` 可选，用于指定方块状态属性（如 `axis`、`facing`）。
- 蓝图物品 id 为 `building_album:blueprint_<文件名>`（文件名会做规范化：只保留小写字母/数字/`_-.`）。展示名取文件名美化结果（如 `my_house.json` → “My House”）。
- 修改/新增文件后**重启游戏**生效（注册表在启动时构建）。
- 内置结构文件同样位于模组 jar 的 `assets/building_album/blueprints/`，可作为格式参考。

## 配置

首次运行后生成 `.minecraft/config/building-album.json`：

```json
{
  "enabled": true,
  "opacity": 0.38,
  "edgeOpacity": 0.85
}
```

`opacity` 是预览方块透明度（自动限制为 `0.05`–`0.80`），`edgeOpacity` 是外框线透明度（`0.10`–`1.00`）。手工编辑后重启游戏生效。

## 构建与运行

要求 **JDK 17–21**（不要用 JDK 22+：Fabric 0.16.14 的 Mixin 无法解析新版 JDK class 文件，游戏会在启动阶段崩溃）。项目在 `gradle.properties` 中通过 `org.gradle.java.home` 指向仓库内 `.tools/jdk-21`（Temurin JDK 21，含 javac，已 gitignore）；Gradle 使用 9.5.1（fabric-loom 1.10.5 要求 Gradle ≥ 8.12）。

```powershell
.\gradlew.bat build      # 编译 + 打 JAR（产物 build/libs/building-album-1.0.0.jar）
.\gradlew.bat runClient  # 启动开发环境客户端
```

## 安装步骤

1. 安装 Minecraft 1.20.1 对应的 Fabric Loader。
2. 下载 1.20.1 对应的 Fabric API。
3. 将 Fabric API 和本模组 JAR 一起放进 `.minecraft/mods/`。
4. 用 Fabric 1.20.1 配置启动游戏。本模组现为双端注册（服务端也需安装，否则蓝图物品无法注册）；创造标签页/预览/右键建造在单人、局域网、服务器均可使用。
