package icu.dreamripples.aeronautics_gravity.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 寻址牌 BlockItem:加 tooltip 说明用法(shift+右键编辑 / shift+滚轮切换 / 透明 / 机器读取)。
 * 普通BlockItem 无 tooltip,故建子类覆写 appendHoverText。
 */
public class AddressingSignBlockItem extends BlockItem {

    public AddressingSignBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_gravity.addressing_sign.edit")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_gravity.addressing_sign.scroll")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_gravity.addressing_sign.use")
                .withStyle(ChatFormatting.GOLD));
    }
}
