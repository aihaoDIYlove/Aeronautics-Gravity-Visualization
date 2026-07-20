package icu.dreamripples.aeronautics_gravity.client;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.simulated_team.simulated.index.SimSpecialTextures;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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

    private static class Visualization {
        final ClientSubLevel subLevel;
        final Map<BlockState, Set<BlockPos>> typeGroups;
        final Map<BlockState, Double> typeMasses;
        final double totalMass;
        final long expiresAt;

        Visualization(ClientSubLevel subLevel,
                      Map<BlockState, Set<BlockPos>> typeGroups,
                      Map<BlockState, Double> typeMasses,
                      double totalMass, long expiresAt) {
            this.subLevel = subLevel;
            this.typeGroups = typeGroups;
            this.typeMasses = typeMasses;
            this.totalMass = totalMass;
            this.expiresAt = expiresAt;
        }
    }

    private static final float COM_SIZE = 0.15f; // 重心球半径(原方块半边长 0.4,缩小后不挡周围方块)
    private static final int COM_COLOR = 0xE000BFFF; // 亮蓝色（深天蓝），穿透方块显示
    private static final float OVERLAY_LINE_WIDTH = 0.0625f;

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

    public static void clientTick() {
        ACTIVE.entrySet().removeIf(e -> e.getValue().expiresAt < System.currentTimeMillis());
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
        Map<BlockState, Set<BlockPos>> typeGroups = new LinkedHashMap<>();
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

                            typeGroups.computeIfAbsent(state, k -> new HashSet<>()).add(holdingPos.immutable());
                            typeMassSums.merge(state, mass, Double::sum);
                            typeCounts.merge(state, 1, Integer::sum);

                            totalM += mass;
                        }
                    }
                }
            }
        }

        Map<BlockState, Double> typeMasses = new IdentityHashMap<>();
        for (var entry : typeMassSums.entrySet()) {
            typeMasses.put(entry.getKey(), entry.getValue() / typeCounts.get(entry.getKey()));
        }

        ACTIVE.put(cs.getUniqueId(), new Visualization(
                cs, typeGroups, typeMasses, totalM,
                System.currentTimeMillis() + DEFAULT_EXPIRE_MS));
    }

    private static void renderTypeGroups(Visualization viz, Outliner outliner, float partialTick) {
        // 按颜色分组而非按 BlockState 分组,让相同颜色的方块合并进同一个 cluster。
        // 这样同色交界处的边由 BlockClusterOutline 的 MergeEntry Set 自动去重,不会 z-fight。
        // 异色交界处两个 cluster 的边框仍会重叠,但颜色不同视觉上是"双色边框"而非同色花斑闪烁。
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
            outliner.showCluster(keyPrefix + color, positions)
                    .colored(color)
                    .withFaceTexture(SimSpecialTextures.HONEY_GLUE)
                    .lineWidth(OVERLAY_LINE_WIDTH)
                    .disableLineNormals();
        }
    }

    private static void renderCenterOfMass(Visualization viz, RenderLevelStageEvent event, float partialTick) {
        if (viz.totalMass <= 0) return;
        Pose3dc pose = viz.subLevel.renderPose(partialTick);
        if (pose == null) return;

        // Sable 的物理 pipeline 保证 rotationPoint = centerOfMass,
        // 所以重心世界坐标 = pose.position()。
        Vector3dc position = pose.position();
        Vec3 camera = event.getCamera().getPosition();

        // 用自定义 RenderType（NO_DEPTH_TEST）画重心标记,使其穿透方块显示不被遮挡。
        // Outliner.showAABB 走 Catnip 默认 RenderType（有深度测试）,OutlineParams 无法禁用深度。
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(position.x() - camera.x, position.y() - camera.y, position.z() - camera.z);

        VertexConsumer buffer = Minecraft.getInstance().renderBuffers().bufferSource()
                .getBuffer(MassRenderTypes.centerOfMass());
        sphere(buffer, poseStack.last(), COM_SIZE, COM_COLOR);

        Minecraft.getInstance().renderBuffers().bufferSource().endBatch(MassRenderTypes.centerOfMass());
        poseStack.popPose();
    }

    private static void sphere(VertexConsumer vc, PoseStack.Pose pose, float radius, int color) {
        // UV sphere: 经度 segments 切片 × 纬度 rings 段。NO_CULL 下正反面都画,
        // 任意角度投影都是圆,圆心即几何中心,配平时肉眼判断是否居中更直观。
        int segments = 12;
        int rings = 8;
        // 预计算 (rings+1)×(segments+1) 顶点,相邻 quad 共用顶点避免重复。
        float[][][] v = new float[rings + 1][segments + 1][3];
        for (int i = 0; i <= rings; i++) {
            double theta = Math.PI * i / rings;          // 0..π, 北极到南极
            double sinT = Math.sin(theta);
            double cosT = Math.cos(theta);
            for (int j = 0; j <= segments; j++) {
                double phi = 2 * Math.PI * j / segments;  // 0..2π
                v[i][j][0] = (float) (radius * sinT * Math.cos(phi));
                v[i][j][1] = (float) (radius * cosT);
                v[i][j][2] = (float) (radius * sinT * Math.sin(phi));
            }
        }
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segments; j++) {
                quad(vc, pose, color,
                        v[i][j][0], v[i][j][1], v[i][j][2],
                        v[i][j + 1][0], v[i][j + 1][1], v[i][j + 1][2],
                        v[i + 1][j + 1][0], v[i + 1][j + 1][1], v[i + 1][j + 1][2],
                        v[i + 1][j][0], v[i + 1][j][1], v[i + 1][j][2]);
            }
        }
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
