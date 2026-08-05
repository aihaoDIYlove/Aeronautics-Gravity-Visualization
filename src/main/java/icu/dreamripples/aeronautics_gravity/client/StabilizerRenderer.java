package icu.dreamripples.aeronautics_gravity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import icu.dreamripples.aeronautics_gravity.block.StabilizerBlock;
import icu.dreamripples.aeronautics_gravity.block.StabilizerBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 自稳定方块 BER - 仅画灯带(染色)。换皮后方块本体为不透明 cube_all,灯带用 72 突出版(REDSTONE_INDICATOR)。
 * 颜色按 mass/lift tier:mass 模式红、lift 模式青、休眠灰。无 NBT sync(tier 在 BlockState 自动同步)。
 * 原星空(portal)视觉已迁移至世界锚点(WorldAnchorRenderer)。
 */
public class StabilizerRenderer extends SafeBlockEntityRenderer<StabilizerBlockEntity> {

    // mass 模式色带:暗红 -> 亮红(向下力语义,同红石配重)
    private static final int MASS_OFF = 0xFF560101;
    private static final int MASS_ON  = 0xFFCD0000;
    // lift 模式色带:暗青 -> 亮青(向上力语义,同红石配轻)
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
        int massTier = state.getValue(StabilizerBlock.MASS_TIER);
        int liftTier = state.getValue(StabilizerBlock.LIFT_TIER);
        if (massTier > 1) {
            return mixArgb(MASS_OFF, MASS_ON, (massTier - 1) / 15F);
        } else if (liftTier > 1) {
            return mixArgb(LIFT_OFF, LIFT_ON, (liftTier - 1) / 15F);
        }
        return IDLE;
    }

    private static int mixArgb(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
