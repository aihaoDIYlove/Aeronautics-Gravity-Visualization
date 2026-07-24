package icu.dreamripples.aeronautics_gravity.mixin;

import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 AnalogTransmissionBlockEntity 的 private signal 字段 setter。
 * <p>
 * ConvenientAnalogTransmissionBlockEntity 用自有的 lastSignal 检测信号变化，但父类 tick
 * 也会用 signal 字段做同样的检测。若不同步父类 signal，super.tick() 会重复进入信号变化分支，
 * 再次 attachKinetics -> propagateNewSource，触发第二次 destroyBlock（掉落第二个物品）。
 */
@Mixin(AnalogTransmissionBlockEntity.class)
public interface AnalogTransmissionBlockEntityAccessor {

    @Accessor("signal")
    void aeronautics_gravity$setSignal(int value);
}
