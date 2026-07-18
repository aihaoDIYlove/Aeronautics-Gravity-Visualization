package icu.dreamripples.aeronautics_gravity;

import com.mojang.logging.LogUtils;
import icu.dreamripples.aeronautics_gravity.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(AeronauticsGravityVisualization.MOD_ID)
public class AeronauticsGravityVisualization {
    public static final String MOD_ID = "aeronautics_gravity";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Create 模组的主标签页 key */
    private static final ResourceLocation CREATE_TAB = ResourceLocation.parse("create:palette");

    public AeronauticsGravityVisualization(IEventBus modEventBus) {
        ModItems.ITEMS.register(modEventBus);
        modEventBus.addListener(this::addToCreativeTab);
        LOGGER.info("Aeronautics Gravity Visualization loaded!");
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (CREATE_TAB.equals(event.getTabKey())) {
            event.accept(ModItems.SPARK_WAND.get());
        }
    }
}
