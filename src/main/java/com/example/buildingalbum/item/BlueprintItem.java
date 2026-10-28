package com.example.buildingalbum.item;

import com.example.buildingalbum.server.BlueprintRotation;
import com.example.buildingalbum.server.BuildHistory;
import com.example.buildingalbum.structure.BuildRecord;
import com.example.buildingalbum.structure.Structure;
import com.example.buildingalbum.structure.StructureBuilder;
import com.example.buildingalbum.structure.StructurePlacementMath;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * 一张建筑蓝图：绑定了对应的 {@link Structure}。
 * 手持并瞄准方块面时客户端显示整栋结构的半透明预览；
 * 对准后右键（服务器端）把结构真实放置到世界中。
 * <p>{@code displayName} 为空时使用翻译键 {@code item.<namespace>.blueprint_<id>}（内置蓝图）；
 * 用户自建蓝图直接传入美化后的名字。
 */
public class BlueprintItem extends Item {
    private static final int MAX_BUILD_PARTICLES = 24;

    private final Structure structure;
    private final Text displayName;

    public BlueprintItem(Settings settings, Structure structure) {
        this(settings, structure, null);
    }

    public BlueprintItem(Settings settings, Structure structure, Text displayName) {
        super(settings);
        this.structure = structure;
        this.displayName = displayName;
    }

    public Structure getStructure() {
        return structure;
    }

    @Override
    public Text getName(ItemStack stack) {
        return displayName != null ? displayName : super.getName(stack);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        if (player == null) {
            return ActionResult.PASS;
        }

        // 客户端：仅触发挥手动画并让服务器执行；真正的放置发生在服务端。
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        BlockPos origin = StructurePlacementMath.anchorOf(context.getBlockPos(), context.getSide());
        BlockRotation rotation = StructurePlacementMath.rotationFor(
                player.getHorizontalFacing(), BlueprintRotation.get(player.getUuid()));

        StructureBuilder.Result result = StructureBuilder.place(world, structure, origin, rotation);

        if (result.placed() > 0) {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                BuildHistory.record(serverPlayer, new BuildRecord(
                        world.getRegistryKey(), structure.id(), result.changes()));
            }

            if (world instanceof ServerWorld serverWorld) {
                playBuildEffects(serverWorld, origin, result.changes());
            }

            player.sendMessage(Text.translatable("message.building_album.built", result.placed()), true);
            return ActionResult.SUCCESS;
        }
        player.sendMessage(Text.translatable("message.building_album.build_failed"), true);
        return ActionResult.FAIL;
    }

    /** 建造完成音效 + 抽样粒子（大建筑最多 24 处，避免一次刷出上百个粒子）。 */
    private static void playBuildEffects(
            ServerWorld world, BlockPos origin, List<BuildRecord.BlockChange> changes) {
        world.playSound(null, origin, SoundEvents.BLOCK_STONE_PLACE,
                SoundCategory.BLOCKS, 0.8F, 0.9F);

        if (changes.isEmpty()) {
            return;
        }

        int step = Math.max(1, changes.size() / MAX_BUILD_PARTICLES);
        int spawned = 0;
        for (int i = 0; i < changes.size() && spawned < MAX_BUILD_PARTICLES; i += step, spawned++) {
            BlockPos pos = changes.get(i).pos();
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    1, 0.3D, 0.3D, 0.3D, 0.0D);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.building_album.blueprint.tooltip.size",
                structure.size().getX(), structure.size().getY(), structure.size().getZ())
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.building_album.blueprint.tooltip.preview").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.building_album.blueprint.tooltip.rotate").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.building_album.blueprint.tooltip.build").formatted(Formatting.GRAY));
    }
}
