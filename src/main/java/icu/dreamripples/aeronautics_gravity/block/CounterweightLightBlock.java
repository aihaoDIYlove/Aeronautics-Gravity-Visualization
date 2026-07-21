package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 配"轻"块 - 通过 ScrollValueBehaviour 调节 lift_tier 1..36,对应 Sable floating_scale 1..36。
 * 走 Sable floating_material 路径:lift_tier 切换 -> BlockState 变化 ->
 * SableCommonEvents.handleBlockChange -> FloatingBlockController 重新计算 cluster 浮力。
 * 自身 mass=1(避免 MassTracker.build 在 mass=0 时除零 NaN),浮力由 floating_material + floating_scale 提供,
 * prevent_self_lift=true 保证浮力不超重力(不飞天)。
 */
public class CounterweightLightBlock extends Block implements IBE<CounterweightLightBlockEntity> {

    public static final IntegerProperty LIFT_TIER = IntegerProperty.create("lift_tier", 1, 36);

    public CounterweightLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIFT_TIER, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIFT_TIER);
    }

    @Override
    public Class<CounterweightLightBlockEntity> getBlockEntityClass() {
        return CounterweightLightBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CounterweightLightBlockEntity> getBlockEntityType() {
        return ModBlocks.COUNTERWEIGHT_LIGHT_BE.get();
    }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
