package icu.dreamripples.aeronautics_gravity.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 红石配重方块 - 红石信号 0..15 -> mass_tier 1..16 -> Sable mass 1..16 kpg。
 * 三种皮(铁/煤/金)共用本类与 physics_block_properties 的 overrides 映射。
 */
public class RedstoneCounterweightBlock extends RedstoneTierBlock {

    public static final IntegerProperty MASS_TIER = IntegerProperty.create("mass_tier", 1, 16);

    public RedstoneCounterweightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MASS_TIER, 1));
    }

    @Override
    protected IntegerProperty tierProperty() {
        return MASS_TIER;
    }
}
