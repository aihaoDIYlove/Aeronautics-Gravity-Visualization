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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.List;

/**
 * 自稳定方块 - 纯姿态力矩 PD 控制器(控制律详见 {@link StabilizerBlockEntity} Javadoc)。
 * BlockState 为纯灯带显示载体(Sable 不读):LIFT_TIER = 输出强度(青色带,1=灭),
 * OVERDRIVE = 过载(任一轴倾斜超过该轴满档角,输出档 +1 到 16,灯带转红),
 * MASS_TIER 恒 1,仅为 blockstate schema 兼容保留(旧存档方块无缝加载,遗留点亮值由 BE 首 tick 归一;
 * 新增 OVERDRIVE 属性对旧存档同样透明,缺省即 false)。
 */
public class StabilizerBlock extends Block implements IBE<StabilizerBlockEntity>, IWrenchable {

    public static final IntegerProperty MASS_TIER = IntegerProperty.create("mass_tier", 1, 16);
    public static final IntegerProperty LIFT_TIER = IntegerProperty.create("lift_tier", 1, 16);
    public static final BooleanProperty OVERDRIVE = BooleanProperty.create("overdrive");

    public StabilizerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(MASS_TIER, 1)
                .setValue(LIFT_TIER, 1)
                .setValue(OVERDRIVE, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MASS_TIER, LIFT_TIER, OVERDRIVE);
    }

    @Override
    public Class<StabilizerBlockEntity> getBlockEntityClass() {
        return StabilizerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StabilizerBlockEntity> getBlockEntityType() {
        return ModBlocks.STABILIZER_BE.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.starlight_logistics.stabilizer.placement")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.starlight_logistics.stabilizer.pairing")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
