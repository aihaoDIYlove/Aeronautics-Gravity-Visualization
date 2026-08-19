package icu.dreamripples.aero_suite.starlight.block;

import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 珍珠滞留台: 参考置物台的右键存取交互, 但单槽恒 1 个物品(无论是否可堆叠)、无 GUI、
 * 物品渲染在方块内部 4px 高处。合成 = 超轻玻璃 + 置物台(无序)。
 *
 * <ul>
 *   <li><b>空手右键</b> {@link #useWithoutItem}: 取出内部物品(满堆原样给, 内部恒 count=1)。</li>
 *   <li><b>持物右键</b> {@link #useItemOn}: 内部为空时放入手中物品 1 个(创造模式不消耗)。</li>
 *   <li><b>红石上升沿</b> {@link PearlStasisBlockEntity#updateRedstone}:
 *       内部是已绑定玩家的激活末影珍珠时, 把该玩家传送到滞留台上方并消耗珍珠。</li>
 *   <li>漏斗/机械手经 capability 双向存取({@code PearlStasisBlockEntity.getItemHandler})。</li>
 *   <li>比较器: 非空输出 1。</li>
 * </ul>
 */
public class PearlStasisBlock extends Block implements EntityBlock {

    public PearlStasisBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlocks.PEARL_STASIS_BE.get().create(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, net.minecraft.world.InteractionHand hand,
                                               BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof PearlStasisBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.isEmpty() || !be.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide()) {
            be.setHeld(stack.copyWithCount(1));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof PearlStasisBlockEntity be) || be.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            ItemStack taken = be.getHeld().copy();
            be.setHeld(ItemStack.EMPTY);
            if (!player.getInventory().add(taken)) {
                player.drop(taken, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof PearlStasisBlockEntity be) {
            be.updateRedstone(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof PearlStasisBlockEntity be && !be.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, be.getHeld());
            be.setHeld(ItemStack.EMPTY);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PearlStasisBlockEntity be && !be.isEmpty() ? 1 : 0;
    }
}
