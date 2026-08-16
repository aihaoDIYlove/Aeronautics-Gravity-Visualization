package icu.dreamripples.aero_suite.gravity.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import icu.dreamripples.aero_suite.gravity.block.RedstoneCounterweightLightBlock;
import icu.dreamripples.aero_suite.gravity.block.RedstoneTierBlock;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 红石配"轻"块的轻量 BE - 仅为挂载 Flywheel visual(灯带染色)而存在。
 * 无 tick、无 NBT、无 behaviour:红石信号已编码在 BlockState.LIFT_TIER(由 RedstoneTierBlock.neighborChanged 维护),
 * BlockState 自动同步到客户端,visual 直接读 tier 算颜色。
 *
 * 实现 IHaveGoggleInformation:玩家佩戴工程师护目镜注视本方块时,Create 的 GoggleOverlayRenderer
 * 会调用 addToGoggleTooltip,展示当前 BlockState.LIFT_TIER 对应的浮力(tier == 浮力 kpg)。
 * 红石版本无 ScrollValueBehaviour 弹板,护目镜是查看当前档位的唯一可视化途径。
 */
public class RedstoneCounterweightLightBlockEntity extends BlockEntity implements IHaveGoggleInformation {

    public RedstoneCounterweightLightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        int tier = getBlockState().getValue(RedstoneCounterweightLightBlock.LIFT_TIER);
        CreateLang.builder()
            .add(Component.translatable("tooltip.gravity_visualization.current_lift")
                .withStyle(ChatFormatting.GRAY))
            .forGoggles(tooltip);
        CreateLang.number(tier)
            .add(CreateLang.text(" kpg"))
            .style(ChatFormatting.GOLD)
            .forGoggles(tooltip, 1);
        return true;
    }
}
