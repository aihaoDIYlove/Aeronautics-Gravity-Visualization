package icu.dreamripples.aeronautics_gravity.fluid;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.function.Consumer;

/**
 * 星空液体的 FluidType。
 * <p>
 * 贴图为本 mod 自制的 {@code aeronautics_gravity:fluid/starlight_still} / {@code starlight_flow}
 * (末地之海色调星海动画, 由 tools/gen_starlight.py 生成); 雾色/着色走 {@link IClientFluidTypeExtensions} 默认实现。
 * <p>
 * NeoForge 1.21.1：{@link FluidType} 默认 {@code initializeClient} 是空实现, 直接 implements
 * {@link IClientFluidTypeExtensions} 并不会自动注册 -- 必须 override {@code initializeClient} 并
 * {@code consumer.accept(this)}, 否则 {@code ClientExtensionsManager.FLUID_TYPE_EXTENSIONS} 映射到
 * DEFAULT(空实现) -> {@code getStillTexture} 返回 null -> 渲染流体方块时 {@code FluidSpriteCache}
 * {@code MapN.getOrDefault(null, ...)} NPE 崩溃(且报错不指向 fluid, 极难排查). 见下方 {@link #initializeClient}.
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
