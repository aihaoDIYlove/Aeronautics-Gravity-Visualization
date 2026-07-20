package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 更方便的模拟传动器 - 查表输出固定 RPM，无视输入转速。
 * 红石信号 0..15 对应 0 32 48 64 80 96 112 128 144 160 176 192 208 224 240 256 RPM。
 * <p>
 * 实现方式：
 * - 继承原版 AnalogTransmissionBlockEntity，复用其 tick() 中信号变化检测 + detach/attach 流程
 * - 重写 propagateRotationTo，让内部连接（主方块 <-> extraWheel）保持 1:1 传动
 *   （原版会按 signal 修改齿数比，与"查表固定值"语义冲突）
 * - 边界连接的速度注入由 RotationPropagatorMixin 拦截 getConveyedSpeed 完成
 */
public class ConvenientAnalogTransmissionBlockEntity extends AnalogTransmissionBlockEntity {

    /** 输出转速查表：signal 0..15 -> RPM */
    private static final float[] RPM_TABLE = {
            0f, 32f, 48f, 64f, 80f, 96f, 112f, 128f,
            144f, 160f, 176f, 192f, 208f, 224f, 240f, 256f
    };

    public ConvenientAnalogTransmissionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 查表返回当前信号对应的固定 RPM（绝对值）。 */
    public float getTargetSpeed() {
        Level level = getLevel();
        int signal = (level != null) ? level.getBestNeighborSignal(getBlockPos()) : 0;
        signal = Math.max(0, Math.min(15, signal));
        BlockState state = getBlockState();
        boolean reversed = state.hasProperty(ConvenientAnalogTransmissionBlock.REVERSED)
                && state.getValue(ConvenientAnalogTransmissionBlock.REVERSED);
        int idx = reversed ? (15 - signal) : signal;
        return RPM_TABLE[idx];
    }

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState state, BlockState state2,
                                     BlockPos pos, boolean b1, boolean b2) {
        // 内部连接（主方块 <-> extraWheel）保持 1:1 传动，绕过原版按 signal 修改齿数比的逻辑
        if (target == this) return 1f;
        if (target instanceof AnalogTransmissionBlockEntity.AnalogTransmissionCogwheel cog
                && cog.getParentBlockEntity() == this) {
            return 1f;
        }
        // 边界连接由 RotationPropagatorMixin 注入固定 RPM，这里返回 0 让原版不处理
        return 0f;
    }
}
