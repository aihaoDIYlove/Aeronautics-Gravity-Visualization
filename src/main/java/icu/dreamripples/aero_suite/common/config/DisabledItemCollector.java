package icu.dreamripples.aero_suite.common.config;

import icu.dreamripples.aero_suite.common.AeroSuite;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;

/**
 * 物品删除执行器: 配置停用某功能后, 玩家"获取到"该功能物品的瞬间删除(背包变空)。
 *
 * <p>实现为服务端每 tick 扫描(而非逐获取事件拦截): 获取途径太多(拾取/创造页/give 指令/
 * JEI 作弊拿取/箱子取出), 逐事件拦截必有漏网; 扫描 41 格背包 + 光标 carried 覆盖所有
 * "玩家持有"状态, 每 tick 开销可忽略(全开时 anyDisabled=false 直接短路)。
 *
 * <p>语义边界(与配置页约定):
 * <ul>
 *   <li>只删"玩家持有"的物品; 机器内部/箱子里的不删(自动化产线不蒸发现有库存, 玩家取出
 *       瞬间才删)。</li>
 *   <li>已放置的方块不受影响(停用只拦获取+合成, 不拆世界)。</li>
 *   <li>删除时发 actionbar 灰字提示, 避免玩家误以为 bug。</li>
 * </ul>
 */
@EventBusSubscriber(modid = "gravity_visualization")
public class DisabledItemCollector {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 10 != 0) return; // 每 10 tick 扫一次(0.5s), 获取到删除的延迟无感
        if (!FeatureGates.anyDisabled()) return;
        Set<net.minecraft.world.item.Item> disabled = FeatureGates.disabledItems();
        if (disabled.isEmpty()) return;

        boolean removed = false;
        // 1) 背包 41 格(含盔甲+副手, containerMenu.slots 覆盖主背包+热栏+光标之外)
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && disabled.contains(stack.getItem())) {
                inv.setItem(i, ItemStack.EMPTY);
                removed = true;
            }
        }
        // 2) 光标上(创造页/普通 GUI 拿起未放下的瞬间)
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && disabled.contains(carried.getItem())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            removed = true;
        }
        if (removed) {
            AeroSuite.LOGGER.debug("Removed disabled item(s) from {}", player.getName().getString());
            player.displayClientMessage(
                    Component.translatable("message.gravity_visualization.item_disabled")
                            .withStyle(s -> s.withColor(0xAAAAAA)),
                    true); // actionbar
            player.containerMenu.broadcastChanges();
        }
    }
}
