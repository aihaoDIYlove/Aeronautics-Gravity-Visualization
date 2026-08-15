package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.SmartInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * 顺序供料器 BE - 可编程投料磁带。9 标记槽(幽灵物品) + 9 物品槽一一对应。
 *
 * <p>**状态机**(完整语义见类 Javadoc 与 SequentialFeederItemHandler):
 * <ul>
 *   <li>{@code currentStep}: 指向某个<b>已标记</b>槽的下标(未标记槽不参与回环)。</li>
 *   <li>{@code stepOutputUsed}: 本步那 1 个是否已被外部取走。外部(机械手/漏斗)每次最多取 1 个,
 *       取走后置 true;红石脉冲只在 used==true 时前进一格(自节拍 R 语义),否则脉冲丢弃。</li>
 *   <li>指针前进后 used 复位,新当前槽待取。当前槽空(缺货)则 extract 永远为空 -> used 永不置
 *       true -> 整机停住等补货(自动满足"停住不跳步")。</li>
 * </ul>
 *
 * <p>**对外 IItemHandler**(全 6 面统一,见 {@link SequentialFeederItemHandler}):
 * insert 路由到任意匹配标记的未满槽(补货随时),extract 只放行当前步槽且带 1/步闸门。
 *
 * <p>**红石**:vanilla {@code hasNeighborSignal} 上升沿({@code prevPowered} 写 NBT 防重载假脉冲)。
 * 标记被清导致 currentStep 悬空时,重算序列并前移到下一个已标记槽(回环)。
 */
public class SequentialFeederBlockEntity extends SmartBlockEntity implements MenuProvider {

    public static final int SLOTS = 9;

    /** 物品槽(实体库存)。isValid: 对应标记槽非空且 stack 与标记 isSameItemSameComponents 匹配。 */
    public SmartInventory inventory;
    /** 标记槽(幽灵物品,count 恒 1,只表种类)。菜单直接包它;变化时经 onMarkerChanged 校正指针。 */
    public ItemStackHandler markers = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            onMarkerChanged(slot);
        }
    };

    // 状态机字段(currentStep/stepOutputUsed 另经 ContainerData 同步给打开的菜单)
    private int currentStep = 0;
    private boolean stepOutputUsed = false;
    private boolean prevPowered = false;
    /**
     * 指针因标记取消而<b>非时钟</b>前移且原步输出额度未用完(used=true)时置位:
     * 下一红石脉冲只把 used 复位(重挂当前槽),不前进 -- 否则脉冲会跳过新槽整轮不供料。
     */
    private boolean rearmPending = false;

    private final SequentialFeederItemHandler itemHandler = new SequentialFeederItemHandler(this);

    /** 菜单数据通道: [0]=currentStep, [1]=stepOutputUsed(0/1) */
    public final ContainerData feederData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> currentStep;
                case 1 -> stepOutputUsed ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public SequentialFeederBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        inventory = new SmartInventory(SLOTS, this, (slot, stack) -> isMarkedMatch(slot, stack));
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 无额外 behaviour(openTracker 等 Create 弹板不需要);SmartBlockEntity 要求实现
    }

    /** 标记槽 i 是否允许 stack: 标记非空且全组件匹配(isSameItemSameComponents,可区分药水种类)。 */
    public boolean isMarkedMatch(int slot, ItemStack stack) {
        ItemStack marker = markers.getStackInSlot(slot);
        return !marker.isEmpty() && ItemStack.isSameItemSameComponents(marker, stack);
    }

    public int getCurrentStep() {
        return currentStep;
    }

    /** 对外 capped handler(capability 与 shift 快速移动共用;每次返回同一实例) */
    public SequentialFeederItemHandler getItemHandler() {
        return itemHandler;
    }

    public boolean isStepOutputUsed() {
        return stepOutputUsed;
    }

    /** 槽 i 是否已标记 */
    public boolean isMarked(int slot) {
        return !markers.getStackInSlot(slot).isEmpty();
    }

    /**
     * 外部取走当前步 1 个后被 SequentialFeederItemHandler 调用:置 used,notifyUpdate 同步客户端。
     */
    public void markStepOutputUsed() {
        if (stepOutputUsed)
            return;
        stepOutputUsed = true;
        setChanged();
        if (level != null && !level.isClientSide)
            notifyUpdate();
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.aeronautics_gravity.sequential_feeder");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return SequentialFeederMenu.create(id, inv, this);
    }

    // 标记变化钩子

    /**
     * 标记槽变化(设置/清除标记)。校正 currentStep:若当前槽标记被清且恰为当前步,
     * 前移到下一个已标记槽(回环);全部未标记则归 0。
     *
     * <p>标记取消<b>不是时钟脉冲</b>,前进时保留 stepOutputUsed -- 否则本步输出额度
     * 已用掉(used=true 等脉冲)时取消标记会复位 used,机械手无脉冲就白取下一槽 1 个。
     */
    private void onMarkerChanged(int changedSlot) {
        if (level == null)
            return;
        if (markers.getStackInSlot(changedSlot).isEmpty() && currentStep == changedSlot) {
            advanceToNextMarked(true);
        }
        if (!level.isClientSide)
            notifyUpdate();
    }

    /**
     * 指针前进到下一个已标记槽(回环;跳过未标记)。若无任何标记,currentStep 归 0。
     *
     * @param keepUsed true 时不复位 stepOutputUsed -- 用于标记取消/读档校正这类
     *                 非时钟脉冲的前进:本步输出额度已用掉就不因指针跳转白送输出。
     *                 此时若 used=true 再置 rearmPending,让下一脉冲只重挂不前进。
     *                 红石 tick 前进传 false(脉冲换步,新步待取)。
     */
    public void advanceToNextMarked(boolean keepUsed) {
        for (int i = 1; i <= SLOTS; i++) {
            int candidate = (currentStep + i) % SLOTS;
            if (isMarked(candidate)) {
                currentStep = candidate;
                if (keepUsed) {
                    if (stepOutputUsed)
                        rearmPending = true;
                } else {
                    stepOutputUsed = false;
                    rearmPending = false;
                }
                setChanged();
                return;
            }
        }
        // 无任何标记: 归 0(keepUsed 时保留 used,防止跳回 0 号槽白送一次输出;
        // used 已用掉时同样挂 rearmPending,全清后再标记不跳过新槽)
        if (currentStep != 0 || stepOutputUsed) {
            currentStep = 0;
            if (keepUsed) {
                if (stepOutputUsed)
                    rearmPending = true;
            } else {
                stepOutputUsed = false;
                rearmPending = false;
            }
            setChanged();
        }
    }

    // 红石 tick

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide)
            return;
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (powered && !prevPowered) {
            // 上升沿: 先还非时钟前移欠下的"重挂",再走自节拍 R 语义
            if (rearmPending) {
                rearmPending = false;
                stepOutputUsed = false;
            } else if (stepOutputUsed) {
                // 上升沿: 自节拍 R 语义 -- 仅当本步已输出(被取走)才前进,否则丢弃脉冲
                advanceToNextMarked(false);
            }
        }
        prevPowered = powered;
    }

    // 序列化

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("Markers", markers.serializeNBT(registries));
        tag.putInt("CurrentStep", currentStep);
        tag.putBoolean("StepOutputUsed", stepOutputUsed);
        tag.putBoolean("PrevPowered", prevPowered);
        tag.putBoolean("RearmPending", rearmPending);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Inventory"))
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        if (tag.contains("Markers"))
            markers.deserializeNBT(registries, tag.getCompound("Markers"));
        currentStep = tag.contains("CurrentStep") ? tag.getInt("CurrentStep") : 0;
        stepOutputUsed = tag.getBoolean("StepOutputUsed");
        prevPowered = tag.getBoolean("PrevPowered");
        rearmPending = tag.getBoolean("RearmPending");
        // NBT 里的 currentStep 可能因标记变化而悬空(如标记在客户端菜单直接改),读后校正
        // 读档校正非时钟脉冲,保留 used 防白送
        if (!isMarked(currentStep) && hasAnyMarker())
            advanceToNextMarked(true);
    }

    private boolean hasAnyMarker() {
        for (int i = 0; i < SLOTS; i++)
            if (isMarked(i))
                return true;
        return false;
    }
}
