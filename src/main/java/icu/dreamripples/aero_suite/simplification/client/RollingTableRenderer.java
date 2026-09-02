package icu.dreamripples.aero_suite.simplification.client;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import icu.dreamripples.aero_suite.simplification.block.RollingTableBlockEntity;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 滚动加工台渲染:台面物品为 BE 伪渲染(非真实掉落物),位移按
 * {@code lerp(prevBeltPosition, beltPosition, partialTicks)} 插值,并按位移量做
 * {@code offset * 360} 翻转产生"滚动"错觉(照抄 Create ItemDrainRenderer, MIT)。
 * 上方投入(insertedFrom 垂直)的物品居中展示,不做翻转。
 */
public class RollingTableRenderer extends SmartBlockEntityRenderer<RollingTableBlockEntity> {

    public RollingTableRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(RollingTableBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        renderItem(be, partialTicks, ms, buffer, light, overlay);
    }

    protected void renderItem(RollingTableBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        TransportedItemStack transported = be.getHeldItem();
        if (transported == null)
            return;

        var msr = TransformStack.of(ms);
        Direction insertedFrom = transported.insertedFrom;
        if (insertedFrom == null)
            return;
        boolean horizontal = insertedFrom.getAxis().isHorizontal();

        ms.pushPose();
        ms.translate(.5f, 15 / 16f, .5f);
        float offset = Mth.lerp(partialTicks, transported.prevBeltPosition, transported.beltPosition);
        float sideOffset = Mth.lerp(partialTicks, transported.prevSideOffset, transported.sideOffset);

        if (horizontal) {
            Vec3 offsetVec = Vec3.atLowerCornerOf(insertedFrom.getOpposite().getNormal())
                .scale(.5f - offset);
            ms.translate(offsetVec.x, offsetVec.y, offsetVec.z);
            boolean alongX = insertedFrom.getClockWise().getAxis() == Direction.Axis.X;
            if (!alongX)
                sideOffset *= -1;
            ms.translate(alongX ? sideOffset : 0, 0, alongX ? 0 : sideOffset);
        } else {
            ms.translate(0, 0, 0);
        }

        ItemStack itemStack = transported.stack;
        Random r = new Random(0);
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        int count = (int) (Mth.log2((int) (itemStack.getCount()))) / 2;
        boolean renderUpright = BeltHelper.isItemUpright(itemStack);
        BakedModel bakedModel = itemRenderer.getModel(itemStack, null, null, 0);
        boolean blockItem = bakedModel.isGui3d();

        if (renderUpright)
            ms.translate(0, 3 / 32d, 0);

        if (horizontal) {
            int positive = insertedFrom.getAxisDirection().getStep();
            float verticalAngle = positive * offset * 360;
            if (insertedFrom.getAxis() != Direction.Axis.X)
                msr.rotateXDegrees(verticalAngle);
            if (insertedFrom.getAxis() != Direction.Axis.Z)
                msr.rotateZDegrees(-verticalAngle);

            if (renderUpright) {
                Vec3 itemPosition = VecHelper.getCenterOf(be.getBlockPos());
                Vec3 offsetVec = Vec3.atLowerCornerOf(insertedFrom.getOpposite().getNormal())
                    .scale(.5f - offset);
                Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                Vec3 vectorForOffset = itemPosition.add(offsetVec);
                Vec3 diff = vectorForOffset.subtract(cameraPosition);

                if (insertedFrom.getAxis() != Direction.Axis.X)
                    diff = VecHelper.rotate(diff, verticalAngle, Direction.Axis.X);
                if (insertedFrom.getAxis() != Direction.Axis.Z)
                    diff = VecHelper.rotate(diff, -verticalAngle, Direction.Axis.Z);

                float yRot = (float) Mth.atan2(diff.z, -diff.x);
                ms.mulPose(Axis.YP.rotation((float) (yRot - Math.PI / 2)));
                ms.translate(0, 0, -1 / 16f);
            }
        }

        for (int i = 0; i <= count; i++) {
            ms.pushPose();
            if (blockItem)
                ms.translate(r.nextFloat() * .0625f * i, 0, r.nextFloat() * .0625f * i);
            ms.scale(.5f, .5f, .5f);
            if (!blockItem && !renderUpright)
                msr.rotateXDegrees(90);
            itemRenderer.render(itemStack, ItemDisplayContext.FIXED, false, ms, buffer, light, overlay, bakedModel);
            ms.popPose();

            if (!renderUpright) {
                if (!blockItem)
                    msr.rotateYDegrees(10);
                ms.translate(0, blockItem ? 1 / 64d : 1 / 16d, 0);
            } else
                ms.translate(0, 0, -1 / 16f);
        }

        ms.popPose();
    }
}
