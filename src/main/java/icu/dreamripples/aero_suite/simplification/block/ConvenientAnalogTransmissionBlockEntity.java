package icu.dreamripples.aero_suite.simplification.block;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import icu.dreamripples.aero_suite.mixin.simplification.AnalogTransmissionBlockEntityAccessor;
import icu.dreamripples.aero_suite.mixin.simplification.RotationPropagatorMixin;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlock;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlockEntity;
import dev.simulated_team.simulated.mixin_interface.extra_kinetics.KineticBlockEntityExtension;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 更方便的模拟传动器 - 查表输出固定 RPM，无视输入转速。
 * 红石信号 0..15 对应 0 32 48 64 80 96 112 128 144 160 176 192 208 224 240 256 RPM。
 * <p>
 * 实现方式：
 * - 继承原版 AnalogTransmissionBlockEntity，复用其 tick() 中信号变化检测 + detach/attach 流程
 * - 重写 propagateRotationTo，内部连接（主BE <-> extraWheel）保持 1:1 传动
 * - 齿轮面（extraWheel）↔ 外部齿轮：Mixin 放行，由 Create 原版齿轮逻辑处理（pass-through）
 * - extraWheel → 主BE（内部连接）：Mixin 注入查表 RPM，实现齿轮速度→传动杆输出转速的转换
 * - 传动杆面 ↔ 外部传动杆/齿轮：Mixin 注入查表 RPM
 * <p>
 * 信号 0 时 getTargetSpeed() 返回 0，Mixin 注入 0——传动杆与齿轮环断开，
 * 齿轮环可独立作为普通齿轮传递应力——类似离合器效果。
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
        return RPM_TABLE[signal];
    }

    /**
     * 护目镜信息: 仅显示当前输出转速(查表 RPM, 由红石信号决定)。
     * 不调 super: KineticBlockEntity.addToGoggleTooltip 在 calculateStressApplied()==0 时返回 false
     * 且不加内容(本 BE 无 Create stress 配置), 调了也是空操作。
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.builder()
                .add(Component.translatable("block.simplification_related.convenient_analog_transmission")
                        .withStyle(ChatFormatting.WHITE))
                .forGoggles(tooltip);

        CreateLang.builder()
                .add(Component.translatable("tooltip.simplification_related.current_output_speed")
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);
        CreateLang.number(getTargetSpeed())
                .translate("generic.unit.rpm")
                .style(ChatFormatting.GOLD)
                .forGoggles(tooltip, 2);

        return true;
    }

    @Override
    public void tick() {
        if (getLevel() == null) return;

        final int bestNeighborSignal = getLevel().getBestNeighborSignal(getBlockPos());

        if (!getLevel().isClientSide) {
            // 复用父类持久化的 signal 字段检测变化（signal 从 NBT 恢复，重进后与 bestNeighborSignal 一致，
            // 避免自维护 lastSignal 重进后重置为 0 误判信号变化，触发不必要的 detach/reattach）
            int currentSignal = ((AnalogTransmissionBlockEntityAccessor) this).aeronautics_gravity$getSignal();
            if (bestNeighborSignal != currentSignal) {
                KineticBlockEntity extraWheel = getExtraKinetics();

                // 父类原有的 detach/reattach 逻辑
                this.detachKinetics();
                extraWheel.detachKinetics();
                this.removeSource();
                extraWheel.removeSource();

                // 同步父类 signal 字段，防止 super.tick() 重复进入信号变化分支
                // （否则 super.tick 会再次 attachKinetics -> propagateNewSource，触发第二次
                // destroyBlock，且 setBlockAndUpdate 会用 BlockEntity 内部 state 重新放回方块，掉落第二个物品）
                ((AnalogTransmissionBlockEntityAccessor) this).aeronautics_gravity$setSignal(bestNeighborSignal);
                getLevel().setBlockAndUpdate(getBlockPos(),
                        getBlockState().setValue(AnalogTransmissionBlock.POWERED, bestNeighborSignal > 0));

                // 关键修复：在 re-attach 前为新网络分配 ID，
                // 防止 propagateNewSource 的循环检测因 hasNetwork()=false 而误杀方块
                this.getOrCreateNetwork();
                extraWheel.getOrCreateNetwork();

                if (((KineticBlockEntityExtension) this).simulated$getConnectedToExtraKinetics()) {
                    this.attachKinetics();
                    extraWheel.attachKinetics();
                } else {
                    extraWheel.attachKinetics();
                    this.attachKinetics();
                }
            }
        }

        // 不调用 getExtraKinetics().tick()：super.tick()（AnalogTransmissionBlockEntity.tick）
        // 在信号变化分支已被同步跳过后，仍会执行 extraWheel.tick() + KineticBlockEntity.tick
        super.tick();
    }

    /** 返回主BE是否被指定邻居驱动（用于 Mixin 判断反向传播到 source，避免 overpower source）。 */
    public boolean isDrivenBy(KineticBlockEntity neighbor) {
        return hasSource() && source.equals(neighbor.getBlockPos());
    }

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState state, BlockState state2,
                                     BlockPos pos, boolean b1, boolean b2) {
        // 内部连接（主方块 <-> extraWheel）保持 1:1 传动
        // 齿轮面→传动杆面 的转速转换由 RotationPropagatorMixin 在内部连接上注入查表 RPM 完成
        if (target == this) return 1f;
        if (target instanceof AnalogTransmissionBlockEntity.AnalogTransmissionCogwheel cog
                && cog.getParentBlockEntity() == this) {
            return 1f;
        }
        // 边界连接由 RotationPropagatorMixin 注入固定 RPM，这里返回 0 让原版不处理
        return 0f;
    }
}
