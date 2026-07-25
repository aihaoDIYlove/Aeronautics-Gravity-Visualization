package icu.dreamripples.aeronautics_gravity.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import icu.dreamripples.aeronautics_gravity.AeronauticsGravityVisualization;
import net.minecraft.resources.ResourceLocation;

/**
 * Flywheel PartialModel 注册 - 灯带几何,配重/配轻共用(染色不同由 Visual 类决定)。
 * 必须在 ModelEvent.RegisterAdditional 之前触发类加载,在 FMLClientSetupEvent 调用 init() 即可。
 */
public class ModPartialModels {

    public static final PartialModel REDSTONE_INDICATOR =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath(
                    AeronauticsGravityVisualization.MOD_ID, "block/redstone_indicator"));

    public static void init() {
        // 触发 static 字段初始化,完成 PartialModel 注册
    }
}
