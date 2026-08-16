package icu.dreamripples.aero_suite.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlock;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

/**
 * 变速式便携引擎 - 继承 Simulated 便携引擎,复用燃料/燃烧/超热/渲染/染色框架。
 * 弹板用 SpeedTierScrollBehaviour(离散 15 档 × 2 方向,见该类),弹板值 = 档位索引,
 * formatter 显示对应 RPM,使弹板数字与实际转速一致。
 *
 * 与原版便携引擎的差异:
 * - 转速可调(原版固定 32),通过弹板设置(15 档 32..256)
 * - 超热只翻倍应力容量(calculateAddedStressCapacity ×2),转速不翻倍(原版转速 ×2)
 * - 方向由弹板符号值决定,移除原版 movementDirection 弹板(字段保留避免 tick NPE)
 *
 * movementDirection 处理:super.addBehaviours 创建该字段后从 behaviours 列表移除,
 * 使其弹板不显示、不响应输入,但字段非 null -- tick() 方向冲突段读
 * movementDirection.getValue() 不 NPE;该段反转 movementDirection 不影响输出
 * (getGeneratedSpeed 不读 movementDirection)。
 */
public class VariableSpeedPortableEngineBlockEntity extends PortableEngineBlockEntity {

    public SpeedTierScrollBehaviour speedSetting;

    public VariableSpeedPortableEngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        // 移除原版方向弹板(字段保留避免 tick NPE),用转速弹板替代
        behaviours.remove(this.movementDirection);
        speedSetting = new SpeedTierScrollBehaviour(
                Component.translatable("block.aeronautics_gravity.variable_speed_portable_engine.speed"),
                this, new VariableSpeedValueBoxTransform());
        speedSetting.value = 1;  // 第一档 32 RPM
        speedSetting.withCallback(i -> updateGeneratedRotation());
        behaviours.add(speedSetting);
    }

    @Override
    public float getGeneratedSpeed() {
        if (!PortableEngineBlock.isLitState(getBlockState())) return 0;
        return convertToDirection(speedSetting.getSignedRpm(),
                getBlockState().getValue(HORIZONTAL_FACING));
        // 不乘 superHeated -- 转速不翻倍(超热效应转移到 calculateAddedStressCapacity)
    }

    @Override
    public float calculateAddedStressCapacity() {
        // network 实际提供 SU = capacity × |speed|(见 KineticNetwork.getActualCapacityOf),
        // 故 capacity = baseSU / speed 使 SU 不随转速变;超热 capacity ×2 -> SU ×2(转速不翻倍)。
        // baseSU 2048 = 原版便携引擎 32 RPM 时的输出(capacity 64 × speed 32)。
        float speed = Math.abs(getGeneratedSpeed());
        if (speed == 0) return 0;
        float capacity = 2048f / speed;
        return isSuperHeated() ? capacity * 2 : capacity;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // super(PortableEngineBlockEntity -> GeneratingKineticBlockEntity) 显示:
        // 应力发生器状态 + 提供的应力(用我们覆盖的 calculateAddedStressCapacity, SU 固定)+ 便携式引擎 + 燃料。
        // 转速/超热追加在后,使"应力发生器状态"作为抬头在最上方。
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        CreateLang.builder()
                .add(Component.translatable("tooltip.aeronautics_gravity.current_output_speed")
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip);
        CreateLang.number(Math.abs(getGeneratedSpeed()))
                .translate("generic.unit.rpm")
                .style(ChatFormatting.GOLD)
                .forGoggles(tooltip, 1);
        if (isSuperHeated()) {
            CreateLang.builder()
                    .add(Component.translatable("tooltip.aeronautics_gravity.superheated_stress_boost")
                            .withStyle(ChatFormatting.LIGHT_PURPLE))
                    .forGoggles(tooltip);
        }
        return true;
    }

    /** 弹板位置:复用便携引擎原版 PortableEngineValueBoxTransform 的几何(顶面偏前)。 */
    private static class VariableSpeedValueBoxTransform extends ValueBoxTransform {
        @Override
        public Vec3 getLocalOffset(LevelAccessor levelAccessor, BlockPos blockPos, BlockState blockState) {
            Direction facing = blockState.getValue(HORIZONTAL_FACING);
            float yRot = AngleHelper.horizontalAngle(facing);
            return VecHelper.rotateCentered(VecHelper.voxelSpace(8, 13.5f, 7.4f), yRot, Direction.Axis.Y);
        }

        @Override
        public void rotate(LevelAccessor levelAccessor, BlockPos blockPos, BlockState blockState, PoseStack poseStack) {
            float yRot = AngleHelper.horizontalAngle(blockState.getValue(HORIZONTAL_FACING));
            TransformStack.of(poseStack)
                    .rotateYDegrees(yRot)
                    .rotateXDegrees(90)
                    .translate(0, 0.1, 0);
        }
    }
}
