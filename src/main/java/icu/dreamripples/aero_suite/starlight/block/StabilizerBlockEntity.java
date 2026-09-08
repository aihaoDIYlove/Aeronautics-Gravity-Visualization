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
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import icu.dreamripples.aero_suite.common.config.AeroSuiteConfig;
import icu.dreamripples.aero_suite.common.config.FeatureGates;
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

import java.util.List;
import java.util.function.Function;

/**
 * 自稳定方块 BE - 纯姿态力矩 PD + 垂直阻尼(heave),与 simulated:gimbal_sensor 同构的"姿态黑盒"。
 *
 * 控制律只读整船姿态(logicalPose),不读质心、不读方块自身位置:任意位置放置行为一致,
 * 多块叠加 = 增益叠加。全部增益在 {@link AeroSuiteConfig.Tunables}(配置屏即改即生效),无代码常量。
 *
 * **P 项(恢复力矩)**:每物理 tick 在 sable$physicsTick 里算。
 *   - 姿态:世界 DOWN 经 orientation.transformInverse 到本地 = ld(与 gimbal_sensor 同款公式)。
 *     tilt = atan2(len(ld.xz), -ld.y)。
 *   - 死区(总倾斜角)由侧面 4 面 ScrollValueBehaviour 调 0..30°。tilt < 死区 -> P/D 全停(灯带归零):
 *     未倾斜不出力。
 *   - tau_P = Kp * (-ld.z, 0, ld.x)(本地系,Y 分量恒 0 不碰偏航)。ld 的水平分量指向高端
 *     (世界竖直在船体系里偏向抬起侧),绕竖直轴转 -90° 即"把船转回竖直"的力矩轴;
 *     幅值 = Kp*sin(tilt),天然饱和、连续无换向。
 *   - **D 项(阻尼)**:tau_D = -KD_eff * (wx, 0, wz),不阻 yaw 保转向自由。
 *     KD_eff = min(KD_MAX, KD*(1+ALPHA*tiltSpeed)*(1+DAMP_TILT_GAIN*tiltDeg/30)):
 *     阻尼只跟速度走 -> 过冲点(穿越水平位)速度峰值处自动最强、极端位置自动归零;
 *     KD_MAX 是离散稳定护栏(每子步角冲量过大 -> 反向过冲高频抖振)。
 *   - 合并 tau_P+tau_D 后 handle.applyAngularImpulse(本地系角冲量,· timeStep)。
 *
 * **heave 垂直阻尼(与姿态无关,仅受红石门控)**:竖直速度 v_y = v_world·up,
 *   up = 维度重力反方向(DimensionPhysicsData.getGravity,不硬编码 Y 轴);中心线性冲量
 *   = -k_heave * 总质量(MassData.getMass,含装置)* v_y * timeStep,转本地系后
 *   applyLinearImpulse。质心施加不产生寄生力矩;阻尼器不阻止到达新高度,只压低巡航速度。
 *   水平静止但上下颠簸时也要工作,故不受死区门控。k_heave = 0 关闭。
 *
 * **红石模式(上下两面 ScrollOptionBehaviour 切换)**:ACTIVE_WHEN_OFF(默认,无红石时工作)/
 *   ACTIVE_WHEN_ON(有红石时工作)。非工作状态时 P/D/heave 全部不施力,灯带归零。
 *
 * **灯带显示**:单一输出强度档 1+round(15*sin(tilt)) 写入 BlockState.LIFT_TIER(青色带,
 *   渲染器按档插值);MASS_TIER 恒 1,纯为 blockstate schema 兼容保留(旧存档的点亮值首 tick 归一)。
 *   停摆即回 1,tick 里刷 blockstate,值变才 setBlock。
 */
public class StabilizerBlockEntity extends SmartBlockEntity
        implements IHaveGoggleInformation, BlockEntitySubLevelActor {

    private static final int MIN_DEADBAND = 0;
    private static final int MAX_DEADBAND = 30;
    /** 大倾角阻尼加成的归一化分母(度):|tilt| 达此值时加成满幅 (1+gain) */
    private static final double DAMP_TILT_NORM_DEG = 30.0;

    // 灯带显示档位目标(服务端 physicsTick 写,tick 刷进 blockstate;不持久化,停摆即回 1)
    private byte pendingLiftTier = 1;

    private ScrollValueBehaviour deadbandBehaviour;
    // 红石控制模式(上下两面切换)。value=0 -> ACTIVE_WHEN_OFF(默认,无红石时工作)。
    // 用 RedstoneModeBehaviour(独立 BehaviourType)而非裸 ScrollOptionBehaviour:后者继承
    // ScrollValueBehaviour.TYPE,会与 deadbandBehaviour 在 SmartBlockEntity 的 behaviours map 里冲突覆盖。
    private RedstoneModeBehaviour redstoneModeBehaviour;

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
        pendingLiftTier = 1;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        setVisualTiers(pendingLiftTier);
    }

    /** 灯带显示档位写入 blockstate(纯视觉,Sable 不读档位)。LIFT_TIER 承载输出强度;MASS_TIER 归 1(兼容旧存档遗留的点亮值)。值变才 setBlock,防刷更新。 */
    private void setVisualTiers(int liftTarget) {
        BlockState state = getBlockState();
        int newLift = Mth.clamp(liftTarget, 1, 16);
        int curLift = state.getValue(StabilizerBlock.LIFT_TIER);
        int curMass = state.getValue(StabilizerBlock.MASS_TIER);
        if (newLift == curLift && curMass == 1) return;
        level.setBlockAndUpdate(worldPosition, state
                .setValue(StabilizerBlock.LIFT_TIER, newLift)
                .setValue(StabilizerBlock.MASS_TIER, 1));
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (level == null || level.isClientSide) return;

        // 红石停用:P/D/heave 全部不施力,灯带归零
        if (!redstoneModeBehaviour.get().isActiveFor(level.getBestNeighborSignal(worldPosition))) {
            setDormant();
            return;
        }

        Pose3dc pose = subLevel.logicalPose();

        // heave 垂直阻尼:与姿态无关(水平静止但上下颠簸时也要工作),仅受红石门控
        applyHeaveDamping(subLevel, pose, handle, timeStep);

        // 姿态:世界 DOWN 的本地表示(与 gimbal_sensor 同款)
        Vector3d ld = new Vector3d(0, -1, 0);
        pose.orientation().transformInverse(ld);
        double tiltRad = Math.atan2(Math.sqrt(ld.x() * ld.x() + ld.z() * ld.z()), -ld.y());
        double tiltDeg = Math.toDegrees(tiltRad);

        // 死区:未倾斜不出力(P/D 全停;heave 已处理)
        if (tiltDeg < deadbandBehaviour.getValue()) {
            setDormant();
            return;
        }

        // P 项:恢复力矩。ld 水平分量指向高端,绕竖直轴转 -90° 得回正力矩轴;幅值 = Kp*sin(tilt) 天然饱和
        Vector3d torque = new Vector3d(-ld.z(), 0, ld.x())
                .mul(cfg(t -> t.stabilizerKp.getF(), AeroSuiteConfig.Tunables.STABILIZER_KP_DEFAULT));

        // D 项:自适应阻尼(过冲点速度峰值处自动最强),不阻 yaw(本地 y)保转向自由;KD_MAX 封顶防离散抖振
        Vector3d angVelLocal = handle.getAngularVelocity(new Vector3d());
        pose.orientation().transformInverse(angVelLocal);
        double tiltSpeed = Math.sqrt(angVelLocal.x() * angVelLocal.x() + angVelLocal.z() * angVelLocal.z());
        double kdEff = cfg(t -> t.stabilizerKd.getF(), AeroSuiteConfig.Tunables.STABILIZER_KD_DEFAULT)
                * (1.0 + cfg(t -> t.stabilizerKdAlpha.getF(), AeroSuiteConfig.Tunables.STABILIZER_KD_ALPHA_DEFAULT) * tiltSpeed)
                * (1.0 + cfg(t -> t.stabilizerDampTiltGain.getF(), AeroSuiteConfig.Tunables.STABILIZER_DAMP_TILT_GAIN_DEFAULT) * tiltDeg / DAMP_TILT_NORM_DEG);
        double kdMax = cfg(t -> t.stabilizerKdMax.getF(), AeroSuiteConfig.Tunables.STABILIZER_KD_MAX_DEFAULT);
        if (kdEff > kdMax) kdEff = kdMax;
        torque.add(new Vector3d(-kdEff * angVelLocal.x(), 0, -kdEff * angVelLocal.z()));

        // 角冲量 = 力矩 * timeStep;applyAngularImpulse 接受本地系角冲量
        handle.applyAngularImpulse(torque.mul(timeStep));

        // 灯带:输出强度按 sin(tilt)(P 项相对幅值),1..16 写 LIFT_TIER
        pendingLiftTier = (byte) Mth.clamp(1 + (int) Math.round(15 * Math.sin(tiltRad)), 1, 16);
    }

    /** heave 垂直阻尼:中心线性冲量(不产生寄生力矩),抗上下颠簸。乘总质量使减速度与船重无关。 */
    private void applyHeaveDamping(ServerSubLevel subLevel, Pose3dc pose, RigidBodyHandle handle, double timeStep) {
        float kHeave = cfg(t -> t.stabilizerKHeave.getF(), AeroSuiteConfig.Tunables.STABILIZER_K_HEAVE_DEFAULT);
        if (kHeave <= 0) return;
        MassData massData = subLevel.getMassTracker();
        if (massData.isInvalid()) return;
        // up = 维度重力反方向(不硬编码 Y 轴)
        Vector3d up = DimensionPhysicsData.getGravity(level);
        if (up.lengthSquared() < 1e-8) return;
        up.normalize();
        double vUp = handle.getLinearVelocity(new Vector3d()).dot(up);
        // 世界系冲量 = -k * M * vUp * dt * up;applyLinearImpulse 收本地系,转轴后施加
        Vector3d impulseWorld = new Vector3d(up).mul(-kHeave * massData.getMass() * vUp * timeStep);
        handle.applyLinearImpulse(pose.orientation().transformInverse(impulseWorld, new Vector3d()));
    }

    /** null-safe 配置读取:配置未就绪时回退 Tunables 默认常量(同 ExtendoGrabServer 模式)。 */
    private static float cfg(Function<AeroSuiteConfig.Tunables, Float> getter, float def) {
        AeroSuiteConfig c = FeatureGates.CONFIG;
        return c != null ? getter.apply(c.tunables) : def;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        BlockState state = getBlockState();
        int liftTier = state.getValue(StabilizerBlock.LIFT_TIER);
        boolean active = liftTier > 1;

        CreateLang.builder()
                .add(Component.translatable("block.starlight_logistics.stabilizer")
                        .withStyle(ChatFormatting.WHITE))
                .forGoggles(tooltip);

        CreateLang.builder()
                .add(Component.translatable("tooltip.starlight_logistics.stabilizer.status")
                        .withStyle(ChatFormatting.GRAY))
                .add(Component.literal(": ")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .add(Component.translatable(active
                                ? "tooltip.starlight_logistics.stabilizer.active"
                                : "tooltip.starlight_logistics.stabilizer.idle")
                        .withStyle(active ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        if (active) {
            CreateLang.builder()
                    .add(Component.translatable("tooltip.starlight_logistics.stabilizer.output")
                            .withStyle(ChatFormatting.GRAY))
                    .forGoggles(tooltip, 1);
            CreateLang.number(liftTier)
                    .add(CreateLang.text(" / 15"))
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
     * ScrollValueBehaviour 子类 - 死区角度 0..30°。tilt < 死区时 P/D 全停(防小扰动)。
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
