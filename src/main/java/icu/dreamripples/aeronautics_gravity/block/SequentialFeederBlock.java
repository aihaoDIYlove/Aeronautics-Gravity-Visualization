package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 顺序供料器方块。右键开 UI(空手或持非交互物品)。破坏时掉落 9 物品槽内容
 * (Create IBE.onRemove 只清理 behaviours 不掉库存,见 SmartBlockEntity.destroy)。
 */
public class SequentialFeederBlock extends Block implements IBE<SequentialFeederBlockEntity> {

    public SequentialFeederBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        return onBlockEntityUse(level, pos, be -> {
            player.openMenu(be, be::sendToMenu);
            return InteractionResult.SUCCESS;
        });
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            withBlockEntityDo(level, pos, be -> {
                for (int i = 0; i < SequentialFeederBlockEntity.SLOTS; i++) {
                    ItemStack stack = be.inventory.getStackInSlot(i);
                    if (!stack.isEmpty())
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            });
        }
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<SequentialFeederBlockEntity> getBlockEntityClass() {
        return SequentialFeederBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SequentialFeederBlockEntity> getBlockEntityType() {
        return ModBlocks.SEQUENTIAL_FEEDER_BE.get();
    }
}
