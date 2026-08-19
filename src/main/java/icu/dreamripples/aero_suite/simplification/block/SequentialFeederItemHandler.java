package icu.dreamripples.aero_suite.simplification.block;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nonnull;

/**
 * 顺序供料器对外 IItemHandler(全 6 面统一)。
 *
 * <ul>
 *   <li>{@link #insertItem}: 补货。路由到第一个标记匹配(isSameItemSameComponents)且未满
 *       的槽,任意时刻可给任意已标记槽补货,不影响 currentStep 与 stepState。</li>
 *   <li>{@link #extractItem}: 取料。只放行当前步槽且要求 ARMED(见 BE 状态机),至多 1 个
 *       (1/步闸门),非模拟取走后 onStepOutputTaken 转移状态。</li>
 *   <li>{@link #getStackInSlot(int)}: 9 槽全量透传 -- Create 打包机/工厂仪表
 *       (PackagerBlockEntity.getAvailableItems)与库存管家只靠本方法盘点库存,
 *       必须看到全部槽位,否则仪表读数为 0 会疯狂请求包裹。取料门控完全由
 *       {@link #extractItem} 承担,漏斗/机械手依然只取得走当前步的料。</li>
 * </ul>
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
        // 统计视角: 9 槽全量透传(打包机/工厂仪表/库存管家靠此盘点),取料门控在 extractItem
        return be.inventory.getStackInSlot(slot);
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
        if (!be.isArmed())
            return ItemStack.EMPTY;
        ItemStack available = be.inventory.getStackInSlot(slot);
        if (available.isEmpty())
            return ItemStack.EMPTY;
        int toTake = Math.min(1, amount); // 1/步闸门
        ItemStack extracted = be.inventory.extractItem(slot, toTake, simulate);
        if (!simulate && !extracted.isEmpty())
            be.onStepOutputTaken();
        return extracted;
    }
}
