package icu.dreamripples.aeronautics_gravity.client;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockMaterial;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class MassVisualizer {

    private static final Map<UUID, Visualization> ACTIVE = new LinkedHashMap<>();
    private static final long DEFAULT_EXPIRE_MS = 6 * 60 * 1000;
    private static final int RESCAN_INTERVAL_TICKS = 10;

    private static class Visualization {
        final ClientSubLevel subLevel;
        final Map<BlockState, Set<BlockPos>> typeGroups = new LinkedHashMap<>();
        final Map<BlockState, Double> typeMasses = new IdentityHashMap<>();
        final Map<BlockState, Double> typeLifts = new IdentityHashMap<>(); // 单块标称浮力 = liftStrength * floatingScale;同类型同值,首次遇到时算一次
        double totalMass;
        long expiresAt;
        SubLevelBlockGetter blockGetter; // rescan 时建,渲染时查裸露面邻居
        boolean heavy; // true=重块模式(只 >=2 或有浮力,不剔除,穿透找重块/浮力块配平); false=全量模式(朝向玩家剔除,只画质量)

        Visualization(ClientSubLevel subLevel, long expiresAt, boolean heavy) {
            this.subLevel = subLevel;
            this.expiresAt = expiresAt;
            this.heavy = heavy;
        }
    }

    // 重心标记:3 轴十字准星,跟船本地轴旋转,NO_DEPTH_TEST 穿墙可见。
    // 臂长与臂粗都随相机距离缩放:<=近端用小值(近处精),>=远端用大值(远处仍可见),
    // 之间线性插值,让屏幕上的视大小大致恒定。
    private static final float COM_ARM_LENGTH_NEAR = 0.125f; // 距离<=COM_DIST_NEAR 时的单臂半长
    private static final float COM_ARM_LENGTH_FAR = 2.0f;    // 距离>=COM_DIST_FAR 时的单臂半长
    private static final float COM_ARM_HALF_NEAR = 0.0125f; // 距离<=COM_DIST_NEAR 时的臂半粗
    private static final float COM_ARM_HALF_FAR = 0.1f;      // 距离>=COM_DIST_FAR 时的臂半粗
    private static final double COM_DIST_NEAR = 4.0;          // 近端距离(格)
    private static final double COM_DIST_FAR = 64.0;          // 远端距离(格)
    private static final int COM_COLOR = 0xE000BFFF;         // 亮蓝,穿透方块显示

    // 质量数字:公告板,画在方块中心,只画表面方块(任一面接触空气),只画玩家半径内的方块。
    private static final double NUMBER_RADIUS = 16.0;        // 玩家半径(格),超出不画
    private static final double NUMBER_RADIUS_SQ = NUMBER_RADIUS * NUMBER_RADIUS;
    private static final float NUMBER_SCALE = 0.02f;          // 字号(scale 后 Y 取负翻转)
    private static final double HEAVY_MASS_THRESHOLD = 2.0;   // 重块模式阈值:>=2 才画(配平分析)
    private static final float LINE_OFFSET_PX = 6f;           // 双行时行偏移(像素,scale 前);±6px -> ±0.12格,约半字高,不重叠

    public static void toggle(ClientSubLevel subLevel, boolean heavy) {
        UUID id = subLevel.getUniqueId();
        Visualization existing = ACTIVE.get(id);
        if (existing != null) {
            if (existing.heavy == heavy) {
                ACTIVE.remove(id); // 同模式再点 -> 关
            } else {
                existing.heavy = heavy; // 异模式 -> 切换,复用已扫数据
            }
        } else {
            ACTIVE.clear();
            activate(subLevel, heavy);
        }
    }

    public static boolean hasActive() {
        return !ACTIVE.isEmpty();
    }

    private static int tickCounter = 0;

    public static void clientTick() {
        ACTIVE.entrySet().removeIf(e -> e.getValue().expiresAt < System.currentTimeMillis());
        // 每 RESCAN_INTERVAL_TICKS 重扫一次,让遮罩反映最新的方块摧毁/放置。
        // showCluster 每帧从 viz.typeGroups 重建 cluster mesh,就地改 Set 下一帧自动生效,无刷新闪烁。
        if (!ACTIVE.isEmpty() && (tickCounter++ % RESCAN_INTERVAL_TICKS) == 0) {
            for (var viz : ACTIVE.values()) rescan(viz);
        }
    }

    public static void renderOverlay(RenderLevelStageEvent event) {
        if (ACTIVE.isEmpty()) return;

        // 用 RenderLevelStageEvent 的 partialTick,与子级 renderPose 同帧插值,避免半帧错位。
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        var stage = event.getStage();

        for (var viz : ACTIVE.values()) {
            // 质量数字:在 AFTER_TRANSLUCENT_BLOCKS 画,走 Font + SEE_THROUGH(无深度测试),
            // 让中心数字穿透本体方块显示。代价是也会穿透其他方块,靠表面剔除+半径裁剪缓解。
            if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                renderMassNumbers(viz, event, partialTick);
            }
            // 重心标记:放在 AFTER_PARTICLES 阶段渲染。原因:
            // 1. NO_DEPTH_TEST 只让 COM 自己不被已有深度挡住,但不阻止后续绘制覆盖。
            //    Sable 子级渲染(FancySubLevelRenderDispatcher / Flywheel)通常在
            //    AFTER_TRANSLUCENT_BLOCKS 到 AFTER_PARTICLES 之间提交,会写颜色 + 深度,
            //    若 COM 在它们之前画,就会被结构像素直接覆盖。
            // 2. 不能用 AFTER_LEVEL:那个 stage 在 LevelRenderer.renderLevel() 末尾的
            //    popPose() 之后触发,PoseStack 已经弹出相机变换,只剩 identity。此时
            //    translate(pos - camera) 只做平移不做旋转,COM 会像 HUD 一样贴在屏幕上,
            //    视角背对结构也不消失。AFTER_PARTICLES 在 particles 渲染后触发,相机变换
            //    仍在 PoseStack 上,顶点能被正确变换到世界空间。
            // 3. 配合 @SubscribeEvent(priority = LOWEST) 确保本 subscriber 在同 stage 的
            //    Catnip Outliner.renderOutlines 和 Sable 子级 hook 之后执行,COM 真正最后画。
            if (stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                renderCenterOfMass(viz, event, partialTick);
            }
        }
    }

    private static void activate(ClientSubLevel cs, boolean heavy) {
        Visualization viz = new Visualization(cs, System.currentTimeMillis() + DEFAULT_EXPIRE_MS, heavy);
        ACTIVE.put(cs.getUniqueId(), viz);
        rescan(viz);
    }

    /**
     * 就地重建 viz 的质量分组。每 {@link #RESCAN_INTERVAL_TICKS} tick 调一次,
     * 让遮罩反映最新的方块摧毁/放置。
     */
    private static void rescan(Visualization viz) {
        ClientSubLevel cs = viz.subLevel;
        viz.typeGroups.clear();
        viz.typeMasses.clear();
        viz.typeLifts.clear();

        Map<BlockState, Double> typeMassSums = new IdentityHashMap<>();
        Map<BlockState, Integer> typeCounts = new IdentityHashMap<>();
        double totalM = 0;
        BlockPos.MutableBlockPos holdingPos = new BlockPos.MutableBlockPos();

        // 子级方块存储在 plot 的独立 chunk 中,不会注册到父级 ClientLevel 的 chunk map。
        // 若用 cs.getLevel() 作为 BlockGetter,VoxelNeighborhoodState.isSolid 会读到 air,
        // 导致 getMass 返回 0、方块被过滤。这里用子级 chunk 自建 BlockGetter。
        var bg = new SubLevelBlockGetter(cs);
        viz.blockGetter = bg;

        for (var holder : cs.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            if (chunk == null || chunk.isEmpty()) continue;

            ChunkPos chunkPos = chunk.getPos();
            int chunkMinX = chunkPos.getMinBlockX();
            int chunkMinZ = chunkPos.getMinBlockZ();
            LevelChunkSection[] sections = chunk.getSections();

            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section == null || section.hasOnlyAir()) continue;

                int sectionMinY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
                for (int localX = 0; localX < 16; localX++) {
                    for (int localY = 0; localY < 16; localY++) {
                        for (int localZ = 0; localZ < 16; localZ++) {
                            BlockState state = section.getBlockState(localX, localY, localZ);
                            if (state.isAir()) continue;

                            int x = chunkMinX + localX;
                            int y = sectionMinY + localY;
                            int z = chunkMinZ + localZ;
                            holdingPos.set(x, y, z);

                            double mass = PhysicsBlockPropertyHelper.getMass(bg, holdingPos, state);
                            if (mass <= 0) continue;

                            viz.typeGroups.computeIfAbsent(state, k -> new HashSet<>()).add(holdingPos.immutable());
                            typeMassSums.merge(state, mass, Double::sum);
                            typeCounts.merge(state, 1, Integer::sum);

                            totalM += mass;

                            // 浮力:同 BlockState 同值(来自 floating_material override),首次遇到算一次。
                            // 标称浮力 = liftStrength * floatingScale。getFloatingMaterial/getFloatingScale 只读
                            // state 属性,不涉及 isSolid,所以不依赖 bg(与 getMass 不同)。
                            if (!viz.typeLifts.containsKey(state)) {
                                FloatingBlockMaterial mat = PhysicsBlockPropertyHelper.getFloatingMaterial(state);
                                viz.typeLifts.put(state, mat == null ? 0.0 : mat.liftStrength() * PhysicsBlockPropertyHelper.getFloatingScale(state));
                            }
                        }
                    }
                }
            }
        }

        for (var entry : typeMassSums.entrySet()) {
            viz.typeMasses.put(entry.getKey(), entry.getValue() / typeCounts.get(entry.getKey()));
        }
        viz.totalMass = totalM;
    }

    private static void renderMassNumbers(Visualization viz, RenderLevelStageEvent event, float partialTick) {
        if (viz.blockGetter == null) return;
        Pose3dc pose = viz.subLevel.renderPose(partialTick);
        if (pose == null) return;

        Vector3dc position = pose.position();
        Quaterniondc orientation = pose.orientation();
        Vector3dc rotationPoint = pose.rotationPoint();
        Vec3 camera = event.getCamera().getPosition();

        // localPlayer = R^-1 * (camera - position) + rotationPoint。旋转保长,本地距离=世界距离,
        // 逐方块只做平方距离比较,无需逐块旋转。position/orientation/rotationPoint 都取自 renderPose。
        Vector3d localPlayer = new Vector3d(camera.x, camera.y, camera.z).sub(position);
        orientation.transformInverse(localPlayer);
        localPlayer.add(rotationPoint);

        Font font = Minecraft.getInstance().font;
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        // 公告板:cameraOrientation = R_cam^-1,mulPose 后文字始终正对相机。
        // 参考 SmartBlockEntityRenderer.renderNameplateOnHover 的 billboard 写法。
        Quaternionf billboard = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        int light = LightTexture.FULL_BRIGHT;
        PoseStack poseStack = event.getPoseStack();
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
        Vector3d localFace = new Vector3d();
        Vector3d worldFace = new Vector3d();

        for (var entry : viz.typeGroups.entrySet()) {
            BlockState state = entry.getKey();
            Double massBoxed = viz.typeMasses.get(state);
            if (massBoxed == null) continue;
            double mass = massBoxed;
            double lift = viz.typeLifts.getOrDefault(state, 0.0);
            // 普通模式:只画质量(照旧)。重块模式:画质量(mass>=2) 和/或 浮力(lift>0)。
            // 重块模式过滤放宽:既不重(mass<2)又无浮力(lift<=0)的轻普通块才跳过--
            // 否则 counterweight_light(mass=1) 这类纯浮力块会被 mass<2 踢掉,看不到浮力。
            boolean drawMass = !viz.heavy || mass >= HEAVY_MASS_THRESHOLD;
            boolean drawLift = viz.heavy && lift > 0;
            if (!drawMass && !drawLift) continue;
            // drawInBatch 的 color 是 ARGB,alpha 会被尊重(不像 Catnip 的 colored(int)),这里强制不透明。
            int massColor = massToColor(mass) | 0xFF000000;
            int liftColor = liftToColor(lift) | 0xFF000000;
            String massText = drawMass ? formatMass(mass) : null;
            String liftText = drawLift ? formatLift(lift) : null;
            float massTextX = massText != null ? -font.width(massText) / 2f : 0f;
            float liftTextX = liftText != null ? -font.width(liftText) / 2f : 0f;
            // 双行时浮力在上、质量在下。scale 是 (s,-s,s),drawInBatch 传 y=+k 经 scale 后世界 y=-k*s(向下),
            // y=-k -> 世界 y=+k*s(向上)。所以质量行 y=+OFFSET(下),浮力行 y=-OFFSET(上)。
            boolean both = drawMass && drawLift;
            float massY = both ? LINE_OFFSET_PX : 0f;
            float liftY = both ? -LINE_OFFSET_PX : 0f;

            for (BlockPos pos : entry.getValue()) {
                // 半径裁剪(本地空间)
                double dx = pos.getX() + 0.5 - localPlayer.x();
                double dy = pos.getY() + 0.5 - localPlayer.y();
                double dz = pos.getZ() + 0.5 - localPlayer.z();
                if (dx * dx + dy * dy + dz * dz > NUMBER_RADIUS_SQ) continue;

                // 全量模式:朝向玩家的面(每轴按分量正负选一个,最多3面)任一 air 才画,
                // 砍背面缓解花眼。重块模式跳过剔除--重块稀疏不花眼,且要穿透找到所有重块配平。
                if (!viz.heavy) {
                    // 主方向轴 DDA 遮挡剔除:沿主方向(分量接近最大值的轴)从方块向玩家逐格步进,
                    // 遇任何实心方块即遮挡。比"只查第一格"强:能剔除 U 形墙空气腔后的第二面墙
                    // (第一格是空气腔 air,但第二格才是遮挡墙)。
                    // 并列主轴(45 度棱)都做 DDA,任一全 air 就画,避免棱误杀。
                    double ddx = localPlayer.x() - (pos.getX() + 0.5);
                    double ddy = localPlayer.y() - (pos.getY() + 0.5);
                    double ddz = localPlayer.z() - (pos.getZ() + 0.5);
                    double adx = Math.abs(ddx), ady = Math.abs(ddy), adz = Math.abs(ddz);
                    double maxComp = Math.max(adx, Math.max(ady, adz));
                    boolean exposed = false;
                    if (adx >= maxComp - 0.1)
                        exposed = isAxisClear(viz.blockGetter, neighborPos, pos.getX(), pos.getY(), pos.getZ(),
                                ddx >= 0 ? 1 : -1, 0, 0, (int) adx);
                    if (!exposed && ady >= maxComp - 0.1)
                        exposed = isAxisClear(viz.blockGetter, neighborPos, pos.getX(), pos.getY(), pos.getZ(),
                                0, ddy >= 0 ? 1 : -1, 0, (int) ady);
                    if (!exposed && adz >= maxComp - 0.1)
                        exposed = isAxisClear(viz.blockGetter, neighborPos, pos.getX(), pos.getY(), pos.getZ(),
                                0, 0, ddz >= 0 ? 1 : -1, (int) adz);
                    if (!exposed) continue;
                }

                // 数字画在方块中心(本地),用 SEE_THROUGH 穿透本体方块显示。
                // 为何必须 SEE_THROUGH:MC 深度缓冲全局共享,不透明方块已写满深度,
                // 中心位置深度比表面远,NORMAL 深度测试会被自身方块面深度剔除 -> 完全看不到。
                // 代价:SEE_THROUGH 无深度测试,也会穿透其他方块。无法"只穿透本体"--
                // 那需要自定义 framebuffer 多 pass,在 MC 管线内极 hacky。靠表面剔除+半径裁剪缓解。
                localFace.set(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                // 世界坐标: worldFace = R * (localFace - rotationPoint) + position
                // 与 COM 准星同一套变换,绕开 posePlotToProjected 的 +R*cam 坑。
                worldFace.set(localFace).sub(rotationPoint);
                orientation.transform(worldFace);
                worldFace.add(position);

                poseStack.pushPose();
                poseStack.translate(worldFace.x - camera.x, worldFace.y - camera.y, worldFace.z - camera.z);
                poseStack.mulPose(billboard);
                poseStack.scale(NUMBER_SCALE, -NUMBER_SCALE, NUMBER_SCALE);
                Matrix4f matrix = poseStack.last().pose();
                // 同一 matrix 下先画浮力(上)再画质量(下),避免重复 translate/scale。
                if (drawLift) {
                    font.drawInBatch(liftText, liftTextX, liftY, liftColor, false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, 0, light);
                }
                if (drawMass) {
                    font.drawInBatch(massText, massTextX, massY, massColor, false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, 0, light);
                }
                poseStack.popPose();
            }
        }
    }

    private static String formatMass(double mass) {
        return mass < 1.0
                ? String.format(Locale.ROOT, "%.2f", mass)
                : String.format(Locale.ROOT, "%.1f", mass);
    }

    private static String formatLift(double lift) {
        return lift < 1.0
                ? String.format(Locale.ROOT, "%.2f", lift)
                : String.format(Locale.ROOT, "%.1f", lift);
    }

    /**
     * 沿 (stepX,stepY,stepZ) 方向从 (x,y,z)+step 起逐格步进 maxSteps 格,
     * 遇非 air 方块返回 false(路径被遮挡)。用于主方向轴 DDA 遮挡剔除。
     */
    private static boolean isAxisClear(BlockGetter bg, BlockPos.MutableBlockPos pos, int x, int y, int z,
                                       int stepX, int stepY, int stepZ, int maxSteps) {
        for (int i = 0; i < maxSteps; i++) {
            x += stepX; y += stepY; z += stepZ;
            pos.set(x, y, z);
            if (!bg.getBlockState(pos).isAir()) return false;
        }
        return true;
    }

    private static void renderCenterOfMass(Visualization viz, RenderLevelStageEvent event, float partialTick) {
        if (viz.totalMass <= 0) return;
        Pose3dc pose = viz.subLevel.renderPose(partialTick);
        if (pose == null) return;

        // Sable 的物理 pipeline 保证 rotationPoint = centerOfMass,
        // 所以重心世界坐标 = pose.position()。
        Vector3dc position = pose.position();
        Quaterniondc orientation = pose.orientation();
        Vec3 camera = event.getCamera().getPosition();

        // 用自定义 RenderType（NO_DEPTH_TEST）画重心标记,使其穿透方块显示不被遮挡。
        // Outliner.showAABB 走 Catnip 默认 RenderType（有深度测试）,OutlineParams 无法禁用深度。
        // 先 flush 所有 pending(含 AFTER_TRANSLUCENT_BLOCKS 写入的 textSeeThrough 数字顶点):
        // 让数字在 AFTER_PARTICLES 画(在半透明地形/particles 之后,穿墙可见),再画 COM,
        // 使 COM 盖在数字之上。否则 text 拖到 vanilla final endBatch 才画,会盖住先画的 COM,
        // 导致 COM 看似不穿墙。NO_DEPTH_TEST 内容之间,画图顺序决定谁盖谁--COM 必须最后画。
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(position.x() - camera.x, position.y() - camera.y, position.z() - camera.z);
        // 跟船本地轴旋转:准星随子级 orientation 转,顺带当朝向指示器。位置用 pose.position()
        // (世界 COM),旋转用 orientation,完全不依赖未同步的 rotationPoint。
        poseStack.mulPose(new Quaternionf(orientation));

        // 臂长与臂粗都按相机到 COM 的世界距离缩放:position 和 camera 都是世界坐标,
        // 直接欧氏距离即可,不涉及子级/本地坐标转换。近端小远端大,屏幕视大小大致恒定。
        double dist = Math.sqrt(position.distanceSquared(camera.x, camera.y, camera.z));
        double t = (dist - COM_DIST_NEAR) / (COM_DIST_FAR - COM_DIST_NEAR);
        float l = (float) Mth.clampedLerp(COM_ARM_LENGTH_NEAR, COM_ARM_LENGTH_FAR, t);
        float h = (float) Mth.clampedLerp(COM_ARM_HALF_NEAR, COM_ARM_HALF_FAR, t);

        VertexConsumer buffer = Minecraft.getInstance().renderBuffers().bufferSource()
                .getBuffer(MassRenderTypes.centerOfMass());
        // 3 轴十字:三段细方体沿 X/Y/Z 过原点,交点即 COM。
        box(buffer, poseStack.last(), -l, -h, -h, l, h, h, COM_COLOR); // X
        box(buffer, poseStack.last(), -h, -l, -h, h, l, h, COM_COLOR); // Y
        box(buffer, poseStack.last(), -h, -h, -l, h, h, l, COM_COLOR); // Z

        // 不主动 endBatch(centerOfMass):让 COM 顶点随 vanilla final endBatch 最晚画,
        // 使 COM 成为最后画的世界空间内容(NO_DEPTH_TEST 内容最晚画才不被后续覆盖/遮挡)。
        // 顶点 Matrix4f 是 AFTER_PARTICLES poseStack 的快照(含相机世界变换),
        // final endBatch 时 modelview 已 popPose 为 identity,projection*poseMatrix 仍正确。
        poseStack.popPose();
    }

    /** 画一个轴对齐方体(6 面,NO_CULL 下正反面都可见)。用于构成十字准星的三段臂。 */
    private static void box(VertexConsumer vc, PoseStack.Pose pose,
                            float x0, float y0, float z0,
                            float x1, float y1, float z1, int color) {
        quad(vc, pose, color,
                x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1); // 下 (y0)
        quad(vc, pose, color,
                x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0); // 上 (y1)
        quad(vc, pose, color,
                x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0); // -Z (z0)
        quad(vc, pose, color,
                x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1); // +Z (z1)
        quad(vc, pose, color,
                x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0); // -X (x0)
        quad(vc, pose, color,
                x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1); // +X (x1)
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, int color,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3) {
        vertex(vc, pose, x0, y0, z0, color);
        vertex(vc, pose, x1, y1, z1, color);
        vertex(vc, pose, x2, y2, z2, color);
        vertex(vc, pose, x3, y3, z3, color);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose,
                               float x, float y, float z, int color) {
        vc.addVertex(pose, x, y, z).setColor(color);
    }

    private static int massToColor(double mass) {
        // 颜色用 SimColors 风格的亮色,保证和蜂蜜胶视觉风格一致
        if (mass <= 0) return 0x40808080;
        if (mass <= 0.25) return 0x804CAF50;
        if (mass <= 0.5) return 0x808BC34A;
        if (mass <= 1.0) return 0x80FFEB3B;
        if (mass <= 2.0) return 0x80FF9800;
        if (mass <= 4.0) return 0x80F44336;
        return 0x80B71C1C;
    }

    private static int liftToColor(double lift) {
        // 青色系 = "升力"语义,与 RedstoneCounterweightLightVisual 配轻灯带同色系(暗青->亮青)。
        // 范围:手调配轻 1..36, 红石配轻/stabilizer lift 1..16。分级让强度差异可读。
        if (lift <= 0) return 0x40013A3A;
        if (lift <= 8) return 0xFF013A3A;   // 暗青
        if (lift <= 16) return 0xFF008B8B;  // 中青
        if (lift <= 24) return 0xFF00CDCD;  // 亮青
        return 0xFF00FFFF;                  // 极亮青
    }

    /**
     * 通过子级 plot 的 chunk 访问方块的 BlockGetter。
     * 子级 LevelChunk 是 newEmptyChunk 用父级 Level 独立创建的,不会注册到父级
     * ClientLevel 的 chunk map,所以 cs.getLevel().getBlockState(pos) 在存储区
     * 位置读到的是 air,会让 PhysicsBlockPropertyHelper.getMass 内部的
     * VoxelNeighborhoodState.isSolid 返回 false,从而方块被 mass<=0 过滤。
     * 这里把 plot.getLoadedChunks() 缓存成 chunk map,getBlockState 直接查子级 chunk。
     */
    private static final class SubLevelBlockGetter implements BlockGetter {
        private final Map<Long, LevelChunk> chunks = new HashMap<>();
        private final int minHeight;
        private final int height;

        SubLevelBlockGetter(ClientSubLevel cs) {
            var level = cs.getLevel();
            this.minHeight = level.getMinBuildHeight();
            this.height = level.getHeight();
            for (var holder : cs.getPlot().getLoadedChunks()) {
                LevelChunk chunk = holder.getChunk();
                if (chunk == null) continue;
                ChunkPos p = chunk.getPos();
                chunks.put(ChunkPos.asLong(p.x, p.z), chunk);
            }
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            LevelChunk chunk = chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            LevelChunk chunk = chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            return chunk == null ? null : chunk.getBlockEntity(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            LevelChunk chunk = chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            return chunk == null ? Fluids.EMPTY.defaultFluidState() : chunk.getFluidState(pos);
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public int getMinBuildHeight() {
            return minHeight;
        }
    }
}
