package icu.dreamripples.aero_suite.simplification.block;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 单格漏斗菜单: HopperMenu 构造器 checkContainerSize(container, 5) 且硬编码 5 槽,
 * 不能复用, 按 1 槽重写(槽位居中 80,20, 玩家背包排布照抄原版)。
 */
public class SingleSlotHopperMenu extends AbstractContainerMenu {
    private final Container hopper;

    public SingleSlotHopperMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(1));
    }

    public SingleSlotHopperMenu(int containerId, Inventory playerInventory, Container container) {
        super(ModMenus.SINGLE_SLOT_HOPPER.get(), containerId);
        this.hopper = container;
        checkContainerSize(container, 1);
        container.startOpen(playerInventory.player);
        this.addSlot(new Slot(container, 0, 80, 20));

        for (int l = 0; l < 3; l++) {
            for (int k = 0; k < 9; k++) {
                this.addSlot(new Slot(playerInventory, k + l * 9 + 9, 8 + k * 18, l * 18 + 51));
            }
        }
        for (int i1 = 0; i1 < 9; i1++) {
            this.addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 109));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.hopper.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (index < this.hopper.getContainerSize()) {
                if (!this.moveItemStackTo(itemstack1, this.hopper.getContainerSize(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, this.hopper.getContainerSize(), false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.hopper.stopOpen(player);
    }
}
