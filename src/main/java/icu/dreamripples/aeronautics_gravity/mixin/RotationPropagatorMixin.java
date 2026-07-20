package icu.dreamripples.aeronautics_gravity.mixin;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlockEntity;
import icu.dreamripples.aeronautics_gravity.block.ConvenientAnalogTransmissionBlockEntity;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 RotationPropagator.getConveyedSpeed，为更方便的模拟传动器注入查表固定 RPM。
 * <p>
 * 拦截规则：
 * - 不涉及我们的方块 -> 不拦截，走原版逻辑
 * - 内部连接（主方块 <-> extraWheel）-> 不拦截，仅打破双向自源循环
 * - 边界连接（我们的方块 <-> 外部邻居）-> 注入 targetSpeed
 *   - 邻居 -> 我们：用邻居方向（邻居是 source）
 *   - 我们 -> 邻居：用我们自己的方向（邻居是接收方，初始可能为 0）
 *   - 当我们 -> 邻居且邻居是源（generator）时返回 0：源速度固定，不能被覆盖
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

        ConvenientAnalogTransmissionBlockEntity fromOurs = resolveOurBlockEntity(fromBE);
        ConvenientAnalogTransmissionBlockEntity toOurs = resolveOurBlockEntity(toBE);

        // 不涉及我们的方块 -> 不拦截
        if (fromOurs == null && toOurs == null) return;

        // 内部连接（extraWheel <-> 主方块）交由原版 propagateRotationTo 处理
        // 仅打破双向自源循环（加载后可能出现的临时状态）
        if (fromOurs != null && toOurs != null) {
            if (isSelfSourceLoop(fromBE) && isSelfSourceLoop(toBE)) {
                cir.setReturnValue(0f);
            }
            return;
        }

        boolean neighborIsSource = (toOurs != null);
        ConvenientAnalogTransmissionBlockEntity ourBE = neighborIsSource ? toOurs : fromOurs;
        KineticBlockEntity neighbor = neighborIsSource ? fromBE : toBE;

        // 我们 -> 邻居，但邻居是源（generator）：源速度固定，不能被覆盖
        if (!neighborIsSource && neighbor.isSource()) {
            cir.setReturnValue(0f);
            return;
        }

        float targetSpeed = ourBE.getTargetSpeed();
        if (targetSpeed == 0f) {
            cir.setReturnValue(0f);
            return;
        }

        // 参考速度：
        // - 邻居 -> 我们：用邻居速度（邻居是 source，速度应 > 0）
        // - 我们 -> 邻居：用我们自己的速度（邻居是接收方，初始可能为 0）
        float referenceSpeed = neighborIsSource
                ? neighbor.getTheoreticalSpeed()
                : ourBE.getTheoreticalSpeed();
        if (referenceSpeed == 0f) {
            cir.setReturnValue(0f);
            return;
        }

        // 注入固定 RPM，保持参考方向
        cir.setReturnValue(Math.copySign(targetSpeed, referenceSpeed));
    }

    private static boolean isSelfSourceLoop(KineticBlockEntity be) {
        return be.hasSource() && be.source != null && isSameCoord(be.source, be.getBlockPos());
    }

    private static boolean isSameCoord(BlockPos a, BlockPos b) {
        return a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ();
    }

    private static ConvenientAnalogTransmissionBlockEntity resolveOurBlockEntity(KineticBlockEntity be) {
        if (be instanceof ConvenientAnalogTransmissionBlockEntity direct) {
            return direct;
        }
        if (be instanceof AnalogTransmissionBlockEntity.AnalogTransmissionCogwheel cog) {
            KineticBlockEntity parent = cog.getParentBlockEntity();
            if (parent instanceof ConvenientAnalogTransmissionBlockEntity direct) {
                return direct;
            }
        }
        return null;
    }
}
