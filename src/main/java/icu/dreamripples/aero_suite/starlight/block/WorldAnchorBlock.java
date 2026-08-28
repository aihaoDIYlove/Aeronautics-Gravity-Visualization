package icu.dreamripples.aero_suite.starlight.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 世界锚点方块 - 跨维度物流核心。6 面弹板切换发送/接收模式(ScrollOptionBehaviour)。
 * 不开放 UI(地址靠告示牌配置),右键交由 Create 的 ValueSettingsInputHandler 处理弹板。
 */
public class WorldAnchorBlock extends Block implements IBE<WorldAnchorBlockEntity>, IWrenchable {

    public WorldAnchorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        addLine(tooltipComponents, "send", ChatFormatting.RED);
        addLine(tooltipComponents, "send_stuck", ChatFormatting.GOLD);
        addLine(tooltipComponents, "receive", ChatFormatting.AQUA);
        addLine(tooltipComponents, "receive_stuck", ChatFormatting.GREEN);
        addLine(tooltipComponents, "address_conflict", ChatFormatting.WHITE);
    }

    private void addLine(List<Component> tooltip, String key, ChatFormatting color) {
        tooltip.add(Component.translatable("tooltip.starlight_logistics.world_anchor." + key)
                .withStyle(color));
    }

    @Override
    public Class<WorldAnchorBlockEntity> getBlockEntityClass() {
        return WorldAnchorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WorldAnchorBlockEntity> getBlockEntityType() {
        return ModBlocks.WORLD_ANCHOR_BE.get();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
