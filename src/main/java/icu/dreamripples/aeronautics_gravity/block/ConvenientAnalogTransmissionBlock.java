package icu.dreamripples.aeronautics_gravity.block;

import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlock;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 更方便的模拟传动器 - 外观复用 simulated:analog_transmission，增加 REVERSED 属性用于切换正反方向。
 *
 * Shift+右键切换 REVERSED，选择正向/反向 RPM 查表。
 */
public class ConvenientAnalogTransmissionBlock extends AnalogTransmissionBlock {

    public static final BooleanProperty REVERSED = BooleanProperty.create("reversed");

    public ConvenientAnalogTransmissionBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(REVERSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(REVERSED);
    }

    @Override
    protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (!level.isClientSide && player.isShiftKeyDown()) {
            level.setBlockAndUpdate(pos, state.cycle(REVERSED));
            return ItemInteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
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
