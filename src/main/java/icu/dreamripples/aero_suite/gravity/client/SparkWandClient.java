package icu.dreamripples.aero_suite.gravity.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import icu.dreamripples.aero_suite.common.AeroSuite;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.context.UseOnContext;

/**
 * 火花魔杖的客户端逻辑(质量可视化开关)。
 * 独立成类使 common 侧的 SparkWandItem 不直接引用任何客户端类(Minecraft/ClientSubLevel/
 * MassVisualizer),由 isClientSide 分支调用 -- 避免专用服务器上依赖 JVM 延迟解析的
 * NoClassDefFoundError 隐患(方法内引用一旦被提前解析即炸)。
 */
public final class SparkWandClient {

    private SparkWandClient() {}

    public static void useOnClient(UseOnContext context) {
        var clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null) return;

        var hitPos = context.getClickedPos();

        // 通过 Sable 的 HELPER 直接查找瞄准位置所在的 SubLevel
        SubLevel subLevel = Sable.HELPER.getContaining(clientLevel, hitPos);

        if (AeroSuite.LOGGER.isDebugEnabled()) {
            AeroSuite.LOGGER.debug("[SparkWand] useOn at {}, subLevel={}", hitPos, subLevel);
        }

        if (subLevel instanceof ClientSubLevel cs) {
            boolean heavy = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
            MassVisualizer.toggle(cs, heavy);
            AeroSuite.LOGGER.debug("[SparkWand] => toggled (heavy={})!", heavy);
        }
    }
}
