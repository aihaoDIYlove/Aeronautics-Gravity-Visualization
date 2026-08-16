package icu.dreamripples.aero_suite.starlight.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import icu.dreamripples.aero_suite.starlight.StarlightLogistics;
import net.minecraft.resources.ResourceLocation;

/**
 * Flywheel PartialModel 注册 - 灯带几何。
 * REDSTONE_INDICATOR: 72 小方块突出表面版,配重/配轻块 + 换皮后 stabilizer 用(不透明方块须突出才可见)。
 * ANCHOR_INDICATOR: 32 小方块内嵌版,框住星空,世界锚点专用。
 * 必须在 ModelEvent.RegisterAdditional 之前触发类加载,在 FMLClientSetupEvent 调用 init() 即可。
 */
public class ModPartialModels {

    public static final PartialModel REDSTONE_INDICATOR =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath(
                    StarlightLogistics.MOD_ID, "block/redstone_indicator"));

    public static final PartialModel ANCHOR_INDICATOR =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath(
                    StarlightLogistics.MOD_ID, "block/anchor_indicator"));

    public static void init() {
        // 触发 static 字段初始化,完成 PartialModel 注册
    }
}
