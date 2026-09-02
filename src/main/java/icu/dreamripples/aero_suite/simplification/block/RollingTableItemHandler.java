package icu.dreamripples.aero_suite.simplification.block;

import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 滚动加工台的 {@link IItemHandler} 包装:单槽读写台面物品(方向无关,供机械臂/
 * 漏斗兼容,插入即开始滚动)。
 */
public class RollingTableItemHandler implements IItemHandler {

    private final RollingTableBlockEntity blockEntity;

    public RollingTableItemHandler(RollingTableBlockEntity be) {
        this.blockEntity = be;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return blockEntity.getHeldItemStack();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!blockEntity.getHeldItemStack().isEmpty())
            return stack;
        if (simulate)
            return ItemStack.EMPTY;

        TransportedItemStack heldItem = new TransportedItemStack(stack.copy());
        heldItem.prevBeltPosition = .5f;
        blockEntity.setHeldItem(heldItem, Direction.UP);
        blockEntity.notifyUpdate();
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        TransportedItemStack held = blockEntity.heldItem;
        if (held == null)
            return ItemStack.EMPTY;

        ItemStack stack = held.stack.copy();
        ItemStack extracted = stack.split(amount);
        if (!simulate) {
            held.stack = stack;
            if (stack.isEmpty())
                blockEntity.heldItem = null;
            blockEntity.notifyUpdate();
        }
        return extracted;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }
}
