package icu.dreamripples.aero_suite.gravity.item;

import icu.dreamripples.aero_suite.gravity.client.PortableDiagramClient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/**
 * 便携图解: 右键载具直接打开 Simulated 的「机器图纸」页面, 免去放置 contraption_diagram 实体到墙面的麻烦.
 *
 * 客户端逻辑(Minecraft/ClientSubLevel/DiagramScreen 等客户端类)全部隔离在
 * {@link PortableDiagramClient},本类保持 common 纯净,专用服务器无
 * NoClassDefFoundError 风险。工作原理详见 PortableDiagramClient 的 Javadoc。
 */
public class PortableDiagramItem extends Item {

    public PortableDiagramItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            PortableDiagramClient.useOnClient(context);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gravity_visualization.portable_diagram")
                .withStyle(ChatFormatting.GRAY));
    }
}
