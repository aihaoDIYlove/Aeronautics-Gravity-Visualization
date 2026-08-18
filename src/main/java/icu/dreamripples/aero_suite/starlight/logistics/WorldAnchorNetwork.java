package icu.dreamripples.aero_suite.starlight.logistics;

import com.simibubi.create.content.logistics.box.PackageItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 世界锚点跨维度物流网络 - 服务端全局注册表。
 *
 * 接收端按告示牌配置的 addressFilter 注册到全局 Map(跨所有维度共享)。
 * 发送端用 {@link PackageItem#matchAddress} 做 glob 匹配查找接收端(支持 "factory_*" 通配地址)。
 *
 * **冲突两层**:
 * - 接收端冲突:同 addressFilter 多个接收端 -> 都白灯({@link #isConflicted})。
 * - 发送端冲突:一个包裹匹配到多个接收端 -> 发不出黄灯({@link #send} 返回 CONFLICT)。
 *
 * **不引用 WorldAnchorBlockEntity**:通过 {@link Capabilities.ItemHandler#BLOCK} capability
 * 插入目标物品栏,避免与 BE 循环依赖,Network 独立可编译。
 *
 * **持久化**:静态 Map,服务端重启后由接收端 BE onLoad 重新 register。接收端强加载自身区块,
 * 维度加载时 BE 即注册,发送端总能找到。
 */
public class WorldAnchorNetwork {

    public enum SendResult { SUCCESS, NO_RECEIVER, CONFLICT, TARGET_UNLOADED, TARGET_FULL }

    /** 并发容器:跨维度全局注册表,防御非主线程路径触碰导致的 CME/脏状态 */
    private static final Map<String, List<ReceiverEntry>> RECEIVERS = new ConcurrentHashMap<>();

    public record ReceiverEntry(ResourceKey<Level> dim, BlockPos pos) {}

    /** 接收端注册:addressFilter 为告示牌配置的地址(可为 glob 模式如 "factory_*") */
    public static void register(String address, ResourceKey<Level> dim, BlockPos pos) {
        if (address == null || address.isBlank()) return;
        RECEIVERS.computeIfAbsent(address, k -> new CopyOnWriteArrayList<>()).add(new ReceiverEntry(dim, pos));
    }

    /** 接收端注销:模式切换/移除时调用 */
    public static void deregister(String address, ResourceKey<Level> dim, BlockPos pos) {
        if (address == null || address.isBlank()) return;
        List<ReceiverEntry> list = RECEIVERS.get(address);
        if (list == null) return;
        list.removeIf(e -> e.dim().equals(dim) && e.pos().equals(pos));
        // 仅当已空才删 key,且用双参 remove(值相等才删)防误删并发 register 刚建的新 list。
        // 注意:不能无条件 remove(address, list) -- map 里就是这同一个 list 对象,equals 恒成立,
        // 会把仍持有其他接收端的整个地址条目连根删掉(两锚点同地址后切走其一 -> 另一锚点幽灵失联)。
        if (list.isEmpty()) RECEIVERS.remove(address, list);
    }

    /** 接收端冲突检测:同 addressFilter 多个接收端 -> true(白灯) */
    public static boolean isConflicted(String address) {
        if (address == null || address.isBlank()) return false;
        List<ReceiverEntry> list = RECEIVERS.get(address);
        return list != null && list.size() > 1;
    }

    /**
     * 发送端跨维度传输包裹。
     *
     * @param server    MinecraftServer(用于跨维度取 Level)
     * @param boxAddress 包裹自身标注的收货地址({@link PackageItem#getAddress})
     * @param box       包裹物品(将被插入接收端物品栏;**消费语义**--插入成功时 stack 会被就地缩减,调用方不得复用)
     * @return 发送结果:SUCCESS / NO_RECEIVER / CONFLICT / TARGET_UNLOADED / TARGET_FULL
     */
    public static SendResult send(MinecraftServer server, String boxAddress, ItemStack box) {
        if (boxAddress == null || boxAddress.isBlank()) return SendResult.NO_RECEIVER;
        List<ReceiverEntry> matches = new ArrayList<>();
        for (Map.Entry<String, List<ReceiverEntry>> e : RECEIVERS.entrySet()) {
            if (PackageItem.matchAddress(boxAddress, e.getKey())) {
                matches.addAll(e.getValue());
            }
        }
        if (matches.isEmpty()) return SendResult.NO_RECEIVER;
        if (matches.size() > 1) return SendResult.CONFLICT;
        ReceiverEntry target = matches.get(0);
        ServerLevel level = server.getLevel(target.dim());
        if (level == null || !level.isLoaded(target.pos())) return SendResult.TARGET_UNLOADED;
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, target.pos(), null);
        if (handler == null) return SendResult.TARGET_FULL;
        ItemStack remaining = ItemHandlerHelper.insertItem(handler, box, false);
        return remaining.isEmpty() ? SendResult.SUCCESS : SendResult.TARGET_FULL;
    }
}
