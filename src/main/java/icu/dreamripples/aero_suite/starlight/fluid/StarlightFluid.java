package icu.dreamripples.aero_suite.starlight.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * 星空液体的 source 流体。
 * <p>
 * 重写 {@link #tick(Level, BlockPos, FluidState)} 且<b>不调用 super.tick</b> —— super 即
 * {@code FlowingFluid.tick}，负责流体的流动/蔓延。不调用则桶倒出后只保持一格 source，
 * 不会向相邻方块蔓延（满足"只有一格体积"的设计）。由于 source 不蔓延，flowing 流体不会被生成，
 * 故 {@link ModFluids#STARLIGHT_FLOWING} 用默认 {@link BaseFlowingFluid.Flowing} 即可（实际不会出现）。
 * <p>
 * 参考 Aeronautics 的 {@code LevititeBlendNeoForge}（同样 extends BaseFlowingFluid 并重写 tick），
 * 区别在于本类刻意省略 super.tick 以禁止蔓延。
 */
public class StarlightFluid extends BaseFlowingFluid {
    public StarlightFluid(Properties properties) {
        super(properties);
    }

    @Override
    public void tick(Level level, BlockPos pos, FluidState state) {
        // 故意不调用 super.tick()：星空液体不流动蔓延，只保持一格 source。
    }

    @Override
    public boolean isSource(FluidState state) {
        return true;
    }

    @Override
    public int getAmount(FluidState state) {
        return 8;
    }
}
