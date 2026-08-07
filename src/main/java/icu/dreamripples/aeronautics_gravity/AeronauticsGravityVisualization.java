package icu.dreamripples.aeronautics_gravity;

import com.mojang.logging.LogUtils;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import icu.dreamripples.aeronautics_gravity.advancement.ModTriggers;
import icu.dreamripples.aeronautics_gravity.network.ModPayloads;
import icu.dreamripples.aeronautics_gravity.block.ModBlocks;
import icu.dreamripples.aeronautics_gravity.fluid.ModFluids;
import icu.dreamripples.aeronautics_gravity.item.ModCreativeTabs;
import icu.dreamripples.aeronautics_gravity.item.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(AeronauticsGravityVisualization.MOD_ID)
public class AeronauticsGravityVisualization {
    public static final String MOD_ID = "aeronautics_gravity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AeronauticsGravityVisualization(IEventBus modEventBus) {
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.BLOCK_ENTITIES.register(modEventBus);
        ModFluids.FLUID_TYPES.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModTriggers.TRIGGERS.register(modEventBus);
        ModPayloads.register(modEventBus);
        modEventBus.addListener(AeronauticsGravityVisualization::onCommonSetup);
        LOGGER.info("Aeronautics Gravity Visualization loaded!");
    }

    // 把 starlight_encased_fluid_pipe 注册为 Create fluid_pipe 的 encased 变体,
    // 使 starlight_casing 右键 fluid_pipe 触发套壳(ManualApplicationRecipe + EncasedPipeBlock.handleEncasing).
    // 必须在 block 注册完成后调用 -> FMLCommonSetupEvent.enqueueWork.
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        // AllBlocks.FLUID_PIPE 返回 Registrate 的 BlockEntry, 而 Registrate 是 Create 的 jarjar(不在 compile
        // classpath), 无法访问其类型 -> 改从 registry 按 id 取 FluidPipeBlock 并 cast, 再传入 addVariant.
        event.enqueueWork(() -> {
            FluidPipeBlock pipe = (FluidPipeBlock) BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath("create", "fluid_pipe"));
            EncasingRegistry.addVariant(pipe, ModBlocks.STARLIGHT_ENCASED_FLUID_PIPE_BLOCK.get());
            // 变速式便携引擎:注册应力容量(超热 ×2 由 BE.calculateAddedStressCapacity 覆盖)
            for (var holder : ModBlocks.VARIABLE_SPEED_PORTABLE_ENGINES.values()) {
                BlockStressValues.CAPACITIES.register(holder.get(), () -> 64.0);
            }
        });
    }
}
