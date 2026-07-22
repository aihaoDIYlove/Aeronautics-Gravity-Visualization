package icu.dreamripples.aeronautics_gravity.mixin;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlockEntity;
import icu.dreamripples.aeronautics_gravity.block.ConvenientAnalogTransmissionBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 RotationPropagator.getConveyedSpeed，为更方便的模拟传动器注入查表固定 RPM。
 * <p>
 * 拦截规则：
 * <ol>
 *   <li>extraWheel → 主BE（内部连接）-> 注入 targetSpeed（齿轮速度→查表 RPM）</li>
 *   <li>主BE → extraWheel（内部连接）-> 若 extraWheel 已有转速则注入其当前速度防覆写，
 *       若无转速则放行（传动杆输入时正常传播）</li>
 *   <li>齿轮面 ↔ 外部齿轮 -> 放行，Create 原版齿轮逻辑处理（pass-through）</li>
 *   <li>主BE（传动杆面）↔ 外部 -> 注入 targetSpeed</li>
 *   <li>不涉及我们的方块 -> 不拦截</li>
 * </ol>
 * <p>
 * 信号 0 时 targetSpeed=0，注入返回 0——传动杆与齿轮环断开，
 * 齿轮环可独立作为普通齿轮传递应力——类似离合器效果。
 */
@Mixin(RotationPropagator.class)
public class RotationPropagatorMixin {

    @Inject(
            method = "getConveyedSpeed",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void aeronautics_gravity$getConveyedSpeed(
            KineticBlockEntity fromBE, KineticBlockEntity toBE,
            CallbackInfoReturnable<Float> cir) {

        // 规则 1：extraWheel → 主BE（内部连接，齿轮→传动杆转速转换）
        if (isOurCogwheel(fromBE) && toBE instanceof ConvenientAnalogTransmissionBlockEntity mainBE) {
            injectTableSpeed(mainBE, fromBE.getTheoreticalSpeed(), cir);
            return;
        }

        // 规则 2：主BE → extraWheel（内部连接，反向传播保护）
        // 当 extraWheel 已有转速（齿轮驱动中）时，注入其当前速度防止被覆写；
        // 当 extraWheel 无转速（传动杆输入中）时，放行让 Create 正常传播。
        if (fromBE instanceof ConvenientAnalogTransmissionBlockEntity && isOurCogwheel(toBE)) {
            float wheelSpeed = toBE.getTheoreticalSpeed();
            if (wheelSpeed != 0f) {
                cir.setReturnValue(wheelSpeed);
            }
            return;
        }

        // 规则 3：齿轮面参与的其他连接（外部齿轮 ↔ extraWheel）-> 放行
        if (isOurCogwheel(fromBE) || isOurCogwheel(toBE)) return;

        // 规则 4 & 5：主BE 的传动杆面边界连接
        ConvenientAnalogTransmissionBlockEntity fromOurs = resolveOurBlockEntity(fromBE);
        ConvenientAnalogTransmissionBlockEntity toOurs = resolveOurBlockEntity(toBE);

        // 不涉及我们的方块 -> 不拦截
        if (fromOurs == null && toOurs == null) return;

        // 两个都是我们的方块（两个传动器传动杆面直连等边缘情况）-> 不拦截
        if (fromOurs != null && toOurs != null) return;

        boolean neighborIsSource = (toOurs != null);
        ConvenientAnalogTransmissionBlockEntity ourBE = neighborIsSource ? toOurs : fromOurs;
        KineticBlockEntity neighbor = neighborIsSource ? fromBE : toBE;

        // 我们 -> 邻居，但邻居是源（generator）：源速度固定，不能被覆盖
        if (!neighborIsSource && neighbor.isSource()) {
            cir.setReturnValue(0f);
            return;
        }

        float referenceSpeed = neighborIsSource
                ? neighbor.getTheoreticalSpeed()
                : ourBE.getTheoreticalSpeed();

        injectTableSpeed(ourBE, referenceSpeed, cir);
    }

    /** 注入查表 RPM，保持参考速度的方向符号。targetSpeed=0 时返回 0（断开）。 */
    private static void injectTableSpeed(ConvenientAnalogTransmissionBlockEntity be, float referenceSpeed,
                                         CallbackInfoReturnable<Float> cir) {
        float targetSpeed = be.getTargetSpeed();
        if (targetSpeed == 0f || referenceSpeed == 0f) {
            cir.setReturnValue(0f);
            return;
        }
        cir.setReturnValue(Math.copySign(targetSpeed, referenceSpeed));
    }

    /** 检查 BE 是否是我们方块内部的齿轮（extraWheel）。 */
    private static boolean isOurCogwheel(KineticBlockEntity be) {
        if (be instanceof AnalogTransmissionBlockEntity.AnalogTransmissionCogwheel cog) {
            return cog.getParentBlockEntity() instanceof ConvenientAnalogTransmissionBlockEntity;
        }
        return false;
    }

    private static ConvenientAnalogTransmissionBlockEntity resolveOurBlockEntity(KineticBlockEntity be) {
        if (be instanceof ConvenientAnalogTransmissionBlockEntity direct) {
            return direct;
        }
        return null;
    }
}
