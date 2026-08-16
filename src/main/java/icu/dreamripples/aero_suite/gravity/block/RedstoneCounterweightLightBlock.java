package icu.dreamripples.aero_suite.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 红石配"轻"方块 - 红石信号 0..15 -> lift_tier 1..16 -> floating_material mat_1..16 (浮力 1..16 kpg)。
 * 两种皮(普通/珠光)共用本类。自身 mass=1,浮力由 floating_material + floating_scale 提供,
 * prevent_self_lift=true 保证不飞天。newBlockEntity 返回轻量 BE,仅为挂载灯带染色 visual。
 */
public class RedstoneCounterweightLightBlock extends RedstoneTierBlock {

    public static final IntegerProperty LIFT_TIER = IntegerProperty.create("lift_tier", 1, 16);

    public RedstoneCounterweightLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIFT_TIER, 1));
    }

    @Override
    protected IntegerProperty tierProperty() {
        return LIFT_TIER;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneCounterweightLightBlockEntity(ModBlocks.REDSTONE_COUNTERWEIGHT_LIGHT_BE.get(), pos, state);
    }
}
