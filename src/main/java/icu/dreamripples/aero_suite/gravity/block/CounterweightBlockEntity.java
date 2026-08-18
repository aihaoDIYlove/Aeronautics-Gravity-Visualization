package icu.dreamripples.aero_suite.gravity.block;

import com.google.common.collect.ImmutableList;
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
 * 配重方块 BE - 注册 ScrollValueBehaviour,玩家右键弹板调档(1..20)。
 * 值变化时通过 callback 把新档位写入 BlockState.MASS_TIER,Sable 自动检测并增量更新质量。
 * 同时在 tick 中反向同步 BlockState -> behaviour.value(防止外部修改 BlockState 时 UI 不同步)。
 */
public class CounterweightBlockEntity extends SmartBlockEntity {

    private static final int MIN_TIER = 1;
    private static final int MAX_TIER = 20;

    private ScrollValueBehaviour massTier;

    public CounterweightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        massTier = new MassTierScrollValueBehaviour(
                Component.translatable("block.gravity_visualization.counterweight.mass_tier"),
                this,
                new MassTierValueBoxTransform()
        ).between(MIN_TIER, MAX_TIER).withCallback(this::applyTier);
        massTier.value = MIN_TIER;
        behaviours.add(massTier);
    }

    private void applyTier(int tier) {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (state.getValue(CounterweightBlock.MASS_TIER) == tier) return;
        level.setBlockAndUpdate(worldPosition, state.setValue(CounterweightBlock.MASS_TIER, tier));
    }

    @Override
    public void tick() {
        super.tick();
        // 反向同步:外部修改 BlockState(命令、clipboard 等)时把 behaviour.value 同步过来
        if (!isVirtual() && massTier != null) {
            int stateTier = getBlockState().getValue(CounterweightBlock.MASS_TIER);
            if (massTier.value != stateTier) {
                massTier.setValue(stateTier);
            }
        }
    }

    /**
     * ScrollValueBehaviour 子类 - 自定义弹板内容(标题、刻度、单位)与数字格式化。
     * createBoard 返回的 ValueSettingsBoard 决定右键弹出的设置板外观。
     * formatSettings 决定弹板中滑条上方显示的数字。
     * withFormatter 决定鼠标悬停时白框中的数字。
     */
    private static class MassTierScrollValueBehaviour extends ScrollValueBehaviour {
        public MassTierScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
            withFormatter(i -> i + " kpg");
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            // Create 弹板列为 0..maxValue 含端点:传 MAX_TIER-1 得 0..19 共 20 格,formatter +1 显示为 1..20
            return new ValueSettingsBoard(label, MAX_TIER - 1, 1,
                    ImmutableList.of(Component.translatable("gravity_visualization.unit.mass_kpg")),
                    new ValueSettingsFormatter(this::formatSettings));
        }

        public MutableComponent formatSettings(ValueSettings settings) {
            return Component.literal((settings.value() + 1) + " kpg");
        }

        @Override
        public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
            int tier = Math.max(MIN_TIER, Math.min(MAX_TIER, valueSetting.value() + 1));
            if (new ValueSettings(0, tier - 1).equals(getValueSettings()))
                return;
            setValue(tier);
            playFeedbackSound(this);
        }

        @Override
        public ValueSettings getValueSettings() {
            return new ValueSettings(0, value - 1);
        }
    }

    /**
     * ValueBoxTransform.Sided 子类 - 决定白框显示在方块哪个面、哪个位置。
     * isSideActive 返回 true 让所有面都能弹板(玩家从任意方向右键都能调)。
     * getSouthLocation 返回 voxel 坐标 (8,8,15.5) 即 south 面中心,基类自动旋转到任意 Direction。
     */
    private static class MassTierValueBoxTransform extends ValueBoxTransform.Sided {
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
