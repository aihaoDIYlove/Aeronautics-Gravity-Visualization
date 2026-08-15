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
 * <p>**状态机**(三态,完整语义见 {@link SequentialFeederItemHandler}):
 * <ul>
 *   <li>{@code currentStep}: 指向某个<b>已标记</b>槽的下标(未标记槽不参与回环)。</li>
 *   <li>{@code WAITING}(未就绪,GUI 灰箭头): <b>初始态</b>。放置/读档即此态 -- 首次配置
 *       不白送输出。脉冲模式: 红石上升沿 -> ARMED(挂载当前步)。</li>
 *   <li>{@code ARMED}(就绪,GUI 黄箭头/缺货红箭头): 当前步授权输出至多 1 个。外部
 *       (机械手/漏斗)取走 -> TAKEN。此态下脉冲<b>丢弃</b>(自节拍,防超前)。</li>
 *   <li>{@code TAKEN}(已取走,GUI 绿箭头): 本步额度已消费,指针未动。脉冲模式: 下一
 *       上升沿 -> 前进到下一个已标记槽并 ARMED。</li>
 *   <li>当前槽空(缺货)则 extract 永远为空 -> 整机停住等补货(自动满足"停住不跳步")。</li>
 * </ul>
 *
 * <p>**忽略红石模式**: 取走即前进并重新 ARMED(恒就绪,等效持续输出);单标记槽时回环
 * 自身。切入该模式时若 TAKEN 先前进(本步额度已消费);切入脉冲模式时 ARMED 降级
 * WAITING(旧模式残留的授权不带走,防切换瞬间白送)。
 *
 * <p>**非时钟前进的降级**: 标记取消/读档校正这类<b>非</b>"输出-脉冲"节拍的指针前进,
 * 若状态为 TAKEN 则降级 WAITING -- 新当前槽从未输出,下一脉冲直接挂载它而非再跳一格
 * (否则会整轮跳过新槽)。全部标记清空时同样归 WAITING。
 *
 * <p>**对外 IItemHandler**(全 6 面统一,见 {@link SequentialFeederItemHandler}):
 * insert 路由到任意匹配标记的未满槽(补货随时),extract 只放行当前步槽且带 1/步闸门。
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

    // 状态机字段(currentStep/stepState 另经 ContainerData 同步给打开的菜单)
    private int currentStep = 0;
    /** 当前步状态。初始 WAITING -- 首个脉冲/忽略模式才授权,防止配置完白送 1 个。 */
    private StepState stepState = StepState.WAITING;
    private boolean prevPowered = false;

    private final SequentialFeederItemHandler itemHandler = new SequentialFeederItemHandler(this);

    /** 红石控制模式弹板(6 面)。value=0 -> REDSTONE_PULSE(默认)。 */
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
                Component.translatable("tooltip.aeronautics_gravity.sequential_feeder.mode"),
                this,
                new FeederModeValueBoxTransform());
        modeBehaviour.value = 0; // 默认 REDSTONE_PULSE
        // 切模式时同步状态: 切忽略先还 TAKEN 欠的前进再恒就绪(该模式自走);切脉冲时
        // ARMED 降级 WAITING -- 旧模式残留的授权不带走,否则切换瞬间机械手白取 1 个
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

    /** 对外 capped handler(capability 与 shift 快速移动共用;每次返回同一实例) */
    public SequentialFeederItemHandler getItemHandler() {
        return itemHandler;
    }

    /** 当前步是否就绪(授权输出) */
    public boolean isArmed() {
        return stepState == StepState.ARMED;
    }

    /** 槽 i 是否已标记 */
    public boolean isMarked(int slot) {
        return !markers.getStackInSlot(slot).isEmpty();
    }

    /**
     * 外部取走当前步 1 个后被 SequentialFeederItemHandler 调用:TAKEN(欠一次前进)。
     * 忽略红石模式下立即前进并重新 ARMED(取走即换步,无需脉冲)。
     */
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
        return Component.translatable("block.aeronautics_gravity.sequential_feeder");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return SequentialFeederMenu.create(id, inv, this);
    }

    // 标记变化钩子

    /**
     * 标记槽变化(设置/清除标记)。校正 currentStep:若当前槽标记被清且恰为当前步,
     * 前进到下一个已标记槽(回环);全部未标记则归 0。
     *
     * <p>这是<b>非时钟</b>前进:若状态为 TAKEN 降级 WAITING -- 前进不是"输出-脉冲"
     * 节拍驱动的,新当前槽从未输出,下一脉冲应直接挂载而非再跳一格(否则整轮跳过新槽)。
     */
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

    /**
     * 指针前进到下一个已标记槽(回环;跳过未标记)。只动指针,不改状态;
     * 无任何标记时 currentStep 归 0(状态由调用方决定,见各调用点的降级规则)。
     */
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
        // 忽略模式恒就绪(读档回填:读 NBT 不触发 modeBehaviour 回调,这里兜底;
        // WAITING 是该模式下的瞬态 -- 初始/全清后重新标记,由 tick 恢复)
        if (getMode() == FeederMode.IGNORE_REDSTONE && stepState == StepState.WAITING && hasAnyMarker()) {
            stepState = StepState.ARMED;
            setChanged();
        }
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (getMode() == FeederMode.REDSTONE_PULSE && powered && !prevPowered) {
            // 上升沿: WAITING -> 挂载当前步;TAKEN -> 前进一格再挂载(自节拍 R 语义);
            // ARMED -> 丢弃(已就绪未取走,防超前)
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
        if (tag.contains("StepState")) {
            int ord = tag.getInt("StepState");
            stepState = ord >= 0 && ord < StepState.values().length
                    ? StepState.values()[ord] : StepState.WAITING;
        } else if (tag.contains("Armed")) { // 中间版(单布尔 armed)
            stepState = tag.getBoolean("Armed") ? StepState.ARMED : StepState.WAITING;
        } else { // 原始版("StepOutputUsed" 反向标志): true=额度已消费欠前进 -> TAKEN
            stepState = tag.getBoolean("StepOutputUsed") ? StepState.TAKEN : StepState.WAITING;
        }
        prevPowered = tag.getBoolean("PrevPowered");
        // NBT 里的 currentStep 可能因标记变化而悬空(如标记在客户端菜单直接改),读后校正。
        // 读档校正非时钟前进,TAKEN 降级 WAITING 防跳步
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

    /** 当前步状态 - 见类 Javadoc 状态机说明 */
    public enum StepState {
        /** 未就绪(灰箭头): 等待首个脉冲/模式自走授权 */
        WAITING,
        /** 就绪(黄箭头): 当前步授权输出至多 1 个 */
        ARMED,
        /** 已取走(绿箭头): 本步额度已消费,下一脉冲前进并就绪 */
        TAKEN
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
     * REDSTONE_PULSE: 红石脉冲控制(默认,脉冲上升沿授权/前进,见 StepState)。
     * IGNORE_REDSTONE: 忽略红石,取走即换步(恒就绪)。
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
