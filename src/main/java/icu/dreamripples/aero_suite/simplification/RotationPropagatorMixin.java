package icu.dreamripples.aero_suite.simplification;

import com.simibubi.create.content.kinetics.RotationPropagator;
import icu.dreamripples.aero_suite.simplification.block.ConvenientAnalogTransmissionBlockEntity;
import icu.dreamripples.aero_suite.simplification.client.ConvenientAnalogTransmissionVisual;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 RotationPropagator.getConveyedSpeed 的 RETURN，为更方便的模拟传动器注入查表固定 RPM。
 * <p>
 * 关键：在 RETURN 处拦截而非 HEAD，让原版 getRotationSpeedModifier 先正常计算
 * （其中包含齿轮箱 getAxisModifier 的反转修饰符），再用原版返回值的符号决定注入方向。
 * <p>
 * 拦截规则：
 * <ol>
 *   <li>extraWheel -> 主BE（内部连接）-> 注入 targetSpeed，符号取自原版返回值（=extraWheel 速度×1）</li>
 *   <li>主BE -> extraWheel（内部连接）-> 若 extraWheel 已有转速则注入其当前速度防覆写，
 *       若无转速则放行（传动杆输入时正常 1:1 传播）</li>
 *   <li>齿轮面 ↔ 外部齿轮 -> 放行，Create 原版齿轮逻辑处理（pass-through）</li>
 *   <li>主BE（传动杆面）↔ 外部非源邻居 -> 注入 targetSpeed，符号取自原版返回值
 *       （=邻居速度×getRotationSpeedModifier，含齿轮箱反转修饰符）</li>
 *   <li>主BE -> 源（generator）-> 返回源当前速度（不注入 targetSpeed、不返回 0）。
 *       让 propagateNewSource 看到 |newSpeed - speedOfNeighbour|≈0 而跳过 setSource(主BE)，
 *       避免源 hasSource=主BE 后调整方向时 applyNewSpeed(GeneratingKineticBlockEntity)自毁，也避免拆除源后主BE不停。</li>
 *   <li>主BE -> source（驱动主BE的邻居，反向传播）-> 返回 source 当前速度，
 *       避免循环检测因 targetSpeed > source 速度且同网络而破坏主BE。</li>
 *   <li>源 -> 主BE 且主BE当前速度方向与源新方向冲突 -> 返回主BE当前方向，
 *       避免 incompatible 破坏源（主BE方向随后由源 detach/reattach 同步）。</li>
 *   <li>不涉及我们的方块 -> 不拦截</li>
 * </ol>
 * <p>
 * 信号 0 时 targetSpeed=0，注入返回 0--传动杆与齿轮环断开，
 * 齿轮环可独立作为普通齿轮传递应力--类似离合器效果。
 * <p>
 * 视觉错位修复不在此处：主BE.speed 由网络逻辑持有（=targetSpeed，输出转速），传动杆 visual
 * 若直接读主BE.speed 会显示输出转速而非输入转速。显示解耦由 ConvenientAnalogTransmissionVisual
 * 在客户端按 source 归属计算"显示转速"完成，网络逻辑完全不动。
 */
@Mixin(RotationPropagator.class)
public class RotationPropagatorMixin {

    @Inject(
            method = "getConveyedSpeed",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void aeronautics_gravity$getConveyedSpeed(
            KineticBlockEntity fromBE, KineticBlockEntity toBE,
            CallbackInfoReturnable<Float> cir) {

        // 原版已计算的 conveyed speed = from.getTheoreticalSpeed() * getRotationSpeedModifier(from, to)
        // 其符号已包含齿轮箱 getAxisModifier 的反转修饰符，用它决定注入方向
        float original = cir.getReturnValue();

        // 规则 1：extraWheel -> 主BE（内部连接，齿轮->传动杆转速转换）
        if (isOurCogwheel(fromBE) && toBE instanceof ConvenientAnalogTransmissionBlockEntity mainBE) {
            injectTableSpeed(mainBE, original, cir);
            return;
        }

        // 规则 2：主BE -> extraWheel（内部连接，反向传播保护）
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

        // 规则 4 ~ 8：主BE 的传动杆面边界连接
        ConvenientAnalogTransmissionBlockEntity fromOurs = resolveOurBlockEntity(fromBE);
        ConvenientAnalogTransmissionBlockEntity toOurs = resolveOurBlockEntity(toBE);

        // 不涉及我们的方块 -> 不拦截
        if (fromOurs == null && toOurs == null) return;

        // 两个都是我们的方块（两个传动器传动杆面直连等边缘情况）-> 不拦截
        if (fromOurs != null && toOurs != null) return;

        boolean neighborIsSource = (toOurs != null);
        ConvenientAnalogTransmissionBlockEntity ourBE = neighborIsSource ? toOurs : fromOurs;
        KineticBlockEntity neighbor = neighborIsSource ? fromBE : toBE;

        // 规则 5：主BE -> 源（generator）。返回源当前速度，避免源被伪源 overpower。
        if (!neighborIsSource && neighbor.isSource()) {
            cir.setReturnValue(neighbor.getTheoreticalSpeed());
            return;
        }

        // 规则 6：主BE -> source（驱动主BE的邻居，反向传播）。返回 source 当前速度，
        // 避免循环检测因 targetSpeed > source 速度且同网络而破坏主BE。
        if (!neighborIsSource && ourBE.isDrivenBy(neighbor)) {
            float targetSpeed = ourBE.getTargetSpeed();
            if (targetSpeed == 0f || original == 0f) {
                cir.setReturnValue(0f);
                return;
            }
            cir.setReturnValue(Math.copySign(neighbor.getTheoreticalSpeed(), original));
            return;
        }

        // 规则 7：源 -> 主BE，且主BE当前速度方向与源新方向冲突。返回主BE当前方向，
        // 避免 incompatible 破坏源（主BE方向随后由源 detach/reattach 同步）。
        if (neighborIsSource && neighbor.isSource()) {
            float mainSpeed = ourBE.getTheoreticalSpeed();
            if (mainSpeed != 0f && Math.signum(mainSpeed) != Math.signum(original)) {
                float targetSpeed = ourBE.getTargetSpeed();
                if (targetSpeed == 0f) {
                    cir.setReturnValue(0f);
                    return;
                }
                cir.setReturnValue(Math.copySign(targetSpeed, mainSpeed));
                return;
            }
        }

        // 规则 4：注入查表 RPM，符号取自原版返回值（含齿轮箱反转修饰符）
        injectTableSpeed(ourBE, original, cir);
    }

    /** 注入查表 RPM，保持原版返回值的方向符号。targetSpeed=0 或原值为 0 时返回 0（断开）。 */
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
