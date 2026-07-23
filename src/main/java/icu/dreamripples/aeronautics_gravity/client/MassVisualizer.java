package icu.dreamripples.aeronautics_gravity.client;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.simulated_team.simulated.index.SimSpecialTextures;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
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
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3dc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
        double totalMass;
        long expiresAt;

        Visualization(ClientSubLevel subLevel, long expiresAt) {
            this.subLevel = subLevel;
            this.expiresAt = expiresAt;
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

    public static void toggle(ClientSubLevel subLevel) {
        UUID id = subLevel.getUniqueId();
        if (ACTIVE.containsKey(id)) {
            ACTIVE.remove(id);
        } else {
            ACTIVE.clear();
            activate(subLevel);
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

        // 用 RenderLevelStageEvent 的 partialTick,与 Catnip 的 Outliner.renderOutlines
        // (AFTER_PARTICLES 阶段) 用的是同一帧插值,避免半帧错位。
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        var outliner = Outliner.getInstance();
        var stage = event.getStage();

        for (var viz : ACTIVE.values()) {
            // 方块遮罩分组:必须在 AFTER_TRANSLUCENT_BLOCKS 调用,让 Catnip Outliner 在
            // AFTER_PARTICLES 阶段 renderOutlines 时能从队列里取到 cluster。
            if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                renderTypeGroups(viz, outliner, partialTick);
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

    private static void activate(ClientSubLevel cs) {
        Visualization viz = new Visualization(cs, System.currentTimeMillis() + DEFAULT_EXPIRE_MS);
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

        Map<BlockState, Double> typeMassSums = new IdentityHashMap<>();
        Map<BlockState, Integer> typeCounts = new IdentityHashMap<>();
        double totalM = 0;
        BlockPos.MutableBlockPos holdingPos = new BlockPos.MutableBlockPos();

        // 子级方块存储在 plot 的独立 chunk 中,不会注册到父级 ClientLevel 的 chunk map。
        // 若用 cs.getLevel() 作为 BlockGetter,VoxelNeighborhoodState.isSolid 会读到 air,
        // 导致 getMass 返回 0、方块被过滤。这里用子级 chunk 自建 BlockGetter。
        var bg = new SubLevelBlockGetter(cs);

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

    private static void renderTypeGroups(Visualization viz, Outliner outliner, float partialTick) {
        // 按颜色分组而非按 BlockState 分组,让相同颜色的方块合并进同一个 cluster。
        // 这样同色交界处的面由 BlockClusterOutline 的 MergeEntry Set 自动去重。
        Map<Integer, Set<BlockPos>> colorGroups = new LinkedHashMap<>();
        for (var entry : viz.typeGroups.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            double avgMass = viz.typeMasses.getOrDefault(entry.getKey(), 1.0);
            int color = massToColor(avgMass);
            colorGroups.computeIfAbsent(color, k -> new HashSet<>()).addAll(entry.getValue());
        }

        // key 带 subLevelId 前缀,避免多个 subLevel 的同色 cluster 互相覆盖。
        String keyPrefix = "aeronautics_gravity:" + viz.subLevel.getUniqueId() + ":";
        for (var entry : colorGroups.entrySet()) {
            int color = entry.getKey();
            Set<BlockPos> positions = entry.getValue();
            if (positions.isEmpty()) continue;

            // Sable 的 BlockClusterOutlineMixin.sable$projectFromSublevel 会遍历 BlockPos 找到
            // 对应的 ClientSubLevel 并应用 renderPose（旋转 + 平移），所以这里直接传子级本地 BlockPos。
            // 关闭边线(lineWidth 0):Catnip 的线走 outlineSolid() 会写深度,异色 cluster 边界
            // 处两条共面线 z-fight 闪烁。只保留 outlineTranslucent() 面填充(不写深度),
            // 边界处两层半透明面自然混合,稳定不闪。
            outliner.showCluster(keyPrefix + color, positions)
                    .colored(color)
                    .withFaceTexture(SimSpecialTextures.HONEY_GLUE)
                    .lineWidth(0);
        }
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

        Minecraft.getInstance().renderBuffers().bufferSource().endBatch(MassRenderTypes.centerOfMass());
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
