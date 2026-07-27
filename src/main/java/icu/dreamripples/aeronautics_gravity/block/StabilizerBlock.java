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
 * 自稳定方块 - 同时持有 MASS_TIER(1..16)与 LIFT_TIER(1..16)两个 BlockState 属性。
 * BE 每 5 tick 读取所在载具的姿态 + 质心方位,按 P 控制律互斥地调高其中一个 tier,
 * 另一个保持 1(基线 = 0 贡献:mass=1 kpg + 基础浮力 mat_1)。
 *
 * BlockState 变化由 Sable 自动检测并增量更新:
 *   - mass_tier -> SubLevelPhysicsSystem.handleBlockChange 重算 MassTracker
 *   - lift_tier -> SableCommonEvents.handleBlockChange -> FloatingBlockController 重算 cluster 浮力
 *
 * 互斥输出语义:
 *   - mass 模式 (N, 1):向下力(增重),用于压低"我这一侧在高端"的倾斜
 *   - lift 模式 (1, N):向上力(增浮),用于抬起"我这一侧在低端"的倾斜
 *   - 中性 (1, 1):休眠/停用
 */
public class StabilizerBlock extends Block implements IBE<StabilizerBlockEntity>, IWrenchable {

    public static final IntegerProperty MASS_TIER = IntegerProperty.create("mass_tier", 1, 16);
    public static final IntegerProperty LIFT_TIER = IntegerProperty.create("lift_tier", 1, 16);

    public StabilizerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(MASS_TIER, 1)
                .setValue(LIFT_TIER, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MASS_TIER, LIFT_TIER);
    }

    @Override
    public Class<StabilizerBlockEntity> getBlockEntityClass() {
        return StabilizerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StabilizerBlockEntity> getBlockEntityType() {
        return ModBlocks.STABILIZER_BE.get();
    }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
