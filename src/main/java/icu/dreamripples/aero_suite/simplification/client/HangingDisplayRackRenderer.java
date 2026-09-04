package icu.dreamripples.aero_suite.simplification.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import icu.dreamripples.aero_suite.simplification.block.HangingDisplayRackBlock;
import icu.dreamripples.aero_suite.simplification.block.HangingDisplayRackBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 悬挂展示架 BER: 方块本体纯透明, 把槽内物品渲染在薄板外侧居中悬空
 * (物品中心 = 薄板内侧面沿 FACING 再退 3.5px; 贴天花板下时 y=11.5/16)。
 * 方块物品按半尺寸立方渲染; 平面物品 billboard 朝向相机(测试方块无固定朝向,
 * 保证任何视角都能看到槽内是什么), 姿态复刻 ItemFrame 的 FIXED 竖立展示。
 *
 * <p><b>billboard 的 sublevel 修正(2026-09-04)</b>: 物理化结构上 BE 坐标是 plot
 * 空间的远坐标, 直接 {@code 相机世界坐标 - be.getBlockPos()} 方向近似恒定(bug:
 * 船上平面物品永远朝一个方向)。修法: 相机位置先经 sublevel {@code logicalPose}
 * 的 {@code transformPositionInverse} 逆变换到 BE 局部系(纯 double, 主世界无
 * sublevel 时为恒等), 再算朝向。Create 桌布(TableClothRenderer)无此问题是因为
 * 它根本不做相机数学——物品朝向全部是 {@code blockEntity.facing} 的局部系固定旋转。
 */
@OnlyIn(Dist.CLIENT)
public class HangingDisplayRackRenderer implements BlockEntityRenderer<HangingDisplayRackBlockEntity> {

    public HangingDisplayRackRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(HangingDisplayRackBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ItemStack stack = be.getHeld();
        if (stack.isEmpty()) return;

        // 薄板贴支撑面(0..1px), 物品中心在其内侧沿 FACING 退 3.5px: 负向面(DOWN/NORTH/WEST)
        // 薄板在 15..16px 端 → 物品中心 11.5px; 正向面(UP/SOUTH/EAST) → 4.5px。其余两轴居中。
        Direction facing = be.getBlockState().getValue(HangingDisplayRackBlock.FACING);
        float d = (facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 11.5f : 4.5f) / 16f;
        float x = facing.getAxis() == Direction.Axis.X ? d : 0.5f;
        float y = facing.getAxis() == Direction.Axis.Y ? d : 0.5f;
        float z = facing.getAxis() == Direction.Axis.Z ? d : 0.5f;

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        BakedModel model = itemRenderer.getModel(stack, null, null, 0);
        boolean blockItem = model.isGui3d();

        pose.pushPose();
        pose.translate(x, y, z);

        if (!blockItem) {
            // 平面物品 billboard 朝向相机(FIXED 上下文的精灵在 XY 竖立平面内, 转 Y 即可)
            Vec3 diff = cameraDirection(be.getBlockPos());
            float yRot = (float) (Mth.atan2(diff.x, diff.z) + Math.PI);
            pose.mulPose(Axis.YP.rotation(yRot));
        }

        pose.scale(0.5F, 0.5F, 0.5F);
        itemRenderer.render(stack, ItemDisplayContext.FIXED, false, pose, buffer, packedLight, packedOverlay, model);
        pose.popPose();
    }

    /** 方块中心指向相机的向量(BE 局部系; 物理化结构上先逆变换相机坐标, 见类注释)。 */
    private static Vec3 cameraDirection(BlockPos blockPos) {
        Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        SubLevel subLevel = Sable.HELPER.getContaining(Minecraft.getInstance().level, blockPos);
        if (subLevel != null) {
            cameraPosition = subLevel.logicalPose().transformPositionInverse(cameraPosition);
        }
        return Vec3.atCenterOf(blockPos).subtract(cameraPosition);
    }
}
