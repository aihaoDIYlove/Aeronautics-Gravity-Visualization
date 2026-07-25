package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 红石控制的配重/配轻块基类 - 不再右键调值,改为接收红石信号 0..15 映射到 tier 1..16。
 * 无 ScrollValueBehaviour、无 BE:BlockState 变化由 Sable SubLevelPhysicsSystem.handleBlockChange
 * 自动检测并增量更新 MassTracker / FloatingBlockController。
 *
 * 信号映射:tier = signal + 1(signal 0 -> 1 kpg,signal 15 -> 16 kpg)。
 * onPlace 初始化放置时的 tier;neighborChanged 响应红stone线/拉杆变化。
 * setBlock 后 onPlace 会再触发一次,但 tier 已匹配,不会无限递归。
 *
 * 实现 IWrenchable 保留 Shift+wrench 拆除;普通 wrench 因无 FACING/AXIS 是 no-op
 * (getRotatedBlockState 返回 same state,KineticBlockEntity.switchToBlockState 短路)。
 */
public abstract class RedstoneTierBlock extends Block implements IWrenchable {

    private static final int MAX_TIER = 16; // 红石信号 0..15 -> tier 1..16

    protected RedstoneTierBlock(Properties properties) {
        super(properties);
    }

    protected abstract IntegerProperty tierProperty();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(tierProperty());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        updateTierFromSignal(level, pos, state);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean isMoving) {
        updateTierFromSignal(level, pos, state);
    }

    private void updateTierFromSignal(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;
        int signal = level.getBestNeighborSignal(pos);
        int targetTier = Math.min(MAX_TIER, Math.max(1, signal + 1));
        if (state.getValue(tierProperty()) != targetTier) {
            level.setBlockAndUpdate(pos, state.setValue(tierProperty(), targetTier));
        }
    }
}
