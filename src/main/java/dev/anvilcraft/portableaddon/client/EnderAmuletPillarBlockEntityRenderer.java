package dev.anvilcraft.portableaddon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.anvilcraft.portableaddon.init.blocks.ender_amulet_pillar.EnderAmuletPillarBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;

/**
 * 末影护符柱渲染器
 * 把挂载的护符按各自侧面渲染：四个侧面各自独立，每侧从柱子顶部沿主柱面从上往下
 * 最多排 4 个（方块实体位于底部段，坐标以底部段为原点）。
 */
public class EnderAmuletPillarBlockEntityRenderer implements BlockEntityRenderer<EnderAmuletPillarBlockEntity> {

    /** 护符缩放比例（固定，保证单个护符可见） */
    private static final float SCALE = 0.5F;
    /** 护符距柱心的水平距离（主柱面在 x/z 0.09375~0.90625，护符贴在柱面外侧） */
    private static final float RADIUS = 0.42F;
    /** 每侧第一个护符的顶部高度（顶部盖板底缘在 1.8125，下移避免遮挡） */
    private static final float Y_TOP = 1.55F;
    /** 护符之间的固定竖向间距 */
    private static final float Y_SPACING = 0.45F;

    public EnderAmuletPillarBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
        EnderAmuletPillarBlockEntity be,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        if (be.getLevel() == null) return;
        Map<Direction, List<ItemStack>> amuletsBySide = be.getAmuletsBySide();

        long seed = be.getBlockPos().asLong();
        // 每侧从柱子顶部往下依次排布，最多 4 个
        for (Map.Entry<Direction, List<ItemStack>> entry : amuletsBySide.entrySet()) {
            List<ItemStack> sideList = entry.getValue();
            for (int i = 0; i < sideList.size(); i++) {
                ItemStack stack = sideList.get(i);
                if (stack.isEmpty()) continue;
                // 固定间距从上往下依次排列，护符数量变化时不会重新排布
                float y = Y_TOP - i * Y_SPACING;

                // 依据该侧面选择柱面及旋转：护符正面朝外
                Direction side = entry.getKey();
                float x = 0.5F;
                float z = 0.5F;
                float rot = 0.0F;
                switch (side) {
                    case SOUTH -> { x = 0.5F; z = 0.5F + RADIUS; rot = 0.0F; }
                    case EAST -> { x = 0.5F + RADIUS; z = 0.5F; rot = 90.0F; }
                    case NORTH -> { x = 0.5F; z = 0.5F - RADIUS; rot = 180.0F; }
                    case WEST -> { x = 0.5F - RADIUS; z = 0.5F; rot = 270.0F; }
                    default -> { }
                }

                poseStack.pushPose();
                poseStack.translate(x, y, z);
                poseStack.mulPose(Axis.YP.rotationDegrees(rot));
                poseStack.scale(SCALE, SCALE, SCALE);
                Minecraft.getInstance().getItemRenderer().renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    buffers,
                    be.getLevel(),
                    (int) (seed + side.get2DDataValue() * 1000 + i)
                );
                poseStack.popPose();
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(EnderAmuletPillarBlockEntity blockEntity) {
        // 护符沿整根柱子分布，最高到 +1.8，需扩大剔除包围盒
        return new AABB(blockEntity.getBlockPos()).expandTowards(0.0, 1.0, 0.0).inflate(0.5);
    }
}
