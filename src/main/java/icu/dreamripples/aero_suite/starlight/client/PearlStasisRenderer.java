package icu.dreamripples.aero_suite.starlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import icu.dreamripples.aero_suite.starlight.block.PearlStasisBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 珍珠滞留台 BER: 把内部物品渲染在方块内 8px(6/16)高处, 模仿 Create
 * {@code DepotRenderer} 的物品变换:
 * <ul>
 *   <li><b>平放</b>(默认): 非方块物品 rotateX 90 + -3/16 y 偏移, 方块物品缩放 0.5 直立;</li>
 *   <li><b>直立</b>({@code create:upright_on_belt} 标签 / 流体容器, 经
 *       {@link BeltHelper#isItemUpright}): billboard 朝向相机 + 3/32 y 抬升,
 *       方块物品额外缩 0.755 防底部穿模 -- 与传送带/置物台上的蛋糕、激活珍珠姿态一致。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class PearlStasisRenderer implements BlockEntityRenderer<PearlStasisBlockEntity> {

    private static final float ITEM_Y = 6 / 16f;

    public PearlStasisRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PearlStasisBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ItemStack stack = be.getHeld();
        if (stack.isEmpty()) return;

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        BakedModel model = itemRenderer.getModel(stack, null, null, 0);
        boolean blockItem = model.isGui3d();
        boolean upright = BeltHelper.isItemUpright(stack);

        pose.pushPose();
        pose.translate(0.5F, ITEM_Y, 0.5F);

        if (upright) {
            // 直立物品 billboard 朝向相机(复刻 DepotRenderer.renderItem upright 分支)
            Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 diff = Vec3.atCenterOf(be.getBlockPos()).subtract(cameraPosition);
            float yRot = (float) (Mth.atan2(diff.x, diff.z) + Math.PI);
            pose.mulPose(Axis.YP.rotation(yRot));
            pose.translate(0, 3 / 32F, -1 / 16F);
        }

        if (blockItem && upright) {
            // 直立方块物品(如蛋糕): 稍微抬一点防底部穿模, 缩小到 0.755(同 Depot)
            pose.translate(0, 1 / 16F, 0);
            pose.scale(0.755F, 0.755F, 0.755F);
        } else {
            pose.scale(0.5F, 0.5F, 0.5F);
        }

        if (!blockItem && !upright) {
            // 平放: 与 DepotRenderer.renderItem 非 blockItem 分支一致
            pose.translate(0, -3 / 16F, 0);
            pose.mulPose(Axis.XP.rotationDegrees(90));
        }

        itemRenderer.render(stack, ItemDisplayContext.FIXED, false, pose, buffer, packedLight, packedOverlay, model);
        pose.popPose();
    }
}
