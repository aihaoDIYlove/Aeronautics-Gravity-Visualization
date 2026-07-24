package icu.dreamripples.aeronautics_gravity.item;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import icu.dreamripples.aeronautics_gravity.AeronauticsGravityVisualization;
import icu.dreamripples.aeronautics_gravity.client.MassVisualizer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

public class SparkWandItem extends Item {
    public SparkWandItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            var clientLevel = Minecraft.getInstance().level;
            if (clientLevel == null) return InteractionResult.SUCCESS;

            var hitPos = context.getClickedPos();

            // 通过 Sable 的 HELPER 直接查找瞄准位置所在的 SubLevel
            SubLevel subLevel = Sable.HELPER.getContaining(clientLevel, hitPos);

            AeronauticsGravityVisualization.LOGGER.info(
                    "[SparkWand] useOn at {}, subLevel={}",
                    hitPos, subLevel);

            if (subLevel instanceof ClientSubLevel cs) {
                boolean heavy = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
                MassVisualizer.toggle(cs, heavy);
                AeronauticsGravityVisualization.LOGGER.info("[SparkWand] => toggled (heavy={})!", heavy);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_gravity.spark_wand.full").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_gravity.spark_wand.heavy").withStyle(ChatFormatting.GRAY));
    }
}
