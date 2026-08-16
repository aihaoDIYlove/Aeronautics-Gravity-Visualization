package icu.dreamripples.aero_suite.gravity.block;

import com.google.common.collect.ImmutableList;
import icu.dreamripples.aero_suite.gravity.block.CounterweightLightBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 配"轻"块 BE - 注册 ScrollValueBehaviour,玩家右键弹板调档(1..36)。
 * 值变化时通过 callback 把新档位写入 BlockState.LIFT_TIER,Sable 自动检测并重新计算浮力 cluster。
 * 同时在 tick 中反向同步 BlockState -> behaviour.value(防止外部修改 BlockState 时 UI 不同步)。
 */
public class CounterweightLightBlockEntity extends SmartBlockEntity {

    private static final int MIN_TIER = 1;
    private static final int MAX_TIER = 36;

    private ScrollValueBehaviour liftTier;

    public CounterweightLightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        liftTier = new LiftTierScrollValueBehaviour(
                Component.translatable("block.gravity_visualization.counterweight_light.lift_tier"),
                this,
                new LiftTierValueBoxTransform()
        ).between(MIN_TIER, MAX_TIER).withCallback(this::applyTier);
        liftTier.value = MIN_TIER;
        behaviours.add(liftTier);
    }

    private void applyTier(int tier) {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (state.getValue(CounterweightLightBlock.LIFT_TIER) == tier) return;
        level.setBlockAndUpdate(worldPosition, state.setValue(CounterweightLightBlock.LIFT_TIER, tier));
    }

    @Override
    public void tick() {
        super.tick();
        // 反向同步:外部修改 BlockState(命令、clipboard 等)时把 behaviour.value 同步过来
        if (!isVirtual() && liftTier != null) {
            int stateTier = getBlockState().getValue(CounterweightLightBlock.LIFT_TIER);
            if (liftTier.value != stateTier) {
                liftTier.setValue(stateTier);
            }
        }
    }

    private static class LiftTierScrollValueBehaviour extends ScrollValueBehaviour {
        public LiftTierScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
            withFormatter(i -> i + " kpg");
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, MAX_TIER, 1,
                    ImmutableList.of(Component.translatable("gravity_visualization.unit.lift_kpg")),
                    new ValueSettingsFormatter(this::formatSettings));
        }

        public MutableComponent formatSettings(ValueSettings settings) {
            int value = Math.max(MIN_TIER, Math.min(MAX_TIER, settings.value()));
            return Component.literal(value + " kpg");
        }
    }

    private static class LiftTierValueBoxTransform extends ValueBoxTransform.Sided {
        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return true;
        }

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8, 15.5);
        }
    }
}
