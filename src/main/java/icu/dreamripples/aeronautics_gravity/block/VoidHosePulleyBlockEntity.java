package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.simulated_team.simulated.content.end_sea.EndSeaPhysics;
import dev.simulated_team.simulated.content.end_sea.EndSeaPhysicsData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 虚空软管滑轮 BE - 继承 {@link HosePulleyBlockEntity} 复用软管下垂动画/offset 管理/
 * drainer+filler(在末地之海虚空里 BFS 找不到 FluidState, 空转无害)。
 * <p>
 * 不使用父类 private 的 {@code handler}/{@code internalTank}(无法访问), 改为持有自定义
 * {@link VoidHosePulleyFluidHandler}, 经独立 capability 注册暴露(见 {@link ModCapabilities})。
 * 区域满足(滑轮所在维度有 EndSeaPhysics + 软管末端 y &lt;= startY)时 drain 返回无限星空液体,
 * 绕过父类 drainer(虚空无 FluidState 必空转)。
 * <p>
 * 护目镜: 照搬 Create 软管滑轮(kinetic 转速/应力 + infinite hint), 新增"最底端高度"。
 * 父类 {@code infinite} 字段因 drainer/filler 空转为 false, 手动补 hint(active 时虚空软管恒无限)。
 */
public class VoidHosePulleyBlockEntity extends HosePulleyBlockEntity {

    private final VoidHosePulleyFluidHandler voidHandler;

    public VoidHosePulleyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.voidHandler = new VoidHosePulleyFluidHandler(this);
    }

    public VoidHosePulleyFluidHandler getVoidHandler() {
        return voidHandler;
    }

    /** 末端是否进入末地之海区域。active 时 handler 返回无限 STARLIGHT。 */
    public boolean isEndSeaActive() {
        Level level = getLevel();
        if (level == null) return false;
        EndSeaPhysics physics = EndSeaPhysicsData.of(level);
        if (physics == null) return false;
        double endY = getBlockPos().getY() - getInterpolatedOffset(0);
        // startY 是力场顶部(默认 -40), 但末地海渲染顶部在 startY - 2(实测 -42)。
        // 对齐渲染位置: 末端 y <= startY - 2 才算真正进入末地海(载具悬浮在 startY - 1 = -41)。
        return endY <= physics.startY() - 2;
    }

    /** 软管最底端格子的 y 坐标(滑轮 y - ceil(offset), 与 Create rootPosGetter 一致)。 */
    public int getHoseBottomY() {
        return getBlockPos().getY() - (int) Math.ceil(getInterpolatedOffset(0));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 照搬 Create 软管滑轮: super 显示 kinetic 应力(stress=0 时不加 -- 虚空软管是新 BE type,
        // 无 Create stress 配置, calculateStressApplied()=0, super 不加且返回 false).
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        // 虚空软管 active 时恒"无限"(handler 返回无限 STARLIGHT), 但父类 infinite 字段因
        // drainer/filler 在虚空空转为 false, 手动补 infinite hint(复用 Create 的 hint.hose_pulley lang).
        if (isEndSeaActive()) {
            TooltipHelper.addHint(tooltip, "hint.hose_pulley");
        }
        // 新增: 软管最底端高度(总是显示, 玩家需知道末端是否进入末地之海)
        CreateLang.builder()
                .add(Component.translatable("tooltip.aeronautics_gravity.hose_bottom")
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip);
        CreateLang.number(getHoseBottomY())
                .style(ChatFormatting.GOLD)
                .forGoggles(tooltip, 1);
        // 必须返回 true: GoggleOverlayRenderer 第 138 行 if(hasGoggleInfo && !goggleAddedInfo) return
        // 不显示. super 在 stress=0 时返回 false, 但我们加了内容, 必须返回 true 才显示.
        return true;
    }
}
