package icu.dreamripples.aeronautics_gravity.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nonnull;

/**
 * 顺序供料器对外 IItemHandler(全 6 面统一)。
 *
 * <ul>
 *   <li>{@link #insertItem}: 补货。遍历全部 9 槽,找第一个标记匹配(isSameItemSameComponents)
 *       且未满的槽塞入;无匹配/全满 -> 原样返回(发送方保留物品)。不受 currentStep 影响,
 *       任意时刻可给任意已标记槽补货,补货不重置 stepOutputUsed。</li>
 *   <li>{@link #extractItem}: 取料。只放行当前步槽(currentStep),带 1/步闸门:
 *       本步已输出(stepOutputUsed)或当前槽空 -> 永远返回空;否则至多出 1 个,
 *       非模拟取走后置 stepOutputUsed。红石脉冲在 used==true 时前进指针(自节拍)。</li>
 *   <li>{@link #getStackInSlot(int)}: 装箱视角只暴露当前步槽(共 1 槽)——漏斗/溜槽抽出端
 *       与机械手只看得见"现在对外供的料"。insert 视角仍是 9 槽(通过 insertItem 路由)。</li>
 * </ul>
 *
 * <p>提取槽布局说明: IItemHandler 契约中 getSlots/insertItem/extractItem 三个方法
 * 的 slot 下标空间一致;本 handler 把 getSlots 报 9 但 extractItem 只对 currentStep
 * 槽生效,其余槽 extract 恒空 —— insert 语义是"容器整体",extract 语义是"当前步"。
 * 这对漏斗(只调 extractItem(currentStep, ...))与 Create 拆包机(insertItem 逐个)都正确。
 */
public class SequentialFeederItemHandler implements IItemHandler {

    private final SequentialFeederBlockEntity be;

    public SequentialFeederItemHandler(SequentialFeederBlockEntity be) {
        this.be = be;
    }

    @Override
    public int getSlots() {
        return SequentialFeederBlockEntity.SLOTS;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        // 装箱视角: 只暴露当前步槽,其余槽对外"不存在"
        if (slot == be.getCurrentStep())
            return be.inventory.getStackInSlot(slot);
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return be.inventory.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return be.isMarkedMatch(slot, stack);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        // slot 参数在此无意义(路由式插入): 找第一个标记匹配且未满的槽
        if (stack.isEmpty())
            return stack;
        for (int i = 0; i < be.inventory.getSlots(); i++) {
            if (!be.isMarkedMatch(i, stack))
                continue;
            ItemStack remainder = be.inventory.insertItem(i, stack, simulate);
            if (remainder.getCount() != stack.getCount())
                return remainder;
        }
        return stack;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot != be.getCurrentStep())
            return ItemStack.EMPTY;
        if (be.isStepOutputUsed())
            return ItemStack.EMPTY;
        ItemStack available = be.inventory.getStackInSlot(slot);
        if (available.isEmpty())
            return ItemStack.EMPTY;
        int toTake = Math.min(1, amount); // 1/步闸门
        ItemStack extracted = be.inventory.extractItem(slot, toTake, simulate);
        if (!simulate && !extracted.isEmpty())
            be.markStepOutputUsed();
        return extracted;
    }
}
