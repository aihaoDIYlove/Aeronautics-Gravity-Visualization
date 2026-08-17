package icu.dreamripples.aero_suite.simplification;

import com.simibubi.create.api.stress.BlockStressValues;
import icu.dreamripples.aero_suite.common.AeroSuite;
import icu.dreamripples.aero_suite.common.AeroSuiteIds;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import icu.dreamripples.aero_suite.common.registry.ModItems;
import icu.dreamripples.aero_suite.simplification.block.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * mod2 入口: 航空学：方便物品(simplification_related)。
 * 持有更方便的模拟传动器/变速式便携引擎/顺序供料器的注册 + 菜单类型。
 */
@Mod(SimplificationRelated.MOD_ID)
public class SimplificationRelated {
    public static final String MOD_ID = AeroSuiteIds.SIMPLIFICATION_ID;

    public SimplificationRelated(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        registerConfigScreen(container);
        ModItems.SIMPLIFICATION_ITEMS.register(modEventBus);
        ModBlocks.SIMPLIFICATION_BLOCKS.register(modEventBus);
        ModBlocks.SIMPLIFICATION_BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        modEventBus.addListener(SimplificationRelated::onCommonSetup);
        AeroSuite.LOGGER.info("Aeronautics: Simplification Related loaded!");
    }

    // 变速式便携引擎:注册应力容量(超热 ×2 由 BE.calculateAddedStressCapacity 覆盖)
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (var holder : ModBlocks.VARIABLE_SPEED_PORTABLE_ENGINES.values()) {
                BlockStressValues.CAPACITIES.register(holder.get(), () -> 64.0);
            }
        });
    }

    // 三个 mod 的配置按钮都指向同一份配置屏(catnip BaseConfigScreen, 读 gravity_visualization 的 COMMON 配置)
    private static void registerConfigScreen(net.neoforged.fml.ModContainer container) {
        container.registerExtensionPoint(
                net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (c, last) -> new net.createmod.catnip.config.ui.BaseConfigScreen(last, "gravity_visualization"));
    }
}
