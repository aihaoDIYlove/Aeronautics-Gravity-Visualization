package icu.dreamripples.aeronautics_gravity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import icu.dreamripples.aeronautics_gravity.block.StabilizerBlock;
import icu.dreamripples.aeronautics_gravity.block.StabilizerBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * 自稳定方块 BER - 同时画灯带(染色)和 portal 星空。
 *
 * 三层视觉:轻质玻璃壳(block model) > 8 边角灯带(本 BER 染色) > 中心星空块(本 BER portal shader)。
 *
 * **Flywheel Visual 互斥**:Flywheel 的 SectionCompilerMixin 对有 Visualizer 的 BE cancel renderable,
 * 导致 BER.render 不被调。所以灯带染色不能放 Flywheel Visual(StabilizerVisual 已删),必须放 BER。
 * BER 每 ColorState 变化时重算颜色(mass 红/lift 青/idle 灰),无 NBT sync(tier 在 BlockState 自动同步)。
 *
 * portal:用 ModRenderTypes.END_PORTAL_LEQUAL(复用 rendertype_end_portal shader,depth LEQUAL,
 * texture end_sky+end_portal 双 sampler)画 6 面内壁立方体(0.15..0.85),被灯带 8 边角框住。
 */
public class StabilizerRenderer extends SafeBlockEntityRenderer<StabilizerBlockEntity> {

    private static final float MIN = 0.15f;
    private static final float MAX = 0.85f;

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
        // 1. 灯带染色(SuperByteBuffer + cutout)
        BlockState state = be.getBlockState();
        int color = computeColor(state);
        SuperByteBuffer indicator = CachedBuffers.partial(ModPartialModels.REDSTONE_INDICATOR, state);
        indicator.color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));

        // 2. portal 星空 6 面内壁(0.15..0.85,被灯带 8 边角框住)
        VertexConsumer vc = buffer.getBuffer(ModRenderTypes.END_PORTAL_LEQUAL);
        Matrix4f m = ms.last().pose();
        float a = MIN, b = MAX;
        quad(vc, m, a, a, a, b, a, a, b, a, b, a, a, b);   // DOWN  (-Y)
        quad(vc, m, a, b, a, a, b, b, b, b, b, b, b, a);   // UP    (+Y)
        quad(vc, m, a, a, a, a, b, a, b, b, a, b, a, a);   // NORTH (-Z)
        quad(vc, m, a, a, b, b, a, b, b, b, b, a, b, b);   // SOUTH (+Z)
        quad(vc, m, a, a, a, a, a, b, a, b, b, a, b, a);   // WEST  (-X)
        quad(vc, m, b, a, a, b, b, a, b, b, b, b, a, b);   // EAST  (+X)
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

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3) {
        vc.addVertex(m, x0, y0, z0);
        vc.addVertex(m, x1, y1, z1);
        vc.addVertex(m, x2, y2, z2);
        vc.addVertex(m, x3, y3, z3);
    }
}
