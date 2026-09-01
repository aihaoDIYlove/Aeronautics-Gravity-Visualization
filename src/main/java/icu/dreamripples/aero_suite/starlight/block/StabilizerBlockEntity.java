package icu.dreamripples.aero_suite.starlight.block;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
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
 *   - 死区(总倾斜角)由侧面 4 面 ScrollValueBehaviour 调 0..30°。tilt < deadband -> tier (1,1) 休眠。
 *   - **模式判据 = 世界竖直高度差**(h)(不再用水平投影 error!):
 *     h = rel·ld,rel = 方块中心-质心(SubLevel 本地系),ld = 世界 DOWN 的本地表示,
 *     化简后等价于"质心在世界系下高出我的格数"(本地系点积即可,无需真变换到世界系)。
 *     h > 0 -> 我在低端 -> lift;h < 0 -> 高端 -> mass。
 *     旧水平投影 error=rz*pitch+rx*roll 的致命缺陷:质心随自身档位切换实时移动,Sable 重算后
 *     error 过零换向 -> 满幅 chattering 极限环(翻倒高频闪烁根因)。竖直判据下自身配重强化当前
 *     决策方向(收敛极性),且绕开 atan2 在 ±90° 翻倒时的退化。
 *   - **Schmitt 滞回 + 换向冷却 + 档位斜坡**(多块共存防震荡三件套):
 *     modeLatch 锁存当前模式,换向需 heightDiff 越过对面 ±MODE_HYST 阈值;
 *     切换后 SWITCH_COOLDOWN tick 内锁死方向(给其余稳定块引发的质心移动时间沉淀,
 *     否则多块互相拉扯成极限环);setTiers 斜坡限速每 tick 最多 MAX_TIER_STEP 级
 *     (瞬时满幅跳变是多块震荡的放大器)。任何休眠条件(红石停用/非载具/无质心/死区)
 *     归零锁存与冷却,唤醒后首拍按符号直接定。
 *   - 力臂 |质心-方块中心|(3D)< MIN_LEVER_DIST -> 差重无力矩,休眠(锁存保留)。
 *   - **自适应满档角**(按倾斜速度):tiltSpeed = sqrt(ωx²+ωz²)(本地 pitch/roll 角速度,从 D 项缓存读)。
 *     maxAngleEff = MAX_ANGLE_BASE - BETA * tiltSpeed,clamp 到 [MIN_MAX_ANGLE, MAX_ANGLE_BASE]。
 *     慢速(tiltSpeed≈0):30° 满档(温和,防震);快速(tiltSpeed=2):10° 满档(早响应,抢在 45° 物理失效前)。
 *   - **过零衰减**:frac 额外乘 min(1, |heightDiff|/TIER_FADE_BLOCKS),接近水平位输出收束到零——
 *     否则饱和区满档力矩每半周冲过平衡点泵能一次,形成大角度慢摆极限环(改前 >30° 停不下来的根因)。
 *     换向后需重新拉开高度差才恢复出力,顺带软启动防二次泵能。
 *   - tier = clamp(1 + round(15 * frac), 1, 16);setTiers 斜坡逼近目标。
 *   - 互斥输出:低端 -> lift 模式 (1, tier);高端 -> mass 模式 (tier, 1)。tier 变化才 setBlock。
 *
 * **D 项(阻尼,直接施角冲量)**:每物理 tick 在 sable$physicsTick 里算(BlockEntitySubLevelActor)。
 *   - 读载具全局角速度(handle.getAngularVelocity),转本地系,存 angVelLocalCache 供 P 项读。
 *   - **自适应 KD**:tiltSpeed = sqrt(ωx²+ωz²)。KD_eff = KD_BASE * (1 + ALPHA * tiltSpeed)。
 *     慢速:KD=KD_BASE(温和,不干扰转向);快速:KD 翻倍(强阻尼,防过冲到 45°+)。
 *   - 阻尼 pitch/roll 轴(本地 x/z),**不阻尼 yaw(本地 y)**保转向自由。
 *   - 阻尼力矩 = -KD_eff * (ωx, 0, ωz),KD_eff 还随倾角线性增强(30°x2/60°x3,慢速大幅摆动耗散不足补强),
 *     角冲量 = 力矩 * timeStep,handle.applyAngularImpulse(本地)。
 *   - D 是外力矩(非角动量交换),不储存角动量,无陀螺进动,不影响 yaw。
 *
 * **自适应的动机**:固定 KP 下,大(早满档)则震荡,小(晚满档)则 45°+ 才触发(物理已失效)。
 *   按倾斜速度调度增益:快->激进(早满档+强阻尼,抢救),慢->温和(防震)。这是增益调度,比真自适应控制简单稳定。
 *
 * **红石模式(上下两面 ScrollOptionBehaviour 切换)**:ACTIVE_WHEN_OFF(默认,无红石时工作) /
 *   ACTIVE_WHEN_ON(有红石时工作)。非工作状态时 P 项归 (1,1)、D 项不施力。
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
    private static final double KD_BASE = 0.6;          // 基础 D 增益 [N·m·s]
    private static final double ALPHA = 0.6;            // D 自适应系数 [s/rad]
    // 模式判据滞回 [格]:低/高端判定切换需要高度差穿越对面阈值,防零点噪声换向。
    private static final double MODE_HYST = 0.25;
    // 无力臂休眠距离 [格]:离质心过近时差重几乎无力矩,直接休眠。
    private static final double MIN_LEVER_DIST = 0.75;
    // 换向冷却 [tick]:模式切换后锁死这么久,让其余稳定方块引发的质心移动有时间沉淀,
    // 防止多块互相拉扯形成极限环(单靠滞回会把逐帧抖变成大周期固执摆)。
    private static final int SWITCH_COOLDOWN = 12;
    // 档位斜坡:每 tick 每维最多变化量。瞬时 ±15 kpg 的满幅跳变是多块震荡的放大器,减速逼近目标。
    private static final int MAX_TIER_STEP = 1;
    // 过零衰减 [格]:档位按 |heightDiff| 线性收束到 0,越过此距离才允许满档。
    // 防"满档力矩冲过水平位"每半周泵能 -> 大角度等幅摆(缓慢摆动时尤其致命:D 弱,P 恒满)。
    private static final double TIER_FADE_BLOCKS = 1.8;
    // 倾斜增强阻尼:kdEff 随倾角线性放大,30° 时 x2、60° 时 x3(慢速大幅摆动的额外耗散)。
    private static final double DAMP_TILT_GAIN = 2.0;

    // 模式锁存(服务端瞬态,不持久化):0=未知(下次直接按符号判定)/1=低端(lift)/2=高端(mass)。
    // 任何休眠条件触发即归零,唤醒后首拍重新定方向。switchCooldown>0 时禁止换向(档位仍可调)。
    private byte modeLatch = 0;
    private int switchCooldown = 0;

    private ScrollValueBehaviour deadbandBehaviour;
    // 红石控制模式(上下两面切换)。value=0 -> ACTIVE_WHEN_OFF(默认,无红石时工作)。
    // 用 RedstoneModeBehaviour(独立 BehaviourType)而非裸 ScrollOptionBehaviour:后者继承
    // ScrollValueBehaviour.TYPE,会与 deadbandBehaviour 在 SmartBlockEntity 的 behaviours map 里冲突覆盖。
    private RedstoneModeBehaviour redstoneModeBehaviour;
    // sable$physicsTick 写,tick 读(最新物理 tick 的本地角速度)。初始 0。
    // 线程安全说明: Sable 物理同步跑在服务端主线程(SubLevelPhysicsSystem.tick -> prePhysicsTick
    // -> sable$physicsTick,全链路无 Thread/Executor,_research 源码已核实),故无需 volatile/快照。
    private final Vector3d angVelLocalCache = new Vector3d();

    public StabilizerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        deadbandBehaviour = new DeadbandScrollValueBehaviour(
                Component.translatable("block.starlight_logistics.stabilizer.deadband"),
                this,
                new DeadbandValueBoxTransform()  // 侧面 4 面
        ).between(MIN_DEADBAND, MAX_DEADBAND);
        deadbandBehaviour.value = 3;
        behaviours.add(deadbandBehaviour);

        redstoneModeBehaviour = new RedstoneModeBehaviour(
                RedstoneMode.class,
                Component.translatable("tooltip.starlight_logistics.stabilizer.redstone_mode"),
                this,
                new RedstoneModeValueBoxTransform()  // 上下 2 面
        );
        redstoneModeBehaviour.value = 0;  // 默认 ACTIVE_WHEN_OFF(无红石时开启)
        behaviours.add(redstoneModeBehaviour);
    }

    /** 休眠时复位模式锁存与换向冷却:唤醒后首拍按符号直接定向。 */
    private void resetMode() {
        modeLatch = 0;
        switchCooldown = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        int signal = level.getBestNeighborSignal(worldPosition);
        if (!redstoneModeBehaviour.get().isActiveFor(signal)) {
            resetMode();
            setTiers(1, 1);
            return;
        }

        SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) {
            resetMode();
            setTiers(1, 1);
            return;
        }

        MassData massData = serverSubLevel.getMassTracker();
        Vector3dc com = massData.getCenterOfMass();
        if (com == null) {
            resetMode();
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
            resetMode();
            setTiers(1, 1);
            return;
        }

        // 高度判据:方块中心相对质心沿世界重力轴的偏移 h [格]。
        // 世界 UP 在本地系 = -ld(transformInverse(DOWN));h = "质心在世界系下高出我的格数"
        //   = (comWorld.y - blockWorld.y) = ((comLocal-blockLocal)·(-ld)) = rel·ld,rel = block-com。
        // h > 0 -> 质心在我上方 -> 我在低端 -> lift;h < 0 -> 我在高端 -> mass。
        // 不再走水平投影 error:质心会随自身档位切换而移动,水平判据构成自反馈极限环(高频闪烁根因),
        // 竖直判据下自身配重反而强化当前决策方向(收敛),且绕开 atan2 在 ±90° 的退化。
        double bx = worldPosition.getX() + 0.5 - com.x();
        double by = worldPosition.getY() + 0.5 - com.y();
        double bz = worldPosition.getZ() + 0.5 - com.z();
        double distToCom = Math.sqrt(bx * bx + by * by + bz * bz);

        double heightDiff = bx * ld.x() + by * ld.y() + bz * ld.z();

        boolean isLowSide;
        boolean switched = false;
        if (switchCooldown > 0) {
            switchCooldown--;                 // 冷却中:维持现模式,只调档不换向
            isLowSide = modeLatch == 1;
            if (!isLowSide && modeLatch != 2) {  // 冷却期锁存异常(理论不可达),兜底重定
                isLowSide = heightDiff >= 0;
                modeLatch = (byte) (isLowSide ? 1 : 2);
            }
        } else if (modeLatch == 1) {
            isLowSide = true;   // 锁存低端:除非反向证据越过阈值才换向
            if (heightDiff < -MODE_HYST) {
                isLowSide = false;
                modeLatch = 2;
                switched = true;
            }
        } else if (modeLatch == 2) {
            isLowSide = false;
            if (heightDiff > MODE_HYST) {
                isLowSide = true;
                modeLatch = 1;
                switched = true;
            }
        } else {
            isLowSide = heightDiff >= 0;  // 首拍无历史,直接按符号定
            modeLatch = (byte) (isLowSide ? 1 : 2);
        }
        if (switched) switchCooldown = SWITCH_COOLDOWN;

        if (distToCom < MIN_LEVER_DIST) {
            // 差重几乎无力矩,休眠但保留锁存(暂离≠翻案)
            setTiers(1, 1);
            return;
        }

        // 自适应满档角:倾斜速度大 -> 满档角小 -> 早满档(抢在 45° 物理失效前)。档位按总倾斜角调度。
        double tiltSpeed = Math.sqrt(angVelLocalCache.x() * angVelLocalCache.x()
                + angVelLocalCache.z() * angVelLocalCache.z());
        double maxAngleEff = MAX_ANGLE_BASE - BETA * tiltSpeed;
        if (maxAngleEff < MIN_MAX_ANGLE) maxAngleEff = MIN_MAX_ANGLE;

        double frac = Math.toDegrees(tilt) / maxAngleEff;
        if (frac < 0) frac = 0;
        else if (frac > 1) frac = 1;

        // 过零衰减:接近水平位时档位收束(frac 乘以高度差归一值),防止满档力矩冲过平衡点续能。
        // 副作用是换向后短暂软启动(需重新拉开 ~TIER_FADE_BLOCKS 高度差),恰好抑制二次泵能。
        double fade = Math.min(1.0, Math.abs(heightDiff) / TIER_FADE_BLOCKS);
        frac *= fade;

        int tier = 1 + (int) Math.round(15 * frac);
        if (tier < 1) tier = 1;
        else if (tier > 16) tier = 16;

        if (isLowSide) {
            setTiers(1, tier);  // lift 模式(我在低端,需向上力)
        } else {
            setTiers(tier, 1);  // mass 模式(我在高端,需向下力)
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (level == null || level.isClientSide) return;
        if (!redstoneModeBehaviour.get().isActiveFor(level.getBestNeighborSignal(worldPosition))) return;  // 停用

        Pose3dc pose = subLevel.logicalPose();
        Vector3d angVelGlobal = handle.getAngularVelocity(new Vector3d());
        // 全局角速度转本地系,按轴分离 pitch/roll(阻尼)与 yaw(不阻尼);存缓存供 P 项读
        Vector3d angVelLocal = pose.orientation().transformInverse(angVelGlobal, new Vector3d());
        angVelLocalCache.set(angVelLocal);

        // 自适应 KD:倾斜速度大 -> 强阻尼(防过冲到 45°+);大倾角下再线性增强
        //(慢速大幅摆动时角速度小,纯速度反馈耗散不足,是"大角度慢摆停不下来"的成因之一)
        double tiltSpeed = Math.sqrt(angVelLocal.x() * angVelLocal.x()
                + angVelLocal.z() * angVelLocal.z());
        Vector3d ld = new Vector3d(0, -1, 0);
        pose.orientation().transformInverse(ld);
        double tiltDeg = Math.toDegrees(Math.atan2(Math.sqrt(ld.x() * ld.x() + ld.z() * ld.z()), -ld.y()));
        double kdEff = KD_BASE * (1.0 + ALPHA * tiltSpeed) * (1.0 + DAMP_TILT_GAIN * Math.abs(tiltDeg) / MAX_ANGLE_BASE);

        // D 项:阻尼 pitch/roll(本地 x/z),不阻尼 yaw(本地 y)以保转向自由
        Vector3d dampTorqueLocal = new Vector3d(-kdEff * angVelLocal.x(), 0, -kdEff * angVelLocal.z());
        // 角冲量 = 力矩 * timeStep;applyAngularImpulse 接受本地系角冲量
        handle.applyAngularImpulse(dampTorqueLocal.mul(timeStep));
    }

    /**
     * 目标档位写入(带斜坡限速):每维每 tick 最多变化 {@link #MAX_TIER_STEP} 级。
     * 参数是"目标档",实际写入值向目标逼近一步。瞬时满幅跳变会在多块场景互相激励成极限环,
     * 斜坡让每步质量变化足够小,Sable 的质心反馈被摊平到多拍上。
     */
    private void setTiers(int massTarget, int liftTarget) {
        BlockState state = getBlockState();
        int curMass = state.getValue(StabilizerBlock.MASS_TIER);
        int curLift = state.getValue(StabilizerBlock.LIFT_TIER);
        int newMass = Mth.clamp(curMass + Mth.clamp(massTarget - curMass, -MAX_TIER_STEP, MAX_TIER_STEP), 1, 16);
        int newLift = Mth.clamp(curLift + Mth.clamp(liftTarget - curLift, -MAX_TIER_STEP, MAX_TIER_STEP), 1, 16);
        if (newMass == curMass && newLift == curLift) return;
        level.setBlockAndUpdate(worldPosition, state
                .setValue(StabilizerBlock.MASS_TIER, newMass)
                .setValue(StabilizerBlock.LIFT_TIER, newLift));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        BlockState state = getBlockState();
        int massTier = state.getValue(StabilizerBlock.MASS_TIER);
        int liftTier = state.getValue(StabilizerBlock.LIFT_TIER);

        CreateLang.builder()
                .add(Component.translatable("block.starlight_logistics.stabilizer")
                        .withStyle(ChatFormatting.WHITE))
                .forGoggles(tooltip);

        String modeKey;
        int currentTier;
        ChatFormatting modeColor;
        if (massTier > 1) {
            modeKey = "tooltip.starlight_logistics.stabilizer.mass_mode";
            currentTier = massTier;
            modeColor = ChatFormatting.RED;
        } else if (liftTier > 1) {
            modeKey = "tooltip.starlight_logistics.stabilizer.lift_mode";
            currentTier = liftTier;
            modeColor = ChatFormatting.AQUA;
        } else {
            modeKey = "tooltip.starlight_logistics.stabilizer.idle";
            currentTier = 0;
            modeColor = ChatFormatting.DARK_GRAY;
        }
        CreateLang.builder()
                .add(Component.translatable("tooltip.starlight_logistics.stabilizer.mode")
                        .withStyle(ChatFormatting.GRAY))
                .add(Component.literal(": ")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .add(Component.translatable(modeKey)
                        .withStyle(modeColor))
                .forGoggles(tooltip, 1);

        if (currentTier > 0) {
            CreateLang.builder()
                    .add(Component.translatable("tooltip.starlight_logistics.stabilizer.output")
                            .withStyle(ChatFormatting.GRAY))
                    .forGoggles(tooltip, 1);
            CreateLang.number(currentTier)
                    .add(CreateLang.text(" kpg"))
                    .style(ChatFormatting.GOLD)
                    .forGoggles(tooltip, 2);
        }

        CreateLang.builder()
                .add(Component.translatable("block.starlight_logistics.stabilizer.deadband")
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);
        CreateLang.number(deadbandBehaviour.getValue())
                .add(CreateLang.text("°"))
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 2);

        // 红石模式(上下两面切换的当前值)
        RedstoneMode mode = redstoneModeBehaviour.get();
        CreateLang.builder()
                .add(Component.translatable("tooltip.starlight_logistics.stabilizer.redstone_mode")
                        .withStyle(ChatFormatting.GRAY))
                .add(Component.literal(": ")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .add(Component.translatable(mode.getTranslationKey())
                        .withStyle(ChatFormatting.YELLOW))
                .forGoggles(tooltip, 1);

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

        // 独立剪贴板键:Create 默认全部 ValueSettingsBehaviour 共用 "Settings",会把死区角度
        // 粘到配重块/配轻块等其他方块,或反向把别人的档位值粘进来。键不同则粘贴时
        // tag.getCompound(key) 为空直接失败;读侧再按 0..30 校验兜底。
        @Override
        public String getClipboardKey() {
            return "AeroStabilizerDeadband";
        }

        @Override
        public boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag tag, Player player,
                                         Direction side, boolean simulate) {
            if (tag.getInt("Row") != 0 || tag.getInt("Value") < MIN_DEADBAND || tag.getInt("Value") > MAX_DEADBAND)
                return false;
            return super.readFromClipboard(registries, tag, player, side, simulate);
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, MAX_DEADBAND, 1,
                    ImmutableList.of(Component.translatable("starlight_logistics.unit.deadband_deg")),
                    new ValueSettingsFormatter(this::formatSettings));
        }

        public MutableComponent formatSettings(ValueSettings settings) {
            int value = Math.max(MIN_DEADBAND, Math.min(MAX_DEADBAND, settings.value()));
            return Component.literal(value + "°");
        }
    }

    /**
     * 死区 ValueBoxTransform - 只在侧面 4 个面弹板(上下两面留给红石模式)。
     */
    private static class DeadbandValueBoxTransform extends ValueBoxTransform.Sided {
        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction.getAxis() != Direction.Axis.Y;
        }

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8, 15.5);
        }
    }

    /**
     * 红石模式 ValueBoxTransform - 只在上下 2 个面弹板。
     */
    private static class RedstoneModeValueBoxTransform extends ValueBoxTransform.Sided {
        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction.getAxis() == Direction.Axis.Y;
        }

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8, 15.5);
        }
    }

    /**
     * 红石模式 ScrollOptionBehaviour - 独立 BehaviourType,避免与死区 ScrollValueBehaviour
     * 共用 ScrollValueBehaviour.TYPE 而在 SmartBlockEntity 的 behaviours map 里互相覆盖
     * (后者会覆盖前者,导致死区弹板丢失)。仍 extends ScrollOptionBehaviour,所以
     * ScrollValueRenderer(instanceof ScrollValueBehaviour)和 ValueSettingsInputHandler 照常处理。
     */
    private static class RedstoneModeBehaviour extends ScrollOptionBehaviour<RedstoneMode> {
        public static final BehaviourType<RedstoneModeBehaviour> TYPE = new BehaviourType<>();

        public RedstoneModeBehaviour(Class<RedstoneMode> enumClass, Component label,
                                     SmartBlockEntity be, ValueBoxTransform slot) {
            super(enumClass, label, be, slot);
        }

        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

        // 独立 netId:ValueSettingsPacket 用 behaviourIndex(=behaviour.netId()) 路由 setValueSettings,
        // 默认 netId=0 与 deadbandBehaviour 冲突 -> 调整红石模式时 packet 路由到死区(死区变 1、红石没变)。
        // 动态取本 behaviour 在 BE behaviour 列表中的索引,替代硬编码 1:两端 addBehaviours 顺序
        // 一致故索引恒匹配,今后再插入其他 behaviour 也不会静默错路由
        @Override
        public int netId() {
            if (blockEntity != null) {
                var behaviours = blockEntity.getAllBehaviours();
                int i = 0;
                for (BlockEntityBehaviour behaviour : behaviours) {
                    if (behaviour == this) return i;
                    i++;
                }
            }
            return 1;
        }

        // 独立 NBT key:ScrollValueBehaviour.write/read 用固定 key "ScrollValue",而
        // SmartBlockEntity 所有 behaviour 共享同一个 BE tag,两个 ScrollValueBehaviour 共存时
        // "ScrollValue" 会被后 write 的覆盖(本类会覆盖 deadbandBehaviour,导致死区值总被重置为 0)。
        @Override
        public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            nbt.putInt("RedstoneMode", value);
        }

        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            value = nbt.getInt("RedstoneMode");
        }
    }

    /**
     * 红石控制模式 - 上下两面 ScrollOptionBehaviour 切换。
     * ACTIVE_WHEN_OFF: 无红石时工作(默认);ACTIVE_WHEN_ON: 有红石时工作。
     * 图标复用 Create 的 I_PASSIVE(被动/无红石)/I_ACTIVE(主动/有红石)。
     */
    public enum RedstoneMode implements INamedIconOptions {
        ACTIVE_WHEN_OFF(AllIcons.I_PASSIVE),
        ACTIVE_WHEN_ON(AllIcons.I_ACTIVE);

        private final AllIcons icon;
        private final String translationKey;

        RedstoneMode(AllIcons icon) {
            this.icon = icon;
            this.translationKey = "tooltip.starlight_logistics.stabilizer.redstone_mode." + name().toLowerCase();
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }

        /** 给定红石信号,返回本模式是否应工作。 */
        public boolean isActiveFor(int signal) {
            return this == ACTIVE_WHEN_ON ? signal > 0 : signal == 0;
        }
    }
}
