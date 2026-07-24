package icu.dreamripples.aeronautics_gravity.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlockEntity;
import icu.dreamripples.aeronautics_gravity.block.ConvenientAnalogTransmissionBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 RotationPropagator.getConveyedSpeed 的 RETURN，为更方便的模拟传动器注入查表固定 RPM。
 * <p>
 * 关键：在 RETURN 处拦截而非 HEAD，让原版 getRotationSpeedModifier 先正常计算
 * （其中包含齿轮箱 getAxisModifier 的反转修饰符），再用原版返回值的符号决定注入方向。
 * 这样反转齿轮箱等反转连接的方向能被正确保留，避免与 Create 传播预期冲突导致方块被破坏。
 * <p>
 * 拦截规则：
 * <ol>
 *   <li>extraWheel -> 主BE（内部连接）-> 注入 targetSpeed，符号取自原版返回值（=extraWheel 速度×1）</li>
 *   <li>主BE -> extraWheel（内部连接）-> 若 extraWheel 已有转速则注入其当前速度防覆写，
 *       若无转速则放行（传动杆输入时正常 1:1 传播）</li>
 *   <li>齿轮面 ↔ 外部齿轮 -> 放行，Create 原版齿轮逻辑处理（pass-through）</li>
 *   <li>主BE（传动杆面）↔ 外部 -> 注入 targetSpeed，符号取自原版返回值
 *       （=邻居速度×getRotationSpeedModifier，含齿轮箱反转修饰符）</li>
 *   <li>主BE -> 邻居，且邻居是主BE的 source（反向传播到驱动者）-> 返回 source 当前速度，
 *       不注入 targetSpeed。主BE以固定 targetSpeed 转动，但不应反过来 overpower 驱动它的 source，
 *       否则 propagateNewSource 的循环检测会因 targetSpeed > source 速度且同网络而破坏主BE。</li>
 *   <li>不涉及我们的方块 -> 不拦截</li>
 * </ol>
 * <p>
 * 信号 0 时 targetSpeed=0，注入返回 0--传动杆与齿轮环断开，
 * 齿轮环可独立作为普通齿轮传递应力--类似离合器效果。
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

        // 临时调试：涉及主BE+源的边界连接
        if ((fromBE instanceof ConvenientAnalogTransmissionBlockEntity || toBE instanceof ConvenientAnalogTransmissionBlockEntity)
                && (fromBE.isSource() || toBE.isSource())) {
            System.err.println("[AG-DEBUG] getConveyedSpeed from=" + fromBE.getClass().getSimpleName()
                    + "(" + fromBE.getTheoreticalSpeed() + ",src=" + fromBE.isSource() + ")"
                    + " to=" + toBE.getClass().getSimpleName()
                    + "(" + toBE.getTheoreticalSpeed() + ",src=" + toBE.isSource() + ")"
                    + " original=" + original);
        }

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

        // 我们 -> 邻居，但邻居是源（generator）：源速度固定，主BE不应 overpower 源。
        // 返回源当前速度，让 propagateNewSource 认为主BE与源速度一致（|newSpeed - speedOfNeighbour|≈0），
        // 跳过 setSource(主BE)，避免源 hasSource=主BE 后调整方向时 applyNewSpeed 第147行自毁。
        // （此前返回 0 会导致 newSpeed=0 != 源速度，触发 setSource(主BE)+setSpeed(0)，源被伪源 overpower。）
        if (!neighborIsSource && neighbor.isSource()) {
            cir.setReturnValue(neighbor.getTheoreticalSpeed());
            return;
        }

        // 我们 -> 邻居，且邻居是我们的 source（反向传播到驱动者）：
        // 主BE以固定 targetSpeed 转动，但不应反过来 overpower 驱动它的 source。
        // 返回 source 当前速度（带原版方向符号），让 propagateNewSource 的循环检测认为
        // 主BE与 source 速度一致，避免主BE被误判为"overpower 自己网络"而破坏。
        if (!neighborIsSource && ourBE.isDrivenBy(neighbor)) {
            float targetSpeed = ourBE.getTargetSpeed();
            if (targetSpeed == 0f || original == 0f) {
                cir.setReturnValue(0f);
                return;
            }
            cir.setReturnValue(Math.copySign(neighbor.getTheoreticalSpeed(), original));
            return;
        }

        // 源(generator) -> 主BE（应力源直接接入传动杆面）：
        // 应力源改变方向时，主BE仍保持旧方向 targetSpeed，propagateNewSource 会因
        // sign(newSpeed) != sign(主BE速度) 判定 incompatible 并 destroyBlock(源位置)，
        // 导致应力源崩坏。若主BE已有速度且方向与源新方向冲突，返回主BE当前方向，
        // 让 incompatible 不触发；主BE方向随后由源 detach/reattach 同步。
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

        // 注入查表 RPM，符号取自原版返回值（含齿轮箱反转修饰符）
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

    // 临时调试：记录 propagateNewSource 中每次 destroyBlock 调用
    @WrapOperation(
            method = "propagateNewSource",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z")
    )
    private static boolean ag$debugDestroyBlock(Level world, BlockPos pos, boolean drop,
                                                Operation<Boolean> original,
                                                @Local(argsOnly = true) KineticBlockEntity currentTE) {
        System.err.println("[AG-DEBUG] destroyBlock pos=" + pos + " drop=" + drop
                + " | currentTE=" + currentTE.getClass().getSimpleName()
                + " speed=" + currentTE.getTheoreticalSpeed()
                + " isSource=" + currentTE.isSource()
                + " hasSource=" + currentTE.hasSource()
                + " hasNetwork=" + currentTE.hasNetwork());
        new Throwable("[AG-DEBUG] destroyBlock trace").printStackTrace();
        return original.call(world, pos, drop);
    }
}
