package icu.dreamripples.aero_suite.starlight.client;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 虚空之触连锁高亮(客户端): 服务端下发连锁集合后, 用 catnip Outliner 的多方块簇轮廓
 * (showCluster)渲染透视线框; 每 tick 重新 show 同一 slot 续期, 过期/清空(空列表)即移除。
 */
@OnlyIn(Dist.CLIENT)
public final class VoidTouchHighlightClient {

    private static final Object SLOT = new Object();
    /** 与服务端 ARMED_TTL(3s) 对齐, 略长 0.5s 兜底(超时后服务端不再续期, 本地自行消失)。 */
    private static final int HIGHLIGHT_TTL_TICKS = (int) (3.5 * 20);

    private static List<BlockPos> positions = List.of();
    private static long expireGameTime;

    private VoidTouchHighlightClient() {}

    /** 网络线程 -> 主线程入口(见 ModPayloads)。空列表 = 清除高亮。 */
    public static void apply(List<BlockPos> list) {
        positions = list;
        var level = Minecraft.getInstance().level;
        expireGameTime = (level != null ? level.getGameTime() : 0) + HIGHLIGHT_TTL_TICKS;
    }

    public static void clientTick() {
        if (positions.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        if (level == null || level.getGameTime() > expireGameTime) {
            positions = List.of();
            Outliner.getInstance().remove(SLOT);
            return;
        }
        Outliner.getInstance().showCluster(SLOT, positions);
    }
}
