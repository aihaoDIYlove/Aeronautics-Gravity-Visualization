package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.item.SmartInventory;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.Vec3;
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
 * <p>**红石控制模式**(6 面弹板,参考世界锚点 AnchorMode):
 * <ul>
 *   <li>REDSTONE_PULSE(默认): 现有自节拍语义,vanilla {@code hasNeighborSignal} 上升沿
 *       ({@code prevPowered} 写 NBT 防重载假脉冲)。</li>
 *   <li>IGNORE_REDSTONE: 忽略红石,当前步 1 个被取走即自动换到下一步(单标记槽时回环自身,
 *       等效持续输出)。</li>
 * </ul>
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

    /** 红石控制模式弹板(6 面)。value=0 -> REDSTONE_PULSE(默认)。 */
    private FeederModeBehaviour modeBehaviour;

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
        modeBehaviour = new FeederModeBehaviour(
                FeederMode.class,
                Component.translatable("tooltip.aeronautics_gravity.sequential_feeder.mode"),
                this,
                new FeederModeValueBoxTransform());
        modeBehaviour.value = 0; // 默认 REDSTONE_PULSE
        // 切到忽略红石时若本步已输出(used=true 等脉冲)立即换步,否则无人推进卡死
        modeBehaviour.withCallback(i -> {
            if (i == FeederMode.IGNORE_REDSTONE.ordinal() && stepOutputUsed)
                advanceToNextMarked(false);
        });
        behaviours.add(modeBehaviour);
    }

    /** 当前红石控制模式 */
    public FeederMode getMode() {
        return modeBehaviour.get();
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
     * 忽略红石模式下取走即换步(advanceToNextMarked(false) 内部复位 used),无需等脉冲。
     */
    public void markStepOutputUsed() {
        if (stepOutputUsed)
            return;
        stepOutputUsed = true;
        if (getMode() == FeederMode.IGNORE_REDSTONE) {
            advanceToNextMarked(false);
            if (level != null && !level.isClientSide)
                notifyUpdate();
            return;
        }
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
        if (getMode() == FeederMode.REDSTONE_PULSE && powered && !prevPowered) {
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

    /** 红石控制模式 ScrollOptionBehaviour - 独立 BehaviourType + 独立 NBT key(参考世界锚点 AnchorModeBehaviour) */
    private static class FeederModeBehaviour extends ScrollOptionBehaviour<FeederMode> {
        public static final BehaviourType<FeederModeBehaviour> TYPE = new BehaviourType<>();

        public FeederModeBehaviour(Class<FeederMode> enumClass, Component label,
                                   SequentialFeederBlockEntity be, ValueBoxTransform slot) {
            super(enumClass, label, be, slot);
        }

        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

        @Override
        public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            nbt.putInt("FeederMode", value);
        }

        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            value = nbt.getInt("FeederMode");
        }
    }

    /** 6 面都显示弹板 */
    private static class FeederModeValueBoxTransform extends ValueBoxTransform.Sided {
        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return true;
        }

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8, 15.5);
        }
    }

    /**
     * 红石控制模式 - 6 面弹板切换。
     * REDSTONE_PULSE: 红石脉冲控制(默认,现有自节拍语义)。
     * IGNORE_REDSTONE: 忽略红石,取走即换步。
     * 图标复用 Create 的 I_ACTIVE(脉冲)/I_PASSIVE(忽略)。
     */
    public enum FeederMode implements INamedIconOptions {
        REDSTONE_PULSE(AllIcons.I_ACTIVE),
        IGNORE_REDSTONE(AllIcons.I_PASSIVE);

        private final AllIcons icon;
        private final String translationKey;

        FeederMode(AllIcons icon) {
            this.icon = icon;
            this.translationKey = "tooltip.aeronautics_gravity.sequential_feeder.mode." + name().toLowerCase();
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }
    }
}
