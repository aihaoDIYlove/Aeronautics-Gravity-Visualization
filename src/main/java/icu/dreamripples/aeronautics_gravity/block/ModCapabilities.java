package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlock;
import icu.dreamripples.aeronautics_gravity.AeronauticsGravityVisualization;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * 为虚空软管滑轮 BE 注册 {@link Capabilities.FluidHandler#BLOCK} capability,
 * 返回 {@link VoidHosePulleyBlockEntity#getVoidHandler()}(无限星空液体)。
 * <p>
 * 参考 Create {@code HosePulleyBlockEntity.registerCapabilities}: 只在管道面
 * ({@link HosePulleyBlock#hasPipeTowards})暴露 handler, 其他方向返回 null。
 * 1.21.1 NeoForge 的 @EventBusSubscriber 自动路由事件到 MOD/GAME bus(无需 bus 参数,
 * 见 Simulated ModBusEvents), RegisterCapabilitiesEvent 自动走 MOD bus。
 */
@EventBusSubscriber(modid = AeronauticsGravityVisualization.MOD_ID)
public class ModCapabilities {

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlocks.VOID_HOSE_PULLEY_BE.get(),
                (be, context) -> {
                    if (context == null
                            || HosePulleyBlock.hasPipeTowards(be.getLevel(), be.getBlockPos(), be.getBlockState(), context))
                        return be.getVoidHandler();
                    return null;
                });
        // 世界锚点:暴露 ItemHandler(双向,漏斗/溜槽导入导出包裹)
        WorldAnchorBlockEntity.registerCapabilities(event);
    }
}
