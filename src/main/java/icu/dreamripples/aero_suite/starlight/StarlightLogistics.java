package icu.dreamripples.aero_suite.starlight;

import com.simibubi.create.content.decoration.encasing.EncasingRegistry;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import icu.dreamripples.aero_suite.common.AeroSuite;
import icu.dreamripples.aero_suite.common.AeroSuiteIds;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import icu.dreamripples.aero_suite.common.registry.ModItems;
import icu.dreamripples.aero_suite.starlight.component.ModDataComponents;
import icu.dreamripples.aero_suite.starlight.fluid.ModFluids;
import icu.dreamripples.aero_suite.starlight.network.ModPayloads;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * mod3 入口: 航空学：星空物流(starlight_logistics)。
 * 持有星空液体/玻璃/机壳/稳定器/世界锚点/寻址牌/激活珍珠的注册 + DataComponent + 网络。
 */
@Mod(StarlightLogistics.MOD_ID)
public class StarlightLogistics {
    public static final String MOD_ID = AeroSuiteIds.STARLIGHT_ID;

    // 寻址牌的 WoodType/BlockSetType:必须在 mod 构造早期注册(static 块,早于 DeferredRegister
    // 和 Sheets 静态初始化 / LayerDefinitions.buildRoots)。WoodType name 带 namespace,使
    // Sheets.createSignMaterial 解析到 starlight_logistics:entity/signs/addressing_sign 贴图,
    // ModelLayers.createSignModelName 解析到 starlight_logistics:sign/addressing_sign layer。
    // (LayerDefinitions.buildRoots 遍历 WoodType.values() 自动建 sign layer, 不能手动再注册。)
    public static final BlockSetType ADDRESSING_SIGN_BLOCK_SET_TYPE;
    public static final WoodType ADDRESSING_SIGN_WOOD_TYPE;
    static {
        ADDRESSING_SIGN_BLOCK_SET_TYPE = BlockSetType.register(new BlockSetType("starlight_logistics_addressing_sign"));
        ADDRESSING_SIGN_WOOD_TYPE = WoodType.register(new WoodType(
            "starlight_logistics:addressing_sign", ADDRESSING_SIGN_BLOCK_SET_TYPE,
            SoundType.GLASS, SoundType.HANGING_SIGN,
            SoundEvents.FENCE_GATE_CLOSE, SoundEvents.FENCE_GATE_OPEN));
    }

    public StarlightLogistics(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        registerConfigScreen(container);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModItems.STARLIGHT_ITEMS.register(modEventBus);
        ModBlocks.STARLIGHT_BLOCKS.register(modEventBus);
        ModBlocks.STARLIGHT_BLOCK_ENTITIES.register(modEventBus);
        ModFluids.FLUID_TYPES.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);
        ModParticles.STARLIGHT_PARTICLES.register(modEventBus);
        ModPayloads.register(modEventBus);
        modEventBus.addListener(StarlightLogistics::onCommonSetup);
        AeroSuite.LOGGER.info("Aeronautics: Starlight Logistics loaded!");
    }

    // 把 starlight_encased_fluid_pipe 注册为 Create fluid_pipe 的 encased 变体,
    // 使 starlight_casing 右键 fluid_pipe 触发套壳(ManualApplicationRecipe + EncasedPipeBlock.handleEncasing).
    // 必须在 block 注册完成后调用 -> FMLCommonSetupEvent.enqueueWork.
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        // AllBlocks.FLUID_PIPE 返回 Registrate 的 BlockEntry, 而 Registrate 是 Create 的 jarjar(不在 compile
        // classpath), 无法访问其类型 -> 改从 registry 按 id 取 FluidPipeBlock 并 cast, 再传入 addVariant.
        event.enqueueWork(() -> {
            // 防御性 instanceof:Create 缺失/id 变更时 registry.get 返回 AIR,直接强转会
            // ClassCastException 启动即崩
            var pipeBlock = BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath("create", "fluid_pipe"));
            if (!(pipeBlock instanceof FluidPipeBlock pipe)) {
                AeroSuite.LOGGER.warn("create:fluid_pipe not found or not a FluidPipeBlock, skip encased variant registration");
                return;
            }
            EncasingRegistry.addVariant(pipe, ModBlocks.STARLIGHT_ENCASED_FLUID_PIPE_BLOCK.get());
        });
    }

    // 三个 mod 的配置按钮都指向同一份自绘配置屏(读 gravity_visualization 的 COMMON 配置, 本地化见 AeroSuiteConfigScreen)
    private static void registerConfigScreen(net.neoforged.fml.ModContainer container) {
        container.registerExtensionPoint(
                net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (c, last) -> new icu.dreamripples.aero_suite.common.config.AeroSuiteConfigScreen(last));
    }
}
