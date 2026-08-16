package icu.dreamripples.aero_suite.block;

import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

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
}
