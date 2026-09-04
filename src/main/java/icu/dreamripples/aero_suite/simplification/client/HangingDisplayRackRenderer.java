package icu.dreamripples.aero_suite.simplification.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import icu.dreamripples.aero_suite.simplification.block.HangingDisplayRackBlock;
import icu.dreamripples.aero_suite.simplification.block.HangingDisplayRackBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
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
            Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 diff = Vec3.atCenterOf(be.getBlockPos()).subtract(cameraPosition);
            float yRot = (float) (Mth.atan2(diff.x, diff.z) + Math.PI);
            pose.mulPose(Axis.YP.rotation(yRot));
        }

        pose.scale(0.5F, 0.5F, 0.5F);
        itemRenderer.render(stack, ItemDisplayContext.FIXED, false, pose, buffer, packedLight, packedOverlay, model);
        pose.popPose();
    }
}
