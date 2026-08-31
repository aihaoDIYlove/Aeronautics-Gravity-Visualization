package icu.dreamripples.aero_suite.gravity.extendo;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlock;
import icu.dreamripples.aero_suite.common.AeroSuiteIds;
import icu.dreamripples.aero_suite.common.config.AeroSuiteConfig;
import icu.dreamripples.aero_suite.common.config.FeatureGates;
import icu.dreamripples.aero_suite.gravity.advancement.ModTriggers;
import icu.dreamripples.aero_suite.starlight.network.ExtendoGrabActionPayload;
import icu.dreamripples.aero_suite.starlight.network.ExtendoGrabDragPayload;
import icu.dreamripples.aero_suite.starlight.network.ExtendoGrabSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机械手载具抓取(伸缩机械手 + 载具绳索连接器) -- 服务端会话与物理约束。
 *
 * <p>玩法: 手持 {@code create:extendo_grip} 右键载具上的 {@code simulated:rope_connector}
 * 点按切换拖拽(与 Simulated 创造手杖同款: 约束马达拖拽, Tab+鼠标调姿态), 生存消耗:
 * <ul>
 *   <li>耐久 10/秒、背罐空气 15/秒(基础罐 900 点 = 整罐 1 分钟)、饥饿 1 点/秒 -- 三项均可在配置屏
 *       "数值特性相关"页调整(即时生效); 铜背罐可代付耐久({@link BacktankUtil#canAbsorbDamage}, 与
 *       Create 伸缩手/土豆加农炮同机制, 每秒一调扣配置的气/秒, 扩容附魔只加容量不降价)。
 *       背罐无耐久, 资源是空气 DataComponent</li>
 *   <li>饥饿 1/秒({@code causeFoodExhaustion(4)}, vanilla 4 exhaustion = 1 点饥饿/饱和)</li>
 * </ul>
 * 自动释放: 再次右键 / 主手离开机械手(含耐久耗尽碎裂、切到副手) / 饥饿归零 / 死亡、离线、换维度 /
 * 载具被拆 / 特性开关关闭 / 超出触及距离(BLOCK_INTERACTION_RANGE + 2 缓冲, 仿 Simulated 把手 validRange)。
 * 除玩家主动停止(仍持机械手, 保留动量可"甩投")外, 其余断开一律先抵消载具线/角速度(刹车,
 * 防拖拽途中积累的动量把载具弹飞)。饥饿归零时不允许建立新会话。
 *
 * <p>约束实现照搬 Simulated 创造手杖({@code PhysicsStaffServerHandler.DragSession}):
 * 每物理 tick 删旧约束重建 {@link FreeConstraintConfiguration}(bodyA=null 世界系, bodyB=载具),
 * 角轴目标 0 = 姿态焊在 orientation 系上; 线轴目标 = (客户端上传的玩家相对偏移 + 服务端插值眼位)
 * 变换到约束姿态系。区别: 创造手杖不限力, 本特性按载具质量限力(抓得动 3 倍自重, 防弹射)。
 *
 * <p>校验(服务端权威, 客户端不可信): 会话创建时校验 锚点方块属于该 sublevel 的 plot
 * (仿世界锚点 isInsideSubLevel) + 该方块是绳索连接器 + 眼位距离 ≤ 触及距离; 拖拽包校验
 * subLevel 一致 + 目标点有限且限长(触及+缓冲) + 四元数有限并归一化, 且锚点字段一律忽略
 * (以创建时校验值为准, 防挪锚点绕过触及校验)。攻击/锁定模式刻意不做(用户决策: 超模)。
 */
@EventBusSubscriber(modid = AeroSuiteIds.GRAVITY_ID)
public final class ExtendoGrabServer {

    /** FeatureGates 开关键(纯行为开关, 条目见 {@code AeroSuiteFeatures.ALL})。 */
    public static final String GATE_KEY = "extendo_grab";

    // 约束参数 = Simulated 创造手杖默认值(config/server/physics/SimPhysics)
    private static final double LINEAR_STIFFNESS = 2650;
    private static final double LINEAR_DAMPING = 125;
    private static final double ANGULAR_STIFFNESS = 10000;
    private static final double ANGULAR_DAMPING = 850;
    // 力上限(每约束轴): 载具自重 × 3 倍重力(Sable DimensionPhysics.DEFAULT_GRAVITY = -11 m/s²)。
    // 下限保证小件可用, 上限防求解器数值爆炸; 角轴同一标量(扭矩单位), 大载具旋转略迟缓属预期。
    private static final double MAX_FORCE_PER_MASS = 3.0 * 11.0;
    private static final double MIN_MAX_FORCE = 300;
    private static final double MAX_MAX_FORCE = 100000;
    // 抓取距离: clamp 下限(创造手杖同款); 释放阈值 = 触及距离 + 缓冲(仿把手 validRange, 防"刚好够不着"抖动脱手)
    private static final double MIN_GRAB_DISTANCE = 2.0;
    private static final double DISTANCE_BUFFER = 2.0;

    // 生存消耗(每秒; 三项数值均可在配置屏"数值特性相关"页调整, 见下方 hungerPerSecond 等读取器)
    private static final int COST_INTERVAL_TICKS = 20;
    /** 拖拽包失联超时: 客户端每 tick 发包, 断流 2 秒(40 tick)视为客户端已放弃 */
    private static final int PACKET_TIMEOUT_TICKS = 40;
    /** vanilla: 4 exhaustion = 1 点饥饿(先扣饱和) */
    private static final float EXHAUSTION_PER_HUNGER = 4.0f;
    /** 背罐基础空气容量(Create 默认 900; 扩容附魔只加容量不降价, 按比例延长可拖拽时长) */
    private static final int BASE_AIR = 900;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static volatile boolean sableHooked;
    /** create:extendo_grip 不走 AllItems(Registrate 类型不在编译类路径), 惰性注册表查找并缓存 */
    private static volatile Item extendoGripItem;

    private ExtendoGrabServer() {}

    /** mod1 入口构造期调用: 注册 Sable 物理 tick 钩子(幂等; 回调仅服务端触发)。 */
    public static void init() {
        if (sableHooked) return;
        sableHooked = true;
        SableEventPlatform.INSTANCE.onPhysicsTick((physicsSystem, timeStep) -> physicsTickAll(physicsSystem));
    }

    // ── 数值特性(配置屏"数值特性相关"页可调; CONFIG 未加载时回退默认值, 与 AeroSuiteConfig.Tunables 默认一致) ──

    /** 抓取时每秒消耗的饥饿值(点); 0 = 不消耗。 */
    public static float hungerPerSecond() {
        AeroSuiteConfig c = FeatureGates.CONFIG;
        return c != null ? c.tunables.extendoGrabHunger.getF() : AeroSuiteConfig.Tunables.HUNGER_DEFAULT;
    }

    /** 抓取时每秒消耗的机械手耐久; 0 = 不消耗。 */
    public static int durabilityPerSecond() {
        AeroSuiteConfig c = FeatureGates.CONFIG;
        return c != null ? c.tunables.extendoGrabDurability.get() : AeroSuiteConfig.Tunables.DURABILITY_DEFAULT;
    }

    /** 穿背罐时每秒代扣的背罐空气; usesPerTank = 基础容量/本值(canAbsorbDamage 每调用扣 基础容量/usesPerTank 气)。 */
    public static int airPerSecond() {
        AeroSuiteConfig c = FeatureGates.CONFIG;
        return c != null ? c.tunables.extendoGrabAir.get() : AeroSuiteConfig.Tunables.AIR_DEFAULT;
    }

    /**
     * 拖拽软度; 0 = 与创造物理手杖完全一致(全刚度 + 不限力, 彻底跟手), 调大则约束刚度按
     * 1/(1+S) 软化并启用力上限。手杖拖拽从不摇晃的根因即它 hasMaxForce=false 不限力 --
     * 限力约束在急转视角时需求力矩超过上限反复饱和/释放, 表现为载具剧烈摇晃。
     */
    public static float softness() {
        AeroSuiteConfig c = FeatureGates.CONFIG;
        return c != null ? c.tunables.extendoGrabSoftness.getF() : AeroSuiteConfig.Tunables.SOFTNESS_DEFAULT;
    }

    /** create:extendo_grip 判定(客户端/服务端共用; 缓存注册表查找结果)。 */
    public static boolean isHoldingGrip(Player player) {
        Item item = extendoGripItem;
        if (item == null) {
            item = BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("create", "extendo_grip"));
            extendoGripItem = item;
        }
        return player.getMainHandItem().is(item);
    }

    // ── payload 入口(payload 注册处主线程回调) ──────────────────

    public static void handleAction(ExtendoGrabActionPayload payload, ServerPlayer player) {
        if (!FeatureGates.isEnabled(GATE_KEY)) return;
        if (payload.start()) tryStart(payload, player);
        // 停止请求: 仍持机械手 = 玩家主动停止(保留动量); 否则是切物品等异常窗口(刹车)
        else release(player.getUUID(), !isHoldingGrip(player));
    }

    public static void handleDrag(ExtendoGrabDragPayload payload, ServerPlayer player) {
        if (!FeatureGates.isEnabled(GATE_KEY)) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !isHoldingGrip(player)) return;
        if (!session.subLevel.getUniqueId().equals(payload.subLevel())) return;

        // 客户端不可信, 逐包校验(合法客户端三种值恒在界内, 见 ExtendoGrabClient.clientTick/onMouseMove):
        // 目标点必须有限且长度 ≤ 触及+缓冲(与服务端脱手阈值同界); 姿态四元数必须有限且非零(可归一化)。
        // 非法包整体丢弃且不刷新 lastPacketTick -> 持续非法自然走断流超时脱手, 无法借此保活会话。
        Vector3dc goal = payload.playerRelativeGoal();
        Quaterniondc rawOrientation = payload.orientation();
        if (!isFinite(goal) || !isFinite(rawOrientation)) return;
        if (goal.length() > player.blockInteractionRange() + DISTANCE_BUFFER) return;
        Quaterniond orientation = new Quaterniond(rawOrientation);
        if (orientation.lengthSquared() < 1.0e-12) return;
        orientation.normalize();

        session.lastPacketTick = session.level.getGameTime();
        session.playerRelativeGoal.set(goal);
        // payload.localAnchor() 刻意忽略: 锚点以 tryStart 校验过的为准(合法客户端每次也原样回传),
        // 防止客户端每包挪锚点绕过物理 tick 的触及距离校验/改变约束焊点(字段仅为协议对齐而保留)
        session.orientation.set(orientation);
    }

    private static boolean isFinite(Vector3dc v) {
        return Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z());
    }

    private static boolean isFinite(Quaterniondc q) {
        return Double.isFinite(q.x()) && Double.isFinite(q.y())
                && Double.isFinite(q.z()) && Double.isFinite(q.w());
    }

    /** 建立会话: 全部条件通过才创建, 任何失败静默忽略(客户端等不到回执自然复位)。 */
    private static void tryStart(ExtendoGrabActionPayload payload, ServerPlayer player) {
        if (player.isSpectator()) return;
        if (player.getFoodData().getFoodLevel() <= 0) return;   // 饥饿归零禁抓(防抓一下即弹飞)
        // 一人一会话: 残留旧会话先释放(刹车, 同步 sync(false), 随后可能再 sync(true))
        release(player.getUUID(), true);
        if (!isHoldingGrip(player)) return;

        ServerLevel level = player.serverLevel();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        SubLevel subLevel = container.getSubLevel(payload.subLevel());
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) return;

        BlockPos anchorPos = payload.anchorPos();
        // 锚点方块必须属于该载具的 plot(WorldAnchorBlockEntity.isInsideSubLevel 同款判定)
        LevelPlot plot = container.getPlot(new ChunkPos(anchorPos));
        if (plot == null || plot.getSubLevel() != serverSubLevel) return;
        // 锚点方块必须是绳索连接器
        if (!(level.getBlockState(anchorPos).getBlock() instanceof RopeConnectorBlock)) return;
        // 禁止抓取玩家所站的载具(骑乘飞行漏洞: 站上去抓自己 = 目标点随人车反馈回路 = 沿视线无限飞行)
        if (isStandingOn(player, serverSubLevel)) return;

        Vector3d anchorLocal = JOMLConversion.atCenterOf(anchorPos);
        Vector3d anchorWorld = serverSubLevel.logicalPose().transformPosition(new Vector3d(anchorLocal));
        Vec3 eye = player.getEyePosition();
        double dist = anchorWorld.distance(eye.x, eye.y, eye.z);
        // 触及距离外不允许建立会话
        if (dist > player.blockInteractionRange()) return;
        double distance = Math.max(MIN_GRAB_DISTANCE, dist);

        Session session = new Session(player.getUUID(), level, serverSubLevel, anchorLocal,
                anchorWorld.sub(eye.x, eye.y, eye.z, new Vector3d()));
        SESSIONS.put(player.getUUID(), session);
        sendSync(player, true, distance);
        // 成就: 首次提起 / 提起 100 kpg 以上重物(mass 单位即 MassVisualizer 显示的 kpg)
        ModTriggers.EXTENDO_GRAB_FIRST_LIFT.get().trigger(player);
        if (serverSubLevel.getMassTracker().getMass() > 100.0) {
            ModTriggers.EXTENDO_GRAB_HEAVY_LIFT.get().trigger(player);
        }
    }

    /**
     * 玩家是否站在该载具上(骑乘飞行漏洞拦截)。
     * 坐标链: 玩家父世界坐标 --transformPositionInverse--> 载具本地坐标; 本地坐标值即 plotyard
     * 全局坐标, 可直接喂父 Level.getBlockState 读载具方块(与 tryStart 锚点校验同一条等价链;
     * {@code Sable.HELPER.getContaining} 系列吃 plotyard 全局坐标, 拿玩家普通世界坐标去查
     * 恒为 null, 不能用)。脚下 0.6 格内有非空气方块即视为踩在载具上(跳起掠过会被保守拦截)。
     */
    private static boolean isStandingOn(ServerPlayer player, ServerSubLevel subLevel) {
        Vector3d local = subLevel.logicalPose().transformPositionInverse(
                new Vector3d(player.getX(), player.getY(), player.getZ()));
        return !player.serverLevel().getBlockState(
                BlockPos.containing(local.x, local.y - 0.6, local.z)).isAir();
    }

    /**
     * 释放会话(约束移除 + 向该玩家回执 sync(false)); 无会话时静默。
     *
     * @param killMomentum true 时抵消载具线/角速度(刹车)。非自愿断开(饥饿/超时/超距/死亡/切物品)
     *                     必须刹车 -- 约束移除只解除拉力, 拖拽途中积累的动量会把载具弹飞;
     *                     玩家主动停止(仍持机械手)保留动量, 允许"甩投"载具。
     */
    private static void release(UUID playerUuid, boolean killMomentum) {
        Session session = SESSIONS.remove(playerUuid);
        if (session == null) return;
        session.removeConstraint();
        if (killMomentum) session.killMomentum();
        ServerPlayer player = session.level.getServer().getPlayerList().getPlayer(playerUuid);
        if (player != null) sendSync(player, false, 0);
    }

    private static void sendSync(ServerPlayer player, boolean dragging, double distance) {
        PacketDistributor.sendToPlayer(player, new ExtendoGrabSyncPayload(dragging, distance));
    }

    // ── 服务端游戏 tick: 会话有效性 + 每秒消耗 ──────────────────

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            Session session = entry.getValue();
            ServerPlayer player = session.level.getServer().getPlayerList().getPlayer(entry.getKey());
            long now = session.level.getGameTime();

            boolean release = player == null || player.isRemoved() || player.isDeadOrDying()
                    || player.serverLevel() != session.level          // 换维度
                    || !FeatureGates.isEnabled(GATE_KEY)
                    || !isHoldingGrip(player)                         // 切物品/副手/耐久耗尽碎裂
                    || session.subLevel.isRemoved()                   // 载具被拆
                    || isStandingOn(player, session.subLevel)         // 中途跳上载具(防骑乘飞行回路)
                    || player.getFoodData().getFoodLevel() <= 0       // 饥饿耗尽
                    || now - session.lastPacketTick > PACKET_TIMEOUT_TICKS;   // 客户端断流

            if (!release) {
                if (now - session.lastCostTick >= COST_INTERVAL_TICKS) {
                    session.lastCostTick = now;
                    float hunger = hungerPerSecond();
                    if (hunger > 0)
                        player.causeFoodExhaustion(hunger * EXHAUSTION_PER_HUNGER);   // 创造免疫(invulnerable)
                    // 背罐(含创造)代付 -> 不扣耐久; 否则扣机械手耐久(吃耐久附魔, 碎裂后下一 tick isHoldingGrip 释放)
                    if (!BacktankUtil.canAbsorbDamage(player, Math.max(BASE_AIR / airPerSecond(), 1))) {
                        int durability = durabilityPerSecond();
                        if (durability > 0) {
                            ItemStack grip = player.getMainHandItem();
                            grip.hurtAndBreak(durability, player, EquipmentSlot.MAINHAND);
                        }
                    }
                }
            }

            if (release) {
                it.remove();
                session.removeConstraint();
                session.killMomentum();   // 非自愿断开一律刹车, 防残余动量弹飞载具
                if (player != null) sendSync(player, false, 0);
            }
        }
    }

    // ── Sable 物理 tick: 每会话重建约束 + 距离校验 ───────────────

    private static void physicsTickAll(SubLevelPhysicsSystem physicsSystem) {
        if (SESSIONS.isEmpty()) return;
        ServerLevel level = physicsSystem.getLevel();
        for (Session session : List.copyOf(SESSIONS.values())) {
            if (session.level == level) session.physicsTick(physicsSystem);
        }
    }

    private static final class Session {
        private final UUID playerUuid;
        private final ServerLevel level;
        private final ServerSubLevel subLevel;
        private final Vector3d plotAnchor = new Vector3d();         // 抓取锚点(sublevel 本地, 块中心; 创建时校验定死, 拖拽包不更新)
        private final Vector3d playerRelativeGoal = new Vector3d(); // 客户端每 tick 上传
        private final Vector3d localGoal = new Vector3d();          // 每 tick 复用暂存
        private final Quaterniond orientation = new Quaterniond();  // 约束姿态系(= 创建时载具姿态, Tab 旋转时客户端改写)
        private final double maxForce;
        private long lastCostTick;
        private long lastPacketTick;
        @Nullable
        private PhysicsConstraintHandle constraint;

        private Session(UUID playerUuid, ServerLevel level, ServerSubLevel subLevel,
                        org.joml.Vector3dc anchorLocal, Vector3dc initialGoal) {
            this.playerUuid = playerUuid;
            this.level = level;
            this.subLevel = subLevel;
            this.plotAnchor.set(anchorLocal);
            // 初始目标 = 锚点(玩家相对偏移): 首个拖拽包下一 tick 才到, 若留 (0,0,0) 则首个
            // 物理 tick 约束目标 = 眼位, 不限力下载具朝玩家猛冲(双击快速释放即"沿视线弹飞")
            this.playerRelativeGoal.set(initialGoal);
            this.orientation.set(subLevel.logicalPose().orientation());
            this.maxForce = Mth.clamp(subLevel.getMassTracker().getMass() * MAX_FORCE_PER_MASS,
                    MIN_MAX_FORCE, MAX_MAX_FORCE);
            // 首个拖拽包到达前不算断流
            this.lastPacketTick = level.getGameTime();
        }

        private void physicsTick(SubLevelPhysicsSystem physicsSystem) {
            if (subLevel.isRemoved()) return;   // 服务端 tick 兜底释放
            Player player = subLevel.getLevel().getPlayerByUUID(playerUuid);
            if (player == null) return;

            // 每物理 tick 删旧约束重建(创造手杖同款; Sable 约束帧随位姿漂移, 重建保持目标精确)
            removeConstraint();
            attachConstraint(physicsSystem);
            if (constraint == null) return;

            // 软度 S(效果在 [0,1] 内铺开, >1 只是继续放慢弹簧):
            //  刚度/阻尼同乘 1/(1+9S), 保持阻尼比只放慢响应;
            //  力上限从 100 倍(≈ 不限力, 求解器实际不会饱和)连续收到 1 倍(3 倍自重)。
            // 两项都必须连续 -- 摇晃根因是限力饱和而非刚度: 旧实现 S>0 即启用 1 倍上限,
            // 0 -> 0.01 跨过"不限力/限力"断崖, 手感天差地别。
            double soft = Mth.clamp(softness(), 0.0, 1.0);
            double stiffScale = 1.0 / (1.0 + 9.0 * soft);
            double linearStiffness = LINEAR_STIFFNESS * stiffScale;
            double linearDamping = LINEAR_DAMPING * stiffScale;
            double angularStiffness = ANGULAR_STIFFNESS * stiffScale;
            double angularDamping = ANGULAR_DAMPING * stiffScale;
            boolean hasMaxForce = soft > 1.0e-3;
            double forceCap = hasMaxForce
                    ? Mth.clamp(maxForce * (1.0 + 99.0 * (1.0 - soft)), MIN_MAX_FORCE, MAX_MAX_FORCE)
                    : 0.0;

            // 角轴: 目标 0 + 高刚度 = 姿态焊在 orientation 系上(Tab 旋转改写 orientation)
            for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
                constraint.setMotor(axis, 0.0, angularStiffness, angularDamping, hasMaxForce, forceCap);
            }

            // 触及距离校验: 眼位(逐物理 tick 插值) ↔ 锚点当前世界坐标, 超出即脱手
            double partialTick = physicsSystem.getPartialPhysicsTick();
            double ex = Mth.lerp(partialTick, player.xOld, player.getX());
            double ey = Mth.lerp(partialTick, player.yOld, player.getY()) + player.getEyeHeight();
            double ez = Mth.lerp(partialTick, player.zOld, player.getZ());
            Vector3d anchorWorld = subLevel.logicalPose().transformPosition(new Vector3d(plotAnchor));
            double dx = anchorWorld.x - ex;
            double dy = anchorWorld.y - ey;
            double dz = anchorWorld.z - ez;
            double reach = player.blockInteractionRange() + DISTANCE_BUFFER;
            if (dx * dx + dy * dy + dz * dz > reach * reach) {
                release(playerUuid, true);
                return;
            }

            // 线轴目标 = (玩家相对偏移 + 插值眼位) 变换到约束姿态系(创造手杖同款)
            localGoal.set(playerRelativeGoal).add(ex, ey, ez);
            orientation.transformInverse(localGoal);
            constraint.setMotor(ConstraintJointAxis.LINEAR_X, localGoal.x(), linearStiffness, linearDamping, hasMaxForce, forceCap);
            constraint.setMotor(ConstraintJointAxis.LINEAR_Y, localGoal.y(), linearStiffness, linearDamping, hasMaxForce, forceCap);
            constraint.setMotor(ConstraintJointAxis.LINEAR_Z, localGoal.z(), linearStiffness, linearDamping, hasMaxForce, forceCap);
        }

        private void attachConstraint(SubLevelPhysicsSystem physicsSystem) {
            PhysicsPipeline pipeline = physicsSystem.getPipeline();
            constraint = pipeline.addConstraint(null, subLevel,
                    new FreeConstraintConfiguration(JOMLConversion.ZERO, plotAnchor, orientation));
        }

        private void removeConstraint() {
            if (constraint != null) {
                constraint.remove();
                constraint = null;
            }
        }

        /**
         * 刹车: 抵消当前线/角速度。RigidBodyHandle 无速度 setter, Sable 官方清零方式即
         * PhysicsPipeline.resetVelocity = 加"当前速度取反"(/sable 命令同款)。
         */
        private void killMomentum() {
            if (subLevel.isRemoved()) return;
            RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
            if (handle == null) return;
            handle.addLinearAndAngularVelocity(
                    handle.getLinearVelocity(new Vector3d()).negate(),
                    handle.getAngularVelocity(new Vector3d()).negate());
        }
    }

}
