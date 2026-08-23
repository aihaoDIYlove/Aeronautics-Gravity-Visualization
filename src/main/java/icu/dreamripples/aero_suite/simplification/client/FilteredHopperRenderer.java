package icu.dreamripples.aero_suite.simplification.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import icu.dreamripples.aero_suite.simplification.block.FilteredHopperSlotPositioning;
import icu.dreamripples.aero_suite.simplification.block.FilteredSingleSlotHopperBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 过滤漏斗 BER: 在 4 个横向面各画一遍过滤标记物品(常驻渲染, 非悬停框)。
 * 镜像 Create {@code FilteringRenderer.renderOnBlockEntity} 的 {@link ValueBoxTransform.Sided}
 * 循环: 遍历 6 方向, 对每个非空 filter 调 {@link ValueBoxRenderer#renderItemIntoValueBox}
 * 画在 {@link FilteredHopperSlotPositioning} 定位处。空过滤不画(没有标记可显示)。
 *
 * <p>悬停白框由 {@link FilteredHopperOutline} 经 Outliner 单独画, 不在本 BER。
 */
@OnlyIn(Dist.CLIENT)
public class FilteredHopperRenderer implements BlockEntityRenderer<FilteredSingleSlotHopperBlockEntity> {

    private final FilteredHopperSlotPositioning positioning = new FilteredHopperSlotPositioning();

    public FilteredHopperRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FilteredSingleSlotHopperBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ItemStack filter = be.getFilter();
        if (filter.isEmpty())
            return;

        // Sided 定位是"按面可显示"的: isSideActive 限定 4 横向面, 每面画一遍同一 filter
        for (Direction direction : Direction.values()) {
            positioning.fromSide(direction);
            if (!positioning.shouldRender(be.getLevel(), be.getBlockPos(), be.getBlockState()))
                continue;
            pose.pushPose();
            positioning.transform(be.getLevel(), be.getBlockPos(), be.getBlockState(), pose);
            ValueBoxRenderer.renderItemIntoValueBox(filter, pose, buffer, packedLight, packedOverlay);
            pose.popPose();
        }
    }
}
