package icu.dreamripples.aero_suite.simplification.block;

import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlock;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 更方便的模拟传动器 - 外观复用 simulated:analog_transmission。
 * 不注册额外 blockstate 属性，沿用原版 POWERED 即可。
 */
public class ConvenientAnalogTransmissionBlock extends AnalogTransmissionBlock {

    public ConvenientAnalogTransmissionBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<AnalogTransmissionBlockEntity> getBlockEntityClass() {
        return (Class<AnalogTransmissionBlockEntity>) (Class<?>) ConvenientAnalogTransmissionBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AnalogTransmissionBlockEntity> getBlockEntityType() {
        return ModBlocks.CONVENIENT_ANALOG_TRANSMISSION_BE.get();
    }
}
