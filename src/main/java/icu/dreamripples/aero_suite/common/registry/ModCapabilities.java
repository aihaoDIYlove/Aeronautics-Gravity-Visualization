package icu.dreamripples.aero_suite.common.registry;

import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlock;
import icu.dreamripples.aero_suite.starlight.StarlightLogistics;
import icu.dreamripples.aero_suite.starlight.block.VoidHosePulleyBlockEntity;
import icu.dreamripples.aero_suite.starlight.block.WorldAnchorBlockEntity;
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
 * 不写 bus 参数: 1.21.1 的 @EventBusSubscriber 自动路由 MOD/GAME bus,
 * 显式 bus = Bus.MOD 在 NeoForge 21.1.235 已是 deprecation-for-removal(编译警告)。
 */
@EventBusSubscriber(modid = StarlightLogistics.MOD_ID)
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
        // 顺序供料器:全 6 面统一 capped handler(insert 路由补货,extract 只当前步 1/步)
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlocks.SEQUENTIAL_FEEDER_BE.get(),
                (be, context) -> be.getItemHandler());
        // 珍珠滞留台:双向 1 格(插入恒收 1 个, 抽取整件)
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlocks.PEARL_STASIS_BE.get(),
                (be, context) -> be.getItemHandler());
    }
}
