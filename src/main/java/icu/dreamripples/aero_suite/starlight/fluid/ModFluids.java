package icu.dreamripples.aero_suite.starlight.fluid;

import icu.dreamripples.aero_suite.common.AeronauticsGravityVisualization;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import icu.dreamripples.aero_suite.common.registry.ModItems;
import icu.dreamripples.aero_suite.starlight.fluid.StarlightFluid;
import icu.dreamripples.aero_suite.starlight.fluid.StarlightFluidType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 星空液体注册。
 * <p>
 * 不用 Registrate（Create/Aeronautics 的 Registrate 不在本 mod 的 compile classpath），
 * 改用 NeoForge 原生 {@link DeferredRegister} 手写 5 件套中的 3 件（FluidType + Source/Flowing Fluid）。
 * LiquidBlock 在 {@link ModBlocks}、BucketItem 在 {@link ModItems} 注册。
 * <p>
 * API 依据 _research/Registrate 的 FluidBuilder：FluidType 注册到 {@link NeoForgeRegistries#FLUID_TYPES}
 * （不是 {@code Registries.FLUID_TYPE}）；LiquidBlock/BucketItem 构造接直接 Fluid 而非 Supplier；
 * BucketItem/LiquidBlock 的 fluid capability 由 NeoForge 默认提供，无需手写。
 * <p>
 * 贴图为本 mod 自制的 starlight_still/flow (末地之海色调星海动画, tools/gen_starlight.py 生成), lang key = {@code fluid.aeronautics_gravity.starlight}。
 */
public class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, AeronauticsGravityVisualization.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, AeronauticsGravityVisualization.MOD_ID);

    private static final ResourceLocation STILL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AeronauticsGravityVisualization.MOD_ID, "fluid/starlight_still");
    private static final ResourceLocation FLOWING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AeronauticsGravityVisualization.MOD_ID, "fluid/starlight_flow");

    public static final DeferredHolder<FluidType, StarlightFluidType> STARLIGHT_FLUID_TYPE =
            FLUID_TYPES.register("starlight", () -> new StarlightFluidType(
                    FluidType.Properties.create()
                            .descriptionId("fluid.aeronautics_gravity.starlight")
                            .viscosity(1500)
                            .density(1400)
                            .canConvertToSource(false)   // 不形成无限源
                            .canSwim(false)
                            .lightLevel(7),  // 流体发光: 方块层投射光见 ModBlocks.STARLIGHT_BLOCK.lightLevel; 此处让桶贴图(DynamicFluidContainerModel)也全亮一致
                    STILL_TEXTURE, FLOWING_TEXTURE));

    public static final DeferredHolder<Fluid, StarlightFluid> STARLIGHT =
            FLUIDS.register("starlight", () -> new StarlightFluid(starlightProperties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> STARLIGHT_FLOWING =
            FLUIDS.register("starlight_flowing", () -> new BaseFlowingFluid.Flowing(starlightProperties()));

    private static BaseFlowingFluid.Properties starlightProperties() {
        return new BaseFlowingFluid.Properties(STARLIGHT_FLUID_TYPE, STARLIGHT::get, STARLIGHT_FLOWING::get)
                .bucket(() -> ModItems.STARLIGHT_BUCKET.get())
                .block(() -> ModBlocks.STARLIGHT_BLOCK.get())
                .slopeFindDistance(3)
                .levelDecreasePerBlock(2)
                .tickRate(25)
                .explosionResistance(100f);
    }
}
