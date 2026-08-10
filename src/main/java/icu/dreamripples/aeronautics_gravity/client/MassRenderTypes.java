package icu.dreamripples.aeronautics_gravity.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MassRenderTypes {

    /** 重心/浮心标记：无深度测试（穿墙可见），QUADS 模式画 blocky 杆 + 方块头。 */
    public static final RenderType CENTER_OF_MASS = RenderType.create(
            "aeronautics_gravity:center_of_mass",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderType.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderType.NO_DEPTH_TEST)
                    .setCullState(RenderType.NO_CULL)
                    .setWriteMaskState(RenderType.COLOR_WRITE)
                    .createCompositeState(false)
    );

    static RenderType centerOfMass() {
        return CENTER_OF_MASS;
    }
}