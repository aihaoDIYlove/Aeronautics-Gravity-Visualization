package icu.dreamripples.aeronautics_gravity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import icu.dreamripples.aeronautics_gravity.block.WorldAnchorBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * 世界锚点 BER - 灯带(ANCHOR_INDICATOR 染色) + portal 星空。
 *
 * 三层视觉:复杂框架壳(block model) > 8 边角灯带(本 BER 染色,32 内嵌框住星空) > 中心星空块(本 BER portal shader)。
 * 灯带颜色 = {@link WorldAnchorBlockEntity#getLampColor()}:SEND 红/黄、RECEIVE 青/绿/白。
 *
 * **不用 Flywheel Visual**:Flywheel SectionCompilerMixin 对有 Visualizer 的 BE cancel renderable,
 * 导致 BER.render 不被调。所以灯带染色放 BER(同 stabilizer 的处理)。
 *
 * portal:用 {@link ModRenderTypes#END_PORTAL_LEQUAL}(复用 rendertype_end_portal shader,depth LEQUAL,
 * texture end_sky+end_portal 双 sampler)画 6 面内壁立方体(0.15..0.85),被灯带 8 边角框住。
 */
public class WorldAnchorRenderer extends SafeBlockEntityRenderer<WorldAnchorBlockEntity> {

    private static final float MIN = 0.15f;
    private static final float MAX = 0.85f;

    public WorldAnchorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(WorldAnchorBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        // 1. 灯带染色(ANCHOR_INDICATOR 32 内嵌,框住星空)
        BlockState state = be.getBlockState();
        int color = be.getLampColor();
        SuperByteBuffer indicator = CachedBuffers.partial(ModPartialModels.ANCHOR_INDICATOR, state);
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
