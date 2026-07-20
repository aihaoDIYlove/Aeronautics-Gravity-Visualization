package icu.dreamripples.aeronautics_gravity;

import com.mojang.logging.LogUtils;
import icu.dreamripples.aeronautics_gravity.block.ModBlocks;
import icu.dreamripples.aeronautics_gravity.item.ModCreativeTabs;
import icu.dreamripples.aeronautics_gravity.item.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(AeronauticsGravityVisualization.MOD_ID)
public class AeronauticsGravityVisualization {
    public static final String MOD_ID = "aeronautics_gravity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AeronauticsGravityVisualization(IEventBus modEventBus) {
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        LOGGER.info("Aeronautics Gravity Visualization loaded!");
    }
}
