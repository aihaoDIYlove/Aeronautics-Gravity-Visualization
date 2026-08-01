package icu.dreamripples.aeronautics_gravity.fluid;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.function.Consumer;

/**
 * 星空液体的 FluidType。
 * <p>
 * 贴图复用 Aeronautics 的 levitite_blend（{@code aeronautics:fluid/levitite_blend_still} / {@code _flow}），
 * 视觉上与 levitite_blend 一致；雾色/着色走 {@link IClientFluidTypeExtensions} 默认实现。
 * <p>
 * NeoForge 1.21.1：{@link FluidType} 直接 implements {@link IClientFluidTypeExtensions} 即会被客户端自动注册
 * （参考 Aeronautics 的 {@code AeroFluidType}，同样不 override {@code initializeClient}）。
 */
public class StarlightFluidType extends FluidType implements IClientFluidTypeExtensions {
    private final ResourceLocation stillTexture;
    private final ResourceLocation flowingTexture;

    public StarlightFluidType(Properties properties, ResourceLocation stillTexture, ResourceLocation flowingTexture) {
        super(properties);
        this.stillTexture = stillTexture;
        this.flowingTexture = flowingTexture;
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        // FluidType 默认 initializeClient 是空实现 -- 必须显式注册 this 才能让
        // ClientExtensionsManager.FLUID_TYPE_EXTENSIONS 映射到本类, 否则 FluidSpriteCache
        // 取到 DEFAULT(空实现) -> getStillTexture 返回 null -> 渲染流体方块时
        // MapN.getOrDefault(null, ...) NPE 崩溃(且报错不指向 fluid, 极难排查).
        consumer.accept(this);
    }

    @Override
    public ResourceLocation getStillTexture() {
        return stillTexture;
    }

    @Override
    public ResourceLocation getFlowingTexture() {
        return flowingTexture;
    }
}
