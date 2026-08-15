package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.foundation.gui.menu.MenuBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * 顺序供料器菜单。9 幽灵标记槽 + 9 实体物品槽 + 36 玩家槽。
 *
 * <p>不直接继承 GhostItemMenu: 它的 {@code clicked()} 硬编码"slotId >= 36 全是幽灵槽"
 * (slotId-36 直接索引 ghostInventory),我们的实体槽会被误当幽灵槽索引越界。
 * 这里继承 MenuBase 后移植幽灵点击逻辑(Create GhostItemMenu 同款行为):
 * 手持物品左键幽灵槽 -> 设标记(count=1);空手左键 -> 清标记;创造中键 -> 取满堆。
 *
 * <p>两端构造都直接包装 BE 本体的 markers/inventory handler: 服务端权威,客户端经
 * sendToMenu 的 update tag 反查 client BE 拿同一份 handler(ToolboxMenu 模式)。
 *
 * <p>槽布局(index -> GUI): 0..8 = 标记槽, 9..17 = 物品槽, 18..53 = 玩家。
 */
public class SequentialFeederMenu extends MenuBase<SequentialFeederBlockEntity> {

    public static final int MARKER_SLOTS_START = 0;
    public static final int ITEM_SLOTS_START = 9;
    public static final int PLAYER_SLOTS_START = 18;

    public SequentialFeederMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    public SequentialFeederMenu(MenuType<?> type, int id, Inventory inv, SequentialFeederBlockEntity be) {
        super(type, id, inv, be);
    }

    public static SequentialFeederMenu create(int id, Inventory inv, SequentialFeederBlockEntity be) {
        return new SequentialFeederMenu(ModMenus.SEQUENTIAL_FEEDER.get(), id, inv, be);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected SequentialFeederBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        BlockPos pos = extraData.readBlockPos();
        CompoundTag nbt = extraData.readNbt();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null)
            return null;
        if (level.getBlockEntity(pos) instanceof SequentialFeederBlockEntity be) {
            be.readClient(nbt, extraData.registryAccess());
            return be;
        }
        return null;
    }

    @Override
    protected void initAndReadInventory(SequentialFeederBlockEntity contentHolder) {
    }

    @Override
    protected void addSlots() {
        int x = 16;
        int y = 33;
        // 9 标记槽(幽灵)
        for (int i = 0; i < SequentialFeederBlockEntity.SLOTS; i++)
            addSlot(new MarkerSlot(contentHolder.markers, i, x + i * 20, y));
        // 9 物品槽(实体)
        y = 78;
        for (int i = 0; i < SequentialFeederBlockEntity.SLOTS; i++)
            addSlot(new SlotItemHandler(contentHolder.inventory, i, x + i * 20, y) {
                @Override
                public boolean isActive() {
                    // 已标记,或槽里还有遗留物品(标记被取消后槽保持显示,玩家能取走存货,
                    // 取空后才隐藏 -- 否则取消标记会把物品"锁死"在不可见槽里)
                    return contentHolder.isMarked(getSlotIndex())
                            || !contentHolder.inventory.getStackInSlot(getSlotIndex()).isEmpty();
                }
            });
        // 玩家槽：3 行背包 + 快捷栏，间距 20 以匹配贴图槽位网格
        y = 106;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, x + col * 20, y + row * 20));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col, x + col * 20, y + 65));
    }

    @Override
    protected void saveData(SequentialFeederBlockEntity contentHolder) {
    }

    // ---- 幽灵槽 ----

    /** 幽灵标记槽: isFake 让 vanilla 不把它当真实容器(不掉落/不同步),mayPickup 禁止取出。 */
    private static class MarkerSlot extends SlotItemHandler {
        public MarkerSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean isFake() {
            return true;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    // ---- 幽灵点击逻辑(移植 GhostItemMenu.clicked) ----

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        // 只拦截标记槽(0..8)走幽灵逻辑;其余全部放行 vanilla。
        // 不能按"slotId<0 直接丢弃"写:手持物品按住拖动时客户端会把点击转成
        // QUICK_CRAFT 协议(slotId=-999 的起止包 + 途经槽包),吞掉 -999 会让
        // 按下期间鼠标有轻微位移的"点击放置"整组失效 -- 表现为玩家栏偶发放不进。
        if (slotId >= MARKER_SLOTS_START && slotId < ITEM_SLOTS_START) {
            handleGhostClick(slotId, dragType, clickType, player);
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    private void handleGhostClick(int slotId, int button, ClickType clickType, Player player) {
        int slot = slotId - MARKER_SLOTS_START;
        if (clickType == ClickType.CLONE) {
            if (player.isCreative() && getCarried().isEmpty()) {
                ItemStack stackInSlot = contentHolder.markers.getStackInSlot(slot).copy();
                stackInSlot.setCount(stackInSlot.getMaxStackSize());
                setCarried(stackInSlot);
            }
            return;
        }
        if (clickType != ClickType.PICKUP)
            return;
        ItemStack held = getCarried();
        ItemStack insert = ItemStack.EMPTY;
        if (!held.isEmpty()) {
            insert = held.copy();
            insert.setCount(1);
        }
        // 仅服务端改写(SlotItemHandler.set 在两端都会跑,但权威写必须服务端;
        // 客户端预测写靠 clicked 在两端各跑一次的 vanilla 机制)
        contentHolder.markers.setStackInSlot(slot, insert);
        getSlot(slotId).setChanged();
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slotIn) {
        return slotIn.container == playerInventory;
    }

    @Override
    public boolean canDragTo(Slot slotIn) {
        return slotIn.container == playerInventory;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // shift 点玩家物品 -> 路由 insert(找匹配标记槽)
        if (index >= PLAYER_SLOTS_START) {
            Slot slot = getSlot(index);
            ItemStack stackToMove = slot.getItem();
            if (stackToMove.isEmpty())
                return ItemStack.EMPTY;
            ItemStack remainder = contentHolder.getItemHandler().insertItem(0, stackToMove.copy(), false);
            if (remainder.getCount() == stackToMove.getCount())
                return ItemStack.EMPTY;
            slot.set(remainder);
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        // 幽灵标记槽: shift 点 -> 设到第一个空标记槽
        if (index < ITEM_SLOTS_START) {
            ItemStack stack = getSlot(index).getItem();
            if (stack.isEmpty())
                return ItemStack.EMPTY;
            for (int i = 0; i < SequentialFeederBlockEntity.SLOTS; i++) {
                if (contentHolder.markers.getStackInSlot(i).isEmpty()) {
                    ItemStack copy = stack.copy();
                    copy.setCount(1);
                    contentHolder.markers.setStackInSlot(i, copy);
                    getSlot(i).setChanged();
                    break;
                }
            }
            return ItemStack.EMPTY;
        }
        // 物品槽 -> 玩家
        Slot slot = getSlot(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty())
            return ItemStack.EMPTY;
        if (!moveItemStackTo(stack, PLAYER_SLOTS_START, slots.size(), true))
            return ItemStack.EMPTY;
        slot.set(stack);
        slot.setChanged();
        return ItemStack.EMPTY;
    }
}
