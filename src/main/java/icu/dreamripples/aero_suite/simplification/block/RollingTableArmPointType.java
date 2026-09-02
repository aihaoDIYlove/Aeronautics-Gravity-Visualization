package icu.dreamripples.aero_suite.simplification.block;

import com.simibubi.create.Create;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import icu.dreamripples.aero_suite.common.AeroSuiteIds;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 滚动加工台的机械臂交互点类型:让机械臂能点选台面作取/放目标。
 * 取放走基类默认的 {@code ItemHandler.BLOCK} capability(RollingTableItemHandler),
 * 只覆写交互高度到台面上方(13/16,同 DepotPoint 抬到台面)。
 * <p>
 * 注册进 Create 的 {@code ARM_INTERACTION_POINT_TYPE}(注册制,非 capability 泛用)。
 * 注意:Create 内部 registry API,Create 大版本升级时需复核。
 */
public class RollingTableArmPointType extends ArmInteractionPointType {

    private static final ResourceLocation ID =
        ResourceLocation.fromNamespaceAndPath(AeroSuiteIds.SIMPLIFICATION_ID, "rolling_table");
    private static final RollingTableArmPointType INSTANCE = new RollingTableArmPointType();

    /**
     * 经 {@link RegisterEvent} 注册进 Create 的 arm_interaction_point_type 自定义
     * registry:该 registry 在 Create 侧 bootstrap 后即冻结,直接
     * {@code Registry.register} 会 IllegalStateException("Registry is already frozen"),
     * 必须走注册期事件(同 FeatureEnabledCondition 的 CONDITION_CODEC 先例)。
     */
    public static void onRegister(RegisterEvent event) {
        event.register(CreateRegistries.ARM_INTERACTION_POINT_TYPE, ID, () -> INSTANCE);
    }

    @Override
    public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof RollingTableBlock;
    }

    @Override
    public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
        return new RollingTablePoint(this, level, pos, state);
    }

    public static class RollingTablePoint extends ArmInteractionPoint {
        public RollingTablePoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
            super(type, level, pos, state);
        }

        @Override
        protected Vec3 getInteractionPositionVector() {
            return Vec3.atLowerCornerOf(pos)
                .add(.5f, 14 / 16f, .5f);
        }
    }
}
