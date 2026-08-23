package icu.dreamripples.aero_suite.simplification.block;

import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 带过滤的单格漏斗 BE: 在 {@link SingleSlotHopperBlockEntity}(原版漏斗 1 格恒 1 个)基础上
 * 加一个过滤标记 {@code filter}。
 *
 * <p><b>过滤咽喉</b>: 仅覆写 {@link #canPlaceItem}, 在单格漏斗原有的"空槽才放"判定后
 * 追加 {@code && testFilter(stack)}。原版漏斗三条入料路径(吸上方容器/吸掉落物/外部插入)
 * 全部收敛到 {@code canPlaceItem}({@code HopperBlockEntity.canPlaceItemInContainer} 首行),
 * 故一处拦截即全覆盖, 无需按朝向区分(用户决策 Q2=A 无脑拦)。语义同黄铜漏斗: 只管"进",
 * 不管推出。
 *
 * <p><b>过滤匹配</b>(用户决策 Q1=B 支持创造过滤卡):
 * <ul>
 *   <li>{@code filter} 为空 -> 放行所有(无过滤);</li>
 *   <li>{@code filter} 是 Create {@link FilterItem} -> 走 {@link FilterItemStack#test}
 *       (标签/属性/NBT 规则匹配);</li>
 *   <li>否则 -> {@link ItemStack#isSameItemSameComponents} 精确匹配(含药水种类等组件)。</li>
 * </ul>
 *
 * <p><b>客户端同步</b>: 原版 {@code HopperBlockEntity} 不覆写 {@code getUpdatePacket/
 * getUpdateTag} -- 漏斗物品只在 GUI 经菜单槽同步, 方块外观不依赖内容, 故默认不发 BE 同步包。
 * 但本 BE 的 {@code filter} 要在方块面上由 {@code FilteredHopperRenderer} 渲染出来,
 * 客户端必须拿到 filter。故覆写 {@link #getUpdateTag}({@code saveWithFullMetadata},
 * 永含 BE id, 不会触发"空 tag 静默丢弃")与 {@link #getUpdatePacket}; 读侧靠 NeoForge
 * {@code IBlockEntityExtension.onDataPacket} 默认实现(tag 非空 -> loadWithComponents ->
 * loadAdditional), 不必自行覆写 onDataPacket。运行时改 filter 后用
 * {@code level.sendBlockUpdated(pos, state, state, 3)} 触发同步包(vanilla SignBlockEntity 同款)。
 *
 * <p>{@link #getType} 必须覆写: 基类构造器把类型字段硬编码成 vanilla
 * {@code BlockEntityType.HOPPER}, 不覆写则存盘 id 错(重载还原成原版漏斗)。
 */
public class FilteredSingleSlotHopperBlockEntity extends SingleSlotHopperBlockEntity {

    private ItemStack filter = ItemStack.EMPTY;

    public FilteredSingleSlotHopperBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntityType<?> getType() {
        return ModBlocks.FILTERED_SINGLE_SLOT_HOPPER_BE.get();
    }

    // ---- 过滤 ----

    public ItemStack getFilter() {
        return filter;
    }

    /** 设置过滤标记(服务端权威写 + 触发客户端同步)。空栈 = 清除过滤。 */
    public void setFilter(ItemStack stack) {
        this.filter = stack == null ? ItemStack.EMPTY : stack.copy();
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    /** 当前 stack 是否通过过滤(空过滤放行所有)。 */
    public boolean testFilter(ItemStack stack) {
        if (filter.isEmpty() || stack.isEmpty())
            return true;
        if (filter.getItem() instanceof FilterItem) {
            if (level == null)
                return true; // 客户端早期/无 level 时宽松放行(canPlaceItem 实际只服务端调)
            return FilterItemStack.of(filter).test(level, stack);
        }
        return ItemStack.isSameItemSameComponents(filter, stack);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // super = 单格漏斗"非空槽才放"(拦截空槽插入与合并堆两条路径); 追加过滤
        return super.canPlaceItem(slot, stack) && testFilter(stack);
    }

    // ---- 菜单(复用 SingleSlotHopperMenu, 仅换标题键) ----

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.simplification_related.filtered_single_slot_hopper");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return new SingleSlotHopperMenu(id, player, this);
    }

    // ---- 客户端同步 ----

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // saveWithFullMetadata = id + components + saveAdditional; 永非空(tag 含 "id"),
        // 不触发 onDataPacket 的空 tag 静默丢弃。
        return saveWithFullMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        // create(this) 内部用 getUpdateTag(registries) 取要发的 tag
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- 持久化 ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // 空栈用 saveOptional(1.21.1 ItemStack.save(Provider) 空栈抛 Cannot encode empty)
        tag.put("Filter", filter.saveOptional(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // parseOptional: 无 "Filter" 键 / 空内容 -> EMPTY(清除过滤时也能正确读到空)
        filter = ItemStack.parseOptional(registries, tag.getCompound("Filter"));
    }
}
