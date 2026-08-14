package icu.dreamripples.aeronautics_gravity.fluid;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
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

    /**
     * 泡在星空液体里的掉落物匀速上升。
     * <p>
     * NeoForge 1.21.1 {@code ItemEntity.tick} 中:实体泡在非 vanilla 流体(本类 {@code isVanilla()} 默认 false)
     * 且淹没深度 > 0.1 时,每 tick 调用本方法 -- 该分支<b>替代</b> {@code applyGravity()},故物品在液体内不受重力,
     * 由本方法完全接管 y 方向运动(参考 {@code ItemEntity.tick} 第145-154行的分支)。
     * <p>
     * 固定 y=0.15/tick ≈ 3格/秒。{@code move()} 后 {@code multiply(f,0.98,f)} 把 y 衰减到 0.147,
     * 下一 tick 本方法重置为 0.15 -> 视觉严格匀速。x/z 保留惯性并施加 0.99 阻力,避免水平漂移突兀刹停。
     * <p>
     * 上升高度 = 星空液体柱高度(液体不蔓延,每格独立 source)。物品浮出液面脱离流体后,
     * 走 {@code applyGravity()} 分支自然下落,顶部需 Funnel/Depot 接走以完成向上物流。
     */
    @Override
    public void setItemMovement(ItemEntity entity) {
        Vec3 v = entity.getDeltaMovement();
        entity.setDeltaMovement(v.x * 0.99, 0.15, v.z * 0.99);
    }
}
