package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 配重方块 - 通过 ScrollValueBehaviour 调节 mass_tier 1..20,对应 Sable mass 1..20 kpg。
 * BlockState 变化由 Sable 的 SubLevelPhysicsSystem.handleBlockChange 自动检测并增量更新 MassTracker,
 * 无需 Mixin 或手动通知。质量到 mass_tier 的映射由 physics_block_properties/counterweight.json 的
 * overrides 字段定义。
 */
public class CounterweightBlock extends Block implements IBE<CounterweightBlockEntity>, IWrenchable {

    public static final IntegerProperty MASS_TIER = IntegerProperty.create("mass_tier", 1, 20);

    public CounterweightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MASS_TIER, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MASS_TIER);
    }

    @Override
    public Class<CounterweightBlockEntity> getBlockEntityClass() {
        return CounterweightBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CounterweightBlockEntity> getBlockEntityType() {
        return ModBlocks.COUNTERWEIGHT_BE.get();
    }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
