package icu.dreamripples.aero_suite.starlight.block;

import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlock;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

/**
 * 虚空软管滑轮 - 外观/动画/轴朝向复用 Create 的 {@link HosePulleyBlock},
 * 仅把 BE 重定向到 {@link VoidHosePulleyBlockEntity}(自带无限星空液体 handler)。
 * 不注册额外 blockstate 属性, 沿用 {@code HORIZONTAL_FACING}。
 */
public class VoidHosePulleyBlock extends HosePulleyBlock {

    public VoidHosePulleyBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.starlight_logistics.void_hose_pulley")
                .withStyle(ChatFormatting.AQUA));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<HosePulleyBlockEntity> getBlockEntityClass() {
        return (Class<HosePulleyBlockEntity>) (Class<?>) VoidHosePulleyBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HosePulleyBlockEntity> getBlockEntityType() {
        return ModBlocks.VOID_HOSE_PULLEY_BE.get();
    }
}
