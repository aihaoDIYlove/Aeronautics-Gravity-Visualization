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
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
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
 * 自稳定方块 BE - PD 混合 + 倾斜速度自适应,P 项走连续点力(同螺旋桨通路)。
 *
 * **P 项(恢复力,直接施力)**:每物理 tick 在 sable$physicsTick 里算。
 *   - 姿态:世界 DOWN 经 orientation.transformInverse 到本地 = ld。
 *     pitch = atan2(ld.z, -ld.y);roll = atan2(ld.x, -ld.y);tilt = sqrt(pitch²+roll²)。
 *   - 死区(总倾斜角)由侧面 4 面 ScrollValueBehaviour 调 0..30°。tilt < deadband -> 休眠。
 *   - **模式判据 = 世界竖直高度差**(h):h = rel·ld,rel = 方块中心-质心(SubLevel 本地系)。
 *     h > 0 -> 我在低端 -> 向上力(BALLOON_LIFT 组);h < 0 -> 高端 -> 向下力(GRAVITY 组)。
 *     方向每 tick 按符号重算,无锁存/滞回:施力不改变质量分布,质心不漂移,不存在换向反馈。
 *   - **力是连续值**:F = frac × MAX_FORCE_KPG(单位 = kpg 等效重力,乘维度 |g| 换算成力),
 *     无 1..16 档量化,也就无需档位斜坡/换向冷却/滞回三件套(旧质量方案防震荡的全部补丁)。
 *   - **过零衰减**:frac 乘 min(1, |heightDiff|/TIER_FADE_BLOCKS),接近水平位输出收束到零,
 *     防满幅力矩冲过平衡点泵能。施力不挪质心,h 不会因自身出力而自拆力臂,衰减只做空间增益整形。
 *   - **自适应满档角**(按倾斜速度):tiltSpeed = sqrt(ωx²+ωz²)。
 *     maxAngleEff = MAX_ANGLE_BASE - BETA * tiltSpeed,clamp 到 [MIN_MAX_ANGLE, MAX_ANGLE_BASE]。
 *     慢速:30° 满档(温和);快速(tiltSpeed=2):10° 满档(早响应,抢在 45° 物理失效前)。
 *   - **施力通路**(照搬 Sable 螺旋桨 BlockEntitySubLevelPropellerActor):
 *     subLevel.getOrCreateQueuedForceGroup(组).applyAndRecordPointForce(方块中心, F·g·timeStep)。
 *     QueuedForceGroup 只累加力/力矩,完全不触碰 MassTracker —— 质量、惯性张量、质心全部不变,
 *     消除旧方案"加质量拉近质心自拆力臂"+"质心实时漂移引发换向抖动"两大震荡源。
 *   - MASS_TIER/LIFT_TIER blockstate **降级为纯灯带显示**(physics_block_properties/stabilizer.json
 *     固定 mass=1/材料,Sable 不再读档位):低端点 LIFT_TIER、高端点 MASS_TIER,数值 = 1+round(15×frac),
 *     渲染器/护目镜照旧读 blockstate,无需 NBT sync。
 *
 * **D 项(阻尼,直接施角冲量)**:同在 sable$physicsTick。
 *   - 读载具全局角速度(handle.getAngularVelocity),转本地系,存 angVelLocalCache 供自适应满档角读。
 *   - **自适应 KD**:tiltSpeed = sqrt(ωx²+ωz²)。KD_eff = KD_BASE * (1 + ALPHA * tiltSpeed)。
 *   - 阻尼 pitch/roll 轴(本地 x/z),**不阻尼 yaw(本地 y)**保转向自由;KD_eff 随倾角线性增强。
 *   - 阻尼力矩 = -KD_eff * (ωx, 0, ωz),角冲量 = 力矩 * timeStep,handle.applyAngularImpulse(本地)。
 *
 * **红石模式(上下两面 ScrollOptionBehaviour 切换)**:ACTIVE_WHEN_OFF(默认,无红石时工作) /
 *   ACTIVE_WHEN_ON(有红石时工作)。非工作状态时灯带归零档、P/D 均不施力。
 * KD_BASE/ALPHA/MAX_ANGLE_BASE/BETA/MAX_FORCE_KPG 是代码常量(开发者实测调);死区是玩家右键调。
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
    // P 项最大力 [kpg 等效]:frac=1 时施加相当于 16 kpg 重量的力(与旧档位上限同量级,便于迁移手感)。
    private static final double MAX_FORCE_KPG = 16.0;
    // 过零衰减 [格]:输出按 |heightDiff| 线性收束到 0,越过此距离才允许满幅。
    // 防"满幅力矩冲过水平位"每半周泵能 -> 大角度等幅摆。施力不挪质心,纯空间整形,无自拆副作用。
    private static final double TIER_FADE_BLOCKS = 1.8;
    // 倾斜增强阻尼:kdEff 随倾角线性放大,30° 时 x2、60° 时 x3(慢速大幅摆动的额外耗散)。
    private static final double DAMP_TILT_GAIN = 2.0;

    // 灯带显示档位目标(服务端 physicsTick 写,tick 刷进 blockstate;不持久化,停摆即回 1)。
    private byte pendingMassTier = 1;
    private byte pendingLiftTier = 1;

    private ScrollValueBehaviour deadbandBehaviour;
    // 红石控制模式(上下两面切换)。value=0 -> ACTIVE_WHEN_OFF(默认,无红石时工作)。
    // 用 RedstoneModeBehaviour(独立 BehaviourType)而非裸 ScrollOptionBehaviour:后者继承
    // ScrollValueBehaviour.TYPE,会与 deadbandBehaviour 在 SmartBlockEntity 的 behaviours map 里冲突覆盖。
    private RedstoneModeBehaviour redstoneModeBehaviour;
    // sable$physicsTick 写(最新物理 tick 的本地角速度)。初始 0。
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

    /** 休眠:灯带显示归零档(下个 tick 刷进 blockstate)。 */
    private void setDormant() {
        pendingMassTier = 1;
        pendingLiftTier = 1;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        setVisualTiers(pendingMassTier, pendingLiftTier);
    }

    /** 灯带显示档位写入 blockstate(纯视觉,Sable 不再读档位质量)。值变才 setBlock,防刷更新。 */
    private void setVisualTiers(int massTarget, int liftTarget) {
        BlockState state = getBlockState();
        int newMass = Mth.clamp(massTarget, 1, 16);
        int newLift = Mth.clamp(liftTarget, 1, 16);
        int curMass = state.getValue(StabilizerBlock.MASS_TIER);
        int curLift = state.getValue(StabilizerBlock.LIFT_TIER);
        if (newMass == curMass && newLift == curLift) return;
        level.setBlockAndUpdate(worldPosition, state
                .setValue(StabilizerBlock.MASS_TIER, newMass)
                .setValue(StabilizerBlock.LIFT_TIER, newLift));
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (level == null || level.isClientSide) return;

        // 全局角速度转本地系,存缓存供自适应满档角读
        Pose3dc pose = subLevel.logicalPose();
        Vector3d angVelGlobal = handle.getAngularVelocity(new Vector3d());
        Vector3d angVelLocal = pose.orientation().transformInverse(angVelGlobal, new Vector3d());
        angVelLocalCache.set(angVelLocal);

        if (!redstoneModeBehaviour.get().isActiveFor(level.getBestNeighborSignal(worldPosition))) {
            setDormant();  // 停用:P 不施力、灯带归零
        } else {
            MassData massData = subLevel.getMassTracker();
            Vector3dc com = massData.getCenterOfMass();
            if (com == null) {
                setDormant();
            } else {
                tickForces(subLevel, pose, com, timeStep);
            }
        }

        // D 项:阻尼 pitch/roll(本地 x/z),不阻尼 yaw(本地 y)以保转向自由
        Vector3d ld = new Vector3d(0, -1, 0);
        pose.orientation().transformInverse(ld);
        double tiltSpeed = Math.sqrt(angVelLocal.x() * angVelLocal.x() + angVelLocal.z() * angVelLocal.z());
        double tiltDeg = Math.toDegrees(Math.atan2(Math.sqrt(ld.x() * ld.x() + ld.z() * ld.z()), -ld.y()));
        double kdEff = KD_BASE * (1.0 + ALPHA * tiltSpeed) * (1.0 + DAMP_TILT_GAIN * Math.abs(tiltDeg) / MAX_ANGLE_BASE);
        Vector3d dampTorqueLocal = new Vector3d(-kdEff * angVelLocal.x(), 0, -kdEff * angVelLocal.z());
        // 角冲量 = 力矩 * timeStep;applyAngularImpulse 接受本地系角冲量
        handle.applyAngularImpulse(dampTorqueLocal.mul(timeStep));
    }

    /** P 项:按姿态偏差算连续恢复力,经 QueuedForceGroup 施点力(同螺旋桨通路,不碰 MassTracker)。 */
    private void tickForces(ServerSubLevel subLevel, Pose3dc pose, Vector3dc com, double timeStep) {
        // 姿态:世界 DOWN 的本地表示
        Vector3d ld = new Vector3d(0, -1, 0);
        pose.orientation().transformInverse(ld);
        double pitch = Math.atan2(ld.z(), -ld.y());
        double roll = Math.atan2(ld.x(), -ld.y());
        double tilt = Math.sqrt(pitch * pitch + roll * roll);

        int deadbandDeg = deadbandBehaviour.getValue();
        if (tilt < Math.toRadians(deadbandDeg)) {
            setDormant();
            return;
        }

        // 高度判据:方块中心相对质心沿世界重力轴的偏移 h [格]。
        // h = (blockLocal - comLocal)·ld = "质心在世界系下高出我的格数"。
        // h > 0 -> 质心在我上方 -> 我在低端 -> 向上力;h < 0 -> 高端 -> 向下力。
        // 每 tick 按符号重算,无锁存:施力不改质量分布,质心不动,不存在换向自反馈。
        double bx = worldPosition.getX() + 0.5 - com.x();
        double by = worldPosition.getY() + 0.5 - com.y();
        double bz = worldPosition.getZ() + 0.5 - com.z();
        double heightDiff = bx * ld.x() + by * ld.y() + bz * ld.z();
        boolean lowSide = heightDiff >= 0;

        // 自适应满档角:倾斜速度大 -> 满档角小 -> 早满档(抢在 45° 物理失效前)。
        double tiltSpeed = Math.sqrt(angVelLocalCache.x() * angVelLocalCache.x()
                + angVelLocalCache.z() * angVelLocalCache.z());
        double maxAngleEff = MAX_ANGLE_BASE - BETA * tiltSpeed;
        if (maxAngleEff < MIN_MAX_ANGLE) maxAngleEff = MIN_MAX_ANGLE;

        double frac = Math.toDegrees(tilt) / maxAngleEff;
        if (frac < 0) frac = 0;
        else if (frac > 1) frac = 1;

        // 过零衰减:接近水平位时输出收束,防止满幅力矩冲过平衡点泵能。
        double fade = Math.min(1.0, Math.abs(heightDiff) / TIER_FADE_BLOCKS);
        frac *= fade;

        // 连续恢复力 [kpg 等效] x 维度 |g| -> 力 [N],冲量 = 力 * timeStep。方向:低端沿世界 UP(-ld),高端 DOWN(ld)。
        double forceKpg = frac * MAX_FORCE_KPG;
        if (forceKpg > 1e-3) {
            Vector3d gravity = DimensionPhysicsData.getGravity(level);
            double g = gravity.length();
            if (g > 1e-4) {
                Vector3d dirLocal = new Vector3d(ld).mul(lowSide ? -1.0 : 1.0);
                Vector3d impulse = dirLocal.mul(forceKpg * g * timeStep);
                Vector3d at = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
                // 不经 ForceGroups 的任何成员(其字段类型 RegistryObject 来自 Veil,不在编译类路径,
                // 连字段引用都会触发该类加载):按注册路径从 vanilla Registry 直取 ForceGroup 实例。
                ResourceLocation groupId = Sable.sablePath(lowSide ? "balloon_lift" : "gravity");
                ForceGroup group = ForceGroups.REGISTRY.get(groupId);
                QueuedForceGroup queued = subLevel.getOrCreateQueuedForceGroup(group);
                queued.applyAndRecordPointForce(at, impulse);
            }
        }

        // 灯带显示档位(1..16)随连续输出取整,低端点 lift、高端点 mass;tick 里刷 blockstate。
        int vis = Mth.clamp(1 + (int) Math.round(15 * frac), 1, 16);
        pendingMassTier = (byte) (lowSide ? 1 : vis);
        pendingLiftTier = (byte) (lowSide ? vis : 1);
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

        // 独立 netId:ValueSettingsPacket 用 behaviourIndex(=behaviour.netId())路由 setValueSettings,
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
