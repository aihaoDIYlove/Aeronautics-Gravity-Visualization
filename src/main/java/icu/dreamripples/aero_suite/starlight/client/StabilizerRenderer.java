package icu.dreamripples.aero_suite.starlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import icu.dreamripples.aero_suite.starlight.block.StabilizerBlock;
import icu.dreamripples.aero_suite.starlight.block.StabilizerBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 自稳定方块 BER - 仅画灯带(染色)。换皮后方块本体为不透明 cube_all,灯带用 72 突出版(REDSTONE_INDICATOR)。
 * 颜色按输出档(LIFT_TIER):青色带暗->亮插值,休眠灰。无 NBT sync(tier 在 BlockState 自动同步)。
 * 原星空(portal)视觉已迁移至世界锚点(WorldAnchorRenderer)。
 */
public class StabilizerRenderer extends SafeBlockEntityRenderer<StabilizerBlockEntity> {

    // 输出强度色带:暗青 -> 亮青(沿用旧 lift 模式的视觉,同红石配轻)
    private static final int LIFT_OFF = 0xFF013A3A;
    private static final int LIFT_ON  = 0xFF00CDCD;
    // 休眠:暗灰
    private static final int IDLE     = 0xFF222222;

    public StabilizerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(StabilizerBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        // 灯带染色 - 换皮后不透明 cube_all,用 72 突出版(REDSTONE_INDICATOR)
        BlockState state = be.getBlockState();
        int color = computeColor(state);
        SuperByteBuffer indicator = CachedBuffers.partial(ModPartialModels.REDSTONE_INDICATOR, state);
        indicator.color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
    }

    private static int computeColor(BlockState state) {
        int liftTier = state.getValue(StabilizerBlock.LIFT_TIER);
        if (liftTier > 1) {
            return icu.dreamripples.aero_suite.common.client.AeroSuiteColors.mixArgb(LIFT_OFF, LIFT_ON, (liftTier - 1) / 15F);
        }
        return IDLE;
    }
}
