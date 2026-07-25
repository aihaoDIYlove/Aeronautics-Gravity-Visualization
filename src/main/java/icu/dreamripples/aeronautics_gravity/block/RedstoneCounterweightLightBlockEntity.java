package icu.dreamripples.aeronautics_gravity.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 红石配"轻"块的轻量 BE - 仅为挂载 Flywheel visual(灯带染色)而存在。
 * 无 tick、无 NBT、无 behaviour:红石信号已编码在 BlockState.LIFT_TIER(由 RedstoneTierBlock.neighborChanged 维护),
 * BlockState 自动同步到客户端,visual 直接读 tier 算颜色。
 */
public class RedstoneCounterweightLightBlockEntity extends BlockEntity {

    public RedstoneCounterweightLightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
