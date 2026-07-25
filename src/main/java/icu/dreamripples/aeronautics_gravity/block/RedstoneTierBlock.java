package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 红石控制的配重/配轻块基类 - 接收红石信号 0..15 映射到 tier 1..16。
 * BlockState 变化由 Sable SubLevelPhysicsSystem.handleBlockChange 自动检测并增量更新
 * MassTracker / FloatingBlockController。
 *
 * 信号映射:tier = signal + 1(signal 0 -> 1 kpg,signal 15 -> 16 kpg)。
 * onPlace 初始化放置时的 tier;neighborChanged 响应红石线/拉杆变化。
 * setBlock 后 onPlace 会再触发一次,但 tier 已匹配,不会无限递归。
 *
 * 实现 EntityBlock 挂载轻量 BE(无 tick 无 NBT),仅为 Flywheel visual(灯带染色)提供载体。
 * 实现 IWrenchable 保留 Shift+wrench 拆除;普通 wrench 因无 FACING/AXIS 是 no-op。
 */
public abstract class RedstoneTierBlock extends Block implements IWrenchable, EntityBlock {

    private static final int MAX_TIER = 16; // 红石信号 0..15 -> tier 1..16

    protected RedstoneTierBlock(Properties properties) {
        super(properties);
    }

    protected abstract IntegerProperty tierProperty();

    @Override
    public abstract BlockEntity newBlockEntity(BlockPos pos, BlockState state);

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null; // 无 tick,BE 仅为挂载 Flywheel visual
    }

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
