package icu.dreamripples.aero_suite.gravity;

import icu.dreamripples.aero_suite.common.AeroSuite;
import icu.dreamripples.aero_suite.common.AeroSuiteIds;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import icu.dreamripples.aero_suite.common.registry.ModCreativeTabs;
import icu.dreamripples.aero_suite.common.registry.ModItems;
import icu.dreamripples.aero_suite.gravity.advancement.ModTriggers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * mod1 入口: 航空学：重力可视化(gravity_visualization)。
 * 持有火花魔杖/便携图解/配重配轻系列的注册 + 成就触发器 + 共享创造页。
 */
@Mod(GravityVisualization.MOD_ID)
public class GravityVisualization {
    public static final String MOD_ID = AeroSuiteIds.GRAVITY_ID;

    public GravityVisualization(IEventBus modEventBus) {
        ModItems.GRAVITY_ITEMS.register(modEventBus);
        ModBlocks.GRAVITY_BLOCKS.register(modEventBus);
        ModBlocks.GRAVITY_BLOCK_ENTITIES.register(modEventBus);
        ModTriggers.TRIGGERS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        AeroSuite.LOGGER.info("Aeronautics: Gravity Visualization loaded!");
    }
}
