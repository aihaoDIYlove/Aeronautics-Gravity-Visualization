package icu.dreamripples.aero_suite.gravity.client;

import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripRenderHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlock;
import dev.simulated_team.simulated.index.SimKeys;
import dev.simulated_team.simulated.util.SimColors;
import icu.dreamripples.aero_suite.common.config.FeatureGates;
import icu.dreamripples.aero_suite.gravity.extendo.ExtendoGrabServer;
import icu.dreamripples.aero_suite.starlight.network.ExtendoGrabActionPayload;
import icu.dreamripples.aero_suite.starlight.network.ExtendoGrabDragPayload;
import icu.dreamripples.aero_suite.starlight.network.ExtendoGrabSyncPayload;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Vector3dc;

/**
 * 机械手载具抓取 -- 客户端会话/输入/发包。
 *
 * <p>交互(点按切换, 与 Simulated 创造手杖一致): 主手持 {@code create:extendo_grip} 时 use 键
 * "新按压" = 切换(vanilla 按住右键每 4 tick 重触发 use, 只认新按压, 见 {@link #onUsePress});
 * 拖拽中任意右键 = 停止(吞掉本次交互); 未拖拽时命中载具方块 =
 * 乐观置位并发开始请求(不吞交互 -- vanilla 对绳索连接器本无操作, 其他载具方块交互照常,
 * 服务端校验不过则等不到回执自动复位)。
 *
 * <p>状态机: sessionSubLevel 置位(乐观) -> 服务端 sync(true, 距离) -> active=true 开始每 tick
 * 发 {@link ExtendoGrabDragPayload}; sync(false)/回执超时/本地任一释放条件 -> 复位。
 * 服务端为权威(校验绳索连接器/触及距离/消耗/切物品/死亡等), 客户端镜像校验只为省包。
 *
 * <p>姿态与距离: 拖拽中载具姿态与玩家视角**水平锁定**(yaw 跟随, 俯仰不随 -- "手持"语义, 与创造
 * 手杖的世界固定姿态不同), 按住 Simulated 创造手杖的姿态键({@code SimKeys.ROTATE_MODE}, 默认 Tab,
 * 不自建 KeyMapping -- 直接复用其键位, 在控制设置里随创造手杖一并改键)移动鼠标可叠加调整姿态,
 * 鼠标位移经 {@link #onMouseMove} 改写目标姿态并取消 vanilla 视角转动(mixin 注入, 仿 Simulated
 * 创造手杖同款数学)。按住 Tab 期间 vanilla 玩家列表会同显(创造手杖亦有此视觉副作用)。
 * 滚轮调拉伸远近(吞滚轮防切快捷栏, 创造手杖同款灵敏度曲线), 机械手伸出动画随载具实际距离伸缩
 * ({@link #keepGripExtended})。
 */
public final class ExtendoGrabClient {

    private static final double ROTATE_SENSITIVITY = 0.35;   // Simulated 创造手杖默认灵敏度
    /** 滚轮调距灵敏度 */
    private static final double SCROLL_SENSITIVITY = 0.5;
    private static final int ACK_TIMEOUT_TICKS = 40;         // 开始请求无回执的复位时限
    /** 与服务端 ExtendoGrabServer.DISTANCE_BUFFER 一致的客户端镜像释放阈值 */
    private static final double DISTANCE_BUFFER = 2.0;
    /** 与服务端 ExtendoGrabServer.MIN_GRAB_DISTANCE 一致的拉伸距离下限 */
    private static final double MIN_GRAB_DISTANCE = 2.0;

    static {
        // 服务端 S2C 回执 -> 客户端会话状态(payload 注册处只读此字段, 不引用客户端类, 专用服安全)
        ExtendoGrabSyncPayload.clientListener = ExtendoGrabClient::handleServerSync;
    }

    /** 乐观置位的抓取会话; 仅 active=true 时才算真正拖拽(服务端已确认)。 */
    private static SubLevel sessionSubLevel;
    private static Vector3dc sessionAnchor;   // sublevel 本地锚点(命中方块中心)
    private static final Quaterniond sessionOrientation = new Quaterniond();  // 约束姿态系(= 创建时载具姿态, Tab 旋转时客户端改写)
    private static float lastFollowYaw;   // 上一 tick 玩家 yaw(视角跟随: yaw 变化量反向锁进载具姿态)
    private static double sessionDistance;    // 服务端回执的权威拉伸距离
    private static boolean active;
    private static int awaitingAck;
    /** 上一客户端 tick 末 use 键是否按住(判别"新按压"与 vanilla 按住重触发) */
    private static boolean useWasDown;

    private ExtendoGrabClient() {}

    /**
     * use 键按压({@code InputEvent.InteractionKeyMappingTriggered}, use 输入对双手各发一次)。
     * 返回 true = 吞掉本次交互。
     */
    public static boolean onUsePress(InteractionHand hand) {
        // 副手事件: 主手按下瞬间会话已乐观置位, 吞掉避免同一次点击触发副手物品(如进食)
        if (hand == InteractionHand.OFF_HAND) return sessionSubLevel != null;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) return false;

        if (sessionSubLevel != null) {   // 已有会话: 新按压 = 切换停止; 按住重触发 = 仅吞掉
            if (!useWasDown) stop();
            return true;
        }
        // vanilla 按住右键每 4 tick 重触发 startUseItem(Minecraft.rightClickDelay 机制), 本事件对
        // 重触发同样发射 -- 切换型交互必须只认"新按压"(上一客户端 tick 末键未按住), 否则按住右键
        // 会 开始/停止 高频震荡, 载具鬼畜
        if (useWasDown) return false;
        if (!FeatureGates.isEnabled(ExtendoGrabServer.GATE_KEY)) return false;
        if (!ExtendoGrabServer.isHoldingGrip(player)) return false;
        if (player.getFoodData().getFoodLevel() <= 0) return false;   // 饥饿归零禁抓(防抓一下即弹飞)
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
        SubLevel subLevel = Sable.HELPER.getContainingClient(hit.getLocation());
        if (subLevel == null) return false;   // 非载具方块: 不干预(vanilla 交互照常)
        // 仅绳索连接器接管(客户端预校验, 服务端仍权威复核): 其余载具方块的交互完全不受影响,
        // 也避免误点后乐观置位造成短窗吞右键
        if (!isRopeConnectorAt(subLevel, hit.getBlockPos())) return false;
        // 镜像服务端"禁抓所站载具"(站上去抓自己 = 骑乘飞行回路), 服务端仍权威复核;
        // 拒绝时弹 actionbar 提示(点按是离散事件, 无刷屏问题)
        if (subLevel instanceof ClientSubLevel cs && isStandingOn(player, cs)) {
            player.displayClientMessage(
                    Component.translatable("aero_suite.extendo_grab.self_grab_denied")
                            .withStyle(ChatFormatting.GOLD),
                    true);
            return false;
        }

        // 乐观置位 + 发起请求; 绳索连接器/触及距离由服务端权威校验, 回执确认后才进入拖拽
        sessionSubLevel = subLevel;
        sessionAnchor = JOMLConversion.atCenterOf(hit.getBlockPos());
        sessionOrientation.set(subLevel.logicalPose().orientation());
        lastFollowYaw = player.getYRot();
        sessionDistance = 0;
        active = false;
        awaitingAck = ACK_TIMEOUT_TICKS;
        sendToServer(new ExtendoGrabActionPayload(true, subLevel.getUniqueId(), hit.getBlockPos()));
        return false;
    }

    /** 每客户端 tick(AeronauticsGravityClient.onClientTick): 发拖拽数据 + 镜像释放校验。 */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        // 先更新按压沿判别基准: 本 tick 末键态 = 下一 tick 事件里的"上一 tick 末键态"
        useWasDown = mc.options != null && mc.options.keyUse.isDown();
        if (sessionSubLevel == null) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            clear();
            return;
        }

        if (!active) {
            // 服务端未确认(非绳索连接器/超距/开关关/不持机械手), 超时复位
            if (awaitingAck > 0 && --awaitingAck == 0) clear();
            return;
        }

        boolean release = player.isDeadOrDying()
                || !ExtendoGrabServer.isHoldingGrip(player)
                || !FeatureGates.isEnabled(ExtendoGrabServer.GATE_KEY)
                || sessionSubLevel.isRemoved()
                || player.getFoodData().getFoodLevel() <= 0
                || distanceExceeded(player);
        if (release) {
            // 镜像释放只清本地不发包: 上面每一条服务端都有同一条件的权威检测(距离校验在物理 tick),
            // 由服务端统一刹车 + 回执 sync(false); 客户端若补发"停止"包反而会把刹车路径变成保留动量
            clear();
            return;
        }

        // 视角跟随("手持"语义, 与创造手杖的差异点): 玩家转向时把载具世界姿态前乘反向 yaw 旋转,
        // 锁定载具与玩家视角的相对水平角度(不跟俯仰 -- 像端着托盘, 低头东西不翻; Tab 旋转叠加其上)。
        // 玩家视角转 Δyaw 等价世界系 R_y(-Δyaw), 载具同乘即保持相对角; yaw 过 ±180 界时 Δ≈±360°
        // 自动等于恒等旋转, 无需特判。
        float yaw = player.getYRot();
        if (yaw != lastFollowYaw) {
            sessionOrientation.premul(new Quaterniond().rotationY(-Math.toRadians(yaw - lastFollowYaw)));
            lastFollowYaw = yaw;
        }

        // 目标点 = 视线方向 × 拉伸距离(玩家相对偏移; 服务端加回插值眼位) -- 创造手杖同款
        Vec3 goal = player.getLookAngle().scale(sessionDistance);
        sendToServer(new ExtendoGrabDragPayload(sessionSubLevel.getUniqueId(),
                JOMLConversion.toJOML(goal), sessionAnchor, sessionOrientation));

        // 抓取高亮: 复用 Simulated 绳索右键选点时的 Outliner 点框效果(同款绿色/线宽),
        // 松手后停止刷新, Outliner 自动淡出(8 tick)。
        // 喂给 Outliner 的必须是 sublevel 本地(plotyard)坐标, 而非预变换后的主世界坐标:
        // Sable 的 ChasingAABBOutlineMixin 在每帧渲染时拿框中心 getContainingClient 查到
        // sublevel 后 push renderPose() 绕相机重投影(= 渲染在子维度, 与载具同帧插值),
        // 绳索连物理鬼畜都跟得上的原因; 预变换成主世界坐标则 mixin 查不到 sublevel,
        // 只剩 20Hz tick 刷新 + chase 平滑, 必然不跟手。
        Outliner.getInstance().chaseAABB("extendo_grab_anchor",
                        new AABB(sessionAnchor.x(), sessionAnchor.y(), sessionAnchor.z(),
                                sessionAnchor.x(), sessionAnchor.y(), sessionAnchor.z()))
                .colored(new Color(SimColors.SUCCESS_LIME))
                .lineWidth(1 / 3f)
                .disableLineNormals();
    }

    /**
     * Entity.turn 注入点(EntityTurnMixin, 加性注入避免与 Simulated 在 turnPlayer 调用点的
     * @Inject 抢同一条指令): active 且按住姿态键时, 鼠标位移改写载具目标姿态并取消 vanilla
     * 视角转动。返回 true = 取消 vanilla 转动。
     */
    public static boolean onMouseMove(double yaw, double pitch) {
        // isPressed() 内部判 keybind null, Simulated 客户端未初始化时安全返回 false
        if (!active || sessionSubLevel == null || !SimKeys.ROTATE_MODE.isPressed()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Vec3 axis = mc.player.calculateViewVector(0.0f, mc.player.getYRot() - 90.0f);   // 玩家右侧水平轴
        sessionOrientation.rotateLocalY(Math.toRadians(yaw) * ROTATE_SENSITIVITY);      // 鼠标左右 = 绕本地 Y
        sessionOrientation.premul(new Quaterniond(new AxisAngle4d(
                Math.toRadians(-pitch) * ROTATE_SENSITIVITY, axis.x, axis.y, axis.z))); // 鼠标上下 = 绕玩家右轴
        return true;
    }

    /**
     * 滚轮调距(创造手杖同款): 拖拽确认后滚轮增减拉伸距离并吞掉滚轮(不切快捷栏)。
     * 灵敏度随当前距离缩放(clamp(√(dist/10), 1, 5), 近距微调远距粗调), 按住疾跑 ×4;
     * 距离限 [触及下限 2, 触及距离+缓冲](超出会被服务端脱手)。纯客户端状态: 服务端每 tick
     * 只消费客户端上传的目标点, 无需同步包。
     */
    public static boolean onMouseScroll(double deltaY) {
        if (!active || sessionSubLevel == null) return false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return false;
        double sensMultiplier = Mth.clamp(Math.sqrt(sessionDistance / 10.0), 1.0, 5.0)
                * (mc.options.keySprint.isDown() ? 4.0 : 1.0);
        sessionDistance = Mth.clamp(sessionDistance + deltaY * SCROLL_SENSITIVITY * sensMultiplier,
                MIN_GRAB_DISTANCE, player.blockInteractionRange() + DISTANCE_BUFFER);
        return true;
    }

    /**
     * 拖拽确认期间驱动 Create 伸缩机械手的伸出动画(攻击时置 0.95、每 tick 衰减 ×0.95)。
     * 须经 ClientTickEvent.Post 的 LOWEST 优先级调用(AeronauticsGravityClient.onClientTickLast),
     * 保证覆写发生在 Create 的 NORMAL 衰减之后, 否则渲染会看到逐 tick 呼吸。
     * 伸出程度随载具实际距离映射: [2, 触及+缓冲] -> 视觉伸缩 [0.05, 1.0]。注意 Create 渲染器
     * (ExtendoGripItemRenderer/RenderHandler)把动画值<b>立方</b>后才是视觉伸缩, 所以映射在视觉域
     * 做完再开立方回动画域——直接给动画域下限会立方归零(0.2^3≈0.008 ≈ 全缩, 实测踩坑)。
     * 滚轮调距/载具飞远时动画随之伸缩; 停止拖拽后不再覆写, 动画按 Create 原生衰减自然收回。
     */
    public static void keepGripExtended() {
        if (!active || sessionSubLevel == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !ExtendoGrabServer.isHoldingGrip(player)) return;
        double maxDist = player.blockInteractionRange() + DISTANCE_BUFFER;
        double t = Mth.clamp((anchorWorldDistance(player) - MIN_GRAB_DISTANCE) / (maxDist - MIN_GRAB_DISTANCE), 0.0, 1.0);
        float anim = (float) Math.cbrt(0.05 + 0.95 * t);
        ExtendoGripRenderHandler.mainHandAnimation = anim;
        ExtendoGripRenderHandler.lastMainHandAnimation = anim;
    }

    /** 服务端回执(S2C, 经 ExtendoGrabSyncPayload.clientListener 分发, 客户端主线程)。 */
    public static void handleServerSync(ExtendoGrabSyncPayload payload) {
        if (payload.dragging()) {
            if (sessionSubLevel != null) {
                active = true;
                sessionDistance = payload.distance();
                awaitingAck = 0;
            }
        } else {
            clear();
        }
    }

    /**
     * 载具本地(plotyard)坐标读方块: 客户端 plot chunk 不注册进父世界 chunk map
     * (MassVisualizer gotcha), 按已加载 chunk 逐个查; 未加载返回 null(按"不在载具上"处理, 与
     * 服务端未加载=空气的 fail-open 行为一致)。
     */
    private static BlockState subLevelBlockState(ClientSubLevel cs, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        for (var holder : cs.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            if (chunk != null && chunk.getPos().equals(chunkPos)) {
                return chunk.getBlockState(pos);
            }
        }
        return null;
    }

    /** 命中方块是否为绳索连接器(客户端预校验, 服务端仍权威复核)。 */
    private static boolean isRopeConnectorAt(SubLevel subLevel, BlockPos pos) {
        if (!(subLevel instanceof ClientSubLevel cs)) return false;
        BlockState state = subLevelBlockState(cs, pos);
        return state != null && state.getBlock() instanceof RopeConnectorBlock;
    }

    /**
     * 玩家是否站在该载具上(骑乘飞行漏洞拦截, 镜像服务端 isStandingOn):
     * 玩家父世界坐标逆变换到载具本地系, 脚下 0.6 格内有非空气方块即视为踩在载具上。
     */
    private static boolean isStandingOn(LocalPlayer player, ClientSubLevel cs) {
        org.joml.Vector3d local = cs.logicalPose().transformPositionInverse(
                new org.joml.Vector3d(player.getX(), player.getY(), player.getZ()));
        BlockState below = subLevelBlockState(cs, BlockPos.containing(local.x, local.y - 0.6, local.z));
        return below != null && !below.isAir();
    }

    /** 锚点当前世界坐标到玩家眼位的实际距离(镜像释放与伸出动画共用)。 */
    private static double anchorWorldDistance(LocalPlayer player) {
        org.joml.Vector3d anchorWorld = sessionSubLevel.logicalPose().transformPosition(
                new org.joml.Vector3d(sessionAnchor));
        Vec3 eye = player.getEyePosition();
        return Math.sqrt(eye.distanceToSqr(anchorWorld.x, anchorWorld.y, anchorWorld.z));
    }

    private static boolean distanceExceeded(LocalPlayer player) {
        double reach = player.blockInteractionRange() + DISTANCE_BUFFER;
        return anchorWorldDistance(player) > reach;
    }

    private static void stop() {
        if (sessionSubLevel != null) {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.send(new ExtendoGrabActionPayload(false, sessionSubLevel.getUniqueId(), BlockPos.ZERO));
            }
        }
        clear();
    }

    private static void clear() {
        sessionSubLevel = null;
        sessionAnchor = null;
        active = false;
        awaitingAck = 0;
    }

    private static void sendToServer(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.send(payload);
    }
}
