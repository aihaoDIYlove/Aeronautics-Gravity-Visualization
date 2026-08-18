package icu.dreamripples.aero_suite.simplification.block;

import com.mojang.serialization.MapCodec;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.jetbrains.annotations.Nullable;

/**
 * 单格漏斗方块: 行为/外观/红石启停全部继承原版 HopperBlock,
 * 仅替换 BE 类型(否则 getTicker 硬编码 BlockEntityType.HOPPER 会拿不到 ticker)。
 */
public class SingleSlotHopperBlock extends HopperBlock {
    public static final MapCodec<HopperBlock> CODEC = simpleCodec(SingleSlotHopperBlock::new);

    public SingleSlotHopperBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<HopperBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SingleSlotHopperBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // 基类 createTickerHelper 对比的是 BlockEntityType.HOPPER, 必须换成自己的类型
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlocks.SINGLE_SLOT_HOPPER_BE.get(),
                        (BlockEntityTicker<HopperBlockEntity>) HopperBlockEntity::pushItemsTick);
    }
}
