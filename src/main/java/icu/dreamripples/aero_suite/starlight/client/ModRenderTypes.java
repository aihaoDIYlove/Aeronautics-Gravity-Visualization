package icu.dreamripples.aero_suite.starlight.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;

/**
 * 自定义 RenderType - 复用原版 rendertype_end_portal shader,depth 用 LEQUAL + NO_CULL。
 *
 * 关键:texture state 必须用 MultiTextureStateShard 绑定两个贴图(end_sky + end_portal),
 * 因为 rendertype_end_portal.fsh 采样两个 sampler(Sampler0=end_sky, Sampler1=end_portal)。
 * 只绑 end_portal 会导致 end_sky sampler 采样空,shader 输出全黑 -> 看不到星空。
 * (原版 RenderType.endPortal() 的 texture state 就是这两个贴图,见 TheEndPortalRenderer.END_SKY_LOCATION/END_PORTAL_LOCATION)
 *
 * depth 用 LEQUAL(默认就是 LEQUAL,显式写出明确意图):portal quad 在玻璃壳内部,
 * 透过玻璃透明部分(discard 不写深度,缓冲=clear)portal 深度 < 1.0 通过;被玻璃边框遮挡部分
 * (缓冲=边框深度)portal 深度更大不通过,自然剔除。NO_CULL 让 6 面内壁顶点顺序无需精确。
 *
 * extends RenderStateShard 是为了访问 protected 字段 RENDERTYPE_END_PORTAL_SHADER / LEQUAL_DEPTH_TEST /
 * NO_CULL / MultiTextureStateShard,同 Create 的 foundation.render.RenderTypes 模式。
 */
public class ModRenderTypes extends RenderStateShard {

    public static final RenderType END_PORTAL_LEQUAL = RenderType.create(
            "stabilizer_end_portal",
            DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_END_PORTAL_SHADER)
                    .setTextureState(
                            MultiTextureStateShard.builder()
                                    .add(TheEndPortalRenderer.END_SKY_LOCATION, false, false)
                                    .add(TheEndPortalRenderer.END_PORTAL_LOCATION, false, false)
                                    .build())
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private ModRenderTypes() {
        super("stabilizer_end_portal", () -> {
        }, () -> {
        });
    }
}
