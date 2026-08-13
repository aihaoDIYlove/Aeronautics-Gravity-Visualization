package icu.dreamripples.aeronautics_gravity.block;

import icu.dreamripples.aeronautics_gravity.event.StarlightGlowHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * 星空液体方块: 生物/玩家浸入时获得发光效果(光灵箭同款, 15 秒).
 * <p>
 * 重写 {@link #entityInside} 在实体碰撞箱与本方块重叠时触发 -- {@code Entity.checkInsideBlocks}
 * 每 tick 遍历实体覆盖的方块调用 {@code entityInside}, 与流体是否有碰撞箱无关. 仅服务端施加,
 * 客户端不重复. 刷新策略与末地之海场景共用 {@link StarlightGlowHandler#applyGlow}.
 */
public class StarlightLiquidBlock extends LiquidBlock {
    public StarlightLiquidBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
        super(fluid, properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (!level.isClientSide && entity instanceof LivingEntity living) {
            StarlightGlowHandler.applyGlow(living);
        }
    }
}
