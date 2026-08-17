package icu.dreamripples.aero_suite.mixin.simplification;

import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlockEntity;
import icu.dreamripples.aero_suite.simplification.block.ConvenientAnalogTransmissionBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 AnalogTransmissionBlockEntity 的 private signal 字段 getter/setter。
 * <p>
 * ConvenientAnalogTransmissionBlockEntity 直接复用父类持久化的 signal 字段检测信号变化，
 * 而非自维护 lastSignal。原因：lastSignal 不持久化，重进存档后重置为 0，而父类 signal 从 NBT
 * 恢复为之前的值，导致 bestNeighborSignal != lastSignal 误判信号变化，触发一次不必要的
 * detach/reattach。重进时主BE/extraWheel 的 speed 从 NBT 恢复为非0值，handleRemoved 会执行
 * propagateMissingSource 重新 add 负载（source=主BE），后续 reattach 时序中 extraWheel 被
 * 主BE反向驱动（而非从外部齿轮获取速度），与外部齿轮速度冲突，表现为过载卡住。
 * 复用持久化的 signal 字段后，重进时 signal==bestNeighborSignal，不进入 reattach 分支。
 */
@Mixin(AnalogTransmissionBlockEntity.class)
public interface AnalogTransmissionBlockEntityAccessor {

    @Accessor("signal")
    int aeronautics_gravity$getSignal();

    @Accessor("signal")
    void aeronautics_gravity$setSignal(int value);
}