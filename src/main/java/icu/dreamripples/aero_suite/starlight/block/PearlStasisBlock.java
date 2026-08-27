package icu.dreamripples.aero_suite.starlight.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * 珍珠滞留台: 参考置物台的右键存取交互, 但单槽恒 1 个物品(无论是否可堆叠)、无 GUI。
 * 合成 = 超轻玻璃 + 置物台(无序)。
 *
 * <ul>
 *   <li><b>空手右键</b> {@link #useWithoutItem}: 取出内部物品(内部恒 count=1)。</li>
 *   <li><b>持物右键</b> {@link #useItemOn}: 内部为空时放入手中物品 1 个(创造模式不消耗)。</li>
 *   <li><b>红石触发</b>: 顶面弹板三档({@link PearlStasisBlockEntity.TriggerMode}) --
 *       上升沿一次 / 有红石持续 / 无红石持续; 内部是已绑定玩家的激活末影珍珠时,
 *       把该玩家传送到滞留台上方并消耗珍珠。</li>
 *   <li>漏斗/机械手经 capability 双向存取({@code PearlStasisBlockEntity.getItemHandler})。</li>
 *   <li>比较器: 非空输出 1。</li>
 * </ul>
 *
 * <p>实现 {@link IBE}: newBlockEntity/ticker(行为弹板需要 SmartBlockEntity tick)均由
 * 接口默认方法提供。
 */
public class PearlStasisBlock extends Block implements IBE<PearlStasisBlockEntity>, IWrenchable {

    public PearlStasisBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.starlight_logistics.pearl_stasis.teleport")
                .withStyle(ChatFormatting.AQUA));
    }

    @Override
    public Class<PearlStasisBlockEntity> getBlockEntityClass() {
        return PearlStasisBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PearlStasisBlockEntity> getBlockEntityType() {
        return ModBlocks.PEARL_STASIS_BE.get();
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
            be.updateRedstone();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof PearlStasisBlockEntity be && !be.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, be.getHeld());
            be.setHeld(ItemStack.EMPTY);
        }
        IBE.onRemove(state, level, pos, newState);
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
