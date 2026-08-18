package icu.dreamripples.aero_suite.simplification.block;

import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 单格漏斗 BE: 完整复用原版漏斗逻辑(吸取上方容器/掉落物、按朝向排出、冷却、GUI),
 * 仅把容量压到 1 格且每格恒 1 个(不论物品自身堆叠上限)。
 *
 * <p>单件限制靠两处配合:
 * <ul>
 *   <li>{@code getMaxStackSize() == 1}: 拦住 setItem 的 limitSize 与 capability 插入路径;</li>
 *   <li>{@code canPlaceItem == 槽位为空}: 原版 tryMoveInItem 的"合并已有堆"分支用的是
 *       <b>物品自身</b> maxStackSize({@code canMergeItems}), 只靠 maxStackSize 拦不住
 *       64 堆叠物品被 grow 到 2; canPlaceItem 在两条路径(空槽插入/合并)之前都会被检查。</li>
 * </ul>
 *
 * <p>{@link #getType()} 必须覆写: 基类构造器把类型字段硬编码成 vanilla
 * {@code BlockEntityType.HOPPER}, 不覆写则 saveWithFullMetadata 写出的 id 是原版漏斗,
 * 区块重载后会还原成 5 格原版漏斗。NeoForge 在 BlockEntity.getType() 上注释
 * "use getter so correct type is checked for modded subclasses", 即官方指定的做法。
 */
public class SingleSlotHopperBlockEntity extends HopperBlockEntity {

    public SingleSlotHopperBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        // 基类 items 字段初始化为 5 格; getContainerSize() 读 items.size(), 换成 1 格列表
        this.setItems(NonNullList.withSize(1, ItemStack.EMPTY));
    }

    @Override
    public BlockEntityType<?> getType() {
        return ModBlocks.SINGLE_SLOT_HOPPER_BE.get();
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // 非空一律拒绝: 同时拦截空槽插入与"合并已有堆"两条路径(见类注释)
        return this.getItem(slot).isEmpty();
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.simplification_related.single_slot_hopper");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return new SingleSlotHopperMenu(id, player, this);
    }
}
