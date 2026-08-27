package icu.dreamripples.aero_suite.starlight.block;

import com.simibubi.create.content.decoration.encasing.CasingBlock;
import icu.dreamripples.aero_suite.common.config.FeatureGates;
import icu.dreamripples.aero_suite.starlight.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 星空机壳: 继承 Create CasingBlock(铜机壳改色 + CT + 可套管道),新增星光闪烁粒子。
 *
 * 粒子机制仿 vanilla MyceliumBlock.animateTick: 客户端每 tick 对玩家周围可视随机区块的
 * 每个方块随机调用一次,nextInt(10)==0 时在随机面上冒一个 STARLIGHT_SPARKLE。
 * 只用 common API(ParticleOptions/addParticle),client-only 的粒子类由注册的 Provider 创建。
 */
public class StarlightCasingBlock extends CasingBlock {

    public StarlightCasingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(10) != 0) return; // 每方块约每秒 2 次,与 mycelium 同密度
        if (!FeatureGates.isEnabled("starlight_casing")) return; // 门控关闭,世界内残留方块不再冒粒子
        // 六个面等概率;被相邻方块贴住的面用与区块渲染同一套剔除逻辑跳过
        Direction face = Direction.getRandom(random);
        if (!Block.shouldRenderFace(state, level, pos, face, pos.relative(face))) return;
        // 在面内随机取点(略外扩 0.05 形成贴面光点而非嵌进方块里)
        double x = pos.getX() + 0.5 + (face.getStepX() * (0.5 + 0.05)) + jitter(random, face.getStepX());
        double y = pos.getY() + 0.5 + (face.getStepY() * (0.5 + 0.05)) + jitter(random, face.getStepY());
        double z = pos.getZ() + 0.5 + (face.getStepZ() * (0.5 + 0.05)) + jitter(random, face.getStepZ());
        addSparkle(ModParticles.STARLIGHT_SPARKLE.get(), level, x, y, z);
    }

    // 面内两个切向的随机偏移: 步进为 0 的轴取 [-0.4,0.4],步进非 0 的轴取 0(面法向不再偏)
    private static double jitter(RandomSource random, int step) {
        return step == 0 ? (random.nextDouble() - 0.5) * 0.8 : 0;
    }

    // 套壳管道同款发射(StarlightEncasedPipeBlock 调用),保持单一实现
    public static void addSparkle(ParticleOptions type, Level level, double x, double y, double z) {
        level.addParticle(type, x, y, z, 0, 0, 0);
    }
}
