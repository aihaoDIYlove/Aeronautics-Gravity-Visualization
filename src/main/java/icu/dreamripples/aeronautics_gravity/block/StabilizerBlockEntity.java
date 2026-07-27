package icu.dreamripples.aeronautics_gravity.block;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

/**
 * 自稳定方块 BE - PD 混合 + 倾斜速度自适应,维持载具水平且不干预 yaw 转向。
 *
 * **P 项(恢复力,调 mass/lift tier)**:每游戏 tick 在 tick() 里算。
 *   - 姿态:世界 DOWN 经 orientation.transformInverse 到本地 = ld。
 *     pitch = atan2(ld.z, -ld.y);roll = atan2(ld.x, -ld.y);tilt = sqrt(pitch²+roll²)。
 *   - 死区(总倾斜角)由右键 ScrollValueBehaviour 调 0..30°。tilt < deadband -> tier (1,1) 休眠。
 *   - 方位 r = (getBlockPos + 0.5) - 质心。|r| < 0.5 -> 无力臂,休眠。
 *   - 误差 error = rz*pitch + rx*roll(力臂加权倾斜投影)。projection = |error|/|r|,projDeg = toDeg(projection)。
 *   - **自适应满档角**(按倾斜速度):tiltSpeed = sqrt(ωx²+ωz²)(本地 pitch/roll 角速度,从 D 项缓存读)。
 *     maxAngleEff = MAX_ANGLE_BASE - BETA * tiltSpeed,clamp 到 [MIN_MAX_ANGLE, MAX_ANGLE_BASE]。
 *     慢速(tiltSpeed≈0):30° 满档(温和,防震);快速(tiltSpeed=2):10° 满档(早响应,抢在 45° 物理失效前)。
 *   - tier = clamp(1 + round(15 * projDeg / maxAngleEff), 1, 16)。无固定 KP,满档角随速度自适应。
 *   - 互斥:error > 0(我在低端)-> lift 模式 (1, tier);error < 0(我在高端)-> mass 模式 (tier, 1)。
 *   - tier 变化才 setBlock。
 *
 * **D 项(阻尼,直接施角冲量)**:每物理 tick 在 sable$physicsTick 里算(BlockEntitySubLevelActor)。
 *   - 读载具全局角速度(handle.getAngularVelocity),转本地系,存 angVelLocalCache 供 P 项读。
 *   - **自适应 KD**:tiltSpeed = sqrt(ωx²+ωz²)。KD_eff = KD_BASE * (1 + ALPHA * tiltSpeed)。
 *     慢速:KD=KD_BASE(温和,不干扰转向);快速:KD 翻倍(强阻尼,防过冲到 45°+)。
 *   - 阻尼 pitch/roll 轴(本地 x/z),**不阻尼 yaw(本地 y)**保转向自由。
 *   - 阻尼力矩 = -KD_eff * (ωx, 0, ωz),角冲量 = 力矩 * timeStep,handle.applyAngularImpulse(本地)。
 *   - D 是外力矩(非角动量交换),不储存角动量,无陀螺进动,不影响 yaw。
 *
 * **自适应的动机**:固定 KP 下,大(早满档)则震荡,小(晚满档)则 45°+ 才触发(物理已失效)。
 *   按倾斜速度调度增益:快→激进(早满档+强阻尼,抢救),慢→温和(防震)。这是增益调度,比真自适应控制简单稳定。
 *
 * **红石仅使能**:signal=0 时 P 项归 (1,1)、D 项不施力。
 * KD_BASE/ALPHA/MAX_ANGLE_BASE/BETA 是代码常量(开发者实测调);死区是玩家右键调。
 */
public class StabilizerBlockEntity extends SmartBlockEntity
        implements IHaveGoggleInformation, BlockEntitySubLevelActor {

    private static final int MIN_DEADBAND = 0;
    private static final int MAX_DEADBAND = 30;
    // P 项自适应满档角:慢速 MAX_ANGLE_BASE 满档,快速按 BETA 递减(早满档抢救),下限 MIN_MAX_ANGLE。
    private static final double MAX_ANGLE_BASE = 30.0;  // 慢速满档角 [度]
    private static final double BETA = 10.0;            // 满档角随角速度递减 [度/(rad/s)]
    private static final double MIN_MAX_ANGLE = 5.0;    // 满档角下限 [度]
    // D 项自适应阻尼:KD_eff = KD_BASE * (1 + ALPHA * tiltSpeed)。
    private static final double KD_BASE = 0.5;          // 基础 D 增益 [N·m·s]
    private static final double ALPHA = 0.5;            // D 自适应系数 [s/rad]

    private ScrollValueBehaviour deadbandBehaviour;
    // sable$physicsTick 写,tick 读(最新物理 tick 的本地角速度)。初始 0。
    private final Vector3d angVelLocalCache = new Vector3d();

    public StabilizerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        deadbandBehaviour = new DeadbandScrollValueBehaviour(
                Component.translatable("block.aeronautics_gravity.stabilizer.deadband"),
                this,
                new DeadbandValueBoxTransform()
        ).between(MIN_DEADBAND, MAX_DEADBAND);
        deadbandBehaviour.value = 3;
        behaviours.add(deadbandBehaviour);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        int signal = level.getBestNeighborSignal(worldPosition);
        if (signal == 0) {
            setTiers(1, 1);
            return;
        }

        SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) {
            setTiers(1, 1);
            return;
        }

        MassData massData = serverSubLevel.getMassTracker();
        Vector3dc com = massData.getCenterOfMass();
        if (com == null) {
            setTiers(1, 1);
            return;
        }

        Pose3dc pose = serverSubLevel.logicalPose();
        Vector3d ld = new Vector3d(0, -1, 0);
        pose.orientation().transformInverse(ld);

        double pitch = Math.atan2(ld.z(), -ld.y());
        double roll = Math.atan2(ld.x(), -ld.y());
        double tilt = Math.sqrt(pitch * pitch + roll * roll);

        int deadbandDeg = deadbandBehaviour.getValue();
        double deadbandRad = Math.toRadians(deadbandDeg);
        if (tilt < deadbandRad) {
            setTiers(1, 1);
            return;
        }

        double rx = worldPosition.getX() + 0.5 - com.x();
        double rz = worldPosition.getZ() + 0.5 - com.z();
        double rMag = Math.sqrt(rx * rx + rz * rz);
        if (rMag < 0.5) {
            setTiers(1, 1);
            return;
        }

        double error = rz * pitch + rx * roll;
        double projection = Math.abs(error) / rMag;
        double projDeg = Math.toDegrees(projection);

        // 自适应满档角:倾斜速度大 -> 满档角小 -> 早满档(抢在 45° 物理失效前)
        double tiltSpeed = Math.sqrt(angVelLocalCache.x() * angVelLocalCache.x()
                + angVelLocalCache.z() * angVelLocalCache.z());
        double maxAngleEff = MAX_ANGLE_BASE - BETA * tiltSpeed;
        if (maxAngleEff < MIN_MAX_ANGLE) maxAngleEff = MIN_MAX_ANGLE;

        double frac = projDeg / maxAngleEff;
        if (frac < 0) frac = 0;
        else if (frac > 1) frac = 1;
        int tier = 1 + (int) Math.round(15 * frac);
        if (tier < 1) tier = 1;
        else if (tier > 16) tier = 16;

        if (error > 0) {
            setTiers(1, tier);  // lift 模式(我在低端,需向上力)
        } else {
            setTiers(tier, 1);  // mass 模式(我在高端,需向下力)
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (level == null || level.isClientSide) return;
        if (level.getBestNeighborSignal(worldPosition) == 0) return;  // 停用

        Pose3dc pose = subLevel.logicalPose();
        Vector3d angVelGlobal = handle.getAngularVelocity(new Vector3d());
        // 全局角速度转本地系,按轴分离 pitch/roll(阻尼)与 yaw(不阻尼);存缓存供 P 项读
        Vector3d angVelLocal = pose.orientation().transformInverse(angVelGlobal, new Vector3d());
        angVelLocalCache.set(angVelLocal);

        // 自适应 KD:倾斜速度大 -> 强阻尼(防过冲到 45°+)
        double tiltSpeed = Math.sqrt(angVelLocal.x() * angVelLocal.x()
                + angVelLocal.z() * angVelLocal.z());
        double kdEff = KD_BASE * (1.0 + ALPHA * tiltSpeed);

        // D 项:阻尼 pitch/roll(本地 x/z),不阻尼 yaw(本地 y)以保转向自由
        Vector3d dampTorqueLocal = new Vector3d(-kdEff * angVelLocal.x(), 0, -kdEff * angVelLocal.z());
        // 角冲量 = 力矩 * timeStep;applyAngularImpulse 接受本地系角冲量
        handle.applyAngularImpulse(dampTorqueLocal.mul(timeStep));
    }

    private void setTiers(int massTier, int liftTier) {
        BlockState state = getBlockState();
        int curMass = state.getValue(StabilizerBlock.MASS_TIER);
        int curLift = state.getValue(StabilizerBlock.LIFT_TIER);
        if (curMass == massTier && curLift == liftTier) return;
        level.setBlockAndUpdate(worldPosition, state
                .setValue(StabilizerBlock.MASS_TIER, massTier)
                .setValue(StabilizerBlock.LIFT_TIER, liftTier));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        BlockState state = getBlockState();
        int massTier = state.getValue(StabilizerBlock.MASS_TIER);
        int liftTier = state.getValue(StabilizerBlock.LIFT_TIER);

        CreateLang.builder()
                .add(Component.translatable("block.aeronautics_gravity.stabilizer")
                        .withStyle(ChatFormatting.WHITE))
                .forGoggles(tooltip);

        String modeKey;
        int currentTier;
        ChatFormatting modeColor;
        if (massTier > 1) {
            modeKey = "tooltip.aeronautics_gravity.stabilizer.mass_mode";
            currentTier = massTier;
            modeColor = ChatFormatting.RED;
        } else if (liftTier > 1) {
            modeKey = "tooltip.aeronautics_gravity.stabilizer.lift_mode";
            currentTier = liftTier;
            modeColor = ChatFormatting.AQUA;
        } else {
            modeKey = "tooltip.aeronautics_gravity.stabilizer.idle";
            currentTier = 0;
            modeColor = ChatFormatting.DARK_GRAY;
        }
        CreateLang.builder()
                .add(Component.translatable("tooltip.aeronautics_gravity.stabilizer.mode")
                        .withStyle(ChatFormatting.GRAY))
                .add(Component.literal(": ")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .add(Component.translatable(modeKey)
                        .withStyle(modeColor))
                .forGoggles(tooltip, 1);

        if (currentTier > 0) {
            CreateLang.builder()
                    .add(Component.translatable("tooltip.aeronautics_gravity.stabilizer.output")
                            .withStyle(ChatFormatting.GRAY))
                    .forGoggles(tooltip, 1);
            CreateLang.number(currentTier)
                    .add(CreateLang.text(" kpg"))
                    .style(ChatFormatting.GOLD)
                    .forGoggles(tooltip, 2);
        }

        CreateLang.builder()
                .add(Component.translatable("block.aeronautics_gravity.stabilizer.deadband")
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);
        CreateLang.number(deadbandBehaviour.getValue())
                .add(CreateLang.text("°"))
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 2);

        return true;
    }

    /**
     * ScrollValueBehaviour 子类 - 死区角度 0..30°。tilt < deadband 时休眠(防小扰动)。
     */
    private static class DeadbandScrollValueBehaviour extends ScrollValueBehaviour {
        public DeadbandScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
            withFormatter(i -> i + "°");
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, MAX_DEADBAND, 1,
                    ImmutableList.of(Component.translatable("aeronautics_gravity.unit.deadband_deg")),
                    new ValueSettingsFormatter(this::formatSettings));
        }

        public MutableComponent formatSettings(ValueSettings settings) {
            int value = Math.max(MIN_DEADBAND, Math.min(MAX_DEADBAND, settings.value()));
            return Component.literal(value + "°");
        }
    }

    /**
     * ValueBoxTransform.Sided 子类 - 所有面都能弹板,voxel 中心。
     */
    private static class DeadbandValueBoxTransform extends ValueBoxTransform.Sided {
        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return true;
        }

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8, 15.5);
        }
    }
}
