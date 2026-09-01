package icu.dreamripples.aero_suite.starlight.block;

import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import icu.dreamripples.aero_suite.common.config.FeatureGates;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import icu.dreamripples.aero_suite.starlight.ModParticles;

import java.util.function.Supplier;

/**
 * 星空套壳管道: 继承 Create 的 EncasedPipeBlock, 仅覆盖 getBlockEntityType 返回自注册 BE.
 *
 * 为什么必须继承 + 覆盖: EncasedPipeBlock.getBlockEntityType() 硬编码返回 Create 的
 * AllBlockEntityTypes.ENCASED_FLUID_PIPE, 其 validBlocks 不含本方块, 直接用会在放置时
 * "Block entity type not valid for block" 崩溃. 自注册 BE 类型(FluidPipeBlockEntity::new,
 * validBlocks=本方块) + 覆盖 getBlockEntityType 指向它即可, 无需 Mixin.
 *
 * 继承 EncasedPipeBlock 而非仅实现接口: FluidPipeBlockEntity.StandardPipeFluidTransportBehaviour
 * 的方向判定都是 state.getBlock() instanceof EncasedPipeBlock, 必须是真子类流体才流通.
 *
 * onWrenched/handleEncasing/getCloneItemStack 继承: 扳手拆除还原成 Create 的 FLUID_PIPE
 * (裸铜管道), 中键拾取返回 FLUID_PIPE, 套壳时 transferSixWayProperties 保留方向.
 */
public class StarlightEncasedPipeBlock extends EncasedPipeBlock {
    public StarlightEncasedPipeBlock(BlockBehaviour.Properties properties, Supplier<Block> casing) {
        super(properties, casing);
    }

    @Override
    public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
        return ModBlocks.STARLIGHT_ENCASED_FLUID_PIPE_BE.get();
    }

    // 星光闪烁粒子(与 StarlightCasingBlock 同密度): 套壳管道外观就是星空机壳,视觉保持一致。
    // 六面板方块在六个面中心附近随机冒点。
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(10) != 0) return;
        if (!FeatureGates.isEnabled("starlight_casing")) return;
        if (!StarlightCasingBlock.sparkleAllowed(level, pos)) return;
        Direction face = Direction.getRandom(random);
        if (!Block.shouldRenderFace(state, level, pos, face, pos.relative(face))) return;
        double x = pos.getX() + 0.5 + face.getStepX() * 0.55 + (face.getStepX() == 0 ? (random.nextDouble() - 0.5) * 0.8 : 0);
        double y = pos.getY() + 0.5 + face.getStepY() * 0.55 + (face.getStepY() == 0 ? (random.nextDouble() - 0.5) * 0.8 : 0);
        double z = pos.getZ() + 0.5 + face.getStepZ() * 0.55 + (face.getStepZ() == 0 ? (random.nextDouble() - 0.5) * 0.8 : 0);
        ParticleOptions type = ModParticles.STARLIGHT_SPARKLE.get();
        StarlightCasingBlock.addSparkle(type, level, x, y, z);
    }
}
