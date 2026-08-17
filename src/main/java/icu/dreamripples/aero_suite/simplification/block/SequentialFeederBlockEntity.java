package icu.dreamripples.aero_suite.simplification.block;

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
 * <p>**状态机**(三态,对外语义见 {@link SequentialFeederItemHandler}):
 * <ul>
 *   <li>{@code currentStep}: 指向某个<b>已标记</b>槽的下标(未标记槽不参与回环)。</li>
 *   <li>{@code WAITING}(灰箭头): <b>初始态</b>,未授权输出 -- 首次配置不白送。脉冲挂载当前步。</li>
 *   <li>{@code ARMED}(黄箭头/缺货红箭头): 授权取走 1 个,取走后转 TAKEN;此态脉冲丢弃(防超前)。</li>
 *   <li>{@code TAKEN}(绿箭头): 本步额度已消费,下一脉冲<b>前进一格</b>再 ARMED(自节拍)。</li>
 *   <li>当前槽空(缺货)则 extract 恒空 -> 停住等补货,不跳步。</li>
 * </ul>
 *
 * <p>**忽略红石模式**: 取走即前进并重新 ARMED(恒就绪);切入时先还 TAKEN 欠的前进。
 * 切回脉冲模式时 ARMED 降级 WAITING(旧模式残留授权不带走,防切换瞬间白送)。
 *
 * <p>**非时钟前进**(标记取消/读档校正): TAKEN 降级 WAITING -- 新当前槽从未输出,
 * 下一脉冲直接挂载而非再跳一格。
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

    // 状态机字段(currentStep/stepState 经 ContainerData 同步给菜单)
    private int currentStep = 0;
    private StepState stepState = StepState.WAITING;
    private boolean prevPowered = false;

    private final SequentialFeederItemHandler itemHandler = new SequentialFeederItemHandler(this);

    private FeederModeBehaviour modeBehaviour;

    /** 菜单数据通道: [0]=currentStep, [1]=stepState.ordinal() */
    public final ContainerData feederData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> currentStep;
                case 1 -> stepState.ordinal();
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
                Component.translatable("tooltip.simplification_related.sequential_feeder.mode"),
                this,
                new FeederModeValueBoxTransform());
        modeBehaviour.value = 0; // 默认 REDSTONE_PULSE
        // 切模式同步 stepState(规则见类 Javadoc"忽略红石模式"段)
        modeBehaviour.withCallback(i -> {
            if (i == FeederMode.IGNORE_REDSTONE.ordinal()) {
                if (stepState == StepState.TAKEN)
                    advanceToNextMarked();
                stepState = StepState.ARMED;
            } else if (stepState == StepState.ARMED) {
                stepState = StepState.WAITING;
            }
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

    /** 对外 capped handler(capability 与 shift 快速移动共用) */
    public SequentialFeederItemHandler getItemHandler() {
        return itemHandler;
    }

    public boolean isArmed() {
        return stepState == StepState.ARMED;
    }

    public boolean isMarked(int slot) {
        return !markers.getStackInSlot(slot).isEmpty();
    }

    /** 外部取走当前步 1 个后由 ItemHandler 调用;忽略模式立即前进并重新 ARMED。 */
    public void onStepOutputTaken() {
        stepState = StepState.TAKEN;
        if (getMode() == FeederMode.IGNORE_REDSTONE) {
            advanceToNextMarked();
            stepState = StepState.ARMED;
        }
        setChanged();
        if (level != null && !level.isClientSide)
            notifyUpdate();
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.simplification_related.sequential_feeder");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return SequentialFeederMenu.create(id, inv, this);
    }

    // 标记变化钩子

    /** 当前步的标记被清 -> 前进到下一个已标记槽;非时钟前进,TAKEN 降级 WAITING(见类 Javadoc)。 */
    private void onMarkerChanged(int changedSlot) {
        if (level == null)
            return;
        if (markers.getStackInSlot(changedSlot).isEmpty() && currentStep == changedSlot) {
            advanceToNextMarked();
            if (!hasAnyMarker() || stepState == StepState.TAKEN)
                stepState = StepState.WAITING;
        }
        if (!level.isClientSide)
            notifyUpdate();
    }

    /** 指针前进到下一个已标记槽(回环;跳过未标记),无标记时归 0。只动指针,状态由调用方决定。 */
    public void advanceToNextMarked() {
        for (int i = 1; i <= SLOTS; i++) {
            int candidate = (currentStep + i) % SLOTS;
            if (isMarked(candidate)) {
                currentStep = candidate;
                setChanged();
                return;
            }
        }
        if (currentStep != 0) {
            currentStep = 0;
            setChanged();
        }
    }

    // 红石 tick

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide)
            return;
        // 忽略模式恒就绪: 读 NBT 不触发 modeBehaviour 回调,读档回填靠这里兜底
        if (getMode() == FeederMode.IGNORE_REDSTONE && stepState == StepState.WAITING && hasAnyMarker()) {
            stepState = StepState.ARMED;
            setChanged();
        }
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (getMode() == FeederMode.REDSTONE_PULSE && powered && !prevPowered) {
            // 上升沿转移见类 Javadoc 状态机
            switch (stepState) {
                case WAITING -> {
                    stepState = StepState.ARMED;
                    setChanged();
                }
                case TAKEN -> {
                    advanceToNextMarked();
                    stepState = hasAnyMarker() ? StepState.ARMED : StepState.WAITING;
                    setChanged();
                }
                case ARMED -> { }
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
        tag.putInt("StepState", stepState.ordinal());
        tag.putBoolean("PrevPowered", prevPowered);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Inventory"))
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        if (tag.contains("Markers"))
            markers.deserializeNBT(registries, tag.getCompound("Markers"));
        currentStep = tag.contains("CurrentStep") ? tag.getInt("CurrentStep") : 0;
        // StepState 三代键兼容: StepState(现) / Armed(单布尔中间版) / StepOutputUsed(原始反向标志)
        if (tag.contains("StepState")) {
            int ord = tag.getInt("StepState");
            stepState = ord >= 0 && ord < StepState.values().length
                    ? StepState.values()[ord] : StepState.WAITING;
        } else if (tag.contains("Armed")) {
            stepState = tag.getBoolean("Armed") ? StepState.ARMED : StepState.WAITING;
        } else {
            stepState = tag.getBoolean("StepOutputUsed") ? StepState.TAKEN : StepState.WAITING;
        }
        prevPowered = tag.getBoolean("PrevPowered");
        // currentStep 可能悬空(标记在客户端菜单被改),读后校正(非时钟前进,降级规则同 onMarkerChanged)
        if (!isMarked(currentStep) && hasAnyMarker()) {
            advanceToNextMarked();
            if (stepState == StepState.TAKEN)
                stepState = StepState.WAITING;
        }
    }

    private boolean hasAnyMarker() {
        for (int i = 0; i < SLOTS; i++)
            if (isMarked(i))
                return true;
        return false;
    }

    /** 当前步状态 - 见类 Javadoc */
    public enum StepState {
        /** 未就绪(灰): 等待脉冲/模式自走授权 */
        WAITING,
        /** 就绪(黄/缺货红): 授权输出至多 1 个 */
        ARMED,
        /** 已取走(绿): 额度已消费,下一脉冲前进并就绪 */
        TAKEN
    }

    /** 模式弹板 - 独立 BehaviourType + 独立 NBT key,防三重冲突(见 CLAUDE.md 惯例) */
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

    /** 控制模式 - REDSTONE_PULSE 脉冲控制(默认,语义见 StepState)/ IGNORE_REDSTONE 忽略红石(取走即换步)。 */
    public enum FeederMode implements INamedIconOptions {
        REDSTONE_PULSE(AllIcons.I_ACTIVE),
        IGNORE_REDSTONE(AllIcons.I_PASSIVE);

        private final AllIcons icon;
        private final String translationKey;

        FeederMode(AllIcons icon) {
            this.icon = icon;
            this.translationKey = "tooltip.simplification_related.sequential_feeder.mode." + name().toLowerCase();
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
