package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.List;

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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_gravity.stabilizer.placement")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
