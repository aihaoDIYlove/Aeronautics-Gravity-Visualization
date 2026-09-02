package icu.dreamripples.aero_suite.simplification.block;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * 滚动加工台方块。13px 高台面(同 Create 分液池外形);掉落物砸在台面上会沿
 * 砸落速度方向进入滚动(ItemDrain 的 updateEntityAfterFallOn 语义, MIT)。
 */
public class RollingTableBlock extends Block implements IBE<RollingTableBlockEntity>, IWrenchable {

    public RollingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(
                "block.simplification_related.rolling_table.desc").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.CASING_13PX.get(Direction.UP);
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter worldIn, Entity entityIn) {
        super.updateEntityAfterFallOn(worldIn, entityIn);
        if (!(entityIn instanceof net.minecraft.world.entity.item.ItemEntity itemEntity))
            return;
        if (!itemEntity.isAlive())
            return;
        if (itemEntity.level().isClientSide)
            return;

        DirectBeltInputBehaviour inputBehaviour =
            BlockEntityBehaviour.get(worldIn, entityIn.blockPosition(), DirectBeltInputBehaviour.TYPE);
        if (inputBehaviour == null)
            return;
        Vec3 deltaMovement = itemEntity.getDeltaMovement()
            .multiply(1, 0, 1)
            .normalize();
        Direction nearest = Direction.getNearest(deltaMovement.x, deltaMovement.y, deltaMovement.z);
        ItemStack remainder = inputBehaviour.handleInsertion(itemEntity.getItem(), nearest, false);
        itemEntity.setItem(remainder);
        if (remainder.isEmpty())
            itemEntity.discard();
    }

    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.hasBlockEntity() || state.getBlock() == newState.getBlock())
            return;
        withBlockEntityDo(worldIn, pos, be -> {
            ItemStack heldItemStack = be.getHeldItemStack();
            if (!heldItemStack.isEmpty())
                Containers.dropItemStack(worldIn, pos.getX(), pos.getY(), pos.getZ(), heldItemStack);
        });
        worldIn.removeBlockEntity(pos);
    }

    @Override
    public Class<RollingTableBlockEntity> getBlockEntityClass() {
        return RollingTableBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RollingTableBlockEntity> getBlockEntityType() {
        return ModBlocks.ROLLING_TABLE_BE.get();
    }
}
