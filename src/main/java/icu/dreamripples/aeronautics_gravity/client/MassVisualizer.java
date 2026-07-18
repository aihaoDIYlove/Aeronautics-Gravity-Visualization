package icu.dreamripples.aeronautics_gravity.client;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.simulated_team.simulated.index.SimSpecialTextures;
import dev.simulated_team.simulated.util.SimColors;
import net.createmod.catnip.outliner.Outliner;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

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
        final Vec3 localCom;
        final double totalMass;
        final long expiresAt;

        Visualization(ClientSubLevel subLevel,
                      Map<BlockState, Set<BlockPos>> typeGroups,
                      Map<BlockState, Double> typeMasses,
                      Vec3 localCom, double totalMass, long expiresAt) {
            this.subLevel = subLevel;
            this.typeGroups = typeGroups;
            this.typeMasses = typeMasses;
            this.localCom = localCom;
            this.totalMass = totalMass;
            this.expiresAt = expiresAt;
        }
    }

    private static final float COM_SIZE = 0.4f;
    private static final float BLOCK_OVERLAY_INFLATE = 0.005f;
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

        for (var viz : ACTIVE.values()) {
            renderTypeGroups(viz, outliner, partialTick);
            renderCenterOfMass(viz, outliner, partialTick);
        }
    }

    private static void activate(ClientSubLevel cs) {
        Map<BlockState, Set<BlockPos>> typeGroups = new LinkedHashMap<>();
        Map<BlockState, Double> typeMassSums = new IdentityHashMap<>();
        Map<BlockState, Integer> typeCounts = new IdentityHashMap<>();

        double totalM = 0, hcx = 0, hcy = 0, hcz = 0;
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
                            hcx += mass * (x + 0.5);
                            hcy += mass * (y + 0.5);
                            hcz += mass * (z + 0.5);
                        }
                    }
                }
            }
        }

        Map<BlockState, Double> typeMasses = new IdentityHashMap<>();
        for (var entry : typeMassSums.entrySet()) {
            typeMasses.put(entry.getKey(), entry.getValue() / typeCounts.get(entry.getKey()));
        }

        Vec3 localCom = totalM > 0
                ? new Vec3(hcx / totalM, hcy / totalM, hcz / totalM)
                : Vec3.ZERO;

        ACTIVE.put(cs.getUniqueId(), new Visualization(
                cs, typeGroups, typeMasses, localCom, totalM,
                System.currentTimeMillis() + DEFAULT_EXPIRE_MS));
    }

    private static void renderTypeGroups(Visualization viz, Outliner outliner, float partialTick) {
        Pose3dc pose = viz.subLevel.renderPose(partialTick);
        if (pose == null) return;

        Quaterniondc orient = pose.orientation();
        Vector3dc position = pose.position();
        Vec3 com = viz.localCom;

        for (var entry : viz.typeGroups.entrySet()) {
            BlockState state = entry.getKey();
            Set<BlockPos> positions = entry.getValue();
            if (positions.isEmpty()) continue;

            double avgMass = viz.typeMasses.getOrDefault(state, 1.0);
            int color = massToColor(avgMass);

            for (BlockPos pos : positions) {
                Vec3 worldCenter = transformBlockCenter(pos, com, orient, position);
                AABB aabb = new AABB(
                        worldCenter.x - 0.5 - BLOCK_OVERLAY_INFLATE,
                        worldCenter.y - 0.5 - BLOCK_OVERLAY_INFLATE,
                        worldCenter.z - 0.5 - BLOCK_OVERLAY_INFLATE,
                        worldCenter.x + 0.5 + BLOCK_OVERLAY_INFLATE,
                        worldCenter.y + 0.5 + BLOCK_OVERLAY_INFLATE,
                        worldCenter.z + 0.5 + BLOCK_OVERLAY_INFLATE);

                // key 用存储区 BlockPos(稳定不变),每帧覆盖同一 entry,避免 outlines map 堆积
                outliner.showAABB(pos, aabb)
                        .colored(color)
                        .withFaceTexture(SimSpecialTextures.HONEY_GLUE)
                        .lineWidth(OVERLAY_LINE_WIDTH)
                        .disableLineNormals();
            }
        }
    }

    private static void renderCenterOfMass(Visualization viz, Outliner outliner, float partialTick) {
        if (viz.totalMass <= 0) return;
        Pose3dc pose = viz.subLevel.renderPose(partialTick);
        if (pose == null) return;

        // transformPosition(localCom) = orientation.transform(localCom - rotationPoint) + position
        // Sable 的物理 pipeline 保证 rotationPoint = centerOfMass,所以
        // transformPosition(localCom) = position,即重心世界坐标 = pose.position()
        Vector3dc position = pose.position();
        AABB aabb = new AABB(
                position.x() - COM_SIZE,
                position.y() - COM_SIZE,
                position.z() - COM_SIZE,
                position.x() + COM_SIZE,
                position.y() + COM_SIZE,
                position.z() + COM_SIZE);

        outliner.showAABB("aeronautics_gravity:com_" + viz.subLevel.getUniqueId(), aabb)
                .colored(SimColors.ACTIVE_YELLOW)
                .withFaceTexture(SimSpecialTextures.HONEY_GLUE)
                .lineWidth(OVERLAY_LINE_WIDTH * 1.5f)
                .disableLineNormals();
    }

    /**
     * 手动计算方块中心在载具当前位置的世界坐标。
     *
     * 不使用 pose.transformPosition(local),因为客户端的 renderPose.rotationPoint
     * 可能未被服务器同步(默认 (0,0,0)),会导致
     *   transformPosition(local) = orientation.transform(local - 0) + position
     * 即整个存储区坐标被旋转后加到 position 上,遮罩被甩到很远的地方(空气中)。
     *
     * 正确变换(当 rotationPoint = centerOfMass 时):
     *   world = orientation.transform(local - centerOfMass) + position
     *
     * 这里手动减去 localCom(= centerOfMass),绕过 rotationPoint 字段。
     */
    private static Vec3 transformBlockCenter(BlockPos pos, Vec3 localCom,
                                             Quaterniondc orient, Vector3dc position) {
        double ox = pos.getX() + 0.5 - localCom.x;
        double oy = pos.getY() + 0.5 - localCom.y;
        double oz = pos.getZ() + 0.5 - localCom.z;

        Vector3d rotated = orient.transform(new Vector3d(ox, oy, oz));

        return new Vec3(
                rotated.x + position.x(),
                rotated.y + position.y(),
                rotated.z + position.z());
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
