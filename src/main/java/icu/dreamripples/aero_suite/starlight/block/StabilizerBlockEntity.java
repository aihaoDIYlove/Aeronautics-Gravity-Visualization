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
 *   - **满档角(双值,与 gimbal_sensor 侧面设置同机制)**:点东西面调 X 轴(东西倾)、点南北面调
 *     Z 轴(南北倾),各 0..90°、默认 15°,调值板单行(轴由点击面决定)。单轴 0 = 关该轴通道,
 *     双轴 0 = P/D 全关。各通道输出从 0° 起随倾斜线性爬升、到该轴满档角恰好满幅。
 *     **无死区**:任何非零倾角都有恢复力矩、零倾斜输出恰好为零("未倾斜不出力"由比例性天然保证),
 *     载具不会停在死区内的任意奇角,过满档角也无力矩跳变。
 *   - tau_P 按通道独立饱和:tau.x = -ld.z * Kp/max(|ld.z|, sin(Az)),tau.z = ld.x * Kp/max(|ld.x|, sin(Ax))
 *     (Ax/Az = X/Z 轴满档角;本地系,Y 分量恒 0 不碰偏 yaw)。ld 的水平分量指向低端(斜坡上球往
 *     低处滚),ld × 本地UP 即回正力矩轴;各通道 |ld 分量| < sin(A) 时线性、达到后钳在 Kp
 *     ——连续、无换向、无跳变。
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
 *   水平静止但上下颠簸时也要工作,仅受红石门控。k_heave = 0 关闭。
 *
 * **红石模式(上下两面 ScrollOptionBehaviour 切换)**:ACTIVE_WHEN_OFF(默认,无红石时工作)/
 *   ACTIVE_WHEN_ON(有红石时工作)。非工作状态时 P/D/heave 全部不施力,灯带归零。
 *
 * **灯带显示**:单一输出强度档 1+round(15*max(两通道 min(1,|ld 分量|/sin(满档角)))) 写入 BlockState.LIFT_TIER(青色带,
 *   渲染器按档插值);MASS_TIER 恒 1,纯为 blockstate schema 兼容保留(旧存档的点亮值首 tick 归一)。
 *   停摆即回 1,tick 里刷 blockstate,值变才 setBlock。护目镜的分轴输出档(0..15)另行经
 *   BE NBT 同步(OutX/OutZ,值变才 sendData;LIFT_TIER 只有合并值且 1=灭,不适合做数字显示)。
 */
public class StabilizerBlockEntity extends SmartBlockEntity
        implements IHaveGoggleInformation, BlockEntitySubLevelActor {

    private static final int MIN_FULL_SCALE = 0;
    private static final int MAX_FULL_SCALE = 90;
    /** 大倾角阻尼加成的归一化分母(度):|tilt| 达此值时加成满幅 (1+gain) */
    private static final double DAMP_TILT_NORM_DEG = 30.0;

    // 灯带显示档位目标(服务端 physicsTick 写,tick 刷进 blockstate;不持久化,停摆即回 1)
    private byte pendingLiftTier = 1;
    // 过载:任一轴倾斜超过该轴满档角(输出钳在 Kp 顶格)-> 输出档 +1 到 16、灯带转红、护目镜"过载中"。
    // 纯警示,物理出力不变;与灯带一起走 blockstate OVERDRIVE 属性同步
    private boolean pendingOverdrive = false;
    // 护目镜分轴输出档(0..15,关=0 满=15):服务端 physicsTick 算,tick 里变了才 sendData
    // 走 BE clientPacket NBT 同步(LIFT_TIER 只有合并值,分轴显示必须单独同步);客户端 read 后供护目镜
    private byte pendingOutX = 0, pendingOutZ = 0;  // 最新计算值(未同步)
    private byte sentOutX = -1, sentOutZ = -1;      // 已同步到客户端(-1 保证首帧必发)

    private FullScaleScrollValueBehaviour fullScaleBehaviour;
    // 红石控制模式(上下两面切换)。value=0 -> ACTIVE_WHEN_OFF(默认,无红石时工作)。
    // 用 RedstoneModeBehaviour(独立 BehaviourType)而非裸 ScrollOptionBehaviour:后者继承
    // ScrollValueBehaviour.TYPE,会与 fullScaleBehaviour 在 SmartBlockEntity 的 behaviours map 里冲突覆盖。
    private RedstoneModeBehaviour redstoneModeBehaviour;

    public StabilizerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        FullScaleValueBoxTransform fullScaleBox = new FullScaleValueBoxTransform();
        fullScaleBehaviour = new FullScaleScrollValueBehaviour(
                Component.translatable("block.starlight_logistics.stabilizer.full_scale"),
                this,
                fullScaleBox  // 侧面 4 面;稍后 bind 回注 behaviour 引用(记录交互面)
        ).between(MIN_FULL_SCALE, MAX_FULL_SCALE);
        fullScaleBox.bind(fullScaleBehaviour);  // 默认值 15/15 在 behaviour 字段初始化
        behaviours.add(fullScaleBehaviour);

        redstoneModeBehaviour = new RedstoneModeBehaviour(
                RedstoneMode.class,
                Component.translatable("tooltip.starlight_logistics.stabilizer.redstone_mode"),
                this,
                new RedstoneModeValueBoxTransform()  // 上下 2 面
        );
        redstoneModeBehaviour.value = 0;  // 默认 ACTIVE_WHEN_OFF(无红石时开启)
        behaviours.add(redstoneModeBehaviour);
    }

    /** 休眠:灯带显示归零档、过载与分轴输出档归 0(下个 tick 刷进 blockstate / sendData)。 */
    private void setDormant() {
        pendingLiftTier = 1;
        pendingOverdrive = false;
        pendingOutX = 0;
        pendingOutZ = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        setVisualTiers(pendingLiftTier, pendingOverdrive);
        // 分轴输出档同步:变了才发包
        if (sentOutX != pendingOutX || sentOutZ != pendingOutZ) {
            sentOutX = pendingOutX;
            sentOutZ = pendingOutZ;
            sendData();
        }
    }

    // 分轴输出档走 BE NBT 同步(护目镜在客户端读;Write 同时覆盖存档与 getUpdateTag 两条路)
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putByte("OutX", sentOutX);
        tag.putByte("OutZ", sentOutZ);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        sentOutX = tag.getByte("OutX");
        sentOutZ = tag.getByte("OutZ");
    }

    /** 灯带显示档位写入 blockstate(纯视觉,Sable 不读档位)。LIFT_TIER 承载输出强度(过载钉 16 配 OVERDRIVE 红);MASS_TIER 归 1(兼容旧存档遗留的点亮值)。值变才 setBlock,防刷更新。 */
    private void setVisualTiers(int liftTarget, boolean overdrive) {
        BlockState state = getBlockState();
        int newLift = Mth.clamp(liftTarget, 1, 16);
        int curLift = state.getValue(StabilizerBlock.LIFT_TIER);
        int curMass = state.getValue(StabilizerBlock.MASS_TIER);
        boolean curOver = state.getValue(StabilizerBlock.OVERDRIVE);
        if (newLift == curLift && curMass == 1 && overdrive == curOver) return;
        level.setBlockAndUpdate(worldPosition, state
                .setValue(StabilizerBlock.LIFT_TIER, newLift)
                .setValue(StabilizerBlock.MASS_TIER, 1)
                .setValue(StabilizerBlock.OVERDRIVE, overdrive));
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

        // 满档角(双值):X 轴(东西倾,|ld.x|)点东西面调,Z 轴(南北倾,|ld.z|)点南北面调,各 0..90°。
        // 单轴 0 = 关该轴通道,双轴 0 = P/D 全关(heave 已处理,仍只受红石门控;0 = gimbal_sensor 的关闭约定)。
        // 各通道输出随倾斜线性爬升、到该轴满档角恰好满幅——无死区:零倾斜输出恰好为零,
        // 任何非零倾角都有恢复力矩,载具不会停在死区内的任意奇角。
        double sinAx = Math.sin(Math.toRadians(fullScaleBehaviour.getValueX()));
        double sinAz = Math.sin(Math.toRadians(fullScaleBehaviour.getValueZ()));
        if (sinAx < 1e-3 && sinAz < 1e-3) {
            setDormant();
            return;
        }
        // 输出档 = round(15*frac)(0..15);任一轴 rawFrac > 1(倾斜超过该轴满档角,力矩已钳在 Kp 顶格)
        // 该轴再 +1 到 16 = 过载档。纯警示(物理出力不变),灯带转红 + 护目镜"过载中"
        double rawFracX = sinAx >= 1e-3 ? Math.abs(ld.x()) / sinAx : 0.0;
        double rawFracZ = sinAz >= 1e-3 ? Math.abs(ld.z()) / sinAz : 0.0;
        double fracX = Math.min(1.0, rawFracX);
        double fracZ = Math.min(1.0, rawFracZ);
        double outFrac = Math.max(fracX, fracZ);
        pendingOutX = (byte) Math.min(16, Math.round(15 * fracX) + (rawFracX > 1.0 ? 1 : 0));
        pendingOutZ = (byte) Math.min(16, Math.round(15 * fracZ) + (rawFracZ > 1.0 ? 1 : 0));
        pendingOverdrive = pendingOutX > 15 || pendingOutZ > 15;

        // P 项:恢复力矩。ld 水平分量指向低端(斜坡上球往低处滚),tau = Kp*(ld × 本地UP) 即回正轴;
        // 两通道独立饱和:< 满档角线性段,>= 满档角钳在 Kp(max 同时免除零除)
        double kp = cfg(t -> t.stabilizerKp.getF(), AeroSuiteConfig.Tunables.STABILIZER_KP_DEFAULT);
        Vector3d torque = new Vector3d(
                sinAz >= 1e-3 ? -ld.z() * kp / Math.max(Math.abs(ld.z()), sinAz) : 0.0,
                0,
                sinAx >= 1e-3 ? ld.x() * kp / Math.max(Math.abs(ld.x()), sinAx) : 0.0);

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

        // 灯带:常规 1+max(输出档)-> 1..16;过载钉 16 配 OVERDRIVE 红(渲染器转色)
        pendingLiftTier = pendingOverdrive
                ? 16
                : (byte) Mth.clamp(1 + Math.max(pendingOutX, pendingOutZ), 1, 16);
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
        // 过载判定走同步的分轴输出档(16 = 过载),省一个额外同步位
        boolean overdrive = sentOutX > 15 || sentOutZ > 15;

        CreateLang.builder()
                .add(Component.translatable("block.starlight_logistics.stabilizer")
                        .withStyle(ChatFormatting.WHITE))
                .forGoggles(tooltip);

        CreateLang.builder()
                .add(Component.translatable("tooltip.starlight_logistics.stabilizer.status")
                        .withStyle(ChatFormatting.GRAY))
                .add(Component.literal(": ")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .add(Component.translatable(overdrive
                                ? "tooltip.starlight_logistics.stabilizer.overdriven"
                                : active
                                        ? "tooltip.starlight_logistics.stabilizer.active"
                                        : "tooltip.starlight_logistics.stabilizer.idle")
                        .withStyle(overdrive ? ChatFormatting.RED
                                : active ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        if (active) {
            CreateLang.builder()
                    .add(Component.translatable("tooltip.starlight_logistics.stabilizer.output")
                            .withStyle(ChatFormatting.GRAY))
                    .forGoggles(tooltip, 1);
            CreateLang.builder()
                    .add(Component.literal("X " + Mth.clamp(sentOutX, 0, 16) + "/15  Z " + Mth.clamp(sentOutZ, 0, 16) + "/15")
                            .withStyle(overdrive ? ChatFormatting.RED : ChatFormatting.GOLD))
                    .forGoggles(tooltip, 2);
        }

        CreateLang.builder()
                .add(Component.translatable("block.starlight_logistics.stabilizer.full_scale")
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);
        CreateLang.builder()
                .add(Component.literal("X " + fullScaleBehaviour.getValueX() + "°  Z " + fullScaleBehaviour.getValueZ() + "°")
                        .withStyle(ChatFormatting.AQUA))
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
     * ScrollValueBehaviour 子类 - 满档角双值 0..90°(与 gimbal_sensor 侧面设置同机制):
     * 点东西面编辑 X 轴(东西倾)、点南北面编辑 Z 轴(南北倾),两轴独立;调值板单行,
     * 轴由交互面决定(行不参与选轴)。
     * 输出随倾斜线性爬升,到该轴满档角恰好满幅;单轴 0 = 关该轴。
     * 轴路由靠 lastSide:由 FullScaleValueBoxTransform.fromSide 记录(Create 在交互与渲染前
     * 都会先调 fromSide 传注视/交互面,ValueSettingsInputHandler 与 ValueBox 源码已核实)。
     */
    private static class FullScaleScrollValueBehaviour extends ScrollValueBehaviour {
        // 最近交互/注视的面(东西面 -> X 轴,南北面 -> Z 轴);EAST 仅初始值,首帧渲染即被覆盖
        private Direction lastSide = Direction.EAST;
        private int valueX = 15;  // X 轴(东西倾,|ld.x| 通道)满档角
        private int valueZ = 15;  // Z 轴(南北倾,|ld.z| 通道)满档角
        // 基类 formatter 字段包私有不可访问,自持一份(gimbal 同款)
        private final Function<Integer, String> boxFormatter;

        public FullScaleScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
            this.boxFormatter = i -> (editingX() ? "X " : "Z ") + i + "°";
            withFormatter(this.boxFormatter);
        }

        boolean editingX() {
            return lastSide.getAxis() == Direction.Axis.X;
        }

        // 协变返回:基类 between 返回 ScrollValueBehaviour,链式赋给本类型字段会编译失败(gimbal 同款覆写)
        @Override
        public FullScaleScrollValueBehaviour between(int min, int max) {
            super.between(min, max);
            return this;
        }

        int getValueX() {
            return valueX;
        }

        int getValueZ() {
            return valueZ;
        }

        @Override
        public int getValue() {
            return editingX() ? valueX : valueZ;
        }

        @Override
        public void setValue(int value) {
            value = Mth.clamp(value, MIN_FULL_SCALE, MAX_FULL_SCALE);
            if (value == getValue()) return;
            if (editingX()) valueX = value;
            else valueZ = value;
            blockEntity.setChanged();
            blockEntity.sendData();
        }

        // 调值板:单行(轴由交互面决定,行不选轴;createBoard 在 fromSide 记录交互面之后调用,
        // 行标签即当前轴)。里程碑间隔 15(板宽 ∝ maxValue+里程碑数,间隔 1 会拉出 ~550px 长板;
        // 板内滚轮 ±1°、Shift 吸附里程碑 15°)
        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, MAX_FULL_SCALE, 15,
                    ImmutableList.of(Component.literal(editingX() ? "X" : "Z")),
                    new ValueSettingsFormatter(this::formatSettings));
        }

        // 单行板:行恒 0,轴由交互面(lastSide)决定
        @Override
        public ValueSettings getValueSettings() {
            return new ValueSettings(0, getValue());
        }

        @Override
        public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
            int value = Mth.clamp(valueSetting.value(), MIN_FULL_SCALE, MAX_FULL_SCALE);
            if (!valueSetting.equals(getValueSettings()))
                playFeedbackSound(this);
            if (editingX()) valueX = value;
            else valueZ = value;
            blockEntity.setChanged();
            blockEntity.sendData();
        }

        public MutableComponent formatSettings(ValueSettings settings) {
            int value = Math.max(MIN_FULL_SCALE, Math.min(MAX_FULL_SCALE, settings.value()));
            return Component.literal((editingX() ? "X " : "Z ") + value + "°");
        }

        // 基类 formatValue 读基类 value 字段(本类不用它,恒 0 -> 侧面数值框显示 0°),
        // 覆写为走 getValue()(gimbal 同款)
        @Override
        public String formatValue() {
            return this.boxFormatter.apply(this.getValue());
        }

        // 独立剪贴板键:Create 默认全部 ValueSettingsBehaviour 共用 "Settings",会把满档角
        // 粘到配重块/配轻块等其他方块,或反向把别人的档位值粘进来。键不同则粘贴时
        // tag.getCompound(key) 为空直接失败;读侧再按 0..90 双值校验兜底。换键也顺带让
        // 旧"死区"时代的剪贴板残留(单值语义不同)粘贴失败,不会以错误语义生效。
        @Override
        public String getClipboardKey() {
            return "AeroStabilizerFullScale";
        }

        @Override
        public boolean writeToClipboard(HolderLookup.Provider registries, CompoundTag tag, Direction side) {
            if (!acceptsValueSettings()) return false;
            tag.putInt("FullScaleX", valueX);
            tag.putInt("FullScaleZ", valueZ);
            return true;
        }

        @Override
        public boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag tag, Player player,
                                         Direction side, boolean simulate) {
            if (!acceptsValueSettings()) return false;
            if (!tag.contains("FullScaleX") || !tag.contains("FullScaleZ")) return false;
            int x = tag.getInt("FullScaleX");
            int z = tag.getInt("FullScaleZ");
            if (x < MIN_FULL_SCALE || x > MAX_FULL_SCALE || z < MIN_FULL_SCALE || z > MAX_FULL_SCALE)
                return false;
            if (simulate) return true;
            valueX = x;
            valueZ = z;
            blockEntity.setChanged();
            blockEntity.sendData();
            return true;
        }

        // 独立 NBT 键(双值):共享 BE tag,不能用基类固定键 "ScrollValue"(会与
        // RedstoneModeBehaviour 的注释同理撞键);旧死区时代的 "ScrollValue" 残值被忽略,默认 15/15。
        @Override
        public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            nbt.putInt("FullScaleX", valueX);
            nbt.putInt("FullScaleZ", valueZ);
        }

        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            valueX = nbt.contains("FullScaleX")
                    ? Mth.clamp(nbt.getInt("FullScaleX"), MIN_FULL_SCALE, MAX_FULL_SCALE) : 15;
            valueZ = nbt.contains("FullScaleZ")
                    ? Mth.clamp(nbt.getInt("FullScaleZ"), MIN_FULL_SCALE, MAX_FULL_SCALE) : 15;
        }
    }

    /**
     * 满档角 ValueBoxTransform - 只在侧面 4 个面弹板(上下两面留给红石模式)。
     * fromSide 把交互/注视面记进 behaviour.lastSide(东西面 -> X 轴,南北面 -> Z 轴):
     * Create 在交互(ValueSettingsInputHandler)与渲染(ValueBox.render,只用注视面)前
     * 都会先调 fromSide,故 lastSide 恒为玩家正注视/交互的面。
     */
    private static class FullScaleValueBoxTransform extends ValueBoxTransform.Sided {
        private FullScaleScrollValueBehaviour behaviour;

        FullScaleValueBoxTransform bind(FullScaleScrollValueBehaviour behaviour) {
            this.behaviour = behaviour;
            return this;
        }

        @Override
        public Sided fromSide(Direction direction) {
            if (behaviour != null) behaviour.lastSide = direction;
            return super.fromSide(direction);
        }

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
     * 红石模式 ScrollOptionBehaviour - 独立 BehaviourType,避免与满档角 ScrollValueBehaviour
     * 共用 ScrollValueBehaviour.TYPE 而在 SmartBlockEntity 的 behaviours map 里互相覆盖
     * (后者会覆盖前者,导致满档角弹板丢失)。仍 extends ScrollOptionBehaviour,所以
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
        // 默认 netId=0 与 fullScaleBehaviour 冲突 -> 调整红石模式时 packet 路由到满档角(角度变 1、红石没变)。
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
        // "ScrollValue" 会被后 write 的覆盖(本类会覆盖 fullScaleBehaviour,导致满档角总被重置为 0)。
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
