package icu.dreamripples.aero_suite.simplification.block;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 过滤漏斗标记槽定位: 仿 Create {@code SmartChuteFilterSlotPositioning} -- 4 个横向面各一个,
 * 标记物贴在侧面 y=11、z=15.5(贴近面)处。{@link #isSideActive} 限定 4 横向面(上下不显示),
 * {@link #getLocalOffset} 把"南面基准点"按朝向绕 Y 轴旋到当前面。
 *
 * <p>独立于 Create 的 SmartChute 定位类(虽几何相同), 避免对"另一个方块专用类"的耦合。
 */
public class FilteredHopperSlotPositioning extends ValueBoxTransform.Sided {

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction side = getSide();
        float horizontalAngle = AngleHelper.horizontalAngle(side);
        Vec3 southLocation = VecHelper.voxelSpace(8, 13, 15.5f);
        return VecHelper.rotateCentered(southLocation, horizontalAngle, Axis.Y);
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        return direction.getAxis().isHorizontal();
    }

    @Override
    protected Vec3 getSouthLocation() {
        return Vec3.ZERO;
    }
}
